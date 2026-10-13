package com.zhubao.erp.business.domain;

import java.util.List;
import java.util.Optional;

public interface OrderChangeRepository {

    OrderChange save(OrderChange change);

    Optional<OrderChange> findById(String id);

    Optional<OrderChange> findByRequestId(String requestId);

    List<OrderChange> listByMother(String motherOrderCode);
}
