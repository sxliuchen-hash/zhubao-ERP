package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 客户按次账单。确认前的草稿不在这里，也不写工资。
 */
public interface CustomerDeliveryBillRepository {

    void save(CustomerDeliveryBill bill, List<CustomerDeliveryBillLine> lines, WorkSession session);

    Optional<CustomerDeliveryBill> find(String billId);

    Optional<CustomerDeliveryBill> findByRequest(String requestId);

    Optional<CustomerDeliveryBill> findInitial(String deliveryId);

    List<CustomerDeliveryBill> listByRoot(String rootBillId);

    List<CustomerDeliveryBillLine> lines(String billId);

    void saveCoverage(CustomerBillCoverage coverage, BigDecimal receivableAmount, WorkSession session);

    BigDecimal coveredTotal(String receivableCode);
}
