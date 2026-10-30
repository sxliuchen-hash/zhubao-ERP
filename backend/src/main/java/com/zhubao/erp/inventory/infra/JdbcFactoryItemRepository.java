package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.ConsumptionTiming;
import com.zhubao.erp.inventory.domain.CustodyMode;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.FactoryItemRepository;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.factory-item.repository", havingValue = "jdbc")
public class JdbcFactoryItemRepository implements FactoryItemRepository {

    private final DataSource dataSource;

    public JdbcFactoryItemRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(FactoryItem item, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into inv_factory_item(
                    item_id, category, material_name, spec_code, quantity_unit,
                    consumption_timing, custody_mode, enabled, version_no)
                values (?,?,?,?,?,?,?,?,?)
                """)) {
            bind(statement, item);
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (isDuplicate(ex)) {
                throw new BusinessException("同一类别、材质、规格和单位已经建档，不能合并");
            }
            throw new IllegalStateException("无法保存物品档案", ex);
        }
    }

    @Override
    public void updatePolicy(FactoryItem next, int expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                update inv_factory_item
                   set consumption_timing=?, custody_mode=?, version_no=version_no+1
                 where item_id=? and version_no=?
                   and category=? and material_name=? and spec_code=? and quantity_unit=?
                """)) {
            statement.setString(1, next.consumptionTiming().name());
            statement.setString(2, next.custodyMode().name());
            statement.setString(3, next.itemId());
            statement.setInt(4, expectedVersion);
            statement.setString(5, next.category().name());
            statement.setString(6, next.materialName());
            statement.setString(7, next.specCode());
            statement.setString(8, next.unit());
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("物品档案已被其他人修改，请刷新后重试");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新物品档案", ex);
        }
    }

    @Override
    public Optional<FactoryItem> find(String itemId) {
        return queryOne("select * from inv_factory_item where item_id=?", itemId);
    }

    @Override
    public Optional<FactoryItem> findIdentity(FactoryItemCategory category, String materialName, String specCode, String unit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select * from inv_factory_item
                      where category=? and material_name=? and spec_code=? and quantity_unit=?
                     """)) {
            statement.setString(1, category.name());
            statement.setString(2, materialName);
            statement.setString(3, specCode);
            statement.setString(4, unit);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取物品档案", ex);
        }
    }

    @Override
    public List<FactoryItem> list(String materialName) {
        String name = materialName == null ? "" : materialName.trim();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select * from inv_factory_item
                      where (? = '' or material_name = ?)
                      order by item_id
                     """)) {
            statement.setString(1, name);
            statement.setString(2, name);
            try (ResultSet rows = statement.executeQuery()) {
                List<FactoryItem> items = new ArrayList<>();
                while (rows.next()) {
                    items.add(map(rows));
                }
                return List.copyOf(items);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法列出物品档案", ex);
        }
    }

    private Optional<FactoryItem> queryOne(String sql, String itemId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取物品档案", ex);
        }
    }

    private static void bind(PreparedStatement statement, FactoryItem item) throws SQLException {
        statement.setString(1, item.itemId());
        statement.setString(2, item.category().name());
        statement.setString(3, item.materialName());
        statement.setString(4, item.specCode());
        statement.setString(5, item.unit());
        statement.setString(6, item.consumptionTiming().name());
        statement.setString(7, item.custodyMode().name());
        statement.setBoolean(8, item.enabled());
        statement.setInt(9, item.version());
    }

    private static FactoryItem map(ResultSet rows) throws SQLException {
        return new FactoryItem(
                rows.getString("item_id"),
                FactoryItemCategory.valueOf(rows.getString("category")),
                rows.getString("material_name"),
                rows.getString("spec_code"),
                rows.getString("quantity_unit"),
                ConsumptionTiming.valueOf(rows.getString("consumption_timing")),
                CustodyMode.valueOf(rows.getString("custody_mode")),
                rows.getBoolean("enabled"),
                rows.getInt("version_no")
        );
    }

    private static boolean isDuplicate(SQLException ex) {
        String state = ex.getSQLState();
        if ("23505".equals(state) || "23000".equals(state)) {
            return true;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return message.contains("unique") || message.contains("duplicate");
    }
}
