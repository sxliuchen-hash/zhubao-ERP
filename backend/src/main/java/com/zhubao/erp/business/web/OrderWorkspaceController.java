package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.OrderWorkspaceQueryService;
import com.zhubao.erp.business.domain.OrderWorkspaceAccess;
import com.zhubao.erp.business.domain.OrderWorkspacePage;
import com.zhubao.erp.business.domain.OrderWorkspaceQuery;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单中心只读列表。身份来自登录会话，不接受客户端传入的操作人或数据范围。
 */
@RestController
@RequestMapping("/api/business/order-workspace")
public class OrderWorkspaceController {

    private final OrderWorkspaceQueryService orderWorkspaceQueryService;
    private final OperatorContextResolver operatorContextResolver;
    private final AuthSessionService authSessionService;

    public OrderWorkspaceController(
            OrderWorkspaceQueryService orderWorkspaceQueryService,
            OperatorContextResolver operatorContextResolver,
            AuthSessionService authSessionService
    ) {
        this.orderWorkspaceQueryService = orderWorkspaceQueryService;
        this.operatorContextResolver = operatorContextResolver;
        this.authSessionService = authSessionService;
    }

    @GetMapping
    public ApiResponse<OrderWorkspacePage> list(
            HttpServletRequest request,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) String facet,
            @RequestParam(required = false) String dueBefore,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size
    ) {
        OperatorContext operator = operatorContextResolver.requireSession(request);
        AuthSession session = authSessionService.session(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new PermissionDeniedException("请先登录后再访问业务数据"));
        return ApiResponse.ok(orderWorkspaceQueryService.query(
                operator,
                new OrderWorkspaceAccess(session.menuCodes(), session.allowedProcesses()),
                OrderWorkspaceQuery.parse(view, query, customerId, process, facet, dueBefore, page, size)
        ));
    }
}
