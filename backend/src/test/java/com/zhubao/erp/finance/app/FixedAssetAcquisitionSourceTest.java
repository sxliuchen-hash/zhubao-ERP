package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetAcquisitionView;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetAcquisitionRepository;
import com.zhubao.erp.finance.infra.InMemoryFixedAssetPhysicalRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetAcquisitionRepository;
import com.zhubao.erp.finance.infra.JdbcFixedAssetPhysicalRepository;
import com.zhubao.erp.finance.web.FixedAssetAcquisitionController;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedAssetAcquisitionSourceTest {
    @Test
    void 维修或不明性质的应付不能作为购置来源() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetAcquisitionService service = service(books, new InMemoryFixedAssetPhysicalRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        for (String kind : new String[]{"ASSET_REPAIR", "REPAIR", "其他"}) {
            String code = "AP-" + kind;
            books.savePayable(new Payable(code, kind, "2026-10", "维修商", "REPAIR|old", null, "",
                    new BigDecimal("100.00"), BigDecimal.ZERO, "财务", "2026-10-01T08:00:00"));
            assertThatThrownBy(() -> service.receive("asset-cover-" + kind, finance, "COVERED_PAYABLE", "",
                    "新机器", "设备", null, BigDecimal.ZERO, "2026-10", 12, "", "2026-10-01", "", "", code, null, null, null))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(books.listAssets()).isEmpty();
        assertThat(books.listPayables()).hasSize(3);
    }

    private final OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 新购置只记一次原值且付款不记折旧() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        FixedAssetAcquisitionService service = service(books, physical, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        assertThatThrownBy(() -> purchase(service, "h02-zero-01", new BigDecimal("0"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知金额不能写成 0");

        FixedAssetAcquisitionView incomplete = purchase(service, "h02-open-01", null, null, null, null);
        assertThat(incomplete.amountState()).isEqualTo("INCOMPLETE");
        assertThat(incomplete.originalValue()).isNull();
        assertThat(incomplete.paidAmount()).isNull();
        assertThat(incomplete.paid()).isFalse();
        assertThat(incomplete.assetCode()).isEmpty();
        assertThat(books.listAssets()).isEmpty();
        assertThat(books.listPayables()).isEmpty();
        assertThat(books.listPayablePayments()).isEmpty();

        assertThatThrownBy(() -> purchase(service, "h02-nopay1", new BigDecimal("1000.00"), new BigDecimal("400.00"), "2026-10-02", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有付款凭据，不能标成已付");
        assertThat(books.listAssets()).isEmpty();
        assertThat(books.listPayables()).isEmpty();

        FixedAssetAcquisitionView bought = purchase(service, "h02-buy-001", new BigDecimal("1000.00"), null, null, null);
        assertThat(bought.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(bought.paid()).isFalse();
        assertThat(bought.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(bought.paymentCode()).isEmpty();
        assertThat(bought.payableCode()).isNotBlank();
        assertThat(bought.handoverLocationLabel()).isEqualTo("抛光间");
        assertThat(books.listAssets()).hasSize(1);
        assertThat(books.listPayables()).hasSize(1);
        assertThat(books.listPayables().get(0).amount()).isEqualByComparingTo("1000.00");
        assertThat(books.listPayables().get(0).paidAmount()).isEqualByComparingTo("0.00");
        assertThat(books.listDepreciations()).isEmpty();
        assertThat(physical.find(bought.assetCode()).orElseThrow().locationName()).isEqualTo("抛光间");

        FixedAssetAcquisitionView paid = purchase(service, "h02-pay-001", new BigDecimal("800.00"), new BigDecimal("300.00"), "2026-10-03", "PV-1");
        assertThat(paid.originalValue()).isEqualByComparingTo("800.00");
        assertThat(paid.paidAmount()).isEqualByComparingTo("300.00");
        assertThat(paid.paid()).isTrue();
        assertThat(paid.paymentCode()).isNotBlank();
        assertThat(books.listAssets()).hasSize(2);
        assertThat(books.listPayables()).hasSize(2);
        assertThat(books.listPayablePayments()).hasSize(1);
        assertThat(books.listDepreciations()).isEmpty();
        assertThat(originalSum(books)).isEqualByComparingTo("1800.00");

        FixedAssetAcquisitionView replay = purchase(service, "h02-pay-001", new BigDecimal("800.00"), new BigDecimal("300.00"), "2026-10-03", "PV-1");
        assertThat(replay.acquisitionId()).isEqualTo(paid.acquisitionId());
        assertThat(books.listAssets()).hasSize(2);
        assertThat(books.listPayablePayments()).hasSize(1);
        assertThat(originalSum(books)).isEqualByComparingTo("1800.00");
    }

    @Test
    void 已有卡片和已有应付都不另开一笔() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        InMemoryFixedAssetPhysicalRepository physical = new InMemoryFixedAssetPhysicalRepository();
        FixedAssetAcquisitionService service = service(books, physical, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        FixedAsset legacy = legacy("FIN-AST-1", "抛光机", "1000.00");
        books.saveAsset(legacy);
        books.savePayable(new Payable(
                "FIN-AP-OLD", "设备", "2026-09", "甲供应商", "EQ-1", null, "",
                new BigDecimal("1000.00"), new BigDecimal("0.00"), "财务", "2026-09-01T08:00:00"));
        books.savePayable(new Payable(
                "FIN-AP-WAGE", "工资", "2026-09", "员工", "WG-1", null, "",
                new BigDecimal("1000.00"), new BigDecimal("0.00"), "财务", "2026-09-01T08:00:00"));

        assertThatThrownBy(() -> purchaseOnto(service, "h02-old-buy", "FIN-AST-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已有资产不自动补一笔采购");
        assertThat(books.listAssets()).containsExactly(legacy);
        assertThat(books.listPayables()).hasSize(2);

        FixedAssetAcquisitionView adopted = service.receive(
                "h02-old-001", finance, "EXISTING_CARD", "FIN-AST-1",
                null, null, null, null, null, null, "", "", "抛光间", "", "", null, null, null);
        assertThat(adopted.assetCode()).isEqualTo("FIN-AST-1");
        assertThat(adopted.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(adopted.supplierLabel()).isEqualTo("待补");
        assertThat(adopted.acquiredOnLabel()).isEqualTo("待补");
        assertThat(adopted.payableCode()).isEmpty();
        assertThat(adopted.paidAmount()).isNull();
        assertThat(adopted.paid()).isFalse();
        assertThat(books.listPayables()).hasSize(2);
        assertThat(books.listPayablePayments()).isEmpty();
        assertThat(books.listDepreciations()).isEmpty();
        assertThat(physical.find("FIN-AST-1").orElseThrow().locationName()).isEqualTo("抛光间");
        assertThatThrownBy(() -> service.receive(
                "h02-old-002", finance, "EXISTING_CARD", "FIN-AST-1",
                null, null, new BigDecimal("1000.00"), null, null, null, "", "", "", "", "", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该资产已经记录购置来源，不能再补一笔采购");
        assertThatThrownBy(() -> service.receive(
                "h02-wage-01", finance, "COVERED_PAYABLE", "",
                "另一台", "设备", null, new BigDecimal("0.00"), "2026-10", 12, "", "2026-10-01", "", "", "FIN-AP-WAGE",
                null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("工资、外协或物料应付不能当作资产原值");

        FixedAssetAcquisitionView covered = service.receive(
                "h02-cover01", finance, "COVERED_PAYABLE", "",
                "空压机", "设备", null, new BigDecimal("100.00"), "2026-10", 12, "", "2026-10-01", "", "", "FIN-AP-OLD",
                null, null, null);
        assertThat(covered.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(covered.coveredPayableCode()).isEqualTo("FIN-AP-OLD");
        assertThat(covered.payableCode()).isEmpty();
        assertThat(covered.paid()).isFalse();
        assertThat(covered.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(covered.supplierLabel()).isEqualTo("甲供应商");
        assertThat(books.listPayables()).hasSize(2);
        assertThat(books.listAssets()).hasSize(2);
        assertThat(originalSum(books)).isEqualByComparingTo("2000.00");
        assertThat(books.listDepreciations()).isEmpty();
        assertThatThrownBy(() -> service.receive(
                "h02-cover02", finance, "COVERED_PAYABLE", "",
                "第三台", "设备", null, new BigDecimal("0.00"), "2026-10", 12, "", "2026-10-02", "", "", "FIN-AP-OLD",
                null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这笔应付已经对应过资产");
        assertThat(books.listAssets()).hasSize(2);
        assertThat(books.listPayables()).hasSize(2);
    }

    @Test
    void 失败不留下卡片或应付() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetAcquisitionService failing = service(books, new InMemoryFixedAssetPhysicalRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }));
        assertThatThrownBy(() -> purchase(failing, "h02-fail-01", new BigDecimal("1000.00"), new BigDecimal("100.00"), "2026-10-02", "PV-9"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(books.listAssets()).isEmpty();
        assertThat(books.listPayables()).isEmpty();
        assertThat(books.listPayablePayments()).isEmpty();
        assertThat(books.listDepreciations()).isEmpty();
    }

    @Test
    void 未授权不能登记购置() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetAcquisitionService service = service(books, new InMemoryFixedAssetPhysicalRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        books.saveAsset(legacy("FIN-AST-1", "抛光机", "1000.00"));
        OperatorContext keeper = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThatThrownBy(() -> service.list(keeper, "")).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.receive(
                "h02-deny-01", keeper, "EXISTING_CARD", "FIN-AST-1",
                null, null, null, null, null, null, "", "", "", "", "", null, null, null))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(books.listAssets()).hasSize(1);
        assertThat(books.listPayables()).isEmpty();
    }

    @Test
    void jdbc购置付款与已有应付去重且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFinanceRepository books = new JdbcFinanceRepository(dataSource);
        JdbcFixedAssetAcquisitionRepository acquisitions = new JdbcFixedAssetAcquisitionRepository(dataSource);
        JdbcFixedAssetPhysicalRepository physical = new JdbcFixedAssetPhysicalRepository(dataSource);
        FixedAssetAcquisitionService service = new FixedAssetAcquisitionService(
                books, acquisitions, physical, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        books.saveAsset(legacy("FIN-AST-1", "抛光机", "2500.00"));
        books.savePayable(new Payable(
                "FIN-AP-OLD", "设备", "2026-09", "甲供应商", "EQ-1", null, "",
                new BigDecimal("2500.00"), new BigDecimal("0.00"), "财务", "2026-09-01T08:00:00"));

        FixedAssetAcquisitionView adopted = service.receive(
                "h02-jdbc-old", finance, "EXISTING_CARD", "FIN-AST-1",
                null, null, null, null, null, null, "", "", "", "", "", null, null, null);
        FixedAssetAcquisitionView bought = purchase(service, "h02-jdbc-buy", new BigDecimal("1000.00"), new BigDecimal("400.00"), "2026-10-04", "PV-2");
        assertThat(adopted.paidAmount()).isNull();
        assertThat(bought.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(bought.paidAmount()).isEqualByComparingTo("400.00");
        assertThat(count(dataSource, "select count(*) from fin_fixed_asset")).isEqualByComparingTo("2");
        assertThat(count(dataSource, "select sum(original_value) from fin_fixed_asset")).isEqualByComparingTo("3500.00");
        assertThat(count(dataSource, "select count(*) from fin_payable")).isEqualByComparingTo("2");
        assertThat(count(dataSource, "select count(*) from fin_payable_payment")).isEqualByComparingTo("1");
        assertThat(count(dataSource, "select count(*) from fin_depreciation_entry")).isEqualByComparingTo("0");

        assertThatThrownBy(() -> service.receive(
                "h02-jdbc-cov", finance, "COVERED_PAYABLE", "",
                "第三台", "设备", null, new BigDecimal("0.00"), "2026-10", 12, "", "2026-10-05", "", "", bought.payableCode(),
                null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这笔应付已经对应过资产");
        assertThat(count(dataSource, "select count(*) from fin_fixed_asset")).isEqualByComparingTo("2");

        FixedAssetAcquisitionService failing = new FixedAssetAcquisitionService(
                books, acquisitions, physical, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
            throw new IllegalStateException("失败");
        }), guard);
        assertThatThrownBy(() -> purchase(failing, "h02-jdbc-f1", new BigDecimal("500.00"), null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from fin_fixed_asset")).isEqualByComparingTo("2");
        assertThat(count(dataSource, "select sum(original_value) from fin_fixed_asset")).isEqualByComparingTo("3500.00");
        assertThat(count(dataSource, "select count(*) from fin_payable")).isEqualByComparingTo("2");
    }

    @Test
    void 接口不把未付款标成已付() {
        InMemoryFinanceRepository books = new InMemoryFinanceRepository();
        FixedAssetAcquisitionService service = service(books, new InMemoryFixedAssetPhysicalRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(finance);
        FixedAssetAcquisitionController controller = new FixedAssetAcquisitionController(service, actors);
        assertThat(controller.list(null, request).data()).isEmpty();
        FixedAssetAcquisitionView saved = controller.receive(new FixedAssetAcquisitionController.ReceiveRequest(
                "h02-http-01", "PURCHASE", "", "抛光机", "设备", new BigDecimal("1000.00"), new BigDecimal("0.00"),
                "2026-10", 12, "甲供应商", "2026-10-01", "", "", "", null, null, null
        ), request).data();
        assertThat(saved.paid()).isFalse();
        assertThat(saved.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(saved.originalValue()).isEqualByComparingTo("1000.00");
        assertThat(books.listPayablePayments()).isEmpty();
        assertThat(books.listDepreciations()).isEmpty();
    }

    private FixedAssetAcquisitionView purchase(FixedAssetAcquisitionService service, String requestId, BigDecimal original,
                                               BigDecimal payment, String paidOn, String voucher) {
        return service.receive(
                requestId, finance, "PURCHASE", "", "抛光机", "设备", original, new BigDecimal("0.00"),
                "2026-10", 12, "甲供应商", "2026-10-01", "抛光间", "保管员甲", "", payment, paidOn, voucher);
    }

    private void purchaseOnto(FixedAssetAcquisitionService service, String requestId, String assetCode) {
        service.receive(
                requestId, finance, "PURCHASE", assetCode, "抛光机", "设备", new BigDecimal("1000.00"), new BigDecimal("0.00"),
                "2026-10", 12, "甲供应商", "2026-10-01", "", "", "", null, null, null);
    }

    private FixedAssetAcquisitionService service(InMemoryFinanceRepository books, InMemoryFixedAssetPhysicalRepository physical,
                                                 com.zhubao.erp.work.app.BusinessWorkUnit unit) {
        return new FixedAssetAcquisitionService(
                books, new InMemoryFixedAssetAcquisitionRepository(), physical, unit, guard);
    }

    private static BigDecimal originalSum(InMemoryFinanceRepository books) {
        return books.listAssets().stream().map(FixedAsset::originalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
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
        var scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (int version : new int[]{36, 44, 108, 109}) {
                String sql = scripts.stream().filter(script -> script.version() == version).findFirst().orElseThrow().sql();
                for (String item : MigrationCatalog.statements(sql)) {
                    String trimmed = item.trim();
                    if (trimmed.regionMatches(true, 0, "create", 0, 6)) {
                        statement.execute(trimmed);
                    }
                }
            }
        }
        return dataSource;
    }
}
