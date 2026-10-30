package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.DeliveryConfirmation;
import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliveryInHouseCheck;
import com.zhubao.erp.business.domain.DeliveryStockPlace;
import com.zhubao.erp.business.domain.DeliveryStockPlaceLookup;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 一次确认整张交付草稿。每一袋单独出库，汇总单只保存这些出库事实，不再扣一次库存。
 * 已有出库单只作来源阅读。任一明细失败时，本次库存、出库单和确认一起回滚。
 */
@Service
public class DeliveryConfirmationService {

    private static final DateTimeFormatter RECORDED_AT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DeliveryDraftRepository drafts;
    private final ActivityBagRepository bags;
    private final DeliveryStockPlaceLookup places;
    private final DeliveryInHouseCheck inHouse;
    private final StockLedgerService stock;
    private final BusinessDocumentRepository documents;
    private final BusinessWorkUnit workUnit;

    public DeliveryConfirmationService(DeliveryDraftRepository drafts, ActivityBagRepository bags,
                                       DeliveryStockPlaceLookup places, DeliveryInHouseCheck inHouse,
                                       StockLedgerService stock, BusinessDocumentRepository documents,
                                       BusinessWorkUnit workUnit) {
        this.drafts = drafts;
        this.bags = bags;
        this.places = places;
        this.inHouse = inHouse;
        this.stock = stock;
        this.documents = documents;
        this.workUnit = workUnit;
    }

    public DeliveryConfirmation confirm(String requestId, String actorId, String deliveryId, int expectedVersion) {
        String actor = actorId == null ? "" : actorId.trim();
        String id = workUnit.execute(new CommandCall(
                requestId, actor, "CONFIRM_DELIVERY", "DELIVERY_DRAFT",
                deliveryId == null ? "" : deliveryId.trim(),
                (deliveryId == null ? "" : deliveryId.trim()) + "|" + expectedVersion
        ), session -> confirmIn(actor, deliveryId, expectedVersion, session));
        return load(id);
    }

