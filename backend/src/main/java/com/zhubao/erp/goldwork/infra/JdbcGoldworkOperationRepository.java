package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkOperationReport;
import com.zhubao.erp.goldwork.domain.GoldworkOperationRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-operation.repository", havingValue = "jdbc")
public class JdbcGoldworkOperationRepository implements GoldworkOperationRepository {

    private final DataSource dataSource;

    public JdbcGoldworkOperationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(GoldworkOperationReport report, WorkSession session) {
        if (photoUsed(report.attachmentCode(), session)) {
            throw new BusinessException("这张照片已经用于其他业务，不能当作本次拍摄");
        }
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into gld_goldwork_operation(
                    report_id, goldwork_code, step_id, piece_count, net_weight, weight_unit, purity_text,
                    attachment_code, worker_name, reported_on, precision_state)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, report.reportId());
            statement.setString(2, report.goldworkCode());
            statement.setString(3, report.stepId());
            setDecimal(statement, 4, report.pieceCount());
            setDecimal(statement, 5, report.netWeight());
            setText(statement, 6, report.weightUnit());
            setText(statement, 7, report.purity());
            statement.setString(8, report.attachmentCode());
            statement.setString(9, report.workerName());
            statement.setString(10, report.reportedOn());
            statement.setString(11, report.precisionState());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存金工报工", ex);
        }
    }

    @Override
    public Optional<GoldworkOperationReport> find(String reportId) {
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
            throw new IllegalStateException("无法读取金工报工", ex);
        }
    }

    @Override
    public boolean photoUsed(String attachmentCode, WorkSession session) {
        Connection connection = session == null ? null : WorkSessionBindings.requiredJdbc(session);
        if (connection == null) {
            try (Connection own = dataSource.getConnection()) {
                return photoUsed(own, attachmentCode);
            } catch (SQLException ex) {
                throw new IllegalStateException("无法核对金工照片", ex);
            }
        }
        return photoUsed(connection, attachmentCode);
    }

    @Override
    public List<GoldworkOperationReport> list(String goldworkCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     selectSql() + " where goldwork_code=? order by report_id")) {
            statement.setString(1, goldworkCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<GoldworkOperationReport> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取金工报工", ex);
        }
    }

    private boolean photoUsed(Connection connection, String attachmentCode) {
        if (attachmentCode == null || attachmentCode.isBlank()) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "select report_id from gld_goldwork_operation where attachment_code=? for update")) {
            statement.setString(1, attachmentCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核对金工照片", ex);
        }
    }

    private static GoldworkOperationReport map(ResultSet rows) throws SQLException {
        return new GoldworkOperationReport(
                rows.getString("report_id"),
                rows.getString("goldwork_code"),
                rows.getString("step_id"),
                rows.getBigDecimal("piece_count"),
                rows.getBigDecimal("net_weight"),
                rows.getString("weight_unit"),
                rows.getString("purity_text"),
                rows.getString("attachment_code"),
                rows.getString("worker_name"),
                rows.getString("reported_on"),
                rows.getString("precision_state")
        );
    }

    private static void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
            return;
        }
        statement.setBigDecimal(index, value);
    }

    private static void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private static String selectSql() {
        return """
                select report_id, goldwork_code, step_id, piece_count, net_weight, weight_unit, purity_text,
                       attachment_code, worker_name, reported_on, precision_state
                  from gld_goldwork_operation
                """;
    }
}
