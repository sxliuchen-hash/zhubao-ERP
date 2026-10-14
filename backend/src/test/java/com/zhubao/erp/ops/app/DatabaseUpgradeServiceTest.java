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
    void realCatalogKeepsContiguousVersionsThroughV57() {
        List<MigrationScript> scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        assertThat(scripts).hasSize(57);
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
        assertThat(scripts.get(48).version()).isEqualTo(49);
        assertThat(scripts.get(48).fileName()).isEqualTo("V49__m3_process_progress.sql");
        assertThat(scripts.get(49).version()).isEqualTo(50);
        assertThat(scripts.get(49).fileName()).isEqualTo("V50__m3_stock_batch_code.sql");
        assertThat(scripts.get(50).version()).isEqualTo(51);
        assertThat(scripts.get(50).fileName()).isEqualTo("V51__m3_print_job.sql");
        assertThat(scripts.get(51).version()).isEqualTo(52);
        assertThat(scripts.get(51).fileName()).isEqualTo("V52__m3_order_change.sql");
        assertThat(scripts.get(52).version()).isEqualTo(53);
        assertThat(scripts.get(52).fileName()).isEqualTo("V53__m3_order_change_delta.sql");
        assertThat(scripts.get(53).version()).isEqualTo(54);
        assertThat(scripts.get(53).fileName()).isEqualTo("V54__m3_order_requirement_version.sql");
        assertThat(scripts.get(54).version()).isEqualTo(55);
        assertThat(scripts.get(54).fileName()).isEqualTo("V55__m3_label_trace.sql");
        assertThat(scripts.get(55).version()).isEqualTo(56);
        assertThat(scripts.get(55).fileName()).isEqualTo("V56__m3_compact_label_defaults.sql");
        assertThat(scripts.get(56).version()).isEqualTo(57);
        assertThat(scripts.get(56).fileName()).isEqualTo("V57__m3_label_layout.sql");
        assertThat(MigrationCatalog.loadClasspath()).extracting(MigrationScript::version)
                .containsExactlyElementsOf(scripts.stream().map(MigrationScript::version).toList());
    }

    @Test
    void v57为旧模板增加可空版式列() throws Exception {
        MigrationScript script = MigrationCatalog.loadClasspath().get(56);
        assertThat(script.probe().kind()).isEqualTo(SchemaProbe.Kind.COLUMN);
        assertThat(script.probe().tableName()).isEqualTo("sys_label_template");
        assertThat(script.probe().columnName()).isEqualTo("layout_json");
        DataSource dataSource = dataSource();
        execute(dataSource, "create table sys_label_template (id bigint primary key, template_code varchar(80))");
        execute(dataSource, "insert into sys_label_template (id, template_code) values (1, 'MOTHER-DEFAULT')");
        JdbcSchemaInspector inspector = new JdbcSchemaInspector(dataSource);
        assertThat(inspector.matches(script.probe())).isFalse();
        runOnce(dataSource, script.sql());
        assertThat(inspector.matches(script.probe())).isTrue();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select layout_json from sys_label_template where id = 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isNull();
        }
    }

    @Test
    void classpathV49ThroughV53ProbesMatchTargetStructure() throws Exception {
        List<MigrationScript> scripts = MigrationCatalog.loadClasspath();
        MigrationScript progress = scripts.get(48);
        MigrationScript batch = scripts.get(49);
        MigrationScript printJob = scripts.get(50);
        MigrationScript orderChange = scripts.get(51);
        MigrationScript delta = scripts.get(52);
        MigrationScript requirement = scripts.get(53);
        assertThat(progress.probe().kind()).isEqualTo(SchemaProbe.Kind.TABLE);
        assertThat(progress.probe().tableName()).isEqualTo("biz_process_progress_event");
        assertThat(batch.probe().kind()).isEqualTo(SchemaProbe.Kind.COLUMN);
        assertThat(batch.probe().tableName()).isEqualTo("inv_stock_ledger");
        assertThat(batch.probe().columnName()).isEqualTo("from_mother_order_code");
        assertThat(printJob.probe().tableName()).isEqualTo("biz_print_job");
        assertThat(orderChange.probe().tableName()).isEqualTo("biz_order_change");
        assertThat(delta.probe().kind()).isEqualTo(SchemaProbe.Kind.COLUMN);
        assertThat(delta.probe().columnName()).isEqualTo("worker_name");
        assertThat(requirement.probe().kind()).isEqualTo(SchemaProbe.Kind.TABLE);
        assertThat(requirement.probe().tableName()).isEqualTo("biz_execution_round");

        DataSource dataSource = dataSource();
        execute(dataSource, "create table inv_stock_balance (id bigint primary key, stock_status varchar(30) not null)");
        execute(dataSource, "create table inv_stock_ledger (id bigint primary key, to_stock_status varchar(30) not null)");
        JdbcSchemaInspector inspector = new JdbcSchemaInspector(dataSource);
        assertThat(inspector.matches(progress.probe())).isFalse();
        assertThat(inspector.matches(batch.probe())).isFalse();
        assertThat(inspector.matches(printJob.probe())).isFalse();
        assertThat(inspector.matches(orderChange.probe())).isFalse();
        assertThat(inspector.matches(delta.probe())).isFalse();
        assertThat(inspector.matches(requirement.probe())).isFalse();

        runTwice(dataSource, progress.sql());
        runTwice(dataSource, printJob.sql());
        runTwice(dataSource, orderChange.sql());
        runOnce(dataSource, batch.sql());
        runOnce(dataSource, delta.sql());
        runOnce(dataSource, requirement.sql());

        assertThat(inspector.matches(progress.probe())).isTrue();
        assertThat(inspector.matches(batch.probe())).isTrue();
        assertThat(inspector.matches(printJob.probe())).isTrue();
        assertThat(inspector.matches(orderChange.probe())).isTrue();
        assertThat(inspector.matches(delta.probe())).isTrue();
        assertThat(inspector.matches(requirement.probe())).isTrue();
        assertThat(inspector.tableExists("biz_process_progress")).isTrue();
        assertThat(inspector.columnExists("inv_stock_balance", "batch_code")).isTrue();
        assertThat(inspector.columnExists("biz_order_change", "customer_delta")).isTrue();
        assertThat(inspector.columnExists("biz_order_change", "rework_process")).isTrue();
        assertThat(inspector.tableExists("biz_order_requirement_version")).isTrue();
    }

    @Test
    void v56只更新未改动的内置默认模板() throws Exception {
        MigrationScript script = MigrationCatalog.loadClasspath().get(55);
        assertThat(script.probe().kind()).isEqualTo(SchemaProbe.Kind.ROW);
        assertThat(script.probe().tableName()).isEqualTo("sys_label_default_revision");
        assertThat(script.probe().expectedValue()).isEqualTo("V56");
        DataSource dataSource = dataSource();
        execute(dataSource, "create table sys_label_template (template_code varchar(80) primary key, "
                + "label_type varchar(40), created_by varchar(80), fields_json text, "
                + "printer_setting_json text, footer_left_source varchar(80), footer_right_source varchar(80))");
        String oldMotherFields = "[{\"label\":\"验证码\",\"source\":\"verificationCode\",\"sortOrder\":1},"
                + "{\"label\":\"客户\",\"source\":\"customer\",\"sortOrder\":2},"
                + "{\"label\":\"加工要求\",\"source\":\"processingRequirement\",\"sortOrder\":3},"
                + "{\"label\":\"期限\",\"source\":\"dueDate\",\"sortOrder\":4},"
                + "{\"label\":\"单号\",\"source\":\"code\",\"sortOrder\":5},"
                + "{\"label\":\"物料\",\"source\":\"goodsName\",\"sortOrder\":6},"
                + "{\"label\":\"数量\",\"source\":\"quantity\",\"sortOrder\":7},"
                + "{\"label\":\"重量\",\"source\":\"weight\",\"sortOrder\":8}]";
        String oldPrinter = "{\"widthMm\":60,\"heightMm\":40,\"dpi\":203,\"printerName\":\"\",\"copies\":1}";
        execute(dataSource, "insert into sys_label_template values ('MOTHER-DEFAULT','MOTHER','system','"
                + oldMotherFields + "','" + oldPrinter + "','express','receiver')");
        execute(dataSource, "insert into sys_label_template values ('WORK_ORDER-DEFAULT','WORK_ORDER','system','自定义字段','"
                + oldPrinter + "','','')");
        execute(dataSource, "insert into sys_label_template values ('OUTBOUND-DEFAULT','OUTBOUND','system','"
                + "[{\"label\":\"客户\",\"source\":\"customer\",\"sortOrder\":1},"
                + "{\"label\":\"数量\",\"source\":\"quantity\",\"sortOrder\":2},"
                + "{\"label\":\"重量\",\"source\":\"weight\",\"sortOrder\":3}]','"
                + oldPrinter + "','自定义脚注','receiver')");
        execute(dataSource, "insert into sys_label_template values ('REWORK-DEFAULT','REWORK','system','"
                + "[{\"label\":\"客户\",\"source\":\"customer\",\"sortOrder\":1},"
                + "{\"label\":\"物料\",\"source\":\"goodsName\",\"sortOrder\":2},"
                + "{\"label\":\"数量\",\"source\":\"quantity\",\"sortOrder\":3}]',"
                + "'自定义打印设置','','')");
        execute(dataSource, "insert into sys_label_template values ('MOTHER-CUSTOM','MOTHER','operator01','"
                + oldMotherFields + "','" + oldPrinter + "','express','receiver')");
        JdbcSchemaInspector inspector = new JdbcSchemaInspector(dataSource);
        assertThat(inspector.matches(script.probe())).isFalse();
        runTwice(dataSource, script.sql());
        assertThat(inspector.matches(script.probe())).isTrue();
        assertThat(value(dataSource, "MOTHER-DEFAULT", "fields_json")).contains("verificationCode").doesNotContain("goodsName");
        assertThat(value(dataSource, "MOTHER-DEFAULT", "printer_setting_json")).contains("\"widthMm\":40", "\"dpi\":300");
        assertThat(value(dataSource, "MOTHER-DEFAULT", "footer_left_source")).isEmpty();
        assertThat(value(dataSource, "WORK_ORDER-DEFAULT", "fields_json")).isEqualTo("自定义字段");
        assertThat(value(dataSource, "WORK_ORDER-DEFAULT", "printer_setting_json")).contains("\"widthMm\":40");
        assertThat(value(dataSource, "OUTBOUND-DEFAULT", "fields_json")).doesNotContain("weight");
        assertThat(value(dataSource, "OUTBOUND-DEFAULT", "footer_left_source")).isEqualTo("自定义脚注");
        assertThat(value(dataSource, "REWORK-DEFAULT", "fields_json")).doesNotContain("goodsName");
        assertThat(value(dataSource, "REWORK-DEFAULT", "printer_setting_json")).isEqualTo("自定义打印设置");
        assertThat(value(dataSource, "MOTHER-CUSTOM", "fields_json")).isEqualTo(oldMotherFields);
        assertThat(value(dataSource, "MOTHER-CUSTOM", "printer_setting_json")).isEqualTo(oldPrinter);
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

    private static String value(DataSource dataSource, String templateCode, String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "select " + column + " from sys_label_template where template_code = ?")) {
            statement.setString(1, templateCode);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static void runTwice(DataSource dataSource, String sql) throws Exception {
        for (int repetition = 0; repetition < 2; repetition++) {
            runOnce(dataSource, sql);
        }
    }

    private static void runOnce(DataSource dataSource, String sql) throws Exception {
        for (String statement : MigrationCatalog.statements(sql)) {
            // H2 不能在一条 ALTER 里连续加多列。测试按 ADD COLUMN 拆开执行，已发布脚本保持原样。
            for (String piece : h2AlterPieces(statement)) {
                execute(dataSource, piece);
            }
        }
    }

    static java.util.List<String> h2AlterPieces(String statement) {
        String trimmed = statement.trim();
        int add = trimmed.toLowerCase(java.util.Locale.ROOT).indexOf("add column");
        if (add < 0) {
            return java.util.List.of(trimmed);
        }
        String prefix = trimmed.substring(0, add).trim();
        String[] parts = trimmed.substring(add).split(",\\s*(?=ADD COLUMN)");
        if (parts.length == 1) {
            return java.util.List.of(trimmed);
        }
        java.util.List<String> pieces = new java.util.ArrayList<>();
        for (String part : parts) {
            pieces.add(prefix + " " + part.trim());
        }
        return pieces;
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
