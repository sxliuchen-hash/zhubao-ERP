package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeLine;
import com.zhubao.erp.business.domain.MobileIntakeLookup;
import com.zhubao.erp.business.domain.MobileIntakeResult;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderCodes;
import com.zhubao.erp.business.domain.ReceiptLine;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.scan.app.CourierNumbers;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final MasterDataService masterData;
    private final StockLedgerService stockLedgerService;
    private final AuthUserAccountRepository accounts;
    /** 本进程已发出、事务可能尚未提交的母单号，避免并发收货拿到同一个流水。 */
    private final Set<String> reservedMotherCodes = ConcurrentHashMap.newKeySet();
    private final Object motherCodeLock = new Object();

    public MobileIntakeService(
            BusinessOperationService businessOperationService,
            BusinessDocumentRepository businessDocumentRepository
    ) {
        this(businessOperationService, businessDocumentRepository, null, null, null, null);
    }

    public MobileIntakeService(
            BusinessOperationService businessOperationService,
            BusinessDocumentRepository businessDocumentRepository,
            BusinessWorkUnit workUnit
    ) {
        this(businessOperationService, businessDocumentRepository, workUnit, null, null, null);
    }

    public MobileIntakeService(
            BusinessOperationService businessOperationService,
            BusinessDocumentRepository businessDocumentRepository,
            BusinessWorkUnit workUnit,
            MasterDataService masterData
    ) {
        this(businessOperationService, businessDocumentRepository, workUnit, masterData, null, null);
    }

    public MobileIntakeService(
            BusinessOperationService businessOperationService,
            BusinessDocumentRepository businessDocumentRepository,
            BusinessWorkUnit workUnit,
            MasterDataService masterData,
            StockLedgerService stockLedgerService
    ) {
        this(businessOperationService, businessDocumentRepository, workUnit, masterData, stockLedgerService, null);
    }

    @Autowired
    public MobileIntakeService(
            BusinessOperationService businessOperationService,
            BusinessDocumentRepository businessDocumentRepository,
            BusinessWorkUnit workUnit,
            MasterDataService masterData,
            StockLedgerService stockLedgerService,
            AuthUserAccountRepository accounts
    ) {
        this.businessOperationService = businessOperationService;
        this.businessDocumentRepository = businessDocumentRepository;
        this.workUnit = workUnit;
        this.masterData = masterData;
        this.stockLedgerService = stockLedgerService;
        this.accounts = accounts;
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
        String customerName = masterData == null ? command.customerName().trim()
                : masterData.customerForIntake(command.customerId()).name();
        long customerId = masterData == null ? resolveCustomerId(customerName, command.customerId()) : command.customerId();
        assertCustomerAllowed(operator, customerName, customerId);
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
        List<MobileIntakeLine> lines = normalizedLines(command);
        String motherOrderCode = nextMotherOrderCode(session);
        MobileIntakeLine first = lines.get(0);
        String materialCode = first.materialCode();
        String expressCompany = command.expressCompany() == null ? "" : command.expressCompany().trim();
        String sourceType = command.sourceType() == null || command.sourceType().isBlank()
                ? "COURIER" : command.sourceType().trim();
        String quantityUnit = first.quantityUnit();
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
                first.goodsName(),
                !command.lines().isEmpty() ? "INTAKE_LINES:" + sourceType : sourceType,
                null,
                "",
                "RAW-CUSTOMER",
                first.quantity(),
                quantityUnit,
                first.weight(),
                operator,
                // 运单号只留在快递单号。证据编号要对上附件；现场文件改由业务证据引用关联。
                "",
                "收货生成母单",
                command.processingRequirement().trim(),
                command.dueDate().trim(),
                customerName
        );
        InboundConfirmationResult confirmed = session == null
                ? businessOperationService.confirmInbound(inbound)
                : businessOperationService.postInboundInSession(inbound, session);
        if (!command.lines().isEmpty()) {
            if (session == null || stockLedgerService == null) throw new BusinessException("逐材质收货工作单元尚未装配");
            BigDecimal totalQuantity = BigDecimal.ZERO;
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (int index = 0; index < lines.size(); index++) {
                MobileIntakeLine line = lines.get(index);
                StockLedgerEntry entry;
                if (index == 0) {
                    entry = null;
                } else {
                    StockDimension to = new StockDimension(OwnerType.CUSTOMER, customerId, customerId,
                            motherOrderCode, null, line.materialCode(), "RAW-CUSTOMER", StockStatus.RAW_WAITING);
                    entry = stockLedgerService.transfer(new StockTransferCommand("inbound_confirm", motherOrderCode,
                            null, to, line.quantity(), line.weight(), "收货明细第 " + (index + 1) + " 行"), session);
                }
                String ledgerCode = index == 0 ? confirmed.ledgerCode() : entry.code();
                String materialName = masterData == null ? line.materialCode()
                        : masterData.materialForIntake(line.materialCode()).name();
                businessDocumentRepository.saveReceiptLine(motherOrderCode, new ReceiptLine(index + 1,
                        line.materialCode(), materialName, line.goodsName(), line.quantity(), line.quantityUnit(),
                        line.weight(), ledgerCode), session);
                totalQuantity = totalQuantity.add(line.quantity());
                totalWeight = totalWeight.add(line.weight());
            }
            businessDocumentRepository.updateMotherOrderSummary(motherOrderCode, totalQuantity, totalWeight, session);
        }
        MotherOrderRecord saved = session == null
                ? businessDocumentRepository.findMotherOrderByCode(confirmed.motherOrderCode())
                    .orElseThrow(() -> new BusinessException("母单已入账但未能读回：" + confirmed.motherOrderCode()))
                : businessDocumentRepository.findMotherOrderByCode(confirmed.motherOrderCode(), session)
                    .orElseThrow(() -> new BusinessException("母单已入账但未能读回：" + confirmed.motherOrderCode()));
        return resultOf(saved, false);
    }

    private List<MobileIntakeLine> normalizedLines(MobileIntakeCommand command) {
        List<MobileIntakeLine> raw = command.lines().isEmpty()
                ? List.of(new MobileIntakeLine(command.materialCode(), command.goodsName(), command.quantity(),
                        command.quantityUnit(), command.weight())) : command.lines();
        List<MobileIntakeLine> result = new ArrayList<>();
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        String orderUnit = null;
        for (MobileIntakeLine line : raw) {
            if (line == null || line.quantity() == null || line.weight() == null
                    || line.quantity().signum() <= 0 || line.weight().signum() <= 0
                    || line.goodsName() == null || line.goodsName().isBlank()) {
                throw new BusinessException("每条收货明细都要填写货品、正数数量和重量");
            }
            String code = line.materialCode() == null || line.materialCode().isBlank()
                    ? "MAT-MOBILE" : line.materialCode().trim();
            if (masterData != null) masterData.materialForIntake(code);
            String unit = line.quantityUnit() == null || line.quantityUnit().isBlank() ? "件" : line.quantityUnit().trim();
            if (orderUnit != null && !orderUnit.equals(unit)) {
                throw new BusinessException("同一母单明细须使用相同数量单位，以便汇总和拆单");
            }
            orderUnit = unit;
            result.add(new MobileIntakeLine(code, line.goodsName().trim(), line.quantity(), unit, line.weight()));
            quantity = quantity.add(line.quantity());
            weight = weight.add(line.weight());
        }
        if (command.lines().size() > 0 && (command.quantity() == null || command.weight() == null
                || command.quantity().compareTo(quantity) != 0 || command.weight().compareTo(weight) != 0)) {
            throw new BusinessException("母单数量和重量必须等于明细合计");
        }
        return result;
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
        if (!CustomerScopeRules.allowed(operator, customerId, customerName)) {
            throw new BusinessException("客户不在当前账号范围内");
        }
    }

    private boolean canRead(OperatorContext operator, MotherOrderRecord record) {
        return DocumentVisibility.canSee(operator, record.customerId(), record.customerName(), "inbound",
                List.of(record.operator(), record.receiverName(), record.unpackSigner()),
                accounts == null ? List.of() : accounts.findAll());
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
