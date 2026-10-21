package com.zhubao.erp.approval.domain;

import java.util.List;
import java.util.Optional;

public interface ApprovalTaskRepository {

    ApprovalTask save(ApprovalTask task);

    /**
     * 仅当任务仍为待审批时写入决策。并发决策只有一次成功。
     */
    Optional<ApprovalTask> decideIfWaiting(ApprovalTask task);

    Optional<ApprovalTask> findByCode(String approvalCode);

    List<ApprovalTask> findAll();
}
