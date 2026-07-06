package com.zhubao.erp.approval.domain;

import java.util.List;
import java.util.Optional;

public interface ApprovalTaskRepository {

    ApprovalTask save(ApprovalTask task);

    Optional<ApprovalTask> findByCode(String approvalCode);

    List<ApprovalTask> findAll();
}
