package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/**
 * 外协发外明细。读取只汇总已保存的发外数量，不另写库存、客户账或工资。
 */
public interface OutsourcingDispatchRepository {

    void save(OutsourcingDispatch dispatch, WorkSession session);

    Optional<OutsourcingDispatch> lock(String dispatchId, WorkSession session);

    void update(OutsourcingDispatch next, int expectedVersion, WorkSession session);

    Optional<OutsourcingDispatch> find(String dispatchId);

    Optional<OutsourcingDispatch> findByRequest(String requestId);

    Optional<OutsourcingDispatch> findBySentBag(String bagId);

    List<OutsourcingDispatch> listByTask(String taskId);

    List<OutsourcingDispatch> listByParty(String partyId);
}
