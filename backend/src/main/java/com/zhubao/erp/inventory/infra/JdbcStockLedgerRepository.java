package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.inventory.domain.CustomerLedgerFilter;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferRules;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "zhubao.inventory.repository", havingValue = "jdbc")
public class JdbcStockLedgerRepository implements StockLedgerRepository {

    private final DataSource dataSource;

    public JdbcStockLedgerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public StockLedgerEntry save(StockLedgerEntry entry) {
        StockTransferRules.verify(entry);
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                writeLedger(connection, entry);
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
    public StockLedgerEntry appendDeparture(StockLedgerEntry entry, WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("库存离厂必须使用工作单元连接");
        }
        StockTransferRules.verifyDeparture(entry);
        Connection connection = session.connection().get();
        try {
            insertLedger(connection, entry);
            debitBalance(connection, entry);
            return entry;
        } catch (SQLException ex) {
            throw new IllegalStateException("库存离厂写入失败", ex);
        }
    }

    @Override
    public StockLedgerEntry save(StockLedgerEntry entry, WorkSession session) {
        StockTransferRules.verify(entry);
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("库存写入必须使用工作单元连接");
        }
        try {
            writeLedger(session.connection().get(), entry);
            return entry;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save stock ledger entry", ex);
        }
    }

    private void writeLedger(Connection connection, StockLedgerEntry entry) throws SQLException {
        if (entry.to() == null || entry.sourceLedgerCode() != null) {
            throw new IllegalArgumentException("普通库存转移必须写明去向，冲正请使用收货冲正");
        }
        insertLedger(connection, entry);
        if (entry.from() != null) {
            debitBalance(connection, entry);
        }
        creditBalance(connection, entry.to(), entry.quantity(), entry.weight(), entry.code());
    }

    @Override
    public StockLedgerEntry appendInboundReversal(StockLedgerEntry entry, WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("收货冲正必须使用工作单元连接");
        }
        if (entry.to() != null || entry.from() == null || entry.sourceLedgerCode() == null
                || entry.sourceLedgerCode().isBlank() || !"inbound_reversal".equals(entry.documentType())) {
            throw new IllegalArgumentException("收货冲正只能扣减原收货余额，不能转入其他库存状态");
        }
        Connection connection = session.connection().get();
        try {
            insertLedger(connection, entry);
            debitBalance(connection, entry);
            return entry;
        } catch (SQLException ex) {
            throw new IllegalStateException("收货冲正写入失败", ex);
        }
    }

    @Override
    public List<StockLedgerEntry> findAllLedgers() {
        return queryLedgers("", List.of());
    }

    @Override
    public List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes) {
        Set<Long> ids = customerIds == null ? Set.of() : customerIds;
        Set<String> mothers = motherOrderCodes == null ? Set.of() : motherOrderCodes;
        if (ids.isEmpty() && mothers.isEmpty()) {
            return List.of();
        }
        // 占位符过多时退回一次全量读取再过滤，避免超长 IN 语句；仍然不是逐行查询。
        if (ids.size() + mothers.size() > 800) {
            return findAllLedgers().stream().filter(entry -> CustomerLedgerFilter.touches(entry, ids, mothers)).toList();
        }
        StringBuilder where = new StringBuilder(" where ");
        List<Object> parameters = new ArrayList<>();
        if (!ids.isEmpty()) {
            where.append("customer_id in (");
            appendPlaceholders(where, ids.size());
            where.append(')');
            parameters.addAll(ids);
        }
        if (!mothers.isEmpty()) {
            if (!ids.isEmpty()) {
                where.append(" or ");
            }
            where.append("mother_order_code in (");
            appendPlaceholders(where, mothers.size());
            where.append(')');
            parameters.addAll(mothers);
        }
        return queryLedgers(where.toString(), parameters);
    }

    @Override
    public boolean hasOrderMovement(String motherOrderCode, String orderCode) {
        if (motherOrderCode == null || motherOrderCode.isBlank() || orderCode == null || orderCode.isBlank()) {
            return false;
        }
        boolean mother = motherOrderCode.equals(orderCode);
        String sql = mother
                ? "select 1 from inv_stock_ledger where mother_order_code = ? limit 1"
                : """
                select 1 from inv_stock_ledger
                 where mother_order_code = ?
                   and (document_code = ? or work_order_code = ? or from_work_order_code = ?)
                 limit 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            if (!mother) {
                statement.setString(2, orderCode);
                statement.setString(3, orderCode);
                statement.setString(4, orderCode);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核对库存流水", ex);
        }
    }

    @Override
    public List<StockLedgerEntry> ledgersForMother(String motherOrderCode, WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("库存读取必须使用工作单元连接");
        }
        try {
            return queryLedgers(session.connection().get(), " where mother_order_code = ?", List.of(motherOrderCode));
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load stock ledger entries", ex);
        }
    }

    private List<StockLedgerEntry> queryLedgers(String whereSql, List<Object> parameters) {
        try (Connection connection = dataSource.getConnection()) {
            return queryLedgers(connection, whereSql, parameters);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load stock ledger entries", ex);
        }
    }

    private List<StockLedgerEntry> queryLedgers(Connection connection, String whereSql, List<Object> parameters)
            throws SQLException {
        return queryLedgers(connection, whereSql, parameters, false);
    }

    private List<StockLedgerEntry> queryLedgers(Connection connection, String whereSql, List<Object> parameters, boolean lock)
            throws SQLException {
        String sql = """
                select code, source_ledger_code, biz_time, document_type, document_code, owner_type, owner_customer_id,
                       customer_id, mother_order_code, work_order_code, from_work_order_code, material_code,
                       quantity, weight, from_warehouse_code, to_warehouse_code,
                       from_stock_status, to_stock_status, from_batch_code, to_batch_code, from_mother_order_code, remark
                  from inv_stock_ledger
                """ + whereSql + " order by biz_time, id" + (lock ? " for update" : "");
        List<StockLedgerEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OwnerType owner = ownerType(rs.getString("owner_type"));
                    Long ownerCustomerId = nullableLong(rs, "owner_customer_id");
                    Long customerId = nullableLong(rs, "customer_id");
                    String motherOrderCode = rs.getString("mother_order_code");
                    String materialCode = rs.getString("material_code");
                    String toWarehouse = rs.getString("to_warehouse_code");
                    String toStatus = rs.getString("to_stock_status");
                    StockDimension to = toWarehouse == null || toStatus == null ? null : dimension(
                            owner, ownerCustomerId, customerId, motherOrderCode,
                            rs.getString("work_order_code"), materialCode, toWarehouse, StockStatus.valueOf(toStatus)
                    );
                    String fromWarehouse = rs.getString("from_warehouse_code");
                    String fromStatus = rs.getString("from_stock_status");
                    String fromBatch = rs.getString("from_batch_code");
                    String toBatch = rs.getString("to_batch_code");
                    String fromMother = rs.getString("from_mother_order_code");
                    boolean explicitFromMother = !rs.wasNull();
                    if (to != null && toBatch != null && !toBatch.isBlank()) {
                        to = dimension(owner, ownerCustomerId, customerId, motherOrderCode, rs.getString("work_order_code"),
                                materialCode, toWarehouse, StockStatus.valueOf(toStatus), toBatch);
                    }
                    StockDimension anchor = to == null ? null : to;
                    StockDimension from = fromWarehouse == null || fromStatus == null ? null : dimension(
                            anchor == null ? owner : anchor.ownerType(),
                            anchor == null ? ownerCustomerId : anchor.ownerCustomerId(),
                            anchor == null ? customerId : anchor.customerId(),
                            explicitFromMother ? (fromMother == null ? "" : fromMother) : (anchor == null ? motherOrderCode : anchor.motherOrderCode()),
                            to == null ? rs.getString("work_order_code") : rs.getString("from_work_order_code"),
                            anchor == null ? materialCode : anchor.materialCode(),
                            fromWarehouse,
                            StockStatus.valueOf(fromStatus),
                            fromBatch == null ? "" : fromBatch
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
                            rs.getString("remark"),
                            rs.getString("source_ledger_code")
                    ));
                }
            }
        }
        return entries;
    }

    private static void appendPlaceholders(StringBuilder sql, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
    }

    @Override
    public List<StockBalance> findAllBalances() {
        String sql = """
                select owner_type, owner_customer_id, customer_id, mother_order_code, work_order_code,
                       material_code, warehouse_code, stock_status, batch_code, available_quantity, available_weight
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
                                StockStatus.valueOf(rs.getString("stock_status")),
                                rs.getString("batch_code")
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
    public StockBalance findBalance(StockDimension dimension, WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("库存读取必须使用工作单元连接");
        }
        try {
            return readBalance(session.connection().get(), dimension);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load stock balance", ex);
        }
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
                    code, source_ledger_code, biz_time, document_type, document_code, owner_type, owner_customer_id,
                    customer_id, mother_order_code, work_order_code, from_work_order_code, material_code, quantity, weight,
                    from_warehouse_code, to_warehouse_code, from_stock_status, to_stock_status,
                    from_batch_code, to_batch_code, from_mother_order_code, remark
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        StockDimension anchor = entry.to() == null ? entry.from() : entry.to();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.code());
            statement.setString(2, entry.sourceLedgerCode());
            statement.setTimestamp(3, Timestamp.from(entry.bizTime()));
            statement.setString(4, entry.documentType());
            statement.setString(5, entry.documentCode());
            statement.setString(6, anchor.ownerType().name());
            setNullableLong(statement, 7, anchor.ownerCustomerId());
            setNullableLong(statement, 8, anchor.customerId());
            statement.setString(9, anchor.motherOrderCode());
            statement.setString(10, entry.to() == null ? anchor.workOrderCode() : entry.to().workOrderCode());
            statement.setString(11, entry.to() == null || entry.from() == null ? null : entry.from().workOrderCode());
            statement.setString(12, anchor.materialCode());
            statement.setBigDecimal(13, entry.quantity());
            statement.setBigDecimal(14, entry.weight());
            statement.setString(15, entry.from() == null ? null : entry.from().warehouseCode());
            statement.setString(16, entry.to() == null ? null : entry.to().warehouseCode());
            statement.setString(17, entry.from() == null ? null : entry.from().stockStatus().name());
            statement.setString(18, entry.to() == null ? null : entry.to().stockStatus().name());
            statement.setString(19, entry.from() == null ? "" : entry.from().batchCode());
            statement.setString(20, entry.to() == null ? "" : entry.to().batchCode());
            statement.setString(21, entry.from() == null ? null : entry.from().motherOrderCode());
            statement.setString(22, entry.remark());
            statement.executeUpdate();
        }
    }

    private void debitBalance(Connection connection, StockLedgerEntry entry) throws SQLException {
        String sql = """
                update inv_stock_balance
                   set available_quantity = available_quantity - ?,
                       available_weight = available_weight - ?, last_ledger_code = ?
                 where dimension_key = ?
                   and available_quantity >= ? and available_weight >= ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, entry.quantity());
            statement.setBigDecimal(2, entry.weight());
            statement.setString(3, entry.code());
            statement.setString(4, dimensionKey(entry.from()));
            statement.setBigDecimal(5, entry.quantity());
            statement.setBigDecimal(6, entry.weight());
            if (statement.executeUpdate() == 0) {
                // 条件扣减已经作出足额判定，此处重读只用于提供具体失败原因。
                StockTransferRules.requireSufficient(readBalance(connection, entry.from()), entry);
                throw new BusinessException("来源库存不足，请刷新后重试");
            }
        }
    }

    private StockBalance readBalance(Connection connection, StockDimension dimension) throws SQLException {
        String sql = "select available_quantity, available_weight from inv_stock_balance where dimension_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dimensionKey(dimension));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new StockBalance(dimension, rs.getBigDecimal("available_quantity"), rs.getBigDecimal("available_weight"));
                }
            }
        }
        return new StockBalance(dimension, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void creditBalance(Connection connection, StockDimension dimension, BigDecimal quantity, BigDecimal weight, String ledgerCode)
            throws SQLException {
        String sql = """
                insert into inv_stock_balance (
                    owner_type, owner_customer_id, customer_id, mother_order_code, work_order_code,
                    material_code, warehouse_code, stock_status, batch_code, dimension_key,
                    available_quantity, available_weight, last_ledger_code
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    available_quantity = available_quantity + values(available_quantity),
                    available_weight = available_weight + values(available_weight),
                    last_ledger_code = values(last_ledger_code)
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
            statement.setString(9, dimension.batchCode());
            statement.setString(10, dimensionKey(dimension));
            statement.setBigDecimal(11, quantity);
            statement.setBigDecimal(12, weight);
            statement.setString(13, ledgerCode);
            statement.executeUpdate();
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
        return dimension(ownerType, ownerCustomerId, customerId, motherOrderCode, workOrderCode, materialCode, warehouseCode, stockStatus, "");
    }

    private StockDimension dimension(
            OwnerType ownerType,
            Long ownerCustomerId,
            Long customerId,
            String motherOrderCode,
            String workOrderCode,
            String materialCode,
            String warehouseCode,
            StockStatus stockStatus,
            String batchCode
    ) {
        return new StockDimension(ownerType, ownerCustomerId, customerId, motherOrderCode, workOrderCode, materialCode, warehouseCode, stockStatus, batchCode);
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
        ) + (dimension.batchCode() == null || dimension.batchCode().isBlank() ? "" : "|" + dimension.batchCode());
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

    @Override
    public List<StockLedgerEntry> ledgersForMother(String motherOrderCode, WorkSession session, boolean lock) {
        if (lock && (session == null || session.connection().isEmpty())) {
            throw new IllegalStateException("库存流水锁定读取必须使用工作单元连接");
        }
        if (session != null && session.connection().isEmpty()) {
            throw new IllegalStateException("库存流水读取必须使用工作单元连接");
        }
        try {
            if (session != null) {
                return queryLedgers(session.connection().get(), " where mother_order_code = ?", List.of(motherOrderCode), lock);
            }
            try (Connection connection = dataSource.getConnection()) {
                return queryLedgers(connection, " where mother_order_code = ?", List.of(motherOrderCode), false);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load stock ledger entries", ex);
        }
    }

    @Override
    public List<StockBalance> balancesForMother(String motherOrderCode, WorkSession session, boolean lock) {
        if (lock && (session == null || session.connection().isEmpty())) {
            throw new IllegalStateException("库存余额锁定读取必须使用工作单元连接");
        }
        if (session != null && session.connection().isEmpty()) {
            throw new IllegalStateException("库存余额读取必须使用工作单元连接");
        }
        String sql = """
                select owner_type, owner_customer_id, customer_id, mother_order_code, work_order_code,
                       material_code, warehouse_code, stock_status, batch_code, available_quantity, available_weight
                  from inv_stock_balance
                 where mother_order_code = ?
                """ + (lock ? " for update" : "");
        try {
            Connection connection = session == null ? dataSource.getConnection() : session.connection().get();
            boolean own = session == null;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, motherOrderCode);
                try (ResultSet rs = statement.executeQuery()) {
                    List<StockBalance> balances = new ArrayList<>();
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
                                        StockStatus.valueOf(rs.getString("stock_status")),
                                        rs.getString("batch_code")
                                ),
                                rs.getBigDecimal("available_quantity"),
                                rs.getBigDecimal("available_weight")
                        ));
                    }
                    return List.copyOf(balances);
                }
            } finally {
                if (own) {
                    connection.close();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load stock balances", ex);
        }
    }
}
