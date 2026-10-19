package com.zhubao.erp.label.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintRecycle;
import com.zhubao.erp.label.domain.PrintJobRepository;
import com.zhubao.erp.label.domain.PrintJobStatus;
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
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "jdbc")
public class JdbcPrintJobRepository implements PrintJobRepository {

    private final DataSource dataSource;

    public JdbcPrintJobRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PrintJob> findById(String id) {
        return queryOne("select * from biz_print_job where id = ? and recycled_at is null", id);
    }

    @Override
    public Optional<PrintJob> findByRequestId(String requestId) {
        return queryOne("select * from biz_print_job where request_id = ?", requestId);
    }

    @Override
    public PrintJob save(PrintJob job) {
        String sql = """
                update biz_print_job
                   set status_code = ?, station_id = ?, lease_until = ?, failure_reason = ?,
                       predecessor_id = ?, retry_reason = ?, content_snapshot = ?, updated_at = ?
                 where id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, job.status().name());
            statement.setString(2, job.stationId());
            statement.setString(3, job.leaseUntil());
            statement.setString(4, job.failureReason());
            statement.setString(5, job.predecessorId());
            statement.setString(6, job.retryReason());
            statement.setString(7, job.contentSnapshot());
            statement.setString(8, job.updatedAt());
            statement.setString(9, job.id());
            if (statement.executeUpdate() == 0) {
                return insertIfAbsent(job);
            }
            return findById(job.id()).orElse(job);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存打印任务", ex);
        }
    }

    @Override
    public PrintJob insertIfAbsent(PrintJob job) {
        String sql = """
                insert into biz_print_job (
                    id, request_id, business_code, template_code, copies, content_snapshot, status_code,
                    station_id, lease_until, failure_reason, predecessor_id, retry_reason, created_by, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, job);
            statement.executeUpdate();
            return job;
        } catch (SQLException ex) {
            if (isDuplicate(ex)) {
                return findByRequestId(job.requestId()).orElseThrow(() -> new IllegalStateException("无法读取已有打印任务", ex));
            }
            throw new IllegalStateException("无法保存打印任务", ex);
        }
    }

    @Override
    public Optional<PrintJob> claim(String stationId, String leaseUntil, String now) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String select = "select * from biz_print_job where status_code = 'PENDING' and recycled_at is null order by created_at limit 1 for update";
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
        String sql = "select * from biz_print_job where recycled_at is null order by created_at";
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

    private boolean isDuplicate(SQLException ex) {
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }

    @Override
    public Optional<PrintJob> findAny(String id) {
        return queryOne("select * from biz_print_job where id = ?", id);
    }

    @Override
    public List<PrintJob> listByBusinessAny(String businessCode) {
        String sql = "select * from biz_print_job where business_code = ? order by created_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, businessCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<PrintJob> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法列出打印任务", ex);
        }
    }

    @Override
    public void recycle(String id, String operator, String at, String batch, String reason) {
        if (findAny(id).isEmpty()) {
            throw new BusinessException("打印任务不存在");
        }
        updateRecycle("update biz_print_job set recycled_at = ?, recycled_by = ?, recycle_batch = ?, recycle_reason = ? "
                + "where id = ? and recycled_at is null", operator, at, batch, reason, id, "打印任务已在回收站");
    }

    @Override
    public int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason) {
        String sql = "update biz_print_job set recycled_at = ?, recycled_by = ?, recycle_batch = ?, recycle_reason = ? "
                + "where business_code = ? and recycled_at is null";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, at);
            statement.setString(2, operator);
            statement.setString(3, batch);
            statement.setString(4, reason);
            statement.setString(5, businessCode);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("回收打印任务失败", ex);
        }
    }

    @Override
    public int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason,
                                     WorkSession session) {
        String sql = "update biz_print_job set recycled_at = ?, recycled_by = ?, recycle_batch = ?, recycle_reason = ? "
                + "where business_code = ? and recycled_at is null";
        try (PreparedStatement statement = session.connection().orElseThrow().prepareStatement(sql)) {
            statement.setString(1, at);
            statement.setString(2, operator);
            statement.setString(3, batch);
            statement.setString(4, reason);
            statement.setString(5, businessCode);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("回收打印任务失败", ex);
        }
    }

    @Override
    public void restore(String id) {
        updateNullable("update biz_print_job set recycled_at = null, recycled_by = null, recycle_batch = null, recycle_reason = null "
                + "where id = ? and recycled_at is not null", id, "打印任务不在回收站");
    }

    @Override
    public void restoreBatch(String batch) {
        String sql = "update biz_print_job set recycled_at = null, recycled_by = null, recycle_batch = null, recycle_reason = null "
                + "where recycle_batch = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, batch);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("恢复打印任务失败", ex);
        }
    }

    @Override
    public void restoreBatch(String batch, WorkSession session) {
        String sql = "update biz_print_job set recycled_at = null, recycled_by = null, recycle_batch = null, "
                + "recycle_reason = null where recycle_batch = ?";
        try (PreparedStatement statement = session.connection().orElseThrow().prepareStatement(sql)) {
            statement.setString(1, batch);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("恢复打印任务失败", ex);
        }
    }

    @Override
    public Optional<PrintRecycle> recycleMark(String id) {
        String sql = "select id, business_code, recycled_at, recycled_by, recycle_batch, recycle_reason from biz_print_job "
                + "where id = ? and recycled_at is not null";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRecycle(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取打印回收站失败", ex);
        }
    }

    @Override
    public List<PrintRecycle> recycled() {
        String sql = "select id, business_code, recycled_at, recycled_by, recycle_batch, recycle_reason from biz_print_job "
                + "where recycled_at is not null order by recycled_at desc";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PrintRecycle> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapRecycle(rs));
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("列出打印回收站失败", ex);
        }
    }

    @Override
    public Optional<PrintJob> lockAny(String id, WorkSession session) {
        try (PreparedStatement statement = session.connection().orElseThrow().prepareStatement(
                "select * from biz_print_job where id = ? for update")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("锁定打印任务失败", ex);
        }
    }

    @Override
    public PrintJob replaceCopies(String id, int copies, String stationId, String updatedAt, WorkSession session) {
        String sql = "update biz_print_job set copies = ?, station_id = ?, updated_at = ? "
                + "where id = ? and status_code = 'PENDING' and recycled_at is null";
        try (PreparedStatement statement = session.connection().orElseThrow().prepareStatement(sql)) {
            statement.setInt(1, copies);
            statement.setString(2, stationId);
            statement.setString(3, updatedAt);
            statement.setString(4, id);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("任务已领取、状态不明或已回收，不能直接改单");
            }
            return lockAny(id, session).orElseThrow(() -> new BusinessException("打印任务不存在"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new IllegalStateException("修改打印任务失败", ex);
        }
    }

    private void updateRecycle(String sql, String operator, String at, String batch, String reason, String id, String missing) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, at);
            statement.setString(2, operator);
            statement.setString(3, batch);
            statement.setString(4, reason);
            statement.setString(5, id);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException(missing);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new IllegalStateException(missing, ex);
        }
    }

    private void updateNullable(String sql, String id, String missing) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException(missing);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new IllegalStateException(missing, ex);
        }
    }

    private PrintRecycle mapRecycle(ResultSet rs) throws SQLException {
        return new PrintRecycle(rs.getString("id"), rs.getString("business_code"), rs.getString("recycled_at"),
                rs.getString("recycled_by"), rs.getString("recycle_batch"), rs.getString("recycle_reason"));
    }
}
