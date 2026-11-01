package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ReworkTask;
import com.zhubao.erp.business.domain.ReworkTaskRepository;
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
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.rework-task.repository", havingValue = "jdbc")
public class JdbcReworkTaskRepository implements ReworkTaskRepository {

    private final DataSource dataSource;

    public JdbcReworkTaskRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(ReworkTask task, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_rework_task(
                    rework_id, request_id, source_bag_id, rework_bag_id, target_code,
                    requirement_version, round_no, quantity)
                values (?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, task.reworkId());
            statement.setString(2, task.requestId());
            statement.setString(3, task.sourceBagId());
            statement.setString(4, task.reworkBagId());
            statement.setString(5, task.targetCode());
            statement.setString(6, task.requirementVersion());
            statement.setInt(7, task.round());
            statement.setBigDecimal(8, task.quantity());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存返工任务", ex);
        }
    }

    @Override
    public Optional<ReworkTask> findByRequest(String requestId) {
        return list("request_id=?", requestId).stream().findFirst();
    }

    @Override
    public List<ReworkTask> listBySource(String sourceBagId) {
        return list("source_bag_id=?", sourceBagId);
    }

    @Override
    public List<ReworkTask> listByReworkBag(String reworkBagId) {
        return list("rework_bag_id=?", reworkBagId);
    }

    private List<ReworkTask> list(String where, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_rework_task where " + where + " order by rework_id")) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                List<ReworkTask> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new ReworkTask(
                            rows.getString("rework_id"), rows.getString("request_id"), rows.getString("source_bag_id"),
                            rows.getString("rework_bag_id"), rows.getString("target_code"),
                            rows.getString("requirement_version"), rows.getInt("round_no"),
                            rows.getBigDecimal("quantity") == null ? BigDecimal.ZERO : rows.getBigDecimal("quantity")));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取返工任务", ex);
        }
    }
}
