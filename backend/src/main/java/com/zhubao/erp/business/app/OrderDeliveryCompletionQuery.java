package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildLineAllocation;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ExecutionRound;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment.ChildDelivery;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment.DeliveryBlock;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment.LaborFact;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment.QuantityFact;
import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.OrderRequirementVersion;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessBatchHandoff;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityReworkReport;
import com.zhubao.erp.business.domain.QualityRound;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.ReceiptLine;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 判断一张生产目标是否已经交齐。进度 100%、仅有出货单或成品余额为零都不等于交货完成。
 * 本服务不写库存、进度或财务。
 */
@Service
public class OrderDeliveryCompletionQuery {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

    private final BusinessDocumentRepository documents;
    private final StockLedgerRepository stocks;
    private final OrderRequirementRepository requirements;

    public OrderDeliveryCompletionQuery(
            BusinessDocumentRepository documents,
            StockLedgerRepository stocks,
            OrderRequirementRepository requirements
    ) {
        this.documents = documents;
        this.stocks = stocks;
        this.requirements = requirements;
    }

    public OrderDeliveryAssessment assess(String targetType, String targetCode) {
        return assess(targetType, targetCode, null, false);
    }

    /**
     * @param lock 确认结单时为真，必须带着工作单元，锁后重读当前事实
     */
    public OrderDeliveryAssessment assess(String targetType, String targetCode, WorkSession session, boolean lock) {
        if (lock && session == null) {
            throw new BusinessException("确认交货必须在工作单元中重新读取");
        }
        Read read = new Read(session, lock);
        String type = targetType == null ? "" : targetType.trim();
        String code = targetCode == null ? "" : targetCode.trim();
        if (OrderDeliveryAssessment.MOTHER.equals(type)) {
            return assessMother(code, read);
        }
        if (OrderDeliveryAssessment.CHILD.equals(type)) {
            return assessChild(code, read);
        }
        Bag bag = new Bag();
        bag.hash("type|" + type + "|code|" + code + "|unknown");
        bag.block("UNKNOWN_TARGET", "无法识别结单目标", true);
        return finish(type, code, "", null, "", false, bag, List.of());
    }

    private OrderDeliveryAssessment assessMother(String code, Read read) {
        Bag bag = new Bag();
        bag.hash("type|MOTHER|code|" + code);
        Optional<MotherOrderRecord> loaded = mother(code, read);
        if (loaded.isEmpty()) {
            bag.block("UNKNOWN_TARGET", "找不到母单", true);
            return finish(OrderDeliveryAssessment.MOTHER, code, code, null, "", false, bag, List.of());
        }
        MotherOrderRecord mother = loaded.get();
        bag.hash("customer|" + mother.customerId());
        if (recycledMother(code, read)) {
            bag.block("RECYCLED", "母单在回收站，不能结单", false);
            return finish(OrderDeliveryAssessment.MOTHER, code, code, mother.customerId(), mother.customerName(), false, bag, List.of());
        }
        List<ChildWorkOrderRecord> children = children(code, read);
        Optional<MotherSplitState> split = split(code, read);
        String decision = split.map(MotherSplitState::decision).orElse("");
        bag.hash("split|" + decision + "|children|" + children.size());
        if (!children.isEmpty() || MotherSplitState.SPLIT.equals(decision)) {
            return rollup(mother, children, decision, read, bag);
        }
        if (!MotherSplitState.KEEP.equals(decision)) {
            bag.block("DECISION_MISSING", "尚未决定不拆或拆单，不能按母单结单", false);
            return finish(OrderDeliveryAssessment.MOTHER, code, code, mother.customerId(), mother.customerName(), false, bag, List.of());
        }
        return assessProduction(OrderDeliveryAssessment.MOTHER, mother.code(), mother, null, read, bag);
    }

