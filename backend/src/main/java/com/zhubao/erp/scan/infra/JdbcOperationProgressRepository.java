package com.zhubao.erp.scan.infra;

import com.zhubao.erp.scan.domain.OperationProgressRecord;
import com.zhubao.erp.scan.domain.OperationProgressRepository;
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
@ConditionalOnProperty(name = "zhubao.scan-progress.repository", havingValue = "jdbc")
public class JdbcOperationProgressRepository implements OperationProgressRepository {

    private static final String COLUMNS = """
            progress_code, target_type, target_code, mother_order_code, stage, process_name,
            clause_no, note, operator_id, operator_name, operator_role, supplementary,
            recorded_at, request_id, content_hash
            """;

    private final DataSource dataSource;

    public JdbcOperationProgressRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(OperationProgressRecord record) {
        String sql = """
                insert into scan_operation_progress (
                    progress_code, target_type, target_code, mother_order_code, stage, process_name,
                    clause_no, note, operator_id, operator_name, operator_role, supplementary,
                    recorded_at, request_id, content_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.code());
            statement.setString(2, record.targetType());
            statement.setString(3, record.targetCode());
            statement.setString(4, record.motherOrderCode());
            statement.setString(5, record.stage());
            statement.setString(6, record.process());
            statement.setString(7, record.clauseNo());
            statement.setString(8, record.note());
            statement.setString(9, record.operatorId());
            statement.setString(10, record.operatorName());
            statement.setString(11, record.operatorRole());
            statement.setBoolean(12, record.supplementary());
            statement.setString(13, record.recordedAt());
            statement.setString(14, record.requestId());
            statement.setString(15, record.contentHash());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new IllegalStateException("过程记录已存在", ex);
            }
            throw new IllegalStateException("无法保存过程记录：" + record.code(), ex);
        }
    }

    @Override
    public Optional<OperationProgressRecord> findByCode(String code) {
        return one("where progress_code = ?", code);
    }

    @Override
    public List<OperationProgressRecord> findByRequestId(String requestId) {
        return list("where request_id = ? order by id", requestId);
    }

    @Override
    public List<OperationProgressRecord> findByTarget(String targetType, String targetCode) {
        return list("where target_type = ? and target_code = ? order by id", targetType, targetCode);
    }

    @Override
    public Optional<OperationProgressRecord> findReplay(
            String requestId,
            String operatorId,
            String targetType,
            String targetCode,
            String contentHash
    ) {
        return one(
                """
                where request_id = ? and operator_id = ? and target_type = ? and target_code = ?
                  and content_hash = ?
                """,
                requestId, operatorId, targetType, targetCode, contentHash
        );
    }

    private Optional<OperationProgressRecord> one(String where, String... args) {
        List<OperationProgressRecord> rows = list(where, args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private List<OperationProgressRecord> list(String where, String... args) {
        String sql = "select " + COLUMNS + " from scan_operation_progress " + where;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setString(i + 1, args[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<OperationProgressRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取过程记录", ex);
        }
    }

    private OperationProgressRecord map(ResultSet rs) throws SQLException {
        return new OperationProgressRecord(
                rs.getString("progress_code"),
                rs.getString("target_type"),
                rs.getString("target_code"),
                rs.getString("mother_order_code"),
                rs.getString("stage"),
                rs.getString("process_name"),
                rs.getString("clause_no"),
                rs.getString("note"),
                rs.getString("operator_id"),
                rs.getString("operator_name"),
                rs.getString("operator_role"),
                rs.getBoolean("supplementary"),
                rs.getString("recorded_at"),
                rs.getString("request_id"),
                rs.getString("content_hash")
        );
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
