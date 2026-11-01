package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalances;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * 公司珠料和配件入库。采购应付和入库在同一次提交里完成；期初和已有应付不另开一笔。
 */
@Service
public class CompanyMaterialReceiptService {

    private final FactoryItemRepository items;
    private final CompanyMaterialReceiptRepository receipts;
    private final FinanceRepository finance;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public CompanyMaterialReceiptService(FactoryItemRepository items, CompanyMaterialReceiptRepository receipts,
                                         FinanceRepository finance, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.items = items;
        this.receipts = receipts;
        this.finance = finance;
        this.unit = unit;
        this.permissions = permissions;
    }

    public CompanyMaterialReceipt receive(String requestId, OperatorContext actor, String itemId, BigDecimal quantity,
                                          BigDecimal weight, String unitName, String warehouseCode, String bizDate,
                                          CompanyMaterialSourceKind sourceKind, String supplierName, BigDecimal amount,
                                          String coveredPayableCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RECEIVE, actor);
        if (sourceKind == null) {
            throw new BusinessException("请选择入库来源");
        }
        if (sourceKind == CompanyMaterialSourceKind.COVERED_PAYABLE) {
            RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_PAYABLE, actor);
        }
        BigDecimal counted = quantity(quantity);
        StockMeasurement.Weight measured = weight(weight);
        String unitText = required(unitName, "请填写单位", 16);
        String warehouse = required(warehouseCode, "请填写仓库", 40);
        String day = date(bizDate);
        BigDecimal money = CompanyMaterialBalances.knownAmount(amount);
        String supplier = supplierName == null ? "" : supplierName.trim();
        String covered = coveredPayableCode == null ? "" : coveredPayableCode.trim();
        if (sourceKind == CompanyMaterialSourceKind.PURCHASE) {
            required(supplier, "请填写供应商", 80);
            if (money != null) {
                RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_PAYABLE, actor);
            }
        }
        if (supplier.length() > 80) {
            throw new BusinessException("供应商过长");
        }
        FactoryItem item = item(itemId);
        if (!item.unit().equals(unitText)) {
            throw new BusinessException("单位必须与物品档案一致");
        }
        String receiptId = unit.execute(new CommandCall(
                requestId,
                actor.operator(),
                "RECEIVE_COMPANY_MATERIAL",
                "COMPANY_MATERIAL",
                item.itemId(),
                payload(item.itemId(), counted, measured, unitText, warehouse, day, sourceKind, supplier, money, covered)
        ), session -> {
            FactoryItem locked = item(item.itemId());
            if (!locked.unit().equals(unitText)) {
                throw new BusinessException("单位必须与物品档案一致");
            }
            Payable existing = resolveCover(sourceKind, covered, money);
            if (existing != null && receipts.coversPayable(existing.code(), session)) {
                throw new BusinessException("这笔应付已经对应过入库");
            }
            String id = "GS-" + token();
            String batch = "PC-" + token();
            String payableCode = "";
            CompanyMaterialAmountState amountState = amountState(sourceKind, money);
            BigDecimal storedAmount = money;
            String storedSupplier = supplier;
            if (sourceKind == CompanyMaterialSourceKind.PURCHASE && money != null) {
                payableCode = "AP-" + token();
                finance.savePayable(new Payable(
                        payableCode,
                        "公司料",
                        day.substring(0, 7),
                        supplier,
                        "CMR|" + id,
                        null,
                        "",
                        money,
                        new BigDecimal("0.00"),
                        actor.operator(),
                        LocalDateTime.now().withNano(0).toString()
                ), session);
            }
            if (existing != null) {
                storedAmount = existing.amount();
                amountState = CompanyMaterialAmountState.KNOWN;
                if (storedSupplier.isEmpty()) {
                    storedSupplier = existing.payee();
                }
            }
            CompanyMaterialReceipt receipt = new CompanyMaterialReceipt(
                    id, locked.itemId(), batch, counted, unitText, measured, warehouse, sourceKind,
                    storedSupplier, amountState, storedAmount, payableCode,
                    existing == null ? "" : existing.code(), 1, day, LocalDateTime.now().withNano(0).toString());
            receipts.insert(receipt, session);
            receipts.add(new CompanyMaterialBalance(
                    locked.itemId(), batch, warehouse, counted, unitText, measured), session);
            return id;
        });
        return receipts.find(receiptId).orElseThrow(() -> new BusinessException("公司料入库不存在"));
    }

    /** 读取批次余额。不写入库、应付或库存。 */
    public CompanyMaterialBalance balance(OperatorContext actor, String itemId, String batchCode, String warehouseCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RECEIVE, actor);
        return receipts.balance(text(itemId), text(batchCode), text(warehouseCode)).orElseThrow(
                () -> new BusinessException("公司料批次不存在"));
    }

    public OwnerType owner() {
        return OwnerType.COMPANY;
    }

    private Payable resolveCover(CompanyMaterialSourceKind sourceKind, String covered, BigDecimal money) {
        if (sourceKind != CompanyMaterialSourceKind.COVERED_PAYABLE) {
            if (!covered.isEmpty()) {
                throw new BusinessException("只有关联已有应付时才能填写应付单");
            }
            return null;
        }
        if (covered.isEmpty()) {
            throw new BusinessException("请选择已经存在的应付");
        }
        Payable payable = finance.findPayable(covered).orElseThrow(() -> new BusinessException("应付不存在"));
        if ("工资".equals(payable.kind()) || "OUTSOURCE".equals(payable.kind())) {
            throw new BusinessException("工资或外协应付不能当作公司料入库来源");
        }
        if (money != null && money.compareTo(payable.amount()) != 0) {
            throw new BusinessException("不能改已有应付金额");
        }
        return payable;
    }

    private static CompanyMaterialAmountState amountState(CompanyMaterialSourceKind sourceKind, BigDecimal money) {
        if (sourceKind == CompanyMaterialSourceKind.OPENING) {
            return money == null ? CompanyMaterialAmountState.NOT_PURCHASE : CompanyMaterialAmountState.KNOWN;
        }
        if (sourceKind == CompanyMaterialSourceKind.COVERED_PAYABLE) {
            return CompanyMaterialAmountState.KNOWN;
        }
        return money == null ? CompanyMaterialAmountState.INCOMPLETE : CompanyMaterialAmountState.KNOWN;
    }

    private FactoryItem item(String itemId) {
        FactoryItem found = items.find(text(itemId)).orElseThrow(() -> new BusinessException("物品档案不存在"));
        if (!found.enabled()) {
            throw new BusinessException("物品已停用");
        }
        if (found.category() == FactoryItemCategory.CONSUMABLE) {
            throw new BusinessException("耗材不在这张采购入库里");
        }
        if (found.category() == FactoryItemCategory.REUSABLE_TOOL) {
            throw new BusinessException("工具借用不是采购入库");
        }
        if (found.category() != FactoryItemCategory.BEAD && found.category() != FactoryItemCategory.ACCESSORY) {
            throw new BusinessException("请选择公司珠料或配件");
        }
        return found;
    }

    private static BigDecimal quantity(BigDecimal quantity) {
        BigDecimal counted = StockMeasurement.processingScale(quantity);
        if (counted.signum() <= 0) {
            throw new BusinessException("入库数量必须大于零");
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
        StockMeasurement.Weight known = StockMeasurement.known(StockMeasurement.processingScale(weight));
        if (known.value().signum() <= 0) {
            throw new BusinessException("已知重量必须大于零");
        }
        return known;
    }

    private static String payload(String itemId, BigDecimal quantity, StockMeasurement.Weight weight, String unit,
                                  String warehouse, String day, CompanyMaterialSourceKind sourceKind, String supplier,
                                  BigDecimal amount, String covered) {
        String weightText = weight.state() == StockMeasurement.WeightState.KNOWN ? weight.value().toPlainString() : "UNKNOWN";
        String amountText = amount == null ? "INCOMPLETE" : amount.toPlainString();
        return itemId + "\n" + quantity.toPlainString() + "\n" + weightText + "\n" + unit + "\n" + warehouse
                + "\n" + day + "\n" + sourceKind.name() + "\n" + supplier + "\n" + amountText + "\n" + covered;
    }

    private static String date(String value) {
        String text = required(value, "请填写业务日期", 10);
        try {
            LocalDate parsed = LocalDate.parse(text);
            return parsed.toString();
        } catch (DateTimeParseException ex) {
            throw new BusinessException("业务日期需使用 yyyy-MM-dd");
        }
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
