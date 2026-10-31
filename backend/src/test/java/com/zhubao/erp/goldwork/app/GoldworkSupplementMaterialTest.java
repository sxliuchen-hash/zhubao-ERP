package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLineView;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.domain.GoldworkSupplementView;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkMaterialRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkSupplementRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkMaterialRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkSupplementRepository;
import com.zhubao.erp.inventory.app.CompanyMaterialReceiptService;
import com.zhubao.erp.inventory.app.FactoryItemService;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentKinds;
import com.zhubao.erp.inventory.domain.StockDocumentLine;
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
import com.zhubao.erp.work.domain.WorkSession;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldworkSupplementMaterialTest {

    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 补料另记来源且不改最初来料() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        FactoryItemService itemService = new FactoryItemService(items, unit, guard);
        CompanyMaterialReceiptService receipts = new CompanyMaterialReceiptService(items, stocks, new InMemoryFinanceRepository(), unit, guard);
        var bead = itemService.register("m05-item-01", keeper, FactoryItemCategory.BEAD, "足金粒", "料", "克", null, null);
        CompanyMaterialReceipt first = receipts.receive("m05-recv-01", keeper, bead.itemId(), new BigDecimal("10"), null, "克", "WH-01",
                "2026-10-31", CompanyMaterialSourceKind.OPENING, "", null, null);
        CompanyMaterialReceipt extra = receipts.receive("m05-recv-02", keeper, bead.itemId(), new BigDecimal("4"), null, "克", "WH-02",
                "2026-10-31", CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m05-order-01", keeper, customer.customerId(), "COMPANY_PREPARED", "戒托", "张三", "", "", "");
        InMemoryGoldworkMaterialRepository materials = new InMemoryGoldworkMaterialRepository();
        InMemoryStockDocumentRepository documents = new InMemoryStockDocumentRepository();
        GoldworkMaterialLineView original = new GoldworkMaterialService(orders, materials, stocks, documents, unit, guard).receive(
                "m05-line-001", keeper, order.goldworkCode(), first.receiptId(), null,
                null, new BigDecimal("2.50000"), null, "克", "足金");
        InMemoryGoldworkSupplementRepository supplements = new InMemoryGoldworkSupplementRepository();
        GoldworkSupplementService service = new GoldworkSupplementService(
                orders, materials, supplements, stocks, documents, unit, guard);

        assertThatThrownBy(() -> service.issue("m05-add-001", keeper, order.goldworkCode(), original.lineId(), extra.receiptId(),
                null, "加厚", null, new BigDecimal("0.3"), null, "克", "足金", true, false, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能在旧来源上直接加数");
        assertThatThrownBy(() -> service.issue("m05-own-001", keeper, order.goldworkCode(), original.lineId(), extra.receiptId(),
                null, "加厚", null, new BigDecimal("0.3"), null, "克", "足金", false, true, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能静默改所有权");
        assertThatThrownBy(() -> service.issue("m05-fee-001", keeper, order.goldworkCode(), original.lineId(), extra.receiptId(),
                null, "加厚", null, new BigDecimal("0.3"), null, "克", "足金", false, false, new BigDecimal("20")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("补料是否另收费尚未核定");
        assertThat(supplements.list(order.goldworkCode())).isEmpty();

        GoldworkSupplementView issued = service.issue("m05-issue01", keeper, order.goldworkCode(), original.lineId(), extra.receiptId(),
                null, "加厚", null, new BigDecimal("0.23456"), null, "克", "足金", false, false, null);
        assertThat(issued.direction()).isEqualTo("ISSUE");
        assertThat(issued.owner()).isEqualTo("COMPANY");
        assertThat(issued.sourceId()).isEqualTo(extra.receiptId());
        assertThat(issued.netWeight()).isEqualByComparingTo("0.23456");
        assertThat(issued.postedToLedger()).isFalse();
        assertThat(issued.feePosted()).isFalse();
        var kept = materials.find(original.lineId()).orElseThrow();
        assertThat(kept.netWeight()).isEqualByComparingTo("2.50000");
        assertThat(kept.sourceId()).isEqualTo(first.receiptId());
        assertThat(kept.owner()).isEqualTo(OwnerType.COMPANY);
        assertThat(stocks.balance(bead.itemId(), first.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(stocks.balance(bead.itemId(), extra.batchCode(), "WH-02").orElseThrow().quantity()).isEqualByComparingTo("4");

        assertThatThrownBy(() -> service.giveBack("m05-backbad", keeper, order.goldworkCode(), original.lineId(), original.lineId(),
                "退回", null, new BigDecimal("0.1"), null, "克", "足金", false, false, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退补不能写回最初来料");
        GoldworkSupplementView returned = service.giveBack("m05-back-01", keeper, order.goldworkCode(), original.lineId(),
                issued.supplementId(), "退回", null, new BigDecimal("0.10000"), null, "克", "足金", false, false, null);
        assertThat(returned.direction()).isEqualTo("RETURN");
        assertThat(returned.priorSupplementId()).isEqualTo(issued.supplementId());
        assertThat(returned.sourceId()).isEqualTo(extra.receiptId());
        assertThat(supplements.find(issued.supplementId()).orElseThrow().netWeight()).isEqualByComparingTo("0.23456");
        assertThat(materials.find(original.lineId()).orElseThrow().netWeight()).isEqualByComparingTo("2.50000");

        documents.save(new StockDocument(
                "JC-S", StockDocumentKinds.PREP_RECEIPT, StockDocumentKinds.POSTED, OwnerType.CUSTOMER.name(),
                customer.customerId(), "甲客户", "WH-01", "", "", "", "", "", "", "库管", "m05-doc",
                "2026-10-31", "2026-10-31T09:00:00", "", StockDocumentKinds.IN,
                List.of(new StockDocumentLine(1, "AU", "克", new BigDecimal("1"), null, "PC-AU", "", null, true,
                        new BigDecimal("1"), null, "STK-AU"))
        ), idle());
        GoldworkSupplementView customerIssue = service.issue("m05-cust-01", keeper, order.goldworkCode(), original.lineId(),
                "JC-S", 1, "客户补边", null, new BigDecimal("0.2"), null, "克", "足金", false, false, null);
        assertThat(customerIssue.owner()).isEqualTo("CUSTOMER");
        assertThat(materials.find(original.lineId()).orElseThrow().owner()).isEqualTo(OwnerType.COMPANY);
    }

    @Test
    void jdbc补料失败回滚且不改最初来料() throws Exception {
        JdbcDataSource dataSource = dataSource();
        var unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository stocks = new JdbcCompanyMaterialReceiptRepository(dataSource);
        var bead = new FactoryItemService(items, unit, guard).register(
                "m05-jdbc-it", keeper, FactoryItemCategory.BEAD, "足金粒", "料", "克", null, null);
        CompanyMaterialReceiptService receipts = new CompanyMaterialReceiptService(
                items, stocks, new com.zhubao.erp.finance.infra.JdbcFinanceRepository(dataSource), unit, guard);
        CompanyMaterialReceipt first = receipts.receive("m05-jdbc-r1", keeper, bead.itemId(), new BigDecimal("8"), null, "克", "WH-01",
                "2026-10-31", CompanyMaterialSourceKind.OPENING, "", null, null);
        CompanyMaterialReceipt extra = receipts.receive("m05-jdbc-r2", keeper, bead.itemId(), new BigDecimal("3"), null, "克", "WH-02",
                "2026-10-31", CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        JdbcGoldworkOrderRepository orders = new JdbcGoldworkOrderRepository(dataSource);
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m05-jdbc-od", keeper, customer.customerId(), "COMPANY_PREPARED", "戒托", "张三", "", "", "");
        JdbcGoldworkMaterialRepository materials = new JdbcGoldworkMaterialRepository(dataSource);
        GoldworkMaterialLineView original = new GoldworkMaterialService(
                orders, materials, stocks, new InMemoryStockDocumentRepository(), unit, guard).receive(
                "m05-jdbc-ln", keeper, order.goldworkCode(), first.receiptId(), null,
                null, new BigDecimal("2.5"), null, "克", "足金");
        GoldworkSupplementService service = new GoldworkSupplementService(
                orders, materials, new JdbcGoldworkSupplementRepository(dataSource), stocks,
                new InMemoryStockDocumentRepository(), unit, guard);
        GoldworkSupplementView issued = service.issue("m05-jdbc-is", keeper, order.goldworkCode(), original.lineId(), extra.receiptId(),
                null, "加厚", null, new BigDecimal("0.23456"), null, "克", "足金", false, false, null);
        assertThat(issued.netWeight().scale()).isGreaterThan(3);
        assertThat(issued.postedToLedger()).isFalse();
        assertThat(issued.feePosted()).isFalse();
        assertThat(materials.find(original.lineId()).orElseThrow().netWeight()).isEqualByComparingTo("2.5");
        assertThat(count(dataSource, "select count(*) from gld_goldwork_supplement")).isEqualByComparingTo("1");
        GoldworkSupplementService failing = new GoldworkSupplementService(
                orders, materials, new JdbcGoldworkSupplementRepository(dataSource), stocks, new InMemoryStockDocumentRepository(),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.giveBack("m05-jdbc-rt", keeper, order.goldworkCode(), original.lineId(),
                issued.supplementId(), "退回", null, new BigDecimal("0.1"), null, "克", "足金", false, false, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from gld_goldwork_supplement")).isEqualByComparingTo("1");
        assertThat(stocks.balance(bead.itemId(), extra.batchCode(), "WH-02").orElseThrow().quantity()).isEqualByComparingTo("3");
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
                    "V126__m3_goldwork_supplement.sql"}) {
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

    private static WorkSession idle() {
        return new WorkSession() {
            @Override
            public Optional<Connection> connection() {
                return Optional.empty();
            }

            @Override
            public void onRollback(Runnable undo) {
            }
        };
    }
}