    private OrderDeliveryAssessment assessChild(String code, Read read) {
        Bag bag = new Bag();
        bag.hash("type|CHILD|code|" + code);
        Optional<ChildWorkOrderRecord> loaded = child(code, read);
        if (loaded.isEmpty()) {
            bag.block("UNKNOWN_TARGET", "找不到子单", true);
            return finish(OrderDeliveryAssessment.CHILD, code, "", null, "", false, bag, List.of());
        }
        ChildWorkOrderRecord child = loaded.get();
        Optional<MotherOrderRecord> mother = mother(child.motherOrderCode(), read);
        if (mother.isEmpty()) {
            bag.block("UNKNOWN_TARGET", "子单找不到母单", true);
            return finish(OrderDeliveryAssessment.CHILD, code, child.motherOrderCode(), child.customerId(), child.customerName(), false, bag, List.of());
        }
        if (CustomerScopeRules.identityConflicts(child.customerId(), mother.get().customerId())) {
            bag.block("CUSTOMER_CONFLICT", "母单和子单客户不一致", true);
        }
        if (recycledChild(code, read) || recycledMother(child.motherOrderCode(), read)) {
            bag.block("RECYCLED", "单据在回收站，不能结单", false);
            return finish(OrderDeliveryAssessment.CHILD, code, child.motherOrderCode(), child.customerId(), child.customerName(), false, bag, List.of());
        }
        return assessProduction(OrderDeliveryAssessment.CHILD, child.code(), mother.get(), child, read, bag);
    }

    private OrderDeliveryAssessment rollup(
            MotherOrderRecord mother,
            List<ChildWorkOrderRecord> children,
            String decision,
            Read read,
            Bag bag
    ) {
        if (children.isEmpty()) {
            bag.block("SPLIT_EMPTY", "已决定拆单但没有子单", true);
        }
        if (!MotherSplitState.SPLIT.equals(decision)) {
            bag.block("SPLIT_MISMATCH", "存在子单但拆单决定不是已拆", true);
        }
        List<ChildDelivery> summaries = new ArrayList<>();
        Map<String, BigDecimal> covered = new LinkedHashMap<>();
        for (ChildWorkOrderRecord child : children) {
            if (recycledChild(child.code(), read)) {
                bag.block("RECYCLED_CHILD", "回收子单 " + child.code() + " 仍在应交范围，不能当成已交或从数量中扣除", true);
                summaries.add(new ChildDelivery(child.code(), false, OrderDeliveryAssessment.NOT_COMPLETE, "", List.of(
                        new DeliveryBlock("RECYCLED", "子单在回收站", false)
                )));
                bag.hash("child|" + child.code() + "|recycled");
                continue;
            }
            OrderDeliveryAssessment childView = assessChild(child.code(), read);
            summaries.add(new ChildDelivery(childView.targetCode(), childView.eligible(), childView.deliveryState(),
                    childView.deliveryRevision(), childView.blocks()));
            bag.hash("child|" + child.code() + "|" + childView.deliveryState() + "|" + childView.deliveryRevision());
            if (childView.blocks().stream().anyMatch(DeliveryBlock::review)) {
                bag.block("CHILD_REVIEW", "子单 " + child.code() + " 交货待核", true);
            } else if (!childView.eligible()) {
                bag.block("CHILD_OPEN", "子单 " + child.code() + " 尚未交齐", false);
            }
            for (QuantityFact fact : childView.quantities()) {
                if (QuantityFact.TARGET.equals(fact.role()) && fact.required() != null) {
                    covered.merge(key(fact.materialCode(), fact.unit()), scale(fact.required()), BigDecimal::add);
                }
            }
        }
        for (QuantityFact need : requiredOf(mother, null, read, bag)) {
            BigDecimal used = covered.getOrDefault(key(need.materialCode(), need.unit()), ZERO);
            BigDecimal left = scale(need.required()).subtract(used);
            bag.hash("remainder|" + need.materialCode() + "|" + need.unit() + "|" + plain(left));
            if (left.signum() > 0) {
                bag.quantities.add(new QuantityFact(QuantityFact.REMAINDER, need.materialCode(), need.unit(), left, ZERO));
                bag.block("UNSPLIT_REMAINDER", "母单还有未拆出的 " + plain(left) + need.unit(), false);
            } else if (left.signum() < 0) {
                bag.block("OVER_ALLOCATED", "子单分配超过母单 " + need.materialCode(), true);
            }
        }
        return finish(OrderDeliveryAssessment.MOTHER, mother.code(), mother.code(), mother.customerId(), mother.customerName(),
                false, bag, summaries);
    }

