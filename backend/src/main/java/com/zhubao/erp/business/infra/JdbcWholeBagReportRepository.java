package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.WholeBagReport;
import com.zhubao.erp.business.domain.WholeBagReportRepository;
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
@ConditionalOnProperty(name = "zhubao.whole-bag-report.repository", havingValue = "jdbc")
public class JdbcWholeBagReportRepository implements WholeBagReportRepository {

    private final DataSource dataSource;

    public JdbcWholeBagReportRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(WholeBagReport report, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_whole_bag_report(
                    batch_code, request_id, bag_id, bag_version, target_code, step_id, quantity,
                    attachment_code, operator_name, progress_percent, created_at)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, report.batchCode());
            statement.setString(2, report.requestId());
            statement.setString(3, report.bagId());
            statement.setInt(4, report.bagVersion());
            statement.setString(5, report.targetCode());
            statement.setString(6, report.stepId());
            statement.setBigDecimal(7, report.quantity());
            statement.setString(8, report.attachmentCode());
            statement.setString(9, report.operatorName());
            if (report.progressPercent() == null) {
                statement.setNull(10, Types.INTEGER);
            } else {
                statement.setInt(10, report.progressPercent());
            }
            statement.setString(11, report.createdAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存整袋报工", ex);
        }
    }

    @Override
    public Optional<WholeBagReport> findByRequest(String requestId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_whole_bag_report where request_id=?")) {
            statement.setString(1, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取整袋报工", ex);
        }
    }

    private WholeBagReport map(ResultSet rows) throws SQLException {
        int progress = rows.getInt("progress_percent");
        Integer progressPercent = rows.wasNull() ? null : progress;
        BigDecimal quantity = rows.getBigDecimal("quantity");
        return new WholeBagReport(
                rows.getString("batch_code"),
                rows.getString("request_id"),
                rows.getString("bag_id"),
                rows.getInt("bag_version"),
                rows.getString("target_code"),
                rows.getString("step_id"),
                quantity,
                rows.getString("attachment_code"),
                rows.getString("operator_name"),
                progressPercent,
                rows.getString("created_at"));
    }
}
