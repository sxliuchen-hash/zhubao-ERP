package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentAttachmentPage;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.documents.repository", havingValue = "jdbc")
public class JdbcDocumentManagementRepository implements DocumentManagementRepository {

    private final DataSource dataSource;

    public JdbcDocumentManagementRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<DocumentPackage> packages() {
        return query("""
                select code, title, category, business_code, owner_name, purpose, required_evidence,
                       linked_evidence, status, retention_policy, last_updated, tone
                  from doc_package
                 order by id
                """, mapper((rs) -> new DocumentPackage(
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getString("business_code"),
                rs.getString("owner_name"),
                rs.getString("purpose"),
                rs.getInt("required_evidence"),
                rs.getInt("linked_evidence"),
                rs.getString("status"),
                rs.getString("retention_policy"),
                rs.getString("last_updated"),
                rs.getString("tone")
        )));
    }

    @Override
    public List<EvidenceAttachment> attachments() {
        return query("""
                select code, document_code, business_type, business_code, file_name, media_type,
                       storage_location, storage_policy, source, status, owner_name, checksum,
                       uploaded_at, fallback_action, tone, object_type
                  from doc_evidence_attachment
                 order by id
                """, mapper((rs) -> new EvidenceAttachment(
                rs.getString("code"),
                rs.getString("document_code"),
                rs.getString("business_type"),
                rs.getString("business_code"),
                rs.getString("file_name"),
                rs.getString("media_type"),
                rs.getString("storage_location"),
                rs.getString("storage_policy"),
                rs.getString("source"),
                rs.getString("status"),
                rs.getString("owner_name"),
                rs.getString("checksum"),
                rs.getString("uploaded_at"),
                rs.getString("fallback_action"),
                rs.getString("tone"),
                rs.getString("object_type")
        )));
    }

    @Override
    public DocumentAttachmentPage attachmentsPage(String documentCode, long afterId, int limit) {
        validatePage(documentCode, afterId, limit);
        String sql = """
                select id, code, document_code, business_type, business_code, file_name, media_type,
                       storage_location, storage_policy, source, status, owner_name, checksum,
                       uploaded_at, fallback_action, tone, object_type
                  from doc_evidence_attachment
                 where document_code = ? and id > ?
                 order by id
                 limit ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentCode);
            statement.setLong(2, afterId);
            statement.setInt(3, limit + 1);
            List<DocumentAttachmentPage.Entry> entries = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new DocumentAttachmentPage.Entry(rs.getLong("id"), new EvidenceAttachment(
                            rs.getString("code"),
                            rs.getString("document_code"),
                            rs.getString("business_type"),
                            rs.getString("business_code"),
                            rs.getString("file_name"),
                            rs.getString("media_type"),
                            rs.getString("storage_location"),
                            rs.getString("storage_policy"),
                            rs.getString("source"),
                            rs.getString("status"),
                            rs.getString("owner_name"),
                            rs.getString("checksum"),
                            rs.getString("uploaded_at"),
                            rs.getString("fallback_action"),
                            rs.getString("tone"),
                            rs.getString("object_type")
                    )));
                }
            }
            boolean hasMore = entries.size() > limit;
            if (hasMore) entries = new ArrayList<>(entries.subList(0, limit));
            return new DocumentAttachmentPage(entries, hasMore);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query document attachment page", ex);
        }
    }

    @Override
    public List<DocumentAuditEvent> auditEvents() {
        return query("""
                select event_time, event_code, document_code, action, operator_name, detail, tone
                  from doc_audit_event
                 order by id
                """, mapper((rs) -> new DocumentAuditEvent(
                rs.getString("event_time"),
                rs.getString("event_code"),
                rs.getString("document_code"),
                rs.getString("action"),
                rs.getString("operator_name"),
                rs.getString("detail"),
                rs.getString("tone")
        )));
    }

    @Override
    public void savePackage(DocumentPackage documentPackage) {
        try (Connection connection = dataSource.getConnection()) {
            if (packageExists(connection, documentPackage.code())) {
                updatePackage(connection, documentPackage);
            } else {
                insertPackage(connection, documentPackage);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save document package: " + documentPackage.code(), ex);
        }
    }

    @Override
    public void saveAttachment(EvidenceAttachment attachment) {
        try (Connection connection = dataSource.getConnection()) {
            if (attachmentExists(connection, attachment.code())) {
                updateAttachment(connection, attachment);
            } else {
                insertAttachment(connection, attachment);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save document attachment: " + attachment.code(), ex);
        }
    }

    @Override
    public void saveAuditEvent(DocumentAuditEvent auditEvent) {
        try (Connection connection = dataSource.getConnection()) {
            if (auditEventExists(connection, auditEvent.eventCode())) {
                updateAuditEvent(connection, auditEvent);
            } else {
                insertAuditEvent(connection, auditEvent);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save document audit event: " + auditEvent.eventCode(), ex);
        }
    }

    private boolean packageExists(Connection connection, String code) throws SQLException {
        return exists(connection, "select id from doc_package where code = ?", code);
    }

    private boolean attachmentExists(Connection connection, String code) throws SQLException {
        return exists(connection, "select id from doc_evidence_attachment where code = ?", code);
    }

    private boolean auditEventExists(Connection connection, String eventCode) throws SQLException {
        return exists(connection, "select id from doc_audit_event where event_code = ?", eventCode);
    }

    private boolean exists(Connection connection, String sql, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertPackage(Connection connection, DocumentPackage documentPackage) throws SQLException {
        String sql = """
                insert into doc_package (
                    code, title, category, business_code, owner_name, purpose, required_evidence,
                    linked_evidence, status, retention_policy, last_updated, tone
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPackage(statement, documentPackage);
            statement.executeUpdate();
        }
    }

