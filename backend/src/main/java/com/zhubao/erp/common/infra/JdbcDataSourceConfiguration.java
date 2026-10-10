package com.zhubao.erp.common.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

@Configuration
@Conditional(JdbcDataSourceConfiguration.AnyJdbcRepository.class)
public class JdbcDataSourceConfiguration {

    /**
     * 任一领域选择 JDBC 时统一装配数据源，领域仓储开关与各自事务边界保持独立。
     */
    static final class AnyJdbcRepository implements Condition {
        private static final List<String> PREFIXES = List.of(
                "business", "business-document", "inventory", "approval", "documents", "finance",
                "register-import", "machine-output", "machine-profile", "label-template", "gateway",
                "hardware.poc", "security.menu", "security.customer-authorization", "auth.users", "auth.audit", "auth.pre-approval"
        );
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return PREFIXES.stream().anyMatch(prefix -> "jdbc".equalsIgnoreCase(context.getEnvironment().getProperty("zhubao." + prefix + ".repository")));
        }
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    DataSource erpDataSource(Environment environment) {
        String url = required(environment, "spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        String driverClassName = environment.getProperty("spring.datasource.driver-class-name");
        return new DriverManagerErpDataSource(url, username, password, driverClassName);
    }

    private String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required datasource property: " + key);
        }
        return value;
    }

    private static final class DriverManagerErpDataSource implements DataSource {

        private final String url;
        private final String username;
        private final String password;

        private DriverManagerErpDataSource(String url, String username, String password, String driverClassName) {
            this.url = url;
            this.username = username;
            this.password = password;
            if (driverClassName != null && !driverClassName.isBlank()) {
                try {
                    Class.forName(driverClassName);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("Datasource driver class not found: " + driverClassName, ex);
                }
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (isWrapperFor(iface)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return Objects.equals(iface, DataSource.class) || iface.isInstance(this);
        }
    }
}
