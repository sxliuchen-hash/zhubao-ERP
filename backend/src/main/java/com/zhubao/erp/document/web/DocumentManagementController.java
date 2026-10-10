package com.zhubao.erp.document.web;

import com.zhubao.erp.document.domain.AttachmentContent;
import com.zhubao.erp.document.domain.AttachmentPreviewUrl;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.document.app.DocumentCaptureService;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentCaptureView;
import com.zhubao.erp.document.domain.DocumentManagementSummary;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.domain.StoredContentInventory;
import com.zhubao.erp.document.domain.StoredContentItem;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentManagementController {

    private final DocumentManagementService documentManagementService;
    private final DocumentCaptureService documentCaptureService;
    private final OperatorContextResolver operatorContextResolver;

    public DocumentManagementController(
            DocumentManagementService documentManagementService,
            DocumentCaptureService documentCaptureService,
            OperatorContextResolver operatorContextResolver
    ) {
        this.documentManagementService = documentManagementService;
        this.documentCaptureService = documentCaptureService;
        this.operatorContextResolver = operatorContextResolver;
    }

    @GetMapping("/captures")
    public ApiResponse<List<DocumentCaptureView>> captures(@RequestParam String businessCode) {
        return ApiResponse.ok(documentCaptureService.list(businessCode));
    }

    @PostMapping(value = "/captures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceAttachmentUploadResult> capture(
            @RequestParam String objectType,
            @RequestParam String businessCode,
            @RequestParam String scene,
            @RequestParam MultipartFile file,
            HttpServletRequest request
    ) throws IOException {
        return ApiResponse.ok(documentCaptureService.capture(
                objectType,
                businessCode,
                scene,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize(),
                operatorContextResolver.resolve(request)
        ));
    }

    @GetMapping("/summary")
    public ApiResponse<DocumentManagementSummary> summary() {
        return ApiResponse.ok(documentManagementService.summary());
    }

    @GetMapping("/packages")
    public ApiResponse<List<DocumentPackage>> packages() {
        return ApiResponse.ok(documentManagementService.packages());
    }

    @GetMapping("/attachments")
    public ApiResponse<List<EvidenceAttachment>> attachments() {
        return ApiResponse.ok(documentManagementService.attachments());
    }

    @GetMapping("/storage")
    public ApiResponse<StoredContentInventory> storage() {
        return ApiResponse.ok(documentManagementService.storageInventory());
    }

    @GetMapping("/audit-events")
    public ApiResponse<List<DocumentAuditEvent>> auditEvents() {
        return ApiResponse.ok(documentManagementService.auditEvents());
    }

    @PostMapping(value = "/attachments/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceAttachmentUploadResult> uploadAttachment(
            @RequestParam String documentCode,
            @RequestParam String businessType,
            @RequestParam String businessCode,
            @RequestParam String source,
            @RequestParam String owner,
            @RequestParam(required = false) String fallbackAction,
            @RequestParam MultipartFile file
    ) throws IOException {
        return ApiResponse.ok(documentManagementService.uploadAttachment(
                documentCode,
                businessType,
                businessCode,
                file.getOriginalFilename(),
                file.getContentType(),
                source,
                owner,
                fallbackAction,
                file.getBytes()
        ));
    }

    @PostMapping("/attachments/{code}/retirements")
    public ApiResponse<StoredContentItem> retireAttachment(
            @PathVariable String code,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(documentManagementService.retireStoredContent(
                code,
                operatorContextResolver.resolve(request).operator()
        ));
    }

    @GetMapping("/attachments/{code}/preview-url")
    public ApiResponse<AttachmentPreviewUrl> previewUrl(@PathVariable String code) {
        return ApiResponse.ok(documentManagementService.previewUrl(code));
    }

    @GetMapping("/attachments/{code}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable String code,
            @RequestParam long expiresAt,
            @RequestParam String token
    ) {
        AttachmentContent content = documentManagementService.previewContent(code, expiresAt, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.fileName() + "\"")
                .body(content.content());
    }
}
