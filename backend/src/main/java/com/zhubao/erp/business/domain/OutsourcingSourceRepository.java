package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/**
 * 外协方和任务来源。读取不写发外、库存、客户账或工资。
 */
public interface OutsourcingSourceRepository {

    void saveParty(OutsourcingParty party, int expectedVersion, WorkSession session);

    Optional<OutsourcingParty> findParty(String partyId);

    Optional<OutsourcingParty> lockParty(String partyId, WorkSession session);

    void saveTask(OutsourcingTask task, WorkSession session);

    Optional<OutsourcingTask> findTask(String taskId);

    Optional<OutsourcingTask> findTaskByRequest(String requestId);

    List<OutsourcingTask> listByTarget(String targetCode);
}
