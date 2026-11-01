package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.scan.domain.PublicDeliveryAccess;
import com.zhubao.erp.scan.domain.PublicDeliveryAccessRepository;
import com.zhubao.erp.scan.domain.PublicDeliverySummary;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * 用不可枚举标识公开指定交付摘要。查询不写出库。停用不删除交付。
 */
@Service
public class PublicDeliveryAccessService {

    private final com.zhubao.erp.document.domain.DocumentManagementRepository documents;
    private final com.zhubao.erp.document.app.DocumentManagementService content;
    private final DeliveryDraftRepository drafts;
    private final PublicDeliveryAccessRepository accesses;
    private final BusinessWorkUnit workUnit;
    private final SecureRandom random = new SecureRandom();

    public PublicDeliveryAccessService(DeliveryDraftRepository drafts, PublicDeliveryAccessRepository accesses,
                                       BusinessWorkUnit workUnit) {
        this(drafts, accesses, workUnit, null, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public PublicDeliveryAccessService(DeliveryDraftRepository drafts, PublicDeliveryAccessRepository accesses,
            BusinessWorkUnit workUnit, com.zhubao.erp.document.domain.DocumentManagementRepository documents,
            com.zhubao.erp.document.app.DocumentManagementService content) {
        this.documents = documents; this.content = content;
        this.drafts = drafts;
        this.accesses = accesses;
        this.workUnit = workUnit;
    }

    public String open(String requestId, String actorId, String deliveryId, List<String> publicPhotoIds) {
        String delivery = deliveryId == null ? "" : deliveryId.trim();
        List<String> given = publicPhotoIds == null ? List.of() : publicPhotoIds;
        new PublicDeliveryAccess("0".repeat(64), delivery, 1L, List.of(), given, true, 1);
        List<String> photos = given.stream().sorted().distinct().toList();
        String fingerprint = delivery + photos.stream().map(id -> "\n" + id.length() + ":" + id).collect(java.util.stream.Collectors.joining());
        return workUnit.execute(new CommandCall(
                requestId, actorId, "OPEN_PUBLIC_DELIVERY", "DELIVERY_DRAFT", delivery, fingerprint
        ), session -> openIn(delivery, photos, session));
    }

    public void disable(String requestId, String actorId, String accessId) {
        workUnit.execute(new CommandCall(
                requestId, actorId, "DISABLE_PUBLIC_DELIVERY", "PUBLIC_DELIVERY", accessId, accessId
        ), session -> {
            PublicDeliveryAccess current = accesses.find(accessId).orElseThrow(() -> new BusinessException("找不到公开交付"));
            accesses.disable(accessId, current.version(), session);
            return accessId;
        });
    }

    public PublicDeliverySummary read(String accessId) {
        PublicDeliveryAccess access = accesses.find(accessId).orElseThrow(() -> new BusinessException("找不到公开交付"));
        if (!access.enabled()) {
            throw new BusinessException("公开链接已停用");
        }
        return summary(access);
    }

    public PublicDeliverySummary readForDelivery(String accessId, String deliveryId) {
        PublicDeliverySummary summary = read(accessId);
        if (!summary.deliveryId().equals(deliveryId == null ? "" : deliveryId.trim())) {
            throw new BusinessException("公开标识与交付单不一致");
        }
        return summary;
    }

    private String openIn(String deliveryId, List<String> publicPhotoIds, WorkSession session) {
        DeliveryDraft draft = drafts.find(deliveryId).orElseThrow(() -> new BusinessException("交付草稿不存在"));
        if (draft.status() != DeliveryDraftStatus.CONFIRMED) {
            throw new BusinessException("只有已确认交付才能公开");
        }
        List<String> bags = drafts.confirmedSources(deliveryId).stream().map(source -> source.deliveryId() + "-" + source.lineNo()).toList();
        if (bags.isEmpty()) {
            throw new BusinessException("发货袋不存在");
        }
        for (String photo : publicPhotoIds) assertPhoto(deliveryId, photo);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String accessId = HexFormat.of().formatHex(bytes);
        accesses.save(new PublicDeliveryAccess(
                accessId, draft.deliveryId(), draft.customerId(), bags,
                publicPhotoIds == null ? List.of() : publicPhotoIds, true, 1
        ), session);
        return accessId;
    }

    public com.zhubao.erp.document.domain.AttachmentStreamContent photo(String accessId, String photoId) {
        PublicDeliverySummary summary = read(accessId);
        if (!summary.publicPhotoIds().contains(photoId)) throw new BusinessException("图片未获准公开");
        assertPhoto(summary.deliveryId(), photoId);
        if (content == null) throw new BusinessException("公开图片内容尚未装配");
        var stream = content.contentStream(photoId);
        return new com.zhubao.erp.document.domain.AttachmentStreamContent(stream.code(), stream.fileName(), stream.mediaType(), stream.size(), stream.header(),
                (offset, length, output) -> {
                    PublicDeliverySummary current = read(accessId);
                    if (!current.publicPhotoIds().contains(photoId)) throw new BusinessException("图片未获准公开");
                    assertPhoto(current.deliveryId(), photoId);
                    stream.write(offset, length, output);
                });
    }

    private void assertPhoto(String deliveryId, String code) {
        if (documents == null) throw new BusinessException("公开图片必须核验真实交付附件");
        var attachment = documents.findAttachmentByCode(code).orElseThrow(() -> new BusinessException("公开图片不存在"));
        if (!java.util.Set.of("image/jpeg", "image/png").contains(attachment.mediaType())
                || !java.util.Set.of("已上传", "已关联", "已归档").contains(attachment.status()))
            throw new BusinessException("公开图片必须是有效JPEG或PNG照片");
        boolean belongs = com.zhubao.erp.document.domain.EvidenceCodes.OUTBOUND_ORDER.equals(attachment.objectType())
                && drafts.confirmedSources(deliveryId).stream().anyMatch(source -> source.outboundCode().equals(attachment.businessCode()));
        if (!belongs) throw new BusinessException("公开图片不属于本次交付出库");
    }

    private PublicDeliverySummary summary(PublicDeliveryAccess access) {
        DeliveryDraft draft = drafts.find(access.deliveryId()).orElseThrow(() -> new BusinessException("交付不存在"));
        var lines = drafts.lines(access.deliveryId());
        var details = drafts.confirmedSources(access.deliveryId()).stream().map(source -> {
            var line = lines.stream().filter(item -> item.lineNo() == source.lineNo()).findFirst().orElseThrow();
            return new PublicDeliverySummary.Bag(source.deliveryId() + "-" + source.lineNo(), line.materialCode(),
                    line.goodsSpecification(), line.processingSummary(), source.quantity(), line.quantityUnit(), source.weight(), line.targetCode());
        }).toList();
        var photos = access.publicPhotoIds().stream().filter(code -> {
            try { assertPhoto(access.deliveryId(), code); return true; }
            catch (BusinessException ex) { return false; }
        }).toList();
        String name = lines.isEmpty() ? null : lines.get(0).customerName();
        return new PublicDeliverySummary(access.accessId(), access.deliveryId(), access.customerId(), access.bagCodes(), photos, name, details, draft.remark());
    }
}
