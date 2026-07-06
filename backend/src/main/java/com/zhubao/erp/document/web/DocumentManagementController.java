package com.zhubao.erp.document.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentManagementSummary;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
