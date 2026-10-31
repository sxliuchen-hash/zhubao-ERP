package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetCustodyRepository;
import com.zhubao.erp.finance.domain.FixedAssetCustodyTransfer;
import com.zhubao.erp.finance.domain.FixedAssetCustodyView;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 追加资产交接。更新当前位置和保管人，保留前后值。不改原值，不记折旧或应付。
 */
@Service
public class FixedAssetCustodyService {

    private final FinanceRepository finance;
    private final FixedAssetPhysicalRepository physical;
    private final FixedAssetCustodyRepository custody;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;
    private final AtomicLong sequence = new AtomicLong();

    public FixedAssetCustodyService(FinanceRepository finance, FixedAssetPhysicalRepository physical,
                                    FixedAssetCustodyRepository custody, BusinessWorkUnit unit,
                                    PermissionGuard permissions) {
        this.finance = finance;
        this.physical = physical;
        this.custody = custody;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<FixedAssetCustodyView> history(OperatorContext actor, String assetCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.ASSET_CUSTODY, actor);
        FixedAsset asset = requireAsset(assetCode);
        return custody.list(asset.code()).stream().map(row -> view(row, asset)).toList();
    }

    public FixedAssetCustodyView transfer(String requestId, OperatorContext actor, String assetCode,
                                          Long expectedVersion, String location, String custodian, String useStatus) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.ASSET_CUSTODY, actor);
        String code = required(assetCode, "请选择固定资产");
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BusinessException("请提供卡片版本");
        }
        String nextLocation = fact(location, "位置");
        String nextCustodian = fact(custodian, "保管人");
        String nextStatus = status(useStatus);
        String payload = expectedVersion + "|" + mark(nextLocation) + "|" + mark(nextCustodian) + "|" + mark(nextStatus);
        String id = unit.execute(new CommandCall(
                requestId, actor.operator(), "TRANSFER_FIXED_ASSET", "FIXED_ASSET", code, payload
        ), session -> apply(session, code, expectedVersion, nextLocation, nextCustodian, nextStatus));
        FixedAsset asset = requireAsset(code);
        return custody.list(code).stream()
                .filter(row -> id.equals(row.transferId()))
                .findFirst()
                .map(row -> view(row, asset))
                .orElseThrow(() -> new BusinessException("资产交接不存在"));
    }

    private String apply(WorkSession session, String assetCode, long expectedVersion, String location, String custodian, String useStatus) {
        FixedAsset asset = requireAsset(assetCode);
        FixedAssetPhysical current = physical.lock(asset.code(), session).orElse(null);
        long version = current == null ? 0 : current.version();
        if (expectedVersion != version) {
            throw new BusinessException("资产卡片版本已变化");
        }
        String fromLocation = current == null ? null : current.locationName();
        String fromCustodian = current == null ? null : current.custodianName();
        String fromStatus = current == null ? null : current.useStatus();
        String toLocation = location.isEmpty() ? fromLocation : location;
        String toCustodian = custodian.isEmpty() ? fromCustodian : custodian;
        String toStatus = useStatus.isEmpty() ? fromStatus : useStatus;
        if (same(fromLocation, toLocation) && same(fromCustodian, toCustodian) && same(fromStatus, toStatus)) {
            throw new BusinessException("交接没有变化");
        }
        long nextVersion = version + 1;
        String id = FinanceTexts.code("FIN-MOV", sequence);
        custody.insert(new FixedAssetCustodyTransfer(
                id, asset.code(), fromLocation, toLocation, fromCustodian, toCustodian, fromStatus, toStatus, nextVersion
        ), session);
        FixedAssetPhysical updated = new FixedAssetPhysical(
                asset.code(),
                current == null ? null : current.modelName(),
                current == null ? null : current.serialNumber(),
                toLocation,
                toCustodian,
                toStatus,
                nextVersion
        );
        if (current == null) {
            physical.insert(updated, session);
        } else {
            physical.replace(updated, version, session);
        }
        return id;
    }

    private FixedAssetCustodyView view(FixedAssetCustodyTransfer row, FixedAsset asset) {
        return new FixedAssetCustodyView(
                row.transferId(),
                row.assetCode(),
                asset.originalValue(),
                label(row.fromLocation()),
                label(row.toLocation()),
                label(row.fromCustodian()),
                label(row.toCustodian()),
                statusLabel(row.fromStatus()),
                statusLabel(row.toStatus()),
                row.version()
        );
    }

    private FixedAsset requireAsset(String assetCode) {
        String code = required(assetCode, "请选择固定资产");
        return finance.listAssets().stream()
                .filter(asset -> code.equals(asset.code()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到固定资产"));
    }

    private static String status(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.equals("待补") || text.equals("未补") || text.equals("未知") || text.equals("待核")) {
            throw new BusinessException("不能把待补写成使用状态");
        }
        return switch (text) {
            case "IN_USE", "在用" -> "IN_USE";
            case "IDLE", "闲置" -> "IDLE";
            default -> throw new BusinessException("使用状态只能登记在用或闲置");
        };
    }

    private static String fact(String raw, String label) {
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

    private static String statusLabel(String stored) {
        if (stored == null || stored.isBlank()) {
            return "待补";
        }
        return switch (stored) {
            case "IN_USE" -> "在用";
            case "IDLE" -> "闲置";
            default -> stored;
        };
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? "待补" : value;
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        if (text.length() > 80) {
            throw new BusinessException("资产编码过长");
        }
        return text;
    }

    private static String mark(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private static boolean same(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        return a.equals(b);
    }
}
