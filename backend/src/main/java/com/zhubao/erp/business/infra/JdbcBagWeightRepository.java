package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BagWeightFact;
import com.zhubao.erp.business.domain.BagWeightRepository;
import com.zhubao.erp.business.domain.BagWeightState;
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
import java.sql.Types;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.bag-weight.repository", havingValue = "jdbc")
public class JdbcBagWeightRepository implements BagWeightRepository {

    private final DataSource dataSource;

    public JdbcBagWeightRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<BagWeightFact> find(String bagId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select weight_state, weight_value, version_no from biz_bag_weight where bag_id=?")) {
            statement.setString(1, bagId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                String state = rows.getString("weight_state");
                BigDecimal value = rows.getBigDecimal("weight_value");
                boolean missing = rows.wasNull();
                return Optional.of(new BagWeightFact(
                        bagId, BagWeightState.valueOf(state), missing ? null : value, rows.getInt("version_no")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取袋重量", ex);
        }
    }

    @Override
    public void save(BagWeightFact fact, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            if (findLocked(connection, fact.bagId()).isEmpty()) {
                insert(connection, fact);
            } else {
                update(connection, fact);
            }
            insertHistory(connection, fact);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存袋重量", ex);
        }
    }

    private Optional<String> findLocked(Connection connection, String bagId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select bag_id from biz_bag_weight where bag_id=? for update")) {
            statement.setString(1, bagId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(rows.getString(1)) : Optional.empty();
            }
        }
    }

    private void insert(Connection connection, BagWeightFact fact) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into biz_bag_weight(bag_id, weight_state, weight_value, version_no) values (?,?,?,?)")) {
            bind(statement, fact);
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, BagWeightFact fact) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "update biz_bag_weight set weight_state=?, weight_value=?, version_no=? where bag_id=?")) {
            statement.setString(1, fact.state().name());
            if (fact.value() == null) {
                statement.setNull(2, Types.DECIMAL);
            } else {
                statement.setBigDecimal(2, fact.value());
            }
            statement.setInt(3, fact.version());
            statement.setString(4, fact.bagId());
            statement.executeUpdate();
        }
    }

    private void insertHistory(Connection connection, BagWeightFact fact) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into biz_bag_weight_record(bag_id, version_no, weight_state, weight_value) values (?,?,?,?)")) {
            statement.setString(1, fact.bagId());
            statement.setInt(2, fact.version());
            statement.setString(3, fact.state().name());
            if (fact.value() == null) {
                statement.setNull(4, Types.DECIMAL);
            } else {
                statement.setBigDecimal(4, fact.value());
            }
            statement.executeUpdate();
        }
    }

    @Override
    public java.util.List<BagWeightFact> history(String bagId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select weight_state, weight_value, version_no from biz_bag_weight_record where bag_id=? order by version_no")) {
            statement.setString(1, bagId);
            try (ResultSet rows = statement.executeQuery()) {
                java.util.List<BagWeightFact> history = new java.util.ArrayList<>();
                while (rows.next()) {
                    BigDecimal value = rows.getBigDecimal("weight_value");
                    boolean missing = rows.wasNull();
                    history.add(new BagWeightFact(bagId, BagWeightState.valueOf(rows.getString("weight_state")),
                            missing ? null : value, rows.getInt("version_no")));
                }
                return history;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取称重记录", ex);
        }
    }

    private void bind(PreparedStatement statement, BagWeightFact fact) throws SQLException {
        statement.setString(1, fact.bagId());
        statement.setString(2, fact.state().name());
        if (fact.value() == null) {
            statement.setNull(3, Types.DECIMAL);
        } else {
            statement.setBigDecimal(3, fact.value());
        }
        statement.setInt(4, fact.version());
    }
}
