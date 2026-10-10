package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ConsumableItem;
import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.ConsumableStockView;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FinanceCommands.DepreciationRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.IssueConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.OutsourceBillCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PayablePaymentCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PurchaseConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveConsumableItemCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveFixedAssetCommand;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消耗品、固定资产、外协应付和付款。消耗品不进入客户金料库存。
 */
@Service
public class FinanceCostService {

    static final String PURCHASE = "PURCHASE";
    static final String ISSUE = "ISSUE";

    private final FinanceRepository finance;
    private final AtomicLong sequence = new AtomicLong();

    public FinanceCostService(FinanceRepository finance) {
        this.finance = finance;
    }

    public List<ConsumableStockView> consumableStocks() {
        List<ConsumableStockView> views = new ArrayList<>();
        for (ConsumableItem item : finance.listConsumableItems()) {
            OnHand onHand = onHand(item.code());
            views.add(new ConsumableStockView(
                    item.code(),
                    item.name(),
                    item.unitName(),
                    FinanceTexts.quantity(onHand.quantity()),
                    FinanceTexts.money(onHand.amount())
            ));
        }
        return views;
    }

    public List<ConsumableMovement> movements(String itemCode) {
        String code = FinanceTexts.blank(itemCode);
        return finance.listConsumableMovements().stream()
                .filter(movement -> code.isEmpty() || code.equals(movement.itemCode()))
                .toList();
    }

    public ConsumableItem saveItem(SaveConsumableItemCommand command) {
        ConsumableItem item = new ConsumableItem(command.code().trim(), command.name().trim(), command.unitName().trim());
        finance.saveConsumableItem(item);
        return item;
    }

    public ConsumableMovement purchase(PurchaseConsumableCommand command, String operator) {
        FinanceTexts.month(command.yearMonth());
        ConsumableItem item = requireItem(command.itemCode());
        BigDecimal quantity = FinanceTexts.quantity(command.quantity());
        BigDecimal amount = FinanceTexts.money(command.amount());
        String payableCode = FinanceTexts.code("FIN-AP", sequence);
        finance.savePayable(new Payable(
                payableCode,
                "CONSUMABLE",
                command.yearMonth(),
                command.supplier().trim(),
                "CONSUMABLE|" + payableCode,
                null,
                "",
                amount,
                FinanceTexts.ZERO,
                operator,
                FinanceTexts.now()
        ));
        ConsumableMovement movement = new ConsumableMovement(
                FinanceTexts.code("FIN-PUR", sequence),
                item.code(),
                PURCHASE,
                quantity,
                amount,
                command.supplier().trim(),
                null,
                command.yearMonth(),
                payableCode,
                operator,
                FinanceTexts.now()
        );
        finance.saveConsumableMovement(movement);
        return movement;
    }

    public ConsumableMovement issue(IssueConsumableCommand command, String operator) {
        FinanceTexts.month(command.yearMonth());
        ConsumableItem item = requireItem(command.itemCode());
        BigDecimal quantity = FinanceTexts.quantity(command.quantity());
        OnHand onHand = onHand(item.code());
        if (quantity.compareTo(onHand.quantity()) > 0) {
            throw new BusinessException("消耗品结存数量不足");
        }
        BigDecimal cost = onHand.quantity().signum() == 0
                ? FinanceTexts.ZERO
                : FinanceTexts.money(onHand.amount().multiply(quantity).divide(onHand.quantity(), 2, RoundingMode.HALF_UP));
        if (cost.compareTo(onHand.amount()) > 0) {
            cost = FinanceTexts.money(onHand.amount());
        }
        ConsumableMovement movement = new ConsumableMovement(
                FinanceTexts.code("FIN-ISS", sequence),
                item.code(),
                ISSUE,
                quantity,
                cost,
                "",
                command.customerId(),
                command.yearMonth(),
                "",
                operator,
                FinanceTexts.now()
        );
        finance.saveConsumableMovement(movement);
        return movement;
    }

    public List<FixedAsset> assets() {
        return finance.listAssets();
    }

    public List<DepreciationEntry> depreciations(String yearMonth) {
        String month = FinanceTexts.blank(yearMonth);
        if (!month.isEmpty()) {
            FinanceTexts.month(month);
        }
        return finance.listDepreciations().stream()
                .filter(entry -> month.isEmpty() || month.equals(entry.yearMonth()))
                .toList();
    }

