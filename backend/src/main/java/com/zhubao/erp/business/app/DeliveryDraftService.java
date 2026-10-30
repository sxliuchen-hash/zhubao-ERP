package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftSheet;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliveryInHouseCheck;
import com.zhubao.erp.business.domain.DeliverySourceSelection;
import com.zhubao.erp.business.domain.OrderCodes;
import com.zhubao.erp.business.domain.ProcessingTargetCustomer;
import com.zhubao.erp.business.domain.ProcessingTargetFacts;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 建立独立交付草稿。同一客户的多张普通加工目标可以放在一张草稿里。
 * 草稿不扣库存、不改袋、不写出库。跨种类混单未确认前不放开。
 * 打样只保存打样单号，金工精度未核定前不保存数量，两者都不写成普通工单。
 */
@Service
public class DeliveryDraftService {

    private final DeliveryDraftRepository drafts;
    private final ActivityBagRepository bags;
    private final ProcessingTargetCustomer customers;
    private final DeliveryInHouseCheck inHouse;
    private final BusinessWorkUnit workUnit;

    public DeliveryDraftService(DeliveryDraftRepository drafts, ActivityBagRepository bags,
                                ProcessingTargetCustomer customers, DeliveryInHouseCheck inHouse,
                                BusinessWorkUnit workUnit) {
        this.drafts = drafts;
        this.bags = bags;
        this.customers = customers;
        this.inHouse = inHouse;
        this.workUnit = workUnit;
    }

    public DeliveryDraftSheet open(String requestId, String actorId, long customerId, String logisticsNote,
                                   String remark, List<DeliverySourceSelection> selections) {
        List<DeliverySourceSelection> lines = selections == null ? List.of() : List.copyOf(selections);
        String payload = payload(logisticsNote, remark, lines);
        String deliveryId = workUnit.execute(new CommandCall(
                requestId, actorId, "CREATE_DELIVERY_DRAFT", "DELIVERY_DRAFT", Long.toString(customerId), payload
        ), session -> create(requestId, customerId, logisticsNote, remark, lines, session));
        return load(deliveryId);
    }

