package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalances;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.company-material-receipt.repository", havingValue = "jdbc")
public class JdbcCompanyMaterialReceiptRepository implements CompanyMaterialReceiptRepository {

    private final DataSource dataSource;

    public JdbcCompanyMaterialReceiptRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override public java.util.List<CompanyMaterialBalance> balancesOf(String itemId) {
        try (Connection connection=dataSource.getConnection(); PreparedStatement query=connection.prepareStatement("select * from inv_company_material_balance where item_id=?")) {
            query.setString(1,itemId);
            try (ResultSet rows=query.executeQuery()) {
                java.util.List<CompanyMaterialBalance> result=new java.util.ArrayList<>();
                while (rows.next()) result.add(mapBalance(rows));
                return java.util.List.copyOf(result);
            }
        } catch (SQLException ex) { throw new IllegalStateException("无法读取本厂物品存量明细",ex); }
    }

    @Override public java.util.List<CompanyMaterialReceipt> receiptsOf(String itemId) {
        try (Connection connection=dataSource.getConnection(); PreparedStatement query=connection.prepareStatement("select * from inv_company_material_receipt where item_id=?")) {
            query.setString(1,itemId);
            try (ResultSet rows=query.executeQuery()) {
                java.util.List<CompanyMaterialReceipt> result=new java.util.ArrayList<>();
                while (rows.next()) result.add(mapReceipt(rows));
                return java.util.List.copyOf(result);
            }
        } catch (SQLException ex) { throw new IllegalStateException("无法读取本厂物品存量明细",ex); }
    }

    @Override
    public void insert(CompanyMaterialReceipt receipt, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into inv_company_material_receipt(
                    receipt_id, item_id, batch_code, quantity, quantity_unit, weight_state, weight_value,
                    warehouse_code, source_kind, supplier_name, amount_state, amount_value,
                    payable_code, covered_payable_code, version_no, business_date, recorded_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, receipt.receiptId());
            statement.setString(2, receipt.itemId());
            statement.setString(3, receipt.batchCode());
            statement.setBigDecimal(4, receipt.quantity());
            statement.setString(5, receipt.unit());
            statement.setString(6, receipt.weight().state().name());
            setDecimal(statement, 7, receipt.weight().value());
            statement.setString(8, receipt.warehouseCode());
            statement.setString(9, receipt.sourceKind().name());
            statement.setString(10, receipt.supplierName());
            statement.setString(11, receipt.amountState().name());
            setDecimal(statement, 12, receipt.amount());
            statement.setString(13, receipt.payableCode());
            statement.setString(14, receipt.coveredPayableCode());
            statement.setInt(15, receipt.version());
            statement.setString(16, receipt.businessDate());
            statement.setString(17, receipt.recordedAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存公司料入库", ex);
        }
    }

    @Override
    public void add(CompanyMaterialBalance incoming, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            CompanyMaterialBalance current = lock(connection, incoming.itemId(), incoming.batchCode(), incoming.warehouseCode());
            CompanyMaterialBalance next = CompanyMaterialBalances.merge(current, incoming);
            if (current == null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into inv_company_material_balance(
                            item_id, batch_code, warehouse_code, quantity, quantity_unit, weight_state, weight_value)
                        values (?,?,?,?,?,?,?)
                        """)) {
                    bindBalance(statement, next);
                    statement.executeUpdate();
                }
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    update inv_company_material_balance
                       set quantity=?, quantity_unit=?, weight_state=?, weight_value=?
                     where item_id=? and batch_code=? and warehouse_code=?
                    """)) {
                statement.setBigDecimal(1, next.quantity());
                statement.setString(2, next.unit());
                statement.setString(3, next.weight().state().name());
                setDecimal(statement, 4, next.weight().value());
                statement.setString(5, next.itemId());
                statement.setString(6, next.batchCode());
                statement.setString(7, next.warehouseCode());
                if (statement.executeUpdate() != 1) {
                    throw new BusinessException("公司料批次已被其他人修改，请刷新后重试");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新公司料批次", ex);
        }
    }

    @Override
    public void take(String itemId, String batchCode, String warehouseCode, BigDecimal quantity, String unit,
                     StockMeasurement.Weight weight, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            CompanyMaterialBalance current = lock(connection, itemId, batchCode, warehouseCode);
            if (current == null || current.quantity().compareTo(quantity) < 0) {
                throw new BusinessException("可领数量不足");
            }
            if (!current.unit().equals(unit)) {
                throw new BusinessException("单位必须与来源批次一致");
            }
            CompanyMaterialBalances.assertFullMovementWeight(current, quantity, weight);
            StockMeasurement.Weight left = CompanyMaterialBalances.remainder(current.weight(), weight);
            BigDecimal leftQuantity = current.quantity().subtract(quantity);
            if (leftQuantity.signum() == 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        delete from inv_company_material_balance
                         where item_id=? and batch_code=? and warehouse_code=?
                        """)) {
                    statement.setString(1, itemId);
                    statement.setString(2, batchCode);
                    statement.setString(3, warehouseCode);
                    statement.executeUpdate();
                }
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    update inv_company_material_balance
                       set quantity=?, weight_state=?, weight_value=?
                     where item_id=? and batch_code=? and warehouse_code=?
                    """)) {
                statement.setBigDecimal(1, leftQuantity);
                statement.setString(2, left.state().name());
                setDecimal(statement, 3, left.value());
                statement.setString(4, itemId);
                statement.setString(5, batchCode);
                statement.setString(6, warehouseCode);
                if (statement.executeUpdate() != 1) {
                    throw new BusinessException("公司料批次已被其他人修改，请刷新后重试");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法扣减公司料批次", ex);
        }
    }

