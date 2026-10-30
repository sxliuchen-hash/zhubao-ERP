package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.ConsumableAdoption;
import com.zhubao.erp.inventory.domain.ConsumableAdoptionRepository;
import com.zhubao.erp.inventory.domain.ConsumableAdoptionStatus;
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
@ConditionalOnProperty(name = "zhubao.consumable-adoption.repository", havingValue = "jdbc")
public class JdbcConsumableAdoptionRepository implements ConsumableAdoptionRepository {

    private final DataSource dataSource;

    public JdbcConsumableAdoptionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(ConsumableAdoption adoption, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            if (find(connection, adoption.oldItemCode()).isPresent()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        update inv_consumable_adoption
                           set status=?, reason=?, factory_item_id=?, receipt_id=?, quantity=?,
                               amount_state=?, amount_value=?, spec_code=?, warehouse_code=?
                         where old_item_code=?
                        """)) {
                    bindUpdate(statement, adoption);
                    statement.executeUpdate();
                }
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into inv_consumable_adoption(
                        adoption_id, old_item_code, status, reason, factory_item_id, receipt_id, quantity,
                        amount_state, amount_value, spec_code, warehouse_code)
                    values (?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                statement.setString(1, adoption.adoptionId());
                statement.setString(2, adoption.oldItemCode());
                statement.setString(3, adoption.status().name());
                statement.setString(4, adoption.reason());
                statement.setString(5, adoption.factoryItemId());
                statement.setString(6, adoption.receiptId());
                setDecimal(statement, 7, adoption.quantity());
                statement.setString(8, adoption.amountState().name());
                setDecimal(statement, 9, adoption.amount());
                statement.setString(10, adoption.specCode());
                statement.setString(11, adoption.warehouseCode());
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存耗材接续", ex);
        }
    }

    @Override
    public Optional<ConsumableAdoption> findByOldCode(String oldItemCode) {
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, oldItemCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取耗材接续", ex);
        }
    }

    private Optional<ConsumableAdoption> find(Connection connection, String oldItemCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select * from inv_consumable_adoption where old_item_code=?")) {
            statement.setString(1, oldItemCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    private static void bindUpdate(PreparedStatement statement, ConsumableAdoption adoption) throws SQLException {
        statement.setString(1, adoption.status().name());
        statement.setString(2, adoption.reason());
        statement.setString(3, adoption.factoryItemId());
        statement.setString(4, adoption.receiptId());
        setDecimal(statement, 5, adoption.quantity());
        statement.setString(6, adoption.amountState().name());
        setDecimal(statement, 7, adoption.amount());
        statement.setString(8, adoption.specCode());
        statement.setString(9, adoption.warehouseCode());
        statement.setString(10, adoption.oldItemCode());
    }

    private static ConsumableAdoption map(ResultSet rows) throws SQLException {
        return new ConsumableAdoption(
                rows.getString("adoption_id"),
                rows.getString("old_item_code"),
                ConsumableAdoptionStatus.valueOf(rows.getString("status")),
                rows.getString("reason"),
                rows.getString("factory_item_id"),
                rows.getString("receipt_id"),
                rows.getBigDecimal("quantity"),
                CompanyMaterialAmountState.valueOf(rows.getString("amount_state")),
                rows.getBigDecimal("amount_value"),
                rows.getString("spec_code"),
                rows.getString("warehouse_code")
        );
    }

    private static void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        statement.setBigDecimal(index, value);
    }
}
