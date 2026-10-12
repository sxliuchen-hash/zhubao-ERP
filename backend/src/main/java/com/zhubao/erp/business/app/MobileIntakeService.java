package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.DuplicateCourierException;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeLookup;
import com.zhubao.erp.business.domain.MobileIntakeResult;
import com.zhubao.erp.business.domain.MotherOrderRecord;
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
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手机收货：按快递单号打开已有母单，或生成新母单并写入库存台账。
 */
@Service
public class MobileIntakeService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Map<String, Long> KNOWN_CUSTOMERS = Map.of(
            "恒信珠宝", 1001L,
            "雅辰饰品", 1002L,
            "璟瑞工贸", 1003L,
            "瑞禾珠宝", 1004L
    );

    private final BusinessOperationService businessOperationService;
    private final BusinessDocumentRepository businessDocumentRepository;
    private final BusinessWorkUnit workUnit;
    private final AtomicInteger sequence = new AtomicInteger();

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
        String normalized = CourierNumbers.normalize(courierNo);
        if (!CourierNumbers.isWaybill(normalized)) {
            throw new BusinessException("未识别的快递单号");
        }
        return businessDocumentRepository.findMotherOrderByCourierNo(normalized)
                .map(this::lookupOf)
                .orElseGet(() -> MobileIntakeLookup.missing(normalized));
    }

    public MobileIntakeResult intake(MobileIntakeCommand command, OperatorContext operator) {
        if (operator == null || operator.operator() == null || operator.operator().isBlank()) {
            throw new BusinessException("请先登录后再收货");
        }
        String courierNo = CourierNumbers.normalize(command.courierNo());
        if (!CourierNumbers.isWaybill(courierNo)) {
            throw new BusinessException("未识别的快递单号");
        }
        String customerName = command.customerName().trim();
        assertCustomerAllowed(operator, customerName, command.customerId());
        if (command.requestId() != null && !command.requestId().isBlank()) {
            return intakeOnce(command, operator.operator().trim(), courierNo, customerName);
        }
        return businessDocumentRepository.findMotherOrderByCourierNo(courierNo)
                .map(record -> resultOf(record, true))
                .orElseGet(() -> create(command, operator.operator().trim(), courierNo, customerName, null));
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
        try {
            String text = workUnit.execute(new CommandCall(
                    command.requestId(),
                    operator,
                    "INTAKE_CREATE",
                    "WAYBILL",
                    courierNo,
                    ReceivingResultText.intakePayload(command, customerId, customerName)
            ), session -> createOrOpen(command, operator, courierNo, customerName, session));
            return ReceivingResultText.intake(text);
        } catch (DuplicateCourierException ex) {
            return openCommitted(courierNo);
        }
    }

    private String createOrOpen(
            MobileIntakeCommand command,
            String operator,
            String courierNo,
            String customerName,
            WorkSession session
    ) {
        Optional<MotherOrderRecord> existing = businessDocumentRepository.findMotherOrderByCourierNo(courierNo, session);
        if (existing.isPresent()) {
            return ReceivingResultText.intake(resultOf(existing.get(), true));
        }
        try {
            return ReceivingResultText.intake(create(command, operator, courierNo, customerName, session));
        } catch (BusinessException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("快递单号已关联母单")) {
                MotherOrderRecord opened = businessDocumentRepository.findMotherOrderByCourierNo(courierNo, session)
                        .orElseThrow(() -> ex);
                return ReceivingResultText.intake(resultOf(opened, true));
            }
            throw ex;
        }
    }

    public MobileIntakeResult view(String motherOrderCode) {
        MotherOrderRecord record = businessDocumentRepository.findMotherOrderByCode(motherOrderCode)
                .orElseThrow(() -> new BusinessException("母单不存在：" + motherOrderCode));
        return resultOf(record, true);
    }

    private MobileIntakeResult openCommitted(String courierNo) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Optional<MotherOrderRecord> found = businessDocumentRepository.findMotherOrderByCourierNo(courierNo);
            if (found.isPresent()) {
                return resultOf(found.get(), true);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new BusinessException("快递单号已关联母单，请按运单重新打开");
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
        String expressCompany = command.expressCompany() == null || command.expressCompany().isBlank()
                ? "快递"
                : command.expressCompany().trim();
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
                "手机收货",
                null,
                "",
                "RAW-CUSTOMER",
                command.quantity(),
                quantityUnit,
                command.weight(),
                operator,
                courierNo,
                "手机收货生成母单",
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
        String day = LocalDate.now().format(DAY);
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = "MD-" + day + "-" + String.format("%03d", sequence.incrementAndGet());
            boolean exists = session == null
                    ? businessDocumentRepository.motherOrderExists(code)
                    : businessDocumentRepository.motherOrderExists(code, session);
            if (!exists) {
                return code;
            }
        }
        throw new BusinessException("母单号生成失败，请重试");
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

    private MobileIntakeLookup lookupOf(MotherOrderRecord record) {
        return new MobileIntakeLookup(
                true,
                record.code(),
                record.courierNo(),
                record.customerName(),
                record.processingRequirement(),
                record.dueDate(),
                record.goodsName()
        );
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
