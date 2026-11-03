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
 * 不因客户收款或一次发货重算。外协数量不算员工产量。返工是售后，不改变工资。
 * 工资数量按最终成品核定，定额工价由人工填写。没有最终成品不入账，也不写 0。
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
        if (closure.kind() == BusinessKind.GOLDWORK) {
            throw new BusinessException("金工本版只记录件数，不核算工资");
        }
        StockMeasurement.rejectUndecidedGoldScale(closure.kind());
        if (closure.kind() == BusinessKind.SAMPLING) {
            throw new BusinessException("打样本版不核算工资");
        }
        if (closure.closure() == EmployeeClosureKind.NOT_CLOSED) {
            throw new BusinessException("加工目标尚未闭合，不能核算工资");
        }
        if (closure.closure() == EmployeeClosureKind.TOTAL_LOSS
                || closure.finishedQuantity() != null && closure.finishedQuantity().signum() == 0) {
            throw new BusinessException("没有最终成品，不能按定额核算工资");
        }
        if (shares.stream().anyMatch(EmployeeWorkShare::proxy)) {
            throw new BusinessException("工资只发给实际加工的人");
        }
        if (!closure.workEvidenceComplete()) throw new BusinessException("员工实作依据不完整，请核对工序、人员及报工照片");
        var remaining = new ArrayList<>(closure.work());
        List<EmployeeWorkShare> verified = new ArrayList<>();
        for (EmployeeWorkShare share : shares) {
            var work = remaining.stream().filter(item -> item.payeeAccount().equals(share.payeeAccount())
                    && (item.stepId().equals(share.processName()) || item.processName().equals(share.processName())))
                    .findFirst().orElseThrow(() -> new BusinessException("计酬人员或工序没有对应实作依据"));
            BigDecimal expected = wageQuantity(work.quantity(), closure.finishedQuantity());
            if (share.quantity().compareTo(expected) != 0) {
                throw new BusinessException("工资数量按最终成品核定，定额工价请另行填写");
            }
            remaining.remove(work);
            verified.add(new EmployeeWorkShare(share.payeeAccount(), work.stepId(), share.quantity(), share.unitPrice(), false));
        }
        if (!remaining.isEmpty()) throw new BusinessException("尚有工序或人员的实作未核算");
        String month = YearMonth.now().toString();
        String mother = target.startsWith("ZD-") ? "" : target;
        List<String> codes = new ArrayList<>();
        for (EmployeeWorkShare share : verified) {
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

    private static BigDecimal wageQuantity(BigDecimal worked, BigDecimal finished) {
        if (finished == null || worked.compareTo(finished) <= 0) {
            return worked;
        }
        return finished;
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
