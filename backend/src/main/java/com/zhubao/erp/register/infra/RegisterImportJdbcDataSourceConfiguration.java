package com.zhubao.erp.register.infra;

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
import java.util.Objects;
import java.util.logging.Logger;

@Configuration
@ConditionalOnProperty(name = "zhubao.register-import.repository", havingValue = "jdbc")
class RegisterImportJdbcDataSourceConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    DataSource registerImportDataSource(Environment environment) {
        String url = required(environment, "spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        String driverClassName = environment.getProperty("spring.datasource.driver-class-name");
        return new DriverManagerRegisterImportDataSource(url, username, password, driverClassName);
    }

    private String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required datasource property: " + key);
        }
        return value;
    }

    private static final class DriverManagerRegisterImportDataSource implements DataSource {

        private final String url;
        private final String username;
        private final String password;

        private DriverManagerRegisterImportDataSource(String url, String username, String password, String driverClassName) {
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
