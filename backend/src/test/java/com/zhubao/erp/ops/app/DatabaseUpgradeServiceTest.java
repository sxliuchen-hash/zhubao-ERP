package com.zhubao.erp.ops.app;

import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.domain.SchemaProbe;
import com.zhubao.erp.ops.domain.UpgradePlan;
import com.zhubao.erp.ops.domain.UpgradeStatus;
import com.zhubao.erp.ops.infra.JdbcSchemaInspector;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 升级控制用 H2 的 MySQL 兼容模式验证状态机。这不是真实 MySQL 安装或升级验收。
 */
class DatabaseUpgradeServiceTest {

    @TempDir
    Path directory;

    @Test
    void realCatalogKeepsContiguousVersionsThroughV48() {
        List<MigrationScript> scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        assertThat(scripts).hasSize(48);
        assertThat(scripts.get(0).version()).isEqualTo(1);
        assertThat(scripts.get(38).version()).isEqualTo(39);
        assertThat(scripts.get(39).version()).isEqualTo(40);
        assertThat(scripts.get(39).fileName()).isEqualTo("V40__m3_finance_payment_idempotency.sql");
        assertThat(scripts.get(40).version()).isEqualTo(41);
        assertThat(scripts.get(40).fileName()).isEqualTo("V41__m3_finance_receipt_idempotency.sql");
        assertThat(scripts.get(41).version()).isEqualTo(42);
        assertThat(scripts.get(41).fileName()).isEqualTo("V42__m3_business_evidence_link.sql");
        assertThat(scripts.get(42).version()).isEqualTo(43);
        assertThat(scripts.get(42).fileName()).isEqualTo("V43__m3_operation_progress.sql");
        assertThat(scripts.get(43).version()).isEqualTo(44);
        assertThat(scripts.get(43).fileName()).isEqualTo("V44__m3_command_request.sql");
        assertThat(scripts.get(44).version()).isEqualTo(45);
        assertThat(scripts.get(44).fileName()).isEqualTo("V45__m3_mother_order_courier_identity.sql");
        assertThat(scripts.get(45).version()).isEqualTo(46);
        assertThat(scripts.get(45).fileName()).isEqualTo("V46__m3_quality_round.sql");
        assertThat(scripts.get(46).version()).isEqualTo(47);
        assertThat(scripts.get(46).fileName()).isEqualTo("V47__m3_label_menu.sql");
        assertThat(scripts.get(47).version()).isEqualTo(48);
        assertThat(scripts.get(47).fileName()).isEqualTo("V48__m3_users_menu.sql");
        assertThat(MigrationCatalog.loadClasspath()).extracting(MigrationScript::version)
                .containsExactlyElementsOf(scripts.stream().map(MigrationScript::version).toList());
    }

    @Test
    void classpathV48CreatesUsersProbeAndOnlyAdministratorBinding() throws Exception {
        MigrationScript script = MigrationCatalog.loadClasspath().get(47);
        assertThat(script.version()).isEqualTo(48);
        assertThat(script.probe().kind()).isEqualTo(SchemaProbe.Kind.ROW);
        assertThat(script.probe().tableName()).isEqualTo("sys_navigation_menu");
        assertThat(script.probe().columnName()).isEqualTo("code");
        assertThat(script.probe().expectedValue()).isEqualTo("users");
        assertThat(script.sql()).isEqualTo(Files.readString(
                Path.of("src/main/resources/db/mysql/V48__m3_users_menu.sql"), StandardCharsets.UTF_8));

        DataSource dataSource = dataSource();
        execute(dataSource, "create table sys_navigation_menu (code varchar(60) primary key, label varchar(100), "
                + "module varchar(100), route varchar(100), button_codes varchar(200), sort_order int)");
        execute(dataSource, "create table sys_role_menu (role_code varchar(60), menu_code varchar(60), "
                + "primary key (role_code, menu_code))");
        execute(dataSource, "insert into sys_navigation_menu (code, button_codes) values ('configuration', 'AUTH_ACCOUNT_MANAGE')");
        execute(dataSource, "insert into sys_role_menu values ('GENERAL_MANAGER', 'users')");
        JdbcSchemaInspector inspector = new JdbcSchemaInspector(dataSource);
        assertThat(inspector.matches(script.probe())).isFalse();

        // 使用类路径实际加载的 SQL，重复执行同时验证菜单迁移的幂等性。
        for (int repetition = 0; repetition < 2; repetition++) {
            for (String statement : MigrationCatalog.statements(script.sql())) {
                execute(dataSource, statement);
            }
        }

        assertThat(inspector.matches(script.probe())).isTrue();
        assertThat(count(dataSource, "select count(*) from sys_navigation_menu where code = 'users' "
                + "and label = '用户管理' and route = '/users' "
                + "and button_codes = 'AUTH_ACCOUNT_MANAGE,CUSTOMER_AUTHORIZATION_MANAGE'"))
                .isEqualTo(1);
        assertThat(count(dataSource, "select count(*) from sys_role_menu where menu_code = 'users'"))
                .isEqualTo(1);
        assertThat(count(dataSource, "select count(*) from sys_role_menu where menu_code = 'users' and role_code = 'ADMIN'"))
                .isEqualTo(1);
        assertThat(count(dataSource, "select count(*) from sys_navigation_menu where code = 'configuration' and button_codes = ''"))
                .isEqualTo(1);
    }

