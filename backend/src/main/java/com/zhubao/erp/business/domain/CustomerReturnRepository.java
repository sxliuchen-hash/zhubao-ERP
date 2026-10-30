package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/**
 * 客户退货来源。读取不改原出库，也不写工资。
 */
public interface CustomerReturnRepository {

    void save(CustomerReturnSource source, WorkSession session);

    Optional<CustomerReturnSource> find(String returnId);

    Optional<CustomerReturnSource> findByRequest(String requestId);

    List<CustomerReturnSource> listByOutbound(String outboundCode);
}
