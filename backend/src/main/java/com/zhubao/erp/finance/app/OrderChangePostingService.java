package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderChange;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 改单生效时追加应收和工资。不覆盖原来的账单行和已确认工资。
 */
@Service
public class OrderChangePostingService {

    private final FinanceRepository finance;
    private final FinanceBillingService billing;
    private final BusinessDocumentRepository documents;
    private final AtomicLong sequence = new AtomicLong();

    public OrderChangePostingService(
            FinanceRepository finance,
            FinanceBillingService billing,
            BusinessDocumentRepository documents
    ) {
        this.finance = finance;
        this.billing = billing;
        this.documents = documents;
    }

    public void post(OrderChange change, String operator, WorkSession session) {
        if (change.amountUnchanged() || (change.customerDelta().signum() == 0 && change.workerDelta().signum() == 0)) {
            return;
        }
        MotherOrderRecord mother = documents.findMotherOrderByCode(change.motherOrderCode())
                .orElseThrow(() -> new BusinessException("改单母单不存在，不能入账"));
        long customerId = mother.customerId() == null ? 0L : mother.customerId();
        String customerName = mother == null || mother.customerName() == null ? "" : mother.customerName();
        String orderCode = change.workOrderCode().isBlank() ? change.motherOrderCode() : change.workOrderCode();
        if (change.customerDelta().signum() < 0) {
            billing.reverseForChange(
                    change.receivableCode(), change.customerDelta().abs(), change.id(), operator, session,
                    customerId, change.motherOrderCode(), orderCode
            );
        } else if (change.customerDelta().signum() > 0) {
            String sourceKey = "CHANGE|" + change.id() + "|" + orderCode + "|" + change.processName();
            if (finance.findReceivableBySourceKey(sourceKey).isEmpty()) {
                finance.saveReceivable(new ReceivableLine(
                        FinanceTexts.code("FIN-CHG", sequence),
                        customerId,
                        customerName,
                        YearMonth.now().toString(),
                        "CHANGE",
                        sourceKey,
                        change.motherOrderCode(),
                        orderCode,
                        change.processName(),
                        BigDecimal.ONE,
                        ChargeUnit.JOB,
                        FinanceTexts.money(change.customerDelta()),
                        FinanceTexts.money(change.customerDelta()),
                        "",
                        "改单 " + change.id(),
                        operator,
                        FinanceTexts.now()
                ), session);
            }
        }
        if (change.workerDelta().signum() != 0) {
            String sourceKey = "PAY-ADJ|" + change.id();
            boolean already = finance.listPayrollLines().stream().anyMatch(line -> sourceKey.equals(line.sourceKey()));
            if (!already) {
                String month = YearMonth.now().toString();
                boolean confirmed = finance.findPayrollRun(month).map(run -> run.confirmed()).orElse(false);
                BigDecimal amount = FinanceTexts.money(change.workerDelta());
                finance.savePayrollLine(new PayrollLine(
                        FinanceTexts.code("FIN-PAY-ADJ", sequence),
                        month,
                        change.workerName(),
                        orderCode,
                        change.processName(),
                        change.motherOrderCode(),
                        customerId,
                        BigDecimal.ONE,
                        ChargeUnit.JOB,
                        amount,
                        amount,
                        FinanceTexts.ZERO,
                        amount,
                        confirmed,
                        sourceKey
                ), session);
                if (confirmed) {
                    String payableSource = "PAYROLL-ADJ|" + change.id();
                    if (finance.findPayableBySource(payableSource).isEmpty()) {
                        finance.savePayable(new Payable(
                                FinanceTexts.code("FIN-AP", sequence),
                                "PAYROLL",
                                month,
                                change.workerName(),
                                payableSource,
                                customerId,
                                change.motherOrderCode(),
                                amount,
                                FinanceTexts.ZERO,
                                operator,
                                FinanceTexts.now()
                        ), session);
                    }
                }
            }
        }
    }
}
