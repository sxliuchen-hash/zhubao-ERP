package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLineView;
import com.zhubao.erp.goldwork.domain.GoldworkMovementView;
import com.zhubao.erp.goldwork.domain.GoldworkOrderStatus;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkMaterialRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkMovementRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkMaterialRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkMovementRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOrderRepository;
import com.zhubao.erp.inventory.app.CompanyMaterialReceiptService;
import com.zhubao.erp.inventory.app.FactoryItemService;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockDocumentRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldworkTransformationTest {

    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 投入产出不把毛重当净金也不按短重算损耗() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        var bead = new FactoryItemService(items, unit, guard).register(
                "m03-item-01", keeper, FactoryItemCategory.BEAD, "足金粒", "料", "克", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(items, stocks, new InMemoryFinanceRepository(), unit, guard)
                .receive("m03-recv-01", keeper, bead.itemId(), new BigDecimal("10"), null, "克", "WH-01", "2026-10-31",
                        CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m03-order-01", keeper, customer.customerId(), "COMPANY_PREPARED", "戒托", "张三", "", "", "");
        InMemoryGoldworkMaterialRepository materials = new InMemoryGoldworkMaterialRepository();
        GoldworkMaterialLineView source = new GoldworkMaterialService(
                orders, materials, stocks, new InMemoryStockDocumentRepository(), unit, guard).receive(
                "m03-line-001", keeper, order.goldworkCode(), received.receiptId(), null,
                new BigDecimal("3"), new BigDecimal("2.50000"), new BigDecimal("2.80000"), "克", "足金");
        InMemoryGoldworkMovementRepository movements = new InMemoryGoldworkMovementRepository();
        GoldworkMovementService service = new GoldworkMovementService(orders, materials, movements, unit, guard);

        assertThatThrownBy(() -> record(service, "m03-gross-01", order.goldworkCode(), source.lineId(),
                "OUTPUT", null, new BigDecimal("2.8"), "克", "足金", "", "", true, false, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("镶石成品总重不能直接当金料净重");
        assertThatThrownBy(() -> record(service, "m03-loss-001", order.goldworkCode(), source.lineId(),
                "OUTPUT", new BigDecimal("1"), null, "克", "足金", "", "", false, true, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能以短重推定责任或赔偿");
        assertThatThrownBy(() -> record(service, "m03-factor1", order.goldworkCode(), source.lineId(),
                "OUTPUT", new BigDecimal("1"), null, "克", "18K", "", "改色", false, false, new BigDecimal("0.75"), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金料转换关系尚未核定，不能按系数换算重量");
        assertThatThrownBy(() -> record(service, "m03-piece01", order.goldworkCode(), source.lineId(),
                "INPUT", new BigDecimal("3"), null, "克", "足金", "", "", false, false, null, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工不按同颗数守恒");
        assertThatThrownBy(() -> record(service, "m03-bead001", order.goldworkCode(), source.lineId(),
                "INPUT", new BigDecimal("1"), null, "克", "足金", "", "", false, false, null, false, "POLISH_BEAD"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("工序不在本金工路线上");
        assertThat(movements.list(order.goldworkCode())).isEmpty();

        GoldworkMovementView input = record(service, "m03-input01", order.goldworkCode(), source.lineId(),
                "INPUT", new BigDecimal("1.23456"), null, "克", "足金", "", "", false, false, null, false);
        assertThat(input.netWeight()).isEqualByComparingTo("1.23456");
        assertThat(input.netWeight().scale()).isEqualTo(5);
        assertThat(input.postedToLedger()).isFalse();
        assertThat(input.owner()).isEqualTo("COMPANY");
        assertThat(input.precisionState()).isEqualTo("UNDECIDED");
        GoldworkMovementView replay = record(service, "m03-input01", order.goldworkCode(), source.lineId(),
                "INPUT", new BigDecimal("1.23456"), null, "克", "足金", "", "", false, false, null, false);
        assertThat(replay.movementId()).isEqualTo(input.movementId());

        GoldworkMovementView output = record(service, "m03-output1", order.goldworkCode(), source.lineId(),
                "OUTPUT", null, new BigDecimal("3.10000"), "克", "足金", "", "", false, false, null, false);
        assertThat(output.netWeight()).isNull();
        assertThat(output.grossWeight()).isEqualByComparingTo("3.10000");
        assertThatThrownBy(() -> record(service, "m03-purity1", order.goldworkCode(), source.lineId(),
                "OUTPUT", new BigDecimal("1.1"), null, "克", "18K", "", "", false, false, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("成色变化必须写明已经发生的转换，不能按系数换算");
        GoldworkMovementView changed = record(service, "m03-purity2", order.goldworkCode(), source.lineId(),
                "OUTPUT", new BigDecimal("1.10000"), null, "克", "18K", "", "客户确认改18K", false, false, null, false);
        assertThat(changed.netWeight()).isEqualByComparingTo("1.10000");
        assertThat(changed.conversionNote()).isEqualTo("客户确认改18K");
        assertThatThrownBy(() -> record(service, "m03-disp-01", order.goldworkCode(), source.lineId(),
                "DISPOSAL", null, null, "", "足金", "LOSS", "", false, false, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能以短重推定责任或赔偿");
        GoldworkMovementView held = record(service, "m03-disp-02", order.goldworkCode(), source.lineId(),
                "DISPOSAL", null, null, "", "足金", "HOLD", "", false, false, null, false);
        assertThat(held.disposition()).isEqualTo("HOLD");
        assertThat(held.netWeight()).isNull();

        assertThat(materials.find(source.lineId()).orElseThrow().netWeight()).isEqualByComparingTo("2.50000");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(orders.find(order.goldworkCode()).orElseThrow().status()).isEqualTo(GoldworkOrderStatus.OPEN);
        assertThat(movements.list(order.goldworkCode())).hasSize(4);
        OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        assertThatThrownBy(() -> service.list(finance, order.goldworkCode())).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void jdbc投入产出失败回滚且不把毛重写入净重() throws Exception {
        JdbcDataSource dataSource = dataSource();
        var unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository stocks = new JdbcCompanyMaterialReceiptRepository(dataSource);
        var bead = new FactoryItemService(items, unit, guard).register(
                "m03-jdbc-it", keeper, FactoryItemCategory.BEAD, "足金粒", "料", "克", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(
                items, stocks, new com.zhubao.erp.finance.infra.JdbcFinanceRepository(dataSource), unit, guard)
                .receive("m03-jdbc-rc", keeper, bead.itemId(), new BigDecimal("8"), null, "克", "WH-01", "2026-10-31",
                        CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        JdbcGoldworkOrderRepository orders = new JdbcGoldworkOrderRepository(dataSource);
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m03-jdbc-od", keeper, customer.customerId(), "COMPANY_PREPARED", "戒托", "张三", "", "", "");
        JdbcGoldworkMaterialRepository materials = new JdbcGoldworkMaterialRepository(dataSource);
        GoldworkMaterialLineView source = new GoldworkMaterialService(
                orders, materials, stocks, new InMemoryStockDocumentRepository(), unit, guard).receive(
                "m03-jdbc-ln", keeper, order.goldworkCode(), received.receiptId(), null,
                null, new BigDecimal("2.5"), null, "克", "足金");
        GoldworkMovementService service = new GoldworkMovementService(
                orders, materials, new JdbcGoldworkMovementRepository(dataSource), unit, guard);
        GoldworkMovementView saved = record(service, "m03-jdbc-mv", order.goldworkCode(), source.lineId(),
                "OUTPUT", null, new BigDecimal("1.23456"), "克", "足金", "", "", false, false, null, false);
        assertThat(saved.netWeight()).isNull();
        assertThat(saved.grossWeight()).isEqualByComparingTo("1.23456");
        assertThat(saved.grossWeight().scale()).isGreaterThan(3);
        assertThat(saved.postedToLedger()).isFalse();
        assertThat(count(dataSource, "select count(*) from gld_goldwork_movement")).isEqualByComparingTo("1");
        GoldworkMovementService failing = new GoldworkMovementService(
                orders, materials, new JdbcGoldworkMovementRepository(dataSource),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> record(failing, "m03-jdbc-bad", order.goldworkCode(), source.lineId(),
                "INPUT", new BigDecimal("1"), null, "克", "足金", "", "", false, false, null, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from gld_goldwork_movement")).isEqualByComparingTo("1");
        assertThat(materials.find(source.lineId()).orElseThrow().netWeight()).isEqualByComparingTo("2.5");
    }

    private GoldworkMovementView record(GoldworkMovementService service, String requestId, String goldworkCode,
                                        String sourceLineId, String kind, BigDecimal net, BigDecimal gross,
                                        String unit, String purity, String disposition, String note,
                                        boolean useGrossAsNet, boolean inferShortage, BigDecimal factor,
                                        boolean conservePieces) {
        return record(service, requestId, goldworkCode, sourceLineId, kind, net, gross, unit, purity, disposition, note,
                useGrossAsNet, inferShortage, factor, conservePieces, "GOLD_CAST");
    }

    private GoldworkMovementView record(GoldworkMovementService service, String requestId, String goldworkCode,
                                        String sourceLineId, String kind, BigDecimal net, BigDecimal gross,
                                        String unit, String purity, String disposition, String note,
                                        boolean useGrossAsNet, boolean inferShortage, BigDecimal factor,
                                        boolean conservePieces, String stepId) {
        return service.record(requestId, keeper, goldworkCode, stepId, kind, sourceLineId,
                null, net, gross, unit, purity, disposition, note, useGrossAsNet, inferShortage, factor, conservePieces);
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
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V36__m3_finance_operations.sql", "V44__m3_command_request.sql",
                    "V97__m3_factory_item.sql", "V98__m3_company_material_receipt.sql",
                    "V122__m3_goldwork_order.sql", "V123__m3_goldwork_material.sql",
                    "V124__m3_goldwork_movement.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    String trimmed = item.trim();
                    if (trimmed.regionMatches(true, 0, "create", 0, 6) || trimmed.regionMatches(true, 0, "alter", 0, 5)) {
                        statement.execute(item);
                    }
                }
            }
        }
        return dataSource;
    }
}
