package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.app.ConsumableQuantityWriteGate;
import com.zhubao.erp.finance.domain.ConsumableItem;
import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.ConsumableAdoption;
import com.zhubao.erp.inventory.domain.ConsumableAdoptionRepository;
import com.zhubao.erp.inventory.domain.ConsumableAdoptionStatus;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.FactoryItemPolicy;
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
 * 把旧耗材结存接到唯一数量账。不把期初再做成采购、应付或费用。
 */
@Service
public class ConsumableOpeningAdoptionService implements ConsumableQuantityWriteGate {

    private static final String PURCHASE = "PURCHASE";
    private static final String ISSUE = "ISSUE";

    private final FinanceRepository finance;
    private final FactoryItemRepository items;
    private final CompanyMaterialReceiptRepository receipts;
    private final ConsumableAdoptionRepository adoptions;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public ConsumableOpeningAdoptionService(FinanceRepository finance, FactoryItemRepository items,
                                            CompanyMaterialReceiptRepository receipts, ConsumableAdoptionRepository adoptions,
                                            BusinessWorkUnit unit, PermissionGuard permissions) {
        this.finance = finance;
        this.items = items;
        this.receipts = receipts;
        this.adoptions = adoptions;
        this.unit = unit;
        this.permissions = permissions;
    }

    public ConsumableAdoption adopt(String requestId, OperatorContext actor, String oldItemCode, String specCode, String warehouseCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_CONSUMABLE, actor);
        String code = required(oldItemCode, "请选择旧耗材", 40);
        String spec = specCode == null ? "" : specCode.trim();
        String warehouse = warehouseCode == null ? "" : warehouseCode.trim();
        if (spec.length() > 40) {
            throw new BusinessException("规格过长");
        }
        if (warehouse.length() > 40) {
            throw new BusinessException("仓库过长");
        }
        ConsumableItem item = finance.findConsumableItem(code).orElseThrow(() -> new BusinessException("找不到消耗品"));
        String saved = unit.execute(new CommandCall(
                requestId, actor.operator(), "ADOPT_CONSUMABLE_OPENING", "CONSUMABLE", code,
                code + "\n" + spec + "\n" + warehouse
        ), session -> {
            ConsumableAdoption existing = adoptions.findByOldCode(code).orElse(null);
            if (existing != null && existing.status() == ConsumableAdoptionStatus.ADOPTED) {
                return existing.adoptionId();
            }
            Holding holding = holding(code);
            String id = existing == null ? "CA-" + token() : existing.adoptionId();
            if (holding.pendingReason() != null) {
                adoptions.save(pending(id, code, holding.pendingReason(), spec, warehouse), session);
                return id;
            }
            if (spec.isEmpty()) {
                adoptions.save(pending(id, code, "缺少可独立领用的规格", spec, warehouse), session);
                return id;
            }
            if (warehouse.isEmpty()) {
                adoptions.save(pending(id, code, "缺少接续仓库", spec, warehouse), session);
                return id;
            }
            if (holding.quantity().signum() == 0) {
                adoptions.save(new ConsumableAdoption(
                        id, code, ConsumableAdoptionStatus.ADOPTED, "结存为零，不另做采购",
                        "", "", BigDecimal.ZERO, CompanyMaterialAmountState.NOT_PURCHASE, null, spec, warehouse), session);
                return id;
            }
            String itemId = ensureItem(item, spec, session);
            String receiptId = "GS-" + token();
            String batch = "AD-" + token();
            CompanyMaterialAmountState amountState = holding.amount() == null
                    ? CompanyMaterialAmountState.INCOMPLETE : CompanyMaterialAmountState.KNOWN;
            receipts.insert(new CompanyMaterialReceipt(
                    receiptId, itemId, batch, holding.quantity(), item.unitName(), StockMeasurement.unknown(), warehouse,
                    CompanyMaterialSourceKind.OPENING, "", amountState, holding.amount(), "", "", 1), session);
            receipts.add(new CompanyMaterialBalance(
                    itemId, batch, warehouse, holding.quantity(), item.unitName(), StockMeasurement.unknown()), session);
            adoptions.save(new ConsumableAdoption(
                    id, code, ConsumableAdoptionStatus.ADOPTED, "", itemId, receiptId, holding.quantity(),
                    amountState, holding.amount(), spec, warehouse), session);
            return id;
        });
        return adoptions.findByOldCode(code).orElseThrow(() -> new BusinessException("耗材接续不存在"));
    }

    @Override
    public void assertWritable(String itemCode) {
        ConsumableAdoption adoption = adoptions.findByOldCode(itemCode == null ? "" : itemCode.trim()).orElse(null);
        if (adoption != null && adoption.status() == ConsumableAdoptionStatus.ADOPTED) {
            throw new BusinessException("该耗材数量已接续，不能再写旧数量账");
        }
    }

    private String ensureItem(ConsumableItem item, String spec, com.zhubao.erp.work.domain.WorkSession session) {
        return items.findIdentity(FactoryItemCategory.CONSUMABLE, item.name(), spec, item.unitName())
                .map(FactoryItem::itemId)
                .orElseGet(() -> {
                    String itemId = "FI-" + token();
                    items.insert(new FactoryItem(
                            itemId, FactoryItemCategory.CONSUMABLE, item.name(), spec, item.unitName(),
                            FactoryItemPolicy.timing(FactoryItemCategory.CONSUMABLE, null),
                            FactoryItemPolicy.custody(FactoryItemCategory.CONSUMABLE, null),
                            true, 1), session);
                    return itemId;
                });
    }

    private Holding holding(String code) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        boolean seen = false;
        for (ConsumableMovement movement : finance.listConsumableMovements()) {
            if (!code.equals(movement.itemCode())) {
                continue;
            }
            seen = true;
            if (PURCHASE.equals(movement.direction())) {
                quantity = quantity.add(movement.quantity());
                amount = amount.add(movement.amount());
            } else if (ISSUE.equals(movement.direction())) {
                quantity = quantity.subtract(movement.quantity());
                amount = amount.subtract(movement.amount());
            }
        }
        if (!seen) {
            return Holding.pending("没有可核对的数量来源");
        }
        if (quantity.signum() < 0 || amount.signum() < 0) {
            return Holding.pending("旧结存来源待核");
        }
        if (quantity.stripTrailingZeros().scale() > 3) {
            return Holding.pending("旧数量精度无法接续");
        }
        BigDecimal money = amount.signum() == 0 ? null : amount.setScale(2, RoundingMode.HALF_UP);
        return new Holding(quantity, money, null);
    }

    private static ConsumableAdoption pending(String id, String code, String reason, String spec, String warehouse) {
        return new ConsumableAdoption(
                id, code, ConsumableAdoptionStatus.PENDING, reason, "", "", null,
                CompanyMaterialAmountState.INCOMPLETE, null, spec, warehouse);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String required(String value, String message, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        if (text.length() > max) {
            throw new BusinessException("旧耗材编号过长");
        }
        return text;
    }

    private record Holding(BigDecimal quantity, BigDecimal amount, String pendingReason) {
        static Holding pending(String reason) {
            return new Holding(null, null, reason);
        }
    }
}
