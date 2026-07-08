package com.zhubao.erp.register.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportBatchRepository;
import com.zhubao.erp.register.domain.RegisterImportBatchRow;
import com.zhubao.erp.register.domain.RegisterImportPreviewMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
@ConditionalOnProperty(name = "zhubao.register-import.repository", havingValue = "jdbc")
public class JdbcRegisterImportBatchRepository implements RegisterImportBatchRepository {

    private static final TypeReference<Map<String, String>> FIELD_MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<RegisterImportPreviewMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcRegisterImportBatchRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public RegisterImportBatch save(RegisterImportBatch batch) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertBatch(connection, batch);
                for (RegisterImportBatchRow row : batch.rows()) {
                    insertRow(connection, row);
                }
                connection.commit();
                return batch;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save register import batch: " + batch.importBatchNo(), ex);
        }
    }

    @Override
    public RegisterImportBatch updateRowFields(
            String importBatchNo,
            int sourceLineNo,
            Map<String, String> normalizedFields,
            String nextAction
    ) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int updatedRows = updateRowFields(connection, importBatchNo, sourceLineNo, normalizedFields);
                if (updatedRows == 0) {
                    connection.rollback();
                    return null;
                }
                updateNextAction(connection, importBatchNo, nextAction);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to correct register import row: " + importBatchNo, ex);
        }
        return findByBatchNo(importBatchNo)
                .orElseThrow(() -> new IllegalStateException("Register import batch disappeared: " + importBatchNo));
    }

    @Override
    public RegisterImportBatch markRowsPosted(
            String importBatchNo,
            List<Integer> sourceLineNos,
            boolean batchPosted,
            String nextAction
    ) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!sourceLineNos.isEmpty()) {
                    markRowsPosted(connection, importBatchNo, sourceLineNos);
                }
                if (batchPosted) {
                    markBatchPosted(connection, importBatchNo, nextAction);
                } else {
                    updateNextAction(connection, importBatchNo, nextAction);
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to mark register import batch posted: " + importBatchNo, ex);
        }
        return findByBatchNo(importBatchNo)
                .orElseThrow(() -> new IllegalStateException("Register import batch disappeared: " + importBatchNo));
    }

    @Override
    public Optional<RegisterImportBatch> findByBatchNo(String importBatchNo) {
        String sql = """
                select import_batch_no, template_code, template_name, source_file_name, target_command,
                       total_rows, ready_rows, warning_rows, blocked_rows, executable, batch_status,
                       confirmed_by, confirmed_at, confirm_remark, next_action
                  from register_import_batch
                 where import_batch_no = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, importBatchNo);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapBatch(rs, rows(connection, importBatchNo)));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load register import batch: " + importBatchNo, ex);
        }
    }

    @Override
    public List<RegisterImportBatch> findRecent(int limit) {
        String sql = """
                select import_batch_no, template_code, template_name, source_file_name, target_command,
                       total_rows, ready_rows, warning_rows, blocked_rows, executable, batch_status,
                       confirmed_by, confirmed_at, confirm_remark, next_action
                  from register_import_batch
                 order by id desc
                 limit ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<RegisterImportBatch> batches = new ArrayList<>();
                while (rs.next()) {
                    String importBatchNo = rs.getString("import_batch_no");
                    batches.add(mapBatch(rs, rows(connection, importBatchNo)));
                }
                return List.copyOf(batches);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load recent register import batches", ex);
        }
    }

    private void markRowsPosted(Connection connection, String importBatchNo, List<Integer> sourceLineNos) throws SQLException {
        String placeholders = IntStream.range(0, sourceLineNos.size())
                .mapToObj(index -> "?")
                .collect(Collectors.joining(", "));
        String sql = """
                update register_import_row
                   set row_status = 'POSTED'
                 where import_batch_no = ?
                   and source_line_no in (%s)
                """.formatted(placeholders);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, importBatchNo);
            for (int i = 0; i < sourceLineNos.size(); i++) {
                statement.setInt(i + 2, sourceLineNos.get(i));
            }
            statement.executeUpdate();
        }
    }

    private int updateRowFields(
            Connection connection,
            String importBatchNo,
            int sourceLineNo,
            Map<String, String> normalizedFields
    ) throws SQLException {
        String sql = """
                update register_import_row
                   set normalized_fields_json = ?
                 where import_batch_no = ?
                   and source_line_no = ?
                   and row_status <> 'POSTED'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toJson(normalizedFields));
            statement.setString(2, importBatchNo);
            statement.setInt(3, sourceLineNo);
            return statement.executeUpdate();
        }
    }

    private void markBatchPosted(Connection connection, String importBatchNo, String nextAction) throws SQLException {
        String sql = """
                update register_import_batch
                   set batch_status = 'POSTED',
                       next_action = ?
                 where import_batch_no = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nextAction);
            statement.setString(2, importBatchNo);
            statement.executeUpdate();
        }
    }

    private void updateNextAction(Connection connection, String importBatchNo, String nextAction) throws SQLException {
        String sql = """
                update register_import_batch
                   set next_action = ?
                 where import_batch_no = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nextAction);
            statement.setString(2, importBatchNo);
            statement.executeUpdate();
        }
    }

    private void insertBatch(Connection connection, RegisterImportBatch batch) throws SQLException {
        String sql = """
                insert into register_import_batch (
                    import_batch_no, template_code, template_name, source_file_name, target_command,
                    total_rows, ready_rows, warning_rows, blocked_rows, executable, batch_status,
                    confirmed_by, confirmed_at, confirm_remark, next_action
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, batch.importBatchNo());
            statement.setString(2, batch.templateCode());
            statement.setString(3, batch.templateName());
            statement.setString(4, batch.sourceFileName());
            statement.setString(5, batch.targetCommand());
            statement.setInt(6, batch.totalRows());
            statement.setInt(7, batch.readyRows());
            statement.setInt(8, batch.warningRows());
            statement.setInt(9, batch.blockedRows());
            statement.setBoolean(10, batch.executable());
            statement.setString(11, batch.status());
            statement.setString(12, batch.confirmedBy());
            statement.setString(13, batch.confirmedAt());
            statement.setString(14, batch.confirmRemark());
            statement.setString(15, batch.nextAction());
            statement.executeUpdate();
        }
    }

    private void insertRow(Connection connection, RegisterImportBatchRow row) throws SQLException {
        String sql = """
                insert into register_import_row (
                    import_batch_no, source_line_no, source_register_sequence, row_status,
                    normalized_fields_json, messages_json
                ) values (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.importBatchNo());
            statement.setInt(2, row.sourceLineNo());
            statement.setString(3, row.sourceRegisterSequence());
            statement.setString(4, row.status());
            statement.setString(5, toJson(row.normalizedFields()));
            statement.setString(6, toJson(row.messages()));
            statement.executeUpdate();
        }
    }

    private List<RegisterImportBatchRow> rows(Connection connection, String importBatchNo) throws SQLException {
        String sql = """
                select import_batch_no, source_line_no, source_register_sequence, row_status,
                       normalized_fields_json, messages_json
                  from register_import_row
                 where import_batch_no = ?
                 order by source_line_no, id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, importBatchNo);
            try (ResultSet rs = statement.executeQuery()) {
                List<RegisterImportBatchRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new RegisterImportBatchRow(
                            rs.getString("import_batch_no"),
                            rs.getInt("source_line_no"),
                            rs.getString("source_register_sequence"),
                            rs.getString("row_status"),
                            readFields(rs.getString("normalized_fields_json")),
                            readMessages(rs.getString("messages_json"))
                    ));
                }
                return List.copyOf(rows);
            }
        }
    }

    private RegisterImportBatch mapBatch(ResultSet rs, List<RegisterImportBatchRow> rows) throws SQLException {
        return new RegisterImportBatch(
                rs.getString("import_batch_no"),
                rs.getString("template_code"),
                rs.getString("template_name"),
                rs.getString("source_file_name"),
                rs.getString("target_command"),
                rs.getInt("total_rows"),
                rs.getInt("ready_rows"),
                rs.getInt("warning_rows"),
                rs.getInt("blocked_rows"),
                rs.getBoolean("executable"),
                rs.getString("batch_status"),
                rs.getString("confirmed_by"),
                rs.getString("confirmed_at"),
                rs.getString("confirm_remark"),
                rs.getString("next_action"),
                rows
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize register import payload", ex);
        }
    }

    private Map<String, String> readFields(String json) {
        try {
            return objectMapper.readValue(json, FIELD_MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize register import fields", ex);
        }
    }

    private List<RegisterImportPreviewMessage> readMessages(String json) {
        try {
            return objectMapper.readValue(json, MESSAGE_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize register import messages", ex);
        }
    }
}
