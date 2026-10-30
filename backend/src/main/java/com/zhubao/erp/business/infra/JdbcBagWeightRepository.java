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
