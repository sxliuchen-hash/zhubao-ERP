package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.PortionRelease;
import com.zhubao.erp.business.domain.PortionReleaseRepository;
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
@ConditionalOnProperty(name = "zhubao.portion-release.repository", havingValue = "jdbc")
public class JdbcPortionReleaseRepository implements PortionReleaseRepository {

    private final DataSource dataSource;

    public JdbcPortionReleaseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PortionRelease> find(String bagId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select released_qty, frozen_qty from biz_portion_release where bag_id=?")) {
            statement.setString(1, bagId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PortionRelease(bagId, rows.getBigDecimal("released_qty"), rows.getBigDecimal("frozen_qty")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取放行数量", ex);
        }
    }

    @Override
    public void save(PortionRelease release, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            if (locked(connection, release.bagId())) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "update biz_portion_release set released_qty=?, frozen_qty=? where bag_id=?")) {
                    statement.setBigDecimal(1, release.released());
                    statement.setBigDecimal(2, release.frozen());
                    statement.setString(3, release.bagId());
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "insert into biz_portion_release(bag_id, released_qty, frozen_qty) values (?,?,?)")) {
                    statement.setString(1, release.bagId());
                    statement.setBigDecimal(2, release.released());
                    statement.setBigDecimal(3, release.frozen());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存放行数量", ex);
        }
    }

    private boolean locked(Connection connection, String bagId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select bag_id from biz_portion_release where bag_id=? for update")) {
            statement.setString(1, bagId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
