package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeLookup;
import com.zhubao.erp.business.domain.MobileIntakeResult;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderCodes;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.scan.app.CourierNumbers;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手机收货：按快递单号打开已有母单，或生成新母单并写入库存台账。
 */
@Service
public class MobileIntakeService {

    private static final Map<String, Long> KNOWN_CUSTOMERS = Map.of(
            "恒信珠宝", 1001L,
            "雅辰饰品", 1002L,
            "璟瑞工贸", 1003L,
            "瑞禾珠宝", 1004L
    );

    private final BusinessOperationService businessOperationService;
    private final BusinessDocumentRepository businessDocumentRepository;
    private final BusinessWorkUnit workUnit;
    /** 本进程已发出、事务可能尚未提交的母单号，避免并发收货拿到同一个流水。 */
    private final Set<String> reservedMotherCodes = ConcurrentHashMap.newKeySet();
    private final Object motherCodeLock = new Object();

    public MobileIntakeService(
            BusinessOperationService businessOperationService,
            BusinessDocumentRepository businessDocumentRepository
    ) {
        this(businessOperationService, businessDocumentRepository, null);
    }

    @Autowired
    public MobileIntakeService(
            BusinessOperationService businessOperationService,
            BusinessDocumentRepository businessDocumentRepository,
            BusinessWorkUnit workUnit
    ) {
        this.businessOperationService = businessOperationService;
        this.businessDocumentRepository = businessDocumentRepository;
        this.workUnit = workUnit;
    }

    public MobileIntakeLookup lookup(String courierNo) {
        return lookup(courierNo, null);
    }

    public MobileIntakeLookup lookup(String courierNo, OperatorContext operator) {
        String normalized = CourierNumbers.normalize(courierNo);
        if (!CourierNumbers.isWaybill(normalized)) {
            throw new BusinessException("未识别的快递单号");
        }
        List<MotherOrderRecord> found = businessDocumentRepository.listMotherOrdersByCourierNo(normalized).stream()
                .filter(record -> canRead(operator, record))
                .toList();
        if (found.isEmpty()) return MobileIntakeLookup.missing(normalized);
        MotherOrderRecord only = found.size() == 1 ? found.get(0) : null;
        return new MobileIntakeLookup(true, only == null ? "" : only.code(), normalized,
                only == null ? "" : only.customerName(), only == null ? "" : only.processingRequirement(),
                only == null ? "" : only.dueDate(), only == null ? "" : only.goodsName(),
                found.stream().map(record -> new MobileIntakeLookup.Candidate(record.code(), record.customerName(),
                        record.createdAt(), record.goodsName(), record.processingRequirement(), record.dueDate())).toList());
    }

    public MobileIntakeResult intake(MobileIntakeCommand command, OperatorContext operator) {
        if (operator == null || operator.operator() == null || operator.operator().isBlank()) {
            throw new BusinessException("请先登录后再收货");
        }
        String courierNo = CourierNumbers.normalize(command.courierNo());
        String sourceType = command.sourceType() == null || command.sourceType().isBlank() ? "COURIER" : command.sourceType().trim();
        if (!List.of("COURIER", "CUSTOMER_DELIVERY", "INTERNAL_OTHER").contains(sourceType)) {
            throw new BusinessException("收货来源无效");
        }
        if (!courierNo.isBlank() && !CourierNumbers.isWaybill(courierNo)) {
            throw new BusinessException("未识别的快递单号");
        }
        String customerName = command.customerName().trim();
        assertCustomerAllowed(operator, customerName, command.customerId());
        if (command.requestId() == null || command.requestId().isBlank()) {
            throw new BusinessException("建单必须提供请求编号，请升级客户端后重试");
        }
        return intakeOnce(command, operator.operator().trim(), courierNo, customerName);
    }

    private MobileIntakeResult intakeOnce(
            MobileIntakeCommand command,
            String operator,
            String courierNo,
            String customerName
    ) {
        if (workUnit == null) {
            throw new BusinessException("来料回放尚未装配");
        }
        long customerId = resolveCustomerId(customerName, command.customerId());
        String text = workUnit.execute(new CommandCall(
                    command.requestId(),
                    operator,
                    "INTAKE_CREATE",
                    "REQUEST",
                    command.requestId(),
                    ReceivingResultText.intakePayload(command, customerId, customerName)
        ), session -> ReceivingResultText.intake(create(command, operator, courierNo, customerName, session)));
        return ReceivingResultText.intake(text);
    }

