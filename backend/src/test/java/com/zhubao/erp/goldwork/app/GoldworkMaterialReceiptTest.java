package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLineView;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkMaterialRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkMaterialRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOrderRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import com.zhubao.erp.inventory.app.CompanyMaterialReceiptService;
import com.zhubao.erp.inventory.app.FactoryItemService;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentKinds;
import com.zhubao.erp.inventory.domain.StockDocumentLine;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockDocumentRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldworkMaterialReceiptTest {

    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 申报计量不截断不入账且不同成色不能相加() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        var bead = new FactoryItemService(items, unit, guard).register(
                "m02-item-01", keeper, FactoryItemCategory.BEAD, "足金粒", "料", "克", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(items, stocks, new InMemoryFinanceRepository(), unit, guard)
                .receive("m02-recv-01", keeper, bead.itemId(), new BigDecimal("10"), null, "克", "WH-01", "2026-10-31",
                        CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m02-order-01", keeper, customer.customerId(), "COMPANY_PREPARED", "戒托", "张三", "", "", "");
        InMemoryStockDocumentRepository documents = new InMemoryStockDocumentRepository();
        GoldworkMaterialService service = new GoldworkMaterialService(
                orders, new InMemoryGoldworkMaterialRepository(), stocks, documents, unit, guard);

        assertThatThrownBy(() -> service.receive(
                "m02-zero-001", keeper, order.goldworkCode(), received.receiptId(), null,
                null, BigDecimal.ZERO, null, "克", "足金"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知重量不能写成 0");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("10");

        BigDecimal net = new BigDecimal("1.23456");
        GoldworkMaterialLineView fine = service.receive(
                "m02-line-001", keeper, order.goldworkCode(), received.receiptId(), null,
                new BigDecimal("2"), net, new BigDecimal("1.30000"), "克", "足金");
        assertThat(fine.netWeight()).isEqualByComparingTo("1.23456");
        assertThat(fine.netWeight().scale()).isEqualTo(5);
        assertThat(fine.grossWeight()).isEqualByComparingTo("1.30000");
        assertThat(fine.pieceCount()).isEqualByComparingTo("2");
        assertThat(fine.precisionState()).isEqualTo("UNDECIDED");
        assertThat(fine.postedToLedger()).isFalse();
        assertThat(fine.owner()).isEqualTo("COMPANY");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("10");

        GoldworkMaterialLineView other = service.receive(
                "m02-line-002", keeper, order.goldworkCode(), received.receiptId(), null,
                null, new BigDecimal("0.5"), null, "克", "18K");
        assertThatThrownBy(() -> service.refuseCombine(fine.lineId(), other.lineId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不同成色或单位不能直接相加");
        GoldworkMaterialLineView same = service.receive(
                "m02-line-003", keeper, order.goldworkCode(), received.receiptId(), null,
                null, new BigDecimal("0.2"), null, "克", "足金");
        assertThatThrownBy(() -> service.refuseCombine(fine.lineId(), same.lineId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工计量精度尚未核定，不能按现有三位库存截断后入账");
        assertThatThrownBy(() -> StockMeasurement.rejectUndecidedGoldScale(BusinessKind.GOLDWORK))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工计量精度尚未核定，不能按现有三位库存截断后入账");

        documents.save(new StockDocument(
                "JC-G", StockDocumentKinds.PREP_RECEIPT, StockDocumentKinds.POSTED, OwnerType.CUSTOMER.name(),
                customer.customerId(), "甲客户", "WH-01", "", "", "", "", "", "", "库管", "m02-doc",
                "2026-10-31", "2026-10-31T09:00:00", "", StockDocumentKinds.IN,
                List.of(new StockDocumentLine(1, "AU", "克", new BigDecimal("3"), null, "PC-AU", "", null, true,
                        new BigDecimal("3"), null, "STK-AU"))
        ), idle());
        GoldworkOrderView customerOrder = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m02-order-02", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "吊坠", "李四", "", "", "");
        GoldworkMaterialLineView blank = service.receive(
                "m02-line-004", keeper, customerOrder.goldworkCode(), "JC-G", 1,
                null, null, null, "", "");
        assertThat(blank.owner()).isEqualTo("CUSTOMER");
        assertThat(blank.pieceCount()).isNull();
        assertThat(blank.netWeight()).isNull();
        assertThat(blank.grossWeight()).isNull();
        assertThat(blank.postedToLedger()).isFalse();
    }

    @Test
    void jdbc申报原样保存且不改公司批次数量() throws Exception {
        JdbcDataSource dataSource = dataSource();
        var unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository stocks = new JdbcCompanyMaterialReceiptRepository(dataSource);
        var bead = new FactoryItemService(items, unit, guard).register(
                "m02-jdbc-it", keeper, FactoryItemCategory.BEAD, "足金粒", "料", "克", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(
                items, stocks, new com.zhubao.erp.finance.infra.JdbcFinanceRepository(dataSource), unit, guard)
                .receive("m02-jdbc-rc", keeper, bead.itemId(), new BigDecimal("8"), null, "克", "WH-01", "2026-10-31",
                        CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        JdbcGoldworkOrderRepository orders = new JdbcGoldworkOrderRepository(dataSource);
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m02-jdbc-od", keeper, customer.customerId(), "COMPANY_PREPARED", "戒托", "张三", "", "", "");
        GoldworkMaterialService service = new GoldworkMaterialService(
                orders, new JdbcGoldworkMaterialRepository(dataSource), stocks, new InMemoryStockDocumentRepository(), unit, guard);
        GoldworkMaterialLineView saved = service.receive(
                "m02-jdbc-ln", keeper, order.goldworkCode(), received.receiptId(), null,
                null, new BigDecimal("1.23456"), null, "克", "足金");
        assertThat(saved.netWeight()).isEqualByComparingTo("1.23456");
        assertThat(saved.netWeight().scale()).isGreaterThan(3);
        assertThat(saved.postedToLedger()).isFalse();
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("8");
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
                    "V122__m3_goldwork_order.sql", "V123__m3_goldwork_material.sql"}) {
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
