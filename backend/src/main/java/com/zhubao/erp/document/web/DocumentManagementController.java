package com.zhubao.erp.document.web;

import com.zhubao.erp.document.domain.AttachmentStreamContent;
import com.zhubao.erp.document.domain.AttachmentMediaPolicy;
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
import com.zhubao.erp.document.domain.DocumentAttachmentPage;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentManagementController {

    private static final int MAX_ATTACHMENT_PAGE_SIZE = 100;

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

    @GetMapping("/attachments/page")
    public ApiResponse<AttachmentPageResponse> attachmentPage(
            @RequestParam String documentCode,
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request
    ) {
        String normalizedDocumentCode = documentCode == null ? "" : documentCode.trim();
        if (normalizedDocumentCode.isEmpty()) throw new BusinessException("文档包编号不能为空");
        if (afterId < 0) throw new BusinessException("附件游标不能小于 0");
        if (limit < 1 || limit > MAX_ATTACHMENT_PAGE_SIZE) {
            throw new BusinessException("附件分页大小必须在 1 到 " + MAX_ATTACHMENT_PAGE_SIZE + " 之间");
        }

        OperatorContext context = operatorContextResolver.requireSession(request);
        List<DocumentAttachmentPage.Entry> visible = new ArrayList<>();
        long scanAfterId = afterId;
        boolean hasMore = false;
        int scanLimit = limit + 1;
        while (visible.size() <= limit) {
            DocumentAttachmentPage page = documentManagementService.attachmentsPage(
                    normalizedDocumentCode,
                    scanAfterId,
                    scanLimit
            );
            if (page.entries().isEmpty()) {
                hasMore = false;
                break;
            }

            long lastScannedId = scanAfterId;
            for (DocumentAttachmentPage.Entry entry : page.entries()) {
                lastScannedId = entry.id();
                if (dataAccess.canRead(context, entry.attachment().businessCode())) {
                    visible.add(entry);
                    if (visible.size() > limit) {
                        hasMore = true;
                        break;
                    }
                }
            }
            if (visible.size() > limit) break;
            scanAfterId = lastScannedId;
            if (!page.hasMore()) {
                hasMore = false;
                break;
            }
        }

        int itemCount = Math.min(limit, visible.size());
        List<EvidenceAttachment> items = visible.subList(0, itemCount).stream()
                .map(DocumentAttachmentPage.Entry::attachment)
                .toList();
        Long nextCursor = hasMore && itemCount > 0 ? visible.get(itemCount - 1).id() : null;
        return ApiResponse.ok(new AttachmentPageResponse(items, nextCursor, hasMore));
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

    @PostMapping("/attachments/{code}/reconciliations")
    public ApiResponse<EvidenceAttachment> reconcileAttachment(
            @PathVariable String code,
            HttpServletRequest request
    ) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        assertManager(context);
        return ApiResponse.ok(documentManagementService.reconcilePendingAttachment(code, context.operator()));
    }

    @GetMapping("/attachments/{code}/preview-url")
    public ResponseEntity<ApiResponse<AttachmentPreviewUrl>> previewUrl(
            @PathVariable String code,
            @RequestParam(defaultValue = "preview") String purpose,
            HttpServletRequest request
    ) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        EvidenceAttachment attachment = documentManagementService.attachments().stream().filter(row -> code.equals(row.code())).findFirst()
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        dataAccess.assertCanRead(context, attachment.businessCode());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(documentManagementService.previewUrl(code, purpose)));
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
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable String code,
            @RequestParam long expiresAt,
            @RequestParam String token,
            @RequestParam String purpose,
            @RequestParam String resolution,
            @RequestHeader(value = "Range", required = false) String range
    ) {
        AttachmentStreamContent content = documentManagementService.previewContentStream(code, expiresAt, token, purpose, resolution);
        boolean verifiedType = isVerifiedMediaType(content);
        boolean download = "download".equals(purpose) || !verifiedType || !isInlineMediaType(content.mediaType());
        boolean video = verifiedType && content.mediaType().startsWith("video/");
        ByteRange selected = video && range != null && !range.isBlank() ? parseRange(range, content.size()) : null;
        if (video && range != null && !range.isBlank() && selected == null) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + content.size())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        }
        long start = selected == null ? 0 : selected.start();
        long length = selected == null ? content.size() : selected.end() - start + 1;
        ResponseEntity.BodyBuilder response = ResponseEntity.status(selected == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT)
                .contentType(verifiedType
                        ? MediaType.parseMediaType(content.mediaType()) : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(download ? "attachment" : "inline")
                        .filename(content.fileName(), StandardCharsets.UTF_8)
                        .build().toString());
        if (video) {
            response.header(HttpHeaders.ACCEPT_RANGES, "bytes");
        }
        if (selected != null) {
            response.header(HttpHeaders.CONTENT_RANGE, "bytes " + selected.start() + "-" + selected.end() + "/" + content.size());
        }
        return response.body(output -> content.write(start, length, output));
    }

    private boolean isInlineMediaType(String mediaType) {
        return mediaType != null && List.of("image/jpeg", "image/png", "video/mp4", "video/webm").contains(mediaType);
    }

    private boolean isVerifiedMediaType(AttachmentStreamContent content) {
        String mediaType = content.mediaType();
        return (isInlineMediaType(mediaType) || "application/pdf".equals(mediaType))
                && AttachmentMediaPolicy.matchesHeader(mediaType, content.header(), content.header().length);
    }

    private ByteRange parseRange(String range, long size) {
        if (size <= 0 || !range.startsWith("bytes=") || range.indexOf(',') >= 0) {
            return null;
        }
        String value = range.substring("bytes=".length()).trim();
        int divider = value.indexOf('-');
        if (divider < 0) {
            return null;
        }
        try {
            String first = value.substring(0, divider).trim();
            String last = value.substring(divider + 1).trim();
            if (first.isEmpty()) {
                long suffix = Long.parseLong(last);
                return suffix > 0 ? new ByteRange(Math.max(0, size - suffix), size - 1) : null;
            }
            long start = Long.parseLong(first);
            long end = last.isEmpty() ? size - 1 : Math.min(Long.parseLong(last), size - 1);
            return start >= 0 && start < size && end >= start ? new ByteRange(start, end) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record ByteRange(long start, long end) {
    }

    public record AttachmentPageResponse(
            List<EvidenceAttachment> items,
            Long nextCursor,
            boolean hasMore
    ) {
        public AttachmentPageResponse {
            items = List.copyOf(items);
        }
    }
}
