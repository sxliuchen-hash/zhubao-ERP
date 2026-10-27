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
        throw new BusinessException("改单不再直接记加工费或工资，费用在交货后结单");
    }
}
