package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliverySourceSelection;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryDeliveryDraftRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.CustomerBillAuthorization;
import com.zhubao.erp.finance.domain.CustomerBillSnapshot;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillLine;
import com.zhubao.erp.finance.domain.CustomerChargeDirection;
import com.zhubao.erp.finance.domain.CustomerChargeLine;
import com.zhubao.erp.finance.domain.CustomerChargeMethod;
import com.zhubao.erp.finance.domain.CustomerDeliveryBill;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.infra.InMemoryCustomerDeliveryBillRepository;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerDeliveryBillingTest {

    private final OperatorContext actor = new OperatorContext("账务员", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);

    @Test
    void 未交齐仍可出账且不重复应收() {
        Harness harness = harness((actor, customerId) -> { });
        String deliveryId = confirmDelivery(harness, new BigDecimal("462"));
        List<CustomerChargeLine> lines = List.of(charge("462", "2"));

        CustomerDeliveryBill bill = harness.billing.confirm(actor, "e06-bill01", deliveryId, lines, new BigDecimal("38"), null);
        CustomerDeliveryBill replay = harness.billing.confirm(actor, "e06-bill01", deliveryId, lines, new BigDecimal("38"), null);
        assertThat(replay.billId()).isEqualTo(bill.billId());
        assertThat(bill.totalAmount()).isEqualByComparingTo("924.00");
        assertThat(bill.rootBillId()).isEqualTo(bill.billId());
        assertThat(harness.bills.lines(bill.billId())).singleElement()
                .satisfies(line -> assertThat(line.amount()).isEqualByComparingTo("924.00"));
        assertThat(harness.finance.listReceivables()).singleElement().satisfies(row -> {
            assertThat(row.amount()).isEqualByComparingTo("924.00");
            assertThat(row.sourceType()).isEqualTo("DELIVERY_BILL");
            assertThat(row.unit()).isEqualTo(ChargeUnit.JOB);
        });
        assertThat(harness.finance.listPayrollLines()).isEmpty();
        assertThatThrownBy(() -> harness.billing.confirm(actor, "e06-bill02", deliveryId, lines, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一已确认发货单只能有一张初始客户账单");
        assertThat(harness.finance.listReceivables()).hasSize(1);

        CustomerDeliveryBill adjusted = harness.billing.adjust(actor, "e06-adj001", deliveryId, List.of(
                new CustomerChargeLine("加工费", "MD-20261030-1001", CustomerChargeMethod.AGREED_AMOUNT, "颗",
                        null, null, null, null, new BigDecimal("800"), null, "改按约定")), null);
        assertThat(adjusted.rootBillId()).isEqualTo(bill.billId());
        assertThat(adjusted.totalAmount()).isEqualByComparingTo("800.00");
        assertThat(harness.finance.findReceivableBySourceKey("delivery-bill:" + bill.billId()).orElseThrow().amount())
                .isEqualByComparingTo("924.00");
        assertThat(harness.finance.listReceivables()).hasSize(2);
        assertThat(harness.finance.findReceivableBySourceKey("delivery-bill:" + adjusted.billId()).orElseThrow().amount())
                .isEqualByComparingTo("-124.00");
        assertThat(harness.finance.listPayrollLines()).isEmpty();

        assertThatThrownBy(() -> harness.billing.confirm(actor, "e06-bill01", deliveryId, List.of(
                new CustomerChargeLine("加工费", "MD-20261030-1001", CustomerChargeMethod.QUANTITY, "颗",
                        new BigDecimal("462"), new BigDecimal("2"), new BigDecimal("462"), new BigDecimal("5"),
                        null, null, "")), new BigDecimal("38"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThat(harness.finance.listReceivables()).hasSize(2);
    }

    @Test
    void 不能用其他目标的旧账覆盖本次交付() {
        Harness harness = harness((actor, customerId) -> { });
        harness.finance.saveReceivable(new ReceivableLine(
                "YS-OTHER", 7L, "甲", "2026-10", "ORDER_SETTLEMENT", "other-settlement",
                "MD-20261030-1999", "MD-20261030-1999", "别人的旧账", BigDecimal.ONE, ChargeUnit.JOB,
                new BigDecimal("100.00"), new BigDecimal("100.00"), "", "", "账务员", "2026-10-30T00:00:00"));
        String deliveryId = confirmDelivery(harness, new BigDecimal("10"));
        assertThatThrownBy(() -> harness.billing.confirm(actor, "rf14-other1", deliveryId, List.of(
                new CustomerChargeLine("加工费", "MD-20261030-1999", CustomerChargeMethod.QUANTITY, "颗",
                        new BigDecimal("10"), new BigDecimal("2"), null, null, null, null, "")),
                null, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("计费目标不属于本次交付");
        assertThat(harness.bills.findInitial(deliveryId)).isEmpty();
    }

    @Test
    void 已确认账单按原输入复算不解析表达式() {
        Harness harness = harness((actor, customerId) -> { });
        String deliveryId = confirmDelivery(harness, new BigDecimal("10"));
        CustomerDeliveryBill bill = harness.billing.confirm(actor, "rf15-bill01", deliveryId, List.of(
                new CustomerChargeLine("加工费", "MD-20261030-1001", CustomerChargeMethod.BASE_MINUS_QUANTITY, "颗",
                        null, new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("1.5"), null, null, "按基数扣免计")),
                null, null);
        List<CustomerDeliveryBillLine> stored = harness.bills.lines(bill.billId());
        assertThat(stored).singleElement().satisfies(line -> {
            assertThat(line.exemptQuantity()).isEqualByComparingTo("1.500");
            assertThat(line.billQuantity()).isEqualByComparingTo("8.500");
            assertThat(line.basis()).isEqualTo("按基数扣免计");
            assertThat(line.expression()).contains("−");
        });
        assertThat(CustomerBillSnapshot.recompute(deliveryId, stored).totalAmount()).isEqualByComparingTo("17.00");
        assertThatThrownBy(() -> CustomerBillSnapshot.recompute(deliveryId, List.of(new CustomerDeliveryBillLine(
                bill.billId(), 1, "加工费", CustomerChargeMethod.QUANTITY, new BigDecimal("10.00"), "10颗 × 1元／颗＝10元",
                "", "", null, null, null, null, null, null, null, "", null, null, null, null, null, null, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账单快照不完整，不能复算");
    }

    @Test
    void 旧凭据须核清覆盖且失败不留应收() {
        Harness harness = harness((actor, customerId) -> { });
        harness.finance.saveReceivable(new ReceivableLine(
                "YS-OLD", 7L, "甲", "2026-10", "ORDER_SETTLEMENT", "old-settlement",
                "MD-20261030-1001", "MD-20261030-1001", "旧结单", BigDecimal.ONE, ChargeUnit.JOB,
                new BigDecimal("100.00"), new BigDecimal("100.00"), "", "", "账务员", "2026-10-30T00:00:00"));
        String deliveryId = confirmDelivery(harness, new BigDecimal("462"));
        List<CustomerChargeLine> lines = List.of(charge("462", "2"));
        assertThatThrownBy(() -> harness.billing.confirm(actor, "e06-cover0", deliveryId, lines, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已有正式凭据，须先核清覆盖");
        CustomerDeliveryBill bill = harness.billing.confirm(
                actor, "e06-cover1", deliveryId, lines, new BigDecimal("38"), new BigDecimal("100.00"));
        assertThat(bill.totalAmount()).isEqualByComparingTo("924.00");
        assertThat(harness.finance.findReceivableByCode("YS-OLD").orElseThrow().amount()).isEqualByComparingTo("100.00");
        assertThat(harness.finance.findReceivableBySourceKey("delivery-bill:" + bill.billId()).orElseThrow().amount())
                .isEqualByComparingTo("824.00");

        Harness denied = harness((actor, customerId) -> {
            throw new PermissionDeniedException("无权入账");
        });
        String openDelivery = confirmDelivery(denied, new BigDecimal("10"));
        assertThatThrownBy(() -> denied.billing.confirm(actor, "e06-deny01", openDelivery, List.of(charge("10", "2")), null, null))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(denied.bills.findInitial(openDelivery)).isEmpty();
        assertThat(denied.finance.listReceivables()).isEmpty();

        Harness failing = harness((actor, customerId) -> { });
        String another = confirmDelivery(failing, new BigDecimal("10"));
        CustomerDeliveryBillingService broken = new CustomerDeliveryBillingService(
                failing.store, new com.zhubao.erp.business.app.DocumentDeliveryStockPlaceLookup(failing.documents),
                failing.bills, failing.finance, failing.authorization,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }));
        assertThatThrownBy(() -> broken.confirm(actor, "e06-fail01", another, List.of(charge("10", "2")), null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(failing.bills.findInitial(another)).isEmpty();
        assertThat(failing.finance.listReceivables()).isEmpty();
        assertThat(failing.finance.listPayrollLines()).isEmpty();
    }

    @Test
    void 同一旧凭据不能重复抵两次发货() {
        Harness harness = harness((actor, customerId) -> { });
        harness.finance.saveReceivable(receivable("YS-OLD", "100.00"));
        String first = confirmDelivery(harness, new BigDecimal("100"), "rf16a");
        List<CustomerChargeLine> lines = List.of(charge("100", "1"));
        CustomerDeliveryBill bill = harness.billing.confirm(
                actor, "rf16-bill-a", first, lines, null, new BigDecimal("100.00"));
        assertThat(harness.billing.confirm(actor, "rf16-bill-a", first, lines, null, new BigDecimal("100.00")).billId())
                .isEqualTo(bill.billId());
        assertThat(harness.bills.coveredTotal("YS-OLD")).isEqualByComparingTo("100.00");
        assertThat(harness.finance.findReceivableByCode("YS-OLD").orElseThrow().amount()).isEqualByComparingTo("100.00");
        assertThat(harness.finance.listReceivables()).hasSize(1);

        String second = confirmDelivery(harness, new BigDecimal("100"), "rf16b");
        assertThatThrownBy(() -> harness.billing.confirm(
                actor, "rf16-bill-b", second, lines, null, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有可核清的旧凭据");
        assertThat(harness.bills.findInitial(second)).isEmpty();
        CustomerDeliveryBill another = harness.billing.confirm(actor, "rf16-bill-c", second, lines, null, null);
        assertThat(another.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(harness.finance.listReceivables()).hasSize(2);
        assertThat(harness.finance.findReceivableBySourceKey("delivery-bill:" + another.billId()).orElseThrow().amount())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void 旧凭据按剩余额度分次覆盖且调整不退回() {
        Harness harness = harness((actor, customerId) -> { });
        harness.finance.saveReceivable(receivable("YS-PART", "100.00"));
        String first = confirmDelivery(harness, new BigDecimal("100"), "rf16c");
        harness.billing.confirm(actor, "rf16-part1", first, List.of(charge("100", "1")), null, new BigDecimal("40.00"));
        String second = confirmDelivery(harness, new BigDecimal("100"), "rf16d");
        CustomerDeliveryBill secondBill = harness.billing.confirm(
                actor, "rf16-part2", second, List.of(charge("100", "1")), null, new BigDecimal("60.00"));
        assertThat(harness.bills.coveredTotal("YS-PART")).isEqualByComparingTo("100.00");
        assertThat(harness.finance.findReceivableByCode("YS-PART").orElseThrow().amount()).isEqualByComparingTo("100.00");
        assertThat(harness.finance.findReceivableBySourceKey("delivery-bill:" + secondBill.billId()).orElseThrow().amount())
                .isEqualByComparingTo("40.00");
        assertThatThrownBy(() -> harness.billing.confirm(
                actor, "rf16-part3", confirmDelivery(harness, new BigDecimal("10"), "rf16e"),
                List.of(charge("10", "1")), null, new BigDecimal("1.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有可核清的旧凭据");

        harness.billing.adjust(actor, "rf16-adj01", first, List.of(charge("80", "1")), null);
        assertThat(harness.bills.coveredTotal("YS-PART")).isEqualByComparingTo("100.00");
        assertThat(harness.finance.findReceivableByCode("YS-PART").orElseThrow().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void 多笔旧凭据来源不明时拒绝覆盖() {
        Harness harness = harness((actor, customerId) -> { });
        harness.finance.saveReceivable(receivable("YS-ONE", "60.00"));
        harness.finance.saveReceivable(receivable("YS-TWO", "40.00"));
        String deliveryId = confirmDelivery(harness, new BigDecimal("100"), "rf16f");
        assertThatThrownBy(() -> harness.billing.confirm(
                actor, "rf16-mix01", deliveryId, List.of(charge("100", "1")), null, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("旧凭据来源不明，须指定凭据");
        assertThat(harness.bills.findInitial(deliveryId)).isEmpty();
        assertThat(harness.bills.coveredTotal("YS-ONE")).isEqualByComparingTo("0");
        assertThat(harness.finance.listReceivables()).hasSize(2);
    }

    @Test
    void 覆盖写入失败后不留下额度() {
        Harness failing = harness((actor, customerId) -> { });
        failing.finance.saveReceivable(receivable("YS-ROLL", "100.00"));
        String deliveryId = confirmDelivery(failing, new BigDecimal("100"), "rf16g");
        CustomerDeliveryBillingService broken = new CustomerDeliveryBillingService(
                failing.store, new com.zhubao.erp.business.app.DocumentDeliveryStockPlaceLookup(failing.documents),
                failing.bills, failing.finance, failing.authorization,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }));
        assertThatThrownBy(() -> broken.confirm(
                actor, "rf16-roll1", deliveryId, List.of(charge("100", "1")), null, new BigDecimal("100.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(failing.bills.findInitial(deliveryId)).isEmpty();
        assertThat(failing.bills.coveredTotal("YS-ROLL")).isEqualByComparingTo("0");
        assertThat(failing.finance.findReceivableByCode("YS-ROLL").orElseThrow().amount()).isEqualByComparingTo("100.00");
        assertThat(failing.finance.listReceivables()).hasSize(1);
    }

    @Test
    void 整单议定价也须核清来源且可明确选择旧凭据() {
        Harness harness = harness(new GuardedCustomerBillAuthorization(new com.zhubao.erp.security.app.PermissionGuard()));
        String delivery = confirmDelivery(harness, new BigDecimal("10"), "blank-target");
        harness.finance.saveReceivable(receivable("YS-FIRST", "100"));
        harness.finance.saveReceivable(receivable("YS-SECOND", "100"));
        List<CustomerChargeLine> lines = List.of(agreed("100"));
        assertThatThrownBy(() -> harness.billing.confirm(actor, "rf16-blank01", delivery, lines, null, null))
                .isInstanceOf(BusinessException.class).hasMessage("旧凭据来源不明，须指定凭据");
        assertThat(harness.finance.listReceivables()).hasSize(2);
        CustomerDeliveryBill bill = harness.billing.confirm(actor, "rf16-select1", delivery, lines, null, new BigDecimal("100"), "YS-SECOND");
        assertThat(harness.bills.coveredTotal("YS-FIRST")).isEqualByComparingTo("0");
        assertThat(harness.bills.coveredTotal("YS-SECOND")).isEqualByComparingTo("100");
        assertThat(harness.finance.listReceivables()).hasSize(2);
        assertThat(harness.billing.confirm(actor, "rf16-select1", delivery, lines, null, new BigDecimal("100"), "YS-SECOND").billId())
                .isEqualTo(bill.billId());
        assertThatThrownBy(() -> harness.billing.confirm(actor, "rf16-select1", delivery, lines, null, new BigDecimal("100"), "YS-FIRST"))
                .isInstanceOf(BusinessException.class).hasMessage("同一请求的内容不一致");
        assertThat(harness.bills.coverages(bill.billId())).singleElement().satisfies(row -> assertThat(row.receivableCode()).isEqualTo("YS-SECOND"));
    }

    @Test
    void 单笔旧账不能由空收费目标绕过也不能指定其他来源() {
        Harness harness = harness(new GuardedCustomerBillAuthorization(new com.zhubao.erp.security.app.PermissionGuard()));
        String delivery = confirmDelivery(harness, new BigDecimal("10"), "single-blank");
        harness.finance.saveReceivable(receivable("YS-ONLY", "100"));
        assertThatThrownBy(() -> harness.billing.confirm(actor, "rf16-one-blank", delivery, List.of(agreed("100")), null, null))
                .isInstanceOf(BusinessException.class).hasMessage("已有正式凭据，须先核清覆盖");
        assertThatThrownBy(() -> harness.billing.confirm(actor, "rf16-wrong-id", delivery, List.of(agreed("100")), null, new BigDecimal("100"), "YS-OTHER"))
                .isInstanceOf(BusinessException.class).hasMessage("指定旧凭据不属于本次交付或额度已用完");
        assertThat(harness.bills.findInitial(delivery)).isEmpty();
        assertThat(harness.finance.listReceivables()).hasSize(1);
    }

    @Test
    void 混合单位按项目和议定金额出账及调整均保留独立覆盖() {
        Harness harness = harness(new GuardedCustomerBillAuthorization(new com.zhubao.erp.security.app.PermissionGuard()));
        String delivery = confirmDelivery(harness, new BigDecimal("10"), "mixed-units", true);
        List<CustomerChargeLine> lines = List.of(charge("10", "2"), new CustomerChargeLine(
                "配件费", "MD-20261030-1002", CustomerChargeMethod.QUANTITY, "件", new BigDecimal("2"), new BigDecimal("3"), null, null, null, null, ""));
        CustomerDeliveryBill bill = harness.billing.confirm(actor, "rf50-mixed-bill", delivery, lines, null, null);
        assertThat(bill.totalAmount()).isEqualByComparingTo("26");
        var snapshot = CustomerBillSnapshot.recompute(delivery, harness.bills.lines(bill.billId()));
        assertThat(snapshot.coverage().shippedQuantity()).isNull();
        assertThat(snapshot.coverage().sources()).hasSize(2).anySatisfy(source -> {
            assertThat(source.quantityUnit()).isEqualTo("颗"); assertThat(source.quantity()).isEqualByComparingTo("10");
        }).anySatisfy(source -> { assertThat(source.quantityUnit()).isEqualTo("件"); assertThat(source.quantity()).isEqualByComparingTo("2"); });
        CustomerDeliveryBill adjusted = harness.billing.adjust(actor, "rf50-mixed-agreed", delivery, List.of(agreed("100")), null);
        assertThat(CustomerBillSnapshot.recompute(delivery, harness.bills.lines(adjusted.billId())).totalAmount()).isEqualByComparingTo("100");
        assertThat(harness.finance.listReceivables().stream().map(ReceivableLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
        assertThatThrownBy(() -> harness.billing.adjust(actor, "rf50-loss-unit", delivery, List.of(agreed("100")), BigDecimal.ONE))
                .isInstanceOf(BusinessException.class).hasMessage("混合单位损耗须按项目分别核定");
    }

    private static CustomerChargeLine agreed(String amount) {
        return new CustomerChargeLine("整单加工费", "", CustomerChargeMethod.AGREED_AMOUNT, "", null, null, null, null,
                new BigDecimal(amount), null, "本次交付议定");
    }

    private static ReceivableLine receivable(String code, String amount) {
        return new ReceivableLine(
                code, 7L, "甲", "2026-10", "ORDER_SETTLEMENT", "old-" + code,
                "MD-20261030-1001", "MD-20261030-1001", "旧结单", BigDecimal.ONE, ChargeUnit.JOB,
                new BigDecimal(amount), new BigDecimal(amount), "", "", "账务员", "2026-10-30T00:00:00");
    }

    private static String confirmDelivery(Harness harness, BigDecimal quantity) {
        return confirmDelivery(harness, quantity, quantity.stripTrailingZeros().toPlainString());
    }

    private static String confirmDelivery(Harness harness, BigDecimal quantity, String marker) {
        return confirmDelivery(harness, quantity, marker, false);
    }

    private static String confirmDelivery(Harness harness, BigDecimal quantity, String marker, boolean mixed) {
        harness.documents.saveMotherOrder(new MotherOrderRecord("MD-20261030-1001", 7L, 7L, "牙骨",
                null, null, null, null, null, null, null, "CUSTOMER", true, "WH-01",
                new BigDecimal("500"), "颗", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));
        String bagId = "BAG-" + marker;
        harness.bags.save(new ActivityBag(bagId, "MD-20261030-1001", "牙骨", "REQ-1", "LINE-1", "", false,
                quantity, StockMeasurement.known(new BigDecimal("5")), 1), 0, null);
        var selections = new java.util.ArrayList<DeliverySourceSelection>();
        selections.add(new DeliverySourceSelection(bagId, 1, "MD-20261030-1001", "颗"));
        if (mixed) {
            harness.documents.saveMotherOrder(new MotherOrderRecord("MD-20261030-1002", 7L, 7L, "牙骨",
                    null, null, null, null, null, null, null, "CUSTOMER", true, "WH-01",
                    new BigDecimal("2"), "件", null, null, null, null, null, null, null, null, null, null, null, null, null, null));
            harness.bags.save(new ActivityBag(bagId + "-2", "MD-20261030-1002", "牙骨", "REQ-1", "LINE-2", "", false,
                    new BigDecimal("2"), StockMeasurement.known(BigDecimal.ONE), 1), 0, null);
            selections.add(new DeliverySourceSelection(bagId + "-2", 1, "MD-20261030-1002", "件"));
        }
        var sheet = new com.zhubao.erp.business.app.DeliveryDraftService(
                harness.store, harness.bags, new com.zhubao.erp.business.app.DocumentProcessingTargetCustomer(harness.documents),
                ignored -> true, harness.unit).open("e06-draft-" + marker, "clerk-01", 7L, "", "", selections);
        harness.unit.execute(new CommandCall("e06-seed-" + marker, "clerk-01", "SEED_CONFIRM", "DELIVERY_DRAFT",
                sheet.draft().deliveryId(), sheet.draft().deliveryId()), session -> {
            var sources = new java.util.ArrayList<DeliveryConfirmedSource>();
            sources.add(new DeliveryConfirmedSource(sheet.draft().deliveryId(), 1, bagId, sheet.draft().deliveryId() + "-1", "STK-E06", quantity, new BigDecimal("5")));
            if (mixed) sources.add(new DeliveryConfirmedSource(sheet.draft().deliveryId(), 2, bagId + "-2", sheet.draft().deliveryId() + "-2", "STK-E06-2", new BigDecimal("2"), BigDecimal.ONE));
            harness.store.confirm(new DeliveryDraft(
                    sheet.draft().deliveryId(), sheet.draft().requestId(), 7L, sheet.draft().businessKind(),
                    "", "", DeliveryDraftStatus.CONFIRMED, 2), sources, 1, session);
            return sheet.draft().deliveryId();
        });
        return sheet.draft().deliveryId();
    }

    private static CustomerChargeLine charge(String quantity, String price) {
        return new CustomerChargeLine("加工费", "MD-20261030-1001", CustomerChargeMethod.QUANTITY, "颗",
                new BigDecimal(quantity), new BigDecimal(price), null, null, null, null, "");
    }

    private static Harness harness(CustomerBillAuthorization authorization) {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        InMemoryDeliveryDraftRepository store = new InMemoryDeliveryDraftRepository();
        InMemoryCustomerDeliveryBillRepository bills = new InMemoryCustomerDeliveryBillRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        CustomerDeliveryBillingService billing = new CustomerDeliveryBillingService(
                store, new com.zhubao.erp.business.app.DocumentDeliveryStockPlaceLookup(documents),
                bills, finance, authorization, unit);
        return new Harness(bags, documents, store, bills, finance, billing, authorization, unit);
    }

    private record Harness(
            InMemoryActivityBagRepository bags,
            InMemoryBusinessDocumentRepository documents,
            InMemoryDeliveryDraftRepository store,
            InMemoryCustomerDeliveryBillRepository bills,
            FinanceRepository finance,
            CustomerDeliveryBillingService billing,
            CustomerBillAuthorization authorization,
            MemoryBusinessWorkUnit unit
    ) {
    }
}
