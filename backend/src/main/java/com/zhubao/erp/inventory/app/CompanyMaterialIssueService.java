package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialReturn;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.FactoryItemRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * 按原批次领用到车间，并按原领用退回。领用不是消耗，也不是客户来料。
 */
@Service
public class CompanyMaterialIssueService {

    private final FactoryItemRepository items;
    private final CompanyMaterialReceiptRepository stocks;
    private final CompanyMaterialIssueRepository issues;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public CompanyMaterialIssueService(FactoryItemRepository items, CompanyMaterialReceiptRepository stocks,
                                       CompanyMaterialIssueRepository issues, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.items = items;
        this.stocks = stocks;
        this.issues = issues;
        this.unit = unit;
        this.permissions = permissions;
    }

    public CompanyMaterialIssue issue(String requestId, OperatorContext actor, String itemId, String batchCode,
                                      String fromWarehouse, String workshopCode, BigDecimal quantity, BigDecimal weight,
                                      String unitName, String targetCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_ISSUE, actor);
        BigDecimal counted = quantity(quantity);
        StockMeasurement.Weight measured = weight(weight);
        String unitText = required(unitName, "请填写单位", 16);
        String batch = required(batchCode, "请选择原批次", 40);
        String from = required(fromWarehouse, "请填写仓库", 40);
        String workshop = required(workshopCode, "请填写车间", 40);
        if (from.equals(workshop)) {
            throw new BusinessException("领用要离开原仓库");
        }
        String target = targetCode == null ? "" : targetCode.trim();
        if (target.length() > 40) {
            throw new BusinessException("业务目标过长");
        }
        FactoryItem item = item(itemId);
        if (!item.unit().equals(unitText)) {
            throw new BusinessException("单位必须与物品档案一致");
        }
        String issueId = unit.execute(new CommandCall(
                requestId, actor.operator(), "ISSUE_COMPANY_MATERIAL", "COMPANY_MATERIAL_ISSUE", item.itemId(),
                payload(item.itemId(), batch, from, workshop, counted, measured, unitText, target)
        ), session -> {
            stocks.take(item.itemId(), batch, from, counted, unitText, measured, session);
            stocks.add(new CompanyMaterialBalance(item.itemId(), batch, workshop, counted, unitText, measured), session);
            String id = "LY-" + token();
            issues.insert(new CompanyMaterialIssue(
                    id, item.itemId(), batch, from, workshop, counted, BigDecimal.ZERO, BigDecimal.ZERO, unitText, measured, target, 1), session);
            return id;
        });
        return issues.find(issueId).orElseThrow(() -> new BusinessException("领用单不存在"));
    }

    public CompanyMaterialReturn returnIssued(String requestId, OperatorContext actor, String issueId, int version,
                                              BigDecimal quantity, BigDecimal weight, String unitName) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RETURN, actor);
        BigDecimal counted = quantity(quantity);
        StockMeasurement.Weight measured = weight(weight);
        String unitText = required(unitName, "请填写单位", 16);
        String id = text(issueId);
        String returnId = unit.execute(new CommandCall(
                requestId, actor.operator(), "RETURN_COMPANY_MATERIAL", "COMPANY_MATERIAL_ISSUE", id,
                id + "\n" + version + "\n" + counted.toPlainString() + "\n" + weightText(measured) + "\n" + unitText
        ), session -> {
            CompanyMaterialIssue current = issues.find(id).orElseThrow(() -> new BusinessException("领用单不存在"));
            if (!current.unit().equals(unitText)) {
                throw new BusinessException("退回单位必须与原领用一致");
            }
            CompanyMaterialIssue updated = issues.addReturned(id, counted, version, session);
            stocks.take(current.itemId(), current.batchCode(), current.workshopCode(), counted, unitText, measured, session);
            stocks.add(new CompanyMaterialBalance(
                    current.itemId(), current.batchCode(), current.fromWarehouse(), counted, unitText, measured), session);
            String code = "TH-" + token();
            issues.insert(new CompanyMaterialReturn(code, updated.issueId(), counted, unitText, measured), session);
            return code;
        });
        return issues.findReturn(returnId).orElseThrow(() -> new BusinessException("退回单不存在"));
    }

    public OwnerType owner() {
        return OwnerType.COMPANY;
    }

    private FactoryItem item(String itemId) {
        FactoryItem found = items.find(text(itemId)).orElseThrow(() -> new BusinessException("物品档案不存在"));
        if (found.category() == FactoryItemCategory.CONSUMABLE || found.category() == FactoryItemCategory.REUSABLE_TOOL) {
            throw new BusinessException("领用退回只用于公司珠料和配件");
        }
        return found;
    }

    private static BigDecimal quantity(BigDecimal quantity) {
        BigDecimal counted = StockMeasurement.processingScale(quantity);
        if (counted.signum() <= 0) {
            throw new BusinessException("数量必须大于零");
        }
        return counted;
    }

    private static StockMeasurement.Weight weight(BigDecimal weight) {
        if (weight == null) {
            return StockMeasurement.unknown();
        }
        if (weight.signum() == 0) {
            throw new BusinessException("未知重量不能写成 0");
        }
        return StockMeasurement.known(StockMeasurement.processingScale(weight));
    }

    private static String payload(String itemId, String batch, String from, String workshop, BigDecimal quantity,
                                  StockMeasurement.Weight weight, String unit, String target) {
        return itemId + "\n" + batch + "\n" + from + "\n" + workshop + "\n" + quantity.toPlainString()
                + "\n" + weightText(weight) + "\n" + unit + "\n" + target;
    }

    private static String weightText(StockMeasurement.Weight weight) {
        return weight.state() == StockMeasurement.WeightState.KNOWN ? weight.value().toPlainString() : "UNKNOWN";
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
            throw new BusinessException(message.replace("请填写", "") + "过长");
        }
        return text;
    }
}
