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
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path files;

    @Test
    void 公开摘要不含内部账且停用不删除交付() throws Exception {
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
        var settings = new com.zhubao.erp.document.infra.AttachmentStorageProperties(); settings.setLocalRoot(files.toString());
        var storage = new com.zhubao.erp.document.infra.LocalAttachmentObjectStorage(settings);
        var image = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",image);
        var stored = storage.put("DOC-1","PHOTO-1","a.png","image/png",image.toByteArray());
        var photos = new com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository();
        photos.saveAttachment(new com.zhubao.erp.document.domain.EvidenceAttachment("PHOTO-1", "DOC-1", "出库单", sheet.draft().deliveryId()+"-1", "a.png", "image/png", stored.storageLocation(), "hot", "PWA", "已上传", "clerk", "hash", "2026-11-01", "", "green", "OUTBOUND_ORDER"));
        PublicDeliveryAccessService access = new PublicDeliveryAccessService(
                drafts, new InMemoryPublicDeliveryAccessRepository(), unit, photos, new com.zhubao.erp.document.app.DocumentManagementService(photos,storage));
        assertThatThrownBy(() -> access.open("missing-photo", "clerk-01", sheet.draft().deliveryId(), List.of("ATT-DOES-NOT-EXIST"))).hasMessage("公开图片不存在");

        assertThatThrownBy(() -> access.open("e13-path01", "clerk-01", sheet.draft().deliveryId(),
                List.of("D:/photos/a.jpg")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("公开图片不能使用内部地址");
        String token = access.open("e13-open01", "clerk-01", sheet.draft().deliveryId(), List.of("PHOTO-1"));
        String replay = access.open("e13-open01", "clerk-01", sheet.draft().deliveryId(), List.of("PHOTO-1"));
        assertThat(replay).isEqualTo(token);
        assertThatThrownBy(() -> access.open("e13-open01", "clerk-01", sheet.draft().deliveryId(), List.of())).isInstanceOf(BusinessException.class);
        photos.saveAttachment(new com.zhubao.erp.document.domain.EvidenceAttachment("WRONG", "DOC-2", "出库单", "FH-20261101-9999-1", "a.png", "image/png", "local://photo", "hot", "PWA", "已上传", "clerk", "hash", "2026-11-01", "", "green", "OUTBOUND_ORDER"));
        assertThatThrownBy(() -> access.open("wrong-photo", "clerk-01", sheet.draft().deliveryId(), List.of("WRONG"))).hasMessage("公开图片不属于本次交付出库");
        var controller = new com.zhubao.erp.scan.web.PublicDeliveryController(access);
        var response = controller.photo(token,"PHOTO-1");
        var output = new java.io.ByteArrayOutputStream(); response.getBody().writeTo(output);
        assertThat(output.toByteArray()).isEqualTo(image.toByteArray());
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        var pending = access.photo(token,"PHOTO-1");
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

        photos.saveAttachment(photos.findAttachmentByCode("PHOTO-1").orElseThrow().withStatus("已下线", "gray"));
        assertThatThrownBy(() -> pending.write(0,pending.size(),new java.io.ByteArrayOutputStream())).isInstanceOf(BusinessException.class);
        assertThat(access.read(token).publicPhotoIds()).isEmpty();
        assertThatThrownBy(() -> access.photo(token, "PHOTO-1")).hasMessage("图片未获准公开");
        access.disable("e13-stop01", "clerk-01", token);
        assertThatThrownBy(() -> access.read(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("公开链接已停用");
        assertThat(drafts.find(sheet.draft().deliveryId()).orElseThrow().status()).isEqualTo(DeliveryDraftStatus.CONFIRMED);
        assertThat(drafts.confirmedSources(sheet.draft().deliveryId()).get(0).quantity()).isEqualByComparingTo("80");
    }
}
