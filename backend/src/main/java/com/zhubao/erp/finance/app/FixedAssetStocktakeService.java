package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetCustodyRepository;
import com.zhubao.erp.finance.domain.FixedAssetCustodyTransfer;
import com.zhubao.erp.finance.domain.FixedAssetDispositionRepository;
import com.zhubao.erp.finance.domain.FixedAssetPhysical;
import com.zhubao.erp.finance.domain.FixedAssetPhysicalRepository;
import com.zhubao.erp.finance.domain.FixedAssetStocktake;
import com.zhubao.erp.finance.domain.FixedAssetStocktakeRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按卡片保存盘点时点的位置和保管。确认只追加差异。
 * 漏扫或未见不报废，也不改原值。
 */
@Service
public class FixedAssetStocktakeService {

    private final FinanceRepository finance;
    private final FixedAssetPhysicalRepository physical;
    private final FixedAssetCustodyRepository custody;
    private final FixedAssetDispositionRepository dispositions;
    private final FixedAssetStocktakeRepository stocktakes;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;
    private final AtomicLong sequence = new AtomicLong();

    public FixedAssetStocktakeService(FinanceRepository finance, FixedAssetPhysicalRepository physical,
                                      FixedAssetCustodyRepository custody, FixedAssetDispositionRepository dispositions,
                                      FixedAssetStocktakeRepository stocktakes, BusinessWorkUnit unit,
                                      PermissionGuard permissions) {
        this.finance = finance;
        this.physical = physical;
        this.custody = custody;
        this.dispositions = dispositions;
        this.stocktakes = stocktakes;
        this.unit = unit;
        this.permissions = permissions;
    }

