package com.zhubao.erp.security.domain;

import java.util.List;
import java.util.Optional;

public interface AuthAccountPreApprovalApplicationRepository {

    AuthAccountPreApprovalApplication save(AuthAccountPreApprovalApplication application);

    Optional<AuthAccountPreApprovalApplication> findByCode(String applicationCode);

    List<AuthAccountPreApprovalApplication> findAll();
}
