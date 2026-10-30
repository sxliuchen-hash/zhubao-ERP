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

    private final DeliveryDraftRepository drafts;
    private final PublicDeliveryAccessRepository accesses;
    private final BusinessWorkUnit workUnit;
    private final SecureRandom random = new SecureRandom();

    public PublicDeliveryAccessService(DeliveryDraftRepository drafts, PublicDeliveryAccessRepository accesses,
                                       BusinessWorkUnit workUnit) {
        this.drafts = drafts;
        this.accesses = accesses;
        this.workUnit = workUnit;
    }

    public String open(String requestId, String actorId, String deliveryId, List<String> publicPhotoIds) {
        String delivery = deliveryId == null ? "" : deliveryId.trim();
        return workUnit.execute(new CommandCall(
                requestId, actorId, "OPEN_PUBLIC_DELIVERY", "DELIVERY_DRAFT", delivery, delivery
        ), session -> openIn(delivery, publicPhotoIds, session));
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
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String accessId = HexFormat.of().formatHex(bytes);
        accesses.save(new PublicDeliveryAccess(
                accessId, draft.deliveryId(), draft.customerId(), bags,
                publicPhotoIds == null ? List.of() : publicPhotoIds, true, 1
        ), session);
        return accessId;
    }

    private PublicDeliverySummary summary(PublicDeliveryAccess access) {
        return new PublicDeliverySummary(
                access.accessId(), access.deliveryId(), access.customerId(), access.bagCodes(), access.publicPhotoIds());
    }
}