    public FixedAssetStocktake open(String requestId, OperatorContext actor, String assetCode, String countedAt) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        String code = required(assetCode, "请选择固定资产");
        String when = time(countedAt);
        requireAsset(code);
        FixedAssetPhysical book = physical.find(code).orElse(null);
        String payload = code + "|" + when;
        String id = unit.execute(new CommandCall(
                requestId, actor.operator(), "OPEN_ASSET_STOCKTAKE", "FIXED_ASSET", code, payload
        ), session -> {
            String stocktakeId = FinanceTexts.code("FIN-PD", sequence);
            stocktakes.insert(new FixedAssetStocktake(
                    stocktakeId, code, when,
                    book == null ? null : book.locationName(),
                    book == null ? null : book.custodianName(),
                    book == null ? null : book.useStatus(),
                    book == null ? 0 : book.version(),
                    null, null, null, "", FixedAssetStocktake.OPEN, 1
            ), session);
            return stocktakeId;
        });
        return stocktakes.find(id).orElseThrow(() -> new BusinessException("盘点单不存在"));
    }

    public FixedAssetStocktake count(String requestId, OperatorContext actor, String stocktakeId, long expectedVersion,
                                     String seen, String location, String custodian, String exceptionText) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        String id = required(stocktakeId, "请选择盘点单");
        String presence = seen(seen);
        String place = fact(location, "位置");
        String holder = fact(custodian, "保管人");
        String note = fact(exceptionText, "异常");
        if (note.length() > 200) {
            throw new BusinessException("异常说明过长");
        }
        unit.execute(new CommandCall(
                requestId, actor.operator(), "COUNT_ASSET_STOCKTAKE", "FIXED_ASSET", id,
                id + "|" + expectedVersion + "|" + presence + "|" + mark(place) + "|" + mark(holder) + "|" + mark(note)
        ), session -> {
            stocktakes.count(id, presence, blankToNull(place), blankToNull(holder), note, expectedVersion, session);
            return id;
        });
        return stocktakes.find(id).orElseThrow(() -> new BusinessException("盘点单不存在"));
    }

    public FixedAssetStocktake confirm(String requestId, OperatorContext actor, String stocktakeId, long expectedVersion) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FACTORY_STOCKTAKE_CONFIRM, actor);
        String id = required(stocktakeId, "请选择盘点单");
        unit.execute(new CommandCall(
                requestId, actor.operator(), "CONFIRM_ASSET_STOCKTAKE", "FIXED_ASSET", id,
                id + "|" + expectedVersion
        ), session -> confirmIn(session, id, expectedVersion));
        return stocktakes.find(id).orElseThrow(() -> new BusinessException("盘点单不存在"));
    }

    private String confirmIn(WorkSession session, String stocktakeId, long expectedVersion) {
        FixedAssetStocktake current = stocktakes.find(stocktakeId).orElseThrow(() -> new BusinessException("盘点单不存在"));
        if (FixedAssetStocktake.CONFIRMED.equals(current.status())) {
            throw new BusinessException("盘点已经确认");
        }
        FixedAsset asset = requireAsset(current.assetCode());
        if (current.seen() == null) {
            throw new BusinessException("请先登记实盘");
        }
        FixedAssetPhysical live = physical.lock(asset.code(), session).orElse(null);
        long liveVersion = live == null ? 0 : live.version();
        if (liveVersion != current.bookVersion()) {
            throw new BusinessException("盘点后位置或保管已变化，请重新盘点");
        }
        String day = current.countedAt().substring(0, 10);
        boolean laterDisposition = dispositions.list(asset.code()).stream()
                .anyMatch(row -> row.occurredOn().compareTo(day) >= 0);
        if (laterDisposition) {
            throw new BusinessException("盘点后已有处置，请重新盘点");
        }
        stocktakes.confirm(stocktakeId, expectedVersion, session);
        if (FixedAssetStocktake.ABSENT.equals(current.seen())) {
            return stocktakeId;
        }
        String nextLocation = current.countLocation() == null ? (live == null ? null : live.locationName()) : current.countLocation();
        String nextCustodian = current.countCustodian() == null ? (live == null ? null : live.custodianName()) : current.countCustodian();
        String fromLocation = live == null ? null : live.locationName();
        String fromCustodian = live == null ? null : live.custodianName();
        if (same(fromLocation, nextLocation) && same(fromCustodian, nextCustodian)) {
            return stocktakeId;
        }
        long nextVersion = liveVersion + 1;
        custody.insert(new FixedAssetCustodyTransfer(
                FinanceTexts.code("FIN-MOV", sequence), asset.code(), fromLocation, nextLocation,
                fromCustodian, nextCustodian, live == null ? null : live.useStatus(),
                live == null ? null : live.useStatus(), nextVersion
        ), session);
        FixedAssetPhysical updated = new FixedAssetPhysical(
                asset.code(),
                live == null ? null : live.modelName(),
                live == null ? null : live.serialNumber(),
                nextLocation, nextCustodian, live == null ? null : live.useStatus(), nextVersion
        );
        if (live == null) {
            physical.insert(updated, session);
        } else {
            physical.replace(updated, liveVersion, session);
        }
        return stocktakeId;
    }

    private FixedAsset requireAsset(String assetCode) {
        return finance.listAssets().stream()
                .filter(asset -> assetCode.equals(asset.code()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到固定资产"));
    }

    private static String time(String value) {
        String text = required(value, "请填写盘点时点");
        try {
            LocalDateTime.parse(text);
            return text;
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate.parse(text);
                return text + "T00:00:00";
            } catch (DateTimeParseException ex) {
                throw new BusinessException("盘点时点格式不正确");
            }
        }
    }

    private static String seen(String value) {
        String text = required(value, "请登记在场或未见");
        if (text.equals(FixedAssetStocktake.PRESENT) || text.equals("在场")) {
            return FixedAssetStocktake.PRESENT;
        }
        if (text.equals(FixedAssetStocktake.ABSENT) || text.equals("未见")) {
            return FixedAssetStocktake.ABSENT;
        }
        throw new BusinessException("请登记在场或未见");
    }

    private static String fact(String value, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.equals("待补") || text.equals("未知")) {
            throw new BusinessException("不能把待补写成" + label);
        }
        return text;
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }

    private static String mark(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static boolean same(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        return a.equals(b);
    }
}
