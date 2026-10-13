package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.domain.CustomerAuthorizationRepository;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 维护客户授权表并解析账号的有效客户范围。
 * {@code CUSTOMER_RANGE} 优先使用授权表；无授权行时兼容账号上的 {@code customerScope}。
 */
@Service
public class CustomerAuthorizationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CustomerAuthorizationRepository repository;

    public CustomerAuthorizationService(CustomerAuthorizationRepository repository) {
        this.repository = repository;
    }

    CustomerAuthorizationRepository repository() {
        return repository;
    }

    public CustomerAuthorization grant(String username, String customerCode, String customerName, OperatorContext operatorContext) {
        requireAdmin(operatorContext);
        return grant(username, customerCode, customerName, operatorContext.operator());
    }

    CustomerAuthorization grant(String username, String customerCode, String customerName, String grantedBy) {
        require(username, "username");
        require(customerCode, "customerCode");
        CustomerAuthorization authorization = new CustomerAuthorization(
                username.trim(),
                customerCode.trim(),
                blankToEmpty(customerName),
                blankToDefault(grantedBy, "system"),
                TIME_FORMATTER.format(LocalDateTime.now())
        );
        return repository.grant(authorization);
    }

    public boolean revoke(String username, String customerCode, OperatorContext operatorContext) {
        requireAdmin(operatorContext);
        return revoke(username, customerCode);
    }

    boolean revoke(String username, String customerCode) {
        require(username, "username");
        require(customerCode, "customerCode");
        return repository.revoke(username.trim(), customerCode.trim());
    }

    public List<CustomerAuthorization> authorizationsFor(String username) {
        require(username, "username");
        return repository.findByUsername(username.trim());
    }

    public List<CustomerAuthorization> allAuthorizations() {
        return repository.findAll();
    }

    /**
     * 有授权行时返回授权表客户编码，否则返回账号上的兼容范围。
     */
    public List<String> effectiveCustomerScope(String username, List<String> accountFallbackScope) {
        if (username == null || username.isBlank()) {
            return accountFallbackScope == null ? List.of() : List.copyOf(accountFallbackScope);
        }
        List<String> granted = repository.findByUsername(username.trim()).stream()
                .map(CustomerAuthorization::customerCode)
                .toList();
        if (!granted.isEmpty()) {
            return granted;
        }
        return accountFallbackScope == null ? List.of() : List.copyOf(accountFallbackScope);
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " 不能为空");
        }
    }

    private void requireAdmin(OperatorContext operatorContext) {
        if (operatorContext == null || operatorContext.role() != RoleCode.ADMIN) {
            throw new PermissionDeniedException("仅系统管理员可以管理客户授权");
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