    private OrderDeliveryAssessment assessProduction(
            String type,
            String code,
            MotherOrderRecord mother,
            ChildWorkOrderRecord child,
            Read read,
            Bag bag
    ) {
        if (stocks == null) {
            bag.block("STOCK_UNAVAILABLE", "缺少库存台账，不能认定交货完成", true);
        }
        List<QuantityFact> required = requiredOf(mother, child, read, bag);
        bag.quantities.addAll(required);
        Set<String> related = relatedOrders(code, mother.code(), read, bag);
        inspectRoute(code, mother, child, read, bag, singleQuantity(required));
        inspectRounds(code, mother.code(), read, bag);
        inspectQuality(code, read, bag);
        if (stocks != null) {
            inspectShipment(mother.code(), related, required, read, bag, OrderDeliveryAssessment.MOTHER.equals(type));
        }
        return finish(type, code, mother.code(), mother.customerId(), blank(child == null ? mother.customerName() : child.customerName()),
                true, bag, List.of());
    }

    private void inspectRoute(
            String code,
            MotherOrderRecord mother,
            ChildWorkOrderRecord child,
            Read read,
            Bag bag,
            BigDecimal orderQuantity
    ) {
        List<String> original = child == null ? WorkshopProcesses.DEFAULT_ORDER : WorkshopProcesses.split(child.processes());
        if (child != null && original.isEmpty()) {
            bag.block("ROUTE_MISSING", "子单没有工序路线", true);
        }
        List<String> route = route(mother.code(), child == null ? "" : child.code(), original, read, bag);
        bag.hash("route|" + String.join(",", route));
        List<ProcessStationHandoff> handoffs = documents.listProcessStationHandoffs(code, read.session(), read.lock());
        List<ProcessBatchHandoff> batches = documents.supportsProcessBatches()
                ? documents.listProcessBatches(code, read.session())
                : List.of();
        for (String process : route) {
            ProcessStationHandoff handoff = handoffs.stream().filter(row -> process.equals(row.processName())).findFirst().orElse(null);
            bag.hash("handoff|" + code + "|" + process + "|" + (handoff == null ? "" : handoff.createdAt()));
            if (handoff == null) {
                bag.block("PROCESS_OPEN", "工序 " + process + " 尚未正式交工", false);
            }
            BigDecimal batchQty = ZERO;
            boolean anyBatch = false;
            for (ProcessBatchHandoff batch : batches) {
                if (!process.equals(batch.processName())) {
                    continue;
                }
                anyBatch = true;
                BigDecimal quantity = batch.quantity() == null ? null : scale(batch.quantity());
                bag.hash("batch|" + batch.batchCode() + "|" + plain(quantity));
                if (quantity == null || quantity.signum() < 0) {
                    bag.block("BATCH_INVALID", "交工批次 " + batch.batchCode() + " 数量无效", true);
                    continue;
                }
                if (quantity.signum() == 0) {
                    bag.labor.add(new LaborFact("BATCH|" + batch.batchCode(), code, process, batch.batchCode(), ZERO,
                            true, false, false, batch.operator()));
                    continue;
                }
                batchQty = batchQty.add(quantity);
                bag.labor.add(new LaborFact("BATCH|" + batch.batchCode(), code, process, batch.batchCode(), quantity,
                        false, false, false, batch.operator()));
            }
            if (orderQuantity != null && batchQty.compareTo(orderQuantity) > 0) {
                bag.block("BATCH_OVER", "工序 " + process + " 的分批数量超过订单数量", true);
            }
            if (handoff != null && !anyBatch) {
                bag.labor.add(new LaborFact("PROCESS|" + code + "|" + process, code, process, "", orderQuantity,
                        false, orderQuantity == null, true, handoff.operator()));
            } else if (handoff != null && orderQuantity != null && batchQty.compareTo(orderQuantity) < 0) {
                bag.labor.add(new LaborFact("GAP|" + code + "|" + process, code, process, "", orderQuantity.subtract(batchQty),
                        false, false, false, ""));
            }
        }
    }

