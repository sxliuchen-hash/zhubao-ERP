package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.CustomerReturnSource;
import com.zhubao.erp.business.domain.CustomerReturnStatus;
import com.zhubao.erp.business.domain.DownstreamFact;
import com.zhubao.erp.business.infra.InMemoryCustomerReturnRepository;
import com.zhubao.erp.finance.domain.CustomerDeliveryBill;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillKind;
import com.zhubao.erp.finance.infra.InMemoryCustomerDeliveryBillRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordedDeliveryDownstreamTest {

    @Test
    void 影响查询读取账单和退回且退款保持待核() {
        InMemoryCustomerDeliveryBillRepository bills = new InMemoryCustomerDeliveryBillRepository();
        InMemoryCustomerReturnRepository returns = new InMemoryCustomerReturnRepository();
        RecordedDeliveryDownstream downstream = new RecordedDeliveryDownstream(bills, returns);
        String deliveryId = "FH-20261030-1901";
        assertThat(downstream.chargeFact(deliveryId)).isEqualTo(DownstreamFact.ABSENT);
        assertThat(downstream.refundFact(deliveryId)).isEqualTo(DownstreamFact.UNDECIDED);
        assertThat(downstream.consumedQuantity("FH-20261030-1901-1")).isEqualByComparingTo("0");

        bills.save(new CustomerDeliveryBill("CB" + "a".repeat(32), "rf19-bill1", "CB" + "a".repeat(32),
                deliveryId, 7L, CustomerDeliveryBillKind.INITIAL, new BigDecimal("80.00"), 1), List.of(), null);
        returns.save(new CustomerReturnSource(
                "CR" + "b".repeat(32), "rf19-return", deliveryId, "FH-20261030-1901-1", "BAG-RF19",
                new BigDecimal("3"), new BigDecimal("1.200"), CustomerReturnStatus.PENDING_REVIEW,
                CustomerReturnSource.UNDECIDED_BASIS, 1), null);

        assertThat(downstream.chargeFact(deliveryId)).isEqualTo(DownstreamFact.CONFIRMED);
        assertThat(downstream.refundFact(deliveryId)).isEqualTo(DownstreamFact.UNDECIDED);
        assertThat(downstream.consumedQuantity("FH-20261030-1901-1")).isEqualByComparingTo("3.000");
    }
}
