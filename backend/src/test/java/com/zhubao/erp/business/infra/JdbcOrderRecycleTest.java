package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.app.OrderMaintenanceCommands;
import com.zhubao.erp.business.app.OrderMaintenanceService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcOrderRecycleTest {
    @Test
    void 母单回收与恢复跨三仓储失败时整批回滚并可重试() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:a4_recycle_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        createTables(source);
        OrderMaintenanceService service = new OrderMaintenanceService(
                new JdbcBusinessDocumentRepository(source), new JdbcPrintJobRepository(source),
                new JdbcPrintRoutingRepository(source), new PermissionGuard(),
                new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source)));
        OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
        var recycle = new OrderMaintenanceCommands.RecycleCommand("MOTHER", "MD-1", "FAIL", "req-recycle-jdbc");

        assertThatThrownBy(() -> service.recycle(recycle, admin)).isInstanceOf(IllegalStateException.class);
        assertThat(value(source, "select count(*) from biz_mother_order where recycled_at is not null")).isZero();
        assertThat(value(source, "select count(*) from biz_print_job where recycled_at is not null")).isZero();
        assertThat(value(source, "select count(*) from biz_print_target_job where recycled_at is not null")).isZero();
        assertThat(value(source, "select count(*) from biz_command_request")).isZero();

        execute(source, "alter table biz_print_target_job drop constraint reject_target");
        service.recycle(recycle, admin);
        service.recycle(recycle, admin);
        assertThat(value(source, "select count(*) from biz_mother_order where recycled_at is not null")).isEqualTo(1);
        assertThat(value(source, "select count(*) from biz_print_job where recycled_at is not null")).isEqualTo(1);
        assertThat(value(source, "select count(*) from biz_print_target_job where recycled_at is not null")).isEqualTo(1);
        assertThat(value(source, "select count(*) from biz_command_request")).isEqualTo(1);

        execute(source, "alter table biz_print_target_job add constraint reject_restore check (recycle_reason is not null)");
        var restore = new OrderMaintenanceCommands.RestoreCommand("MOTHER", "MD-1", "req-restore-jdbc");
        assertThatThrownBy(() -> service.restore(restore, admin)).isInstanceOf(IllegalStateException.class);
        assertThat(value(source, "select count(*) from biz_mother_order where recycled_at is not null")).isEqualTo(1);
        assertThat(value(source, "select count(*) from biz_print_job where recycled_at is not null")).isEqualTo(1);
        assertThat(value(source, "select count(*) from biz_command_request")).isEqualTo(1);

        execute(source, "alter table biz_print_target_job drop constraint reject_restore");
        service.restore(restore, admin);
        service.restore(restore, admin);
        assertThat(value(source, "select count(*) from biz_mother_order where recycled_at is not null")).isZero();
        assertThat(value(source, "select count(*) from biz_print_job where recycled_at is not null")).isZero();
        assertThat(value(source, "select count(*) from biz_print_target_job where recycled_at is not null")).isZero();
        assertThat(value(source, "select count(*) from biz_command_request")).isEqualTo(2);
    }

    private void createTables(JdbcDataSource source) throws SQLException {
        execute(source, "create table biz_order_adjustment (id bigint auto_increment primary key, target_type varchar(20), "
                + "target_code varchar(90), before_text varchar(1000), after_text varchar(1000), reason varchar(200), "
                + "operator_name varchar(80), created_at varchar(40))");
        execute(source, "create table biz_mother_order (code varchar(90) primary key, recycled_at varchar(40), "
                + "recycled_by varchar(80), recycle_batch varchar(90), recycle_reason varchar(200))");
        execute(source, "create table biz_work_order (code varchar(90) primary key, mother_order_code varchar(90), "
                + "customer_id bigint, customer_name varchar(80), processing_requirement varchar(200), due_date varchar(40), "
                + "material_code varchar(40), goods_name varchar(80), quantity decimal(18,3), quantity_unit varchar(20), "
                + "weight decimal(18,3), warehouse_code varchar(40), process_names varchar(200), verification_code varchar(40), "
                + "ledger_code varchar(90), operator varchar(80), created_at varchar(40), recycled_at varchar(40), "
                + "recycled_by varchar(80), recycle_batch varchar(90), recycle_reason varchar(200))");
        execute(source, "create table biz_print_job (id varchar(90) primary key, business_code varchar(90), "
                + "recycled_at varchar(40), recycled_by varchar(80), recycle_batch varchar(90), recycle_reason varchar(200))");
        execute(source, "create table biz_print_batch (id varchar(90) primary key, business_code varchar(90))");
        execute(source, "create table biz_print_target_job (id varchar(90) primary key, batch_id varchar(90), "
                + "recycled_at varchar(40), recycled_by varchar(80), recycle_batch varchar(90), recycle_reason varchar(200), "
                + "constraint reject_target check (recycle_reason <> 'FAIL'))");
        execute(source, "create table biz_command_request (id bigint auto_increment primary key, request_id varchar(64) unique, "
                + "actor_id varchar(64), action_code varchar(64), target_type varchar(32), target_code varchar(90), "
                + "payload_hash char(64), result_text varchar(2000), created_at timestamp default current_timestamp)");
        execute(source, "insert into biz_mother_order (code) values ('MD-1')");
        execute(source, "insert into biz_print_job (id, business_code) values ('P-1', 'MD-1')");
        execute(source, "insert into biz_print_batch values ('B-1', 'MD-1')");
        execute(source, "insert into biz_print_target_job (id, batch_id) values ('J-1', 'B-1')");
    }

    private void execute(JdbcDataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int value(JdbcDataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
