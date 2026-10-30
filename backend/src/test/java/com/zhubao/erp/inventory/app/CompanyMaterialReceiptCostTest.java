package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.inventory.web.CompanyMaterialReceiptController;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyMaterialReceiptCostTest {

    private final OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
    private final OperatorContext warehouse = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 采购应付与入库同次完成且期初和已有应付不重复() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryCompanyMaterialReceiptRepository receipts = new InMemoryCompanyMaterialReceiptRepository();
        FactoryItemService catalog = catalog(items);
        CompanyMaterialReceiptService service = service(items, receipts, finance, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        FactoryItem eight = catalog.register("f02-item-08", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        FactoryItem ten = catalog.register("f02-item-10", admin, FactoryItemCategory.BEAD, "南红", "10mm", "颗", null, null);
        FactoryItem other = catalog.register("f02-item-qj", admin, FactoryItemCategory.BEAD, "青金", "8mm", "颗", null, null);

        CompanyMaterialReceipt bought = service.receive(
                "f02-buy-008", admin, eight.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("80.50"), null);
        assertThat(bought.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(bought.weight().value()).isNull();
        assertThat(bought.amountState()).isEqualTo(CompanyMaterialAmountState.KNOWN);
        assertThat(bought.amount()).isEqualByComparingTo("80.50");
        assertThat(service.owner()).isEqualTo(OwnerType.COMPANY);
        Payable payable = finance.findPayable(bought.payableCode()).orElseThrow();
        assertThat(payable.kind()).isEqualTo("公司料");
        assertThat(payable.customerId()).isNull();
        assertThat(payable.amount()).isEqualByComparingTo("80.50");
        assertThat(payable.sourceCode()).isEqualTo("CMR|" + bought.receiptId());
        assertThat(finance.listReceivables()).isEmpty();

        CompanyMaterialReceipt replay = service.receive(
                "f02-buy-008", admin, eight.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("80.50"), null);
        assertThat(replay.receiptId()).isEqualTo(bought.receiptId());
        assertThat(replay.batchCode()).isEqualTo(bought.batchCode());
        assertThat(finance.listPayables()).hasSize(1);
        CompanyMaterialBalance balance = service.balance(admin, eight.itemId(), bought.batchCode(), "WH-01");
        assertThat(balance.quantity()).isEqualByComparingTo("100");
        assertThat(balance.weight().value()).isNull();

        CompanyMaterialReceipt tenReceipt = service.receive(
                "f02-buy-010", admin, ten.itemId(), new BigDecimal("20"), new BigDecimal("1.250"), "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("10.00"), null);
        CompanyMaterialReceipt otherReceipt = service.receive(
                "f02-buy-qj1", admin, other.itemId(), new BigDecimal("7"), new BigDecimal("0.500"), "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "乙供应商", null, null);
        assertThat(tenReceipt.batchCode()).isNotEqualTo(bought.batchCode());
        assertThat(otherReceipt.amountState()).isEqualTo(CompanyMaterialAmountState.INCOMPLETE);
        assertThat(otherReceipt.amount()).isNull();
        assertThat(otherReceipt.payableCode()).isEmpty();
        assertThat(service.balance(admin, ten.itemId(), tenReceipt.batchCode(), "WH-01").quantity()).isEqualByComparingTo("20");
        assertThat(service.balance(admin, ten.itemId(), tenReceipt.batchCode(), "WH-01").weight().value()).isEqualByComparingTo("1.250");
        assertThat(service.balance(admin, other.itemId(), otherReceipt.batchCode(), "WH-01").quantity()).isEqualByComparingTo("7");

        CompanyMaterialReceipt opening = service.receive(
                "f02-open-01", admin, eight.itemId(), new BigDecimal("5"), new BigDecimal("0.400"), "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.OPENING, "", new BigDecimal("50.00"), null);
        assertThat(opening.sourceKind()).isEqualTo(CompanyMaterialSourceKind.OPENING);
        assertThat(opening.payableCode()).isEmpty();
        assertThat(opening.batchCode()).isNotEqualTo(bought.batchCode());
        assertThat(finance.listPayables()).hasSize(2);

        finance.savePayable(new Payable(
                "AP-OLD-01", "CONSUMABLE", "2026-10", "旧供应商", "CONSUMABLE|AP-OLD-01", null, "",
                new BigDecimal("30.00"), new BigDecimal("0.00"), "财务", LocalDateTime.now().toString()));
        finance.savePayable(new Payable(
                "AP-WAGE-1", "工资", "2026-10", "张三", "WAGE|1", null, "",
                new BigDecimal("12.00"), new BigDecimal("0.00"), "财务", LocalDateTime.now().toString()));
        CompanyMaterialReceipt covered = service.receive(
                "f02-cover01", admin, eight.itemId(), new BigDecimal("3"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.COVERED_PAYABLE, "", null, "AP-OLD-01");
        assertThat(covered.payableCode()).isEmpty();
        assertThat(covered.coveredPayableCode()).isEqualTo("AP-OLD-01");
        assertThat(covered.amount()).isEqualByComparingTo("30.00");
        assertThat(finance.listPayables()).hasSize(4);
        assertThatThrownBy(() -> service.receive(
                "f02-cover02", admin, eight.itemId(), new BigDecimal("1"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.COVERED_PAYABLE, "", null, "AP-OLD-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这笔应付已经对应过入库");
        assertThatThrownBy(() -> service.receive(
                "f02-wage-01", admin, eight.itemId(), new BigDecimal("1"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.COVERED_PAYABLE, "", null, "AP-WAGE-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("工资或外协应付不能当作公司料入库来源");

        finance.registerPayablePayment(new PayablePayment(
                "PAY-F02-01", bought.payableCode(), new BigDecimal("80.50"), "2026-10-30", "财务", "2026-10-30T12:00:00"), "f02-pay-01");
        assertThat(service.balance(admin, eight.itemId(), bought.batchCode(), "WH-01").quantity()).isEqualByComparingTo("100");
        assertThat(service.receive(
                "f02-buy-008", admin, eight.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("80.50"), null).receiptId())
                .isEqualTo(bought.receiptId());
        assertThat(finance.findPayable(bought.payableCode()).orElseThrow().paidAmount()).isEqualByComparingTo("80.50");
        assertThat(finance.listPayables().stream().filter(row -> "公司料".equals(row.kind()))).hasSize(2);

        assertThatThrownBy(() -> service.receive(
                "f02-zero-01", admin, eight.itemId(), new BigDecimal("1"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知金额不能写成 0");
        assertThatThrownBy(() -> service.receive(
                "f02-wt-0001", admin, eight.itemId(), new BigDecimal("1"), BigDecimal.ZERO, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知重量不能写成 0");

        FactoryItem glue = catalog.register("f02-glue-01", admin, FactoryItemCategory.CONSUMABLE, "胶水", "50ml", "瓶", null, null);
        assertThatThrownBy(() -> service.receive(
                "f02-glue-in", admin, glue.itemId(), new BigDecimal("1"), null, "瓶", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("耗材不在这张采购入库里");
        assertThatThrownBy(() -> service.receive(
                "f02-buy-008", admin, eight.itemId(), new BigDecimal("90"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("80.50"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> service.receive(
                "f02-denied1", warehouse, eight.itemId(), new BigDecimal("1"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("1.00"), null))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
        CompanyMaterialReceipt held = service.receive(
                "f02-held-01", warehouse, eight.itemId(), new BigDecimal("2"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null);
        assertThat(held.amount()).isNull();
        assertThat(held.payableCode()).isEmpty();
    }

    @Test
    void 失败不留下入库和应付() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryCompanyMaterialReceiptRepository receipts = new InMemoryCompanyMaterialReceiptRepository();
        FactoryItem item = catalog(items).register("f02-fail-it", admin, FactoryItemCategory.ACCESSORY, "扣针", "小号", "个", null, null);
        CompanyMaterialReceiptService failing = service(items, receipts, finance, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.receive(
                "f02-fail-01", admin, item.itemId(), new BigDecimal("4"), null, "个", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("6.00"), null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(receipts.balance(item.itemId(), "PC-NONE", "WH-01")).isEmpty();
        assertThat(finance.listPayables()).isEmpty();
    }

    @Test
    void jdbc往返一致且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository receipts = new JdbcCompanyMaterialReceiptRepository(dataSource);
        JdbcFinanceRepository finance = new JdbcFinanceRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        FactoryItem item = new FactoryItemService(items, unit, guard).register(
                "f02-jdbc-it", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        CompanyMaterialReceiptService service = new CompanyMaterialReceiptService(items, receipts, finance, unit, guard);
        CompanyMaterialReceipt bought = service.receive(
                "f02-jdbc-01", admin, item.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("80.50"), null);
        CompanyMaterialReceipt loaded = receipts.find(bought.receiptId()).orElseThrow();
        assertThat(loaded.weight().value()).isNull();
        assertThat(loaded.amount()).isEqualByComparingTo("80.50");
        assertThat(finance.findPayable(loaded.payableCode()).orElseThrow().sourceCode()).isEqualTo("CMR|" + loaded.receiptId());
        assertThat(receipts.balance(item.itemId(), loaded.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("100");
        CompanyMaterialReceipt again = service.receive(
                "f02-jdbc-01", admin, item.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("80.50"), null);
        assertThat(again.receiptId()).isEqualTo(bought.receiptId());
        assertThat(finance.listPayables()).hasSize(1);

        CompanyMaterialReceiptService failing = new CompanyMaterialReceiptService(items, receipts, finance,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.receive(
                "f02-jdbc-x1", admin, item.itemId(), new BigDecimal("4"), new BigDecimal("0.200"), "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", new BigDecimal("3.00"), null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(finance.listPayables()).hasSize(1);
    }

    @Test
    void 接口不把未知金额写成零() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        FactoryItem item = catalog(items).register("f02-http-it", admin, FactoryItemCategory.ACCESSORY, "镶标", "小号", "个", null, null);
        CompanyMaterialReceiptService service = service(
                items, new InMemoryCompanyMaterialReceiptRepository(), finance,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(admin);
        CompanyMaterialReceiptController controller = new CompanyMaterialReceiptController(service, actors);
        CompanyMaterialReceipt saved = controller.receive(new CompanyMaterialReceiptController.ReceiveRequest(
                "f02-http-01", item.itemId(), new BigDecimal("6"), null, "个", "WH-01", "2026-10-30",
                "PURCHASE", "甲供应商", null, null), request).data();
        assertThat(saved.amount()).isNull();
        assertThat(saved.amountState()).isEqualTo(CompanyMaterialAmountState.INCOMPLETE);
        assertThat(finance.listPayables()).isEmpty();
        assertThat(saved.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
    }

    private FactoryItemService catalog(InMemoryFactoryItemRepository items) {
        return new FactoryItemService(items, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), guard);
    }

    private CompanyMaterialReceiptService service(InMemoryFactoryItemRepository items, InMemoryCompanyMaterialReceiptRepository receipts,
                                                  InMemoryFinanceRepository finance, MemoryBusinessWorkUnit unit) {
        return new CompanyMaterialReceiptService(items, receipts, finance, unit, guard);
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{"V44__m3_command_request.sql", "V36__m3_finance_operations.sql", "V97__m3_factory_item.sql", "V98__m3_company_material_receipt.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
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