    public MobileIntakeResult view(String motherOrderCode) {
        MotherOrderRecord record = businessDocumentRepository.findMotherOrderByCode(motherOrderCode)
                .orElseThrow(() -> new BusinessException("母单不存在：" + motherOrderCode));
        return resultOf(record, true);
    }

    private MobileIntakeResult create(
            MobileIntakeCommand command,
            String operator,
            String courierNo,
            String customerName,
            WorkSession session
    ) {
        long customerId = resolveCustomerId(customerName, command.customerId());
        String motherOrderCode = nextMotherOrderCode(session);
        String materialCode = command.materialCode() == null || command.materialCode().isBlank()
                ? "MAT-MOBILE"
                : command.materialCode().trim();
        String expressCompany = command.expressCompany() == null ? "" : command.expressCompany().trim();
        String quantityUnit = command.quantityUnit() == null || command.quantityUnit().isBlank()
                ? "件"
                : command.quantityUnit().trim();
        ConfirmInboundCommand inbound = new ConfirmInboundCommand(
                motherOrderCode,
                customerId,
                customerId,
                materialCode,
                LocalDate.now().toString(),
                expressCompany,
                courierNo,
                "",
                operator,
                operator,
                command.goodsName().trim(),
                command.sourceType() == null ? "COURIER" : command.sourceType(),
                null,
                "",
                "RAW-CUSTOMER",
                command.quantity(),
                quantityUnit,
                command.weight(),
                operator,
                courierNo,
                "收货生成母单",
                command.processingRequirement().trim(),
                command.dueDate().trim(),
                customerName
        );
        InboundConfirmationResult confirmed = session == null
                ? businessOperationService.confirmInbound(inbound)
                : businessOperationService.postInboundInSession(inbound, session);
        MotherOrderRecord saved = session == null
                ? businessDocumentRepository.findMotherOrderByCode(confirmed.motherOrderCode())
                    .orElseThrow(() -> new BusinessException("母单已入账但未能读回：" + confirmed.motherOrderCode()))
                : businessDocumentRepository.findMotherOrderByCode(confirmed.motherOrderCode(), session)
                    .orElseThrow(() -> new BusinessException("母单已入账但未能读回：" + confirmed.motherOrderCode()));
        return resultOf(saved, false);
    }

    private String nextMotherOrderCode(WorkSession session) {
        synchronized (motherCodeLock) {
            String code = OrderCodes.next(
                    "MD",
                    LocalDate.now(),
                    businessDocumentRepository.listMotherOrders().stream().map(MotherOrderRecord::code).toList(),
                    candidate -> reservedMotherCodes.contains(candidate) || (session == null
                            ? businessDocumentRepository.motherOrderExists(candidate)
                            : businessDocumentRepository.motherOrderExists(candidate, session))
            );
            reservedMotherCodes.add(code);
            return code;
        }
    }

    private long resolveCustomerId(String customerName, Long provided) {
        if (provided != null && provided > 0) {
            return provided;
        }
        Long known = KNOWN_CUSTOMERS.get(customerName);
        if (known != null) {
            return known;
        }
        return Math.floorMod(customerName.hashCode(), 800_000) + 200_000L;
    }

    private void assertCustomerAllowed(OperatorContext operator, String customerName, Long customerId) {
        if (operator.dataScope() == DataScope.ALL || operator.customerScope().isEmpty()) {
            return;
        }
        String idText = customerId == null ? "" : String.valueOf(customerId);
        boolean allowed = operator.customerScope().stream()
                .anyMatch(item -> item.equals(customerName) || (!idText.isBlank() && item.equals(idText)));
        if (!allowed) {
            throw new BusinessException("客户不在当前账号范围内");
        }
    }

    private boolean canRead(OperatorContext operator, MotherOrderRecord record) {
        if (operator == null || operator.dataScope() == DataScope.ALL || operator.customerScope().isEmpty()) return true;
        return operator.customerScope().contains(String.valueOf(record.customerId()))
                || operator.customerScope().contains(record.customerName());
    }

    private MobileIntakeResult resultOf(MotherOrderRecord record, boolean alreadyExists) {
        return new MobileIntakeResult(
                alreadyExists,
                record.code(),
                record.courierNo(),
                record.customerName(),
                record.processingRequirement(),
                record.dueDate(),
                record.goodsName(),
                record.quantity(),
                record.weight(),
                record.ledgerCode(),
                "JG|mother|" + record.code()
        );
    }
}