    private void updatePackage(Connection connection, DocumentPackage documentPackage) throws SQLException {
        String sql = """
                update doc_package
                   set title = ?, category = ?, business_code = ?, owner_name = ?, purpose = ?,
                       required_evidence = ?, linked_evidence = ?, status = ?, retention_policy = ?,
                       last_updated = ?, tone = ?
                 where code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentPackage.title());
            statement.setString(2, documentPackage.category());
            statement.setString(3, documentPackage.businessCode());
            statement.setString(4, documentPackage.owner());
            statement.setString(5, documentPackage.purpose());
            statement.setInt(6, documentPackage.requiredEvidence());
            statement.setInt(7, documentPackage.linkedEvidence());
            statement.setString(8, documentPackage.status());
            statement.setString(9, documentPackage.retentionPolicy());
            statement.setString(10, documentPackage.lastUpdated());
            statement.setString(11, documentPackage.tone());
            statement.setString(12, documentPackage.code());
            statement.executeUpdate();
        }
    }

    private void bindPackage(PreparedStatement statement, DocumentPackage documentPackage) throws SQLException {
        statement.setString(1, documentPackage.code());
        statement.setString(2, documentPackage.title());
        statement.setString(3, documentPackage.category());
        statement.setString(4, documentPackage.businessCode());
        statement.setString(5, documentPackage.owner());
        statement.setString(6, documentPackage.purpose());
        statement.setInt(7, documentPackage.requiredEvidence());
        statement.setInt(8, documentPackage.linkedEvidence());
        statement.setString(9, documentPackage.status());
        statement.setString(10, documentPackage.retentionPolicy());
        statement.setString(11, documentPackage.lastUpdated());
        statement.setString(12, documentPackage.tone());
    }

    private void insertAttachment(Connection connection, EvidenceAttachment attachment) throws SQLException {
        String sql = """
                insert into doc_evidence_attachment (
                    code, document_code, business_type, business_code, file_name, media_type,
                    storage_location, storage_policy, source, status, owner_name, checksum,
                    uploaded_at, fallback_action, tone, object_type
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAttachment(statement, attachment);
            statement.executeUpdate();
        }
    }

