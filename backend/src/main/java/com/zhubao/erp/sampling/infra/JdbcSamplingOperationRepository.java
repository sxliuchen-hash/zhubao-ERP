package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingMakingChoice;
import com.zhubao.erp.sampling.domain.SamplingOperationReport;
import com.zhubao.erp.sampling.domain.SamplingOperationRepository;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-operation.repository", havingValue = "jdbc")
public class JdbcSamplingOperationRepository implements SamplingOperationRepository {

    private final DataSource dataSource;

    public JdbcSamplingOperationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(SamplingOperationReport report, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        if (photoUsed(connection, report.attachmentCode())) {
            throw new BusinessException("这张照片已经用于其他业务，不能当作本次拍摄");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into smp_sampling_operation(
                    report_id, sampling_code, step_id, version_kind, version_no, making_choice,
                    quantity, unit_name, attachment_code, worker_name, reported_on)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, report.reportId());
            statement.setString(2, report.samplingCode());
            statement.setString(3, report.stepId());
            statement.setString(4, report.versionKind().name());
            statement.setInt(5, report.versionNo());
            if (report.makingChoice() == null) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, report.makingChoice().name());
            }
            statement.setBigDecimal(7, report.quantity());
            statement.setString(8, report.unit());
            statement.setString(9, report.attachmentCode());
            statement.setString(10, report.workerName());
            statement.setString(11, report.reportedOn());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("这张照片已经用于其他业务，不能当作本次拍摄");
            }
            throw new IllegalStateException("无法保存打样报工", ex);
        }
    }

    @Override
    public Optional<SamplingOperationReport> find(String reportId) {
        if (reportId == null || reportId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where report_id=?")) {
            statement.setString(1, reportId.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样报工", ex);
        }
    }

    @Override
    public boolean photoUsed(String attachmentCode, WorkSession session) {
        Connection connection = session == null ? null : WorkSessionBindings.requiredJdbc(session);
        if (connection == null) {
            try (Connection own = dataSource.getConnection()) {
                return photoUsed(own, attachmentCode);
            } catch (SQLException ex) {
                throw new IllegalStateException("无法核对打样照片", ex);
            }
        }
        return photoUsed(connection, attachmentCode);
    }

    @Override
    public List<SamplingOperationReport> list(String samplingCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where sampling_code=? order by report_id")) {
            statement.setString(1, samplingCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<SamplingOperationReport> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样报工", ex);
        }
    }

    private boolean photoUsed(Connection connection, String attachmentCode) {
        if (attachmentCode == null || attachmentCode.isBlank()) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "select report_id from smp_sampling_operation where attachment_code=? for update")) {
            statement.setString(1, attachmentCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核对打样照片", ex);
        }
    }

    private static SamplingOperationReport map(ResultSet rows) throws SQLException {
        String choice = rows.getString("making_choice");
        return new SamplingOperationReport(
                rows.getString("report_id"),
                rows.getString("sampling_code"),
                rows.getString("step_id"),
                SamplingVersionKind.valueOf(rows.getString("version_kind")),
                rows.getInt("version_no"),
                choice == null ? null : SamplingMakingChoice.valueOf(choice),
                rows.getBigDecimal("quantity"),
                rows.getString("unit_name"),
                rows.getString("attachment_code"),
                rows.getString("worker_name"),
                rows.getString("reported_on")
        );
    }

    private static String selectSql() {
        return """
                select report_id, sampling_code, step_id, version_kind, version_no, making_choice,
                       quantity, unit_name, attachment_code, worker_name, reported_on
                  from smp_sampling_operation
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
