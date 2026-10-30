package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialReturn;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.company-material-issue.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCompanyMaterialIssueRepository implements CompanyMaterialIssueRepository {

    private final ConcurrentHashMap<String, CompanyMaterialIssue> issues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompanyMaterialReturn> returns = new ConcurrentHashMap<>();

    @Override
    public void insert(CompanyMaterialIssue issue, WorkSession session) {
        if (issues.putIfAbsent(issue.issueId(), issue) != null) {
            throw new BusinessException("领用单已存在");
        }
        if (session != null) {
            session.onRollback(() -> issues.remove(issue.issueId(), issue));
        }
    }

    @Override
    public Optional<CompanyMaterialIssue> find(String issueId) {
        return Optional.ofNullable(issues.get(issueId));
    }

    @Override
    public CompanyMaterialIssue addReturned(String issueId, BigDecimal quantity, int expectedVersion, WorkSession session) {
        synchronized (issues) {
            CompanyMaterialIssue current = issues.get(issueId);
            if (current == null) {
                throw new BusinessException("领用单不存在");
            }
            if (current.version() != expectedVersion) {
                throw new BusinessException("领用单已被其他人修改，请刷新后重试");
            }
            if (current.returnedQuantity().add(quantity).compareTo(current.quantity()) > 0) {
                throw new BusinessException("退回数量不能超过可退");
            }
            CompanyMaterialIssue next = new CompanyMaterialIssue(
                    current.issueId(), current.itemId(), current.batchCode(), current.fromWarehouse(), current.workshopCode(),
                    current.quantity(), current.returnedQuantity().add(quantity), current.unit(), current.weight(),
                    current.targetCode(), current.version() + 1);
            issues.put(issueId, next);
            if (session != null) {
                session.onRollback(() -> {
                    synchronized (issues) {
                        issues.put(issueId, current);
                    }
                });
            }
            return next;
        }
    }

    @Override
    public void insert(CompanyMaterialReturn returned, WorkSession session) {
        if (returns.putIfAbsent(returned.returnId(), returned) != null) {
            throw new BusinessException("退回单已存在");
        }
        if (session != null) {
            session.onRollback(() -> returns.remove(returned.returnId(), returned));
        }
    }

    @Override
    public Optional<CompanyMaterialReturn> findReturn(String returnId) {
        return Optional.ofNullable(returns.get(returnId));
    }
}
