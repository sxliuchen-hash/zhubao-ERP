package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.domain.CustomerAuthorizationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Manages the customer authorization table and resolves the effective customer scope for a user.
 *
 * <p>Authorization rows are authoritative for {@code CUSTOMER_RANGE} users; the account's
 * {@code customerScope} text field remains a backward-compatible fallback when a user has no rows.</p>
 */
@Service
public class CustomerAuthorizationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CustomerAuthorizationRepository repository;

    public CustomerAuthorizationService(CustomerAuthorizationRepository repository) {
        this.repository = repository;
    }

    public CustomerAuthorization grant(String username, String customerCode, String customerName, String grantedBy) {
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

    public boolean revoke(String username, String customerCode) {
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
     * Effective customer scope for a user: authorization-table customer codes if present, otherwise
     * the provided account-level fallback scope.
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

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
