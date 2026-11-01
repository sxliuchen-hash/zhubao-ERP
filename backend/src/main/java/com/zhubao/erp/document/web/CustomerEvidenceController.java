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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.nio.charset.StandardCharsets;

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
    public ResponseEntity<StreamingResponseBody> download(@RequestBody DownloadRequest request, HttpServletRequest httpRequest) {
        var content = service.downloadContent(operators.requireSession(httpRequest), request.grantId(), request.attachmentCode(), request.ticket());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mediaType())).contentLength(content.size())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(output -> content.write(0, content.size(), output));
    }

    @PostMapping("/api/security/customer-evidence/grants/{grantId}/revocations")
    public ApiResponse<Void> revoke(@PathVariable String grantId, HttpServletRequest request) {
        service.revoke(operators.resolve(request), grantId);
        return ApiResponse.ok(null);
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
