package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalances;
import com.zhubao.erp.inventory.domain.CompanyMaterialConsumption;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
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
 * 确认领用的耗用、退回和留下。费用只记在目标上，不生成客户收费。
 */
@Service
public class CompanyMaterialConsumptionService {

    private final CompanyMaterialReceiptRepository stocks;
    private final CompanyMaterialIssueRepository issues;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public CompanyMaterialConsumptionService(CompanyMaterialReceiptRepository stocks, CompanyMaterialIssueRepository issues,
                                             BusinessWorkUnit unit, PermissionGuard permissions) {
        this.stocks = stocks;
        this.issues = issues;
        this.unit = unit;
        this.permissions = permissions;
    }

    public CompanyMaterialConsumption confirm(String requestId, OperatorContext actor, String issueId, int version,
                                              BigDecimal consumed, BigDecimal returned, BigDecimal kept, BigDecimal scrap,
                                              BigDecimal amount, String targetCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_ISSUE, actor);
        BigDecimal used = part(consumed);
        BigDecimal back = part(returned);
        BigDecimal stay = part(kept);
        BigDecimal wasted = part(scrap == null ? BigDecimal.ZERO : scrap);
        if (wasted.compareTo(used) > 0) {
            throw new BusinessException("废料不能超过实际耗用");
        }
        BigDecimal money = CompanyMaterialBalances.knownAmount(amount);
        CompanyMaterialIssue issue = issues.find(text(issueId)).orElseThrow(() -> new BusinessException("领用单不存在"));
        String target = targetCode == null || targetCode.isBlank() ? issue.targetCode() : targetCode.trim();
        if (target.length() > 40) {
            throw new BusinessException("业务目标过长");
        }
        String saved = unit.execute(new CommandCall(
                requestId, actor.operator(), "CONSUME_COMPANY_MATERIAL", "COMPANY_MATERIAL_ISSUE", issue.issueId(),
                issue.issueId() + "\n" + version + "\n" + plain(used) + "\n" + plain(back) + "\n" + plain(stay)
                        + "\n" + plain(wasted) + "\n" + (money == null ? "INCOMPLETE" : money.toPlainString()) + "\n" + target
        ), session -> {
            CompanyMaterialIssue locked = issues.find(issue.issueId()).orElseThrow(() -> new BusinessException("领用单不存在"));
            issues.dispose(locked.issueId(), used, back, stay, version, session);
            StockMeasurement.Weight unknown = StockMeasurement.unknown();
            if (used.signum() > 0) {
                stocks.take(locked.itemId(), locked.batchCode(), locked.workshopCode(), used, locked.unit(), unknown, session);
            }
            if (back.signum() > 0) {
                stocks.take(locked.itemId(), locked.batchCode(), locked.workshopCode(), back, locked.unit(), unknown, session);
                stocks.add(new CompanyMaterialBalance(
                        locked.itemId(), locked.batchCode(), locked.fromWarehouse(), back, locked.unit(), unknown), session);
            }
            String id = "HY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
            issues.insert(new CompanyMaterialConsumption(
                    id, locked.issueId(), used, back, stay, wasted, locked.unit(),
                    money == null ? CompanyMaterialAmountState.INCOMPLETE : CompanyMaterialAmountState.KNOWN,
                    money, target), session);
            return id;
        });
        return issues.findConsumption(saved).orElseThrow(() -> new BusinessException("耗用确认不存在"));
    }

    private static BigDecimal part(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("请填写去向数量");
        }
        BigDecimal scaled = StockMeasurement.processingScale(value);
        if (scaled.signum() < 0) {
            throw new BusinessException("数量不能为负");
        }
        return scaled;
    }

    private static String plain(BigDecimal value) {
        return value.toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
