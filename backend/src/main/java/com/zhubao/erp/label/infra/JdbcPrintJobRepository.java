package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobRepository;
import com.zhubao.erp.label.domain.PrintJobStatus;
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
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "jdbc")
public class JdbcPrintJobRepository implements PrintJobRepository {

    private final DataSource dataSource;

    public JdbcPrintJobRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PrintJob> findById(String id) {
        return queryOne("select * from biz_print_job where id = ?", id);
    }

    @Override
    public Optional<PrintJob> findByRequestId(String requestId) {
        return queryOne("select * from biz_print_job where request_id = ?", requestId);
    }

    @Override
    public PrintJob save(PrintJob job) {
        String sql = """
                insert into biz_print_job (
                    id, request_id, business_code, template_code, copies, content_snapshot, status_code,
                    station_id, lease_until, failure_reason, predecessor_id, retry_reason, created_by, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    status_code = values(status_code),
                    station_id = values(station_id),
                    lease_until = values(lease_until),
                    failure_reason = values(failure_reason),
                    predecessor_id = values(predecessor_id),
                    retry_reason = values(retry_reason),
                    updated_at = values(updated_at)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, job);
            statement.executeUpdate();
            return job;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存打印任务", ex);
        }
    }

    @Override
    public Optional<PrintJob> claim(String stationId, String leaseUntil, String now) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String select = "select * from biz_print_job where status_code = 'PENDING' order by created_at limit 1 for update";
                PrintJob pending;
                try (PreparedStatement statement = connection.prepareStatement(select);
                     ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        return Optional.empty();
                    }
                    pending = map(rs);
                }
                PrintJob leased = new PrintJob(
                        pending.id(), pending.requestId(), pending.businessCode(), pending.templateCode(), pending.copies(),
                        pending.contentSnapshot(), PrintJobStatus.LEASED, stationId, leaseUntil, "", pending.predecessorId(),
                        pending.retryReason(), pending.createdBy(), pending.createdAt(), now
                );
                try (PreparedStatement update = connection.prepareStatement("""
                        update biz_print_job
                           set status_code = 'LEASED', station_id = ?, lease_until = ?, updated_at = ?
                         where id = ? and status_code = 'PENDING'
                        """)) {
                    update.setString(1, stationId);
                    update.setString(2, leaseUntil);
                    update.setString(3, now);
                    update.setString(4, pending.id());
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                connection.commit();
                return Optional.of(leased);
            } catch (RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previous);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法领取打印任务", ex);
        }
    }

    @Override
    public List<PrintJob> list() {
        String sql = "select * from biz_print_job order by created_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PrintJob> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(map(rs));
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法列出打印任务", ex);
        }
    }

    private Optional<PrintJob> queryOne(String sql, String parameter) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打印任务", ex);
        }
    }

    private void bind(PreparedStatement statement, PrintJob job) throws SQLException {
        statement.setString(1, job.id());
        statement.setString(2, job.requestId());
        statement.setString(3, job.businessCode());
        statement.setString(4, job.templateCode());
        statement.setInt(5, job.copies());
        statement.setString(6, job.contentSnapshot());
        statement.setString(7, job.status().name());
        statement.setString(8, job.stationId());
        statement.setString(9, job.leaseUntil());
        statement.setString(10, job.failureReason());
        statement.setString(11, job.predecessorId());
        statement.setString(12, job.retryReason());
        statement.setString(13, job.createdBy());
        statement.setString(14, job.createdAt());
        statement.setString(15, job.updatedAt());
    }

    private PrintJob map(ResultSet rs) throws SQLException {
        return new PrintJob(
                rs.getString("id"),
                rs.getString("request_id"),
                rs.getString("business_code"),
                rs.getString("template_code"),
                rs.getInt("copies"),
                rs.getString("content_snapshot"),
                PrintJobStatus.valueOf(rs.getString("status_code")),
                rs.getString("station_id"),
                rs.getString("lease_until"),
                rs.getString("failure_reason"),
                rs.getString("predecessor_id"),
                rs.getString("retry_reason"),
                rs.getString("created_by"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }
}