    @Override
    public Optional<CompanyMaterialReceipt> find(String receiptId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from inv_company_material_receipt where receipt_id=?")) {
            statement.setString(1, receiptId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapReceipt(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取公司料入库", ex);
        }
    }

    @Override
    public Optional<CompanyMaterialBalance> balance(String itemId, String batchCode, String warehouseCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select * from inv_company_material_balance
                      where item_id=? and batch_code=? and warehouse_code=?
                     """)) {
            statement.setString(1, itemId);
            statement.setString(2, batchCode);
            statement.setString(3, warehouseCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapBalance(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取公司料批次", ex);
        }
    }

    @Override
    public boolean coversPayable(String payableCode, WorkSession session) {
        if (payableCode == null || payableCode.isBlank()) {
            return false;
        }
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        // 先锁同一应付，再读取两种来源列，避免不同请求并发重复覆盖。
        try (PreparedStatement lock = connection.prepareStatement("select code from fin_payable where code=? for update")) {
            lock.setString(1, payableCode);
            try (ResultSet rows = lock.executeQuery()) {
                if (!rows.next()) {
                    throw new BusinessException("应付不存在");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定入库应付", ex);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                select receipt_id from inv_company_material_receipt
                 where covered_payable_code=? or payable_code=? for update
                """)) {
            statement.setString(1, payableCode);
            statement.setString(2, payableCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核对应付是否已经入库", ex);
        }
    }

    private CompanyMaterialBalance lock(Connection connection, String itemId, String batchCode, String warehouseCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select * from inv_company_material_balance
                 where item_id=? and batch_code=? and warehouse_code=?
                 for update
                """)) {
            statement.setString(1, itemId);
            statement.setString(2, batchCode);
            statement.setString(3, warehouseCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapBalance(rows) : null;
            }
        }
    }

    @Override
    public Optional<CompanyMaterialBalance> lockBalance(String itemId, String batchCode, String warehouseCode, WorkSession session) {
        try {
            return Optional.ofNullable(lock(WorkSessionBindings.requiredJdbc(session), itemId, batchCode, warehouseCode));
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定盘点物料", ex);
        }
    }

    private static void bindBalance(PreparedStatement statement, CompanyMaterialBalance balance) throws SQLException {
        statement.setString(1, balance.itemId());
        statement.setString(2, balance.batchCode());
        statement.setString(3, balance.warehouseCode());
        statement.setBigDecimal(4, balance.quantity());
        statement.setString(5, balance.unit());
        statement.setString(6, balance.weight().state().name());
        setDecimal(statement, 7, balance.weight().value());
    }

    private static CompanyMaterialReceipt mapReceipt(ResultSet rows) throws SQLException {
        return new CompanyMaterialReceipt(
                rows.getString("receipt_id"),
                rows.getString("item_id"),
                rows.getString("batch_code"),
                rows.getBigDecimal("quantity"),
                rows.getString("quantity_unit"),
                weight(rows.getString("weight_state"), rows.getBigDecimal("weight_value")),
                rows.getString("warehouse_code"),
                CompanyMaterialSourceKind.valueOf(rows.getString("source_kind")),
                rows.getString("supplier_name"),
                CompanyMaterialAmountState.valueOf(rows.getString("amount_state")),
                rows.getBigDecimal("amount_value"),
                rows.getString("payable_code"),
                rows.getString("covered_payable_code"),
                rows.getInt("version_no"), rows.getString("business_date"), rows.getString("recorded_at")
        );
    }

    private static CompanyMaterialBalance mapBalance(ResultSet rows) throws SQLException {
        return new CompanyMaterialBalance(
                rows.getString("item_id"),
                rows.getString("batch_code"),
                rows.getString("warehouse_code"),
                rows.getBigDecimal("quantity"),
                rows.getString("quantity_unit"),
                weight(rows.getString("weight_state"), rows.getBigDecimal("weight_value"))
        );
    }

    private static StockMeasurement.Weight weight(String state, BigDecimal value) {
        if ("KNOWN".equals(state)) {
            return StockMeasurement.known(value);
        }
        return StockMeasurement.unknown();
    }

    private static void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setBigDecimal(index, null);
            return;
        }
        statement.setBigDecimal(index, value);
    }
}
