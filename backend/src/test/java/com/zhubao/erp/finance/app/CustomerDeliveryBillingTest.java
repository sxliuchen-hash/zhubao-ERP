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

    private static String confirmDelivery(Harness harness, BigDecimal quantity) {
        harness.documents.saveMotherOrder(new MotherOrderRecord("MD-20261030-1001", 7L, 7L, "牙骨",
                null, null, null, null, null, null, null, "CUSTOMER", true, "WH-01",
                new BigDecimal("500"), "颗", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));
        String bagId = "BAG-" + quantity.stripTrailingZeros().toPlainString();
        harness.bags.save(new ActivityBag(bagId, "MD-20261030-1001", "牙骨", "REQ-1", "LINE-1", "", false,
                quantity, StockMeasurement.known(new BigDecimal("5")), 1), 0, null);
        var sheet = new com.zhubao.erp.business.app.DeliveryDraftService(
                harness.store, harness.bags, new com.zhubao.erp.business.app.DocumentProcessingTargetCustomer(harness.documents),
                ignored -> true, harness.unit).open("e06-draft-" + bagId, "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection(bagId, 1, "MD-20261030-1001", "颗")));
        harness.unit.execute(new CommandCall("e06-seed-" + bagId, "clerk-01", "SEED_CONFIRM", "DELIVERY_DRAFT",
                sheet.draft().deliveryId(), sheet.draft().deliveryId()), session -> {
            harness.store.confirm(new DeliveryDraft(
                    sheet.draft().deliveryId(), sheet.draft().requestId(), 7L, sheet.draft().businessKind(),
                    "", "", DeliveryDraftStatus.CONFIRMED, 2), List.of(new DeliveryConfirmedSource(
                    sheet.draft().deliveryId(), 1, bagId, sheet.draft().deliveryId() + "-1", "STK-E06",
                    quantity, new BigDecimal("5"))), 1, session);
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