    private void inspectRounds(String targetCode, String motherCode, Read read, Bag bag) {
        if (requirements == null) {
            return;
        }
        for (ExecutionRound round : requirements.listByMother(motherCode, read.session(), read.lock())) {
            if (!targetCode.equals(round.sourceWorkOrderCode())) {
                continue;
            }
            bag.hash("round|" + round.roundWorkOrderCode() + "|" + round.processName());
            List<ProcessStationHandoff> handoffs = documents.listProcessStationHandoffs(round.roundWorkOrderCode(), read.session(), read.lock());
            boolean handed = handoffs.stream().anyMatch(row -> round.processName().equals(row.processName()));
            boolean qualityClosed = qualityClosed(round.roundWorkOrderCode(), read, bag, false);
            if (!handed || !qualityClosed) {
                bag.block("ROUND_OPEN", "执行轮次 " + round.roundWorkOrderCode() + " 尚未处理完", false);
            }
            List<ProcessBatchHandoff> batches = documents.supportsProcessBatches()
                    ? documents.listProcessBatches(round.roundWorkOrderCode(), read.session())
                    : List.of();
            boolean positive = false;
            for (ProcessBatchHandoff batch : batches) {
                if (batch.quantity() != null && batch.quantity().signum() > 0) {
                    positive = true;
                    bag.labor.add(new LaborFact("BATCH|" + batch.batchCode(), round.roundWorkOrderCode(), round.processName(),
                            batch.batchCode(), scale(batch.quantity()), false, false, false, batch.operator()));
                }
            }
            if (!positive) {
                String operator = handoffs.stream().filter(row -> round.processName().equals(row.processName()))
                        .map(ProcessStationHandoff::operator).findFirst().orElse("");
                bag.labor.add(new LaborFact("ROUND|" + round.roundWorkOrderCode(), round.roundWorkOrderCode(), round.processName(),
                        "", null, false, true, true, operator));
            }
        }
    }

    private void inspectQuality(String orderCode, Read read, Bag bag) {
        qualityClosed(orderCode, read, bag, true);
    }

    private boolean qualityClosed(String orderCode, Read read, Bag bag, boolean recordBlock) {
        List<QualityRound> rounds = documents.listQualityRounds(orderCode, read.session(), read.lock());
        QualityRound latest = rounds.stream().max(Comparator.comparingInt(QualityRound::roundNo)).orElse(null);
        if (latest != null) {
            bag.hash("quality|" + latest.roundCode() + "|" + latest.outcome() + "|" + latest.frozen());
            return judgeQuality(latest.outcome(), latest.frozen(), latest.reworkOrderCode(), latest.roundType(), read, bag, recordBlock);
        }
        Optional<QualityRequirementCheck> check = documents.findQualityRequirementCheck(orderCode, read.session(), read.lock());
        if (check.isEmpty()) {
            bag.hash("quality|" + orderCode + "|missing");
            if (recordBlock) {
                bag.block("QUALITY_MISSING", "还没有质检结论", false);
            }
            return false;
        }
        QualityRequirementCheck row = check.get();
        bag.hash("quality|" + row.qualityCaseCode() + "|" + row.outcome() + "|" + row.frozen());
        return judgeQuality(row.outcome(), row.frozen(), row.reworkOrderCode(), "CHECK", read, bag, recordBlock);
    }

