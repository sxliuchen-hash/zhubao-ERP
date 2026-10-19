package com.zhubao.erp.label.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintRecycle;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintRoutingRepository;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTargetJob;
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
public class JdbcPrintRoutingRepository implements PrintRoutingRepository {
    private final DataSource dataSource;
    public JdbcPrintRoutingRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override public List<PrintStation> stations() {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "select * from biz_print_station order by station_id"); ResultSet r = s.executeQuery()) {
            List<PrintStation> rows = new ArrayList<>();
            while (r.next()) rows.add(mapStation(r));
            return rows;
        } catch (SQLException ex) { throw failed("读取工位失败", ex); }
    }
    @Override public Optional<PrintStation> station(String id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "select * from biz_print_station where station_id = ?")) {
            s.setString(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapStation(r)) : Optional.empty(); }
        } catch (SQLException ex) { throw failed("读取工位失败", ex); }
    }
    @Override public PrintStation saveStation(PrintStation station) {
        String sql = "insert into biz_print_station (station_id, station_name, enabled, remark, token_hash) values (?, ?, ?, ?, ?) "
                + "on duplicate key update station_name = values(station_name), enabled = values(enabled), "
                + "remark = values(remark), token_hash = values(token_hash)";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, station.stationId()); s.setString(2, station.name()); s.setBoolean(3, station.enabled());
            s.setString(4, station.remark()); s.setString(5, station.tokenHash()); s.executeUpdate();
            return station;
        } catch (SQLException ex) { throw failed("保存工位失败", ex); }
    }
    @Override public List<PrintQueue> queues() {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "select * from biz_print_queue order by station_id, queue_id"); ResultSet r = s.executeQuery()) {
            List<PrintQueue> rows = new ArrayList<>();
            while (r.next()) rows.add(mapQueue(r));
            return rows;
        } catch (SQLException ex) { throw failed("读取打印队列失败", ex); }
    }
    @Override public PrintQueue saveQueue(PrintQueue queue) {
        String sql = "insert into biz_print_queue (station_id, queue_id, queue_name, label_type, width_mm, height_mm, enabled, default_queue) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) on duplicate key update queue_name = values(queue_name), "
                + "label_type = values(label_type), width_mm = values(width_mm), height_mm = values(height_mm), "
                + "enabled = values(enabled), default_queue = values(default_queue)";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, queue.stationId()); s.setString(2, queue.queueId()); s.setString(3, queue.name());
            s.setString(4, queue.labelType()); s.setInt(5, queue.widthMm()); s.setInt(6, queue.heightMm());
            s.setBoolean(7, queue.enabled()); s.setBoolean(8, queue.defaultQueue()); s.executeUpdate();
            return queue;
        } catch (SQLException ex) { throw failed("保存打印队列失败", ex); }
    }
    @Override public Optional<PrintBatch> batchByRequest(String requestId) { return findBatch("request_id", requestId); }
    @Override public Optional<PrintBatch> batch(String batchId) { return findBatch("id", batchId); }
    private Optional<PrintBatch> findBatch(String column, String value) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "select * from biz_print_batch where " + column + " = ?")) {
            s.setString(1, value);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Optional.empty();
                PrintBatch batch = new PrintBatch(r.getString("id"), r.getString("request_id"), r.getString("business_code"),
                        r.getString("template_code"), r.getInt("copies_per_target"), r.getString("payload_hash"),
                        r.getString("content_snapshot"), r.getString("created_by"), r.getString("created_at"), List.of());
                return Optional.of(batch.withJobs(jobsForBatch(c, batch.id())));
            }
        } catch (SQLException ex) { throw failed("读取打印批次失败", ex); }
    }
    @Override public PrintBatch insertBatch(PrintBatch batch) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                insertBatch(c, batch);
                c.commit();
                return batch;
            } catch (SQLException ex) {
                c.rollback();
                if (duplicate(ex)) return batchByRequest(batch.requestId()).orElseThrow(() -> failed("无法读回打印批次", ex));
                throw ex;
            }
        } catch (SQLException ex) { throw failed("创建打印批次失败", ex); }
    }
    @Override public Optional<PrintBatch> rerouteJob(String originalId, PrintJobStatus expected,
                                                       PrintBatch replacement, String reason, String now) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement("update biz_print_target_job set status_code = 'CANCELED', "
                        + "retry_reason = ?, updated_at = ? where id = ? and status_code = ? "
                        + "and (status_code = 'PENDING' or paper_check_result = '确认未出纸')")) {
                    s.setString(1, reason); s.setString(2, now); s.setString(3, originalId);
                    s.setString(4, expected.name());
                    if (s.executeUpdate() != 1) { c.rollback(); return Optional.empty(); }
                }
                insertBatch(c, replacement);
                c.commit();
                return Optional.of(replacement);
            } catch (SQLException ex) {
                c.rollback();
                if (duplicate(ex)) {
                    Optional<PrintBatch> existing = batchByRequest(replacement.requestId());
                    if (existing.isPresent()) return existing;
                }
                throw ex;
            }
        } catch (SQLException ex) { throw failed("改派打印目标失败", ex); }
    }
    private void insertBatch(Connection c, PrintBatch batch) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("insert into biz_print_batch "
                + "(id, request_id, business_code, template_code, copies_per_target, payload_hash, "
                + "content_snapshot, created_by, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            s.setString(1, batch.id()); s.setString(2, batch.requestId()); s.setString(3, batch.businessCode());
            s.setString(4, batch.templateCode()); s.setInt(5, batch.copiesPerTarget());
            s.setString(6, batch.payloadHash()); s.setString(7, batch.contentSnapshot());
            s.setString(8, batch.createdBy()); s.setString(9, batch.createdAt()); s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement("insert into biz_print_target_job "
                + "(id, batch_id, target_station_id, target_queue_id, copies, status_code, claimed_station_id, "
                + "lease_until, failure_reason, paper_check_result, predecessor_id, retry_reason, created_at, updated_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (PrintTargetJob job : batch.jobs()) { bindJob(s, job); s.addBatch(); }
            s.executeBatch();
        }
    }
    @Override public Optional<PrintTargetJob> job(String id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "select * from biz_print_target_job where id = ? and recycled_at is null")) {
            s.setString(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapJob(r)) : Optional.empty(); }
        } catch (SQLException ex) { throw failed("读取目标任务失败", ex); }
    }
    @Override public Optional<PrintTargetJob> claim(String stationId, String leaseUntil, String now) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PrintTargetJob pending;
                try (PreparedStatement s = c.prepareStatement("select * from biz_print_target_job where target_station_id = ? "
                        + "and status_code = 'PENDING' and recycled_at is null order by created_at, id limit 1 for update")) {
                    s.setString(1, stationId);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) { c.rollback(); return Optional.empty(); }
                        pending = mapJob(r);
                    }
                }
                PrintTargetJob leased = pending.withStatus(PrintJobStatus.LEASED, stationId, leaseUntil, "", "", now);
                updateJob(c, leased);
                c.commit();
                return Optional.of(leased);
            } catch (SQLException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { throw failed("领取目标任务失败", ex); }
    }
    @Override public PrintTargetJob saveJob(PrintTargetJob job) {
        try (Connection c = dataSource.getConnection()) { updateJob(c, job); return job; }
        catch (SQLException ex) { throw failed("更新目标任务失败", ex); }
    }
    @Override public Optional<PrintTargetJob> saveJobIfStatus(PrintTargetJob job, PrintJobStatus expected) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "update biz_print_target_job set status_code = ?, claimed_station_id = ?, lease_until = ?, "
                        + "failure_reason = ?, paper_check_result = ?, updated_at = ? where id = ? and status_code = ?")) {
            s.setString(1, job.status().name()); s.setString(2, job.claimedStationId());
            s.setString(3, job.leaseUntil()); s.setString(4, job.failureReason());
            s.setString(5, job.paperCheckResult()); s.setString(6, job.updatedAt());
            s.setString(7, job.id()); s.setString(8, expected.name());
            return s.executeUpdate() == 1 ? Optional.of(job) : Optional.empty();
        } catch (SQLException ex) { throw failed("更新目标任务失败", ex); }
    }
    @Override public Optional<PrintTargetJob> savePaperCheckIfBlank(PrintTargetJob job, PrintJobStatus expected) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "update biz_print_target_job set paper_check_result = ?, updated_at = ? "
                        + "where id = ? and status_code = ? and paper_check_result = ''")) {
            s.setString(1, job.paperCheckResult()); s.setString(2, job.updatedAt());
            s.setString(3, job.id()); s.setString(4, expected.name());
            return s.executeUpdate() == 1 ? Optional.of(job) : Optional.empty();
        } catch (SQLException ex) { throw failed("记录出纸核实失败", ex); }
    }
    @Override public int expireLeases(String now) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "update biz_print_target_job set status_code = 'UNCERTAIN', "
                        + "failure_reason = '租约到期，出纸状态不确定', updated_at = ? "
                        + "where status_code = 'LEASED' and lease_until < ?")) {
            s.setString(1, now); s.setString(2, now);
            return s.executeUpdate();
        } catch (SQLException ex) { throw failed("检查打印租约失败", ex); }
    }
    private void updateJob(Connection c, PrintTargetJob job) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("update biz_print_target_job set status_code = ?, "
                + "claimed_station_id = ?, lease_until = ?, failure_reason = ?, paper_check_result = ?, updated_at = ? where id = ?")) {
            s.setString(1, job.status().name()); s.setString(2, job.claimedStationId()); s.setString(3, job.leaseUntil());
            s.setString(4, job.failureReason()); s.setString(5, job.paperCheckResult()); s.setString(6, job.updatedAt());
            s.setString(7, job.id());
            if (s.executeUpdate() != 1) throw new SQLException("打印目标任务不存在");
        }
    }
    private List<PrintTargetJob> jobsForBatch(Connection c, String id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("select * from biz_print_target_job where batch_id = ? and recycled_at is null order by id")) {
            s.setString(1, id);
            try (ResultSet r = s.executeQuery()) {
                List<PrintTargetJob> rows = new ArrayList<>();
                while (r.next()) rows.add(mapJob(r));
                return rows;
            }
        }
    }
    private void bindJob(PreparedStatement s, PrintTargetJob job) throws SQLException {
        s.setString(1, job.id()); s.setString(2, job.batchId()); s.setString(3, job.targetStationId());
        s.setString(4, job.targetQueueId()); s.setInt(5, job.copies()); s.setString(6, job.status().name());
        s.setString(7, job.claimedStationId()); s.setString(8, job.leaseUntil()); s.setString(9, job.failureReason());
        s.setString(10, job.paperCheckResult()); s.setString(11, job.predecessorId()); s.setString(12, job.retryReason());
        s.setString(13, job.createdAt()); s.setString(14, job.updatedAt());
    }
    private PrintStation mapStation(ResultSet r) throws SQLException { return new PrintStation(r.getString("station_id"),
            r.getString("station_name"), r.getBoolean("enabled"), r.getString("remark"), r.getString("token_hash")); }
    private PrintQueue mapQueue(ResultSet r) throws SQLException { return new PrintQueue(r.getString("station_id"),
            r.getString("queue_id"), r.getString("queue_name"), r.getString("label_type"),
            r.getInt("width_mm"), r.getInt("height_mm"), r.getBoolean("enabled"), r.getBoolean("default_queue")); }
    private PrintTargetJob mapJob(ResultSet r) throws SQLException { return new PrintTargetJob(r.getString("id"),
            r.getString("batch_id"), r.getString("target_station_id"), r.getString("target_queue_id"),
            r.getInt("copies"), PrintJobStatus.valueOf(r.getString("status_code")), r.getString("claimed_station_id"),
            r.getString("lease_until"), r.getString("failure_reason"), r.getString("paper_check_result"),
            r.getString("predecessor_id"), r.getString("retry_reason"), r.getString("created_at"), r.getString("updated_at")); }
    private boolean duplicate(SQLException ex) { return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
            || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique")); }
    private IllegalStateException failed(String message, SQLException ex) { return new IllegalStateException(message, ex); }

    @Override public Optional<PrintTargetJob> jobAny(String id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "select * from biz_print_target_job where id = ?")) {
            s.setString(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapJob(r)) : Optional.empty(); }
        } catch (SQLException ex) { throw failed("读取目标任务失败", ex); }
    }
    @Override public Optional<PrintTargetJob> lockJobAny(String id, WorkSession session) {
        try (PreparedStatement s = session.connection().orElseThrow().prepareStatement(
                "select * from biz_print_target_job where id = ? for update")) {
            s.setString(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapJob(r)) : Optional.empty(); }
        } catch (SQLException ex) { throw failed("锁定目标任务失败", ex); }
    }
    @Override public List<PrintTargetJob> jobsForBusinessAny(String businessCode) {
        String sql = "select j.* from biz_print_target_job j join biz_print_batch b on b.id = j.batch_id "
                + "where b.business_code = ? order by j.id";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, businessCode);
            try (ResultSet r = s.executeQuery()) {
                List<PrintTargetJob> rows = new ArrayList<>();
                while (r.next()) rows.add(mapJob(r));
                return rows;
            }
        } catch (SQLException ex) { throw failed("读取目标任务失败", ex); }
    }
    @Override public void recycleJob(String id, String operator, String at, String batch, String reason) {
        if (jobAny(id).isEmpty()) throw new BusinessException("打印任务不存在");
        String sql = "update biz_print_target_job set recycled_at = ?, recycled_by = ?, recycle_batch = ?, recycle_reason = ? "
                + "where id = ? and recycled_at is null";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, at); s.setString(2, operator); s.setString(3, batch); s.setString(4, reason); s.setString(5, id);
            if (s.executeUpdate() != 1) throw new BusinessException("打印任务已在回收站");
        } catch (BusinessException ex) { throw ex; }
        catch (SQLException ex) { throw failed("回收打印任务失败", ex); }
    }
    @Override public int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason) {
        String sql = "update biz_print_target_job set recycled_at = ?, recycled_by = ?, recycle_batch = ?, recycle_reason = ? "
                + "where recycled_at is null and batch_id in (select id from biz_print_batch where business_code = ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, at); s.setString(2, operator); s.setString(3, batch); s.setString(4, reason); s.setString(5, businessCode);
            return s.executeUpdate();
        } catch (SQLException ex) { throw failed("回收打印任务失败", ex); }
    }
    @Override public int recycleByBusinessCode(String businessCode, String operator, String at, String batch,
                                                String reason, WorkSession session) {
        String sql = "update biz_print_target_job set recycled_at = ?, recycled_by = ?, recycle_batch = ?, recycle_reason = ? "
                + "where recycled_at is null and batch_id in (select id from biz_print_batch where business_code = ?)";
        try (PreparedStatement s = session.connection().orElseThrow().prepareStatement(sql)) {
            s.setString(1, at); s.setString(2, operator); s.setString(3, batch); s.setString(4, reason);
            s.setString(5, businessCode);
            return s.executeUpdate();
        } catch (SQLException ex) { throw failed("回收打印任务失败", ex); }
    }
    @Override public void restoreJob(String id) {
        String sql = "update biz_print_target_job set recycled_at = null, recycled_by = null, recycle_batch = null, "
                + "recycle_reason = null where id = ? and recycled_at is not null";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, id);
            if (s.executeUpdate() != 1) throw new BusinessException("打印任务不在回收站");
        } catch (BusinessException ex) { throw ex; }
        catch (SQLException ex) { throw failed("恢复打印任务失败", ex); }
    }
    @Override public void restoreBatch(String batch) {
        String sql = "update biz_print_target_job set recycled_at = null, recycled_by = null, recycle_batch = null, "
                + "recycle_reason = null where recycle_batch = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, batch);
            s.executeUpdate();
        } catch (SQLException ex) { throw failed("恢复打印任务失败", ex); }
    }
    @Override public void restoreBatch(String batch, WorkSession session) {
        String sql = "update biz_print_target_job set recycled_at = null, recycled_by = null, recycle_batch = null, "
                + "recycle_reason = null where recycle_batch = ?";
        try (PreparedStatement s = session.connection().orElseThrow().prepareStatement(sql)) {
            s.setString(1, batch);
            s.executeUpdate();
        } catch (SQLException ex) { throw failed("恢复打印任务失败", ex); }
    }
    @Override public Optional<PrintRecycle> jobRecycle(String id) {
        String sql = "select j.id, b.business_code, j.recycled_at, j.recycled_by, j.recycle_batch, j.recycle_reason "
                + "from biz_print_target_job j join biz_print_batch b on b.id = j.batch_id "
                + "where j.id = ? and j.recycled_at is not null";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapRecycle(r)) : Optional.empty(); }
        } catch (SQLException ex) { throw failed("读取打印回收站失败", ex); }
    }
    @Override public List<PrintRecycle> recycledJobs() {
        String sql = "select j.id, b.business_code, j.recycled_at, j.recycled_by, j.recycle_batch, j.recycle_reason "
                + "from biz_print_target_job j join biz_print_batch b on b.id = j.batch_id "
                + "where j.recycled_at is not null order by j.recycled_at desc";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            List<PrintRecycle> rows = new ArrayList<>();
            while (r.next()) rows.add(mapRecycle(r));
            return rows;
        } catch (SQLException ex) { throw failed("列出打印回收站失败", ex); }
    }
    @Override public PrintTargetJob replaceTarget(String id, int copies, String stationId, String queueId,
                                                   String updatedAt, WorkSession session) {
        String sql = "update biz_print_target_job set copies = ?, target_station_id = ?, target_queue_id = ?, "
                + "updated_at = ? where id = ? and status_code = 'PENDING' and recycled_at is null";
        try (PreparedStatement s = session.connection().orElseThrow().prepareStatement(sql)) {
            s.setInt(1, copies); s.setString(2, stationId); s.setString(3, queueId);
            s.setString(4, updatedAt); s.setString(5, id);
            if (s.executeUpdate() != 1) throw new BusinessException("任务已领取、状态不明或已回收，不能直接改单");
            return lockJobAny(id, session).orElseThrow(() -> new BusinessException("打印任务不存在"));
        } catch (BusinessException ex) { throw ex; }
        catch (SQLException ex) { throw failed("修改打印任务失败", ex); }
    }
    private PrintRecycle mapRecycle(ResultSet r) throws SQLException {
        return new PrintRecycle(r.getString("id"), r.getString("business_code"), r.getString("recycled_at"),
                r.getString("recycled_by"), r.getString("recycle_batch"), r.getString("recycle_reason"));
    }
}
