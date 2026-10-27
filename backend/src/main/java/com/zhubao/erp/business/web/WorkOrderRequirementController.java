package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.ChildRequirementService;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business/work-orders")
public class WorkOrderRequirementController {

    private final ChildRequirementService requirements;
    private final OperatorContextResolver operatorContextResolver;
    private final AuthSessionService authSessionService;

    public WorkOrderRequirementController(
            ChildRequirementService requirements,
            OperatorContextResolver operatorContextResolver,
            AuthSessionService authSessionService
    ) {
        this.requirements = requirements;
        this.operatorContextResolver = operatorContextResolver;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/{code}/requirement")
    public ApiResponse<RequirementView> update(
            @PathVariable String code,
            @RequestBody RequirementRequest body,
            HttpServletRequest request
    ) {
        if (body == null) {
            throw new BusinessException("加工要求不能为空");
        }
        ChildWorkOrderRecord saved = requirements.edit(code, body.requirement(), body.requestId(), account(request));
        return ApiResponse.ok(new RequirementView(saved.code(), saved.motherOrderCode(), saved.processingRequirement()));
    }

    private AuthUserAccount account(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        AuthUserAccount account = authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再修改加工要求"));
        return new AuthUserAccount(
                account.username(), account.passwordHash(), account.operator(), account.role(), account.dataScope(),
                account.enabled(), account.demoAccount(), account.passwordChangedAt(), account.failedLoginCount(),
                account.lockedUntil(), context.customerScope(), account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(), account.allowedProcesses(), context.coverDuties(),
                context.teamCode(), context.departmentCode()
        );
    }

    public record RequirementRequest(String requirement, String requestId) {
    }

    public record RequirementView(String code, String motherOrderCode, String processingRequirement) {
    }
}
