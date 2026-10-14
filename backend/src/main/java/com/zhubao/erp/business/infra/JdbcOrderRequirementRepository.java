package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ExecutionRound;
import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.OrderRequirementVersion;
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
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "jdbc")
public class JdbcOrderRequirementRepository implements OrderRequirementRepository {

    private final DataSource dataSource;

    public JdbcOrderRequirementRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveVersion(OrderRequirementVersion version, WorkSession session) {
        String sql = """
                insert into biz_order_requirement_version (
                    id, mother_order_code, work_order_code, version_no, requirement_text, due_date, route_text, change_id, effective_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection(session).prepareStatement(sql)) {
            statement.setString(1, version.id());
            statement.setString(2, version.motherOrderCode());
            statement.setString(3, version.workOrderCode());
            statement.setInt(4, version.versionNo());
            statement.setString(5, version.requirementText());
            statement.setString(6, version.dueDate());
            statement.setString(7, version.routeText());
            statement.setString(8, version.changeId());
            statement.setString(9, version.effectiveAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存订单要求版本", ex);
        }
    }

    @Override
    public Optional<OrderRequirementVersion> current(String motherOrderCode, String workOrderCode) {
        String sql = """
                select * from biz_order_requirement_version
                 where mother_order_code = ? and work_order_code = ?
                 order by version_no desc limit 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            statement.setString(2, workOrderCode == null ? "" : workOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OrderRequirementVersion(
                        rs.getString("id"),
                        rs.getString("mother_order_code"),
                        rs.getString("work_order_code"),
                        rs.getInt("version_no"),
                        rs.getString("requirement_text"),
                        rs.getString("due_date"),
                        rs.getString("route_text"),
                        rs.getString("change_id"),
                        rs.getString("effective_at")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取订单要求版本", ex);
        }
    }

    @Override
    public void saveRound(ExecutionRound round, WorkSession session) {
        String sql = """
                insert into biz_execution_round (
                    id, mother_order_code, source_work_order_code, round_work_order_code, process_name,
                    change_id, supersedes_order_code, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection(session).prepareStatement(sql)) {
            statement.setString(1, round.id());
            statement.setString(2, round.motherOrderCode());
            statement.setString(3, round.sourceWorkOrderCode());
            statement.setString(4, round.roundWorkOrderCode());
            statement.setString(5, round.processName());
            statement.setString(6, round.changeId());
            statement.setString(7, round.supersedesOrderCode());
            statement.setString(8, round.createdAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存执行轮次", ex);
        }
    }

    @Override
    public Optional<ExecutionRound> findRound(String roundWorkOrderCode) {
        if (roundWorkOrderCode == null || roundWorkOrderCode.isBlank()) {
            return Optional.empty();
        }
        String sql = "select * from biz_execution_round where round_work_order_code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roundWorkOrderCode.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ExecutionRound(
                        rs.getString("id"),
                        rs.getString("mother_order_code"),
                        rs.getString("source_work_order_code"),
                        rs.getString("round_work_order_code"),
                        rs.getString("process_name"),
                        rs.getString("change_id"),
                        rs.getString("supersedes_order_code"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取执行轮次", ex);
        }
    }

    @Override
    public List<ExecutionRound> listByMother(String motherOrderCode) {
        String sql = "select * from biz_execution_round where mother_order_code = ? order by created_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<ExecutionRound> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new ExecutionRound(
                            rs.getString("id"),
                            rs.getString("mother_order_code"),
                            rs.getString("source_work_order_code"),
                            rs.getString("round_work_order_code"),
                            rs.getString("process_name"),
                            rs.getString("change_id"),
                            rs.getString("supersedes_order_code"),
                            rs.getString("created_at")
                    ));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法列出执行轮次", ex);
        }
    }

    private Connection connection(WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("订单版本写入必须使用工作单元连接");
        }
        return session.connection().get();
    }
}