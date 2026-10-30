package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialReturn;
import com.zhubao.erp.inventory.domain.ConsumableCostEvent;
import com.zhubao.erp.inventory.domain.ConsumableCostRepository;
import com.zhubao.erp.inventory.domain.ConsumptionTiming;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.FactoryItemRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

/**
 * 耗材按已选定的计耗时点领退。同一次数量不重复计费用，采购付款也不再计成本。
 */
@Service
public class ConsumableIssueService {

    private final FactoryItemRepository items;
    private final CompanyMaterialReceiptRepository stocks;
    private final CompanyMaterialIssueRepository issues;
    private final ConsumableCostRepository costs;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public ConsumableIssueService(FactoryItemRepository items, CompanyMaterialReceiptRepository stocks,
                                  CompanyMaterialIssueRepository issues, ConsumableCostRepository costs,
                                  BusinessWorkUnit unit, PermissionGuard permissions) {
        this.items = items;
        this.stocks = stocks;
        this.issues = issues;
        this.costs = costs;
        this.unit = unit;
        this.permissions = permissions;
    }

    public CompanyMaterialIssue issue(String requestId, OperatorContext actor, String itemId, String receiptId,
                                      String workshopCode, BigDecimal quantity) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_ISSUE, actor);
        BigDecimal counted = positive(quantity);
        FactoryItem item = consumable(itemId);
        if (item.consumptionTiming() == ConsumptionTiming.UNDECIDED) {
            throw new BusinessException("耗材计耗方式尚未选定");
        }
        CompanyMaterialReceipt receipt = stocks.find(text(receiptId)).orElseThrow(() -> new BusinessException("公司料入库不存在"));
        if (!receipt.itemId().equals(item.itemId())) {
            throw new BusinessException("领用批次与物品不一致");
        }
        String workshop = required(workshopCode, "请填写车间", 40);
        String issueId = unit.execute(new CommandCall(
                requestId, actor.operator(), "ISSUE_CONSUMABLE", "CONSUMABLE_ISSUE", item.itemId(),
                item.itemId() + "\n" + receipt.receiptId() + "\n" + workshop + "\n" + counted.toPlainString()
                        + "\n" + item.consumptionTiming().name()
        ), session -> {
            stocks.take(item.itemId(), receipt.batchCode(), receipt.warehouseCode(), counted, item.unit(), StockMeasurement.unknown(), session);
            if (item.consumptionTiming() == ConsumptionTiming.ON_ACTUAL_USE) {
                stocks.add(new CompanyMaterialBalance(
                        item.itemId(), receipt.batchCode(), workshop, counted, item.unit(), StockMeasurement.unknown()), session);
            }
            String id = "LY-" + token();
            issues.insert(new CompanyMaterialIssue(
                    id, item.itemId(), receipt.batchCode(), receipt.warehouseCode(), workshop, counted,
                    BigDecimal.ZERO, BigDecimal.ZERO, item.unit(), StockMeasurement.unknown(), "", 1), session);
            boolean posted = item.consumptionTiming() == ConsumptionTiming.ON_ISSUE;
            costs.saveRule(id, item.consumptionTiming(), posted, receipt.receiptId(), session);
            if (posted) {
                costs.insert(costEvent(id, ConsumableCostEvent.COST, counted, portion(receipt, counted)), session);
            }
            return id;
        });
        return issues.find(issueId).orElseThrow(() -> new BusinessException("领用单不存在"));
    }

    public CompanyMaterialReturn returnIssued(String requestId, OperatorContext actor, String issueId, int version, BigDecimal quantity) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RETURN, actor);
        BigDecimal counted = positive(quantity);
        String id = text(issueId);
        String returnId = unit.execute(new CommandCall(
                requestId, actor.operator(), "RETURN_CONSUMABLE", "CONSUMABLE_ISSUE", id,
                id + "\n" + version + "\n" + counted.toPlainString()
        ), session -> {
            CompanyMaterialIssue current = issues.find(id).orElseThrow(() -> new BusinessException("领用单不存在"));
            ConsumptionTiming timing = costs.timingOf(id);
            if (timing == null || timing == ConsumptionTiming.UNDECIDED) {
                throw new BusinessException("耗材计耗方式尚未选定");
            }
            issues.addReturned(id, counted, version, session);
            if (timing == ConsumptionTiming.ON_ACTUAL_USE) {
                stocks.take(current.itemId(), current.batchCode(), current.workshopCode(), counted, current.unit(), StockMeasurement.unknown(), session);
            }
            stocks.add(new CompanyMaterialBalance(
                    current.itemId(), current.batchCode(), current.fromWarehouse(), counted, current.unit(), StockMeasurement.unknown()), session);
            if (costs.costPosted(id)) {
                costs.insert(costEvent(id, ConsumableCostEvent.ADJUSTMENT, counted, adjustment(id, current.quantity(), counted)), session);
            }
            String code = "TH-" + token();
            issues.insert(new CompanyMaterialReturn(code, id, counted, current.unit(), StockMeasurement.unknown()), session);
            return code;
        });
        return issues.findReturn(returnId).orElseThrow(() -> new BusinessException("退回单不存在"));
    }

    public ConsumableCostEvent consume(String requestId, OperatorContext actor, String issueId, int version, BigDecimal quantity) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_ISSUE, actor);
        BigDecimal counted = positive(quantity);
        String id = text(issueId);
        CompanyMaterialIssue current = issues.find(id).orElseThrow(() -> new BusinessException("领用单不存在"));
        ConsumptionTiming timing = costs.timingOf(id);
        if (timing == ConsumptionTiming.ON_ISSUE) {
            throw new BusinessException("领用已经计耗，不能再计一次");
        }
        if (timing != ConsumptionTiming.ON_ACTUAL_USE) {
            throw new BusinessException("耗材计耗方式尚未选定");
        }
        String eventId = unit.execute(new CommandCall(
                requestId, actor.operator(), "CONSUME_CONSUMABLE", "CONSUMABLE_ISSUE", id,
                id + "\n" + version + "\n" + counted.toPlainString()
        ), session -> {
            if (costs.costPosted(id)) {
                throw new BusinessException("领用已经计耗，不能再计一次");
            }
            CompanyMaterialIssue locked = issues.find(id).orElseThrow(() -> new BusinessException("领用单不存在"));
            BigDecimal open = locked.quantity().subtract(locked.returnedQuantity()).subtract(locked.consumedQuantity());
            if (counted.compareTo(open) > 0) {
                throw new BusinessException("可领数量不足");
            }
            issues.dispose(id, counted, BigDecimal.ZERO, open.subtract(counted), version, session);
            stocks.take(locked.itemId(), locked.batchCode(), locked.workshopCode(), counted, locked.unit(), StockMeasurement.unknown(), session);
            CompanyMaterialReceipt source = stocks.find(costs.receiptIdOf(id)).orElse(null);
            ConsumableCostEvent event = costEvent(id, ConsumableCostEvent.COST, counted,
                    source == null ? null : portion(source, counted));
            costs.insert(event, session);
            costs.markCostPosted(id, session);
            return event.eventId();
        });
        return costs.events(id).stream().filter(event -> event.eventId().equals(eventId)).findFirst()
                .orElseThrow(() -> new BusinessException("耗材费用不存在"));
    }

    public java.util.List<ConsumableCostEvent> events(String issueId) {
        return costs.events(issueId);
    }

    private BigDecimal portion(CompanyMaterialReceipt receipt, BigDecimal quantity) {
        if (receipt.amount() == null) {
            return null;
        }
        if (receipt.quantity().signum() <= 0) {
            return null;
        }
        return receipt.amount().multiply(quantity).divide(receipt.quantity(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal adjustment(String issueId, BigDecimal issued, BigDecimal returned) {
        ConsumableCostEvent cost = costs.events(issueId).stream()
                .filter(event -> ConsumableCostEvent.COST.equals(event.kind()) && event.amount() != null)
                .findFirst().orElse(null);
        if (cost == null || issued.signum() <= 0) {
            return null;
        }
        return cost.amount().multiply(returned).divide(issued, 2, RoundingMode.HALF_UP).negate();
    }

    private ConsumableCostEvent costEvent(String issueId, String kind, BigDecimal quantity, BigDecimal amount) {
        return new ConsumableCostEvent(
                "CE-" + token(), issueId, kind,
                amount == null ? CompanyMaterialAmountState.INCOMPLETE : CompanyMaterialAmountState.KNOWN,
                amount, quantity);
    }

    private FactoryItem consumable(String itemId) {
        FactoryItem found = items.find(text(itemId)).orElseThrow(() -> new BusinessException("物品档案不存在"));
        if (found.category() != FactoryItemCategory.CONSUMABLE) {
            throw new BusinessException("只有耗材按计耗时点领退");
        }
        return found;
    }

    private static BigDecimal positive(BigDecimal quantity) {
        BigDecimal counted = StockMeasurement.processingScale(quantity);
        if (counted.signum() <= 0) {
            throw new BusinessException("数量必须大于零");
        }
        return counted;
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String required(String value, String message, int max) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        if (text.length() > max) {
            throw new BusinessException("车间过长");
        }
        return text;
    }
}
