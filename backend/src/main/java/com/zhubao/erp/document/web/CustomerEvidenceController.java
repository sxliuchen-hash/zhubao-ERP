package com.zhubao.erp.document.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.document.app.CustomerEvidenceAccessService;
import com.zhubao.erp.document.domain.CustomerEvidenceDetail;
import com.zhubao.erp.document.domain.CustomerEvidenceDownload;
import com.zhubao.erp.document.domain.CustomerEvidenceGrant;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户证据获准与下载。详情和下载分开，只拿附件编号不能取文件。
 */
@RestController
public class CustomerEvidenceController {

    private final CustomerEvidenceAccessService service;
    private final OperatorContextResolver operators;

    public CustomerEvidenceController(CustomerEvidenceAccessService service, OperatorContextResolver operators) {
        this.service = service;
        this.operators = operators;
    }

    @PostMapping("/api/security/customer-evidence/grants")
    public ApiResponse<CustomerEvidenceGrant> grant(@RequestBody GrantRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(service.grant(operators.resolve(httpRequest), request.customerId(), request.attachmentCode()));
    }

    @GetMapping("/api/customer-portal/evidence")
    public ApiResponse<CustomerEvidenceDetail> detail(HttpServletRequest httpRequest) {
        return ApiResponse.ok(service.detail(operators.requireSession(httpRequest)));
    }

    @PostMapping("/api/customer-portal/evidence/downloads")
    public ApiResponse<CustomerEvidenceDownload> download(@RequestBody DownloadRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(service.download(operators.requireSession(httpRequest),
                request.grantId(), request.attachmentCode(), request.ticket()));
    }

    @PostMapping("/api/customer-portal/evidence/direct-downloads")
    public ApiResponse<CustomerEvidenceDownload> direct(@RequestBody DirectRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(service.downloadByAttachmentCode(operators.requireSession(httpRequest), request.attachmentCode()));
    }

    public record GrantRequest(@NotNull Long customerId, @NotBlank String attachmentCode) {
    }

    public record DownloadRequest(@NotBlank String grantId, @NotBlank String attachmentCode, @NotBlank String ticket) {
    }

    public record DirectRequest(@NotBlank String attachmentCode) {
    }
}
