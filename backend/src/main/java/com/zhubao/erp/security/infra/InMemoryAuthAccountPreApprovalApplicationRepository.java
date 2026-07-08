package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.pre-approval.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthAccountPreApprovalApplicationRepository
        implements AuthAccountPreApprovalApplicationRepository {

    private final Map<String, AuthAccountPreApprovalApplication> applications = new LinkedHashMap<>();

    @Override
    public synchronized AuthAccountPreApprovalApplication save(AuthAccountPreApprovalApplication application) {
        applications.put(application.applicationCode(), application);
        return application;
    }

    @Override
    public synchronized Optional<AuthAccountPreApprovalApplication> findByCode(String applicationCode) {
        return Optional.ofNullable(applications.get(applicationCode));
    }

    @Override
    public synchronized List<AuthAccountPreApprovalApplication> findAll() {
        return new ArrayList<>(applications.values());
    }
}
