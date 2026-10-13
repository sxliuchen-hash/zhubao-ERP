package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface OrderChangeRepository {

    OrderChange save(OrderChange change);

    /** 在工作会话中保存。内存登记撤销，JDBC 使用同一条未提交连接。 */
    OrderChange save(OrderChange change, WorkSession session);

    Optional<OrderChange> findById(String id);

    Optional<OrderChange> findByRequestId(String requestId);

    List<OrderChange> listByMother(String motherOrderCode);

    int latestAppliedVersion(String businessCode);
}
