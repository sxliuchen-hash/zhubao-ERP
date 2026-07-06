package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
@ConditionalOnProperty(name = "zhubao.inventory.repository", havingValue = "jdbc")
public class JdbcStockLedgerRepository implements StockLedgerRepository {

    private final DataSource dataSource;

    public JdbcStockLedgerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public StockLedgerEntry save(StockLedgerEntry entry) {
        verifyTransferDimensions(entry);
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertLedger(connection, entry);
                if (entry.from() != null) {
                    applyDelta(connection, entry.from(), entry.quantity().negate(), entry.weight().negate(), entry.code());
                }
                applyDelta(connection, entry.to(), entry.quantity(), entry.weight(), entry.code());
                connection.commit();
                return entry;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save stock ledger entry", ex);
        }
    }

    @Override
    public List<StockLedgerEntry> findAllLedgers() {
        String sql = """
                select code, biz_time, document_type, document_code, owner_type, owner_customer_id,
                       customer_id, mother_order_code, work_order_code, material_code,
                       quantity, weight, from_warehouse_code, to_warehouse_code,
                       from_stock_status, to_stock_status, remark
                  from inv_stock_ledger
                 order by biz_time, id
                """;
        List<StockLedgerEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                StockDimension to = dimension(
                        ownerType(rs.getString("owner_type")),
                        nullableLong(rs, "owner_customer_id"),
                        nullableLong(rs, "customer_id"),
                        rs.getString("mother_order_code"),
                        rs.getString("work_order_code"),
                        rs.getString("material_code"),
                        rs.getString("to_warehouse_code"),
                        StockStatus.valueOf(rs.getString("to_stock_status"))
                );
                String fromWarehouse = rs.getString("from_warehouse_code");
                String fromStatus = rs.getString("from_stock_status");
                StockDimension from = fromWarehouse == null || fromStatus == null ? null : dimension(
                        to.ownerType(),
                        to.ownerCustomerId(),
                        to.customerId(),
                        to.motherOrderCode(),
                        to.workOrderCode(),
                        to.materialCode(),
                        fromWarehouse,
                        StockStatus.valueOf(fromStatus)
                );
                entries.add(new StockLedgerEntry(
                        rs.getString("code"),
                        rs.getTimestamp("biz_time").toInstant(),
                        rs.getString("document_type"),
                        rs.getString("document_code"),
                        from,
                        to,
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("weight"),
                        rs.getString("remark")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load stock ledger entries", ex);
        }
        return entries;
    }

    @Override
    public List<StockBalance> findAllBalances() {
        String sql = """
                select owner_type, owner_customer_id, customer_id, mother_order_code, work_order_code,
                       material_code, warehouse_code, stock_status, available_quantity, available_weight
                  from inv_stock_balance
                 order by owner_type, customer_id, mother_order_code, work_order_code, material_code, warehouse_code, stock_status
                """;
        List<StockBalance> balances = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                balances.add(new StockBalance(
                        dimension(
                                ownerType(rs.getString("owner_type")),
                                nullableLong(rs, "owner_customer_id"),
                                nullableLong(rs, "customer_id"),
                                rs.getString("mother_order_code"),
                                rs.getString("work_order_code"),
                                rs.getString("material_code"),
                                rs.getString("warehouse_code"),
                                StockStatus.valueOf(rs.getString("stock_status"))
                        ),
                        rs.getBigDecimal("available_quantity"),
                        rs.getBigDecimal("available_weight")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load stock balances", ex);
        }
        return balances;
    }

    @Override
    public StockBalance findBalance(StockDimension dimension) {
        String sql = """
                select available_quantity, available_weight
                  from inv_stock_balance
                 where dimension_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dimensionKey(dimension));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new StockBalance(dimension, rs.getBigDecimal("available_quantity"), rs.getBigDecimal("available_weight"));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load stock balance", ex);
        }
        return new StockBalance(dimension, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    public void clear() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement deleteLedgers = connection.prepareStatement("delete from inv_stock_ledger");
             PreparedStatement deleteBalances = connection.prepareStatement("delete from inv_stock_balance")) {
            deleteLedgers.executeUpdate();
            deleteBalances.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to clear inventory persistence tables", ex);
        }
    }

    private void insertLedger(Connection connection, StockLedgerEntry entry) throws SQLException {
        String sql = """
                insert into inv_stock_ledger (
                    code, biz_time, document_type, document_code, owner_type, owner_customer_id, customer_id,
                    mother_order_code, work_order_code, material_code, quantity, weight,
                    from_warehouse_code, to_warehouse_code, from_stock_status, to_stock_status, remark
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        StockDimension to = entry.to();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.code());
            statement.setTimestamp(2, Timestamp.from(entry.bizTime()));
            statement.setString(3, entry.documentType());
            statement.setString(4, entry.documentCode());
            statement.setString(5, to.ownerType().name());
            setNullableLong(statement, 6, to.ownerCustomerId());
            setNullableLong(statement, 7, to.customerId());
            statement.setString(8, to.motherOrderCode());
            statement.setString(9, to.workOrderCode());
            statement.setString(10, to.materialCode());
            statement.setBigDecimal(11, entry.quantity());
            statement.setBigDecimal(12, entry.weight());
            statement.setString(13, entry.from() == null ? null : entry.from().warehouseCode());
            statement.setString(14, to.warehouseCode());
            statement.setString(15, entry.from() == null ? null : entry.from().stockStatus().name());
            statement.setString(16, to.stockStatus().name());
            statement.setString(17, entry.remark());
            statement.executeUpdate();
        }
    }

    private void applyDelta(Connection connection, StockDimension dimension, BigDecimal quantityDelta, BigDecimal weightDelta, String ledgerCode)
            throws SQLException {
        String key = dimensionKey(dimension);
        String select = "select available_quantity, available_weight from inv_stock_balance where dimension_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    updateBalance(
                            connection,
                            key,
                            rs.getBigDecimal("available_quantity").add(quantityDelta),
                            rs.getBigDecimal("available_weight").add(weightDelta),
                            ledgerCode
                    );
                    return;
                }
            }
        }
        insertBalance(connection, dimension, key, quantityDelta, weightDelta, ledgerCode);
    }

    private void updateBalance(Connection connection, String key, BigDecimal quantity, BigDecimal weight, String ledgerCode) throws SQLException {
        String sql = """
                update inv_stock_balance
                   set available_quantity = ?, available_weight = ?, last_ledger_code = ?
                 where dimension_key = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, quantity);
            statement.setBigDecimal(2, weight);
            statement.setString(3, ledgerCode);
            statement.setString(4, key);
            statement.executeUpdate();
        }
    }

    private void insertBalance(Connection connection, StockDimension dimension, String key, BigDecimal quantity, BigDecimal weight, String ledgerCode)
            throws SQLException {
        String sql = """
                insert into inv_stock_balance (
                    owner_type, owner_customer_id, customer_id, mother_order_code, work_order_code,
                    material_code, warehouse_code, stock_status, dimension_key,
                    available_quantity, available_weight, last_ledger_code
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dimension.ownerType().name());
            setNullableLong(statement, 2, dimension.ownerCustomerId());
            setNullableLong(statement, 3, dimension.customerId());
            statement.setString(4, dimension.motherOrderCode());
            statement.setString(5, dimension.workOrderCode());
            statement.setString(6, dimension.materialCode());
            statement.setString(7, dimension.warehouseCode());
            statement.setString(8, dimension.stockStatus().name());
            statement.setString(9, key);
            statement.setBigDecimal(10, quantity);
            statement.setBigDecimal(11, weight);
            statement.setString(12, ledgerCode);
            statement.executeUpdate();
        }
    }

    private void verifyTransferDimensions(StockLedgerEntry entry) {
        if (entry.from() == null) {
            return;
        }
        StockDimension from = entry.from();
        StockDimension to = entry.to();
        boolean sameBusinessDimension =
                from.ownerType() == to.ownerType()
                        && Objects.equals(from.ownerCustomerId(), to.ownerCustomerId())
                        && Objects.equals(from.customerId(), to.customerId())
                        && Objects.equals(from.motherOrderCode(), to.motherOrderCode())
                        && Objects.equals(from.workOrderCode(), to.workOrderCode())
                        && Objects.equals(from.materialCode(), to.materialCode());
        if (!sameBusinessDimension) {
            throw new IllegalArgumentException("Stock transfer cannot change owner, customer, order, or material dimension");
        }
    }

    private StockDimension dimension(
            OwnerType ownerType,
            Long ownerCustomerId,
            Long customerId,
            String motherOrderCode,
            String workOrderCode,
            String materialCode,
            String warehouseCode,
            StockStatus stockStatus
    ) {
        return new StockDimension(ownerType, ownerCustomerId, customerId, motherOrderCode, workOrderCode, materialCode, warehouseCode, stockStatus);
    }

    private OwnerType ownerType(String value) {
        return OwnerType.valueOf(value);
    }

    private String dimensionKey(StockDimension dimension) {
        return String.join("|",
                dimension.ownerType().name(),
                nullSafe(dimension.ownerCustomerId()),
                nullSafe(dimension.customerId()),
                dimension.motherOrderCode(),
                nullSafe(dimension.workOrderCode()),
                dimension.materialCode(),
                dimension.warehouseCode(),
                dimension.stockStatus().name()
        );
    }

    private String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }
}
