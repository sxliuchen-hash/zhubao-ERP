package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingVersion;
import com.zhubao.erp.sampling.domain.SamplingVersionDecision;
import com.zhubao.erp.sampling.domain.SamplingVersionDecisionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionRepository;
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
@ConditionalOnProperty(name = "zhubao.sampling-version.repository", havingValue = "jdbc")
public class JdbcSamplingVersionRepository implements SamplingVersionRepository {

    private final DataSource dataSource;

    public JdbcSamplingVersionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(SamplingVersion version, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into smp_sampling_version(
                    sampling_code, version_kind, version_no, parameter_text, evidence_code,
                    model_file_name, model_format, model_version, model_location_note, model_evidence_code,
                    opened_by, opened_on)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, version.samplingCode());
            statement.setString(2, version.kind().name());
            statement.setInt(3, version.versionNo());
            setText(statement, 4, version.parameterText());
            setText(statement, 5, version.evidenceCode());
            setText(statement, 6, version.modelFileName());
            setText(statement, 7, version.modelFormat());
            setText(statement, 8, version.modelVersion());
            setText(statement, 9, version.modelLocationNote());
            setText(statement, 10, version.modelEvidenceCode());
            statement.setString(11, version.openedBy());
            statement.setString(12, version.openedOn());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("版本号冲突，请稍后重试");
            }
            throw new IllegalStateException("无法保存打样版本", ex);
        }
    }

    @Override
    public void insertDecision(SamplingVersionDecision decision, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into smp_sampling_version_decision(
                    sampling_code, version_kind, version_no, decision_code, reason_text, credential_code,
                    decided_by, decided_on)
                values (?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, decision.samplingCode());
            statement.setString(2, decision.kind().name());
            statement.setInt(3, decision.versionNo());
            statement.setString(4, decision.decision().name());
            setText(statement, 5, decision.reason());
            setText(statement, 6, decision.credentialCode());
            statement.setString(7, decision.decidedBy());
            statement.setString(8, decision.decidedOn());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("该版本已经有确认记录");
            }
            throw new IllegalStateException("无法保存打样确认", ex);
        }
    }

    @Override
    public Optional<SamplingVersion> find(String samplingCode, SamplingVersionKind kind, int versionNo) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(versionSql() + " where sampling_code=? and version_kind=? and version_no=?")) {
            statement.setString(1, samplingCode);
            statement.setString(2, kind.name());
            statement.setInt(3, versionNo);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapVersion(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样版本", ex);
        }
    }

    @Override
    public Optional<SamplingVersionDecision> findDecision(String samplingCode, SamplingVersionKind kind, int versionNo) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(decisionSql() + " where sampling_code=? and version_kind=? and version_no=?")) {
            statement.setString(1, samplingCode);
            statement.setString(2, kind.name());
            statement.setInt(3, versionNo);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapDecision(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样确认", ex);
        }
    }

    @Override
    public List<SamplingVersion> list(String samplingCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(versionSql() + " where sampling_code=? order by version_kind, version_no")) {
            statement.setString(1, samplingCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<SamplingVersion> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(mapVersion(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样版本", ex);
        }
    }

    @Override
    public List<SamplingVersionDecision> listDecisions(String samplingCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(decisionSql() + " where sampling_code=?")) {
            statement.setString(1, samplingCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<SamplingVersionDecision> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(mapDecision(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样确认", ex);
        }
    }

    @Override
    public List<SamplingVersion> lockKind(String samplingCode, SamplingVersionKind kind, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                versionSql() + " where sampling_code=? and version_kind=? order by version_no for update")) {
            statement.setString(1, samplingCode);
            statement.setString(2, kind.name());
            try (ResultSet rows = statement.executeQuery()) {
                List<SamplingVersion> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(mapVersion(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定打样版本", ex);
        }
    }

    @Override
    public Optional<SamplingVersion> lockOne(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                versionSql() + " where sampling_code=? and version_kind=? and version_no=? for update")) {
            statement.setString(1, samplingCode);
            statement.setString(2, kind.name());
            statement.setInt(3, versionNo);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapVersion(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定打样版本", ex);
        }
    }

    @Override
    public Optional<SamplingVersionDecision> lockDecision(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                decisionSql() + " where sampling_code=? and version_kind=? and version_no=? for update")) {
            statement.setString(1, samplingCode);
            statement.setString(2, kind.name());
            statement.setInt(3, versionNo);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapDecision(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定打样确认", ex);
        }
    }

    private static SamplingVersion mapVersion(ResultSet rows) throws SQLException {
        return new SamplingVersion(
                rows.getString("sampling_code"),
                SamplingVersionKind.valueOf(rows.getString("version_kind")),
                rows.getInt("version_no"),
                text(rows.getString("parameter_text")),
                text(rows.getString("evidence_code")),
                text(rows.getString("model_file_name")),
                text(rows.getString("model_format")),
                text(rows.getString("model_version")),
                text(rows.getString("model_location_note")),
                text(rows.getString("model_evidence_code")),
                rows.getString("opened_by"),
                rows.getString("opened_on")
        );
    }

    private static SamplingVersionDecision mapDecision(ResultSet rows) throws SQLException {
        return new SamplingVersionDecision(
                rows.getString("sampling_code"),
                SamplingVersionKind.valueOf(rows.getString("version_kind")),
                rows.getInt("version_no"),
                SamplingVersionDecisionKind.valueOf(rows.getString("decision_code")),
                text(rows.getString("reason_text")),
                text(rows.getString("credential_code")),
                rows.getString("decided_by"),
                rows.getString("decided_on")
        );
    }

    private static String versionSql() {
        return """
                select sampling_code, version_kind, version_no, parameter_text, evidence_code,
                       model_file_name, model_format, model_version, model_location_note, model_evidence_code,
                       opened_by, opened_on
                  from smp_sampling_version
                """;
    }

    private static String decisionSql() {
        return """
                select sampling_code, version_kind, version_no, decision_code, reason_text, credential_code,
                       decided_by, decided_on
                  from smp_sampling_version_decision
                """;
    }

    private static void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
