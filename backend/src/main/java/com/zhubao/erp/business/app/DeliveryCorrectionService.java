package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryCorrectionIntent;
import com.zhubao.erp.business.domain.DeliveryDownstream;
import com.zhubao.erp.business.domain.DownstreamFact;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliveryImpact;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 交付误录的影响查询和作废。已确认交付只追加留痕边界，不覆盖原数量，不删除费用或退款。
 * 未交接且尚未出账时可以取消出库。原出库记录保留，已出客户账单或已消费的数量不能在这里抹掉。
 */
@Service
public class DeliveryCorrectionService {

    private final DeliveryDraftRepository drafts;
    private final DeliveryDownstream downstream;
    private final BusinessWorkUnit workUnit;
    private final StockLedgerService stock;
    private final BusinessDocumentRepository documents;

    public DeliveryCorrectionService(DeliveryDraftRepository drafts, DeliveryDownstream downstream,
                                     BusinessWorkUnit workUnit) {
        this(drafts, downstream, workUnit, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DeliveryCorrectionService(DeliveryDraftRepository drafts, DeliveryDownstream downstream,
                                     BusinessWorkUnit workUnit, StockLedgerService stock,
                                     BusinessDocumentRepository documents) {
        this.drafts = drafts;
        this.downstream = downstream;
        this.workUnit = workUnit;
        this.stock = stock;
        this.documents = documents;
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
        boolean consumed = sources.stream().anyMatch(source -> source.consumedQuantity().signum() > 0);
        boolean charge = downstream.chargeFact(draft.deliveryId()) == DownstreamFact.CONFIRMED;
        boolean refund = downstream.refundFact(draft.deliveryId()) == DownstreamFact.CONFIRMED;
        String handover = draft.status() == DeliveryDraftStatus.CONFIRMED && !consumed && !charge && !refund
                ? "未交接" : DeliveryImpact.HANDOVER_UNDECIDED;
        return new DeliveryImpact(draft.deliveryId(), draft.status(), handover,
                List.copyOf(sources), downstream.chargeFact(draft.deliveryId()).label(),
                downstream.refundFact(draft.deliveryId()).label());
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
        if (intent == DeliveryCorrectionIntent.REVERSE_BEFORE_HANDOVER) {
            String id = workUnit.execute(new CommandCall(
                    requestId, actorId, "REVERSE_DELIVERY_BEFORE_HANDOVER", "DELIVERY_DRAFT", required(deliveryId),
                    required(deliveryId) + "|" + expectedVersion
            ), session -> reverse(required(deliveryId), expectedVersion, session));
            return drafts.find(id).orElseThrow(() -> new IllegalStateException("取消出库后无法读取交付"));
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
        boolean charge = downstream.chargeFact(draft.deliveryId()) == DownstreamFact.CONFIRMED;
        boolean refund = downstream.refundFact(draft.deliveryId()) == DownstreamFact.CONFIRMED;
        if (intent == DeliveryCorrectionIntent.OVERWRITE_QUANTITY) {
            throw new BusinessException(consumed ? "不能直接覆盖已消费数量" : "不能直接覆盖已确认交付数量");
        }
        if (intent == DeliveryCorrectionIntent.CANCEL_AND_REOPEN) {
            throw new BusinessException(charge || refund ? "不能删除已确认费用或退款" : "不能以取消重开抹平历史");
        }
        throw new BusinessException("不能以取消重开抹平历史");
    }

    private String reverse(String deliveryId, int expectedVersion, WorkSession session) {
        DeliveryDraft current = drafts.lock(deliveryId, session).orElseThrow(() -> new BusinessException("交付草稿不存在"));
        if (current.version() != expectedVersion || current.status() != DeliveryDraftStatus.CONFIRMED) {
            throw new BusinessException("交付草稿已被其他人修改，请刷新后重试");
        }
        boolean consumed = drafts.confirmedSources(current.deliveryId()).stream()
                .anyMatch(source -> consumed(source.outboundCode()).signum() > 0);
        boolean charge = downstream.chargeFact(current.deliveryId()) == DownstreamFact.CONFIRMED;
        boolean refund = downstream.refundFact(current.deliveryId()) == DownstreamFact.CONFIRMED;
        if (consumed) {
            throw new BusinessException("不能直接覆盖已消费数量");
        }
        if (charge || refund) {
            throw new BusinessException("已有客户账单或退款，不能只取消出库");
        }
        if (stock != null && documents != null) {
            for (DeliveryConfirmedSource source : drafts.confirmedSources(current.deliveryId())) {
                var outbound = documents.findOutboundOrderByCode(source.outboundCode()).orElse(null);
                if (outbound == null) {
                    continue;
                }
                stock.transfer(new StockTransferCommand(
                        "outbound_cancel", source.outboundCode(), dimension(outbound, StockStatus.SHIPPED),
                        dimension(outbound, StockStatus.FINISHED), source.quantity(), outbound.weight(),
                        "未交接取消出库，原出库记录保留"), session);
            }
        }
        drafts.reverse(new DeliveryDraft(
                current.deliveryId(), current.requestId(), current.customerId(), current.businessKind(),
                current.logisticsNote(), current.remark(), DeliveryDraftStatus.REVERSED, current.version() + 1
        ), expectedVersion, session);
        return current.deliveryId();
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

    private static StockDimension dimension(com.zhubao.erp.business.domain.OutboundOrderRecord outbound, StockStatus status) {
        return new StockDimension(
                com.zhubao.erp.inventory.domain.OwnerType.CUSTOMER, outbound.ownerCustomerId(), outbound.customerId(),
                outbound.motherOrderCode(), outbound.workOrderCode(), outbound.materialCode(),
                outbound.fromWarehouseCode(), status);
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
