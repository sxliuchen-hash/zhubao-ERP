package com.zhubao.erp.label.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliverySourceSelection;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryDeliveryDraftRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.label.domain.GenerateLabelCommand;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelType;
import com.zhubao.erp.label.domain.ShippingBagLabelPreview;
import com.zhubao.erp.label.infra.InMemoryLabelTemplateRepository;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShippingBagLabelTest {

    @Test
    void 确认快照生成发货袋且补打不出库() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(mother());
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryDeliveryDraftRepository drafts = new InMemoryDeliveryDraftRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        bags.save(new ActivityBag("BAG-B", "MD-20261030-1001", "牙骨", "要求".repeat(10), "LINE-1", "", false,
                new BigDecimal("80"), StockMeasurement.unknown(), 1), 0, null);
        var sheet = new com.zhubao.erp.business.app.DeliveryDraftService(
                drafts, bags, new com.zhubao.erp.business.app.DocumentProcessingTargetCustomer(documents),
                ignored -> true, unit, documents).open("e11-draft1", "clerk-01", 7L, "", "短备注", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗")));
        unit.execute(new CommandCall("e11-seed01", "clerk-01", "SEED_CONFIRM", "DELIVERY_DRAFT",
                sheet.draft().deliveryId(), sheet.draft().deliveryId()), session -> {
            drafts.confirm(new DeliveryDraft(
                    sheet.draft().deliveryId(), sheet.draft().requestId(), 7L, sheet.draft().businessKind(),
                    "", "短备注", DeliveryDraftStatus.CONFIRMED, 2), List.of(new DeliveryConfirmedSource(
                    sheet.draft().deliveryId(), 1, "BAG-B", sheet.draft().deliveryId() + "-1", "STK-E11",
                    new BigDecimal("80"), new BigDecimal("5"))), 1, session);
            return sheet.draft().deliveryId();
        });
        var access = new com.zhubao.erp.scan.app.PublicDeliveryAccessService(drafts, new com.zhubao.erp.scan.infra.InMemoryPublicDeliveryAccessRepository(), unit);
        String token = access.open("label-public", "clerk-01", sheet.draft().deliveryId(), List.of());
        ShippingBagLabelService labels = new ShippingBagLabelService(drafts, access);

        ShippingBagLabelPreview preview = labels.preview(sheet.draft().deliveryId(), 1, token);
        ShippingBagLabelPreview reprint = labels.preview(sheet.draft().deliveryId(), 1, token);

        assertThat(preview.payload().type()).isEqualTo(LabelType.SHIPPING_BAG);
        assertThat(preview.payload().templateCode()).isEqualTo("SHIPPING_BAG_40X30");
        assertThat(preview.payload().primaryCode()).isEqualTo(sheet.draft().deliveryId() + "-1");
        assertThat(preview.payload().primaryCode()).doesNotStartWith("ZD-");
        assertThat(preview.payload().qrContent()).isEqualTo("/d/" + token + "?bag=1");
        assertThat(preview.payload().qrContent()).doesNotStartWith("JG|");
        assertThat(preview.payload().printerSetting().widthMm()).isEqualTo(40);
        assertThat(preview.payload().printerSetting().heightMm()).isEqualTo(30);
        assertThat(preview.payload().fields()).extracting(LabelField::name).contains("数量", "要求");
        assertThat(preview.payload().fields()).extracting(LabelField::value).contains("恒信珠宝", "8mm珠料", "5克").doesNotContain("7", "REQ-1");
        assertThat(access.read(token).customerName()).isEqualTo("恒信珠宝");
        assertThat(access.read(token).bags().get(0).requirement()).isEqualTo("要求".repeat(10));
        assertThat(preview.overflow()).contains("要求放不下，完整内容见A5和扫码页");
        assertThat(reprint.payload().snapshotHash()).isEqualTo(preview.payload().snapshotHash());
        assertThat(drafts.confirmedSources(sheet.draft().deliveryId()).get(0).quantity()).isEqualByComparingTo("80");
        assertThat(drafts.find(sheet.draft().deliveryId()).orElseThrow().status()).isEqualTo(DeliveryDraftStatus.CONFIRMED);
    }

    @Test
    void 旧工单模板仍按原单号生成() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveChildWorkOrder(new ChildWorkOrderRecord(
                "ZD-20261016-0001", "MD-20261016-0008", 8L, "恒信珠宝", "抛光", "2026-10-20",
                "AU9999", "戒托", BigDecimal.ONE, "件", BigDecimal.ONE, "WH01",
                "抛光", "K7M2", "LG-1", "何琳", "2026-10-16T09:00:00"));
        LabelService service = new LabelService(documents, new InMemoryLabelTemplateRepository());
        LabelPayload workOrder = service.generateWorkOrderLabel("ZD-20261016-0001");
        assertThat(workOrder.type()).isEqualTo(LabelType.WORK_ORDER);
        assertThat(workOrder.primaryCode()).isEqualTo("ZD-20261016-0001");
        assertThat(workOrder.qrContent()).startsWith("JG|work_order|ZD-20261016-0001|");
        assertThat(workOrder.printerSetting().widthMm()).isEqualTo(40);
        assertThat(workOrder.printerSetting().heightMm()).isEqualTo(30);
        assertThat(workOrder.templateCode()).isNotEqualTo("SHIPPING_BAG_40X30");
        assertThatThrownBy(() -> service.generate(new GenerateLabelCommand(
                "SHIPPING_BAG", "FH-20261030-0001-1", List.of(), "", "", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("发货袋贴纸只能从已确认交付快照生成");
    }

    private static MotherOrderRecord mother() {
        return new MotherOrderRecord("MD-20261030-1001", 7L, 7L, "牙骨",
                null, null, null, null, null, null, "8mm珠料", "CUSTOMER", true, "WH-01",
                new BigDecimal("80"), "颗", null, null, null, null, null, null,
                null, null, null, null, null, "要求".repeat(10), "2026-11-01", "恒信珠宝");
    }
}
