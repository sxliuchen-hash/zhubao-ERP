package com.zhubao.erp.security.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerPortalBinding;
import com.zhubao.erp.security.domain.CustomerPortalBindingRepository;
import com.zhubao.erp.security.domain.CustomerPortalIdentityAction;
import com.zhubao.erp.security.domain.CustomerPortalIdentityActionRepository;
import com.zhubao.erp.security.domain.CustomerPortalRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 受限客户身份。管理员按稳定客户编号绑定，一个账号只对应一个客户主体。
 * 不签发凭据，不按名称或自填编号认领，也不写入库存、客户账或工资。
 */
@Service
public class CustomerPortalIdentityService {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AtomicLong auditSequence = new AtomicLong();
    private final AuthUserAccountRepository accounts;
    private final CustomerPortalBindingRepository bindings;
    private final MasterDataRepository customers;
    private final CustomerPortalIdentityActionRepository actions;
    private final AuthSessionService sessions;

    public CustomerPortalIdentityService(AuthUserAccountRepository accounts, CustomerPortalBindingRepository bindings,
            MasterDataRepository customers, CustomerPortalIdentityActionRepository actions, AuthSessionService sessions) {
        this.accounts = accounts;
        this.bindings = bindings;
        this.customers = customers;
        this.actions = actions;
        this.sessions = sessions;
    }

