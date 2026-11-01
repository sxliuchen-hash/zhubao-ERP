package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.app.CompanyMaterialIssueService;
import com.zhubao.erp.inventory.app.CompanyMaterialReceiptService;
import com.zhubao.erp.inventory.app.FactoryItemService;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentKinds;
import com.zhubao.erp.inventory.domain.StockDocumentLine;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockDocumentRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.sampling.domain.SamplingMaterialUseView;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.sampling.infra.InMemorySamplingMaterialRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingMaterialRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingOrderRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
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
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SamplingMaterialSourceTest {

    private final OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 公司备料领退仍是公司实物且重复请求不双领() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        var bead = new FactoryItemService(items, unit, guard).register(
                "s03-item-01", keeper, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(items, stocks, finance, unit, guard).receive(
                "s03-recv-01", keeper, bead.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-31",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null);
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        SamplingOrderView order = companyOrder(orders, unit, "s03-order-01");
        SamplingMaterialService service = service(orders, new InMemorySamplingMaterialRepository(), items, stocks, issues, unit,
                new InMemoryStockDocumentRepository(), new StockLedgerService(new InMemoryStockLedgerRepository()));

        SamplingMaterialUseView picked = service.pick(
                "s03-pick-001", keeper, order.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("40"), null, "颗");
        SamplingMaterialUseView replay = service.pick(
                "s03-pick-001", keeper, order.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("40"), null, "颗");
        assertThat(replay.useId()).isEqualTo(picked.useId());
        assertThat(picked.owner()).isEqualTo("COMPANY");
        assertThat(picked.inputQuantity()).isNull();
        assertThat(picked.weightState()).isEqualTo("UNKNOWN");
        assertThat(picked.weightValue()).isNull();
        assertThat(picked.returnedQuantity()).isEqualByComparingTo("0");
        assertThat(issues.find(picked.stockMoveId()).orElseThrow().targetCode()).isEqualTo(order.samplingCode());
        assertThat(new CompanyMaterialIssueService(items, stocks, issues, unit, guard).owner()).isEqualTo(OwnerType.COMPANY);
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("60");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-DY").orElseThrow().quantity()).isEqualByComparingTo("40");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().weight().value()).isNull();
        assertThat(finance.listPayables()).isEmpty();
        assertThat(service.list(keeper, order.samplingCode())).hasSize(1);

        assertThatThrownBy(() -> service.pick(
                "s03-zero-001", keeper, order.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("1"), BigDecimal.ZERO, "颗"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知重量不能写成 0");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("60");

        SamplingMaterialUseView input = service.recordInput("s03-in-0001", keeper, order.samplingCode(), picked.useId(), new BigDecimal("10"));
        assertThat(input.inputQuantity()).isEqualByComparingTo("10");
        assertThat(input.owner()).isEqualTo("COMPANY");
        SamplingMaterialUseView returned = service.returnMaterial(
                "s03-back-001", keeper, order.samplingCode(), picked.useId(), new BigDecimal("5"), null, "颗");
        assertThat(returned.returnedQuantity()).isEqualByComparingTo("5");
        assertThat(returned.inputQuantity()).isEqualByComparingTo("10");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("65");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-DY").orElseThrow().quantity()).isEqualByComparingTo("35");
        assertThatThrownBy(() -> service.returnMaterial(
                "s03-back-002", keeper, order.samplingCode(), picked.useId(), new BigDecimal("30"), null, "颗"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退回和投入不能超过领用");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-DY").orElseThrow().quantity()).isEqualByComparingTo("35");
        assertThat(finance.listPayables()).isEmpty();
        assertThat(finance.listReceivables()).isEmpty();
    }

    @Test
    void 客户来料保持客户权属且不占用母单() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        SamplingOrderView order = new SamplingOrderService(customers, orders, unit, guard).open(
                "s03-order-02", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "", "");
        InMemoryStockDocumentRepository documents = new InMemoryStockDocumentRepository();
        InMemoryStockLedgerRepository ledgerRows = new InMemoryStockLedgerRepository();
        StockLedgerService ledger = new StockLedgerService(ledgerRows);
        StockDimension source = new StockDimension(OwnerType.CUSTOMER, customer.customerId(), customer.customerId(), "", null,
                "XY", "WH-01", StockStatus.RAW_WAITING, "PC-1");
        documents.save(new StockDocument(
                "JC-1", StockDocumentKinds.PREP_RECEIPT, StockDocumentKinds.POSTED, OwnerType.CUSTOMER.name(),
                customer.customerId(), "甲客户", "WH-01", "", "", "", "", "", "", "库管", "s03-doc-01",
                "2026-10-31", "2026-10-31T09:00:00", "", StockDocumentKinds.IN,
                List.of(new StockDocumentLine(1, "XY", "颗", new BigDecimal("10"), new BigDecimal("3.0"), "PC-1", "", null, true,
                        new BigDecimal("10"), new BigDecimal("3.0"), "STK-OPEN"))
        ), idleSession());
        ledger.transfer(new StockTransferCommand("prep_receipt", "JC-1", null, source, new BigDecimal("10"), new BigDecimal("3.0"), "收货"));
        SamplingMaterialService service = service(orders, new InMemorySamplingMaterialRepository(),
                new InMemoryFactoryItemRepository(), new InMemoryCompanyMaterialReceiptRepository(),
                new InMemoryCompanyMaterialIssueRepository(), unit, documents, ledger);

        assertThatThrownBy(() -> service.pick(
                "s03-bad-0001", keeper, order.samplingCode(), "RC-1", "", null, "WS-DY",
                new BigDecimal("1"), new BigDecimal("0.2"), "颗"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户来料不能领用公司批次");
        assertThatThrownBy(() -> service.pick(
                "s03-zero-002", keeper, order.samplingCode(), "", "JC-1", 1, "",
                new BigDecimal("1"), BigDecimal.ZERO, "颗"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知重量不能写成 0");
        assertThat(quantityOf(ledger, source)).isEqualByComparingTo("10");

        SamplingMaterialUseView picked = service.pick(
                "s03-cus-0001", keeper, order.samplingCode(), "", "JC-1", 1, "",
                new BigDecimal("4"), new BigDecimal("1.2"), "颗");
        assertThat(picked.owner()).isEqualTo("CUSTOMER");
        assertThat(picked.inputQuantity()).isNull();
        assertThat(picked.sourceLineNo()).isEqualTo(1);
        StockBalance holding = ledger.balances().stream()
                .filter(balance -> order.samplingCode().equals(balance.dimension().workOrderCode()))
                .findFirst().orElseThrow();
        assertThat(holding.dimension().ownerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(holding.dimension().motherOrderCode()).isEmpty();
        assertThat(holding.dimension().customerId()).isEqualTo(customer.customerId());
        assertThat(holding.quantity()).isEqualByComparingTo("4");
        assertThat(quantityOf(ledger, source)).isEqualByComparingTo("6");

        SamplingMaterialUseView returned = service.returnMaterial(
                "s03-cus-back", keeper, order.samplingCode(), picked.useId(), new BigDecimal("1"), new BigDecimal("0.3"), "颗");
        assertThat(returned.owner()).isEqualTo("CUSTOMER");
        assertThat(returned.returnedQuantity()).isEqualByComparingTo("1");
        assertThat(quantityOf(ledger, source)).isEqualByComparingTo("7");
        assertThat(holdingQuantity(ledger, order.samplingCode())).isEqualByComparingTo("3");

        OperatorContext warehouse = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThatThrownBy(() -> service.pick(
                "s03-deny-001", warehouse, order.samplingCode(), "", "JC-1", 1, "",
                new BigDecimal("1"), new BigDecimal("0.2"), "颗"))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(quantityOf(ledger, source)).isEqualByComparingTo("7");
    }

    @Test
    void 失败不留下领用() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        var bead = new FactoryItemService(items, unit, guard).register(
                "s03-item-02", keeper, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(items, stocks, new InMemoryFinanceRepository(), unit, guard).receive(
                "s03-recv-02", keeper, bead.itemId(), new BigDecimal("20"), null, "颗", "WH-01", "2026-10-31",
                CompanyMaterialSourceKind.OPENING, "", null, null);
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        SamplingOrderView order = companyOrder(orders, unit, "s03-order-03");
        InMemorySamplingMaterialRepository uses = new InMemorySamplingMaterialRepository();
        SamplingMaterialService failing = service(orders, uses, items, stocks, issues, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }), new InMemoryStockDocumentRepository(), new StockLedgerService(new InMemoryStockLedgerRepository()));
        assertThatThrownBy(() -> failing.pick(
                "s03-fail-001", keeper, order.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("5"), null, "颗"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(uses.list(order.samplingCode())).isEmpty();
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("20");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-DY")).isEmpty();
    }

    @Test
    void jdbc公司领用往返且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository stocks = new JdbcCompanyMaterialReceiptRepository(dataSource);
        JdbcCompanyMaterialIssueRepository issues = new JdbcCompanyMaterialIssueRepository(dataSource);
        var bead = new FactoryItemService(items, unit, guard).register(
                "s03-jdbc-it", keeper, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        CompanyMaterialReceipt received = new CompanyMaterialReceiptService(
                items, stocks, new com.zhubao.erp.finance.infra.JdbcFinanceRepository(dataSource), unit, guard).receive(
                "s03-jdbc-rc", keeper, bead.itemId(), new BigDecimal("30"), null, "颗", "WH-01", "2026-10-31",
                CompanyMaterialSourceKind.OPENING, "", null, null);
        JdbcSamplingOrderRepository orders = new JdbcSamplingOrderRepository(dataSource);
        SamplingOrderView order = companyOrder(orders, unit, "s03-jdbc-od");
        SamplingMaterialService service = new SamplingMaterialService(
                orders, new JdbcSamplingMaterialRepository(dataSource), stocks,
                new CompanyMaterialIssueService(items, stocks, issues, unit, guard),
                new InMemoryStockDocumentRepository(), new StockLedgerService(new InMemoryStockLedgerRepository()),
                unit, guard);
        SamplingMaterialUseView picked = service.pick(
                "s03-jdbc-pk", keeper, order.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("8"), null, "颗");
        SamplingMaterialUseView replay = service.pick(
                "s03-jdbc-pk", keeper, order.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("8"), null, "颗");
        assertThat(replay.useId()).isEqualTo(picked.useId());
        assertThat(replay.owner()).isEqualTo("COMPANY");
        assertThat(replay.inputQuantity()).isNull();
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("22");
        SamplingMaterialService failing = new SamplingMaterialService(
                orders, new JdbcSamplingMaterialRepository(dataSource), stocks,
                new CompanyMaterialIssueService(items, stocks, issues, unit, guard),
                new InMemoryStockDocumentRepository(), new StockLedgerService(new InMemoryStockLedgerRepository()),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.pick(
                "s03-jdbc-x1", keeper, order.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("4"), null, "颗")).isInstanceOf(IllegalStateException.class);
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("22");
        assertThat(service.list(keeper, order.samplingCode())).hasSize(1);
    }

    private SamplingMaterialService service(InMemorySamplingOrderRepository orders, InMemorySamplingMaterialRepository uses,
                                            InMemoryFactoryItemRepository items,
                                            InMemoryCompanyMaterialReceiptRepository stocks,
                                            InMemoryCompanyMaterialIssueRepository issues,
                                            com.zhubao.erp.work.app.BusinessWorkUnit unit,
                                            InMemoryStockDocumentRepository documents, StockLedgerService ledger) {
        return new SamplingMaterialService(orders, uses, stocks,
                new CompanyMaterialIssueService(items, stocks, issues, unit, guard), documents, ledger, unit, guard);
    }

    private SamplingOrderView companyOrder(com.zhubao.erp.sampling.domain.SamplingOrderRepository orders,
                                           com.zhubao.erp.work.app.BusinessWorkUnit unit, String requestId) {
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        return new SamplingOrderService(customers, orders, unit, guard).open(
                requestId, keeper, customer.customerId(), "COMPANY_PREPARED", "样品", "张三", "", "");
    }

    private static BigDecimal quantityOf(StockLedgerService ledger, StockDimension dimension) {
        return ledger.balances().stream()
                .filter(balance -> balance.dimension().equals(dimension))
                .map(StockBalance::quantity)
                .findFirst().orElseThrow();
    }

    private static BigDecimal holdingQuantity(StockLedgerService ledger, String samplingCode) {
        return ledger.balances().stream()
                .filter(balance -> samplingCode.equals(balance.dimension().workOrderCode()))
                .map(StockBalance::quantity)
                .findFirst().orElseThrow();
    }

    private static WorkSession idleSession() {
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

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V36__m3_finance_operations.sql",
                    "V97__m3_factory_item.sql", "V98__m3_company_material_receipt.sql", "V137__m3_company_receipt_dates.sql", "V99__m3_company_material_issue.sql",
                    "V114__m3_sampling_order.sql", "V116__m3_sampling_material.sql"}) {
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
