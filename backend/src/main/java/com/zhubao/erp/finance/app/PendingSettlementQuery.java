package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.app.OrderDeliveryCompletionQuery;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment;
import com.zhubao.erp.finance.domain.OrderSettlement;
import com.zhubao.erp.finance.domain.OrderSettlementRepository;
import com.zhubao.erp.finance.domain.SettlementStatus;
import com.zhubao.erp.finance.domain.UnpricedItem;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 截至所选月末的已交货未结单及历史待核清单，不按进度百分比推断交货。 */
@Service
public class PendingSettlementQuery {
    private final BusinessDocumentRepository documents;
    private final OrderDeliveryCompletionQuery deliveries;
    private final OrderSettlementRepository settlements;
    private final OrderSettlementService settlementService;

    public PendingSettlementQuery(BusinessDocumentRepository documents, OrderDeliveryCompletionQuery deliveries,
            OrderSettlementRepository settlements, OrderSettlementService settlementService) {
        this.documents = documents;
        this.deliveries = deliveries;
        this.settlements = settlements;
        this.settlementService = settlementService;
    }

    public List<UnpricedItem> list(String yearMonth, Long customerId) {
        YearMonth month = FinanceTexts.month(yearMonth);
        List<UnpricedItem> result = new ArrayList<>();
        if (!settlements.policyEnabled()) return List.of();
        var children = documents.listAllChildWorkOrders().stream().collect(Collectors.groupingBy(row -> row.motherOrderCode()));
        var outbound = documents.listOutboundOrders();
        var motherDates = outbound.stream().collect(Collectors.groupingBy(row -> row.motherOrderCode(),
                Collectors.mapping(row -> row.deliveryDate() == null ? "" : row.deliveryDate().trim(), Collectors.toList())));
        var childDates = outbound.stream().collect(Collectors.groupingBy(row -> row.workOrderCode() == null ? "" : row.workOrderCode(),
                Collectors.mapping(row -> row.deliveryDate() == null ? "" : row.deliveryDate().trim(), Collectors.toList())));
        var split = documents.listAllMotherSplitStates().stream().collect(Collectors.toMap(
                MotherSplitState::motherOrderCode, MotherSplitState::decision, (first, last) -> last));
        for (var mother : documents.listMotherOrders()) {
            if (customerId != null && !Objects.equals(customerId, mother.customerId())) continue;
            var family = children.getOrDefault(mother.code(), List.of());
            if (!family.isEmpty()) {
                for (var child : family) add(result, month, OrderDeliveryAssessment.CHILD, child.code(), childDates.getOrDefault(child.code(), List.of()));
            } else if (MotherSplitState.KEEP.equals(split.get(mother.code()))) {
                add(result, month, OrderDeliveryAssessment.MOTHER, mother.code(), motherDates.getOrDefault(mother.code(), List.of()));
            }
        }
        return result.stream().sorted(Comparator.comparing(UnpricedItem::motherOrderCode)
                .thenComparing(UnpricedItem::workOrderCode)).toList();
    }

    private void add(List<UnpricedItem> result, YearMonth month, String type, String code, List<String> dates) {
        OrderDeliveryAssessment assessment = deliveries.assess(type, code);
        if (!assessment.feeTarget() || (!assessment.eligible()
                && !OrderDeliveryAssessment.PENDING_REVIEW.equals(assessment.deliveryState()))) return;
        OrderSettlement latest = settlements.latest(type, code, null).orElse(null);
        if (confirmed(latest)) return;
        // 按真实发货日期界定月末；缺少日期的历史记录明确待核，不能补造月份。
        boolean unknownDate = dates.isEmpty() || dates.stream().anyMatch(date -> !validDate(date));
        if (!unknownDate && dates.stream().map(date -> YearMonth.parse(date.substring(0, 7))).max(Comparator.naturalOrder())
                .orElse(month).isAfter(month)) return;
        String reason;
        if (!assessment.eligible()) {
            reason = "交货待核：" + assessment.blocks().stream().map(OrderDeliveryAssessment.DeliveryBlock::message)
                    .distinct().collect(Collectors.joining("；"));
        } else {
            var review = settlementService.reviewLegacy(assessment);
            reason = !review.blocker().isBlank() ? review.blocker()
                    : review.hasHistory() ? "已交货待结单，含历史账需核对接续" : "已交货待结单";
            if (latest != null) reason += "；草稿待确认";
        }
        if (unknownDate) reason += "；发货日期待核";
        result.add(new UnpricedItem(month.toString(), assessment.customerId(), assessment.customerName(),
                assessment.motherOrderCode(), OrderDeliveryAssessment.CHILD.equals(type) ? code : "", "交货后结单", reason));
    }

    private boolean confirmed(OrderSettlement latest) {
        if (latest == null) return false;
        if (latest.status() == SettlementStatus.CONFIRMED) return true;
        return latest.previousCode() != null && !latest.previousCode().isBlank()
                && settlements.find(latest.rootCode(), null).map(row -> row.status() == SettlementStatus.CONFIRMED).orElse(false);
    }

    private boolean validDate(String value) {
        try { java.time.LocalDate.parse(value); return true; }
        catch (java.time.format.DateTimeParseException exception) { return false; }
    }
}
