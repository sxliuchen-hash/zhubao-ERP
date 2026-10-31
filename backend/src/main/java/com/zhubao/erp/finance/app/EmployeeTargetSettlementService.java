package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.EmployeeClosureKind;
import com.zhubao.erp.finance.domain.EmployeeSettlementAuthorization;
import com.zhubao.erp.finance.domain.EmployeeTargetClosure;
import com.zhubao.erp.finance.domain.EmployeeTargetClosureLookup;
import com.zhubao.erp.finance.domain.EmployeeWorkShare;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 加工目标整体闭合后，按计酬人的实作份额确认工费。
 * 不因客户收款或一次发货重算。外协数量不算员工产量。损耗不自动扣工资。
 * 代报、返工和全损计酬尚未核定，这些情况只拒绝，不填 0。
 */
@Service
public class EmployeeTargetSettlementService {

    private static final DateTimeFormatter RECORDED_AT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final BigDecimal NO_LOSS_DEDUCTION = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EmployeeTargetClosureLookup closures;
    private final FinanceRepository finance;
    private final EmployeeSettlementAuthorization authorization;
    private final BusinessWorkUnit workUnit;

    public EmployeeTargetSettlementService(EmployeeTargetClosureLookup closures, FinanceRepository finance,
                                           EmployeeSettlementAuthorization authorization, BusinessWorkUnit workUnit) {
        this.closures = closures;
        this.finance = finance;
        this.authorization = authorization;
        this.workUnit = workUnit;
    }

    public List<PayrollLine> settle(OperatorContext actor, String requestId, String targetCode, long customerId,
                                    List<EmployeeWorkShare> shares) {
        String target = targetCode == null ? "" : targetCode.trim();
        String result = workUnit.execute(new CommandCall(
                requestId, actor.operator(), "SETTLE_EMPLOYEE_TARGET", "BUSINESS_TARGET", target, payload(target, shares)
        ), session -> settleIn(actor, target, customerId, shares, session));
        List<PayrollLine> lines = new ArrayList<>();
        for (String code : result.split(",")) {
            lines.add(finance.listPayrollLines().stream().filter(line -> line.code().equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("员工核算保存后无法读取")));
        }
        return List.copyOf(lines);
    }

    private String settleIn(OperatorContext actor, String target, long customerId, List<EmployeeWorkShare> shares,
                            WorkSession session) {
        authorization.assertCanSettle(actor);
        if (shares == null || shares.isEmpty()) {
            throw new BusinessException("员工核算缺少计酬份额");
        }
        if (alreadySettled(target)) {
            throw new BusinessException("该目标已经核算过工资");
        }
        EmployeeTargetClosure closure = closures.find(target).orElseThrow(() -> new BusinessException("加工目标不存在"));
        StockMeasurement.rejectUndecidedGoldScale(closure.kind());
        if (closure.kind() == BusinessKind.SAMPLING) {
            throw new BusinessException("打样计酬尚未核定，不能入账");
        }
        if (closure.closure() == EmployeeClosureKind.NOT_CLOSED) {
            throw new BusinessException("加工目标尚未闭合，不能核算工资");
        }
        if (closure.closure() == EmployeeClosureKind.TOTAL_LOSS) {
            throw new BusinessException("全损计酬尚未核定，不能按 0 或正常完工入账");
        }
        if (closure.closure() == EmployeeClosureKind.REWORK_OPEN) {
            throw new BusinessException("返工计酬尚未核定，不能入账");
        }
        if (shares.stream().anyMatch(EmployeeWorkShare::proxy)) {
            throw new BusinessException("多人代报计酬尚未核定，不能入账");
        }
        BigDecimal employeeQuantity = closure.employeeQuantity();
        if (employeeQuantity == null || employeeQuantity.signum() <= 0) {
            throw new BusinessException("员工实作数量无效");
        }
        BigDecimal shareQuantity = shares.stream().map(EmployeeWorkShare::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (shareQuantity.compareTo(employeeQuantity) != 0) {
            throw new BusinessException("工费数量必须等于员工实作数量，不能自动扣损耗");
        }
        String month = YearMonth.now().toString();
        String mother = target.startsWith("ZD-") ? "" : target;
        List<String> codes = new ArrayList<>();
        for (EmployeeWorkShare share : shares) {
            BigDecimal amount = share.quantity().multiply(share.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            String code = "WG" + UUID.randomUUID().toString().replace("-", "");
            finance.savePayrollLine(new PayrollLine(
                    code, month, share.payeeAccount(), target, share.processName(), mother, customerId,
                    share.quantity(), chargeUnit(closure.quantityUnit()), share.unitPrice(), amount, NO_LOSS_DEDUCTION, amount, true,
                    sourceKey(target, share), PayrollLine.POST_DELIVERY, share.payeeAccount()
            ), session);
            finance.savePayable(new Payable(
                    "AP" + UUID.randomUUID().toString().replace("-", ""), "工资", month, share.payeeAccount(),
                    target, customerId, mother, amount, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    actor.operator(), RECORDED_AT.format(LocalDateTime.now()),
                    share.payeeAccount(), "", sourceKey(target, share)
            ), session);
            codes.add(code);
        }
        return String.join(",", codes);
    }

    private static ChargeUnit chargeUnit(String quantityUnit) {
        return switch (quantityUnit == null ? "" : quantityUnit.trim()) {
            case "颗", "件" -> ChargeUnit.PIECE;
            case "克" -> ChargeUnit.GRAM;
            case "次" -> ChargeUnit.JOB;
            default -> throw new BusinessException("数量单位不能入账");
        };
    }

    private boolean alreadySettled(String target) {
        String prefix = "employee-target:" + target + ":";
        return finance.listPayrollLines().stream().anyMatch(line -> line.sourceKey() != null && line.sourceKey().startsWith(prefix));
    }

    private static String sourceKey(String target, EmployeeWorkShare share) {
        return "employee-target:" + target + ":" + share.payeeAccount() + ":" + share.processName();
    }

    private static String payload(String target, List<EmployeeWorkShare> shares) {
        StringBuilder text = new StringBuilder(target);
        if (shares != null) {
            for (EmployeeWorkShare share : shares) {
                text.append('\n').append(share.payeeAccount()).append('|').append(share.processName())
                        .append('|').append(share.quantity()).append('|').append(share.unitPrice())
                        .append('|').append(share.proxy());
            }
        }
        return text.toString();
    }
}
