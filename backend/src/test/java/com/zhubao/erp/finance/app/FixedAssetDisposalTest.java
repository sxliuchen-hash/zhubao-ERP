package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetDispositionView;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetDispositionRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetDispositionRepository;
import com.zhubao.erp.finance.web.FixedAssetDispositionController;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedAssetDisposalTest {

    private final OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 报废不生成收款也不扣工资() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetDispositionService service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        FixedAsset asset = legacy();
        books.saveAsset(asset);
        books.saveDepreciation(new com.zhubao.erp.finance.domain.DepreciationEntry(
                "FIN-DEP-1", "FIN-AST-1", "2026-10", new BigDecimal("333.33")));
        assertThatThrownBy(() -> service.record(
                "h06-zero-01", finance, "FIN-AST-1", "SCRAP", "2026-10-20", "无法修复", "", new BigDecimal("0")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知金额不能写成 0");
        assertThatThrownBy(() -> service.record(
                "h06-cash-01", finance, "FIN-AST-1", "DISPOSE", "2026-10-20", "出售", "", new BigDecimal("300.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("处置回款必须另有真实资金来源");

        FixedAssetDispositionView scrapped = service.record(
                "h06-scrap01", finance, "FIN-AST-1", "报废", "2026-10-20", "无法修复", "", null);
        assertThat(scrapped.kindLabel()).isEqualTo("报废");
        assertThat(scrapped.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(scrapped.netValue()).isNull();
        assertThat(scrapped.depreciationImpactLabel()).isEqualTo("待核");
        assertThat(scrapped.proceedsAmount()).isNull();
        assertThat(scrapped.received()).isFalse();
        assertThat(books.listAssets()).containsExactly(asset);
        assertThat(books.listDepreciations()).hasSize(1);
        assertThat(books.listReceipts()).isEmpty();
        assertThat(books.listReceivables()).isEmpty();
        assertThat(books.listPayrollLines()).isEmpty();
        assertThat(books.listPayables()).isEmpty();

        books.saveReceipt(new CustomerReceipt(
                "RC-1", 8L, "甲客户", "2026-10", new BigDecimal("300.00"), "2026-10-21",
                List.of(), "财务", "2026-10-21T08:00:00"));
        FixedAssetDispositionView sold = service.record(
                "h06-sold-01", finance, "FIN-AST-1", "DISPOSE", "2026-10-21", "出售旧机", "RC-1", null);
        assertThat(sold.proceedsAmount()).isEqualByComparingTo("300.00");
        assertThat(sold.received()).isTrue();
        assertThat(sold.netValue()).isNull();
        assertThat(books.listReceipts()).hasSize(1);
        assertThat(books.listReceivables()).isEmpty();
        assertThat(books.listPayrollLines()).isEmpty();
        assertThat(books.listAssets()).containsExactly(asset);
        assertThatThrownBy(() -> service.record(
                "h06-sold-02", finance, "FIN-AST-1", "DISPOSE", "2026-10-22", "再卖一次", "RC-1", new BigDecimal("300.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这笔回款已经对应过处置");
        assertThat(books.listReceipts()).hasSize(1);
        assertThat(books.listAssets()).containsExactly(asset);
    }

    @Test
    void 失败不留下处置也不删卡() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        books.saveAsset(legacy());
        FixedAssetDispositionService failing = service(books, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.record(
                "h06-fail-01", finance, "FIN-AST-1", "STOP", "2026-10-20", "暂停使用", "", null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(books.listAssets()).hasSize(1);
        assertThat(books.listReceipts()).isEmpty();
        assertThat(books.listPayrollLines()).isEmpty();
    }

    @Test
    void 未授权不能处置() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetDispositionService service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy());
        OperatorContext keeper = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThatThrownBy(() -> service.record(
                "h06-deny-01", keeper, "FIN-AST-1", "SCRAP", "2026-10-20", "无法修复", "", null))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(books.listAssets()).hasSize(1);
    }

    @Test
    void jdbc报废保留原卡和已记折旧() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFinanceRepository books = new JdbcFinanceRepository(dataSource);
        FixedAssetDispositionService service = new FixedAssetDispositionService(
                books, new JdbcFixedAssetDispositionRepository(dataSource),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        books.saveAsset(legacy());
        books.saveDepreciation(new com.zhubao.erp.finance.domain.DepreciationEntry(
                "FIN-DEP-1", "FIN-AST-1", "2026-10", new BigDecimal("333.33")));
        FixedAssetDispositionView scrapped = service.record(
                "h06-jdbc-01", finance, "FIN-AST-1", "SCRAP", "2026-10-20", "无法修复", null, null);
        assertThat(scrapped.netValue()).isNull();
        assertThat(scrapped.proceedsAmount()).isNull();
        assertThat(scrapped.depreciationImpactLabel()).isEqualTo("待核");
        assertThat(books.listAssets()).hasSize(1);
        assertThat(books.listDepreciations()).hasSize(1);
        assertThat(books.listDepreciations().get(0).amount()).isEqualByComparingTo("333.33");
    }

    @Test
    void 接口不把报废显示成已收款() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetDispositionService service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy());
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(finance);
        FixedAssetDispositionController controller = new FixedAssetDispositionController(service, actors);
        FixedAssetDispositionView saved = controller.record(new FixedAssetDispositionController.RecordRequest(
                "h06-http-01", "FIN-AST-1", "SCRAP", "2026-10-20", "无法修复", "", null
        ), request).data();
        assertThat(saved.received()).isFalse();
        assertThat(saved.proceedsAmount()).isNull();
        assertThat(saved.netValue()).isNull();
        assertThat(controller.list("FIN-AST-1", request).data()).hasSize(1);
    }

    private FixedAssetDispositionService service(InMemoryFinanceRepository books, com.zhubao.erp.work.app.BusinessWorkUnit unit) {
        return new FixedAssetDispositionService(books, new InMemoryFixedAssetDispositionRepository(), unit, guard);
    }

    private static FixedAsset legacy() {
        return new FixedAsset(
                "FIN-AST-1", "抛光机", "设备", new BigDecimal("1000.00"), new BigDecimal("100.00"),
                "2026-09", 12, "财务", "2026-09-01T08:00:00");
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        var scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (int version : new int[]{36, 44, 112}) {
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
