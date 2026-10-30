package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.NodePortionWriter;
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
@ConditionalOnProperty(name = "zhubao.whole-bag-report.repository", havingValue = "jdbc")
public class JdbcNodePortionRepository implements NodePortionReader, NodePortionWriter {

    private final DataSource dataSource;

    public JdbcNodePortionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<NodePortionBalance> find(String bagId, String stepId) {
        try (Connection connection = dataSource.getConnection()) {
            return read(connection, bagId, stepId, false);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取节点份额", ex);
        }
    }

    @Override
    public Optional<NodePortionBalance> find(String bagId, String stepId, WorkSession session) {
        try {
            return read(WorkSessionBindings.requiredJdbc(session), bagId, stepId, true);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定节点份额", ex);
        }
    }

    @Override
    public void save(NodePortionBalance balance, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            if (read(connection, balance.bagId(), balance.stepId(), true).isEmpty()) {
                insert(connection, balance);
            } else {
                update(connection, balance);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存节点份额", ex);
        }
    }

    private Optional<NodePortionBalance> read(Connection connection, String bagId, String stepId, boolean lock)
            throws SQLException {
        String sql = "select arrived, handled from biz_node_portion where bag_id=? and step_id=?"
                + (lock ? " for update" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bagId);
            statement.setString(2, stepId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new NodePortionBalance(
                        bagId, stepId, rows.getBigDecimal("arrived"), rows.getBigDecimal("handled")));
            }
        }
    }

    private void insert(Connection connection, NodePortionBalance balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into biz_node_portion(bag_id, step_id, arrived, handled) values (?,?,?,?)")) {
            statement.setString(1, balance.bagId());
            statement.setString(2, balance.stepId());
            statement.setBigDecimal(3, balance.arrived());
            statement.setBigDecimal(4, balance.handled());
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, NodePortionBalance balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "update biz_node_portion set arrived=?, handled=? where bag_id=? and step_id=?")) {
            statement.setBigDecimal(1, balance.arrived());
            statement.setBigDecimal(2, balance.handled());
            statement.setString(3, balance.bagId());
            statement.setString(4, balance.stepId());
            statement.executeUpdate();
        }
    }
}
