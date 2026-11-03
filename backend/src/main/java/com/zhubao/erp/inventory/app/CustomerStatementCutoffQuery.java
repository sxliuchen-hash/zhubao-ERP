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
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按指定截止时点只读汇总收料、出厂和损耗。不写出库、不生成账单。
 * 截止日后的变动单独列出。在厂与在外还没有核定口径，不填 0。客户账单本版不开放，也不写成 0。
 */
@Service
public class CustomerStatementCutoffQuery {

    static final String FACTORY_OUTSIDE_PENDING = "本版不显示截止日的在厂和在外，不把当前结存或 0 写成截止日数量";
    static final String BILLS_NOT_OPEN = "本版不向客户开放账单";

    private final StockLedgerRepository stocks;
    private final CustomerPortalIdentityService identities;
    private final CustomerStockUnitSource units;

    public CustomerStatementCutoffQuery(StockLedgerRepository stocks, CustomerPortalIdentityService identities) {
        this(stocks, identities, null);
    }

    @Autowired
    public CustomerStatementCutoffQuery(StockLedgerRepository stocks, CustomerPortalIdentityService identities, CustomerStockUnitSource units) {
        this.stocks = stocks;
        this.identities = identities;
        this.units = units;
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
        boolean unknownUnit = false;
        Map<String, CustomerStatementCutoff.QuantityGroup> groups = new LinkedHashMap<>();
        List<StockLedgerEntry> ledgers = stocks.findAllLedgers();
        for (StockLedgerEntry entry : ledgers) {
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
            StockDimension dimension = owned(entry.to(), customerId) ? entry.to() : entry.from();
            String unit = units == null ? "" : units.unitOf(dimension.motherOrderCode());
            if (unit == null || unit.isBlank()) {
                unknownUnit = true;
                pending.add(movementText(entry) + " 数量单位待核，未合计");
                continue;
            }
            String source = dimension.workOrderCode() == null || dimension.workOrderCode().isBlank()
                    ? dimension.motherOrderCode() : dimension.workOrderCode();
            String groupKey = dimension.materialCode() + "\n" + source + "\n" + unit;
            var group = groups.getOrDefault(groupKey, new CustomerStatementCutoff.QuantityGroup(dimension.materialCode(), source, unit,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            if (isLoss(entry)) {
                loss = loss.add(entry.quantity());
                groups.put(groupKey, new CustomerStatementCutoff.QuantityGroup(group.materialCode(), source, unit,
                        group.receivedQuantity(), group.shippedQuantity(), group.lossQuantity().add(entry.quantity())));
                if (entry.weight() == null) {
                    lossWeightUnknown = true;
                } else {
                    lossWeightKnown = true;
                }
                continue;
            }
            if (isReceipt(entry) || "inbound_reversal".equals(entry.documentType())) {
                boolean reversal = "inbound_reversal".equals(entry.documentType());
                if (reversal && ledgers.stream().noneMatch(original -> original.code().equals(entry.sourceLedgerCode())
                        && isReceipt(original) && ownedBy(original, customerId) && original.bizTime() != null && !original.bizTime().isAfter(cutoff))) {
                    pending.add(movementText(entry) + " 原收货冲正依据待核，未合计");
                    continue;
                }
                BigDecimal change = reversal ? entry.quantity().negate() : entry.quantity();
                received = received.add(change);
                groups.put(groupKey, new CustomerStatementCutoff.QuantityGroup(group.materialCode(), source, unit,
                        group.receivedQuantity().add(change), group.shippedQuantity(), group.lossQuantity()));
                continue;
            }
            if (isShipment(entry)) {
                BigDecimal change = entry.from() != null && entry.from().stockStatus() == StockStatus.SHIPPED
                        ? entry.quantity().negate() : entry.quantity();
                shipped = shipped.add(change);
                groups.put(groupKey, new CustomerStatementCutoff.QuantityGroup(group.materialCode(), source, unit,
                        group.receivedQuantity(), group.shippedQuantity().add(change), group.lossQuantity()));
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
        if (unknownUnit || groups.values().stream().map(CustomerStatementCutoff.QuantityGroup::unit).distinct().count() > 1) {
            received = null;
            shipped = null;
            loss = null;
        }
        CustomerStatementCutoff statement = new CustomerStatementCutoff(
                customerId, cutoff, received, shipped, loss, lossWeight,
                FACTORY_OUTSIDE_PENDING, BILLS_NOT_OPEN, afterCutoff, pending, "", List.copyOf(groups.values()));
        return new CustomerStatementCutoff(
                statement.customerId(), statement.cutoff(), statement.receivedQuantity(), statement.shippedQuantity(),
                statement.lossQuantity(), statement.lossWeight(), statement.factoryAndOutside(), statement.bills(),
                statement.afterCutoff(), statement.pending(), export(statement), statement.quantities());
    }

    /** 对账确认不会出库或出账。 */
    public void acknowledge(OperatorContext operator, Instant cutoff) {
        statement(operator, cutoff);
        throw new BusinessException("对账不能自动出库或出账");
    }

    public static String export(CustomerStatementCutoff statement) {
        StringBuilder text = new StringBuilder();
        text.append("截止时点,").append(statement.cutoff()).append('\n');
        text.append("收料,").append(total(statement.receivedQuantity())).append('\n');
        text.append("出厂,").append(total(statement.shippedQuantity())).append('\n');
        text.append("损耗,").append(total(statement.lossQuantity())).append('\n');
        text.append("材质,来源,单位,收料,出厂,损耗\n");
        for (var group : statement.quantities()) {
            text.append(csv(group.materialCode())).append(',').append(csv(group.sourceCode())).append(',').append(csv(group.unit())).append(',')
                    .append(group.receivedQuantity().toPlainString()).append(',').append(group.shippedQuantity().toPlainString()).append(',')
                    .append(group.lossQuantity().toPlainString()).append('\n');
        }
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

    private static String total(BigDecimal quantity) {
        return quantity == null ? "按单位分别核对" : quantity.toPlainString();
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.matches("^[=+@-].*")) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
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
        return "inbound_confirm".equals(type) || "inbound".equals(type) || "prep_receive".equals(type) || "receipt".equals(type);
    }

    private static boolean isShipment(StockLedgerEntry entry) {
        if ("shipment".equals(entry.documentType()) || "outbound".equals(entry.documentType())
                || "delivery".equals(entry.documentType())) {
            return true;
        }
        return entry.to() != null && entry.to().stockStatus() == StockStatus.SHIPPED
                || entry.from() != null && entry.from().stockStatus() == StockStatus.SHIPPED;
    }

    private static boolean isSensitive(StockLedgerEntry entry) {
        String type = entry.documentType() == null ? "" : entry.documentType().toLowerCase(java.util.Locale.ROOT);
        return type.contains("payroll") || type.contains("wage") || type.contains("cost")
                || type.contains("工资") || type.contains("成本") || type.contains("工费");
    }

    private static boolean isLoss(StockLedgerEntry entry) {
        return "process_loss".equals(entry.documentType()) || "outsourcing_loss".equals(entry.documentType());
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
            for (String code : new String[] {dimension.workOrderCode(), dimension.motherOrderCode()}) {
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
