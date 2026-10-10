package com.zhubao.erp.finance.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * 财务 JDBC 数据源。已有数据源时复用，避免重复建连。
 */
@Configuration
@ConditionalOnProperty(name = "zhubao.finance.repository", havingValue = "jdbc")
class FinanceJdbcDataSourceConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    DataSource financeDataSource(Environment environment) {
        String url = required(environment, "spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        String driverClassName = environment.getProperty("spring.datasource.driver-class-name");
        return new DriverManagerFinanceDataSource(url, username, password, driverClassName);
    }

    private String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required datasource property: " + key);
        }
        return value;
    }

    private static final class DriverManagerFinanceDataSource implements DataSource {
        private final String url;
        private final String username;
        private final String password;

        private DriverManagerFinanceDataSource(String url, String username, String password, String driverClassName) {
            this.url = url;
            this.username = username;
            this.password = password;
            if (driverClassName != null && !driverClassName.isBlank()) {
                try {
                    Class.forName(driverClassName);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("JDBC driver not found: " + driverClassName, ex);
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
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