    private void updateAttachment(Connection connection, EvidenceAttachment attachment) throws SQLException {
        String sql = """
                update doc_evidence_attachment
                   set document_code = ?, business_type = ?, business_code = ?, file_name = ?, media_type = ?,
                       storage_location = ?, storage_policy = ?, source = ?, status = ?, owner_name = ?,
                       checksum = ?, uploaded_at = ?, fallback_action = ?, tone = ?, object_type = ?
                 where code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, attachment.documentCode());
            statement.setString(2, attachment.businessType());
            statement.setString(3, attachment.businessCode());
            statement.setString(4, attachment.fileName());
            statement.setString(5, attachment.mediaType());
            statement.setString(6, attachment.storageLocation());
            statement.setString(7, attachment.storagePolicy());
            statement.setString(8, attachment.source());
            statement.setString(9, attachment.status());
            statement.setString(10, attachment.owner());
            statement.setString(11, attachment.checksum());
            statement.setString(12, attachment.uploadedAt());
            statement.setString(13, attachment.fallbackAction());
            statement.setString(14, attachment.tone());
            statement.setString(15, attachment.objectType());
            statement.setString(16, attachment.code());
            statement.executeUpdate();
        }
    }

    private void bindAttachment(PreparedStatement statement, EvidenceAttachment attachment) throws SQLException {
        statement.setString(1, attachment.code());
        statement.setString(2, attachment.documentCode());
        statement.setString(3, attachment.businessType());
        statement.setString(4, attachment.businessCode());
        statement.setString(5, attachment.fileName());
        statement.setString(6, attachment.mediaType());
        statement.setString(7, attachment.storageLocation());
        statement.setString(8, attachment.storagePolicy());
        statement.setString(9, attachment.source());
        statement.setString(10, attachment.status());
        statement.setString(11, attachment.owner());
        statement.setString(12, attachment.checksum());
        statement.setString(13, attachment.uploadedAt());
        statement.setString(14, attachment.fallbackAction());
        statement.setString(15, attachment.tone());
        statement.setString(16, attachment.objectType());
    }

    private void insertAuditEvent(Connection connection, DocumentAuditEvent auditEvent) throws SQLException {
        String sql = """
                insert into doc_audit_event (
                    event_code, document_code, event_time, action, operator_name, detail, tone
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAuditEvent(statement, auditEvent);
            statement.executeUpdate();
        }
    }

    private void updateAuditEvent(Connection connection, DocumentAuditEvent auditEvent) throws SQLException {
        String sql = """
                update doc_audit_event
                   set document_code = ?, event_time = ?, action = ?, operator_name = ?, detail = ?, tone = ?
                 where event_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auditEvent.documentCode());
            statement.setString(2, auditEvent.time());
            statement.setString(3, auditEvent.action());
            statement.setString(4, auditEvent.operator());
            statement.setString(5, auditEvent.detail());
            statement.setString(6, auditEvent.tone());
            statement.setString(7, auditEvent.eventCode());
            statement.executeUpdate();
        }
    }

    private void bindAuditEvent(PreparedStatement statement, DocumentAuditEvent auditEvent) throws SQLException {
        statement.setString(1, auditEvent.eventCode());
        statement.setString(2, auditEvent.documentCode());
        statement.setString(3, auditEvent.time());
        statement.setString(4, auditEvent.action());
        statement.setString(5, auditEvent.operator());
        statement.setString(6, auditEvent.detail());
        statement.setString(7, auditEvent.tone());
    }

    private <T> List<T> query(String sql, JdbcRowMapper<T> mapper) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
            return List.copyOf(rows);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query document management repository", ex);
        }
    }

    private <T> JdbcRowMapper<T> mapper(JdbcRowMapper<T> mapper) {
        return mapper;
    }

    private void validatePage(String documentCode, long afterId, int limit) {
        if (documentCode == null || documentCode.isBlank()) throw new IllegalArgumentException("文档包编号不能为空");
        if (afterId < 0) throw new IllegalArgumentException("附件游标不能小于 0");
        if (limit < 1 || limit > 101) throw new IllegalArgumentException("附件分页大小必须在 1 到 101 之间");
    }

    @FunctionalInterface
    private interface JdbcRowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