    /** 绑定已核实的客户档案编号。同一对账号和客户再次绑定保持原记录，不重置凭据。 */
    public CustomerPortalBinding bind(OperatorContext operator, String username, Long customerId) {
        requireAdmin(operator);
        String normalized = requireUsername(username);
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("客户绑定不能为空");
        }
        CustomerProfile profile = customers.findCustomer(customerId)
                .orElseThrow(() -> new BusinessException("客户主体不存在，不能按名称或自填编号认领"));
        if (!profile.enabled()) {
            throw new BusinessException("客户主体已停用，不能绑定");
        }
        CustomerPortalBinding existing = bindings.findByUsername(normalized).orElse(null);
        if (existing != null) {
            if (existing.customerId() != customerId) {
                throw new BusinessException("一个客户账号只能绑定一个客户主体");
            }
            if (!existing.enabled()) {
                throw new BusinessException("客户身份已停用，不能重复绑定");
            }
            AuthUserAccount account = requirePortalAccount(normalized);
            CustomerPortalRules.assertRestricted(account, existing);
            return existing;
        }
        AuthUserAccount current = accounts.findByUsername(normalized).orElse(null);
        if (current != null && current.role() != RoleCode.CUSTOMER_PORTAL) {
            throw new BusinessException("不能借用内部账号作为客户身份");
        }
        if (bindings.findByCustomerId(customerId).isPresent()) {
            throw new BusinessException("同客户多人访问尚未核定，不能再绑定账号");
        }
        if (current != null) {
            throw new BusinessException("不能借用内部账号作为客户身份");
        }
        AuthUserAccount account = new AuthUserAccount(
                normalized,
                CustomerPortalRules.UNISSUED_PASSWORD,
                profile.name(),
                RoleCode.CUSTOMER_PORTAL,
                DataScope.CUSTOMER_RANGE,
                true,
                false,
                Instant.now(),
                0,
                null,
                List.of(Long.toString(customerId)),
                false,
                false,
                List.of(),
                List.of()
        );
        CustomerPortalBinding binding = new CustomerPortalBinding(
                normalized,
                customerId,
                true,
                operator.operator(),
                DISPLAY_TIME.format(Instant.now())
        );
        actions.commit(new CustomerPortalIdentityAction(account, binding, audit(
                normalized, operator, "绑定稳定客户主体 " + customerId)));
        return bindings.findByUsername(normalized).orElseThrow(() -> new BusinessException("客户绑定没有写入"));
    }

    /** 客户名称、客户编码或客户自己填写的编号都不能自动认领。 */
    public void refusePresentedClaim(OperatorContext operator, String username, String presented) {
        requireAdmin(operator);
        requireUsername(username);
        if (presented == null || presented.isBlank()) {
            throw new BusinessException("客户绑定不能为空");
        }
        throw new BusinessException("不能按客户名称或自填编号自动认领");
    }

    /** 凭据交付方式尚未核定。不生成口令，也不表示签发成功。 */
    public void issueCredential(OperatorContext operator, String username) {
        requireAdmin(operator);
        requireUsername(username);
        throw new BusinessException("客户凭据交付与找回方式尚未核定，不能签发凭据");
    }

    /** 找回方式尚未核定。不重置口令，也不表示找回成功。 */
    public void recoverCredential(OperatorContext operator, String username) {
        requireAdmin(operator);
        requireUsername(username);
        throw new BusinessException("客户凭据交付与找回方式尚未核定，不能找回凭据");
    }

    /** 停用客户身份并撤销该账号仍有效的会话。 */
    public int disable(OperatorContext operator, String username) {
        requireAdmin(operator);
        String normalized = requireUsername(username);
        AuthUserAccount account = accounts.findByUsername(normalized)
                .orElseThrow(() -> new BusinessException("客户身份不存在：" + normalized));
        if (account.role() != RoleCode.CUSTOMER_PORTAL) {
            throw new BusinessException("内部账号不能按客户身份停用");
        }
        CustomerPortalBinding binding = bindings.findByUsername(normalized)
                .orElseThrow(() -> new BusinessException("客户身份没有绑定客户主体"));
        if (account.enabled() || binding.enabled()) {
            actions.commit(new CustomerPortalIdentityAction(
                    account.withEnabled(false),
                    binding.withEnabled(false),
                    audit(normalized, operator, "停用客户身份")
            ));
        }
        if (sessions == null) {
            throw new BusinessException("会话服务未配置，客户身份已停用但会话尚未撤销");
        }
        return sessions.revokeSessionsForUsername(normalized, "客户身份已停用");
    }

    /** 读取已绑定且仍受限的客户身份。参数不能扩大到其他客户。 */
    public OperatorContext portalOperator(String username) {
        String normalized = requireUsername(username);
        AuthUserAccount account = accounts.findByUsername(normalized)
                .orElseThrow(() -> new PermissionDeniedException("不是客户身份"));
        if (account.role() != RoleCode.CUSTOMER_PORTAL) {
            throw new PermissionDeniedException("不是客户身份");
        }
        CustomerPortalBinding binding = bindings.findByUsername(normalized)
                .orElseThrow(() -> new PermissionDeniedException("客户身份没有绑定客户主体"));
        CustomerPortalRules.assertRestricted(account, binding);
        return new OperatorContext(
                account.operator(),
                RoleCode.CUSTOMER_PORTAL,
                DataScope.CUSTOMER_RANGE,
                false,
                "",
                List.of(Long.toString(binding.customerId())),
                List.of(),
                account.username(),
                "",
                ""
        );
    }

    private AuthUserAccount requirePortalAccount(String username) {
        AuthUserAccount account = accounts.findByUsername(username)
                .orElseThrow(() -> new BusinessException("客户身份不存在：" + username));
        if (account.role() != RoleCode.CUSTOMER_PORTAL) {
            throw new BusinessException("不能借用内部账号作为客户身份");
        }
        return account;
    }

    private AuthAuditEvent audit(String username, OperatorContext operator, String reason) {
        return new AuthAuditEvent(
                "PORTAL-" + DISPLAY_TIME.format(Instant.now()).replace(" ", "-").replace(":", "")
                        + "-" + String.format("%04d", auditSequence.incrementAndGet()),
                "CUSTOMER_PORTAL",
                username,
                operator.operator(),
                operator.role().name(),
                "SUCCESS",
                reason,
                DISPLAY_TIME.format(Instant.now()),
                ""
        );
    }

    private static void requireAdmin(OperatorContext operator) {
        if (operator == null || operator.role() != RoleCode.ADMIN) {
            throw new PermissionDeniedException("只有管理员可以办理客户身份");
        }
    }

    private static String requireUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BusinessException("客户账号不能为空");
        }
        return normalized;
    }
}
