package com.zhubao.erp.ops.app;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.domain.UpgradeStatus;
import com.zhubao.erp.ops.infra.JdbcMigrationStore;
import com.zhubao.erp.ops.infra.JdbcSchemaInspector;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仅显式指定 MySQL 8.4 测试服务器时执行，每项测试创建并清理自己的隔离库。
 */
@EnabledIfSystemProperty(named = "zhubao.test.mysql.url", matches = ".+")
class MySqlMigrationCompatibilityTest {

    private MysqlDataSource server;
    private MysqlDataSource database;
    private String databaseName;

    @BeforeEach
    void createIsolatedDatabase() throws Exception {
        String url = System.getProperty("zhubao.test.mysql.url");
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("测试地址必须使用 jdbc:mysql://");
        }
        URI uri = URI.create(url.substring(5));
        String base = "jdbc:mysql://" + uri.getRawAuthority() + "/";
        String options = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        server = dataSource(base + options);
        String name = "zhubao_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = server.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(8);
            assertThat(connection.getMetaData().getDatabaseMinorVersion()).isEqualTo(4);
            statement.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4");
            databaseName = name;
        }
        database = dataSource(base + name + options);
    }

    @AfterEach
    void removeIsolatedDatabase() throws Exception {
        if (databaseName != null) {
            // 名称由本次测试生成，只有创建成功后才允许清理。
            try (Connection connection = server.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + databaseName + "`");
            }
        }
    }

    @Test
    void originalV36FailsAndPartialTablesDoNotProveCompletion() throws Exception {
        List<MigrationScript> scripts = MigrationCatalog.loadClasspath();
        new DatabaseUpgradeService(database, scripts.subList(0, 35)).upgrade(true);
        String raw = Files.readString(Path.of("src/main/resources/db/mysql/V36__m3_finance_operations.sql"), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new JdbcMigrationStore(database).executeScript(raw))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("year_month");
        JdbcSchemaInspector inspector = new JdbcSchemaInspector(database);
        assertThat(inspector.tableExists("fin_price_agreement")).isTrue();
        assertThat(inspector.tableExists("fin_payable_payment")).isFalse();
        assertThat(new DatabaseUpgradeService(database, scripts).plan().detectedBaseline()).isEqualTo(35);
    }

    @Test
    void exportedInitializationInstallsThroughLatestVersionAndAdoptsOriginalChecksums() throws Exception {
        String directory = System.getProperty("zhubao.test.mysql.init-directory");
        assertThat(directory).as("必须提供由 export-mysql-init.ps1 生成的初始化目录").isNotBlank();
        JdbcMigrationStore store = new JdbcMigrationStore(database);
        List<MigrationScript> scripts = MigrationCatalog.loadClasspath();
        List<Path> files;
        try (var paths = Files.list(Path.of(directory))) {
            files = paths.filter(path -> path.toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        assertThat(files).hasSize(scripts.size());
        for (int i = 0; i < files.size(); i++) {
            String sql = Files.readString(files.get(i), StandardCharsets.UTF_8);
            assertThat(sql).as(files.get(i).getFileName().toString()).isEqualTo(scripts.get(i).sql());
            store.executeScript(sql);
        }
        DatabaseUpgradeService service = new DatabaseUpgradeService(database, scripts);
        assertThat(service.plan().detectedBaseline()).isEqualTo(scripts.get(scripts.size() - 1).version());
        assertThat(service.adopt(true).applied()).hasSize(scripts.size());
        assertCurrentAndRepeatable(service, scripts);
    }

    @Test
    void classpathUpgradeFromV35InstallsFinanceAndRetainsOriginalChecksums() {
        List<MigrationScript> scripts = MigrationCatalog.loadClasspath();
        new DatabaseUpgradeService(database, scripts.subList(0, 35)).upgrade(true);
        DatabaseUpgradeService service = new DatabaseUpgradeService(database, scripts);
        service.upgrade(true);
        assertCurrentAndRepeatable(service, scripts);
    }

    @Test
    void directoryUpgradeFromEmptyDatabaseInstallsThroughLatestVersion() {
        List<MigrationScript> scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        DatabaseUpgradeService service = new DatabaseUpgradeService(database, scripts);
        service.upgrade(true);
        assertCurrentAndRepeatable(service, scripts);
    }

    private void assertCurrentAndRepeatable(DatabaseUpgradeService service, List<MigrationScript> scripts) {
        assertThat(service.plan().problems()).isEmpty();
        assertThat(service.plan().pending()).isEmpty();
        assertThat(service.plan().applied()).hasSize(scripts.size()).allSatisfy(record -> {
            assertThat(record.status()).isEqualTo(UpgradeStatus.SUCCESS);
            assertThat(record.checksum()).isEqualTo(scripts.get(record.version() - 1).checksum());
        });
        assertThat(service.upgrade(true).applied()).hasSize(scripts.size());
        JdbcSchemaInspector inspector = new JdbcSchemaInspector(database);
        for (String table : List.of("fin_receivable_line", "fin_customer_receipt", "fin_payroll_line",
                "fin_payroll_run", "fin_consumable_movement", "fin_depreciation_entry", "fin_payable")) {
            assertThat(inspector.columnExists(table, "year_month")).as(table).isTrue();
        }
        assertThat(inspector.tableExists("fin_payable_payment")).isTrue();
        assertThat(inspector.columnExists("fin_payable_payment", "idempotency_key")).isTrue();
        assertThat(inspector.columnExists("fin_customer_receipt", "idempotency_key")).isTrue();
    }

    private MysqlDataSource dataSource(String url) {
        MysqlDataSource source = new MysqlDataSource();
        source.setURL(url);
        source.setUser(System.getProperty("zhubao.test.mysql.user", "root"));
        source.setPassword(System.getProperty("zhubao.test.mysql.password", ""));
        return source;
    }
}
