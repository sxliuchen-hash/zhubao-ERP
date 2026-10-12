package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.DuplicateCourierException;
import com.zhubao.erp.business.domain.LabelReprintLog;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityReworkReport;
import com.zhubao.erp.business.domain.QualityRound;
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
    private final ConcurrentMap<String, OutboundOrderRecord> outboundOrders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MotherSplitState> splitStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ChildWorkOrderRecord> childWorkOrders = new ConcurrentHashMap<>();
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
        String identity = courierIdentity(record.courierNo());
        if (identity != null) {
            Optional<MotherOrderRecord> occupied = findMotherOrderByCourierNo(identity);
            if (occupied.isPresent() && !occupied.get().code().equals(record.code())) {
                throw new DuplicateCourierException();
            }
        }
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
        return Optional.ofNullable(motherOrders.get(code));
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCode(String code, WorkSession session) {
        requireSession(session);
        return findMotherOrderByCode(code);
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCourierNo(String courierNo) {
        if (courierNo == null || courierNo.isBlank()) {
            return Optional.empty();
        }
        return motherOrders.values().stream()
                .filter(record -> courierNo.equals(record.courierNo()))
                .findFirst();
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCourierNo(String courierNo, WorkSession session) {
        requireSession(session);
        return findMotherOrderByCourierNo(courierNo);
    }

    private static void requireSession(WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("单据写入必须处于业务工作单元");
        }
    }

    private static String courierIdentity(String courierNo) {
        if (courierNo == null || courierNo.isBlank()) {
            return null;
        }
        return courierNo.trim();
    }

    @Override
    public List<MotherOrderRecord> listMotherOrders() {
        return List.copyOf(new ArrayList<>(motherOrders.values()));
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
    public Optional<MotherSplitState> findMotherSplitState(String motherOrderCode) {
        return Optional.ofNullable(splitStates.get(motherOrderCode));
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
    public ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record) {
        childWorkOrders.put(record.code(), record);
        return record;
    }

    @Override
    public Optional<ChildWorkOrderRecord> findChildWorkOrderByCode(String code) {
        return Optional.ofNullable(childWorkOrders.get(code));
    }

    @Override
    public List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode) {
        return childWorkOrders.values().stream()
                .filter(record -> motherOrderCode.equals(record.motherOrderCode()))
                .sorted(Comparator.comparing(ChildWorkOrderRecord::code))
                .toList();
    }

    @Override
    public List<ChildWorkOrderRecord> listAllChildWorkOrders() {
        return childWorkOrders.values().stream()
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
}
