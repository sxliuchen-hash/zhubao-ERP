package com.zhubao.erp.document.web;

import com.zhubao.erp.document.domain.AttachmentContent;
import com.zhubao.erp.document.domain.AttachmentPreviewUrl;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentManagementSummary;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public DocumentManagementController(DocumentManagementService documentManagementService) {
        this.documentManagementService = documentManagementService;
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

    @GetMapping("/attachments/{code}/preview-url")
    public ApiResponse<AttachmentPreviewUrl> previewUrl(@PathVariable String code) {
        return ApiResponse.ok(documentManagementService.previewUrl(code));
    }

    @GetMapping("/attachments/{code}/content")
    public ResponseEntity<byte[]> content(@PathVariable String code) {
        AttachmentContent content = documentManagementService.content(code);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.fileName() + "\"")
                .body(content.content());
    }
}
