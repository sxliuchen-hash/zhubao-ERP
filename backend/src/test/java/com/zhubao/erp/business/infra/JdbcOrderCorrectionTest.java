package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderAdjustment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcOrderCorrectionTest {

    @Test
    void 母单调整记录写入失败时字段覆盖回滚() throws SQLException {
        JdbcDataSource source = source();
        createTables(source);
        execute(source, "insert into biz_mother_order (code, customer_name, material_code, goods_name_snapshot, quantity, weight) "
                + "values ('MD-1', '原客户', 'AU', '旧货品', 2, 10)");
        JdbcBusinessDocumentRepository repository = new JdbcBusinessDocumentRepository(source);
        MotherOrderRecord next = mother();

        assertThatThrownBy(() -> repository.correctMotherOrder(next, adjustment("MOTHER", "MD-1", "FAIL")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(value(source, "select customer_name from biz_mother_order where code = 'MD-1'"))
                .isEqualTo("原客户");
        assertThat(value(source, "select count(*) from biz_order_adjustment")).isEqualTo("0");

        repository.correctMotherOrder(next, adjustment("MOTHER", "MD-1", "已核对"));
        assertThat(value(source, "select customer_name from biz_mother_order where code = 'MD-1'"))
                .isEqualTo("新客户");
        assertThat(value(source, "select count(*) from biz_order_adjustment")).isEqualTo("1");
    }

    @Test
    void 子单调整记录写入失败时字段覆盖回滚() throws SQLException {
        JdbcDataSource source = source();
        createTables(source);
        execute(source, "insert into biz_work_order (code, customer_name, material_code, goods_name, quantity, weight) "
                + "values ('ZD-1', '原客户', 'AU', '旧货品', 2, 10)");
        JdbcBusinessDocumentRepository repository = new JdbcBusinessDocumentRepository(source);
        ChildWorkOrderRecord next = child();

        assertThatThrownBy(() -> repository.correctChildWorkOrder(next, adjustment("WORK_ORDER", "ZD-1", "FAIL")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(value(source, "select material_code from biz_work_order where code = 'ZD-1'"))
                .isEqualTo("AU");
        assertThat(value(source, "select count(*) from biz_order_adjustment")).isEqualTo("0");

        repository.correctChildWorkOrder(next, adjustment("WORK_ORDER", "ZD-1", "已核对"));
        assertThat(value(source, "select material_code from biz_work_order where code = 'ZD-1'"))
                .isEqualTo("PT");
        assertThat(value(source, "select count(*) from biz_order_adjustment")).isEqualTo("1");
    }

    private JdbcDataSource source() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:a4_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return source;
    }

    private void createTables(JdbcDataSource source) throws SQLException {
        execute(source, """
                create table biz_mother_order (
                    code varchar(80) primary key, customer_id bigint, customer_name varchar(120),
                    material_code varchar(80), goods_name_snapshot varchar(120), quantity decimal(18,3),
                    weight decimal(18,3), processing_requirement varchar(300), due_date varchar(40),
                    inbound_remark varchar(300))
                """);
        execute(source, """
                create table biz_work_order (
                    code varchar(80) primary key, customer_id bigint, customer_name varchar(120),
                    material_code varchar(80), goods_name varchar(120), quantity decimal(18,3),
                    weight decimal(18,3), processing_requirement varchar(300), due_date varchar(40),
                    process_names varchar(300))
                """);
        execute(source, """
                create table biz_order_adjustment (
                    id bigint auto_increment primary key, target_type varchar(20), target_code varchar(90),
                    before_text varchar(1000), after_text varchar(1000), reason varchar(200)
                        check (reason <> 'FAIL'), operator_name varchar(80), created_at varchar(40))
                """);
    }

    private MotherOrderRecord mother() {
        return new MotherOrderRecord("MD-1", 2L, null, "PT", "2026-10-19", "", "", "", "", "",
                "新货品", "CUSTOMER", true, "WH", new BigDecimal("3"), "件", new BigDecimal("11"),
                "RAW_WAITING", "L1", null, null, null, "管理员", "", "新备注", "OPEN", "2026-10-19T10:00",
                "抛光", "2026-11-01", "新客户");
    }

    private ChildWorkOrderRecord child() {
        return new ChildWorkOrderRecord("ZD-1", "MD-1", 2L, "新客户", "抛光", "2026-11-01", "PT", "新货品",
                new BigDecimal("3"), "件", new BigDecimal("11"), "WH", "抛光", "1234", "L2", "管理员",
                "2026-10-19T11:00");
    }

    private OrderAdjustment adjustment(String type, String code, String reason) {
        return new OrderAdjustment(type, code, "修改前", "修改后", reason, "管理员", "2026-10-19T12:00");
    }

    private void execute(JdbcDataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String value(JdbcDataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