    public FixedAsset saveAsset(SaveFixedAssetCommand command, String operator) {
        FinanceTexts.month(command.startMonth());
        BigDecimal original = FinanceTexts.money(command.originalValue());
        BigDecimal residual = FinanceTexts.money(command.residualValue());
        if (residual.compareTo(original) >= 0) {
            throw new BusinessException("残值必须小于原值");
        }
        FixedAsset asset = new FixedAsset(
                FinanceTexts.code("FIN-AST", sequence),
                command.name().trim(),
                command.category().trim(),
                original,
                residual,
                command.startMonth(),
                command.usefulMonths(),
                operator,
                FinanceTexts.now()
        );
        finance.saveAsset(asset);
        return asset;
    }

    public List<DepreciationEntry> depreciate(DepreciationRunCommand command) {
        String yearMonth = FinanceTexts.month(command.yearMonth()).toString();
        List<DepreciationEntry> created = new ArrayList<>();
        for (FixedAsset asset : finance.listAssets()) {
            int index = FinanceTexts.depreciationIndex(asset.startMonth(), yearMonth);
            if (index < 1 || index > asset.usefulMonths()) {
                continue;
            }
            boolean exists = finance.listDepreciations().stream()
                    .anyMatch(entry -> asset.code().equals(entry.assetCode()) && yearMonth.equals(entry.yearMonth()));
            if (exists) {
                continue;
            }
            DepreciationEntry entry = new DepreciationEntry(
                    FinanceTexts.code("FIN-DEP", sequence),
                    asset.code(),
                    yearMonth,
                    depreciationAmount(asset, index)
            );
            finance.saveDepreciation(entry);
            created.add(entry);
        }
        return created;
    }

    public Payable outsource(OutsourceBillCommand command, String operator) {
        FinanceTexts.month(command.yearMonth());
        Payable payable = new Payable(
                FinanceTexts.code("FIN-OS", sequence),
                "OUTSOURCE",
                command.yearMonth(),
                command.supplier().trim(),
                "OUTSOURCE|" + System.nanoTime(),
                command.customerId(),
                FinanceTexts.blank(command.motherOrderCode()),
                FinanceTexts.money(command.amount()),
                FinanceTexts.ZERO,
                operator,
                FinanceTexts.now()
        );
        finance.savePayable(payable);
        return payable;
    }

    public PayablePayment pay(PayablePaymentCommand command, String operator) {
        FinanceTexts.day(command.paidOn());
        Payable payable = finance.findPayable(command.payableCode())
                .orElseThrow(() -> new BusinessException("找不到应付"));
        BigDecimal amount = FinanceTexts.money(command.amount());
        BigDecimal unpaid = payable.amount().subtract(payable.paidAmount());
        if (amount.compareTo(unpaid) > 0) {
            throw new BusinessException("付款金额不能超过尚未支付的余额");
        }
        PayablePayment payment = new PayablePayment(
                FinanceTexts.code("FIN-PMT", sequence),
                payable.code(),
                amount,
                command.paidOn(),
                operator,
                FinanceTexts.now()
        );
        finance.savePayablePayment(payment);
        finance.savePayable(payable.pay(FinanceTexts.money(payable.paidAmount().add(amount))));
        return payment;
    }

    private ConsumableItem requireItem(String code) {
        return finance.findConsumableItem(code.trim())
                .orElseThrow(() -> new BusinessException("找不到消耗品"));
    }

    private OnHand onHand(String itemCode) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (ConsumableMovement movement : finance.listConsumableMovements()) {
            if (!itemCode.equals(movement.itemCode())) {
                continue;
            }
            if (PURCHASE.equals(movement.direction())) {
                quantity = quantity.add(movement.quantity());
                amount = amount.add(movement.amount());
            } else if (ISSUE.equals(movement.direction())) {
                quantity = quantity.subtract(movement.quantity());
                amount = amount.subtract(movement.amount());
            }
        }
        return new OnHand(quantity, amount);
    }

    private BigDecimal depreciationAmount(FixedAsset asset, int index) {
        BigDecimal base = asset.originalValue().subtract(asset.residualValue());
        BigDecimal monthly = base.divide(BigDecimal.valueOf(asset.usefulMonths()), 2, RoundingMode.HALF_UP);
        if (index < asset.usefulMonths()) {
            return monthly;
        }
        BigDecimal prior = monthly.multiply(BigDecimal.valueOf(asset.usefulMonths() - 1L));
        return FinanceTexts.money(base.subtract(prior));
    }

    private record OnHand(BigDecimal quantity, BigDecimal amount) {
    }
}
