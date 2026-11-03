package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.EmployeeClosureKind;
import com.zhubao.erp.finance.domain.EmployeeProcessWork;
import com.zhubao.erp.finance.domain.EmployeeSettlementAuthorization;
import com.zhubao.erp.finance.domain.EmployeeTargetClosure;
import com.zhubao.erp.finance.domain.EmployeeTargetClosureLookup;
import com.zhubao.erp.finance.domain.EmployeeWorkShare;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeTargetSettlementTest {

    private final OperatorContext actor = new OperatorContext("核算员", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);

    @Test
    void 闭合后按实作计酬且不因发货或损耗重算() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        EmployeeTargetSettlementService service = service(finance, Map.of(
                "MD-20261030-1001", closure(BusinessKind.PROCESSING, EmployeeClosureKind.CLOSED_NORMAL, "462", "20", "38")));
        List<EmployeeWorkShare> shares = List.of(
                new EmployeeWorkShare("payee-a", "抛光", new BigDecimal("300"), new BigDecimal("2"), false),
                new EmployeeWorkShare("payee-b", "抛光", new BigDecimal("162"), new BigDecimal("2"), false));

        List<PayrollLine> lines = service.settle(actor, "e07-settle1", "MD-20261030-1001", 7L, shares);
        List<PayrollLine> replay = service.settle(actor, "e07-settle1", "MD-20261030-1001", 7L, shares);
        assertThat(replay).extracting(PayrollLine::code).containsExactlyElementsOf(lines.stream().map(PayrollLine::code).toList());
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.lossDeduction()).isEqualByComparingTo("0.00");
            assertThat(line.netAmount()).isEqualByComparingTo(line.grossAmount());
        });
        assertThat(lines.stream().map(PayrollLine::quantity).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("462");
        assertThat(lines.stream().map(PayrollLine::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("924.00");
        assertThat(finance.listPayables()).extracting(payable -> payable.payeeAccount())
                .containsExactlyInAnyOrder("payee-a", "payee-b");
        assertThat(finance.listPayables()).extracting(payable -> payable.amount().toPlainString())
                .containsExactlyInAnyOrder("600.00", "324.00");
        assertThat(replay).hasSize(2);
        assertThat(finance.listPayables()).hasSize(2);
        assertThat(finance.listReceivables()).isEmpty();
        assertThatThrownBy(() -> service.settle(actor, "e07-settle2", "MD-20261030-1001", 7L, shares))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该目标已经核算过工资");
        assertThat(finance.listPayrollLines()).hasSize(2);
    }

    @Test
    void 两名员工的工资分别挂到各自应付() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        EmployeeTargetSettlementService service = service(finance, Map.of(
                "MD-20261030-1013", closure(BusinessKind.PROCESSING, EmployeeClosureKind.CLOSED_NORMAL, "10", "0", null)));
        service.settle(actor, "rf13-split1", "MD-20261030-1013", 7L, List.of(
                new EmployeeWorkShare("payee-a", "抛光", new BigDecimal("6"), new BigDecimal("2"), false),
                new EmployeeWorkShare("payee-b", "抛光", new BigDecimal("4"), new BigDecimal("2"), false)));
        assertThat(finance.listPayables()).anySatisfy(payable -> {
            assertThat(payable.payeeAccount()).isEqualTo("payee-a");
            assertThat(payable.amount()).isEqualByComparingTo("12.00");
        });
        assertThat(finance.listPayables()).anySatisfy(payable -> {
            assertThat(payable.payeeAccount()).isEqualTo("payee-b");
            assertThat(payable.amount()).isEqualByComparingTo("8.00");
        });
        assertThat(finance.listPayables()).extracting(payable -> payable.amount())
                .doesNotContain(new BigDecimal("20.00"));
    }

    @Test
    void 未闭合外协代报返工和全损都不能入账() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        Map<String, EmployeeTargetClosure> closures = Map.of(
                "MD-OPEN", closure(BusinessKind.PROCESSING, EmployeeClosureKind.NOT_CLOSED, "10", "0", null),
                "MD-LOSS", closure(BusinessKind.PROCESSING, EmployeeClosureKind.TOTAL_LOSS, "0", "0", "10"),
                "MD-REWORK", closure(BusinessKind.PROCESSING, EmployeeClosureKind.REWORK_OPEN, "10", "0", null),
                "MD-PROXY", closure(BusinessKind.PROCESSING, EmployeeClosureKind.CLOSED_NORMAL, "10", "0", null),
                "DY-20261030-0001", closure(BusinessKind.SAMPLING, EmployeeClosureKind.CLOSED_NORMAL, "1", "0", null),
                "GJ-20261030-0001", closure(BusinessKind.GOLDWORK, EmployeeClosureKind.CLOSED_NORMAL, "1", "0", null));
        EmployeeTargetSettlementService service = service(finance, closures);
        EmployeeWorkShare share = new EmployeeWorkShare("payee-a", "抛光", new BigDecimal("10"), new BigDecimal("1"), false);
        assertThatThrownBy(() -> service.settle(actor, "e07-open01", "MD-OPEN", 7L, List.of(share)))
                .isInstanceOf(BusinessException.class).hasMessage("加工目标尚未闭合，不能核算工资");
        assertThatThrownBy(() -> service.settle(actor, "e07-loss01", "MD-LOSS", 7L, List.of(
                new EmployeeWorkShare("payee-a", "抛光", new BigDecimal("0.001"), new BigDecimal("1"), false))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有最终成品，不能按定额核算工资");
        List<PayrollLine> reworkPaid = service.settle(actor, "e07-rewk01", "MD-REWORK", 7L, List.of(
                new EmployeeWorkShare("payee-a", "抛光", new BigDecimal("6"), new BigDecimal("1"), false),
                new EmployeeWorkShare("payee-b", "抛光", new BigDecimal("4"), new BigDecimal("1"), false)));
        assertThat(reworkPaid).hasSize(2);
        assertThatThrownBy(() -> service.settle(actor, "e07-prox01", "MD-PROXY", 7L, List.of(
                new EmployeeWorkShare("payee-a", "抛光", new BigDecimal("10"), new BigDecimal("1"), true))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("工资只发给实际加工的人");
        assertThatThrownBy(() -> service.settle(actor, "e07-dy0001", "DY-20261030-0001", 7L, List.of(
                new EmployeeWorkShare("payee-a", "打样", new BigDecimal("1"), new BigDecimal("1"), false))))
                .isInstanceOf(BusinessException.class).hasMessage("打样本版不核算工资");
        assertThatThrownBy(() -> service.settle(actor, "e07-gj0001", "GJ-20261030-0001", 7L, List.of(
                new EmployeeWorkShare("payee-a", "金工", new BigDecimal("1"), new BigDecimal("1"), false))))
                .isInstanceOf(BusinessException.class).hasMessage("金工本版只记录件数，不核算工资");
        assertThat(finance.listPayrollLines()).hasSize(2);
        assertThat(finance.listReceivables()).isEmpty();
    }

    @Test
    void 份额与实作不一致或无权时不入账() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        EmployeeTargetSettlementService service = service(finance, Map.of(
                "MD-20261030-1001", closure(BusinessKind.PROCESSING, EmployeeClosureKind.CLOSED_NORMAL, "462", "20", "38")));
        assertThatThrownBy(() -> service.settle(actor, "e07-short1", "MD-20261030-1001", 7L, List.of(
                new EmployeeWorkShare("payee-a", "抛光", new BigDecimal("424"), new BigDecimal("2"), false))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("工资数量按最终成品核定，定额工价请另行填写");
        EmployeeTargetSettlementService denied = new EmployeeTargetSettlementService(
                target -> Optional.of(closure(BusinessKind.PROCESSING, EmployeeClosureKind.CLOSED_NORMAL, "10", "0", null)),
                finance, actorContext -> {
            throw new PermissionDeniedException("无权核算工资");
        }, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        assertThatThrownBy(() -> denied.settle(actor, "e07-deny01", "MD-20261030-1001", 7L, List.of(
                new EmployeeWorkShare("payee-a", "抛光", new BigDecimal("10"), new BigDecimal("1"), false))))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(finance.listPayrollLines()).isEmpty();
    }

    private static EmployeeTargetSettlementService service(InMemoryFinanceRepository finance,
                                                           Map<String, EmployeeTargetClosure> closures) {
        EmployeeTargetClosureLookup lookup = target -> Optional.ofNullable(closures.get(target));
        EmployeeSettlementAuthorization authorization = actor -> { };
        return new EmployeeTargetSettlementService(lookup, finance, authorization,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
    }

    private static EmployeeTargetClosure closure(BusinessKind kind, EmployeeClosureKind state, String employee,
                                                 String outsourcing, String loss) {
        return new EmployeeTargetClosure(kind, state, new BigDecimal(employee), new BigDecimal(outsourcing),
                loss == null ? null : new BigDecimal(loss), "颗", List.of(
                new EmployeeProcessWork("payee-a", "甲", "POLISH_BEAD", "抛光", employee.equals("462") ? new BigDecimal("300") : new BigDecimal(employee).multiply(new BigDecimal("0.6"))),
                new EmployeeProcessWork("payee-b", "乙", "POLISH_BEAD", "抛光", employee.equals("462") ? new BigDecimal("162") : new BigDecimal(employee).multiply(new BigDecimal("0.4")))), true,
                state == EmployeeClosureKind.CLOSED_NORMAL ? new BigDecimal(employee) : null);
    }
}
