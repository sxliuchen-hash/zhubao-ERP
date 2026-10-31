package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 路线快照的 JDBC 仓储。写入必须使用工作单元连接，失败时随该命令回滚。
 * 默认仍是内存实现；MySQL 档案改为 jdbc。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.route-snapshot.repository", havingValue = "jdbc")
public class JdbcRouteSnapshotRepository implements RouteSnapshotRepository {

    private final DataSource dataSource;

    public JdbcRouteSnapshotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(RouteSnapshot snapshot, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            if (exists(connection, snapshot.snapshotId())) {
                update(connection, snapshot);
            } else {
                insertRow(connection, snapshot);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存路线快照", ex);
        }
    }

    @Override
    public Optional<RouteSnapshot> find(String snapshotId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_route_snapshot where snapshot_id=?")) {
            statement.setString(1, snapshotId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取路线快照", ex);
        }
    }

    @Override
    public List<RouteSnapshot> list(BusinessKind kind, String targetCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_route_snapshot where business_kind=? and target_code=? order by version_no")) {
            statement.setString(1, kind.name());
            statement.setString(2, targetCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<RouteSnapshot> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取路线快照", ex);
        }
    }

    private boolean exists(Connection connection, String snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select snapshot_id from biz_route_snapshot where snapshot_id=?")) {
            statement.setString(1, snapshotId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void insertRow(Connection connection, RouteSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_route_snapshot(
                    snapshot_id, business_kind, target_code, version_no, step_ids, confirmed)
                values (?,?,?,?,?,?)
                """)) {
            bind(statement, snapshot);
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, RouteSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update biz_route_snapshot
                   set business_kind=?, target_code=?, version_no=?, step_ids=?, confirmed=?
                 where snapshot_id=?
                """)) {
            statement.setString(1, snapshot.kind().name());
            statement.setString(2, snapshot.targetCode());
            statement.setInt(3, snapshot.version());
            statement.setString(4, join(snapshot.stepIds()));
            statement.setBoolean(5, snapshot.confirmed());
            statement.setString(6, snapshot.snapshotId());
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, RouteSnapshot snapshot) throws SQLException {
        statement.setString(1, snapshot.snapshotId());
        statement.setString(2, snapshot.kind().name());
        statement.setString(3, snapshot.targetCode());
        statement.setInt(4, snapshot.version());
        statement.setString(5, join(snapshot.stepIds()));
        statement.setBoolean(6, snapshot.confirmed());
    }

    private RouteSnapshot map(ResultSet rows) throws SQLException {
        return new RouteSnapshot(
                rows.getString("snapshot_id"),
                BusinessKind.valueOf(rows.getString("business_kind")),
                rows.getString("target_code"),
                rows.getInt("version_no"),
                split(rows.getString("step_ids")),
                rows.getBoolean("confirmed"));
    }

    private static String join(List<String> steps) {
        for (String step : steps) {
            if (step == null || step.isBlank() || step.indexOf(',') >= 0) {
                throw new BusinessException("工序编号无效");
            }
        }
        return String.join(",", steps);
    }

    private static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(",", -1)).toList();
    }
}