    private boolean judgeQuality(
            String outcome, boolean frozen, String reworkCode, String roundType, Read read, Bag bag, boolean recordBlock
    ) {
        boolean pass = !frozen && (QualityVerdicts.PASS.equals(outcome) || QualityVerdicts.CONCESSION.equals(outcome));
        if (!pass && recordBlock) {
            bag.block("QUALITY_OPEN", frozen ? "质检仍处于冻结" : "质检尚未合格或让步放行", false);
        }
        boolean reworkOpen = reworkCode != null && !reworkCode.isBlank() && !"RECHECK".equals(roundType);
        if (reworkOpen) {
            Optional<QualityReworkReport> report = documents.findQualityReworkReport(reworkCode, read.session(), read.lock());
            bag.hash("rework|" + reworkCode + "|" + report.isPresent());
            if (recordBlock) {
                bag.block("REWORK_OPEN", report.isEmpty() ? "返工还没有报工" : "返工后的复检尚未完成", false);
            }
        }
        return pass && !reworkOpen;
    }

    private void inspectShipment(String motherCode, Set<String> related, List<QuantityFact> required, Read read, Bag bag, boolean motherTarget) {
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        Set<String> ledgersUsed = new LinkedHashSet<>();
        List<StockLedgerEntry> ledgers = stocks.ledgersForMother(motherCode, read.session(), read.lock());
        Map<String, StockLedgerEntry> ledgerByCode = new LinkedHashMap<>();
        for (StockLedgerEntry entry : ledgers) {
            if (entry.code() != null) {
                ledgerByCode.put(entry.code(), entry);
            }
        }
        for (OutboundOrderRecord order : documents.listOutboundOrdersForMother(motherCode, read.session(), read.lock())) {
            String work = order.workOrderCode() == null ? "" : order.workOrderCode().trim();
            if (work.isEmpty()) {
                bag.block("SOURCE_AMBIGUOUS", "出货单 " + order.code() + " 没有工单，不能归属交货", true);
                continue;
            }
            if (!related.contains(work)) {
                continue;
            }
            bag.hash("outbound|" + order.code() + "|" + order.materialCode() + "|" + order.quantityUnit() + "|" + plain(order.quantity()) + "|" + order.status());
            if (!"SHIPPED".equals(order.status())) {
                bag.block("OUTBOUND_STATUS", "出货单 " + order.code() + " 不是已发货", true);
                continue;
            }
            if (order.quantity() == null || order.quantity().signum() < 0 || order.quantityUnit() == null || order.quantityUnit().isBlank()) {
                bag.block("UNIT_MISMATCH", "出货单 " + order.code() + " 的数量或单位无效", true);
                continue;
            }
            boolean knownUnit = required.stream().anyMatch(fact -> fact.materialCode().equals(order.materialCode())
                    && fact.unit().equals(order.quantityUnit()));
            if (!knownUnit) {
                bag.block("UNIT_MISMATCH", "出货单 " + order.code() + " 的单位和订单不一致", true);
                continue;
            }
            if (order.ledgerCode() == null || order.ledgerCode().isBlank() || !ledgerByCode.containsKey(order.ledgerCode())) {
                bag.block("LEDGER_MISSING", "出货单 " + order.code() + " 对不上库存流水", true);
                continue;
            }
            if (!ledgersUsed.add(order.ledgerCode())) {
                bag.block("LEDGER_DUPLICATE", "出货单重复使用流水 " + order.ledgerCode(), true);
            }
            StockLedgerEntry ledger = ledgerByCode.get(order.ledgerCode());
            if (ledger.documentCode() != null && !ledger.documentCode().equals(order.code())) {
                bag.block("LEDGER_MISMATCH", "出货单 " + order.code() + " 和流水单据号不一致", true);
            }
            net.merge(key(order.materialCode(), order.quantityUnit()), scale(order.quantity()), BigDecimal::add);
        }
        for (StockLedgerEntry entry : ledgers) {
            if (!"customer_return".equals(entry.documentType())) {
                continue;
            }
            String work = workOf(entry);
            if (!related.contains(work)) {
                continue;
            }
            bag.hash("return|" + entry.code() + "|" + plain(entry.quantity()));
            String material = entry.to() != null ? entry.to().materialCode() : entry.from() == null ? "" : entry.from().materialCode();
            String unit = unitFor(required, material);
            if (unit.isEmpty()) {
                bag.block("UNIT_MISMATCH", "退货 " + entry.documentCode() + " 对不上订单材质", true);
                continue;
            }
            net.merge(key(material, unit), scale(entry.quantity()).negate(), BigDecimal::add);
        }
        Map<String, BigDecimal> shippedBalance = new LinkedHashMap<>();
        for (StockBalance balance : stocks.balancesForMother(motherCode, read.session(), read.lock())) {
            StockDimension dimension = balance.dimension();
            if (dimension == null || !covers(dimension, related, motherTarget)) {
                continue;
            }
            BigDecimal quantity = balance.quantity() == null ? ZERO : scale(balance.quantity());
            if (quantity.signum() < 0) {
                bag.block("NEGATIVE", "库存数量为负：" + dimension.materialCode(), true);
                continue;
            }
            if (quantity.signum() == 0) {
                continue;
            }
            bag.hash("balance|" + dimension.workOrderCode() + "|" + dimension.materialCode() + "|" + dimension.stockStatus() + "|" + plain(quantity));
            if (dimension.stockStatus() == StockStatus.SHIPPED) {
                shippedBalance.merge(dimension.materialCode(), quantity, BigDecimal::add);
                continue;
            }
            if (dimension.stockStatus() == StockStatus.SCRAP) {
                bag.block("SCRAP_ON_HAND", "仍有报废库存，需先完成实物处置", true);
            } else if (dimension.stockStatus() == StockStatus.FROZEN || dimension.stockStatus() == StockStatus.REWORK) {
                bag.block("QUALITY_STOCK", "仍有冻结或返工库存", true);
            } else {
                bag.block("STOCK_OPEN", "仍有未发出的" + dimension.stockStatus().name() + "库存", false);
            }
        }
        for (QuantityFact fact : required) {
            BigDecimal shipped = net.getOrDefault(key(fact.materialCode(), fact.unit()), ZERO);
            int compared = shipped.compareTo(scale(fact.required()));
            if (compared < 0) {
                bag.block("QTY_SHORT", fact.materialCode() + " 净已发 " + plain(shipped) + "，应交 " + plain(fact.required()), false);
            } else if (compared > 0) {
                bag.block("QTY_OVER", fact.materialCode() + " 净已发超过应交数量", true);
            }
            BigDecimal balance = shippedBalance.getOrDefault(fact.materialCode(), ZERO);
            if (balance.compareTo(shipped) != 0) {
                bag.block("BALANCE_MISMATCH", fact.materialCode() + " 的已发库存和出货净额不一致", true);
            }
        }
    }

