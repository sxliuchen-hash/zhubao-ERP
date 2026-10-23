package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface OrderRequirementRepository {

    void saveVersion(OrderRequirementVersion version, WorkSession session);

    Optional<OrderRequirementVersion> current(String motherOrderCode, String workOrderCode);

    void saveRound(ExecutionRound round, WorkSession session);

    List<ExecutionRound> listByMother(String motherOrderCode);

    /** 一次装入全部执行轮次，避免按母单反复查询。 */
    List<ExecutionRound> listAll();

    Optional<ExecutionRound> findRound(String roundWorkOrderCode);
}