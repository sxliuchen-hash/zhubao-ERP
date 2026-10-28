package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FinanceCommands.PayrollRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveWorkerRateCommand;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.PayrollGenerateResult;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.WorkerRate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工人单价和月度工资。确认后锁定，并形成应付，不生成银行代发。
 */
@Service
public class FinancePayrollService {

    private final FinanceRepository finance;

    public FinancePayrollService(FinanceRepository finance) {
        this.finance = finance;
    }

    public List<WorkerRate> rates() {
        return finance.listWorkerRates();
    }

    public List<PayrollLine> lines(String yearMonth) {
        String month = FinanceTexts.blank(yearMonth);
        if (!month.isEmpty()) {
            FinanceTexts.month(month);
        }
        return finance.listPayrollLines().stream()
                .filter(line -> month.isEmpty() || month.equals(line.yearMonth()))
                .toList();
    }

    public WorkerRate saveRate(SaveWorkerRateCommand command, String operator) {
        throw new BusinessException("员工工价已停止维护，历史工价只可查询。工费请在交货后按单填写");
    }

    public PayrollGenerateResult generate(PayrollRunCommand command) {
        throw new BusinessException("已停止按交工和工价重算工资，请在交货后结单");
    }

    public PayrollRun confirm(PayrollRunCommand command, String operator) {
        throw new BusinessException("已停止整月确认工资，员工应付随交货后结单生成");
    }

}
