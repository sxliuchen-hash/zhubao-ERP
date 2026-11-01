package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetCardView;
import com.zhubao.erp.finance.domain.FixedAssetField;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetPhysicalRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetPhysicalRepository;
import com.zhubao.erp.finance.web.FixedAssetCardController;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedAssetCardAdoptionTest {

    private final OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 分隔符和缺项不能构造相同的请求内容() {
        InMemoryFinanceRepository assets = new InMemoryFinanceRepository();
        assets.saveAsset(legacy("FIN-AST-1", "抛光机", "1000.00"));
        assets.saveAsset(legacy("FIN-AST-2", "车床", "1000.00"));
        FixedAssetCardAdoptionService service = service(assets, new InMemoryFixedAssetPhysicalRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        service.supplement("card-delimiter-01", finance, "FIN-AST-1", 0L, "A|B", "C", "", "", "");
        assertThatThrownBy(() -> service.supplement("card-delimiter-01", finance, "FIN-AST-1", 0L, "A", "B|C", "", "", ""))
                .hasMessage("同一请求的内容不一致");
        service.supplement("card-missing-01", finance, "FIN-AST-2", 0L, "-", "", "", "", "");
        assertThatThrownBy(() -> service.supplement("card-missing-01", finance, "FIN-AST-2", 0L, "", "-", "", "", ""))
                .hasMessage("同一请求的内容不一致");
    }

    @Test
    void 旧卡缺项待补且补登记不重复原值() {
        InMemoryFinanceRepository assets = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        FixedAssetCardAdoptionService service = service(assets, physical, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        FixedAsset polisher = legacy("FIN-AST-1", "抛光机", "1000.00");
        FixedAsset compressor = legacy("FIN-AST-2", "空压机", "2500.00");
        assets.saveAsset(polisher);
        assets.saveAsset(compressor);

        FixedAssetCardView waiting = service.cards(finance).get(0);
        assertThat(waiting.code()).isEqualTo("FIN-AST-1");
        assertThat(waiting.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(waiting.version()).isZero();
        assertPending(waiting.model());
        assertPending(waiting.serialNumber());
        assertPending(waiting.location());
        assertPending(waiting.custodian());
        assertPending(waiting.useStatus());
        assertThat(waiting.location().value()).isEmpty();
        assertThat(physical.find("FIN-AST-1")).isEmpty();

        assertThatThrownBy(() -> service.supplement(
                "h01-blank1", finance, "FIN-AST-1", 0L, "", "", "", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有可登记的实物字段，缺项保持待补");
        assertThatThrownBy(() -> service.supplement(
                "h01-fake-01", finance, "FIN-AST-1", 0L, "", "", "待补", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能把待补写成位置");
        assertThat(physical.find("FIN-AST-1")).isEmpty();

        FixedAssetCardView adopted = service.supplement(
                "h01-loc-01", finance, "FIN-AST-1", 0L, "GP-9", "", "抛光间", "", "在用");
        assertThat(adopted.code()).isEqualTo("FIN-AST-1");
        assertThat(adopted.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(adopted.residualValue()).isEqualByComparingTo("100.00");
        assertThat(adopted.startMonth()).isEqualTo("2026-09");
        assertThat(adopted.usefulMonths()).isEqualTo(12);
        assertThat(adopted.version()).isEqualTo(1);
        assertThat(adopted.model().label()).isEqualTo("GP-9");
        assertThat(adopted.location().label()).isEqualTo("抛光间");
        assertThat(adopted.useStatus().value()).isEqualTo("IN_USE");
        assertThat(adopted.useStatus().label()).isEqualTo("在用");
        assertPending(adopted.serialNumber());
        assertPending(adopted.custodian());
        assertThat(physical.find("FIN-AST-1").orElseThrow().serialNumber()).isNull();
        assertThat(assets.listAssets()).containsExactly(polisher, compressor);
        assertThat(assets.listDepreciations()).isEmpty();
        assertThat(assets.listPayables()).isEmpty();
        FixedAssetCardView other = service.cards(finance).get(1);
        assertThat(other.code()).isEqualTo("FIN-AST-2");
        assertThat(other.originalValue()).isEqualByComparingTo("2500.00");
        assertPending(other.location());
        assertThat(physical.find("FIN-AST-2")).isEmpty();
    }

    @Test
    void 已登记字段不能改写且原请求可回放() {
        InMemoryFinanceRepository assets = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        FixedAssetCardAdoptionService service = service(assets, physical, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        assets.saveAsset(legacy("FIN-AST-1", "抛光机", "1000.00"));
        FixedAssetCardView adopted = service.supplement(
                "h01-loc-01", finance, "FIN-AST-1", 0L, "", "", "抛光间", "", "");

        FixedAssetCardView replay = service.supplement(
                "h01-loc-01", finance, "FIN-AST-1", 0L, "", "", "抛光间", "", "");
        assertThat(replay.version()).isEqualTo(1);
        assertThat(replay.location().value()).isEqualTo("抛光间");
        OperatorContext other = new OperatorContext("另一财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        assertThatThrownBy(() -> service.supplement(
                "h01-loc-01", other, "FIN-AST-1", 0L, "", "", "抛光间", "", ""))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("不能跨账号重放");

        assertThatThrownBy(() -> service.supplement(
                "h01-move-01", finance, "FIN-AST-1", 1L, "", "", "仓库", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已登记的位置不能在接续里改写");
        assertThatThrownBy(() -> service.supplement(
                "h01-scrap1", finance, "FIN-AST-1", 1L, "", "", "", "", "报废"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("使用状态只能登记在用或闲置");
        assertThatThrownBy(() -> service.supplement(
                "h01-stale1", finance, "FIN-AST-1", 0L, "", "SN-9", "", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资产卡片版本已变化");
        assertThat(physical.find("FIN-AST-1").orElseThrow().version()).isEqualTo(1);
        assertThat(physical.find("FIN-AST-1").orElseThrow().locationName()).isEqualTo("抛光间");
        assertThat(physical.find("FIN-AST-1").orElseThrow().serialNumber()).isNull();

        FixedAssetCardView same = service.supplement(
                "h01-same-01", finance, "FIN-AST-1", 1L, "", "", "抛光间", "", "");
        assertThat(same.version()).isEqualTo(adopted.version());
        FixedAssetCardView serial = service.supplement(
                "h01-serial1", finance, "FIN-AST-1", 1L, "", "SN-9", "", "保管员甲", "");
        assertThat(serial.code()).isEqualTo("FIN-AST-1");
        assertThat(serial.version()).isEqualTo(2);
        assertThat(serial.serialNumber().value()).isEqualTo("SN-9");
        assertThat(serial.custodian().value()).isEqualTo("保管员甲");
        assertThat(serial.location().value()).isEqualTo("抛光间");
        assertThat(serial.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(assets.listAssets()).hasSize(1);
        assertThat(assets.listDepreciations()).isEmpty();
        assertThat(assets.listPayables()).isEmpty();
    }

    @Test
    void 失败不留下实物行() {
        InMemoryFinanceRepository assets = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        assets.saveAsset(legacy("FIN-AST-1", "抛光机", "1000.00"));
        FixedAssetCardAdoptionService failing = service(assets, physical, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.supplement(
                "h01-fail-01", finance, "FIN-AST-1", 0L, "", "", "抛光间", "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(physical.find("FIN-AST-1")).isEmpty();
        assertThat(assets.listAssets()).hasSize(1);
        assertThat(assets.listAssets().get(0).originalValue()).isEqualByComparingTo("1000.00");
        assertThat(assets.listDepreciations()).isEmpty();
    }

    @Test
    void 未授权不能接续原卡片() {
        InMemoryFinanceRepository assets = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        FixedAssetCardAdoptionService service = service(assets, physical, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        assets.saveAsset(legacy("FIN-AST-1", "抛光机", "1000.00"));
        assertThat(guard.allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER)).doesNotContain(
                PermissionCode.ASSET_VIEW, PermissionCode.ASSET_CUSTODY);
        OperatorContext keeper = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThatThrownBy(() -> service.cards(keeper)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.supplement(
                "h01-deny-01", keeper, "FIN-AST-1", 0L, "", "", "抛光间", "", ""))
                .isInstanceOf(PermissionDeniedException.class);
        OperatorContext customer = new OperatorContext(
                "客户", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", List.of("8"));
        assertThatThrownBy(() -> service.cards(customer)).isInstanceOf(PermissionDeniedException.class);
        assertThat(physical.find("FIN-AST-1")).isEmpty();
        assertThat(assets.listAssets()).hasSize(1);
        assertThat(assets.listAssets().get(0).originalValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    void jdbc往返不重建原卡片且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFinanceRepository assets = new JdbcFinanceRepository(dataSource);
        JdbcFixedAssetPhysicalRepository physical = new JdbcFixedAssetPhysicalRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        FixedAssetCardAdoptionService service = service(assets, physical, unit);
        assets.saveAsset(legacy("FIN-AST-1", "抛光机", "1000.00"));

        FixedAssetCardView waiting = service.cards(finance).get(0);
        assertPending(waiting.location());
        assertThat(waiting.originalValue()).isEqualByComparingTo("1000.00");
        FixedAssetCardView adopted = service.supplement(
                "h01-jdbc-01", finance, "FIN-AST-1", 0L, "", "SN-1", "抛光间", "", "IDLE");
        FixedAssetCardView again = service.supplement(
                "h01-jdbc-02", finance, "FIN-AST-1", 1L, "", "SN-1", "抛光间", "", "闲置");
        assertThat(again.version()).isEqualTo(adopted.version());
        assertThat(again.code()).isEqualTo("FIN-AST-1");
        assertThat(again.serialNumber().value()).isEqualTo("SN-1");
        assertThat(again.useStatus().value()).isEqualTo("IDLE");
        assertThat(again.useStatus().label()).isEqualTo("闲置");
        assertThat(physical.find("FIN-AST-1").orElseThrow().locationName()).isEqualTo("抛光间");
        assertThat(count(dataSource, "select count(*) from fin_fixed_asset")).isEqualByComparingTo("1");
        assertThat(count(dataSource, "select original_value from fin_fixed_asset")).isEqualByComparingTo("1000.00");
        assertThat(count(dataSource, "select count(*) from fin_depreciation_entry")).isZero();
        assertThat(count(dataSource, "select count(*) from fin_payable")).isZero();

        FixedAssetCardAdoptionService failing = service(assets, physical, new JdbcBusinessWorkUnit(
                dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.supplement(
                "h01-jdbc-f1", finance, "FIN-AST-1", 1L, "GP-2", "", "", "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(physical.find("FIN-AST-1").orElseThrow().modelName()).isNull();
        assertThat(physical.find("FIN-AST-1").orElseThrow().version()).isEqualTo(1);
        assertThat(count(dataSource, "select count(*) from fin_fixed_asset")).isEqualByComparingTo("1");
        assertThat(count(dataSource, "select original_value from fin_fixed_asset")).isEqualByComparingTo("1000.00");
    }

    @Test
    void 接口返回待补且不接收新原值() {
        InMemoryFinanceRepository assets = new InMemoryFinanceRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FixedAssetCardAdoptionService service = service(assets, new InMemoryFixedAssetPhysicalRepository(), unit);
        assets.saveAsset(legacy("FIN-AST-1", "抛光机", "1000.00"));
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(finance);
        FixedAssetCardController controller = new FixedAssetCardController(service, actors);
        FixedAssetCardView listed = controller.list(request).data().get(0);
        assertThat(listed.code()).isEqualTo("FIN-AST-1");
        assertPending(listed.location());
        FixedAssetCardView saved = controller.supplement(new FixedAssetCardController.SupplementRequest(
                "h01-http-01", "FIN-AST-1", 0L, "", "", "抛光间", "", ""), request).data();
        assertThat(saved.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(saved.location().label()).isEqualTo("抛光间");
        assertPending(saved.serialNumber());
        assertThat(assets.listAssets()).hasSize(1);
        assertThat(assets.listDepreciations()).isEmpty();
    }

    private FixedAssetCardAdoptionService service(com.zhubao.erp.finance.domain.FinanceRepository assets,
                                                  com.zhubao.erp.finance.domain.FixedAssetPhysicalRepository physical,
                                                  com.zhubao.erp.work.app.BusinessWorkUnit unit) {
        return new FixedAssetCardAdoptionService(assets, physical, unit, guard);
    }

    private static void assertPending(FixedAssetField field) {
        assertThat(field.presence()).isEqualTo(FixedAssetField.PENDING);
        assertThat(field.value()).isEmpty();
        assertThat(field.label()).isEqualTo("待补");
    }

    private static FixedAsset legacy(String code, String name, String original) {
        return new FixedAsset(
                code, name, "设备", new BigDecimal(original), new BigDecimal("100.00"),
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
        List<com.zhubao.erp.ops.domain.MigrationScript> scripts = MigrationCatalog.loadDirectory(
                Path.of("src/main/resources/db/mysql"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (int version : new int[]{36, 44, 108}) {
                String sql = scripts.stream().filter(script -> script.version() == version).findFirst().orElseThrow().sql();
                for (String item : MigrationCatalog.statements(sql)) {
                    if (item.trim().regionMatches(true, 0, "create", 0, 6)) {
                        statement.execute(item);
                    }
                }
            }
        }
        return dataSource;
    }
}