    private List<QuantityFact> requiredOf(MotherOrderRecord mother, ChildWorkOrderRecord child, Read read, Bag bag) {
        List<ReceiptLine> lines = receiptLines(mother, read);
        if (child == null) {
            return fromLines(lines, mother.materialCode(), mother.quantityUnit(), mother.quantity(), bag);
        }
        List<ChildLineAllocation> allocations = documents.listChildAllocations(mother.code(), read.session()).stream()
                .filter(row -> child.code().equals(row.childOrderCode()))
                .toList();
        if (allocations.isEmpty()) {
            return fromLines(List.of(), child.materialCode(), child.quantityUnit(), child.quantity(), bag);
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();
        Map<String, String> materials = new LinkedHashMap<>();
        for (ChildLineAllocation allocation : allocations) {
            String unit = "";
            for (ReceiptLine line : lines) {
                if (line.lineNo() == allocation.sourceLineNo() && allocation.materialCode().equals(line.materialCode())) {
                    unit = line.quantityUnit() == null ? "" : line.quantityUnit();
                }
            }
            if (unit.isBlank()) {
                bag.block("UNIT_MISMATCH", "子单来源行缺少数量单位", true);
                unit = child.quantityUnit() == null ? "" : child.quantityUnit();
            }
            String material = allocation.materialCode();
            totals.merge(key(material, unit), scale(allocation.quantity()), BigDecimal::add);
            units.put(key(material, unit), unit);
            materials.put(key(material, unit), material);
        }
        List<QuantityFact> facts = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            if (entry.getValue().signum() <= 0) {
                bag.block("NEGATIVE", "应交数量无效", true);
            }
            facts.add(new QuantityFact(QuantityFact.TARGET, materials.get(entry.getKey()), units.get(entry.getKey()), entry.getValue(), ZERO));
        }
        return facts;
    }

