package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryCorrectionIntent;
import com.zhubao.erp.business.domain.DeliveryDownstream;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliveryImpact;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 交付误录的影响查询和作废。已确认交付只追加留痕边界，不覆盖原数量，不删除费用或退款。
 * 交接规则未核定，不能按未交接把出库冲回，也不能取消后重开一张来抹平历史。
 */
@Service
public class DeliveryCorrectionService {

    private final DeliveryDraftRepository drafts;
    private final DeliveryDownstream downstream;
    private final BusinessWorkUnit workUnit;

    public DeliveryCorrectionService(DeliveryDraftRepository drafts, DeliveryDownstream downstream,
                                     BusinessWorkUnit workUnit) {
        this.drafts = drafts;
        this.downstream = downstream;
        this.workUnit = workUnit;
    }

    public DeliveryImpact impact(String deliveryId) {
        DeliveryDraft draft = drafts.find(required(deliveryId))
                .orElseThrow(() -> new BusinessException("交付草稿不存在"));
        List<DeliveryImpact.SourceImpact> sources = new ArrayList<>();
        if (draft.status() == DeliveryDraftStatus.CONFIRMED) {
            for (DeliveryConfirmedSource source : drafts.confirmedSources(draft.deliveryId())) {
                sources.add(new DeliveryImpact.SourceImpact(
                        source.outboundCode(), source.quantity(), consumed(source.outboundCode())));
            }
        } else {
            for (DeliveryDraftLine line : drafts.lines(draft.deliveryId())) {
                sources.add(new DeliveryImpact.SourceImpact(line.bagId(), line.quantity(), BigDecimal.ZERO));
            }
        }
        return new DeliveryImpact(draft.deliveryId(), draft.status(), DeliveryImpact.HANDOVER_UNDECIDED,
                List.copyOf(sources), downstream.hasConfirmedCharge(draft.deliveryId()),
                downstream.hasConfirmedRefund(draft.deliveryId()));
    }

    public DeliveryDraft apply(String requestId, String actorId, String deliveryId, int expectedVersion,
                               DeliveryCorrectionIntent intent) {
        if (intent == null) {
            throw new BusinessException("交付更正方式不能为空");
        }
        if (intent == DeliveryCorrectionIntent.ABANDON_DRAFT) {
            String id = workUnit.execute(new CommandCall(
                    requestId, actorId, "ABANDON_DELIVERY_DRAFT", "DELIVERY_DRAFT", required(deliveryId),
                    required(deliveryId) + "|" + expectedVersion
            ), session -> abandon(required(deliveryId), expectedVersion, session));
            return drafts.find(id).orElseThrow(() -> new IllegalStateException("交付草稿作废后无法读取"));
        }
        DeliveryDraft draft = drafts.find(required(deliveryId))
                .orElseThrow(() -> new BusinessException("交付草稿不存在"));
        if (draft.status() != DeliveryDraftStatus.CONFIRMED) {
            throw new BusinessException("只有已确认交付才能更正");
        }
        if (draft.version() != expectedVersion) {
            throw new BusinessException("交付草稿已被其他人修改，请刷新后重试");
        }
        boolean consumed = drafts.confirmedSources(draft.deliveryId()).stream()
                .anyMatch(source -> consumed(source.outboundCode()).signum() > 0);
        boolean charge = downstream.hasConfirmedCharge(draft.deliveryId());
        boolean refund = downstream.hasConfirmedRefund(draft.deliveryId());
        if (intent == DeliveryCorrectionIntent.OVERWRITE_QUANTITY) {
            throw new BusinessException(consumed ? "不能直接覆盖已消费数量" : "不能直接覆盖已确认交付数量");
        }
        if (intent == DeliveryCorrectionIntent.CANCEL_AND_REOPEN) {
            throw new BusinessException(charge || refund ? "不能删除已确认费用或退款" : "不能以取消重开抹平历史");
        }
        if (consumed) {
            throw new BusinessException("不能直接覆盖已消费数量");
        }
        throw new BusinessException("交接与取消规则尚未核定，不能按未交接直接取消");
    }

    private String abandon(String deliveryId, int expectedVersion, WorkSession session) {
        DeliveryDraft draft = drafts.lock(deliveryId, session).orElseThrow(() -> new BusinessException("交付草稿不存在"));
        if (draft.version() != expectedVersion) {
            throw new BusinessException("交付草稿已被其他人修改，请刷新后重试");
        }
        if (draft.status() != DeliveryDraftStatus.DRAFT) {
            throw new BusinessException("已确认交付不能当作草稿作废");
        }
        DeliveryDraft abandoned = new DeliveryDraft(
                draft.deliveryId(), draft.requestId(), draft.customerId(), draft.businessKind(),
                draft.logisticsNote(), draft.remark(), DeliveryDraftStatus.ABANDONED, draft.version() + 1);
        drafts.abandon(abandoned, expectedVersion, session);
        return draft.deliveryId();
    }

    private BigDecimal consumed(String sourceCode) {
        BigDecimal quantity = downstream.consumedQuantity(sourceCode);
        if (quantity == null || quantity.signum() < 0) {
            throw new BusinessException("已消费数量无效");
        }
        return quantity;
    }

    private static String required(String deliveryId) {
        String id = deliveryId == null ? "" : deliveryId.trim();
        if (id.isEmpty()) {
            throw new BusinessException("交付草稿不存在");
        }
        return id;
    }
}