    private String confirmIn(String actor, String deliveryId, int expectedVersion, WorkSession session) {
        String id = deliveryId == null ? "" : deliveryId.trim();
        DeliveryDraft draft = drafts.lock(id, session).orElseThrow(() -> new BusinessException("交付草稿不存在"));
        if (draft.version() != expectedVersion) {
            throw new BusinessException("交付草稿已被其他人修改，请刷新后重试");
        }
        if (draft.status() != DeliveryDraftStatus.DRAFT) {
            throw new BusinessException("该交付单已经确认");
        }
        if (draft.businessKind() != BusinessKind.PROCESSING) {
            throw new BusinessException("打样和金工的交付确认尚未具备各自的库存来源，不能改记成普通工单");
        }
        List<DeliveryDraftLine> lines = drafts.lines(draft.deliveryId());
        if (lines.isEmpty()) {
            throw new BusinessException("交付草稿至少选择一袋");
        }
        List<DeliveryDraftLine> lockOrder = new ArrayList<>(lines);
        lockOrder.sort(Comparator.comparing(DeliveryDraftLine::bagId));
        Resolved[] resolved = new Resolved[lines.size()];
        for (DeliveryDraftLine line : lockOrder) {
            resolved[line.lineNo() - 1] = resolve(draft, line, session);
        }
        List<DeliveryConfirmedSource> sources = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            sources.add(ship(actor, draft, lines.get(index), resolved[index], session));
        }
        DeliveryDraft confirmed = new DeliveryDraft(
                draft.deliveryId(), draft.requestId(), draft.customerId(), draft.businessKind(),
                draft.logisticsNote(), draft.remark(), DeliveryDraftStatus.CONFIRMED, draft.version() + 1);
        drafts.confirm(confirmed, sources, expectedVersion, session);
        return draft.deliveryId();
    }

    private Resolved resolve(DeliveryDraft draft, DeliveryDraftLine line, WorkSession session) {
        ActivityBag bag = bags.lock(line.bagId(), session).orElseThrow(() -> new BusinessException("袋不存在"));
        if (bag.version() != line.bagVersion()) {
            throw new BusinessException("袋已被其他人修改，请刷新后重试");
        }
        if (!bag.activeLeaf()) {
            throw new BusinessException("已关闭的袋不能进入交付草稿");
        }
        if (!bag.targetCode().equals(line.targetCode())) {
            throw new BusinessException("所选袋不属于该业务目标");
        }
        if (!inHouse.inHouse(bag.id())) {
            throw new BusinessException("外协未回的份额不能当厂内可发");
        }
        if (bag.quantity().compareTo(line.quantity()) != 0) {
            throw new BusinessException("袋上的数量已变化，请重新做草稿");
        }
        if (bag.weight().state() != com.zhubao.erp.inventory.domain.StockMeasurement.WeightState.KNOWN
                || bag.weight().value() == null || bag.weight().value().signum() <= 0
                || line.weight().value() == null || line.weight().value().compareTo(bag.weight().value()) != 0) {
            throw new BusinessException("需要本份额的实际重量后才能转移或发货");
        }
        if (drafts.bagConfirmed(bag.id())) {
            throw new BusinessException("该袋已经交付");
        }
        BusinessTargetReference reference = BusinessTargetReference.fromCode(line.targetCode());
        DeliveryStockPlace place = places.place(reference).orElseThrow(() -> new BusinessException("加工目标不存在"));
        if (place.customerId() != draft.customerId()) {
            throw new BusinessException("不能把不同客户放在同一张交付单");
        }
        if (place.materialCode() == null || !place.materialCode().equals(bag.materialCode())) {
            throw new BusinessException("所选袋与加工目标材质不一致");
        }
        return new Resolved(bag, place);
    }

    private DeliveryConfirmedSource ship(String actor, DeliveryDraft draft, DeliveryDraftLine line, Resolved resolved,
                                         WorkSession session) {
        DeliveryStockPlace place = resolved.place();
        String outboundCode = draft.deliveryId() + "-" + line.lineNo();
        boolean historical = documents.listOutboundOrders().stream()
                .filter(row -> place.motherOrderCode().equals(row.motherOrderCode()))
                .filter(row -> place.workOrderCode().equals(row.workOrderCode()))
                .anyMatch(row -> outboundCode.equals(row.code()));
        if (historical) {
            throw new BusinessException("出库单号无效");
        }
        StockDimension from = dimension(place, StockStatus.FINISHED);
        StockDimension to = dimension(place, StockStatus.SHIPPED);
        StockLedgerEntry entry = stock.transfer(new StockTransferCommand(
                "outbound_shipment", outboundCode, from, to, line.quantity(), resolved.bag().weight().value(),
                "交付 " + draft.deliveryId() + " 袋序 " + line.lineNo()
        ), session);
        documents.saveOutboundOrder(outbound(actor, draft, line, place, outboundCode, entry), session);
        return new DeliveryConfirmedSource(draft.deliveryId(), line.lineNo(), line.bagId(), outboundCode,
                entry.code(), line.quantity(), resolved.bag().weight().value());
    }

    private OutboundOrderRecord outbound(String actor, DeliveryDraft draft, DeliveryDraftLine line,
                                         DeliveryStockPlace place, String outboundCode, StockLedgerEntry entry) {
        String logistics = draft.logisticsNote() == null ? "" : draft.logisticsNote();
        String expressNo = logistics.length() <= 80 ? logistics : "";
        return new OutboundOrderRecord(
                outboundCode, place.motherOrderCode(), place.workOrderCode(), place.customerId(),
                place.ownerCustomerId(), place.materialCode(), place.warehouseCode(), place.warehouseCode(),
                LocalDate.now().toString(), "交付", entry.quantity(), line.quantityUnit(), entry.weight(),
                "", expressNo, logistics, "", "DELIVERY", line.lineNo(), "", actor, "",
                draft.remark(), "SHIPPED", entry.code(), RECORDED_AT.format(LocalDateTime.now()));
    }

    private StockDimension dimension(DeliveryStockPlace place, StockStatus status) {
        return new StockDimension(place.ownerType(), place.ownerCustomerId(), place.customerId(),
                place.motherOrderCode(), place.workOrderCode(), place.materialCode(), place.warehouseCode(), status);
    }

    private DeliveryConfirmation load(String deliveryId) {
        DeliveryDraft draft = drafts.find(deliveryId)
                .orElseThrow(() -> new IllegalStateException("交付确认保存后无法读取"));
        return new DeliveryConfirmation(draft, drafts.lines(deliveryId), drafts.confirmedSources(deliveryId));
    }

    private record Resolved(ActivityBag bag, DeliveryStockPlace place) {
    }
}
