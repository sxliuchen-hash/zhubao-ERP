package com.zhubao.erp.document.web;

import com.zhubao.erp.document.domain.AttachmentContent;
import com.zhubao.erp.document.domain.AttachmentIntegrityReport;
import com.zhubao.erp.document.domain.AttachmentPreviewUrl;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.document.app.BusinessEvidenceService;
import com.zhubao.erp.document.app.DocumentCaptureService;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.BindEvidenceCommand;
import com.zhubao.erp.document.domain.EvidenceBoard;
import com.zhubao.erp.document.domain.BusinessEvidenceLink;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentCaptureView;
import com.zhubao.erp.document.domain.DocumentManagementSummary;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.domain.StoredContentInventory;
import com.zhubao.erp.document.domain.StoredContentItem;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentManagementController {

    private final DocumentManagementService documentManagementService;
    private final DocumentCaptureService documentCaptureService;
    private final OperatorContextResolver operatorContextResolver;
    private final BusinessDataAccess dataAccess;
    private final BusinessEvidenceService businessEvidenceService;

    public DocumentManagementController(
            DocumentManagementService documentManagementService,
            DocumentCaptureService documentCaptureService,
            OperatorContextResolver operatorContextResolver
    ) {
        this(documentManagementService, documentCaptureService, operatorContextResolver, null, null);
    }

    public DocumentManagementController(
            DocumentManagementService documentManagementService,
            DocumentCaptureService documentCaptureService,
            OperatorContextResolver operatorContextResolver,
            BusinessDataAccess dataAccess
    ) {
        this(documentManagementService, documentCaptureService, operatorContextResolver, dataAccess, null);
    }

    @Autowired
    public DocumentManagementController(
            DocumentManagementService documentManagementService,
            DocumentCaptureService documentCaptureService,
            OperatorContextResolver operatorContextResolver,
            BusinessDataAccess dataAccess,
            BusinessEvidenceService businessEvidenceService
    ) {
        this.documentManagementService = documentManagementService;
        this.documentCaptureService = documentCaptureService;
        this.operatorContextResolver = operatorContextResolver;
        this.dataAccess = dataAccess;
        this.businessEvidenceService = businessEvidenceService;
    }

    @GetMapping("/captures")
    public ApiResponse<List<DocumentCaptureView>> captures(@RequestParam String businessCode, HttpServletRequest request) {
        dataAccess.assertCanRead(operatorContextResolver.requireSession(request), businessCode);
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
        OperatorContext context = operatorContextResolver.requireSession(request);
        dataAccess.assertCanRead(context, businessCode);
        return ApiResponse.ok(documentCaptureService.capture(
                objectType,
                businessCode,
                scene,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize(),
                context
        ));
    }

    @PostMapping(value = "/evidence-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceAttachmentUploadResult> storeEvidenceFile(
            @RequestParam String objectType,
            @RequestParam String businessCode,
            @RequestParam String scene,
            @RequestParam MultipartFile file,
            HttpServletRequest request
    ) throws IOException {
        OperatorContext context = operatorContextResolver.requireSession(request);
        dataAccess.assertCanRead(context, businessCode);
        return ApiResponse.ok(documentCaptureService.storeVerified(
                objectType,
                businessCode,
                scene,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize(),
                context
        ));
    }

    @PostMapping("/evidence-links")
    public ApiResponse<BusinessEvidenceLink> bindEvidence(
            @RequestBody BindEvidenceCommand command,
            HttpServletRequest request
    ) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        return ApiResponse.ok(businessEvidenceService.bind(command, context));
    }

    @GetMapping("/evidence-links")
    public ApiResponse<EvidenceBoard> evidenceLinks(
            @RequestParam String targetType,
            @RequestParam String targetCode,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String operationCode,
            HttpServletRequest request
    ) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        dataAccess.assertCanRead(context, targetCode);
        return ApiResponse.ok(businessEvidenceService.query(
                targetType,
                targetCode,
                stage,
                operationType,
                operationCode,
                context
        ));
    }

    @GetMapping("/summary")
    public ApiResponse<DocumentManagementSummary> summary(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        List<DocumentPackage> packages = visiblePackages(context);
        List<EvidenceAttachment> attachments = visibleAttachments(context);
        return ApiResponse.ok(new DocumentManagementSummary(packages.size(), attachments.size(),
                (int) attachments.stream().filter(row -> !List.of("已关联", "已归档", "已上传", "已下线").contains(row.status())).count(),
                (int) attachments.stream().filter(row -> !"无".equals(row.fallbackAction())).count(),
                (int) documentManagementService.auditEvents().stream().filter(row -> packages.stream()
                        .anyMatch(item -> item.code().equals(row.documentCode()))).count()));
    }

    @GetMapping("/packages")
    public ApiResponse<List<DocumentPackage>> packages(HttpServletRequest request) {
        return ApiResponse.ok(visiblePackages(operatorContextResolver.requireSession(request)));
    }

    @GetMapping("/attachments")
    public ApiResponse<List<EvidenceAttachment>> attachments(HttpServletRequest request) {
        return ApiResponse.ok(visibleAttachments(operatorContextResolver.requireSession(request)));
    }

    @GetMapping("/storage")
    public ApiResponse<StoredContentInventory> storage(HttpServletRequest request) {
        assertManager(operatorContextResolver.requireSession(request));
        return ApiResponse.ok(documentManagementService.storageInventory());
    }

    @GetMapping("/storage/integrity")
    public ApiResponse<AttachmentIntegrityReport> storageIntegrity(HttpServletRequest request) {
        assertManager(operatorContextResolver.requireSession(request));
        return ApiResponse.ok(documentManagementService.storageIntegrity());
    }

    @GetMapping("/audit-events")
    public ApiResponse<List<DocumentAuditEvent>> auditEvents(HttpServletRequest request) {
        assertManager(operatorContextResolver.requireSession(request));
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
            @RequestParam MultipartFile file,
            HttpServletRequest request
    ) throws IOException {
        OperatorContext context = operatorContextResolver.requireSession(request);
        dataAccess.assertCanRead(context, businessCode);
        return ApiResponse.ok(documentManagementService.uploadAttachment(
                documentCode,
                businessType,
                businessCode,
                file.getOriginalFilename(),
                file.getContentType(),
                source,
                context.operator(),
                fallbackAction,
                file.getInputStream(),
                file.getSize()
        ));
    }

    @PostMapping("/attachments/{code}/retirements")
    public ApiResponse<StoredContentItem> retireAttachment(
            @PathVariable String code,
            HttpServletRequest request
    ) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        assertManager(context);
        return ApiResponse.ok(documentManagementService.retireStoredContent(
                code,
                context.operator()
        ));
    }

    @GetMapping("/attachments/{code}/preview-url")
    public ResponseEntity<ApiResponse<AttachmentPreviewUrl>> previewUrl(@PathVariable String code, HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        EvidenceAttachment attachment = documentManagementService.attachments().stream().filter(row -> code.equals(row.code())).findFirst()
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        dataAccess.assertCanRead(context, attachment.businessCode());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(documentManagementService.previewUrl(code)));
    }

    private List<DocumentPackage> visiblePackages(OperatorContext context) {
        return documentManagementService.packages().stream().filter(row -> dataAccess.canRead(context, row.businessCode())).toList();
    }

    private List<EvidenceAttachment> visibleAttachments(OperatorContext context) {
        return documentManagementService.attachments().stream().filter(row -> dataAccess.canRead(context, row.businessCode())).toList();
    }

    private void assertManager(OperatorContext context) {
        if (context.dataScope() != DataScope.ALL || context.role() != RoleCode.ADMIN && context.role() != RoleCode.GENERAL_MANAGER) {
            throw new PermissionDeniedException("附件存储和审计仅向公司范围的管理角色开放");
        }
    }

    @GetMapping("/attachments/{code}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable String code,
            @RequestParam long expiresAt,
            @RequestParam String token,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        AttachmentContent content = documentManagementService.previewContent(code, expiresAt, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mediaType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(download ? "attachment" : "inline")
                        .filename(content.fileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(content.content());
    }
}
