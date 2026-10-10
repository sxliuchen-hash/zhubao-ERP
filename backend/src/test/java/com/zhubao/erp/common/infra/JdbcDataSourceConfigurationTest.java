package com.zhubao.erp.common.infra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDataSourceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(JdbcDataSourceConfiguration.class);

    @Test
    void memoryRepositoriesDoNotRequireADatabase() {
        context.run(application -> assertThat(application).doesNotHaveBean(DataSource.class));
    }

    @Test
    void businessDocumentRepositoryCanUseJdbcIndependently() {
        context.withPropertyValues("zhubao.business-document.repository=JDBC", "spring.datasource.url=jdbc:h2:mem:shared-datasource",
                "spring.datasource.driver-class-name=org.h2.Driver").run(application -> {
            assertThat(application).hasSingleBean(DataSource.class);
            try (var connection = application.getBean(DataSource.class).getConnection()) {
                assertThat(connection.isValid(1)).isTrue();
            }
        });
    }

    @Test
    void multipleJdbcModulesShareOneDataSourceAndExistingBeanIsPreserved() {
        DataSource custom = new org.h2.jdbcx.JdbcDataSource();
        context.withBean(DataSource.class, () -> custom).withPropertyValues("zhubao.inventory.repository=jdbc",
                "zhubao.finance.repository=jdbc", "zhubao.auth.users.repository=jdbc").run(application -> {
            assertThat(application).hasSingleBean(DataSource.class);
            assertThat(application.getBean(DataSource.class)).isSameAs(custom);
        });
    }
}