    private List<QuantityFact> fromLines(List<ReceiptLine> lines, String material, String unit, BigDecimal quantity, Bag bag) {
        if (lines == null || lines.isEmpty()) {
            return List.of(single(material, unit, quantity, bag));
        }
        List<QuantityFact> facts = new ArrayList<>();
        for (ReceiptLine line : lines) {
            facts.add(single(line.materialCode(), line.quantityUnit(), line.quantity(), bag));
        }
        return facts;
    }

    private QuantityFact single(String material, String unit, BigDecimal quantity, Bag bag) {
        if (material == null || material.isBlank() || unit == null || unit.isBlank() || quantity == null || quantity.signum() <= 0) {
            bag.block("QTY_INVALID", "订单数量、材质或单位不完整", true);
            return new QuantityFact(QuantityFact.TARGET, material == null ? "" : material, unit == null ? "" : unit, ZERO, ZERO);
        }
        return new QuantityFact(QuantityFact.TARGET, material, unit, scale(quantity), ZERO);
    }

    private Set<String> relatedOrders(String targetCode, String motherCode, Read read, Bag bag) {
        Set<String> orders = new LinkedHashSet<>();
        orders.add(targetCode);
        if (requirements == null) {
            return orders;
        }
        for (ExecutionRound round : requirements.listByMother(motherCode, read.session(), read.lock())) {
            if (targetCode.equals(round.sourceWorkOrderCode()) && round.roundWorkOrderCode() != null && !round.roundWorkOrderCode().isBlank()) {
                orders.add(round.roundWorkOrderCode());
                bag.hash("related|" + round.roundWorkOrderCode());
            }
        }
        return orders;
    }

    private List<String> route(String motherCode, String workOrderCode, List<String> original, Read read, Bag bag) {
        if (requirements == null) {
            return original;
        }
        try {
            Optional<OrderRequirementVersion> version = requirements.current(motherCode, workOrderCode, read.session(), read.lock());
            return OrderRequirementLookup.route(version.orElse(null), original);
        } catch (BusinessException ex) {
            bag.block("ROUTE_INVALID", ex.getMessage(), true);
            return List.of();
        }
    }

    private List<ReceiptLine> receiptLines(MotherOrderRecord mother, Read read) {
        if (read.session() == null) {
            return documents.listReceiptLines(mother.code());
        }
        List<ReceiptLine> stored = documents.listStoredReceiptLines(mother.code(), read.session());
        if (!stored.isEmpty() || mother.quantity() == null) {
            return stored;
        }
        return List.of(new ReceiptLine(1, mother.materialCode(), mother.materialCode(), mother.goodsName(),
                mother.quantity(), mother.quantityUnit(), mother.weight(), mother.ledgerCode()));
    }

