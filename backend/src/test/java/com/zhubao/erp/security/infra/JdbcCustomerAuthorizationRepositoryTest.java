package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.CustomerAuthorization;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCustomerAuthorizationRepositoryTest {

    private JdbcCustomerAuthorizationRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        repository = new JdbcCustomerAuthorizationRepository(dataSource);
    }

    @Test
    void grantsAndReadsByUsername() {
        repository.grant(auth("sales01", "CUST-1001", "恒信珠宝"));
        repository.grant(auth("sales01", "CUST-1002", "雅辰饰品"));
        repository.grant(auth("sales02", "CUST-2001", "海蓝首饰"));

        assertThat(repository.findByUsername("sales01"))
                .extracting(CustomerAuthorization::customerCode)
                .containsExactly("CUST-1001", "CUST-1002");
        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    void grantUpsertsOnDuplicateKey() {
        repository.grant(auth("sales01", "CUST-1001", "恒信珠宝"));
        repository.grant(auth("sales01", "CUST-1001", "恒信珠宝改名"));

        assertThat(repository.findByUsername("sales01"))
                .singleElement()
                .satisfies(row -> assertThat(row.customerName()).isEqualTo("恒信珠宝改名"));
    }

    @Test
    void revokeRemovesRow() {
        repository.grant(auth("sales01", "CUST-1001", "恒信珠宝"));

        assertThat(repository.revoke("sales01", "CUST-1001")).isTrue();
        assertThat(repository.revoke("sales01", "CUST-1001")).isFalse();
        assertThat(repository.findByUsername("sales01")).isEmpty();
    }

    private CustomerAuthorization auth(String username, String customerCode, String customerName) {
        return new CustomerAuthorization(username, customerCode, customerName, "总经理", "2026-07-09T10:00:00");
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table sys_customer_authorization (
                        id bigint primary key auto_increment,
                        username varchar(64) not null,
                        customer_code varchar(120) not null,
                        customer_name varchar(120),
                        granted_by varchar(80),
                        granted_at varchar(40),
                        unique key uk_customer_auth (username, customer_code)
                    )
                    """);
        }
    }
}
