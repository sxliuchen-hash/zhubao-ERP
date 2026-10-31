package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetDepreciationRun;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按现有使用月数算法延续折旧。已生成的月份不覆盖。
 * 停用或报废从哪个月影响折旧尚未核定，不能写入生效月。
 */
@Service
public class FixedAssetDepreciationContinuityService {

    private final FinanceRepository finance;
    private final FinanceCostService costs;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;
    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    public FixedAssetDepreciationContinuityService(FinanceRepository finance, BusinessWorkUnit unit, PermissionGuard permissions) {
        this(finance, new FinanceCostService(finance), unit, permissions);
    }

    FixedAssetDepreciationContinuityService(FinanceRepository finance, FinanceCostService costs,
                                            BusinessWorkUnit unit, PermissionGuard permissions) {
        this.finance = finance;
        this.costs = costs;
        this.unit = unit;
        this.permissions = permissions;
    }

    public FixedAssetDepreciationRun run(String requestId, OperatorContext actor, String yearMonth, String stopEffectiveMonth) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        if (stopEffectiveMonth != null && !stopEffectiveMonth.isBlank()) {
            throw new BusinessException("停用或报废影响折旧的生效月份尚未核定");
        }
        String month = FinanceTexts.month(yearMonth).toString();
        unit.execute(new CommandCall(
                requestId, actor.operator(), "CONTINUE_ASSET_DEPRECIATION", "FIXED_ASSET", month, month
        ), session -> {
            for (FixedAsset asset : finance.listAssets()) {
                int index = FinanceTexts.depreciationIndex(asset.startMonth(), month);
                if (index < 1 || index > asset.usefulMonths()) {
                    continue;
                }
                boolean exists = finance.listDepreciations().stream()
                        .anyMatch(entry -> asset.code().equals(entry.assetCode()) && month.equals(entry.yearMonth()));
                if (exists) {
                    continue;
                }
                finance.saveDepreciation(new DepreciationEntry(
                        FinanceTexts.code("FIN-DEP", sequence),
                        asset.code(),
                        month,
                        costs.plannedDepreciation(asset, index)
                ), session);
            }
            return month;
        });
        return new FixedAssetDepreciationRun(month, FixedAssetDepreciationRun.MANAGEMENT_ALGORITHM, entries(month));
    }

    private List<DepreciationEntry> entries(String month) {
        return finance.listDepreciations().stream()
                .filter(entry -> month.equals(entry.yearMonth()))
                .sorted(Comparator.comparing(DepreciationEntry::assetCode).thenComparing(DepreciationEntry::code))
                .toList();
    }
}