    private Optional<MotherOrderRecord> mother(String code, Read read) {
        return read.session() == null ? documents.findMotherOrderAny(code) : documents.findMotherOrderAny(code, read.session());
    }

    private Optional<ChildWorkOrderRecord> child(String code, Read read) {
        return read.session() == null ? documents.findChildWorkOrderAny(code) : documents.findChildWorkOrderAny(code, read.session());
    }

    private List<ChildWorkOrderRecord> children(String motherCode, Read read) {
        return read.session() == null
                ? documents.listChildWorkOrdersAny(motherCode)
                : documents.listChildWorkOrdersAny(motherCode, read.session());
    }

    private Optional<MotherSplitState> split(String motherCode, Read read) {
        return read.session() == null ? documents.findMotherSplitState(motherCode) : documents.findMotherSplitState(motherCode, read.session());
    }

    private boolean recycledMother(String code, Read read) {
        return read.session() == null ? documents.motherRecycle(code).isPresent() : documents.motherRecycle(code, read.session()).isPresent();
    }

    private boolean recycledChild(String code, Read read) {
        return read.session() == null ? documents.childRecycle(code).isPresent() : documents.childRecycle(code, read.session()).isPresent();
    }

    private static boolean covers(StockDimension dimension, Set<String> orders, boolean motherTarget) {
        String work = dimension.workOrderCode() == null ? "" : dimension.workOrderCode().trim();
        if (orders.contains(work)) {
            return true;
        }
        return motherTarget && work.isEmpty();
    }

    private static String workOf(StockLedgerEntry entry) {
        if (entry.from() != null && entry.from().workOrderCode() != null) {
            return entry.from().workOrderCode();
        }
        if (entry.to() != null && entry.to().workOrderCode() != null) {
            return entry.to().workOrderCode();
        }
        return "";
    }

    private static String unitFor(List<QuantityFact> required, String material) {
        String unit = "";
        for (QuantityFact fact : required) {
            if (fact.materialCode().equals(material)) {
                if (!unit.isEmpty() && !unit.equals(fact.unit())) {
                    return "";
                }
                unit = fact.unit();
            }
        }
        return unit;
    }

    private static BigDecimal singleQuantity(List<QuantityFact> required) {
        if (required.size() != 1) {
            return null;
        }
        return required.get(0).required();
    }

    private static String key(String material, String unit) {
        return (material == null ? "" : material) + "|" + (unit == null ? "" : unit);
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private OrderDeliveryAssessment finish(
            String type, String code, String mother, Long customerId, String customerName, boolean feeTarget,
            Bag bag, List<ChildDelivery> children
    ) {
        boolean review = bag.blocks.stream().anyMatch(DeliveryBlock::review);
        String state = review
                ? OrderDeliveryAssessment.PENDING_REVIEW
                : bag.blocks.isEmpty() ? OrderDeliveryAssessment.COMPLETE : OrderDeliveryAssessment.NOT_COMPLETE;
        boolean eligible = feeTarget && OrderDeliveryAssessment.COMPLETE.equals(state);
        bag.hashLines.sort(Comparator.naturalOrder());
        return new OrderDeliveryAssessment(type, code, mother, customerId, customerName, feeTarget, state, eligible,
                digest(bag.hashLines), bag.blocks, bag.quantities, bag.labor, children);
    }

    private static String digest(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : lines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private record Read(WorkSession session, boolean lock) {
    }

    private static final class Bag {
        private final List<DeliveryBlock> blocks = new ArrayList<>();
        private final List<String> hashLines = new ArrayList<>();
        private final List<QuantityFact> quantities = new ArrayList<>();
        private final List<LaborFact> labor = new ArrayList<>();

        private void block(String code, String message, boolean review) {
            if (blocks.stream().anyMatch(row -> row.code().equals(code) && row.message().equals(message))) {
                return;
            }
            blocks.add(new DeliveryBlock(code, message, review));
        }

        private void hash(String line) {
            hashLines.add(line);
        }
    }
}
