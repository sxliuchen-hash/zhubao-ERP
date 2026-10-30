package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CustomerStatementCutoff;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.app.CustomerPortalIdentityService;
import com.zhubao.erp.security.domain.OperatorContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 按指定截止时点只读汇总收料、出厂和损耗。不写出库、不生成账单。
 * 截止日后的变动单独列出。在厂与在外、客户账单都还没有核定口径，不填 0。
 */
@Service
public class CustomerStatementCutoffQuery {

    static final String FACTORY_OUTSIDE_PENDING = "截止日的在厂与在外尚未核定，不把当前结存或 0 写成截止日数量";
    static final String BILLS_UNDECIDED = "账单可见范围尚未核定";

    private final StockLedgerRepository stocks;
    private final CustomerPortalIdentityService identities;

    public CustomerStatementCutoffQuery(StockLedgerRepository stocks, CustomerPortalIdentityService identities) {
        this.stocks = stocks;
        this.identities = identities;
    }

    public CustomerStatementCutoff statement(OperatorContext operator, Instant cutoff) {
        if (cutoff == null) {
            throw new BusinessException("请指定截止日");
        }
        long customerId = Long.parseLong(identities.portalOperator(operator == null ? "" : operator.username())
                .customerScope().get(0));
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal shipped = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        boolean lossWeightUnknown = false;
        boolean lossWeightKnown = false;
        List<String> afterCutoff = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        boolean withheldSampling = false;
        boolean withheldGoldwork = false;
        for (StockLedgerEntry entry : stocks.findAllLedgers()) {
            if (entry == null || !ownedBy(entry, customerId)) {
                continue;
            }
            BusinessKind kind = kindOf(entry);
            if (kind == BusinessKind.SAMPLING) {
                withheldSampling = true;
                continue;
            }
            if (kind == BusinessKind.GOLDWORK) {
                withheldGoldwork = true;
                continue;
            }
            if (isSensitive(entry)) {
                pending.add("内部费用不在客户对账范围内");
                continue;
            }
            if (entry.bizTime() == null || entry.bizTime().isAfter(cutoff)) {
                afterCutoff.add(movementText(entry) + " 未计入本次截止日");
                continue;
            }
            if (entry.quantity() == null) {
                pending.add(movementText(entry) + " 口径待核，未计入收料、出厂或损耗");
                continue;
            }
            if (isLoss(entry)) {
                loss = loss.add(entry.quantity());
                if (entry.weight() == null) {
                    lossWeightUnknown = true;
                } else {
                    lossWeightKnown = true;
                }
                continue;
            }
            if (isReceipt(entry)) {
                received = received.add(entry.quantity());
                continue;
            }
            if (isShipment(entry)) {
                shipped = shipped.add(entry.quantity());
                continue;
            }
            pending.add(movementText(entry) + " 口径待核，未计入收料、出厂或损耗");
        }
        if (withheldSampling) {
            pending.add("打样库存适配尚未实现，相关流水未计入，也不作为空对账");
        }
        if (withheldGoldwork) {
            pending.add("金工库存适配尚未实现，相关流水未计入，也不作为空对账");
        }
        String lossWeight = !lossWeightKnown || lossWeightUnknown ? "未称" : "已称";
        CustomerStatementCutoff statement = new CustomerStatementCutoff(
                customerId, cutoff, received, shipped, loss, lossWeight,
                FACTORY_OUTSIDE_PENDING, BILLS_UNDECIDED, afterCutoff, pending, "");
        return new CustomerStatementCutoff(
                statement.customerId(), statement.cutoff(), statement.receivedQuantity(), statement.shippedQuantity(),
                statement.lossQuantity(), statement.lossWeight(), statement.factoryAndOutside(), statement.bills(),
                statement.afterCutoff(), statement.pending(), export(statement));
    }

    /** 对账确认不会出库或出账。 */
    public void acknowledge(OperatorContext operator, Instant cutoff) {
        statement(operator, cutoff);
        throw new BusinessException("对账不能自动出库或出账");
    }

    public static String export(CustomerStatementCutoff statement) {
        StringBuilder text = new StringBuilder();
        text.append("截止时点,").append(statement.cutoff()).append('\n');
        text.append("收料,").append(statement.receivedQuantity().toPlainString()).append('\n');
        text.append("出厂,").append(statement.shippedQuantity().toPlainString()).append('\n');
        text.append("损耗,").append(statement.lossQuantity().toPlainString()).append('\n');
        text.append("损耗重量,").append(statement.lossWeight()).append('\n');
        text.append("在厂在外,").append(statement.factoryAndOutside()).append('\n');
        text.append("账单,").append(statement.bills()).append('\n');
        for (String line : statement.afterCutoff()) {
            text.append("截止后,").append(line).append('\n');
        }
        for (String line : statement.pending()) {
            text.append("待核,").append(line).append('\n');
        }
        return text.toString();
    }

    private static boolean ownedBy(StockLedgerEntry entry, long customerId) {
        return owned(entry.to(), customerId) || owned(entry.from(), customerId);
    }

    private static boolean owned(StockDimension dimension, long customerId) {
        return dimension != null && dimension.ownerType() == OwnerType.CUSTOMER
                && dimension.ownerCustomerId() != null && dimension.ownerCustomerId() == customerId;
    }

    private static boolean isReceipt(StockLedgerEntry entry) {
        String type = entry.documentType();
        return "inbound".equals(type) || "prep_receive".equals(type) || "receipt".equals(type);
    }

    private static boolean isShipment(StockLedgerEntry entry) {
        if ("shipment".equals(entry.documentType()) || "outbound".equals(entry.documentType())
                || "delivery".equals(entry.documentType())) {
            return true;
        }
        return entry.to() != null && entry.to().stockStatus() == StockStatus.SHIPPED;
    }

    private static boolean isSensitive(StockLedgerEntry entry) {
        String type = entry.documentType() == null ? "" : entry.documentType().toLowerCase(java.util.Locale.ROOT);
        return type.contains("payroll") || type.contains("wage") || type.contains("cost")
                || type.contains("工资") || type.contains("成本") || type.contains("工费");
    }

    private static boolean isLoss(StockLedgerEntry entry) {
        return "process_loss".equals(entry.documentType());
    }

    private static String movementText(StockLedgerEntry entry) {
        String type = entry.documentType() == null ? "未命名" : entry.documentType();
        String quantity = entry.quantity() == null ? "数量待核" : entry.quantity().toPlainString();
        return type + " " + quantity;
    }

    private static BusinessKind kindOf(StockLedgerEntry entry) {
        for (StockDimension dimension : new StockDimension[] { entry.to(), entry.from() }) {
            if (dimension == null) {
                continue;
            }
            for (String code : List.of(dimension.workOrderCode(), dimension.motherOrderCode())) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                try {
                    return BusinessTargetReference.fromCode(code).kind();
                } catch (RuntimeException ex) {
                    return BusinessKind.PROCESSING;
                }
            }
        }
        return BusinessKind.PROCESSING;
    }
}
