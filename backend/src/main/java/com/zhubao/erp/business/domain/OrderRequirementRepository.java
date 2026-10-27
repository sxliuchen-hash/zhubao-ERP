package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface OrderRequirementRepository {

    void saveVersion(OrderRequirementVersion version, WorkSession session);

    Optional<OrderRequirementVersion> current(String motherOrderCode, String workOrderCode);

    /** 在工作单元中读取当前要求。确认结单时锁定最新版本行。 */
    default Optional<OrderRequirementVersion> current(String motherOrderCode, String workOrderCode, WorkSession session, boolean lock) {
        if (session != null && session.connection().isPresent()) {
            throw new IllegalStateException("订单要求读取必须使用工作单元连接");
        }
        return current(motherOrderCode, workOrderCode);
    }

    void saveRound(ExecutionRound round, WorkSession session);

    List<ExecutionRound> listByMother(String motherOrderCode);

    /** 在工作单元中读取执行轮次。确认结单时锁定该母单的轮次行。 */
    default List<ExecutionRound> listByMother(String motherOrderCode, WorkSession session, boolean lock) {
        if (session != null && session.connection().isPresent()) {
            throw new IllegalStateException("执行轮次读取必须使用工作单元连接");
        }
        return listByMother(motherOrderCode);
    }

    /** 一次装入全部执行轮次，避免按母单反复查询。 */
    List<ExecutionRound> listAll();

    Optional<ExecutionRound> findRound(String roundWorkOrderCode);
}