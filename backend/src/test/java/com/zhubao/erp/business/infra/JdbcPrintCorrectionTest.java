package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.app.OrderMaintenanceCommands;
import com.zhubao.erp.business.app.OrderMaintenanceService;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.infra.JdbcPrintJobRepository;
import com.zhubao.erp.label.infra.JdbcPrintRoutingRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcPrintCorrectionTest {
    @Test
    void 单独目标任务回收与恢复同请求结果一起提交() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:a4_print_recycle_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        createTables(source);
        AtomicBoolean fail = new AtomicBoolean(true);
        OrderMaintenanceService service = new OrderMaintenanceService(
                new JdbcBusinessDocumentRepository(source), new JdbcPrintJobRepository(source),
                new JdbcPrintRoutingRepository(source), new PermissionGuard(),
                new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source), () -> {
                    if (fail.getAndSet(false)) throw new IllegalStateException("请求结果写入失败");
                }));
        OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
        var recycle = new OrderMaintenanceCommands.RecycleCommand("PRINT_TARGET", "J-1", "核纸后作废", "req-target-r");
        assertThatThrownBy(() -> service.recycle(recycle, admin)).isInstanceOf(IllegalStateException.class);
        assertThat(count(source, "select count(*) from biz_print_target_job where recycled_at is not null")).isZero();
        assertThat(count(source, "select count(*) from biz_command_request")).isZero();
        service.recycle(recycle, admin);
        service.recycle(recycle, admin);
        assertThat(count(source, "select count(*) from biz_print_target_job where recycled_at is not null")).isEqualTo(1);
        var restore = new OrderMaintenanceCommands.RestoreCommand("PRINT_TARGET", "J-1", "req-target-restore");
        service.restore(restore, admin);
        service.restore(restore, admin);
        assertThat(count(source, "select count(*) from biz_print_target_job where recycled_at is not null")).isZero();
        assertThat(count(source, "select count(*) from biz_command_request")).isEqualTo(2);
    }

    @Test
    void 调整记录失败时目标任务和请求编号一起回滚() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:a4_print_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        createTables(source);
        OrderMaintenanceService service = new OrderMaintenanceService(
                new JdbcBusinessDocumentRepository(source), new JdbcPrintJobRepository(source),
                new JdbcPrintRoutingRepository(source), new PermissionGuard(),
                new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source)));
        OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
        String revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                new JdbcPrintRoutingRepository(source).jobAny("J-1").orElseThrow().toString()
                        .getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> service.correctPrintTarget("J-1", new OrderMaintenanceCommands.PrintCorrection(
                2, "A", "Q1", "FAIL", "req-print-1", revision), admin))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(source, "select copies from biz_print_target_job where id = 'J-1'")).isEqualTo(1);
        assertThat(count(source, "select count(*) from biz_order_adjustment")).isZero();
        assertThat(count(source, "select count(*) from biz_command_request")).isZero();

        var corrected = new OrderMaintenanceCommands.PrintCorrection(2, "A", "Q1", "核对份数",
                "req-print-1", revision);
        service.correctPrintTarget("J-1", corrected, admin);
        service.correctPrintTarget("J-1", corrected, admin);
        assertThat(count(source, "select copies from biz_print_target_job where id = 'J-1'")).isEqualTo(2);
        assertThat(count(source, "select count(*) from biz_order_adjustment")).isEqualTo(1);
        assertThat(count(source, "select count(*) from biz_command_request")).isEqualTo(1);
        assertThatThrownBy(() -> service.correctPrintTarget("J-1", new OrderMaintenanceCommands.PrintCorrection(
                3, "A", "Q1", "旧页面", "req-print-2", revision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已被修改");
    }

    private void createTables(JdbcDataSource source) throws SQLException {
        execute(source, """
                create table biz_print_batch (
                    id varchar(90) primary key, request_id varchar(90), business_code varchar(90),
                    template_code varchar(40), copies_per_target int, payload_hash varchar(80),
                    content_snapshot clob, created_by varchar(80), created_at varchar(40))
                """);
        execute(source, """
                create table biz_print_target_job (
                    id varchar(90) primary key, batch_id varchar(90), target_station_id varchar(40),
                    target_queue_id varchar(40), copies int, status_code varchar(30),
                    claimed_station_id varchar(40), lease_until varchar(40), failure_reason varchar(200),
                    paper_check_result varchar(100), predecessor_id varchar(90), retry_reason varchar(200),
                    created_at varchar(40), updated_at varchar(40), recycled_at varchar(40),
                    recycled_by varchar(80), recycle_batch varchar(90), recycle_reason varchar(200))
                """);
        execute(source, """
                create table biz_order_adjustment (
                    id bigint auto_increment primary key, target_type varchar(30), target_code varchar(90),
                    before_text varchar(1000), after_text varchar(1000), reason varchar(200)
                        check (reason <> 'FAIL'), operator_name varchar(80), created_at varchar(40))
                """);
        execute(source, """
                create table biz_command_request (
                    id bigint auto_increment primary key, request_id varchar(64) unique,
                    actor_id varchar(64), action_code varchar(64), target_type varchar(32),
                    target_code varchar(90), payload_hash char(64), result_text varchar(2000),
                    created_at timestamp default current_timestamp)
                """);
        execute(source, "create table biz_mother_order (code varchar(90) primary key, recycled_at varchar(40), "
                + "recycled_by varchar(80), recycle_batch varchar(90), recycle_reason varchar(200))");
        execute(source, "create table biz_work_order (code varchar(90) primary key, recycled_at varchar(40), "
                + "recycled_by varchar(80), recycle_batch varchar(90), recycle_reason varchar(200))");
        execute(source, "insert into biz_print_batch values ('B-1', 'P-1', 'MD-1', 'WORK', 1, 'hash', 'snapshot', '管理员', 'now')");
        execute(source, """
                insert into biz_print_target_job (id, batch_id, target_station_id, target_queue_id, copies,
                    status_code, claimed_station_id, lease_until, failure_reason, paper_check_result,
                    predecessor_id, retry_reason, created_at, updated_at)
                values ('J-1', 'B-1', 'A', 'Q1', 1, 'PENDING', '', '', '', '', '', '', 'now', 'now')
                """);
    }

    private void execute(JdbcDataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(JdbcDataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
