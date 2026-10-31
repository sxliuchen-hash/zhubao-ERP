package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetCustodyView;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetCustodyRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetPhysicalRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetCustodyRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetPhysicalRepository;
import com.zhubao.erp.finance.web.FixedAssetCustodyController;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedAssetCustodyTransferTest {

    private final OperatorContext keeper = new OperatorContext("资产管理员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 交接追加前后位置且不改原值() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        InMemoryFixedAssetCustodyRepository custody = new InMemoryFixedAssetCustodyRepository();
        FixedAssetCustodyService service = service(books, physical, custody, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        FixedAsset asset = legacy();
        books.saveAsset(asset);

        FixedAssetCustodyView first = service.transfer("h03-move-01", keeper, "FIN-AST-1", 0L, "抛光间", "保管员甲", "在用");
        assertThat(first.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(first.fromLocationLabel()).isEqualTo("待补");
        assertThat(first.toLocationLabel()).isEqualTo("抛光间");
        assertThat(first.fromCustodianLabel()).isEqualTo("待补");
        assertThat(first.toCustodianLabel()).isEqualTo("保管员甲");
        assertThat(first.fromStatusLabel()).isEqualTo("待补");
        assertThat(first.toStatusLabel()).isEqualTo("在用");
        assertThat(first.version()).isEqualTo(1);
        assertThat(books.listAssets()).containsExactly(asset);
        assertThat(books.listDepreciations()).isEmpty();
        assertThat(books.listPayables()).isEmpty();

        FixedAssetCustodyView replay = service.transfer("h03-move-01", keeper, "FIN-AST-1", 0L, "抛光间", "保管员甲", "在用");
        assertThat(replay.transferId()).isEqualTo(first.transferId());
        assertThat(service.history(keeper, "FIN-AST-1")).hasSize(1);

        FixedAssetCustodyView second = service.transfer("h03-move-02", keeper, "FIN-AST-1", 1L, "仓库", "", "");
        assertThat(second.fromLocationLabel()).isEqualTo("抛光间");
        assertThat(second.toLocationLabel()).isEqualTo("仓库");
        assertThat(second.fromCustodianLabel()).isEqualTo("保管员甲");
        assertThat(second.toCustodianLabel()).isEqualTo("保管员甲");
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.originalValue()).isEqualByComparingTo("1000.00");
        List<FixedAssetCustodyView> history = service.history(keeper, "FIN-AST-1");
        assertThat(history).extracting(FixedAssetCustodyView::toLocationLabel).containsExactly("抛光间", "仓库");
        assertThat(history.get(0).fromLocationLabel()).isEqualTo("待补");
        assertThat(physical.find("FIN-AST-1").orElseThrow().locationName()).isEqualTo("仓库");
        assertThat(physical.find("FIN-AST-1").orElseThrow().serialNumber()).isNull();
        assertThat(books.listDepreciations()).isEmpty();

        assertThatThrownBy(() -> service.transfer("h03-stale1", keeper, "FIN-AST-1", 1L, "办公室", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资产卡片版本已变化");
        assertThatThrownBy(() -> service.transfer("h03-same-01", keeper, "FIN-AST-1", 2L, "仓库", "保管员甲", "在用"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("交接没有变化");
        assertThatThrownBy(() -> service.transfer("h03-scrap1", keeper, "FIN-AST-1", 2L, "", "", "报废"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("使用状态只能登记在用或闲置");
        assertThat(service.history(keeper, "FIN-AST-1")).hasSize(2);
        assertThat(books.listAssets()).containsExactly(asset);
        assertThat(books.listDepreciations()).isEmpty();
    }

    @Test
    void 并发交接不覆盖原值() throws Exception {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        InMemoryFixedAssetCustodyRepository custody = new InMemoryFixedAssetCustodyRepository();
        FixedAssetCustodyService service = service(books, physical, custody, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy());
        List<Object> results = race(() -> service.transfer("h03-race-a1", keeper, "FIN-AST-1", 0L, "抛光间", "", ""),
                () -> service.transfer("h03-race-b1", keeper, "FIN-AST-1", 0L, "仓库", "", ""));
        assertThat(results.stream().filter(FixedAssetCustodyView.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(BusinessException.class::isInstance)).hasSize(1);
        assertThat(service.history(keeper, "FIN-AST-1")).hasSize(1);
        assertThat(physical.find("FIN-AST-1").orElseThrow().version()).isEqualTo(1);
        assertThat(physical.find("FIN-AST-1").orElseThrow().locationName()).isIn("抛光间", "仓库");
        assertThat(books.listAssets()).singleElement().extracting(FixedAsset::originalValue).isEqualTo(new BigDecimal("1000.00"));
        assertThat(books.listDepreciations()).isEmpty();
    }

    @Test
    void 失败不留下交接() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        InMemoryFixedAssetCustodyRepository custody = new InMemoryFixedAssetCustodyRepository();
        books.saveAsset(legacy());
        FixedAssetCustodyService failing = service(books, physical, custody, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.transfer("h03-fail-01", keeper, "FIN-AST-1", 0L, "抛光间", "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(custody.list("FIN-AST-1")).isEmpty();
        assertThat(physical.find("FIN-AST-1")).isEmpty();
        assertThat(books.listAssets()).hasSize(1);
        assertThat(books.listDepreciations()).isEmpty();
    }

    @Test
    void 财务和库管不能办理交接() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetCustodyService service = service(books, new InMemoryFixedAssetPhysicalRepository(),
                new InMemoryFixedAssetCustodyRepository(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy());
        assertThat(guard.allowedPermissionsFor(RoleCode.FINANCE_STATISTICIAN)).doesNotContain(PermissionCode.ASSET_CUSTODY);
        assertThat(guard.allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER)).doesNotContain(PermissionCode.ASSET_CUSTODY);
        assertThat(guard.allowedPermissionsFor(RoleCode.GENERAL_MANAGER)).doesNotContain(PermissionCode.ASSET_CUSTODY);
        OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        assertThatThrownBy(() -> service.transfer("h03-deny-01", finance, "FIN-AST-1", 0L, "抛光间", "", ""))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(books.listAssets()).hasSize(1);
        assertThat(books.listDepreciations()).isEmpty();
    }

    @Test
    void jdbc交接往返且过期版本不覆盖() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFinanceRepository books = new JdbcFinanceRepository(dataSource);
        JdbcFixedAssetPhysicalRepository physical = new JdbcFixedAssetPhysicalRepository(dataSource);
        JdbcFixedAssetCustodyRepository custody = new JdbcFixedAssetCustodyRepository(dataSource);
        FixedAssetCustodyService service = new FixedAssetCustodyService(
                books, physical, custody, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        books.saveAsset(legacy());
        FixedAssetCustodyView moved = service.transfer("h03-jdbc-01", keeper, "FIN-AST-1", 0L, "抛光间", "", "IDLE");
        FixedAssetCustodyView again = service.transfer("h03-jdbc-01", keeper, "FIN-AST-1", 0L, "抛光间", "", "闲置");
        assertThat(again.transferId()).isEqualTo(moved.transferId());
        assertThat(again.toStatusLabel()).isEqualTo("闲置");
        assertThat(again.originalValue()).isEqualByComparingTo("1000.00");
        assertThatThrownBy(() -> service.transfer("h03-jdbc-02", keeper, "FIN-AST-1", 0L, "仓库", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资产卡片版本已变化");
        assertThat(custody.list("FIN-AST-1")).hasSize(1);
        assertThat(physical.find("FIN-AST-1").orElseThrow().locationName()).isEqualTo("抛光间");
        assertThat(count(dataSource, "select original_value from fin_fixed_asset")).isEqualByComparingTo("1000.00");
        assertThat(count(dataSource, "select count(*) from fin_depreciation_entry")).isEqualByComparingTo("0");
        assertThat(count(dataSource, "select count(*) from fin_payable")).isEqualByComparingTo("0");
    }

    @Test
    void 接口保留待补的前值() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetCustodyService service = service(books, new InMemoryFixedAssetPhysicalRepository(),
                new InMemoryFixedAssetCustodyRepository(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy());
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(keeper);
        FixedAssetCustodyController controller = new FixedAssetCustodyController(service, actors);
        FixedAssetCustodyView saved = controller.transfer(new FixedAssetCustodyController.TransferRequest(
                "h03-http-01", "FIN-AST-1", 0L, "抛光间", "", ""
        ), request).data();
        assertThat(saved.fromLocationLabel()).isEqualTo("待补");
        assertThat(saved.toLocationLabel()).isEqualTo("抛光间");
        assertThat(saved.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(controller.history("FIN-AST-1", request).data()).hasSize(1);
        assertThat(books.listDepreciations()).isEmpty();
    }

    private FixedAssetCustodyService service(InMemoryFinanceRepository books, InMemoryFixedAssetPhysicalRepository physical,
                                             InMemoryFixedAssetCustodyRepository custody,
                                             com.zhubao.erp.work.app.BusinessWorkUnit unit) {
        return new FixedAssetCustodyService(books, physical, custody, unit, guard);
    }

    private static List<Object> race(RunnableAttempt first, RunnableAttempt second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> left = pool.submit(() -> call(first, ready, start));
            Future<Object> right = pool.submit(() -> call(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = new ArrayList<>();
            results.add(left.get(5, TimeUnit.SECONDS));
            results.add(right.get(5, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Object call(RunnableAttempt attempt, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return attempt.run();
        } catch (BusinessException ex) {
            return ex;
        } catch (Exception ex) {
            return ex;
        }
    }

    private interface RunnableAttempt {
        FixedAssetCustodyView run();
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
            for (int version : new int[]{36, 44, 108, 110}) {
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
