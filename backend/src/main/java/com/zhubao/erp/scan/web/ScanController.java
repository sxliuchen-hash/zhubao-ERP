package com.zhubao.erp.scan.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.scan.app.OperationProgressService;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.app.ScanContextService;
import com.zhubao.erp.scan.domain.SaveOperationProgressCommand;
import com.zhubao.erp.scan.domain.ScanContextRequest;
import com.zhubao.erp.scan.domain.ScanContextResponse;
import com.zhubao.erp.scan.domain.ScanProgressRecord;
import com.zhubao.erp.scan.domain.ScanResolveRequest;
import com.zhubao.erp.scan.domain.ScanResolveResponse;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private final ScanCodeParser scanCodeParser;
    private final ScanContextService scanContextService;
    private final OperationProgressService operationProgressService;
    private final AuthSessionService authSessionService;

    public ScanController(
            ScanCodeParser scanCodeParser,
            ScanContextService scanContextService,
            OperationProgressService operationProgressService,
            AuthSessionService authSessionService
    ) {
        this.scanCodeParser = scanCodeParser;
        this.scanContextService = scanContextService;
        this.operationProgressService = operationProgressService;
        this.authSessionService = authSessionService;
    }

    /** 只解析格式。allowed 固定为 true，不是权限结论。 */
    @PostMapping("/resolve")
    public ApiResponse<ScanResolveResponse> resolve(@Valid @RequestBody ScanResolveRequest request) {
        return ApiResponse.ok(scanCodeParser.resolve(request.content(), request.scene()));
    }

    /** 查询真实单据上下文。未登录拒绝，查询本身不写账。 */
    @PostMapping("/context")
    public ApiResponse<ScanContextResponse> context(
            @Valid @RequestBody ScanContextRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthUserAccount account = authSessionService.currentAccount(httpRequest.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new PermissionDeniedException("请先登录后再访问业务数据"));
        return ApiResponse.ok(scanContextService.query(request, account));
    }

    /**
     * 追加过程说明。登录后按阶段校验岗位权限。
     * 不交付工序、不提交质检、不计算出货，也不把发货准备写成出货单。
     */
    @PostMapping("/progress-records")
    public ApiResponse<ScanProgressRecord> saveProgress(
            @RequestBody SaveOperationProgressCommand command,
            HttpServletRequest httpRequest
    ) {
        AuthUserAccount account = authSessionService.currentAccount(httpRequest.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new PermissionDeniedException("请先登录后再记录过程"));
        return ApiResponse.ok(operationProgressService.save(command, account));
    }
}