    @Test
    void statementsKeepSemicolonsInsideQuotes() {
        assertThat(MigrationCatalog.statements("insert into t(note) values ('a;b'); select 1;"))
                .containsExactly("insert into t(note) values ('a;b')", "select 1");
    }

    @Test
    void v36ExecutionQuotesReservedColumnWithoutChangingHistoricalChecksum() throws Exception {
        Path source = Path.of("src/main/resources/db/mysql");
        List<MigrationScript> directoryScripts = MigrationCatalog.loadDirectory(source);
        List<MigrationScript> classpathScripts = MigrationCatalog.loadClasspath();
        for (int i = 0; i < directoryScripts.size(); i++) {
            MigrationScript script = directoryScripts.get(i);
            byte[] original = Files.readAllBytes(source.resolve(script.fileName()));
            assertThat(script.checksum()).isEqualTo(MigrationCatalog.checksum(original));
            assertThat(classpathScripts.get(i).sql()).isEqualTo(script.sql());
            if (script.version() == 36) {
                assertThat(script.sql()).contains("`year_month` CHAR(7)", "(customer_id, `year_month`)");
                assertThat(script.sql().replace("`year_month`", "year_month"))
                        .isEqualTo(new String(original, StandardCharsets.UTF_8));
                assertThat(script.probe().tableName()).isEqualTo("fin_payable_payment");
            } else {
                assertThat(script.sql()).isEqualTo(new String(original, StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void partialV36DoesNotCountAsCompletedBaseline() throws Exception {
        DataSource dataSource = dataSource();
        execute(dataSource, "create table fin_price_agreement (code varchar(60) primary key)");
        var probe = MigrationCatalog.loadClasspath().get(35).probe();
        assertThat(new JdbcSchemaInspector(dataSource).matches(probe)).isFalse();
    }

    @Test
    void emptyDatabaseInstallAndRepeatHaveNoExtraSideEffect() throws Exception {
        DataSource dataSource = dataSource();
        write(1, "create table probe_v1 (id int primary key);", "1\tTABLE\tprobe_v1");
        write(2, "insert into probe_v1(id) values (1);", "2\tROW\tprobe_v1\tid\t1");
        DatabaseUpgradeService service = service(dataSource);
        service.upgrade(true);
        service.upgrade(true);
        assertThat(count(dataSource, "select count(*) from probe_v1")).isEqualTo(1);
        assertThat(service.plan().pending()).isEmpty();
        assertThat(service.plan().applied()).extracting("status").containsOnly(UpgradeStatus.SUCCESS);
    }

    @Test
    void existingDatabaseIsNotMarkedCurrentWithoutBaseline() throws Exception {
        DataSource dataSource = dataSource();
        execute(dataSource, "create table probe_v1 (id int primary key)");
        write(1, "create table probe_v1 (id int primary key);", "1\tTABLE\tprobe_v1");
        write(2, "alter table probe_v1 add column note varchar(20);", "2\tCOLUMN\tprobe_v1\tnote");
        write(3, "alter table probe_v1 add column extra varchar(20);", "3\tCOLUMN\tprobe_v1\textra");
        DatabaseUpgradeService service = service(dataSource);
        UpgradePlan plan = service.plan();
        assertThat(plan.detectedBaseline()).isEqualTo(1);
        assertThat(plan.pending()).extracting(MigrationScript::version).containsExactly(2, 3);
        assertThat(plan.problems()).anyMatch(problem -> problem.contains("不能直接标成最新"));
        assertThatThrownBy(() -> service.upgrade(true)).isInstanceOf(UpgradeRejectedException.class);
        assertThat(new JdbcSchemaInspector(dataSource).columnExists("probe_v1", "note")).isFalse();

        service.adopt(true);
        assertThat(service.plan().applied()).singleElement().satisfies(record -> {
            assertThat(record.version()).isEqualTo(1);
            assertThat(record.errorMessage()).contains("未重放脚本");
        });
        service.upgrade(true);
        assertThat(new JdbcSchemaInspector(dataSource).tableExists("probe_v1")).isTrue();
        assertThat(service.plan().applied()).extracting("version").containsExactly(1, 2, 3);
    }

    @Test
    void gapInOldStructureRefusesBaseline() throws Exception {
        DataSource dataSource = dataSource();
        execute(dataSource, "create table probe_v1 (id int)");
        execute(dataSource, "create table probe_v3 (id int)");
        write(1, "create table probe_v1 (id int);", "1\tTABLE\tprobe_v1");
        write(2, "create table probe_v2 (id int);", "2\tTABLE\tprobe_v2");
        write(3, "create table probe_v3 (id int);", "3\tTABLE\tprobe_v3");
        DatabaseUpgradeService service = service(dataSource);
        assertThat(service.plan().structureGap()).isTrue();
        assertThatThrownBy(() -> service.adopt(true)).isInstanceOf(UpgradeRejectedException.class);
        assertThat(new JdbcSchemaInspector(dataSource).tableExists("schema_migration")).isFalse();
    }

    @Test
    void changedChecksumIsRejected() throws Exception {
        DataSource dataSource = dataSource();
        write(1, "create table probe_v1 (id int primary key);", "1\tTABLE\tprobe_v1");
        DatabaseUpgradeService service = service(dataSource);
        service.upgrade(true);
        Files.writeString(directory.resolve("V1__probe.sql"), "create table probe_v1 (id int primary key, changed int);", StandardCharsets.UTF_8);
        DatabaseUpgradeService changed = service(dataSource);
        assertThatThrownBy(() -> changed.upgrade(true))
                .isInstanceOf(UpgradeRejectedException.class)
                .hasMessageContaining("校验和");
        assertThat(changed.plan().applied()).singleElement().extracting("status").isEqualTo(UpgradeStatus.SUCCESS);
    }

    @Test
    void failureStopsLaterVersionsAndBlocksTheNextRun() throws Exception {
        DataSource dataSource = dataSource();
        write(1, "create table probe_v1 (id int primary key);", "1\tTABLE\tprobe_v1");
        write(2, "create table probe_bad (;", "2\tTABLE\tprobe_bad");
        write(3, "create table probe_v3 (id int primary key);", "3\tTABLE\tprobe_v3");
        DatabaseUpgradeService service = service(dataSource);
        assertThatThrownBy(() -> service.upgrade(true)).isInstanceOf(UpgradeRejectedException.class)
                .hasMessageContaining("不能整段回滚");
        assertThat(service.plan().applied()).extracting("status").containsExactly(UpgradeStatus.SUCCESS, UpgradeStatus.FAILED);
        assertThat(new JdbcSchemaInspector(dataSource).tableExists("probe_v3")).isFalse();
        assertThatThrownBy(() -> service.upgrade(true)).isInstanceOf(UpgradeRejectedException.class);
        assertThat(new JdbcSchemaInspector(dataSource).tableExists("probe_v3")).isFalse();
    }

    @Test
    void upgradeWithoutBackupConfirmationIsRejected() {
        assertThat(DatabaseUpgradeMain.run(new String[]{"upgrade", "--url", "jdbc:h2:mem:unused", "--username", "sa"}, System.out, System.err))
                .isEqualTo(2);
    }

    private DatabaseUpgradeService service(DataSource dataSource) {
        return new DatabaseUpgradeService(dataSource, MigrationCatalog.loadDirectory(directory));
    }

    private void write(int version, String sql, String probe) throws Exception {
        Files.writeString(directory.resolve("V" + version + "__probe.sql"), sql, StandardCharsets.UTF_8);
        List<String> lines = Files.exists(directory.resolve("upgrade-probes.tsv"))
                ? new ArrayList<>(Files.readAllLines(directory.resolve("upgrade-probes.tsv"), StandardCharsets.UTF_8))
                : new ArrayList<>();
        lines.add(probe);
        Files.write(directory.resolve("upgrade-probes.tsv"), lines, StandardCharsets.UTF_8);
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:upgrade-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void execute(DataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(DataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
