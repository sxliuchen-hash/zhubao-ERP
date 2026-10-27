package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.OrderChange;
import com.zhubao.erp.business.domain.OrderChangeStatus;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.JdbcOrderChangeRepository;
import com.zhubao.erp.business.infra.JdbcOrderRequirementRepository;
import com.zhubao.erp.finance.app.FinanceBillingService;
import com.zhubao.erp.finance.app.OrderChangePostingService;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 改单状态、应收和工资在同一条 JDBC 连接上提交。结果写入失败时整单回滚。
 */
class OrderChangeJdbcAtomicityTest {

    @Test
    void failedResultRollsBackReceivablePayrollAndAppliedStatus() throws Exception {
        DataSource dataSource = dataSource();
        createSchema(dataSource);
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        new BusinessOperationService(new StockLedgerService(new InMemoryStockLedgerRepository()), null, documents)
                .confirmInbound(new ConfirmInboundCommand(
                        "MD-JDBC", 1001L, 1001L, "AU9999", "2026-10-08", "顺丰", "SF-JDBC", "",
                        "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                        new BigDecimal("10"), "件", new BigDecimal("20"), "何琳", "", "原单",
                        "内壁刻字", "2026-10-20", "恒信珠宝"
                ));
        JdbcFinanceRepository finance = new JdbcFinanceRepository(dataSource);
        finance.saveReceivable(new ReceivableLine(
                "FIN-1", 1001L, "恒信珠宝", "2026-10", "PROCESS", "PROCESS|MD-JDBC|雕刻",
                "MD-JDBC", "MD-JDBC", "雕刻", BigDecimal.ONE, ChargeUnit.JOB,
                new BigDecimal("100.00"), new BigDecimal("100.00"), "", "原账单", "财务", "2026-10-09T10:00:00"
        ));
        JdbcOrderChangeRepository changes = new JdbcOrderChangeRepository(dataSource);
        CommandResultProbe probe = () -> {
            throw new IllegalStateException("结果写入失败");
        };
        OrderChangeService service = new OrderChangeService(
                changes,
                documents,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), probe),
                new OrderChangePostingService(finance, new FinanceBillingService(finance, documents, new StockLedgerService(new InMemoryStockLedgerRepository())), documents),
                null,
                null,
                new JdbcOrderRequirementRepository(dataSource)
        );
        AuthUserAccount receiver = account("收货员", RoleCode.RECEIVER);
        var draft = service.draft("MD-JDBC", "", "改为外圈刻字", "2026-11-01", "jdbc-change-001", receiver);
        service.submitFinance(draft.id(), receiver);
        assertThatThrownBy(() -> service.apply(draft.id(), "jdbc-change-apply", receiver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("结果写入失败");
        assertThat(changes.findById(draft.id()).orElseThrow().status()).isEqualTo(OrderChangeStatus.DEFERRED_TO_SETTLEMENT);
        assertThat(finance.findReceivableByCode("FIN-1").orElseThrow().amount()).isEqualByComparingTo("100.00");
        assertThat(finance.findReceivableBySourceKey("CHANGE-RED|" + draft.id())).isEmpty();
        assertThat(finance.listPayrollLines()).isEmpty();
        assertThat(new JdbcOrderRequirementRepository(dataSource).current("MD-JDBC", "")).isEmpty();
    }

    private static AuthUserAccount account(String operator, RoleCode role) {
        return new AuthUserAccount(
                operator, "hash", operator, role, DataScope.ALL, true, true,
                Instant.now(), 0, null, List.of(), false, false, List.of()
        );
    }

    @Test
    void duplicateRequestReturnsTheStoredRowAndConcurrentDraftsDoNotOverwriteIt() throws Exception {
        DataSource source = dataSource();
        createSchema(source);
        JdbcOrderChangeRepository repository = new JdbcOrderChangeRepository(source);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<OrderChange> left = pool.submit(() -> repository.save(draftRow("CHG-A", "change-jdbc-race", "甲要求")));
            Future<OrderChange> right = pool.submit(() -> repository.save(draftRow("CHG-B", "change-jdbc-race", "乙要求")));
            OrderChange first = left.get(10, TimeUnit.SECONDS);
            OrderChange second = right.get(10, TimeUnit.SECONDS);
            assertThat(first.id()).isEqualTo(second.id());
            OrderChange stored = repository.findByRequestId("change-jdbc-race").orElseThrow();
            assertThat(stored.id()).isEqualTo(first.id());
            assertThat(stored.newRequirement()).isIn("甲要求", "乙要求");
            String loser = "CHG-A".equals(stored.id()) ? "CHG-B" : "CHG-A";
            assertThat(repository.findById(loser)).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    private static OrderChange draftRow(String id, String requestId, String requirement) {
        return new OrderChange(
                id, "MD-JDBC", "", "旧要求", requirement, "2026-10-20", "2026-11-01",
                OrderChangeStatus.DRAFT, 1, false, BigDecimal.ZERO, BigDecimal.ZERO, "", "",
                requestId, "收货员", "2026-10-14T00:00:00", "2026-10-14T00:00:00",
                BigDecimal.ZERO, BigDecimal.ZERO, "", "", "",
                "雕刻、镶嵌、抛光", "雕刻、镶嵌、抛光", 0, ""
        );
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:order-change-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table biz_command_request (
                        id bigint primary key auto_increment,
                        request_id varchar(64) not null,
                        actor_id varchar(64) not null,
                        action_code varchar(64) not null,
                        target_type varchar(32) not null,
                        target_code varchar(90) not null,
                        payload_hash char(64) not null,
                        result_text varchar(2000) not null,
                        created_at timestamp default current_timestamp,
                        unique key uk_command_request_id (request_id)
                    )
                    """);
            statement.execute("""
                    create table biz_order_change (
                        id varchar(40) primary key,
                        mother_order_code varchar(90) not null,
                        work_order_code varchar(90) not null,
                        old_requirement varchar(500) not null,
                        new_requirement varchar(500) not null,
                        old_due_date varchar(40) not null,
                        new_due_date varchar(40) not null,
                        status_code varchar(32) not null,
                        version_no int not null,
                        amount_unchanged boolean not null,
                        customer_unit_price decimal(18, 2) not null,
                        worker_fee decimal(18, 2) not null,
                        finance_note varchar(500) not null,
                        confirmed_by varchar(64) not null,
                        request_id varchar(64) not null,
                        created_by varchar(64) not null,
                        created_at varchar(40) not null,
                        updated_at varchar(40) not null,
                        customer_delta decimal(18, 2) not null,
                        worker_delta decimal(18, 2) not null,
                        receivable_code varchar(40) not null,
                        process_name varchar(32) not null,
                        worker_name varchar(64) not null,
                        old_route varchar(200) not null,
                        new_route varchar(200) not null,
                        expected_version int not null,
                        rework_process varchar(32) not null,
                        unique key uk_order_change_request (request_id)
                    )
                    """);
            statement.execute("""
                    create table fin_receivable_line (
                        code varchar(80) primary key,
                        customer_id bigint not null,
                        customer_name varchar(120) not null,
                        `year_month` char(7) not null,
                        source_type varchar(20) not null,
                        source_key varchar(160) not null,
                        mother_order_code varchar(80) not null,
                        work_order_code varchar(80) not null,
                        process_name varchar(40) not null,
                        quantity decimal(18, 3) not null,
                        charge_unit varchar(20) not null,
                        unit_price decimal(18, 4) not null,
                        amount decimal(18, 2) not null,
                        reverses_code varchar(80) not null,
                        remark varchar(200) not null,
                        operator_name varchar(80) not null,
                        created_at varchar(40) not null,
                        unique key uk_fin_receivable_source (source_key)
                    )
                    """);
            statement.execute("""
                    create table fin_customer_receipt (
                        code varchar(80) primary key,
                        customer_id bigint not null,
                        customer_name varchar(120) not null,
                        `year_month` char(7) not null,
                        amount decimal(18, 2) not null,
                        received_on char(10) not null,
                        operator_name varchar(80) not null,
                        created_at varchar(40) not null
                    )
                    """);
            statement.execute("""
                    create table fin_receipt_allocation (
                        receipt_code varchar(80) not null,
                        line_no int not null,
                        receivable_code varchar(80) not null,
                        amount decimal(18, 2) not null
                    )
                    """);
            statement.execute("""
                    create table fin_payroll_line (
                        code varchar(80) primary key,
                        `year_month` char(7) not null,
                        operator_name varchar(80) not null,
                        order_code varchar(90) not null,
                        process_name varchar(40) not null,
                        mother_order_code varchar(80) not null,
                        customer_id bigint not null,
                        quantity decimal(18, 3) not null,
                        charge_unit varchar(20) not null,
                        unit_price decimal(18, 4) not null,
                        gross_amount decimal(18, 2) not null,
                        loss_deduction decimal(18, 2) not null,
                        net_amount decimal(18, 2) not null,
                        confirmed boolean not null,
                        source_key varchar(160) not null
                    )
                    """);
            statement.execute("""
                    create table fin_payroll_run (
                        `year_month` char(7) primary key,
                        confirmed boolean not null,
                        confirmed_by varchar(80),
                        confirmed_at varchar(40)
                    )
                    """);
            statement.execute("""
                    create table biz_order_requirement_version (
                        id varchar(40) primary key,
                        mother_order_code varchar(90) not null,
                        work_order_code varchar(90) not null,
                        version_no int not null,
                        requirement_text varchar(500) not null,
                        due_date varchar(40) not null,
                        route_text varchar(200) not null,
                        change_id varchar(40) not null,
                        effective_at varchar(40) not null
                    )
                    """);
            statement.execute("""
                    create table biz_execution_round (
                        id varchar(40) primary key,
                        mother_order_code varchar(90) not null,
                        source_work_order_code varchar(90) not null,
                        round_work_order_code varchar(90) not null,
                        process_name varchar(32) not null,
                        change_id varchar(40) not null,
                        supersedes_order_code varchar(90) not null,
                        created_at varchar(40) not null
                    )
                    """);
        }
    }
}
