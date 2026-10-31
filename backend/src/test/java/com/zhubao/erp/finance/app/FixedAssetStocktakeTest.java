package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetPhysical;
import com.zhubao.erp.finance.domain.FixedAssetStocktake;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetCustodyRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetDispositionRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetPhysicalRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetStocktakeRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetCustodyRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetDispositionRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetPhysicalRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetStocktakeRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
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
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedAssetStocktakeTest {

    private final OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
    private final OperatorContext admin = new OperatorContext("资产管理员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 确认差异不改原值且未见不报废() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        InMemoryFixedAssetCustodyRepository custody = new InMemoryFixedAssetCustodyRepository();
        InMemoryFixedAssetDispositionRepository dispositions = new InMemoryFixedAssetDispositionRepository();
        FixedAssetStocktakeService service = service(books, physical, custody, dispositions,
                new InMemoryFixedAssetStocktakeRepository(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy("FIN-AST-1"));
        books.saveAsset(legacy("FIN-AST-2"));
        physical.insert(new FixedAssetPhysical("FIN-AST-1", null, null, "抛光间", "甲", "IN_USE", 1), null);

        FixedAssetStocktake opened = service.open("h07-open-01", finance, "FIN-AST-1", "2026-10-20T09:00:00");
        assertThat(opened.bookLocation()).isEqualTo("抛光间");
        assertThat(opened.bookVersion()).isEqualTo(1);
        assertThat(opened.seen()).isNull();
        assertThatThrownBy(() -> service.confirm("h07-none-01", admin, opened.stocktakeId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登记实盘");
        assertThat(dispositions.list("FIN-AST-1")).isEmpty();
        assertThat(books.listAssets()).hasSize(2);

        FixedAssetStocktake counted = service.count(
                "h07-count1", finance, opened.stocktakeId(), 1, "在场", "仓库", "", "位置不符");
        assertThat(guard.allowedPermissionsFor(RoleCode.FINANCE_STATISTICIAN))
                .doesNotContain(PermissionCode.FACTORY_STOCKTAKE_CONFIRM);
        assertThatThrownBy(() -> service.confirm("h07-deny-01", finance, opened.stocktakeId(), counted.version()))
                .isInstanceOf(PermissionDeniedException.class);

        FixedAssetStocktake confirmed = service.confirm("h07-ok-0001", admin, opened.stocktakeId(), counted.version());
        assertThat(confirmed.status()).isEqualTo(FixedAssetStocktake.CONFIRMED);
        assertThat(confirmed.bookLocation()).isEqualTo("抛光间");
        assertThat(physical.find("FIN-AST-1").orElseThrow().locationName()).isEqualTo("仓库");
        assertThat(custody.list("FIN-AST-1")).hasSize(1);
        assertThat(custody.list("FIN-AST-1").get(0).fromLocation()).isEqualTo("抛光间");
        assertThat(custody.list("FIN-AST-1").get(0).toLocation()).isEqualTo("仓库");
        assertThat(books.listAssets()).extracting(FixedAsset::originalValue)
                .allMatch(value -> value.compareTo(new BigDecimal("1000.00")) == 0);
        assertThat(books.listDepreciations()).isEmpty();
        assertThat(dispositions.list("FIN-AST-1")).isEmpty();
        assertThat(dispositions.list("FIN-AST-2")).isEmpty();
        assertThat(physical.find("FIN-AST-2")).isEmpty();
        assertThatThrownBy(() -> service.confirm("h07-ok-0002", admin, opened.stocktakeId(), confirmed.version()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("盘点已经确认");
        assertThat(custody.list("FIN-AST-1")).hasSize(1);
        assertThat(books.listAssets()).hasSize(2);

        FixedAssetStocktake missed = service.open("h07-open-02", finance, "FIN-AST-2", "2026-10-20T09:00:00");
        service.count("h07-count2", finance, missed.stocktakeId(), 1, "未见", "", "", "未看到");
        FixedAssetStocktake absent = service.confirm("h07-ok-0003", admin, missed.stocktakeId(), 2);
        assertThat(absent.seen()).isEqualTo(FixedAssetStocktake.ABSENT);
        assertThat(dispositions.list("FIN-AST-2")).isEmpty();
        assertThat(books.listAssets()).extracting(FixedAsset::code).containsExactly("FIN-AST-1", "FIN-AST-2");
        assertThat(books.listAssets().get(1).originalValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    void 盘点后变动必须重盘且失败不改原值() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        InMemoryFixedAssetCustodyRepository custody = new InMemoryFixedAssetCustodyRepository();
        InMemoryFixedAssetDispositionRepository dispositions = new InMemoryFixedAssetDispositionRepository();
        InMemoryFixedAssetStocktakeRepository stocktakes = new InMemoryFixedAssetStocktakeRepository();
        books.saveAsset(legacy("FIN-AST-1"));
        physical.insert(new FixedAssetPhysical("FIN-AST-1", null, null, "抛光间", null, null, 1), null);
        FixedAssetStocktakeService service = service(books, physical, custody, dispositions, stocktakes,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        FixedAssetStocktake opened = service.open("h07-open-03", finance, "FIN-AST-1", "2026-10-20T09:00:00");
        service.count("h07-count3", finance, opened.stocktakeId(), 1, "PRESENT", "抛光间", "", "");
        physical.replace(new FixedAssetPhysical("FIN-AST-1", null, null, "临时", null, null, 2), 1, null);
        assertThatThrownBy(() -> service.confirm("h07-late-01", admin, opened.stocktakeId(), 2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("盘点后位置或保管已变化，请重新盘点");
        assertThat(books.listAssets()).singleElement().extracting(FixedAsset::originalValue)
                .isEqualTo(new BigDecimal("1000.00"));
        assertThat(dispositions.list("FIN-AST-1")).isEmpty();

        FixedAssetStocktakeService failing = service(books, physical, custody, dispositions, stocktakes,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }));
        assertThatThrownBy(() -> failing.open("h07-fail-01", finance, "FIN-AST-1", "2026-10-21T09:00:00"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stocktakes.find("FIN-PD-MISSING")).isEmpty();
        assertThat(books.listAssets()).hasSize(1);
    }

    @Test
    void jdbc确认后原值不变() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFinanceRepository books = new JdbcFinanceRepository(dataSource);
        JdbcFixedAssetPhysicalRepository physical = new JdbcFixedAssetPhysicalRepository(dataSource);
        FixedAssetStocktakeService service = new FixedAssetStocktakeService(
                books, physical, new JdbcFixedAssetCustodyRepository(dataSource),
                new JdbcFixedAssetDispositionRepository(dataSource), new JdbcFixedAssetStocktakeRepository(dataSource),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        books.saveAsset(legacy("FIN-AST-1"));
        FixedAssetStocktake opened = service.open("h07-jdbc-o1", finance, "FIN-AST-1", "2026-10-20T09:00:00");
        FixedAssetStocktake counted = service.count("h07-jdbc-c1", finance, opened.stocktakeId(), 1, "PRESENT", "", "", "");
        FixedAssetStocktake confirmed = service.confirm("h07-jdbc-k1", admin, opened.stocktakeId(), counted.version());
        assertThat(confirmed.status()).isEqualTo(FixedAssetStocktake.CONFIRMED);
        assertThat(books.listAssets()).singleElement().extracting(FixedAsset::originalValue)
                .isEqualTo(new BigDecimal("1000.00"));
        assertThat(books.listDepreciations()).isEmpty();
        assertThat(books.listPayables()).isEmpty();
    }

    private FixedAssetStocktakeService service(InMemoryFinanceRepository books, InMemoryFixedAssetPhysicalRepository physical,
                                               InMemoryFixedAssetCustodyRepository custody,
                                               InMemoryFixedAssetDispositionRepository dispositions,
                                               InMemoryFixedAssetStocktakeRepository stocktakes,
                                               com.zhubao.erp.work.app.BusinessWorkUnit unit) {
        return new FixedAssetStocktakeService(books, physical, custody, dispositions, stocktakes, unit, guard);
    }

    private static FixedAsset legacy(String code) {
        return new FixedAsset(
                code, "抛光机", "设备", new BigDecimal("1000.00"), new BigDecimal("100.00"),
                "2026-09", 12, "财务", "2026-09-01T08:00:00");
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        var scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (int version : new int[]{36, 44, 108, 110, 112, 113}) {
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
