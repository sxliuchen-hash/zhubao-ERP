package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentAttachmentPage;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.DocumentPackagePage;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void pagesAttachmentsByDocumentAndStableIdLikeMemoryRepository() throws SQLException {
        insertAttachment(pageAttachment("ATT-PAGE-001", "BUS-PAGE-001"));
        insertAttachment(pageAttachment("ATT-PAGE-002", "BUS-PAGE-002"));
        insertAttachment(pageAttachment("ATT-PAGE-003", "BUS-PAGE-003"));

        DocumentAttachmentPage first = repository.attachmentsPage("DOC-PAGE-001", 0, 2);
        assertThat(first.entries()).extracting(DocumentAttachmentPage.Entry::attachment)
                .extracting(EvidenceAttachment::code)
                .containsExactly("ATT-PAGE-001", "ATT-PAGE-002");
        assertThat(first.hasMore()).isTrue();

        DocumentAttachmentPage last = repository.attachmentsPage(
                "DOC-PAGE-001", first.entries().get(1).id(), 2);
        assertThat(last.entries()).extracting(DocumentAttachmentPage.Entry::attachment)
                .extracting(EvidenceAttachment::code)
                .containsExactly("ATT-PAGE-003");
        assertThat(last.hasMore()).isFalse();

        InMemoryDocumentManagementRepository memory = new InMemoryDocumentManagementRepository();
        memory.saveAttachment(pageAttachment("ATT-PAGE-001", "BUS-PAGE-001"));
        memory.saveAttachment(pageAttachment("ATT-PAGE-002", "BUS-PAGE-002"));
        memory.saveAttachment(pageAttachment("ATT-PAGE-003", "BUS-PAGE-003"));
        DocumentAttachmentPage memoryFirst = memory.attachmentsPage("DOC-PAGE-001", 0, 2);
        assertThat(memoryFirst.entries())
                .extracting(DocumentAttachmentPage.Entry::attachment)
                .extracting(EvidenceAttachment::code)
                .containsExactly("ATT-PAGE-001", "ATT-PAGE-002");

        long stableCursor = first.entries().get(0).id();
        long memoryStableCursor = memoryFirst.entries().get(0).id();
        repository.saveAttachment(pageAttachment("ATT-PAGE-002", "BUS-PAGE-002").withStatus("已归档", "green"));
        memory.saveAttachment(pageAttachment("ATT-PAGE-002", "BUS-PAGE-002").withStatus("已归档", "green"));
        assertThat(repository.attachmentsPage("DOC-PAGE-001", stableCursor, 1).entries())
                .extracting(DocumentAttachmentPage.Entry::attachment)
                .extracting(EvidenceAttachment::code)
                .containsExactly("ATT-PAGE-002");
        assertThat(memory.attachmentsPage("DOC-PAGE-001", memoryStableCursor, 1).entries())
                .extracting(DocumentAttachmentPage.Entry::attachment)
                .extracting(EvidenceAttachment::code)
                .containsExactly("ATT-PAGE-002");

        assertThatThrownBy(() -> repository.attachmentsPage("DOC-PAGE-001", -1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> memory.attachmentsPage("DOC-PAGE-001", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pagesDocumentPackagesByStableIdLikeMemoryRepository() throws SQLException {
        insertPackage(pagePackage("DOC-PAGE-PKG-001", "BUS-PAGE-PKG-001"));
        insertPackage(pagePackage("DOC-PAGE-PKG-002", "BUS-PAGE-PKG-002"));
        insertPackage(pagePackage("DOC-PAGE-PKG-003", "BUS-PAGE-PKG-003"));

        DocumentPackagePage first = repository.packagesPage(1, 2);
        assertThat(first.entries()).extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly("DOC-PAGE-PKG-001", "DOC-PAGE-PKG-002");
        assertThat(first.hasMore()).isTrue();

        DocumentPackagePage last = repository.packagesPage(first.entries().get(1).id(), 2);
        assertThat(last.entries()).extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly("DOC-PAGE-PKG-003");
        assertThat(last.hasMore()).isFalse();

        InMemoryDocumentManagementRepository memory = new InMemoryDocumentManagementRepository();
        memory.savePackage(pagePackage("DOC-PAGE-PKG-001", "BUS-PAGE-PKG-001"));
        memory.savePackage(pagePackage("DOC-PAGE-PKG-002", "BUS-PAGE-PKG-002"));
        memory.savePackage(pagePackage("DOC-PAGE-PKG-003", "BUS-PAGE-PKG-003"));
        DocumentPackagePage memoryFirst = memory.packagesPage(4, 2);
        assertThat(memoryFirst.entries()).extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly("DOC-PAGE-PKG-001", "DOC-PAGE-PKG-002");

        long stableCursor = first.entries().get(0).id();
        long memoryStableCursor = memoryFirst.entries().get(0).id();
        repository.savePackage(pagePackage("DOC-PAGE-PKG-002", "BUS-PAGE-PKG-002", "已归档", "green"));
        memory.savePackage(pagePackage("DOC-PAGE-PKG-002", "BUS-PAGE-PKG-002", "已归档", "green"));
        assertThat(repository.packagesPage(stableCursor, 1).entries())
                .extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly("DOC-PAGE-PKG-002");
        assertThat(memory.packagesPage(memoryStableCursor, 1).entries())
                .extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly("DOC-PAGE-PKG-002");

        assertThatThrownBy(() -> repository.packagesPage(-1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> memory.packagesPage(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filtersPackagePagesByKeywordBeforeLimitAndEscapesLikeCharacters() throws SQLException {
        DocumentPackage codeMatch = pagePackageWithFields(
                "DOC-命中-编码", "普通标题", "BUS-PAGE-CODE", "普通负责人");
        DocumentPackage titleMatch = pagePackageWithFields(
                "DOC-PAGE-TITLE", "标题命中", "BUS-PAGE-TITLE", "普通负责人");
        DocumentPackage businessMatch = pagePackageWithFields(
                "DOC-PAGE-BUSINESS", "普通标题", "BUS-命中-业务", "普通负责人");
        DocumentPackage ownerMatch = pagePackageWithFields(
                "DOC-PAGE-OWNER", "普通标题", "BUS-PAGE-OWNER", "命中负责人");
        insertPackage(codeMatch);
        insertPackage(pagePackage("DOC-PAGE-NONE-001", "BUS-PAGE-NONE-001"));
        insertPackage(titleMatch);
        insertPackage(pagePackage("DOC-PAGE-NONE-002", "BUS-PAGE-NONE-002"));
        insertPackage(businessMatch);
        insertPackage(pagePackage("DOC-PAGE-NONE-003", "BUS-PAGE-NONE-003"));
        insertPackage(ownerMatch);
        String literalKeyword = "%_" + "\\";
        insertPackage(pagePackageWithFields(
                "DOC-PAGE-SPECIAL", "字面量" + literalKeyword + "值", "BUS-PAGE-SPECIAL", "普通负责人"));

        DocumentPackagePage first = repository.packagesPage("命中", 1, 2);
        assertThat(first.entries()).extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly(codeMatch.code(), titleMatch.code());
        assertThat(first.hasMore()).isTrue();
        DocumentPackagePage second = repository.packagesPage("命中", first.entries().get(1).id(), 2);
        assertThat(second.entries()).extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly(businessMatch.code(), ownerMatch.code());
        assertThat(second.hasMore()).isFalse();

        DocumentPackagePage special = repository.packagesPage(literalKeyword, 1, 10);
        assertThat(special.entries()).extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly("DOC-PAGE-SPECIAL");

        InMemoryDocumentManagementRepository memory = new InMemoryDocumentManagementRepository();
        memory.savePackage(codeMatch);
        memory.savePackage(pagePackage("DOC-PAGE-NONE-001", "BUS-PAGE-NONE-001"));
        memory.savePackage(titleMatch);
        memory.savePackage(pagePackage("DOC-PAGE-NONE-002", "BUS-PAGE-NONE-002"));
        memory.savePackage(businessMatch);
        memory.savePackage(pagePackage("DOC-PAGE-NONE-003", "BUS-PAGE-NONE-003"));
        memory.savePackage(ownerMatch);
        memory.savePackage(pagePackageWithFields(
                "DOC-PAGE-SPECIAL", "字面量" + literalKeyword + "值", "BUS-PAGE-SPECIAL", "普通负责人"));
        assertThat(memory.packagesPage("命中", 0, 2).entries())
                .extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly(codeMatch.code(), titleMatch.code());
        assertThat(memory.packagesPage(literalKeyword, 0, 10).entries())
                .extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly("DOC-PAGE-SPECIAL");

        assertThat(repository.packagesPage("   ", 1, 2).entries())
                .extracting(DocumentPackagePage.Entry::documentPackage)
                .extracting(DocumentPackage::code)
                .containsExactly(codeMatch.code(), "DOC-PAGE-NONE-001");
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
                "green",
                null
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
                "green",
                uploaded.objectType()
        ));

        assertThat(reloaded.attachments())
                .filteredOn(attachment -> attachment.code().equals("ATT-JDBC-UP-001"))
                .singleElement()
                .extracting(EvidenceAttachment::status)
                .isEqualTo("已归档");
    }

    @Test
    void savesAndUpdatesDocumentPackage() {
        DocumentPackage documentPackage = new DocumentPackage(
                "DOC-AUTH-PRE-APP-001",
                "账号前置审批证据包",
                "账号前置审批",
                "AUTH-PRE-APP-001",
                "系统管理员",
                "归档账号前置审批证据。",
                1,
                0,
                "待补本人核验附件",
                "账号审批证据长期保留",
                "2026-07-09 12:00:00",
                "amber"
        );

        repository.savePackage(documentPackage);
        repository.savePackage(new DocumentPackage(
                documentPackage.code(),
                documentPackage.title(),
                documentPackage.category(),
                documentPackage.businessCode(),
                documentPackage.owner(),
                documentPackage.purpose(),
                documentPackage.requiredEvidence(),
                1,
                "已关联证据",
                documentPackage.retentionPolicy(),
                "2026-07-09 12:05:00",
                "green"
        ));

        assertThat(new JdbcDocumentManagementRepository(dataSource).packages())
                .filteredOn(item -> item.code().equals("DOC-AUTH-PRE-APP-001"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.linkedEvidence()).isEqualTo(1);
                    assertThat(item.status()).isEqualTo("已关联证据");
                    assertThat(item.tone()).isEqualTo("green");
                });
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
                        tone varchar(20) not null,
                        object_type varchar(32)
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

    private EvidenceAttachment pageAttachment(String code, String businessCode) {
        return new EvidenceAttachment(
                code,
                "DOC-PAGE-001",
                "分页测试",
                businessCode,
                code + ".jpg",
                "image/jpeg",
                "local://" + code,
                "hot 180d -> archive",
                "测试上传",
                "已上传",
                "测试员",
                "sha256:" + code,
                "2026-10-18 10:00",
                "无",
                "green",
                null
        );
    }

    private DocumentPackage pagePackage(String code, String businessCode) {
        return pagePackage(code, businessCode, "待补附件", "amber");
    }

    private DocumentPackage pagePackage(String code, String businessCode, String status, String tone) {
        return pagePackageWithFields(code, "分页文档包", businessCode, "测试员", status, tone);
    }

    private DocumentPackage pagePackageWithFields(
            String code,
            String title,
            String businessCode,
            String owner
    ) {
        return pagePackageWithFields(code, title, businessCode, owner, "待补附件", "amber");
    }

    private DocumentPackage pagePackageWithFields(
            String code,
            String title,
            String businessCode,
            String owner,
            String status,
            String tone
    ) {
        return new DocumentPackage(
                code,
                title,
                "分页测试",
                businessCode,
                owner,
                "验证文档包稳定游标分页。",
                2,
                1,
                status,
                "保留 3 年",
                "2026-10-18 10:00",
                tone
        );
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
