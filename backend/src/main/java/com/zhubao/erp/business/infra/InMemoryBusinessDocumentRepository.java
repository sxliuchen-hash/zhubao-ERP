package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ChildLineAllocation;
import com.zhubao.erp.business.domain.LabelReprintLog;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderAdjustment;
import com.zhubao.erp.business.domain.OrderRecycleMark;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityReworkReport;
import com.zhubao.erp.business.domain.QualityRound;
import com.zhubao.erp.business.domain.ReceiptLine;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryBusinessDocumentRepository implements BusinessDocumentRepository {

    private final ConcurrentMap<String, MotherOrderRecord> motherOrders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ReceiptLine>> receiptLines = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ChildLineAllocation>> childAllocations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OutboundOrderRecord> outboundOrders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MotherSplitState> splitStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ChildWorkOrderRecord> childWorkOrders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OrderRecycleMark> recycledMothers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OrderRecycleMark> recycledChildren = new ConcurrentHashMap<>();
    private final List<OrderAdjustment> orderAdjustments = new CopyOnWriteArrayList<>();
    private final List<LabelReprintLog> reprintLogs = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, ProcessStationHandoff> processHandoffs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QualityRequirementCheck> qualityChecks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QualityRound> qualityRounds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QualityReworkReport> qualityReworkReports = new ConcurrentHashMap<>();
    private final AtomicLong reprintSequence = new AtomicLong();

    @Override
    public boolean motherOrderExists(String code) {
        return motherOrders.containsKey(code);
    }

    @Override
    public boolean motherOrderExists(String code, WorkSession session) {
        requireSession(session);
        return motherOrderExists(code);
    }

    @Override
    public MotherOrderRecord saveMotherOrder(MotherOrderRecord record) {
        motherOrders.put(record.code(), record);
        return record;
    }

    @Override
    public synchronized MotherOrderRecord saveMotherOrder(MotherOrderRecord record, WorkSession session) {
        requireSession(session);
        MotherOrderRecord previous = motherOrders.get(record.code());
        motherOrders.put(record.code(), record);
        session.onRollback(() -> {
            if (previous == null) {
                motherOrders.remove(record.code(), record);
            } else {
                motherOrders.put(record.code(), previous);
            }
        });
        return record;
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCode(String code) {
        MotherOrderRecord record = motherOrders.get(code);
        if (record == null || recycledMothers.containsKey(code)) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCode(String code, WorkSession session) {
        requireSession(session);
        return findMotherOrderByCode(code);
    }

    @Override
    public List<MotherOrderRecord> listMotherOrdersByCourierNo(String courierNo) {
        if (courierNo == null || courierNo.isBlank()) {
            return List.of();
        }
        return motherOrders.values().stream()
                .filter(record -> courierNo.equals(record.courierNo()))
                .filter(record -> !recycledMothers.containsKey(record.code()))
                .sorted(Comparator.comparing(MotherOrderRecord::createdAt).reversed().thenComparing(MotherOrderRecord::code))
                .toList();
    }

    @Override
    public List<MotherOrderRecord> listMotherOrdersByCourierNo(String courierNo, WorkSession session) {
        requireSession(session);
        return listMotherOrdersByCourierNo(courierNo);
    }

    private static void requireSession(WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("单据写入必须处于业务工作单元");
        }
    }

    @Override
    public List<MotherOrderRecord> listMotherOrders() {
        return motherOrders.values().stream()
                .filter(record -> !recycledMothers.containsKey(record.code()))
                .toList();
    }

    @Override
    public List<ReceiptLine> listReceiptLines(String motherOrderCode) {
        List<ReceiptLine> rows = receiptLines.get(motherOrderCode);
        if (rows != null && !rows.isEmpty()) return List.copyOf(rows);
        MotherOrderRecord mother = motherOrders.get(motherOrderCode);
        if (mother == null) return List.of();
        return List.of(new ReceiptLine(1, mother.materialCode(), mother.materialCode(), mother.goodsName(),
                mother.quantity(), mother.quantityUnit(), mother.weight(), mother.ledgerCode()));
    }

    @Override
    public synchronized void saveReceiptLine(String motherOrderCode, ReceiptLine line, WorkSession session) {
        requireSession(session);
        List<ReceiptLine> previous = receiptLines.get(motherOrderCode);
        List<ReceiptLine> next = new ArrayList<>(previous == null ? List.of() : previous);
        if (next.stream().anyMatch(item -> item.lineNo() == line.lineNo())) throw new BusinessException("收货明细行号重复");
        next.add(line);
        receiptLines.put(motherOrderCode, List.copyOf(next));
        session.onRollback(() -> {
            if (previous == null) receiptLines.remove(motherOrderCode);
            else receiptLines.put(motherOrderCode, previous);
        });
    }

    @Override
    public synchronized void updateMotherOrderSummary(String motherOrderCode, java.math.BigDecimal quantity,
                                                       java.math.BigDecimal weight, WorkSession session) {
        requireSession(session);
        MotherOrderRecord previous = motherOrders.get(motherOrderCode);
        if (previous == null) throw new BusinessException("母单不存在：" + motherOrderCode);
        motherOrders.put(motherOrderCode, previous.withSummary(quantity, weight));
        session.onRollback(() -> motherOrders.put(motherOrderCode, previous));
    }

    @Override
    public List<ChildLineAllocation> listChildAllocations(String motherOrderCode, WorkSession session) {
        if (session != null) requireSession(session);
        return List.copyOf(childAllocations.getOrDefault(motherOrderCode, List.of()));
    }

    @Override
    public synchronized void saveChildAllocation(ChildLineAllocation allocation, WorkSession session) {
        requireSession(session);
        String mother = allocation.motherOrderCode();
        List<ChildLineAllocation> previous = childAllocations.get(mother);
        List<ChildLineAllocation> next = new ArrayList<>(previous == null ? List.of() : previous);
        next.add(allocation);
        childAllocations.put(mother, List.copyOf(next));
        session.onRollback(() -> {
            if (previous == null) childAllocations.remove(mother);
            else childAllocations.put(mother, previous);
        });
    }

    @Override
    public boolean outboundOrderExists(String code) {
        return outboundOrders.containsKey(code);
    }

    @Override
    public boolean outboundOrderExists(String code, WorkSession session) {
        requireSession(session);
        return outboundOrderExists(code);
    }

    @Override
    public OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record) {
        outboundOrders.put(record.code(), record);
        return record;
    }

    @Override
    public synchronized OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record, WorkSession session) {
        requireSession(session);
        OutboundOrderRecord existing = outboundOrders.putIfAbsent(record.code(), record);
        if (existing != null) {
            throw new BusinessException("结构化出库单已存在，不能重复确认出库：" + record.code());
        }
        session.onRollback(() -> outboundOrders.remove(record.code(), record));
        return record;
    }

    @Override
    public Optional<OutboundOrderRecord> findOutboundOrderByCode(String code) {
        return Optional.ofNullable(outboundOrders.get(code));
    }

    @Override
    public List<OutboundOrderRecord> listOutboundOrders() {
        return outboundOrders.values().stream()
                .sorted(java.util.Comparator.comparing(OutboundOrderRecord::code))
                .toList();
    }

    @Override
    public void saveMotherSplitState(MotherSplitState state) {
        splitStates.put(state.motherOrderCode(), state);
    }

    @Override
    public synchronized void saveMotherSplitState(MotherSplitState state, WorkSession session) {
        requireSession(session);
        MotherSplitState previous = splitStates.put(state.motherOrderCode(), state);
        session.onRollback(() -> {
            synchronized (this) {
                if (previous == null) {
                    splitStates.remove(state.motherOrderCode(), state);
                } else {
                    splitStates.put(state.motherOrderCode(), previous);
                }
            }
        });
    }

    @Override
    public Optional<MotherSplitState> findMotherSplitState(String motherOrderCode) {
        return Optional.ofNullable(splitStates.get(motherOrderCode));
    }

    @Override
    public Optional<MotherSplitState> findMotherSplitState(String motherOrderCode, WorkSession session) {
        requireSession(session);
        return findMotherSplitState(motherOrderCode);
    }

    @Override
    public synchronized void lockMotherOrder(String motherOrderCode, WorkSession session) {
        requireSession(session);
        if (motherOrderCode == null || !motherOrders.containsKey(motherOrderCode)) {
            throw new BusinessException("母单不存在：" + motherOrderCode);
        }
    }

    @Override
    public List<MotherSplitState> listAllMotherSplitStates() {
        return List.copyOf(splitStates.values());
    }

    @Override
    public boolean childWorkOrderExists(String code) {
        return childWorkOrders.containsKey(code);
    }

    @Override
    public boolean childWorkOrderExists(String code, WorkSession session) {
        requireSession(session);
        return childWorkOrderExists(code);
    }

    @Override
    public ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record) {
        childWorkOrders.put(record.code(), record);
        return record;
    }

    @Override
    public synchronized ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record, WorkSession session) {
        requireSession(session);
        ChildWorkOrderRecord previous = childWorkOrders.put(record.code(), record);
        session.onRollback(() -> {
            synchronized (this) {
                if (previous == null) {
                    childWorkOrders.remove(record.code(), record);
                } else {
                    childWorkOrders.put(record.code(), previous);
                }
            }
        });
        return record;
    }

    @Override
    public Optional<ChildWorkOrderRecord> findChildWorkOrderByCode(String code) {
        ChildWorkOrderRecord record = childWorkOrders.get(code);
        if (!childVisible(record)) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    @Override
    public List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode) {
        return childWorkOrders.values().stream()
                .filter(record -> motherOrderCode.equals(record.motherOrderCode()))
                .filter(this::childVisible)
                .sorted(Comparator.comparing(ChildWorkOrderRecord::code))
                .toList();
    }

    @Override
    public List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode, WorkSession session) {
        requireSession(session);
        return listChildWorkOrders(motherOrderCode);
    }

    @Override
    public List<ChildWorkOrderRecord> listAllChildWorkOrders() {
        return childWorkOrders.values().stream()
                .filter(this::childVisible)
                .sorted(Comparator.comparing(ChildWorkOrderRecord::code))
                .toList();
    }

    @Override
    public boolean verificationCodeExists(String verificationCode) {
        if (verificationCode == null || verificationCode.isBlank()) {
            return false;
        }
        boolean onMother = splitStates.values().stream()
                .anyMatch(state -> verificationCode.equals(state.verificationCode()));
        boolean onChild = childWorkOrders.values().stream()
                .anyMatch(record -> verificationCode.equals(record.verificationCode()));
        return onMother || onChild;
    }

    @Override
    public boolean verificationCodeExists(String verificationCode, WorkSession session) {
        requireSession(session);
        return verificationCodeExists(verificationCode);
    }

    @Override
    public LabelReprintLog saveLabelReprint(LabelReprintLog log) {
        LabelReprintLog saved = new LabelReprintLog(
                reprintSequence.incrementAndGet(),
                log.labelType(),
                log.businessCode(),
                log.verificationCode(),
                log.operator(),
                log.reason(),
                log.reprintedAt()
        );
        reprintLogs.add(saved);
        return saved;
    }

    @Override
    public List<LabelReprintLog> listLabelReprints(String labelType, String businessCode) {
        return reprintLogs.stream()
                .filter(log -> labelType.equals(log.labelType()) && businessCode.equals(log.businessCode()))
                .toList();
    }

    @Override
    public void saveProcessStationHandoff(ProcessStationHandoff handoff) {
        putHandoff(handoff);
    }

    @Override
    public synchronized void saveProcessStationHandoff(ProcessStationHandoff handoff, WorkSession session) {
        requireSession(session);
        putHandoff(handoff);
        String key = handoffKey(handoff.orderCode(), handoff.processName());
        session.onRollback(() -> processHandoffs.remove(key, handoff));
    }

    @Override
    public Optional<ProcessStationHandoff> findProcessStationHandoff(String orderCode, String processName) {
        return Optional.ofNullable(processHandoffs.get(handoffKey(orderCode, processName)));
    }

    @Override
    public Optional<ProcessStationHandoff> findProcessStationHandoff(String orderCode, String processName, WorkSession session) {
        requireSession(session);
        return findProcessStationHandoff(orderCode, processName);
    }

    @Override
    public List<ProcessStationHandoff> listProcessStationHandoffs(String orderCode) {
        return processHandoffs.values().stream()
                .filter(handoff -> orderCode.equals(handoff.orderCode()))
                .toList();
    }

    @Override
    public List<ProcessStationHandoff> listAllProcessStationHandoffs() {
        return List.copyOf(processHandoffs.values());
    }

    @Override
    public void saveQualityRequirementCheck(QualityRequirementCheck check) {
        putQualityCheck(check);
    }

    @Override
    public synchronized void saveQualityRequirementCheck(QualityRequirementCheck check, WorkSession session) {
        requireSession(session);
        putQualityCheck(check);
        session.onRollback(() -> qualityChecks.remove(check.orderCode(), check));
    }

    @Override
    public Optional<QualityRequirementCheck> findQualityRequirementCheck(String orderCode) {
        return Optional.ofNullable(qualityChecks.get(orderCode));
    }

    @Override
    public Optional<QualityRequirementCheck> findQualityRequirementCheck(String orderCode, WorkSession session) {
        requireSession(session);
        return findQualityRequirementCheck(orderCode);
    }

    @Override
    public List<QualityRequirementCheck> listAllQualityRequirementChecks() {
        return qualityChecks.values().stream()
                .sorted(Comparator.comparing(QualityRequirementCheck::orderCode))
                .toList();
    }

    @Override
    public void attachQualityRework(String orderCode, String reworkOrderCode) {
        putQualityRework(orderCode, reworkOrderCode);
    }

    @Override
    public synchronized void attachQualityRework(String orderCode, String reworkOrderCode, WorkSession session) {
        requireSession(session);
        QualityRequirementCheck before = qualityChecks.get(orderCode);
        putQualityRework(orderCode, reworkOrderCode);
        session.onRollback(() -> {
            if (before == null) {
                qualityChecks.remove(orderCode);
            } else {
                qualityChecks.put(orderCode, before);
            }
        });
    }

    @Override
    public void saveQualityRound(QualityRound round) {
        putQualityRound(round);
    }

    @Override
    public synchronized void saveQualityRound(QualityRound round, WorkSession session) {
        requireSession(session);
        putQualityRound(round);
        session.onRollback(() -> qualityRounds.remove(round.roundCode(), round));
    }

    @Override
    public List<QualityRound> listQualityRounds(String orderCode) {
        return qualityRounds.values().stream()
                .filter(round -> orderCode.equals(round.orderCode()))
                .sorted(Comparator.comparingInt(QualityRound::roundNo))
                .toList();
    }

    @Override
    public List<QualityRound> listQualityRounds(String orderCode, WorkSession session) {
        requireSession(session);
        return listQualityRounds(orderCode);
    }

    @Override
    public void saveQualityReworkReport(QualityReworkReport report) {
        putReworkReport(report);
    }

    @Override
    public synchronized void saveQualityReworkReport(QualityReworkReport report, WorkSession session) {
        requireSession(session);
        putReworkReport(report);
        session.onRollback(() -> qualityReworkReports.remove(report.reworkOrderCode(), report));
    }

    @Override
    public Optional<QualityReworkReport> findQualityReworkReport(String reworkOrderCode) {
        return Optional.ofNullable(qualityReworkReports.get(reworkOrderCode));
    }

    @Override
    public Optional<QualityReworkReport> findQualityReworkReport(String reworkOrderCode, WorkSession session) {
        requireSession(session);
        return findQualityReworkReport(reworkOrderCode);
    }

    private void putQualityCheck(QualityRequirementCheck check) {
        QualityRequirementCheck existing = qualityChecks.putIfAbsent(check.orderCode(), check);
        if (existing != null) {
            throw new BusinessException("这张单已经做过质检对照");
        }
    }

    private void putQualityRework(String orderCode, String reworkOrderCode) {
        QualityRequirementCheck current = qualityChecks.get(orderCode);
        if (current == null) {
            throw new BusinessException("请先提交质检对照");
        }
        if (!current.reworkOrderCode().isBlank()) {
            throw new BusinessException("已经开过返工");
        }
        qualityChecks.put(orderCode, current.withRework(reworkOrderCode));
    }

    private synchronized void putQualityRound(QualityRound round) {
        boolean duplicated = qualityRounds.values().stream()
                .anyMatch(existing -> existing.qualityCaseCode().equals(round.qualityCaseCode())
                        && existing.roundNo() == round.roundNo());
        if (duplicated) {
            throw new BusinessException("这一轮质检已经提交");
        }
        QualityRound existing = qualityRounds.putIfAbsent(round.roundCode(), round);
        if (existing != null) {
            throw new BusinessException("这一轮质检已经提交");
        }
    }

    private void putReworkReport(QualityReworkReport report) {
        QualityReworkReport existing = qualityReworkReports.putIfAbsent(report.reworkOrderCode(), report);
        if (existing != null) {
            throw new BusinessException("返工已经报过工");
        }
    }

    private void putHandoff(ProcessStationHandoff handoff) {
        String key = handoffKey(handoff.orderCode(), handoff.processName());
        ProcessStationHandoff existing = processHandoffs.putIfAbsent(key, handoff);
        if (existing != null) {
            throw new BusinessException("这道工序已经交过");
        }
    }

    private String handoffKey(String orderCode, String processName) {
        return orderCode + "\n" + processName;
    }

    private boolean childVisible(ChildWorkOrderRecord record) {
        return record != null
                && !recycledChildren.containsKey(record.code())
                && !recycledMothers.containsKey(record.motherOrderCode());
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderAny(String code) {
        return Optional.ofNullable(motherOrders.get(code));
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderAny(String code, WorkSession session) {
        requireSession(session);
        return findMotherOrderAny(code);
    }

    @Override
    public Optional<ChildWorkOrderRecord> findChildWorkOrderAny(String code) {
        return Optional.ofNullable(childWorkOrders.get(code));
    }

    @Override
    public Optional<ChildWorkOrderRecord> findChildWorkOrderAny(String code, WorkSession session) {
        requireSession(session);
        return findChildWorkOrderAny(code);
    }

    @Override
    public void lockChildWorkOrder(String code, WorkSession session) {
        requireSession(session);
        if (!childWorkOrders.containsKey(code)) {
            throw new BusinessException("子单不存在：" + code);
        }
    }

    @Override
    public synchronized boolean lockMotherOrderIfPresent(String motherOrderCode, WorkSession session) {
        requireSession(session);
        return motherOrders.containsKey(motherOrderCode);
    }

    @Override
    public synchronized Optional<String> childMotherCode(String childCode) {
        ChildWorkOrderRecord child = childWorkOrders.get(childCode);
        return child == null ? Optional.empty() : Optional.of(child.motherOrderCode());
    }

    @Override
    public List<ChildWorkOrderRecord> listChildWorkOrdersAny(String motherOrderCode) {
        return childWorkOrders.values().stream()
                .filter(record -> motherOrderCode.equals(record.motherOrderCode()))
                .sorted(Comparator.comparing(ChildWorkOrderRecord::code))
                .toList();
    }

    @Override
    public synchronized List<ChildWorkOrderRecord> listChildWorkOrdersAny(String motherOrderCode, WorkSession session) {
        requireSession(session);
        return listChildWorkOrdersAny(motherOrderCode);
    }

    @Override
    public void replaceMotherOrder(MotherOrderRecord record) {
        if (!motherOrders.containsKey(record.code())) {
            throw new BusinessException("母单不存在");
        }
        motherOrders.put(record.code(), record);
    }

    @Override
    public void replaceChildWorkOrder(ChildWorkOrderRecord record) {
        if (!childWorkOrders.containsKey(record.code())) {
            throw new BusinessException("子单不存在");
        }
        childWorkOrders.put(record.code(), record);
    }

    @Override
    public synchronized void correctMotherOrder(MotherOrderRecord record, OrderAdjustment adjustment) {
        if (!motherOrders.containsKey(record.code())) {
            throw new BusinessException("母单不存在");
        }
        orderAdjustments.add(adjustment);
        motherOrders.put(record.code(), record);
    }

    @Override
    public synchronized void correctMotherOrder(MotherOrderRecord record, OrderAdjustment adjustment,
                                                WorkSession session) {
        requireSession(session);
        MotherOrderRecord previous = motherOrders.get(record.code());
        if (previous == null) {
            throw new BusinessException("母单不存在");
        }
        orderAdjustments.add(adjustment);
        motherOrders.put(record.code(), record);
        session.onRollback(() -> {
            motherOrders.put(record.code(), previous);
            orderAdjustments.remove(adjustment);
        });
    }

    @Override
    public synchronized void correctChildWorkOrder(ChildWorkOrderRecord record, OrderAdjustment adjustment) {
        if (!childWorkOrders.containsKey(record.code())) {
            throw new BusinessException("子单不存在");
        }
        orderAdjustments.add(adjustment);
        childWorkOrders.put(record.code(), record);
    }

    @Override
    public synchronized void correctChildWorkOrder(ChildWorkOrderRecord record, OrderAdjustment adjustment,
                                                   WorkSession session) {
        requireSession(session);
        ChildWorkOrderRecord previous = childWorkOrders.get(record.code());
        if (previous == null) {
            throw new BusinessException("子单不存在");
        }
        orderAdjustments.add(adjustment);
        childWorkOrders.put(record.code(), record);
        session.onRollback(() -> {
            childWorkOrders.put(record.code(), previous);
            orderAdjustments.remove(adjustment);
        });
    }

    @Override
    public void recycleMother(String code, String operator, String at, String batch, String reason) {
        if (!motherOrders.containsKey(code)) {
            throw new BusinessException("母单不存在");
        }
        if (recycledMothers.containsKey(code)) {
            throw new BusinessException("母单已在回收站");
        }
        recycledMothers.put(code, new OrderRecycleMark("MOTHER", code, at, operator, batch, reason));
    }

    @Override
    public void recycleMother(String code, String operator, String at, String batch, String reason, WorkSession session) {
        requireSession(session);
        recycleMother(code, operator, at, batch, reason);
        session.onRollback(() -> recycledMothers.remove(code));
    }

    @Override
    public void recycleChild(String code, String operator, String at, String batch, String reason) {
        if (!childWorkOrders.containsKey(code)) {
            throw new BusinessException("子单不存在");
        }
        if (recycledChildren.containsKey(code)) {
            throw new BusinessException("子单已在回收站");
        }
        recycledChildren.put(code, new OrderRecycleMark("WORK_ORDER", code, at, operator, batch, reason));
    }

    @Override
    public void recycleChild(String code, String operator, String at, String batch, String reason, WorkSession session) {
        requireSession(session);
        recycleChild(code, operator, at, batch, reason);
        session.onRollback(() -> recycledChildren.remove(code));
    }

    @Override
    public Optional<OrderRecycleMark> motherRecycle(String code) {
        return Optional.ofNullable(recycledMothers.get(code));
    }

    @Override
    public synchronized Optional<OrderRecycleMark> motherRecycle(String code, WorkSession session) {
        requireSession(session);
        return motherRecycle(code);
    }

    @Override
    public Optional<OrderRecycleMark> childRecycle(String code) {
        return Optional.ofNullable(recycledChildren.get(code));
    }

    @Override
    public synchronized Optional<OrderRecycleMark> childRecycle(String code, WorkSession session) {
        requireSession(session);
        return childRecycle(code);
    }

    @Override
    public List<OrderRecycleMark> listMotherRecycles() {
        return List.copyOf(recycledMothers.values());
    }

    @Override
    public List<OrderRecycleMark> listChildRecycles() {
        return List.copyOf(recycledChildren.values());
    }

    @Override
    public void clearChildRecycle(String code) {
        if (recycledChildren.remove(code) == null) {
            throw new BusinessException("子单不在回收站");
        }
    }

    @Override
    public void clearChildRecycle(String code, WorkSession session) {
        requireSession(session);
        OrderRecycleMark previous = recycledChildren.get(code);
        clearChildRecycle(code);
        session.onRollback(() -> recycledChildren.put(code, previous));
    }

    @Override
    public void clearRecycleBatch(String batch) {
        recycledMothers.entrySet().removeIf(entry -> batch.equals(entry.getValue().batch()));
        recycledChildren.entrySet().removeIf(entry -> batch.equals(entry.getValue().batch()));
    }

    @Override
    public void clearRecycleBatch(String batch, WorkSession session) {
        requireSession(session);
        var mothers = recycledMothers.values().stream().filter(mark -> batch.equals(mark.batch())).toList();
        var children = recycledChildren.values().stream().filter(mark -> batch.equals(mark.batch())).toList();
        clearRecycleBatch(batch);
        session.onRollback(() -> {
            mothers.forEach(mark -> recycledMothers.put(mark.code(), mark));
            children.forEach(mark -> recycledChildren.put(mark.code(), mark));
        });
    }

    @Override
    public void saveOrderAdjustment(OrderAdjustment adjustment) {
        orderAdjustments.add(adjustment);
    }

    @Override
    public void saveOrderAdjustment(OrderAdjustment adjustment, WorkSession session) {
        requireSession(session);
        orderAdjustments.add(adjustment);
        session.onRollback(() -> orderAdjustments.remove(adjustment));
    }

    @Override
    public List<OrderAdjustment> listOrderAdjustments(String targetType, String code) {
        List<OrderAdjustment> matched = new ArrayList<>(orderAdjustments.stream()
                .filter(item -> targetType.equals(item.targetType()) && code.equals(item.code()))
                .toList());
        java.util.Collections.reverse(matched);
        return matched;
    }
}
