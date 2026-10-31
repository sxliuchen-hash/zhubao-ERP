package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.CustomerReturnRepository;
import com.zhubao.erp.business.domain.CustomerReturnSource;
import com.zhubao.erp.business.domain.DeliveryDownstream;
import com.zhubao.erp.business.domain.DownstreamFact;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillRepository;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 用已保存的账单和退回回答交付更正影响。退回目前只有待复核，不能当成已确认退款。
 */
@Component
public class RecordedDeliveryDownstream implements DeliveryDownstream {

    private final CustomerDeliveryBillRepository bills;
    private final CustomerReturnRepository returns;

    public RecordedDeliveryDownstream(CustomerDeliveryBillRepository bills, CustomerReturnRepository returns) {
        this.bills = bills;
        this.returns = returns;
    }

    @Override
    public BigDecimal consumedQuantity(String sourceCode) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CustomerReturnSource source : returns.listByOutbound(sourceCode)) {
            sum = sum.add(source.quantity());
        }
        return sum;
    }

    @Override
    public DownstreamFact chargeFact(String deliveryId) {
        return bills.findInitial(deliveryId).isPresent() ? DownstreamFact.CONFIRMED : DownstreamFact.ABSENT;
    }

    @Override
    public DownstreamFact refundFact(String deliveryId) {
        return DownstreamFact.UNDECIDED;
    }
}
