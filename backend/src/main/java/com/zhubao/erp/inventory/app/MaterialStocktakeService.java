package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemRepository;
import com.zhubao.erp.inventory.domain.MaterialStocktake;
import com.zhubao.erp.inventory.domain.MaterialStocktakeRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.ToolCustodyRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * 仅支持发起时盘点。期间发生领退时须重新盘点，不能把当前余额冒充历史余额。
 */
@Service
public class MaterialStocktakeService {

    private final FactoryItemRepository items;
    private final CompanyMaterialReceiptRepository materials;
    private final ToolCustodyRepository tools;
    private final MaterialStocktakeRepository stocktakes;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public MaterialStocktakeService(FactoryItemRepository items, CompanyMaterialReceiptRepository materials,
                                    ToolCustodyRepository tools, MaterialStocktakeRepository stocktakes,
                                    BusinessWorkUnit unit, PermissionGuard permissions) {
        this.items = items;
        this.materials = materials;
        this.tools = tools;
        this.stocktakes = stocktakes;
        this.unit = unit;
        this.permissions = permissions;
    }

    public MaterialStocktake open(String requestId, OperatorContext actor, String itemId, String batchCode,
                                  String locationCode, String countedAt, boolean tool) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RECEIVE, actor);
        String when = time(countedAt);
        FactoryItem item = items.find(text(itemId)).orElseThrow(() -> new BusinessException("物品档案不存在"));
        if (tool != (item.category() == com.zhubao.erp.inventory.domain.FactoryItemCategory.REUSABLE_TOOL)) {
            throw new BusinessException("盘点类型与物品类别不一致");
        }
        if (tool && item.custodyMode() != com.zhubao.erp.inventory.domain.CustodyMode.QUANTITY) {
            throw new BusinessException("逐件工具请按工具编号核对，不能使用数量差额盘点");
        }
        String location = required(locationCode, "请填写盘点位置", 40);
        String batch = batchCode == null ? "" : batchCode.trim();
        String id = unit.execute(new CommandCall(
                requestId, actor.operator(), "OPEN_STOCKTAKE", "STOCKTAKE", item.itemId(),
                item.itemId() + "\n" + batch + "\n" + location + "\n" + when + "\n" + tool
        ), session -> {
            BigDecimal book = book(item, batch, location, session);
            String stocktakeId = "PD-" + token();
            stocktakes.insert(new MaterialStocktake(
                    stocktakeId, LocalDateTime.now().withNano(0).toString(), item.itemId(), batch, location, book, null, MaterialStocktake.OPEN, 1), session);
            return stocktakeId;
        });
        return stocktakes.find(id).orElseThrow(() -> new BusinessException("盘点单不存在"));
    }

    public MaterialStocktake count(String requestId, OperatorContext actor, String stocktakeId, int version, BigDecimal counted) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RECEIVE, actor);
        BigDecimal actual = nonNegative(counted);
        String id = text(stocktakeId);
        unit.execute(new CommandCall(
                requestId, actor.operator(), "COUNT_STOCKTAKE", "STOCKTAKE", id,
                id + "\n" + version + "\n" + actual.toPlainString()
        ), session -> {
            stocktakes.count(id, actual, version, session);
            return id;
        });
        return stocktakes.find(id).orElseThrow(() -> new BusinessException("盘点单不存在"));
    }

    public MaterialStocktake confirm(String requestId, OperatorContext actor, String stocktakeId, int version) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FACTORY_STOCKTAKE_CONFIRM, actor);
        String id = text(stocktakeId);
        unit.execute(new CommandCall(
                requestId, actor.operator(), "CONFIRM_STOCKTAKE", "STOCKTAKE", id,
                id + "\n" + version
        ), session -> {
            MaterialStocktake current = stocktakes.find(id).orElseThrow(() -> new BusinessException("盘点单不存在"));
            if (current.countedQuantity() == null) {
                throw new BusinessException("请先填写实盘数量");
            }
            FactoryItem item = items.find(current.itemId()).orElseThrow(() -> new BusinessException("物品档案不存在"));
            if (book(item, current.batchCode(), current.locationCode(), session).compareTo(current.bookQuantity()) != 0) {
                throw new BusinessException("盘点期间库存已变动，请重新发起盘点");
            }
            BigDecimal delta = current.countedQuantity().subtract(current.bookQuantity());
            stocktakes.confirm(id, version, session);
            if (delta.signum() != 0) {
                apply(current, delta, session);
            }
            return id;
        });
        return stocktakes.find(id).orElseThrow(() -> new BusinessException("盘点单不存在"));
    }

    private void apply(MaterialStocktake current, BigDecimal delta, com.zhubao.erp.work.domain.WorkSession session) {
        FactoryItem item = items.find(current.itemId()).orElseThrow(() -> new BusinessException("物品档案不存在"));
        if (item.category() == com.zhubao.erp.inventory.domain.FactoryItemCategory.REUSABLE_TOOL) {
            tools.addStock(item.itemId(), current.locationCode(), delta, item.unit(), session);
            return;
        }
        if (delta.signum() > 0) {
            materials.add(new CompanyMaterialBalance(
                    item.itemId(), current.batchCode(), current.locationCode(), delta, item.unit(), StockMeasurement.unknown()), session);
            return;
        }
        materials.take(item.itemId(), current.batchCode(), current.locationCode(), delta.abs(), item.unit(), StockMeasurement.unknown(), session);
    }

    private BigDecimal book(FactoryItem item, String batch, String location, com.zhubao.erp.work.domain.WorkSession session) {
        if (item.category() == com.zhubao.erp.inventory.domain.FactoryItemCategory.REUSABLE_TOOL) {
            return tools.lockStock(item.itemId(), location, session);
        }
        return materials.lockBalance(item.itemId(), batch, location, session).map(CompanyMaterialBalance::quantity).orElse(BigDecimal.ZERO);
    }

    private static String time(String countedAt) {
        String text = countedAt == null ? "" : countedAt.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写盘点时点");
        }
        if ("NOW".equals(text)) {
            return text;
        }
        try {
            LocalDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("盘点时点需使用 yyyy-MM-ddTHH:mm:ss");
        }
        throw new BusinessException("暂不支持历史时点盘点，请选择现在发起盘点");
    }

    private static BigDecimal nonNegative(BigDecimal counted) {
        if (counted == null) {
            throw new BusinessException("请填写实盘数量");
        }
        BigDecimal actual = StockMeasurement.processingScale(counted);
        if (actual.signum() < 0) {
            throw new BusinessException("实盘数量不能为负");
        }
        return actual;
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
            throw new BusinessException("盘点位置过长");
        }
        return text;
    }
}
