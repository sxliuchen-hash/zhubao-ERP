package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDocumentManagementRepositoryTest {

    private JdbcDataSource dataSource;
    private JdbcDocumentManagementRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema();
        seedReviewData();
        repository = new JdbcDocumentManagementRepository(dataSource);
    }

    @Test
    void loadsDocumentPackagesAttachmentsAndAuditEvents() {
        assertThat(repository.packages())
                .extracting(DocumentPackage::code)
                .containsExactly("DOC-INBOUND-20260706-001");
        assertThat(repository.attachments())
                .extracting(EvidenceAttachment::code)
                .containsExactly("ATT-IN-20260706-001");
        assertThat(repository.auditEvents())
                .extracting(DocumentAuditEvent::eventCode)
                .containsExactly("DOC-AUD-001");
    }

    @Test
    void savesAndReloadsUploadedAttachmentMetadataAndAuditEvent() {
        EvidenceAttachment uploaded = new EvidenceAttachment(
                "ATT-JDBC-UP-001",
                "DOC-INBOUND-20260706-001",
                "称重交接",
                "HO-JDBC-001",
                "weight-jdbc.jpg",
                "image/jpeg",
                "local://evidence/DOC-INBOUND-20260706-001/ATT-JDBC-UP-001/weight-jdbc.jpg",
                "hot 180d -> archive",
                "PC 后台上传",
                "已上传",
                "库管员 何琳",
                "sha256:jdbc-upload",
                "2026-07-07 07:30",
                "无",
                "green"
        );
        DocumentAuditEvent auditEvent = new DocumentAuditEvent(
                "2026-07-07 07:30",
                "DOC-AUD-ATT-JDBC-UP-001",
                "DOC-INBOUND-20260706-001",
                "上传附件",
                "库管员 何琳",
                "上传 weight-jdbc.jpg 并生成校验和 sha256:jdbc-upload。",
                "green"
        );

        repository.saveAttachment(uploaded);
        repository.saveAuditEvent(auditEvent);
        JdbcDocumentManagementRepository reloaded = new JdbcDocumentManagementRepository(dataSource);

        assertThat(reloaded.attachments())
                .filteredOn(attachment -> attachment.code().equals("ATT-JDBC-UP-001"))
                .singleElement()
                .satisfies(attachment -> {
                    assertThat(attachment.status()).isEqualTo("已上传");
                    assertThat(attachment.storageLocation()).startsWith("local://evidence/");
                    assertThat(attachment.checksum()).isEqualTo("sha256:jdbc-upload");
                });
        assertThat(reloaded.auditEvents())
                .extracting(DocumentAuditEvent::eventCode)
                .contains("DOC-AUD-ATT-JDBC-UP-001");

        repository.saveAttachment(new EvidenceAttachment(
                uploaded.code(),
                uploaded.documentCode(),
                uploaded.businessType(),
                uploaded.businessCode(),
                uploaded.fileName(),
                uploaded.mediaType(),
                uploaded.storageLocation(),
                uploaded.storagePolicy(),
                uploaded.source(),
                "已归档",
                uploaded.owner(),
                uploaded.checksum(),
                uploaded.uploadedAt(),
                uploaded.fallbackAction(),
                "green"
        ));

        assertThat(reloaded.attachments())
                .filteredOn(attachment -> attachment.code().equals("ATT-JDBC-UP-001"))
                .singleElement()
                .extracting(EvidenceAttachment::status)
                .isEqualTo("已归档");
    }

    private void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table doc_package (
                        id bigint primary key auto_increment,
                        code varchar(80) not null unique,
                        title varchar(120) not null,
                        category varchar(50) not null,
                        business_code varchar(80) not null,
                        owner_name varchar(80) not null,
                        purpose varchar(500) not null,
                        required_evidence int not null,
                        linked_evidence int not null,
                        status varchar(50) not null,
                        retention_policy varchar(200) not null,
                        last_updated varchar(30) not null,
                        tone varchar(20) not null
                    )
                    """);
            statement.execute("""
                    create table doc_evidence_attachment (
                        id bigint primary key auto_increment,
                        code varchar(80) not null unique,
                        document_code varchar(80) not null,
                        business_type varchar(50) not null,
                        business_code varchar(80) not null,
                        file_name varchar(200) not null,
                        media_type varchar(80) not null,
                        storage_location varchar(500) not null,
                        storage_policy varchar(120) not null,
                        source varchar(80) not null,
                        status varchar(50) not null,
                        owner_name varchar(80) not null,
                        checksum varchar(120) not null,
                        uploaded_at varchar(30) not null,
                        fallback_action varchar(300) not null,
                        tone varchar(20) not null
                    )
                    """);
            statement.execute("""
                    create table doc_audit_event (
                        id bigint primary key auto_increment,
                        event_code varchar(80) not null unique,
                        document_code varchar(80) not null,
                        event_time varchar(30) not null,
                        action varchar(80) not null,
                        operator_name varchar(80) not null,
                        detail varchar(500) not null,
                        tone varchar(20) not null
                    )
                    """);
        }
    }

    private void seedReviewData() throws SQLException {
        insertPackage(DocumentManagementSeedData.packages().get(0));
        insertAttachment(DocumentManagementSeedData.attachments().get(0));
        insertAuditEvent(DocumentManagementSeedData.auditEvents().get(0));
    }

    private void insertPackage(DocumentPackage documentPackage) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into doc_package (
                         code, title, category, business_code, owner_name, purpose, required_evidence,
                         linked_evidence, status, retention_policy, last_updated, tone
                     ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
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
            statement.executeUpdate();
        }
    }

    private void insertAttachment(EvidenceAttachment attachment) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into doc_evidence_attachment (
                         code, document_code, business_type, business_code, file_name, media_type,
                         storage_location, storage_policy, source, status, owner_name, checksum,
                         uploaded_at, fallback_action, tone
                     ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
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
            statement.executeUpdate();
        }
    }

    private void insertAuditEvent(DocumentAuditEvent auditEvent) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into doc_audit_event (
                         event_code, document_code, event_time, action, operator_name, detail, tone
                     ) values (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, auditEvent.eventCode());
            statement.setString(2, auditEvent.documentCode());
            statement.setString(3, auditEvent.time());
            statement.setString(4, auditEvent.action());
            statement.setString(5, auditEvent.operator());
            statement.setString(6, auditEvent.detail());
            statement.setString(7, auditEvent.tone());
            statement.executeUpdate();
        }
    }
}
