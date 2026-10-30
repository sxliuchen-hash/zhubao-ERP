package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.PortionInspectionRepository;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.portion-inspection.repository", havingValue = "jdbc")
public class JdbcPortionInspectionRepository implements PortionInspectionRepository {

    private final DataSource dataSource;

    public JdbcPortionInspectionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(PortionInspection inspection, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_portion_inspection(
                    inspection_id, request_id, bag_id, requirement_version, pass_qty, rework_qty,
                    scrap_qty, pending_qty, failed_clauses)
                values (?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, inspection.inspectionId());
            statement.setString(2, inspection.requestId());
            statement.setString(3, inspection.bagId());
            statement.setString(4, inspection.requirementVersion());
            statement.setBigDecimal(5, inspection.pass());
            statement.setBigDecimal(6, inspection.rework());
            statement.setBigDecimal(7, inspection.scrap());
            statement.setBigDecimal(8, inspection.pending());
            statement.setString(9, String.join(",", inspection.failedClauses()));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存质量检查", ex);
        }
    }

    @Override
    public List<PortionInspection> listByBag(String bagId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_portion_inspection where bag_id=? order by inspection_id")) {
            statement.setString(1, bagId);
            try (ResultSet rows = statement.executeQuery()) {
                List<PortionInspection> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取质量检查", ex);
        }
    }

    private PortionInspection map(ResultSet rows) throws SQLException {
        String clauses = rows.getString("failed_clauses");
        List<String> failed = clauses == null || clauses.isBlank() ? List.of() : Arrays.asList(clauses.split(","));
        return new PortionInspection(
                rows.getString("inspection_id"), rows.getString("request_id"), rows.getString("bag_id"),
                rows.getString("requirement_version"), decimal(rows, "pass_qty"), decimal(rows, "rework_qty"),
                decimal(rows, "scrap_qty"), decimal(rows, "pending_qty"), failed);
    }

    private static BigDecimal decimal(ResultSet rows, String column) throws SQLException {
        BigDecimal value = rows.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
