package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetDepreciationRun;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.finance.web.FixedAssetDepreciationController;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedAssetDepreciationContinuityTest {

    private final OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 历史金额不符或缺月不自动补尾差() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        books.saveAsset(asset("FIN-AST-1"));
        books.saveDepreciation(new DepreciationEntry("OLD", "FIN-AST-1", "2026-10", new BigDecimal("1.00")));
        var service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        assertThatThrownBy(() -> service.run("dep-history-mismatch", finance, "2026-11", null)).hasMessageContaining("金额与计划不一致");
        assertThatThrownBy(() -> service.run("dep-history-gap", finance, "2026-12", null)).hasMessageContaining("历史折旧");
        assertThat(books.listDepreciations()).hasSize(1);
    }

    @Test
    void 小额长期资产折旧不为负且合计不超过基础() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        books.saveAsset(new FixedAsset("TINY", "工具", "设备", new BigDecimal("1.00"), BigDecimal.ZERO, "2026-01", 200, "财务", "2026-01-01"));
        var service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        for (int i = 0; i < 200; i++) service.run("tiny-month-" + i, finance, java.time.YearMonth.of(2026, 1).plusMonths(i).toString(), null);
        assertThat(books.listDepreciations()).allSatisfy(entry -> assertThat(entry.amount()).isNotNegative());
        assertThat(books.listDepreciations().stream().map(DepreciationEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1.00");
    }

    @Test
    void 已发生处置的资产待核而其他资产可继续() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        var dispositions = new com.zhubao.erp.finance.infra.InMemoryFixedAssetDispositionRepository();
        var unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        books.saveAsset(asset("STOPPED"));
        books.saveAsset(asset("NORMAL"));
        var costs = new FinanceCostService(books, null, dispositions);
        var service = new FixedAssetDepreciationContinuityService(books, costs, unit, guard);
        unit.execute(new com.zhubao.erp.work.domain.CommandCall("seed-asset-disposition", "财务", "SEED", "ASSET", "STOPPED", "scrap"), session -> {
            dispositions.insert(new com.zhubao.erp.finance.domain.FixedAssetDisposition("DSP1", "STOPPED", "SCRAP", "2026-10-20", "损坏", ""), session);
            return "DSP1";
        });
        var result = service.run("dep-disposition-month", finance, "2026-10", null);
        assertThat(result.entries()).extracting(DepreciationEntry::assetCode).containsExactly("NORMAL");
        assertThat(result.awaitingAssetCodes()).containsExactly("STOPPED");
        assertThatThrownBy(() -> costs.depreciate(new com.zhubao.erp.finance.domain.FinanceCommands.DepreciationRunCommand("2026-11")))
                .hasMessageContaining("本版暂停新增折旧，已记折旧保留");
        assertThat(books.listDepreciations()).hasSize(1);
    }

    @Test
    void 末期尾差可复算且同月不重复() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetDepreciationContinuityService service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        FixedAsset asset = asset("FIN-AST-1");
        books.saveAsset(asset);
        assertThatThrownBy(() -> service.run("h05-stop-01", finance, "2026-10", "2026-11"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("本版不填写折旧生效月，停用后暂停新增折旧");
        assertThat(books.listDepreciations()).isEmpty();

        FixedAssetDepreciationRun first = service.run("h05-m1-0001", finance, "2026-10", "");
        FixedAssetDepreciationRun second = service.run("h05-m2-0001", finance, "2026-11", null);
        FixedAssetDepreciationRun last = service.run("h05-m3-0001", finance, "2026-12", null);
        assertThat(first.algorithm()).isEqualTo(FixedAssetDepreciationRun.MANAGEMENT_ALGORITHM);
        assertThat(first.entries()).singleElement().extracting(DepreciationEntry::amount).isEqualTo(new BigDecimal("333.33"));
        assertThat(second.entries()).singleElement().extracting(DepreciationEntry::amount).isEqualTo(new BigDecimal("333.33"));
        assertThat(last.entries()).singleElement().extracting(DepreciationEntry::amount).isEqualTo(new BigDecimal("333.34"));
        assertThat(books.listDepreciations().stream().map(DepreciationEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1000.00");
        FixedAssetDepreciationRun replay = service.run("h05-m1-0001", finance, "2026-10", "");
        FixedAssetDepreciationRun again = service.run("h05-m1-0002", finance, "2026-10", "");
        assertThat(replay.entries()).extracting(DepreciationEntry::code).containsExactly(first.entries().get(0).code());
        assertThat(again.entries()).extracting(DepreciationEntry::code).containsExactly(first.entries().get(0).code());
        assertThat(books.listDepreciations()).hasSize(3);
        assertThat(books.listAssets()).containsExactly(asset);
        assertThat(service.run("h05-after1", finance, "2027-01", null).entries()).isEmpty();
    }

    @Test
    void 已生成历史不被覆盖() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetDepreciationContinuityService service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(asset("FIN-AST-1"));
        books.saveDepreciation(new DepreciationEntry("FIN-DEP-OLD", "FIN-AST-1", "2026-10", new BigDecimal("1.00")));
        FixedAssetDepreciationRun run = service.run("h05-keep-01", finance, "2026-10", null);
        assertThat(run.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.code()).isEqualTo("FIN-DEP-OLD");
            assertThat(entry.amount()).isEqualByComparingTo("1.00");
        });
        assertThat(books.listDepreciations()).hasSize(1);
        assertThat(books.listAssets()).singleElement().extracting(FixedAsset::originalValue).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void 失败不留下新折旧() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        books.saveAsset(asset("FIN-AST-1"));
        books.saveDepreciation(new DepreciationEntry("FIN-DEP-OLD", "FIN-AST-1", "2026-10", new BigDecimal("333.33")));
        FixedAssetDepreciationContinuityService failing = service(books, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.run("h05-fail-01", finance, "2026-11", null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(books.listDepreciations()).extracting(DepreciationEntry::code).containsExactly("FIN-DEP-OLD");
    }

    @Test
    void 未授权不能计提() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetDepreciationContinuityService service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(asset("FIN-AST-1"));
        OperatorContext keeper = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThatThrownBy(() -> service.run("h05-deny-01", keeper, "2026-10", null))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(books.listDepreciations()).isEmpty();
    }

    @Test
    void jdbc同月回放且保留历史金额() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFinanceRepository books = new JdbcFinanceRepository(dataSource);
        FixedAssetDepreciationContinuityService service = new FixedAssetDepreciationContinuityService(
                books, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        books.saveAsset(asset("FIN-AST-1"));
        books.saveDepreciation(new DepreciationEntry("FIN-DEP-OLD", "FIN-AST-1", "2026-10", new BigDecimal("1.00")));
        FixedAssetDepreciationRun kept = service.run("h05-jdbc-k1", finance, "2026-10", null);
        assertThatThrownBy(() -> service.run("h05-jdbc-n1", finance, "2026-12", null)).isInstanceOf(BusinessException.class).hasMessageContaining("历史折旧");
        assertThat(kept.entries()).singleElement().extracting(DepreciationEntry::amount).isEqualTo(new BigDecimal("1.00"));
        assertThat(books.listAssets()).singleElement().extracting(FixedAsset::originalValue).isEqualTo(new BigDecimal("1000.00"));
        assertThat(books.listDepreciations()).hasSize(1);
    }

    @Test
    void 接口拒绝未核定的生效月() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetDepreciationContinuityService service = service(books, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(asset("FIN-AST-1"));
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(finance);
        FixedAssetDepreciationController controller = new FixedAssetDepreciationController(service, actors);
        assertThatThrownBy(() -> controller.run(new FixedAssetDepreciationController.RunRequest(
                "h05-http-01", "2026-10", "2026-10"), request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("本版不填写折旧生效月，停用后暂停新增折旧");
        FixedAssetDepreciationRun run = controller.run(new FixedAssetDepreciationController.RunRequest(
                "h05-http-02", "2026-10", null), request).data();
        assertThat(run.algorithm()).isEqualTo(FixedAssetDepreciationRun.MANAGEMENT_ALGORITHM);
        assertThat(run.entries()).hasSize(1);
    }

    private FixedAssetDepreciationContinuityService service(InMemoryFinanceRepository books,
                                                            com.zhubao.erp.work.app.BusinessWorkUnit unit) {
        return new FixedAssetDepreciationContinuityService(books, unit, guard);
    }

    private static FixedAsset asset(String code) {
        return new FixedAsset(
                code, "抛光机", "设备", new BigDecimal("1000.00"), new BigDecimal("0.00"),
                "2026-10", 3, "财务", "2026-10-01T08:00:00");
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        var scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (int version : new int[]{36, 44}) {
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
