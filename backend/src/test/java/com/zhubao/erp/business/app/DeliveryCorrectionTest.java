package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryCorrectionIntent;
import com.zhubao.erp.business.domain.DeliveryDownstream;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliveryImpact;
import com.zhubao.erp.business.domain.DeliverySourceSelection;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryDeliveryDraftRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryCorrectionTest {

    @Test
    void 已确认交付不覆盖消费数量也不删除费用() {
        Harness harness = harness();
        DeliveryDraft confirmed = confirm(harness, "BAG-B", "e03-draft1", "e03-bag-b01");
        harness.downstream.consumed = new BigDecimal("30");
        harness.downstream.charge = true;
        harness.downstream.refund = true;
        harness.documents.saveOutboundOrder(outbound(confirmed.deliveryId() + "-1"));

        DeliveryImpact impact = harness.correction.impact(confirmed.deliveryId());
        assertThat(impact.handover()).isEqualTo("待核");
        assertThat(impact.handover()).isNotEqualTo("未交接");
        assertThat(impact.confirmedChargeRecord()).isTrue();
        assertThat(impact.confirmedRefundRecord()).isTrue();
        assertThat(impact.sources()).singleElement().satisfies(source -> {
            assertThat(source.originalQuantity()).isEqualByComparingTo("80");
            assertThat(source.consumedQuantity()).isEqualByComparingTo("30");
        });
        assertThat(harness.correction.impact(confirmed.deliveryId()).status()).isEqualTo(DeliveryDraftStatus.CONFIRMED);

        assertThatThrownBy(() -> harness.correction.apply("e03-over01", "clerk-01", confirmed.deliveryId(), 2,
                DeliveryCorrectionIntent.OVERWRITE_QUANTITY))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能直接覆盖已消费数量");
        assertThatThrownBy(() -> harness.correction.apply("e03-reopen", "clerk-01", confirmed.deliveryId(), 2,
                DeliveryCorrectionIntent.CANCEL_AND_REOPEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能删除已确认费用或退款");
        assertThatThrownBy(() -> harness.correction.apply("e03-revers", "clerk-01", confirmed.deliveryId(), 2,
                DeliveryCorrectionIntent.REVERSE_BEFORE_HANDOVER))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能直接覆盖已消费数量");

        harness.downstream.consumed = BigDecimal.ZERO;
        harness.downstream.charge = false;
        harness.downstream.refund = false;
        assertThatThrownBy(() -> harness.correction.apply("e03-hand01", "clerk-01", confirmed.deliveryId(), 2,
                DeliveryCorrectionIntent.REVERSE_BEFORE_HANDOVER))
                .isInstanceOf(BusinessException.class)
                .hasMessage("交接与取消规则尚未核定，不能按未交接直接取消");
        assertThatThrownBy(() -> harness.correction.apply("e03-wipe01", "clerk-01", confirmed.deliveryId(), 2,
                DeliveryCorrectionIntent.CANCEL_AND_REOPEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能以取消重开抹平历史");

        DeliveryConfirmedSource source = harness.store.confirmedSources(confirmed.deliveryId()).get(0);
        assertThat(source.quantity()).isEqualByComparingTo("80");
        assertThat(harness.store.find(confirmed.deliveryId()).orElseThrow().status()).isEqualTo(DeliveryDraftStatus.CONFIRMED);
        assertThat(harness.store.find(confirmed.deliveryId()).orElseThrow().version()).isEqualTo(2);
        assertThat(harness.store.listIds(null)).hasSize(1);
        assertThat(harness.documents.findOutboundOrderByCode(confirmed.deliveryId() + "-1").orElseThrow().quantity())
                .isEqualByComparingTo("80");
        assertThat(harness.ledger.findAllLedgers()).isEmpty();
        assertThat(harness.bags.find("BAG-B").orElseThrow().quantity()).isEqualByComparingTo("80");
    }

    @Test
    void 草稿作废保留原明细且失败回滚() {
        Harness harness = harness();
        var sheet = open(harness, "BAG-B", "e03-draft2", "e03-bag-b02");
        DeliveryDraft abandoned = harness.correction.apply("e03-abandon", "clerk-01", sheet.draft().deliveryId(), 1,
                DeliveryCorrectionIntent.ABANDON_DRAFT);
        DeliveryDraft replay = harness.correction.apply("e03-abandon", "clerk-01", sheet.draft().deliveryId(), 1,
                DeliveryCorrectionIntent.ABANDON_DRAFT);
        assertThat(replay.deliveryId()).isEqualTo(abandoned.deliveryId());
        assertThat(abandoned.status()).isEqualTo(DeliveryDraftStatus.ABANDONED);
        assertThat(abandoned.version()).isEqualTo(2);
        assertThat(harness.store.lines(abandoned.deliveryId())).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualByComparingTo("80"));
        assertThat(harness.store.listIds(null)).hasSize(1);
        assertThatThrownBy(() -> harness.confirmation.confirm("e03-conf01", "clerk-01", abandoned.deliveryId(), 2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该交付草稿已作废");
        assertThat(harness.ledger.findAllLedgers()).isEmpty();

        var other = open(harness, "BAG-C", "e03-draft3", "e03-bag-c01");
        DeliveryCorrectionService failing = new DeliveryCorrectionService(harness.store, harness.downstream,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }));
        assertThatThrownBy(() -> failing.apply("e03-fail01", "clerk-01", other.draft().deliveryId(), 1,
                DeliveryCorrectionIntent.ABANDON_DRAFT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(harness.store.find(other.draft().deliveryId()).orElseThrow().status()).isEqualTo(DeliveryDraftStatus.DRAFT);
        assertThat(harness.store.lines(other.draft().deliveryId())).hasSize(1);
    }

    private static DeliveryDraft confirm(Harness harness, String bagId, String requestId, String bagRequest) {
        var sheet = open(harness, bagId, requestId, bagRequest);
        harness.unit.execute(new CommandCall("e03-seed-" + bagId, "clerk-01", "SEED_CONFIRM", "DELIVERY_DRAFT",
                sheet.draft().deliveryId(), sheet.draft().deliveryId()), session -> {
            harness.store.confirm(new DeliveryDraft(
                    sheet.draft().deliveryId(), sheet.draft().requestId(), sheet.draft().customerId(),
                    sheet.draft().businessKind(), sheet.draft().logisticsNote(), sheet.draft().remark(),
                    DeliveryDraftStatus.CONFIRMED, 2), List.of(new DeliveryConfirmedSource(
                    sheet.draft().deliveryId(), 1, bagId, sheet.draft().deliveryId() + "-1", "STK-E03",
                    new BigDecimal("80"), new BigDecimal("5"))), 1, session);
            return sheet.draft().deliveryId();
        });
        return harness.store.find(sheet.draft().deliveryId()).orElseThrow();
    }

    private static com.zhubao.erp.business.domain.DeliveryDraftSheet open(Harness harness, String bagId,
                                                                         String requestId, String bagRequest) {
        harness.bags.save(new ActivityBag(bagId, "MD-20261030-1001", "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal("80"), StockMeasurement.known(new BigDecimal("5")), 1), 0, null);
        return harness.drafts.open(requestId, "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection(bagId, 1, "MD-20261030-1001", "颗")));
    }

    private static Harness harness() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(new MotherOrderRecord("MD-20261030-1001", 7L, 7L, "牙骨",
                null, null, null, null, null, null, null, "CUSTOMER", true, "WH-01",
                new BigDecimal("80"), "颗", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));
        InMemoryDeliveryDraftRepository store = new InMemoryDeliveryDraftRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        DeliveryDraftService drafts = new DeliveryDraftService(
                store, bags, new DocumentProcessingTargetCustomer(documents), bagId -> true, unit);
        FakeDownstream downstream = new FakeDownstream();
        DeliveryCorrectionService correction = new DeliveryCorrectionService(store, downstream, unit);
        DeliveryConfirmationService confirmation = new DeliveryConfirmationService(
                store, bags, new DocumentDeliveryStockPlaceLookup(documents), bagId -> true,
                new com.zhubao.erp.inventory.app.StockLedgerService(new InMemoryStockLedgerRepository()),
                documents, unit);
        return new Harness(bags, documents, store, drafts, correction, confirmation, downstream,
                new InMemoryStockLedgerRepository(), unit);
    }

    private static OutboundOrderRecord outbound(String code) {
        return new OutboundOrderRecord(code, "MD-20261030-1001", "MD-20261030-1001", 7L, 7L, "牙骨",
                "WH-01", "WH-01", "2026-10-30", "交付", new BigDecimal("80"), "颗", new BigDecimal("5"),
                "", "", "", "", "DELIVERY", 1, "", "clerk-01", "", "", "SHIPPED", "STK-E03",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.parse("2026-10-30T12:00:00")));
    }

    private record Harness(
            InMemoryActivityBagRepository bags,
            InMemoryBusinessDocumentRepository documents,
            InMemoryDeliveryDraftRepository store,
            DeliveryDraftService drafts,
            DeliveryCorrectionService correction,
            DeliveryConfirmationService confirmation,
            FakeDownstream downstream,
            InMemoryStockLedgerRepository ledger,
            MemoryBusinessWorkUnit unit
    ) {
    }

    private static final class FakeDownstream implements DeliveryDownstream {
        private BigDecimal consumed = BigDecimal.ZERO;
        private boolean charge;
        private boolean refund;

        @Override
        public BigDecimal consumedQuantity(String sourceCode) {
            return consumed;
        }

        @Override
        public boolean hasConfirmedCharge(String deliveryId) {
            return charge;
        }

        @Override
        public boolean hasConfirmedRefund(String deliveryId) {
            return refund;
        }
    }
}
