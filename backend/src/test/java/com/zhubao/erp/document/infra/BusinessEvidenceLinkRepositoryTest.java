package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.BusinessEvidenceLink;
import com.zhubao.erp.document.domain.BusinessEvidenceLinkPage;
import com.zhubao.erp.document.domain.EvidenceLinkKey;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessEvidenceLinkRepositoryTest {

    private JdbcBusinessEvidenceLinkRepository jdbc;
    private InMemoryBusinessEvidenceLinkRepository memory;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        jdbc = new JdbcBusinessEvidenceLinkRepository(dataSource);
        memory = new InMemoryBusinessEvidenceLinkRepository();
    }

    @Test
    void findsOnlyExactTargetTypeAndCodeInStableIdOrder() {
        List<BusinessEvidenceLink> links = List.of(
                link("LINK-001", "MOTHER_ORDER", "DOC-001", "ATT-001"),
                link("LINK-002", "MOTHER_ORDER", "DOC-001", "ATT-002"),
                link("LINK-003", "WORK_ORDER", "DOC-001", "ATT-003"),
                link("LINK-004", "MOTHER_ORDER", "DOC-002", "ATT-004")
        );
        links.forEach(link -> {
            jdbc.insert(link);
            memory.insert(link);
        });

        assertThat(jdbc.findByTarget("MOTHER_ORDER", "DOC-001"))
                .extracting(BusinessEvidenceLink::linkCode)
                .containsExactly("LINK-001", "LINK-002");
        assertThat(memory.findByTarget("MOTHER_ORDER", "DOC-001"))
                .extracting(BusinessEvidenceLink::linkCode)
                .containsExactly("LINK-001", "LINK-002");
        assertThat(jdbc.findByTarget("WORK_ORDER", "DOC-001"))
                .extracting(BusinessEvidenceLink::linkCode)
                .containsExactly("LINK-003");
        assertThat(memory.findByTarget("WORK_ORDER", "DOC-001"))
                .extracting(BusinessEvidenceLink::linkCode)
                .containsExactly("LINK-003");
        assertThat(jdbc.findByTarget("MOTHER_ORDER", "DOC-NOT-FOUND")).isEmpty();
        assertThat(memory.findByTarget("MOTHER_ORDER", "DOC-NOT-FOUND")).isEmpty();
    }

    @Test
    void pagesExactTargetWithInterleavedRowsAndStableCursorAfterInsert() {
        List<BusinessEvidenceLink> links = List.of(
                link("LINK-PAGE-001", "MOTHER_ORDER", "DOC-PAGE-001", "ATT-PAGE-001"),
                link("LINK-PAGE-002", "MOTHER_ORDER", "DOC-PAGE-002", "ATT-PAGE-002"),
                link("LINK-PAGE-003", "WORK_ORDER", "DOC-PAGE-001", "ATT-PAGE-003"),
                link("LINK-PAGE-004", "MOTHER_ORDER", "DOC-PAGE-001", "ATT-PAGE-004"),
                link("LINK-PAGE-005", "MOTHER_ORDER", "DOC-PAGE-001", "ATT-PAGE-005")
        );
        links.forEach(link -> {
            jdbc.insert(link);
            memory.insert(link);
        });

        BusinessEvidenceLinkPage jdbcFirst = jdbc.findPageByTarget("MOTHER_ORDER", "DOC-PAGE-001", 0, 2);
        BusinessEvidenceLinkPage memoryFirst = memory.findPageByTarget("MOTHER_ORDER", "DOC-PAGE-001", 0, 2);
        assertPage(jdbcFirst);
        assertPage(memoryFirst);
        assertThat(jdbcFirst.entries()).extracting(BusinessEvidenceLinkPage.Entry::id)
                .containsExactly(1L, 4L);
        assertThat(memoryFirst.entries()).extracting(BusinessEvidenceLinkPage.Entry::id)
                .containsExactly(1L, 4L);

        BusinessEvidenceLink insertedAfterFirstPage =
                link("LINK-PAGE-006", "MOTHER_ORDER", "DOC-PAGE-001", "ATT-PAGE-006");
        jdbc.insert(insertedAfterFirstPage);
        memory.insert(insertedAfterFirstPage);
        long cursor = jdbcFirst.entries().get(1).id();

        BusinessEvidenceLinkPage jdbcLast = jdbc.findPageByTarget("MOTHER_ORDER", "DOC-PAGE-001", cursor, 2);
        BusinessEvidenceLinkPage memoryLast = memory.findPageByTarget("MOTHER_ORDER", "DOC-PAGE-001", cursor, 2);
        assertThat(jdbcLast.entries()).extracting(BusinessEvidenceLinkPage.Entry::link)
                .extracting(BusinessEvidenceLink::linkCode)
                .containsExactly("LINK-PAGE-005", "LINK-PAGE-006");
        assertThat(memoryLast.entries()).extracting(BusinessEvidenceLinkPage.Entry::link)
                .extracting(BusinessEvidenceLink::linkCode)
                .containsExactly("LINK-PAGE-005", "LINK-PAGE-006");
        assertThat(jdbcLast.hasMore()).isFalse();
        assertThat(memoryLast.hasMore()).isFalse();
    }

    @Test
    void rejectsInvalidEvidenceLinkPageCursorAndLimit() {
        assertThatThrownBy(() -> jdbc.findPageByTarget("MOTHER_ORDER", "DOC-PAGE-001", -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> memory.findPageByTarget("MOTHER_ORDER", "DOC-PAGE-001", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbc.findPageByTarget("MOTHER_ORDER", "DOC-PAGE-001", 0, 102))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> memory.findPageByTarget("MOTHER_ORDER", "DOC-PAGE-001", -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertPage(BusinessEvidenceLinkPage page) {
        assertThat(page.entries()).extracting(BusinessEvidenceLinkPage.Entry::link)
                .extracting(BusinessEvidenceLink::linkCode)
                .containsExactly("LINK-PAGE-001", "LINK-PAGE-004");
        assertThat(page.hasMore()).isTrue();
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table doc_business_evidence_link (
                        id bigint primary key auto_increment,
                        link_code varchar(40) not null,
                        attachment_code varchar(80) not null,
                        target_type varchar(32) not null,
                        target_code varchar(90) not null,
                        stage varchar(16) not null,
                        scene varchar(16) not null,
                        operation_type varchar(32) not null,
                        operation_code varchar(96) not null,
                        process_name varchar(20) not null,
                        clause_no varchar(32) not null,
                        operator_name varchar(80) not null,
                        linked_at varchar(30) not null,
                        link_key_hash char(64) not null,
                        created_at timestamp default current_timestamp
                    )
                    """);
        }
    }

    private BusinessEvidenceLink link(String linkCode, String targetType, String targetCode, String attachmentCode) {
        String stage = "收货";
        String scene = "现场";
        String operationType = "INBOUND";
        String operationCode = linkCode + "-OP";
        String processName = "";
        String clauseNo = "";
        return new BusinessEvidenceLink(
                linkCode,
                attachmentCode,
                targetType,
                targetCode,
                stage,
                scene,
                operationType,
                operationCode,
                processName,
                clauseNo,
                "测试员",
                "2026-10-18 10:00",
                EvidenceLinkKey.hash(targetType, targetCode, stage, scene, operationType, operationCode,
                        processName, clauseNo, attachmentCode)
        );
    }
}
