package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetRepairView;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetRepairRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetRepairRepository;
import com.zhubao.erp.finance.web.FixedAssetRepairController;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedAssetRepairSourceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void 每次付款凭据独立保存并可回放读取(boolean jdbc) throws Exception {
        var ds = jdbc ? dataSource() : null;
        com.zhubao.erp.finance.domain.FinanceRepository books = jdbc ? new JdbcFinanceRepository(ds) : new InMemoryFinanceRepository();
        com.zhubao.erp.finance.domain.FixedAssetRepairRepository repairs = jdbc ? new JdbcFixedAssetRepairRepository(ds) : new InMemoryFixedAssetRepairRepository();
        com.zhubao.erp.work.app.BusinessWorkUnit unit = jdbc ? new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds))
                : new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        books.saveAsset(legacy());
        FixedAssetRepairService service = new FixedAssetRepairService(books, repairs, unit, guard);
        var recorded = service.record("repair-evidence-start", finance, legacy().code(), "2026-10-08", "维修商", "维修照片");
        service.confirm("repair-evidence-cost", finance, recorded.repairId(), new BigDecimal("100.00"), "费用凭据");
        var first = service.pay("repair-payment-first", finance, recorded.repairId(), new BigDecimal("40.00"), "2026-10-09", "付款凭据甲");
        var last = service.pay("repair-payment-last", finance, recorded.repairId(), new BigDecimal("60.00"), "2026-10-10", "付款凭据乙");
        assertThat(first.payments()).hasSize(1);
        assertThat(last.payments()).extracting(com.zhubao.erp.finance.domain.FixedAssetRepairPayment::voucher)
                .containsExactlyInAnyOrder("付款凭据甲", "付款凭据乙");
        assertThat(service.pay("repair-payment-last", finance, recorded.repairId(), new BigDecimal("60.00"), "2026-10-10", "付款凭据乙").payments()).hasSize(2);
        assertThat(last.evidenceLabel()).isEqualTo("费用凭据");
        assertThat(last.paidAmount()).isEqualByComparingTo("100.00");
        assertThat(books.listPayablePayments()).extracting(com.zhubao.erp.finance.domain.PayablePayment::code)
                .containsExactlyInAnyOrderElementsOf(last.payments().stream().map(com.zhubao.erp.finance.domain.FixedAssetRepairPayment::paymentCode).toList());
        if (jdbc) assertThat(new JdbcFixedAssetRepairRepository(ds).payments(recorded.repairId())).hasSize(2);
    }

    private final OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 确认费用只记一次且付款不报废() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetRepairRepository repairs = new InMemoryFixedAssetRepairRepository();
        FixedAssetRepairService service = service(books, repairs, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        FixedAsset asset = legacy();
        books.saveAsset(asset);

        FixedAssetRepairView recorded = service.record("h04-rec-001", finance, "FIN-AST-1", "2026-10-08", "", "");
        assertThat(recorded.stateLabel()).isEqualTo("维修中");
        assertThat(recorded.handoverLabel()).isEqualTo("待补");
        assertThat(recorded.evidenceLabel()).isEqualTo("待补");
        assertThat(recorded.costAmount()).isNull();
        assertThat(recorded.paidAmount()).isNull();
        assertThat(recorded.paid()).isFalse();
        assertThat(recorded.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(books.listPayables()).isEmpty();
        assertThat(books.listAssets()).containsExactly(asset);
        assertThat(books.listDepreciations()).isEmpty();

        assertThatThrownBy(() -> service.confirm("h04-bad-001", finance, recorded.repairId(), null, "EV-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金额未齐，不能确认维修费用");
        assertThatThrownBy(() -> service.confirm("h04-bad-002", finance, recorded.repairId(), new BigDecimal("0"), "EV-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知金额不能写成 0");
        assertThatThrownBy(() -> service.confirm("h04-bad-003", finance, recorded.repairId(), new BigDecimal("200.00"), ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("维修费用必须有凭据");
        assertThatThrownBy(() -> service.pay("h04-pay-bad", finance, recorded.repairId(), new BigDecimal("80.00"), "2026-10-09", "PV-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("费用尚未确认，不能付款");
        assertThat(books.listPayables()).isEmpty();

        FixedAssetRepairView confirmed = service.confirm(
                "h04-ok-0001", finance, recorded.repairId(), new BigDecimal("200.00"), "EV-1");
        assertThat(confirmed.stateLabel()).isEqualTo("费用已确认");
        assertThat(confirmed.costAmount()).isEqualByComparingTo("200.00");
        assertThat(confirmed.paid()).isFalse();
        assertThat(confirmed.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(confirmed.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(books.listPayables()).hasSize(1);
        assertThat(books.listPayables().get(0).sourceCode()).isEqualTo("REPAIR|" + recorded.repairId());
        assertThat(books.listDepreciations()).isEmpty();
        FixedAssetRepairView replay = service.confirm(
                "h04-ok-0001", finance, recorded.repairId(), new BigDecimal("200.00"), "EV-1");
        assertThat(replay.payableCode()).isEqualTo(confirmed.payableCode());
        assertThatThrownBy(() -> service.confirm("h04-ok-0002", finance, recorded.repairId(), new BigDecimal("200.00"), "EV-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("维修费用已经确认");
        assertThat(books.listPayables()).hasSize(1);

        assertThatThrownBy(() -> service.pay("h04-pay-nop", finance, recorded.repairId(), new BigDecimal("80.00"), "2026-10-09", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有付款凭据，不能标成已付");
        FixedAssetRepairView paid = service.pay(
                "h04-pay-001", finance, recorded.repairId(), new BigDecimal("80.00"), "2026-10-09", "PV-1");
        assertThat(paid.costAmount()).isEqualByComparingTo("200.00");
        assertThat(paid.paidAmount()).isEqualByComparingTo("80.00");
        assertThat(paid.paid()).isTrue();
        assertThat(paid.stateLabel()).isEqualTo("费用已确认");
        assertThat(books.listPayables()).hasSize(1);
        assertThat(books.listPayablePayments()).hasSize(1);
        assertThat(books.listAssets()).containsExactly(asset);
        assertThat(books.listDepreciations()).isEmpty();
    }

    @Test
    void 确认失败不留下应付() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetRepairRepository repairs = new InMemoryFixedAssetRepairRepository();
        FixedAssetRepairService service = service(books, repairs, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy());
        FixedAssetRepairView recorded = service.record("h04-rec-002", finance, "FIN-AST-1", "2026-10-08", "机修", "EV-2");
        FixedAssetRepairService failing = service(books, repairs, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.confirm("h04-fail-01", finance, recorded.repairId(), new BigDecimal("50.00"), "EV-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repairs.find(recorded.repairId()).orElseThrow().state().name()).isEqualTo("RECORDED");
        assertThat(books.listPayables()).isEmpty();
        assertThat(books.listAssets()).hasSize(1);
        assertThat(books.listDepreciations()).isEmpty();
    }

    @Test
    void 未授权不能确认维修费用() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetRepairService service = service(books, new InMemoryFixedAssetRepairRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy());
        OperatorContext keeper = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThatThrownBy(() -> service.record("h04-deny-01", keeper, "FIN-AST-1", "2026-10-08", "", ""))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(books.listPayables()).isEmpty();
    }

    @Test
    void jdbc维修付款不重复记成本() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFinanceRepository books = new JdbcFinanceRepository(dataSource);
        JdbcFixedAssetRepairRepository repairs = new JdbcFixedAssetRepairRepository(dataSource);
        FixedAssetRepairService service = new FixedAssetRepairService(
                books, repairs, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        books.saveAsset(legacy());
        FixedAssetRepairView recorded = service.record("h04-jdbc-r1", finance, "FIN-AST-1", "2026-10-08", "机修", "");
        FixedAssetRepairView confirmed = service.confirm("h04-jdbc-c1", finance, recorded.repairId(), new BigDecimal("200.00"), "EV-9");
        FixedAssetRepairView paid = service.pay("h04-jdbc-p1", finance, recorded.repairId(), new BigDecimal("200.00"), "2026-10-10", "PV-9");
        assertThat(paid.payableCode()).isEqualTo(confirmed.payableCode());
        assertThat(paid.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(paid.costAmount()).isEqualByComparingTo("200.00");
        assertThat(paid.paidAmount()).isEqualByComparingTo("200.00");
        assertThat(count(dataSource, "select count(*) from fin_fixed_asset")).isEqualByComparingTo("1");
        assertThat(count(dataSource, "select original_value from fin_fixed_asset")).isEqualByComparingTo("1000.00");
        assertThat(count(dataSource, "select count(*) from fin_payable")).isEqualByComparingTo("1");
        assertThat(count(dataSource, "select count(*) from fin_payable_payment")).isEqualByComparingTo("1");
        assertThat(count(dataSource, "select count(*) from fin_depreciation_entry")).isEqualByComparingTo("0");
        assertThat(count(dataSource, "select sum(amount) from fin_payable")).isEqualByComparingTo("200.00");
    }

    @Test
    void 接口把未确认费用留空() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetRepairService service = service(books, new InMemoryFixedAssetRepairRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy());
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(finance);
        FixedAssetRepairController controller = new FixedAssetRepairController(service, actors);
        FixedAssetRepairView saved = controller.record(new FixedAssetRepairController.RecordRequest(
                "h04-http-01", "FIN-AST-1", "2026-10-08", "", ""
        ), request).data();
        assertThat(saved.stateLabel()).isEqualTo("维修中");
        assertThat(saved.costAmount()).isNull();
        assertThat(controller.list("FIN-AST-1", request).data()).hasSize(1);
        assertThat(books.listPayables()).isEmpty();
    }

    private FixedAssetRepairService service(InMemoryFinanceRepository books, InMemoryFixedAssetRepairRepository repairs,
                                            com.zhubao.erp.work.app.BusinessWorkUnit unit) {
        return new FixedAssetRepairService(books, repairs, unit, guard);
    }

    private static FixedAsset legacy() {
        return new FixedAsset(
                "FIN-AST-1", "抛光机", "设备", new BigDecimal("1000.00"), new BigDecimal("100.00"),
                "2026-09", 12, "财务", "2026-09-01T08:00:00");
    }

    private static BigDecimal count(JdbcDataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getBigDecimal(1);
        }
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        var scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (int version : new int[]{36, 44, 111, 139}) {
                String sql = scripts.stream().filter(script -> script.version() == version).findFirst().orElseThrow().sql();
                for (String item : MigrationCatalog.statements(sql)) {
                    if (item.trim().regionMatches(true, 0, "create", 0, 6)) {
                        statement.execute(item.trim());
                    }
                }
            }
        }
        return dataSource;
    }
}