    private String create(String requestId, long customerId, String logisticsNote, String remark,
                          List<DeliverySourceSelection> selections, WorkSession session) {
        if (selections.isEmpty()) {
            throw new BusinessException("交付草稿至少选择一袋");
        }
        List<BusinessTargetReference> references = new ArrayList<>();
        Set<String> bagIds = new HashSet<>();
        for (DeliverySourceSelection selection : selections) {
            String bagId = selection.bagId() == null ? "" : selection.bagId().trim();
            if (!bagIds.add(bagId)) {
                throw new BusinessException("同一袋不能在一张交付草稿里重复选择");
            }
            references.add(BusinessTargetReference.fromCode(selection.targetCode()));
        }
        BusinessKind kind = references.get(0).kind();
        for (BusinessTargetReference reference : references) {
            if (reference.kind() != kind) {
                throw new BusinessException("跨种类混单尚未确认，不能放在同一张交付单");
            }
        }
        StockMeasurement.rejectUndecidedGoldScale(kind);
        List<Integer> lockOrder = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            lockOrder.add(index);
        }
        lockOrder.sort(Comparator.comparing(index -> selections.get(index).bagId() == null
                ? "" : selections.get(index).bagId().trim()));
        ActivityBag[] locked = new ActivityBag[selections.size()];
        for (int index : lockOrder) {
            DeliverySourceSelection selection = selections.get(index);
            String bagId = selection.bagId() == null ? "" : selection.bagId().trim();
            ActivityBag bag = bags.lock(bagId, session).orElseThrow(() -> new BusinessException("袋不存在"));
            if (bag.version() != selection.bagVersion()) {
                throw new BusinessException("袋已被其他人修改，请刷新后重试");
            }
            if (!bag.activeLeaf()) {
                throw new BusinessException("已关闭的袋不能进入交付草稿");
            }
            if (!bag.targetCode().equals(references.get(index).code())) {
                throw new BusinessException("所选袋不属于该业务目标");
            }
            if (!inHouse.inHouse(bag.id())) {
                throw new BusinessException("外协未回的份额不能当厂内可发");
            }
            locked[index] = bag;
        }
        String deliveryId = OrderCodes.next("FH", LocalDate.now(), drafts.listIds(session),
                code -> drafts.listIds(session).contains(code));
        List<DeliveryDraftLine> draftLines = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            BusinessTargetReference reference = references.get(index);
            ActivityBag bag = locked[index];
            String unit = unitOf(kind, reference, selections.get(index), customerId);
            draftLines.add(new DeliveryDraftLine(
                    deliveryId, index + 1, bag.id(), bag.version(), reference.kind(), reference.role(),
                    reference.code(), bag.quantity(), unit, bag.weight(), bag.materialCode(),
                    bag.sourceKey(), bag.requirementVersion()));
        }
        DeliveryDraft draft = new DeliveryDraft(
                deliveryId, requestId, customerId, kind, logisticsNote, remark, DeliveryDraftStatus.DRAFT, 1);
        drafts.save(draft, draftLines, session);
        return deliveryId;
    }

    private String unitOf(BusinessKind kind, BusinessTargetReference reference, DeliverySourceSelection selection,
                          long customerId) {
        if (kind == BusinessKind.PROCESSING) {
            ProcessingTargetFacts facts = customers.facts(reference)
                    .orElseThrow(() -> new BusinessException("加工目标不存在"));
            if (facts.customerId() == null) {
                throw new BusinessException("加工目标缺少客户");
            }
            if (facts.customerId().longValue() != customerId) {
                throw new BusinessException("不能把不同客户放在同一张交付单");
            }
            return processingUnit(facts, selection.quantityUnit());
        }
        return requiredUnit(selection.quantityUnit(), "打样交付需要明确数量单位");
    }

    private String processingUnit(ProcessingTargetFacts facts, String given) {
        String orderUnit = facts.quantityUnit() == null ? "" : facts.quantityUnit().trim();
        if (orderUnit.isEmpty()) {
            throw new BusinessException("加工目标缺少数量单位");
        }
        if (orderUnit.length() > 16) {
            throw new BusinessException("数量单位过长");
        }
        String supplied = given == null ? "" : given.trim();
        if (!supplied.isEmpty() && !supplied.equals(orderUnit)) {
            throw new BusinessException("数量单位与加工目标不一致");
        }
        return orderUnit;
    }

    private String requiredUnit(String given, String missing) {
        String unit = given == null ? "" : given.trim();
        if (unit.isEmpty()) {
            throw new BusinessException(missing);
        }
        if (unit.length() > 16) {
            throw new BusinessException("数量单位过长");
        }
        return unit;
    }

    private DeliveryDraftSheet load(String deliveryId) {
        DeliveryDraft draft = drafts.find(deliveryId)
                .orElseThrow(() -> new IllegalStateException("交付草稿保存后无法读取"));
        return new DeliveryDraftSheet(draft, drafts.lines(deliveryId));
    }

    private String payload(String logisticsNote, String remark, List<DeliverySourceSelection> lines) {
        StringBuilder text = new StringBuilder();
        text.append(logisticsNote == null ? "" : logisticsNote.trim()).append('\n');
        text.append(remark == null ? "" : remark.trim());
        for (DeliverySourceSelection line : lines) {
            text.append('\n')
                    .append(line.bagId() == null ? "" : line.bagId().trim())
                    .append('|').append(line.bagVersion())
                    .append('|').append(line.targetCode() == null ? "" : line.targetCode().trim())
                    .append('|').append(line.quantityUnit() == null ? "" : line.quantityUnit().trim());
        }
        return text.toString();
    }
}
