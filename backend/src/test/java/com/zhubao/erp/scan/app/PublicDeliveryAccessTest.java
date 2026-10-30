package com.zhubao.erp.scan.app;

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
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.scan.domain.PublicDeliverySummary;
import com.zhubao.erp.scan.infra.InMemoryPublicDeliveryAccessRepository;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicDeliveryAccessTest {

    @Test
    void 公开摘要不含内部账且停用不删除交付() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(new MotherOrderRecord("MD-20261030-1001", 7L, 7L, "牙骨",
                null, null, null, null, null, null, null, "CUSTOMER", true, "WH-01",
                new BigDecimal("80"), "颗", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryDeliveryDraftRepository drafts = new InMemoryDeliveryDraftRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        bags.save(new ActivityBag("BAG-B", "MD-20261030-1001", "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal("80"), StockMeasurement.unknown(), 1), 0, null);
        var sheet = new com.zhubao.erp.business.app.DeliveryDraftService(
                drafts, bags, new com.zhubao.erp.business.app.DocumentProcessingTargetCustomer(documents),
                ignored -> true, unit).open("e13-draft1", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗")));
        unit.execute(new CommandCall("e13-seed01", "clerk-01", "SEED_CONFIRM", "DELIVERY_DRAFT",
                sheet.draft().deliveryId(), sheet.draft().deliveryId()), session -> {
            drafts.confirm(new DeliveryDraft(
                    sheet.draft().deliveryId(), sheet.draft().requestId(), 7L, sheet.draft().businessKind(),
                    "", "", DeliveryDraftStatus.CONFIRMED, 2), List.of(new DeliveryConfirmedSource(
                    sheet.draft().deliveryId(), 1, "BAG-B", sheet.draft().deliveryId() + "-1", "STK-E13",
                    new BigDecimal("80"), new BigDecimal("5"))), 1, session);
            return sheet.draft().deliveryId();
        });
        PublicDeliveryAccessService access = new PublicDeliveryAccessService(
                drafts, new InMemoryPublicDeliveryAccessRepository(), unit);

        assertThatThrownBy(() -> access.open("e13-path01", "clerk-01", sheet.draft().deliveryId(),
                List.of("D:/photos/a.jpg")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("公开图片不能使用内部地址");
        String token = access.open("e13-open01", "clerk-01", sheet.draft().deliveryId(), List.of("PHOTO-1"));
        String replay = access.open("e13-open01", "clerk-01", sheet.draft().deliveryId(), List.of("PHOTO-1"));
        assertThat(replay).isEqualTo(token);
        assertThat(token).hasSize(64);
        assertThat(token).doesNotContain("FH-");
        PublicDeliverySummary summary = access.read(token);
        assertThat(summary.deliveryId()).isEqualTo(sheet.draft().deliveryId());
        assertThat(summary.customerId()).isEqualTo(7L);
        assertThat(summary.bagCodes()).containsExactly(sheet.draft().deliveryId() + "-1");
        assertThat(summary.publicPhotoIds()).containsExactly("PHOTO-1");
        assertThatThrownBy(() -> access.readForDelivery(token, "FH-20261030-9999"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("公开标识与交付单不一致");
        assertThatThrownBy(() -> access.read("ab"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("找不到公开交付");

        access.disable("e13-stop01", "clerk-01", token);
        assertThatThrownBy(() -> access.read(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("公开链接已停用");
        assertThat(drafts.find(sheet.draft().deliveryId()).orElseThrow().status()).isEqualTo(DeliveryDraftStatus.CONFIRMED);
        assertThat(drafts.confirmedSources(sheet.draft().deliveryId()).get(0).quantity()).isEqualByComparingTo("80");
    }
}
