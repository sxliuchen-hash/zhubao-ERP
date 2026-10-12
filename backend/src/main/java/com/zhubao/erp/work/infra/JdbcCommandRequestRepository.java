package com.zhubao.erp.work.infra;

import com.zhubao.erp.work.domain.CommandRequest;
import com.zhubao.erp.work.domain.CommandRequestLookup;
import com.zhubao.erp.work.domain.CommandRequestStore;
import com.zhubao.erp.work.domain.DuplicateCommandRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.command-request.repository", havingValue = "jdbc")
public class JdbcCommandRequestRepository implements CommandRequestLookup, CommandRequestStore {

    private final DataSource dataSource;

    public JdbcCommandRequestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<CommandRequest> findByRequestId(String requestId) {
        try (Connection connection = dataSource.getConnection()) {
            return findByRequestId(connection, requestId);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取命令请求", ex);
        }
    }

    @Override
    public Optional<CommandRequest> findByRequestId(Connection connection, String requestId) {
        String sql = """
                select request_id, actor_id, action_code, target_type, target_code, payload_hash, result_text
                  from biz_command_request
                 where request_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CommandRequest(
                        rs.getString("request_id"),
                        rs.getString("actor_id"),
                        rs.getString("action_code"),
                        rs.getString("target_type"),
                        rs.getString("target_code"),
                        rs.getString("payload_hash"),
                        rs.getString("result_text")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取命令请求", ex);
        }
    }

    @Override
    public void insert(Connection connection, CommandRequest request) {
        String sql = """
                insert into biz_command_request (
                    request_id, actor_id, action_code, target_type, target_code, payload_hash, result_text
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.requestId());
            statement.setString(2, request.actorId());
            statement.setString(3, request.action());
            statement.setString(4, request.targetType());
            statement.setString(5, request.targetCode());
            statement.setString(6, request.payloadHash());
            statement.setString(7, request.resultText());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new DuplicateCommandRequestException(ex);
            }
            throw new IllegalStateException("无法占用命令请求", ex);
        }
    }

    @Override
    public void updateResult(Connection connection, String requestId, String resultText) {
        String sql = "update biz_command_request set result_text = ? where request_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resultText);
            statement.setString(2, requestId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("无法写回命令结果");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法写回命令结果", ex);
        }
    }

    private boolean duplicate(SQLException ex) {
        String state = ex.getSQLState() == null ? "" : ex.getSQLState();
        if ("23505".equals(state) || "23000".equals(state)) {
            return true;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return message.contains("unique") || message.contains("duplicate");
    }
}
