package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLineView;
import com.zhubao.erp.goldwork.domain.GoldworkMovementView;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryView;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkMaterialRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkMovementRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkRecoveryRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkMaterialRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkMovementRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkRecoveryRepository;
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

class GoldworkRecoverySourceTest {

    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 回收不并进成品也不折抵费用() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        var bead = new FactoryItemService(items, unit, guard).register(
                "m04-item-01", keeper, FactoryItemCategory.BEAD, "足金粒", "料", "克", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(items, stocks, new InMemoryFinanceRepository(), unit, guard)
                .receive("m04-recv-01", keeper, bead.itemId(), new BigDecimal("10"), null, "克", "WH-01", "2026-10-31",
                        CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m04-order-01", keeper, customer.customerId(), "COMPANY_PREPARED", "戒托", "张三", "", "", "");
        InMemoryGoldworkMaterialRepository materials = new InMemoryGoldworkMaterialRepository();
        GoldworkMaterialLineView source = new GoldworkMaterialService(
                orders, materials, stocks, new InMemoryStockDocumentRepository(), unit, guard).receive(
                "m04-line-001", keeper, order.goldworkCode(), received.receiptId(), null,
                null, new BigDecimal("2.50000"), null, "克", "足金");
        InMemoryGoldworkMovementRepository movements = new InMemoryGoldworkMovementRepository();
        GoldworkMovementView output = new GoldworkMovementService(orders, materials, movements, unit, guard).record(
                "m04-output1", keeper, order.goldworkCode(), "GOLD_CAST", "OUTPUT", source.lineId(),
                null, new BigDecimal("1.80000"), null, "克", "足金", "", "", false, false, null, false);
        InMemoryGoldworkRecoveryRepository recoveries = new InMemoryGoldworkRecoveryRepository();
        GoldworkRecoveryService service = new GoldworkRecoveryService(orders, materials, movements, recoveries, unit, guard);

        assertThatThrownBy(() -> record(service, "m04-zero-01", order.goldworkCode(), source.lineId(), output.movementId(),
                "RECOVERABLE", BigDecimal.ZERO, false, false, null, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知重量不能写成 0");
        assertThatThrownBy(() -> record(service, "m04-clear01", order.goldworkCode(), source.lineId(), output.movementId(),
                "IRRECOVERABLE", null, true, false, null, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收料不能当成损耗清零");
        assertThatThrownBy(() -> record(service, "m04-add-001", order.goldworkCode(), source.lineId(), output.movementId(),
                "RECOVERABLE", new BigDecimal("0.2"), false, true, null, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收料不能并进成品");
        assertThatThrownBy(() -> record(service, "m04-fee-001", order.goldworkCode(), source.lineId(), output.movementId(),
                "RECOVERABLE", new BigDecimal("0.2"), false, false, new BigDecimal("10"), false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收料不能折抵客户费用");
        assertThatThrownBy(() -> record(service, "m04-back-01", order.goldworkCode(), source.lineId(), output.movementId(),
                "REMAINDER", new BigDecimal("0.2"), false, false, null, true, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收返还规则尚未核定");
        assertThatThrownBy(() -> record(service, "m04-infer01", order.goldworkCode(), source.lineId(), output.movementId(),
                "RECOVERABLE", null, false, false, null, false, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收称重尚未核定，不能用短重补重量");
        assertThat(recoveries.list(order.goldworkCode())).isEmpty();

        GoldworkRecoveryView saved = record(service, "m04-save-01", order.goldworkCode(), source.lineId(), output.movementId(),
                "RECOVERABLE", new BigDecimal("0.23456"), false, false, null, false, false);
        assertThat(saved.netWeight()).isEqualByComparingTo("0.23456");
        assertThat(saved.netWeight().scale()).isEqualTo(5);
        assertThat(saved.owner()).isEqualTo("COMPANY");
        assertThat(saved.postedToLedger()).isFalse();
        assertThat(saved.feeOffset()).isFalse();
        assertThat(saved.precisionState()).isEqualTo("UNDECIDED");
        GoldworkRecoveryView unknown = record(service, "m04-save-02", order.goldworkCode(), source.lineId(), output.movementId(),
                "IRRECOVERABLE", null, false, false, null, false, false);
        assertThat(unknown.netWeight()).isNull();
        assertThat(movements.find(output.movementId()).orElseThrow().netWeight()).isEqualByComparingTo("1.80000");
        assertThat(materials.find(source.lineId()).orElseThrow().netWeight()).isEqualByComparingTo("2.50000");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> service.refuseAddToFinished(saved.recoveryId(), output.movementId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工计量精度尚未核定，不能按现有三位库存截断后入账");
    }

    @Test
    void jdbc回收失败回滚且不改成品净重() throws Exception {
        JdbcDataSource dataSource = dataSource();
        var unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository stocks = new JdbcCompanyMaterialReceiptRepository(dataSource);
        var bead = new FactoryItemService(items, unit, guard).register(
                "m04-jdbc-it", keeper, FactoryItemCategory.BEAD, "足金粒", "料", "克", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(
                items, stocks, new com.zhubao.erp.finance.infra.JdbcFinanceRepository(dataSource), unit, guard)
                .receive("m04-jdbc-rc", keeper, bead.itemId(), new BigDecimal("8"), null, "克", "WH-01", "2026-10-31",
                        CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        JdbcGoldworkOrderRepository orders = new JdbcGoldworkOrderRepository(dataSource);
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m04-jdbc-od", keeper, customer.customerId(), "COMPANY_PREPARED", "戒托", "张三", "", "", "");
        JdbcGoldworkMaterialRepository materials = new JdbcGoldworkMaterialRepository(dataSource);
        GoldworkMaterialLineView source = new GoldworkMaterialService(
                orders, materials, stocks, new InMemoryStockDocumentRepository(), unit, guard).receive(
                "m04-jdbc-ln", keeper, order.goldworkCode(), received.receiptId(), null,
                null, new BigDecimal("2.5"), null, "克", "足金");
        JdbcGoldworkMovementRepository movements = new JdbcGoldworkMovementRepository(dataSource);
        GoldworkMovementView output = new GoldworkMovementService(orders, materials, movements, unit, guard).record(
                "m04-jdbc-mv", keeper, order.goldworkCode(), "GOLD_CAST", "OUTPUT", source.lineId(),
                null, new BigDecimal("1.8"), null, "克", "足金", "", "", false, false, null, false);
        GoldworkRecoveryService service = new GoldworkRecoveryService(
                orders, materials, movements, new JdbcGoldworkRecoveryRepository(dataSource), unit, guard);
        GoldworkRecoveryView saved = record(service, "m04-jdbc-sv", order.goldworkCode(), source.lineId(), output.movementId(),
                "REMAINDER", new BigDecimal("0.23456"), false, false, null, false, false);
        assertThat(saved.netWeight()).isEqualByComparingTo("0.23456");
        assertThat(saved.netWeight().scale()).isGreaterThan(3);
        assertThat(saved.postedToLedger()).isFalse();
        assertThat(saved.feeOffset()).isFalse();
        assertThat(movements.find(output.movementId()).orElseThrow().netWeight()).isEqualByComparingTo("1.8");
        assertThat(count(dataSource, "select count(*) from gld_goldwork_recovery")).isEqualByComparingTo("1");
        GoldworkRecoveryService failing = new GoldworkRecoveryService(
                orders, materials, movements, new JdbcGoldworkRecoveryRepository(dataSource),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> record(failing, "m04-jdbc-bad", order.goldworkCode(), source.lineId(), output.movementId(),
                "RECOVERABLE", new BigDecimal("0.1"), false, false, null, false, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from gld_goldwork_recovery")).isEqualByComparingTo("1");
    }

    private GoldworkRecoveryView record(GoldworkRecoveryService service, String requestId, String goldworkCode,
                                        String sourceLineId, String outputId, String kind, BigDecimal net,
                                        boolean clearAsLoss, boolean addToFinished, BigDecimal feeOffset,
                                        boolean autoReturn, boolean inferWeight) {
        return service.record(requestId, keeper, goldworkCode, sourceLineId, outputId, kind,
                null, net, null, net == null ? "" : "克", "足金", clearAsLoss, addToFinished, feeOffset, autoReturn, inferWeight);
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
                    "V124__m3_goldwork_movement.sql", "V125__m3_goldwork_recovery.sql"}) {
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
