package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthAuditEvent;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAuthAuditRepositoryTest {

    private JdbcAuthAuditRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        repository = new JdbcAuthAuditRepository(dataSource);
    }

    @Test
    void savesAndReadsRecentAuditEventsNewestFirst() {
        repository.save(event("AUTH-AUD-JDBC-001", "LOGIN", "leader", "SUCCESS", "abc001"));
        repository.save(event("AUTH-AUD-JDBC-002", "LOGOUT", "leader", "SUCCESS", "abc001"));

        assertThat(repository.findRecent(10))
                .extracting(AuthAuditEvent::eventId)
                .containsExactly("AUTH-AUD-JDBC-002", "AUTH-AUD-JDBC-001");
    }

    @Test
    void storesOnlyTokenSuffixProvidedByService() {
        repository.save(event("AUTH-AUD-JDBC-003", "LOGIN", "warehouse", "SUCCESS", "tok123"));

        assertThat(repository.findRecent(1))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.username()).isEqualTo("warehouse");
                    assertThat(event.sessionTokenSuffix()).isEqualTo("tok123");
                    assertThat(event.sessionTokenSuffix()).doesNotContain("Bearer");
                });
    }

    private AuthAuditEvent event(
            String eventId,
            String eventType,
            String username,
            String result,
            String tokenSuffix
    ) {
        return new AuthAuditEvent(
                eventId,
                eventType,
                username,
                username + "-operator",
                "WORKSHOP_LEADER",
                result,
                "jdbc audit test",
                "2026-07-08 17:30:00",
                tokenSuffix
        );
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table auth_audit_event (
                        id bigint primary key auto_increment,
                        event_id varchar(64) not null,
                        event_type varchar(32) not null,
                        username varchar(64),
                        operator varchar(128),
                        role varchar(64),
                        result varchar(32) not null,
                        reason varchar(255) not null,
                        occurred_at varchar(64) not null,
                        session_token_suffix varchar(16),
                        unique key uk_auth_audit_event_id (event_id)
                    )
                    """);
        }
    }
}
