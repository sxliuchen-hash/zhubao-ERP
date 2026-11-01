package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetCardView;
import com.zhubao.erp.finance.domain.FixedAssetField;
import com.zhubao.erp.finance.domain.FixedAssetPhysical;
import com.zhubao.erp.finance.domain.FixedAssetPhysicalRepository;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 沿用已有固定资产编码补登记实物字段。不新建卡片，不改原值，不记折旧或应付。
 * 没有登记过的位置、保管和其他历史字段保持待补。
 */
@Service
public class FixedAssetCardAdoptionService {

    static final String IN_USE = "IN_USE";
    static final String IDLE = "IDLE";

    private final FinanceRepository finance;
    private final FixedAssetPhysicalRepository physical;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public FixedAssetCardAdoptionService(FinanceRepository finance, FixedAssetPhysicalRepository physical,
                                         BusinessWorkUnit unit, PermissionGuard permissions) {
        this.finance = finance;
        this.physical = physical;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<FixedAssetCardView> cards(OperatorContext actor) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        return finance.listAssets().stream().map(this::view).toList();
    }

    public FixedAssetCardView supplement(String requestId, OperatorContext actor, String assetCode,
                                         Long expectedVersion, String model, String serialNumber,
                                         String location, String custodian, String useStatus) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        String code = requiredCode(assetCode);
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BusinessException("请提供卡片版本");
        }
        String payload = expectedVersion
                + "|" + mark(model)
                + "|" + mark(serialNumber)
                + "|" + mark(location)
                + "|" + mark(custodian)
                + "|" + mark(useStatus);
        unit.execute(new CommandCall(
                requestId, actor.operator(), "ADOPT_FIXED_ASSET_CARD", "FIXED_ASSET", code, payload
        ), session -> apply(session, code, expectedVersion, model, serialNumber, location, custodian, useStatus));
        return view(requireAsset(code));
    }

    private String apply(WorkSession session, String code, long expectedVersion, String model, String serialNumber,
                         String location, String custodian, String useStatus) {
        requireAsset(code);
        FixedAssetPhysical current = physical.lock(code, session).orElse(null);
        long version = current == null ? 0 : current.version();
        if (expectedVersion != version) {
            throw new BusinessException("资产卡片版本已变化");
        }
        FixedAssetPhysical next = merge(code, current, model, serialNumber, location, custodian, useStatus);
        if (next == current) {
            return code;
        }
        if (current == null) {
            physical.insert(next, session);
        } else {
            physical.replace(next, version, session);
        }
        return code;
    }

    private FixedAssetPhysical merge(String code, FixedAssetPhysical current, String model, String serialNumber,
                                     String location, String custodian, String useStatus) {
        String nextModel = mergeFact(current == null ? null : current.modelName(), model, "型号");
        String nextSerial = mergeFact(current == null ? null : current.serialNumber(), serialNumber, "序列号");
        String nextLocation = mergeFact(current == null ? null : current.locationName(), location, "位置");
        String nextCustodian = mergeFact(current == null ? null : current.custodianName(), custodian, "保管人");
        String nextStatus = mergeStatus(current == null ? null : current.useStatus(), useStatus);
        boolean changed = differs(current == null ? null : current.modelName(), nextModel)
                || differs(current == null ? null : current.serialNumber(), nextSerial)
                || differs(current == null ? null : current.locationName(), nextLocation)
                || differs(current == null ? null : current.custodianName(), nextCustodian)
                || differs(current == null ? null : current.useStatus(), nextStatus);
        if (!changed) {
            if (current == null) {
                throw new BusinessException("没有可登记的实物字段，缺项保持待补");
            }
            return current;
        }
        long version = current == null ? 1 : current.version() + 1;
        return new FixedAssetPhysical(code, blankToNull(nextModel), blankToNull(nextSerial),
                blankToNull(nextLocation), blankToNull(nextCustodian), blankToNull(nextStatus), version);
    }

    private FixedAssetCardView view(FixedAsset asset) {
        FixedAssetPhysical row = physical.find(asset.code()).orElse(null);
        return new FixedAssetCardView(
                asset.code(),
                asset.name(),
                asset.category(),
                asset.originalValue(),
                asset.residualValue(),
                asset.startMonth(),
                asset.usefulMonths(),
                fact(row == null ? null : row.modelName()),
                fact(row == null ? null : row.serialNumber()),
                fact(row == null ? null : row.locationName()),
                fact(row == null ? null : row.custodianName()),
                status(row == null ? null : row.useStatus()),
                row == null ? 0 : row.version()
        );
    }

    private FixedAsset requireAsset(String code) {
        return finance.listAssets().stream()
                .filter(asset -> code.equals(asset.code()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到固定资产"));
    }

    private static FixedAssetField fact(String stored) {
        if (stored == null || stored.isBlank()) {
            return FixedAssetField.pending();
        }
        return FixedAssetField.recorded(stored);
    }

    private static FixedAssetField status(String stored) {
        if (stored == null || stored.isBlank()) {
            return FixedAssetField.pending();
        }
        String label = switch (stored) {
            case IN_USE -> "在用";
            case IDLE -> "闲置";
            default -> stored;
        };
        return new FixedAssetField(FixedAssetField.RECORDED, stored, label);
    }

    private static String mergeFact(String current, String incoming, String label) {
        String next = normalizeFact(incoming, label);
        if (next.isEmpty()) {
            return current;
        }
        if (current == null || current.isEmpty()) {
            return next;
        }
        if (current.equals(next)) {
            return current;
        }
        throw new BusinessException("已登记的" + label + "不能在接续里改写");
    }

    private static String mergeStatus(String current, String incoming) {
        String next = normalizeStatus(incoming);
        if (next.isEmpty()) {
            return current;
        }
        if (current == null || current.isEmpty()) {
            return next;
        }
        if (current.equals(next)) {
            return current;
        }
        throw new BusinessException("已登记的使用状态不能在接续里改写");
    }

    private static String normalizeFact(String raw, String label) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.equals("待补") || text.equals("未补") || text.equals("未知") || text.equals("待核")) {
            throw new BusinessException("不能把待补写成" + label);
        }
        if (text.length() > 80) {
            throw new BusinessException(label + "过长");
        }
        return text;
    }

    private static String normalizeStatus(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.equals("待补") || text.equals("未补") || text.equals("未知") || text.equals("待核")) {
            throw new BusinessException("不能把待补写成使用状态");
        }
        return switch (text) {
            case IN_USE, "在用" -> IN_USE;
            case IDLE, "闲置" -> IDLE;
            default -> throw new BusinessException("使用状态只能登记在用或闲置");
        };
    }

    private static String requiredCode(String assetCode) {
        String code = assetCode == null ? "" : assetCode.trim();
        if (code.isEmpty()) {
            throw new BusinessException("请选择固定资产");
        }
        if (code.length() > 80) {
            throw new BusinessException("资产编码过长");
        }
        return code;
    }

    private static String mark(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() + ":" + text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static boolean differs(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        return !a.equals(b);
    }
}
