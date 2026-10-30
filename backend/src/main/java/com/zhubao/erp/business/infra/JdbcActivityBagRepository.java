package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.activity-bag.repository", havingValue = "jdbc")
public class JdbcActivityBagRepository implements ActivityBagRepository {

    private final DataSource dataSource;

    public JdbcActivityBagRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(ActivityBag bag, int expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            Integer current = lockVersion(connection, bag.id());
            int actual = current == null ? 0 : current;
            if (actual != expectedVersion) {
                throw new BusinessException("袋已被其他人修改，请刷新后重试");
            }
            if (current == null) {
                insert(connection, bag);
            } else {
                update(connection, bag);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存袋", ex);
        }
    }

    @Override
    public Optional<ActivityBag> find(String id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select * from biz_activity_bag where bag_id=?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取袋", ex);
        }
    }

    @Override
    public List<ActivityBag> listByTarget(String targetCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_activity_bag where target_code=? order by bag_id")) {
            statement.setString(1, targetCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<ActivityBag> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取袋", ex);
        }
    }

    private Integer lockVersion(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select version_no from biz_activity_bag where bag_id=? for update")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : null;
            }
        }
    }

    private void insert(Connection connection, ActivityBag bag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_activity_bag(
                    bag_id, target_code, material_code, requirement_version, source_key, parent_id,
                    closed, quantity, weight, weight_known, version_no)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            bind(statement, bag);
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, ActivityBag bag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update biz_activity_bag
                   set target_code=?, material_code=?, requirement_version=?, source_key=?, parent_id=?,
                       closed=?, quantity=?, weight=?, weight_known=?, version_no=?
                 where bag_id=?
                """)) {
            statement.setString(1, bag.targetCode());
            statement.setString(2, bag.materialCode());
            statement.setString(3, bag.requirementVersion());
            statement.setString(4, bag.sourceKey());
            statement.setString(5, bag.parentId());
            statement.setBoolean(6, bag.closed());
            statement.setBigDecimal(7, bag.quantity());
            if (bag.weight().state() == StockMeasurement.WeightState.KNOWN) {
                statement.setBigDecimal(8, bag.weight().value());
            } else {
                statement.setBigDecimal(8, null);
            }
            statement.setBoolean(9, bag.weight().state() == StockMeasurement.WeightState.KNOWN);
            statement.setInt(10, bag.version());
            statement.setString(11, bag.id());
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, ActivityBag bag) throws SQLException {
        statement.setString(1, bag.id());
        statement.setString(2, bag.targetCode());
        statement.setString(3, bag.materialCode());
        statement.setString(4, bag.requirementVersion());
        statement.setString(5, bag.sourceKey());
        statement.setString(6, bag.parentId());
        statement.setBoolean(7, bag.closed());
        statement.setBigDecimal(8, bag.quantity());
        if (bag.weight().state() == StockMeasurement.WeightState.KNOWN) {
            statement.setBigDecimal(9, bag.weight().value());
        } else {
            statement.setBigDecimal(9, null);
        }
        statement.setBoolean(10, bag.weight().state() == StockMeasurement.WeightState.KNOWN);
        statement.setInt(11, bag.version());
    }

    private ActivityBag map(ResultSet rows) throws SQLException {
        boolean known = rows.getBoolean("weight_known");
        BigDecimal weight = rows.getBigDecimal("weight");
        return new ActivityBag(
                rows.getString("bag_id"),
                rows.getString("target_code"),
                rows.getString("material_code"),
                rows.getString("requirement_version"),
                rows.getString("source_key"),
                rows.getString("parent_id"),
                rows.getBoolean("closed"),
                rows.getBigDecimal("quantity"),
                known ? StockMeasurement.known(weight) : StockMeasurement.unknown(),
                rows.getInt("version_no")
        );
    }
}
