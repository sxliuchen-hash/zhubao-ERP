package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.QuantityDispositionLine;
import com.zhubao.erp.business.domain.QuantityDispositionRepository;
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
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.quantity-disposition.repository", havingValue = "jdbc")
public class JdbcQuantityDispositionRepository implements QuantityDispositionRepository {

    private final DataSource dataSource;

    public JdbcQuantityDispositionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(QuantityDispositionLine line, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_quantity_disposition(
                    disposition_id, request_id, bag_id, step_id, handoff_qty, loss_qty,
                    scrap_qty, returned_qty, pending_qty)
                values (?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, line.dispositionId());
            statement.setString(2, line.requestId());
            statement.setString(3, line.bagId());
            statement.setString(4, line.stepId());
            statement.setBigDecimal(5, line.handoff());
            statement.setBigDecimal(6, line.loss());
            statement.setBigDecimal(7, line.scrap());
            statement.setBigDecimal(8, line.returned());
            statement.setBigDecimal(9, line.pending());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存数量处置", ex);
        }
    }

    @Override
    public List<QuantityDispositionLine> listByBag(String bagId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_quantity_disposition where bag_id=? order by disposition_id")) {
            statement.setString(1, bagId);
            try (ResultSet rows = statement.executeQuery()) {
                List<QuantityDispositionLine> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取数量处置", ex);
        }
    }

    private QuantityDispositionLine map(ResultSet rows) throws SQLException {
        return new QuantityDispositionLine(
                rows.getString("disposition_id"),
                rows.getString("request_id"),
                rows.getString("bag_id"),
                rows.getString("step_id"),
                decimal(rows, "handoff_qty"),
                decimal(rows, "loss_qty"),
                decimal(rows, "scrap_qty"),
                decimal(rows, "returned_qty"),
                decimal(rows, "pending_qty"));
    }

    private static BigDecimal decimal(ResultSet rows, String column) throws SQLException {
        BigDecimal value = rows.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
