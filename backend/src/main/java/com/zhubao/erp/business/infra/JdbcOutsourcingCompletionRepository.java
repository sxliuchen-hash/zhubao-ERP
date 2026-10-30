package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OutsourcingCompletion;
import com.zhubao.erp.business.domain.OutsourcingCompletionRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-completion.repository", havingValue = "jdbc")
public class JdbcOutsourcingCompletionRepository implements OutsourcingCompletionRepository {

    private final DataSource dataSource;

    public JdbcOutsourcingCompletionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(OutsourcingCompletion completion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_outsourcing_completion(
                    completion_id, request_id, return_id, task_id, dispatch_id, bag_id, node_id, target_code, quantity)
                values (?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, completion.completionId());
            statement.setString(2, completion.requestId());
            statement.setString(3, completion.returnId());
            statement.setString(4, completion.taskId());
            statement.setString(5, completion.dispatchId());
            statement.setString(6, completion.bagId());
            statement.setString(7, completion.nodeId());
            statement.setString(8, completion.targetCode());
            statement.setBigDecimal(9, completion.quantity());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("外协完成量已记录");
            }
            throw new IllegalStateException("无法保存外协完成量", ex);
        }
    }

    @Override
    public Optional<OutsourcingCompletion> find(String completionId) {
        return one("completion_id=?", completionId);
    }

    @Override
    public Optional<OutsourcingCompletion> findByReturn(String returnId) {
        return one("return_id=?", returnId);
    }

    private Optional<OutsourcingCompletion> one(String where, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_outsourcing_completion where " + where)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OutsourcingCompletion(
                        rows.getString("completion_id"), rows.getString("request_id"), rows.getString("return_id"),
                        rows.getString("task_id"), rows.getString("dispatch_id"), rows.getString("bag_id"),
                        rows.getString("node_id"), rows.getString("target_code"), rows.getBigDecimal("quantity")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取外协完成量", ex);
        }
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
