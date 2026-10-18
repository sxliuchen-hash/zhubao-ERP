package com.zhubao.erp.document.web;

import com.zhubao.erp.document.app.BusinessEvidenceService;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.AttachmentStreamContent;
import com.zhubao.erp.document.domain.DocumentAttachmentPage;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.DocumentPackagePage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceLinkPage;
import com.zhubao.erp.document.domain.EvidencePageFilter;
import com.zhubao.erp.document.domain.HistoricalEvidencePage;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DocumentManagementControllerTest {

    @Test
    void servesSignedContentWithPurposeAndSafeHeaders() throws Exception {
        DocumentManagementService service = mock(DocumentManagementService.class);
        byte[] bytes = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        when(service.previewContentStream("ATT-1", 123L, "signed", "preview", "original"))
                .thenReturn(stream("ATT-1", "现场照片.jpg", "image/jpeg", bytes));
        when(service.previewContentStream("ATT-1", 123L, "download-signed", "download", "original"))
                .thenReturn(stream("ATT-1", "现场照片.jpg", "image/jpeg", bytes));
        DocumentManagementController controller = new DocumentManagementController(service, null, null);

        var preview = controller.content("ATT-1", 123L, "signed", "preview", "original", null);
        assertThat(preview.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(preview.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(preview.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
        assertThat(bodyOf(preview)).isEqualTo(bytes);

        var download = controller.content("ATT-1", 123L, "download-signed", "download", "original", null);
        assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("现场照片.jpg");
        assertThat(download.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void treatsPdfAndUnknownTypeAsAttachment() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        when(service.previewContentStream("PDF-1", 123L, "signed", "preview", "original"))
                .thenReturn(stream("PDF-1", "证据.pdf", "application/pdf", "%PDF-1.7".getBytes()));
        when(service.previewContentStream("HTML-1", 123L, "signed", "preview", "original"))
                .thenReturn(stream("HTML-1", "旧附件.html", "text/html", new byte[]{2}));
        when(service.previewContentStream("FALSE-JPEG", 123L, "signed", "preview", "original"))
                .thenReturn(stream("FALSE-JPEG", "伪装照片.jpg", "image/jpeg", "<html>".getBytes()));
        DocumentManagementController controller = new DocumentManagementController(service, null, null);

        var pdf = controller.content("PDF-1", 123L, "signed", "preview", "original", null);
        assertThat(pdf.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(pdf.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        var unknown = controller.content("HTML-1", 123L, "signed", "preview", "original", null);
        assertThat(unknown.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(unknown.getHeaders().getContentType().toString()).isEqualTo("application/octet-stream");
        assertThat(unknown.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        var disguised = controller.content("FALSE-JPEG", 123L, "signed", "preview", "original", null);
        assertThat(disguised.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(disguised.getHeaders().getContentType().toString()).isEqualTo("application/octet-stream");
    }

    @Test
    void streamsVideoAndSupportsSingleByteRange() throws Exception {
        DocumentManagementService service = mock(DocumentManagementService.class);
        byte[] bytes = new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 1, 2, 3, 4};
        when(service.previewContentStream("VIDEO-1", 123L, "signed", "preview", "original"))
                .thenReturn(stream("VIDEO-1", "现场.mp4", "video/mp4", bytes));
        DocumentManagementController controller = new DocumentManagementController(service, null, null);

        var full = controller.content("VIDEO-1", 123L, "signed", "preview", "original", null);
        assertThat(full.getStatusCode().value()).isEqualTo(200);
        assertThat(full.getHeaders().getContentLength()).isEqualTo(bytes.length);
        assertThat(full.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(bodyOf(full)).isEqualTo(bytes);

        var range = controller.content("VIDEO-1", 123L, "signed", "preview", "original", "bytes=4-7");
        assertThat(range.getStatusCode().value()).isEqualTo(206);
        assertThat(range.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 4-7/16");
        assertThat(range.getHeaders().getContentLength()).isEqualTo(4);
        assertThat(bodyOf(range)).isEqualTo(Arrays.copyOfRange(bytes, 4, 8));

        var suffix = controller.content("VIDEO-1", 123L, "signed", "preview", "original", "bytes=-4");
        assertThat(suffix.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 12-15/16");
        assertThat(bodyOf(suffix)).isEqualTo(Arrays.copyOfRange(bytes, 12, 16));

        var invalid = controller.content("VIDEO-1", 123L, "signed", "preview", "original", "bytes=16-");
        assertThat(invalid.getStatusCode().value()).isEqualTo(416);
        assertThat(invalid.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */16");
        assertThat(invalid.getBody()).isNull();
        assertThat(controller.content("VIDEO-1", 123L, "signed", "preview", "original", "bytes=0-1,4-5")
                .getStatusCode().value()).isEqualTo(416);
    }

    @Test
    void bindsRangeHeaderAndStreamsPartialResponseThroughMvc() throws Exception {
        DocumentManagementService service = mock(DocumentManagementService.class);
        byte[] bytes = new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 1, 2, 3, 4};
        when(service.previewContentStream("VIDEO-1", 123L, "signed", "preview", "original"))
                .thenReturn(stream("VIDEO-1", "现场.mp4", "video/mp4", bytes));
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentManagementController(service, null, null)).build();
        var pending = mvc.perform(get("/api/documents/attachments/VIDEO-1/content")
                        .param("expiresAt", "123")
                        .param("token", "signed")
                        .param("purpose", "preview")
                        .param("resolution", "original")
                        .header(HttpHeaders.RANGE, "bytes=4-7"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 4-7/16"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(Arrays.copyOfRange(bytes, 4, 8)));
    }

    private AttachmentStreamContent stream(String code, String fileName, String mediaType, byte[] bytes) {
        return new AttachmentStreamContent(code, fileName, mediaType, bytes.length,
                Arrays.copyOf(bytes, Math.min(12, bytes.length)),
                (offset, length, output) -> output.write(bytes, (int) offset, (int) length));
    }

    private byte[] bodyOf(org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> response)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        return output.toByteArray();
    }

    @Test
    void previewAddressRequiresBusinessReadScope() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        var attachment = new InMemoryDocumentManagementRepository().attachments().get(0);
        when(resolver.requireSession(request)).thenReturn(operator);
        when(service.attachments()).thenReturn(java.util.List.of(attachment));
        doThrow(new PermissionDeniedException("无权读取其他客户附件"))
                .when(access).assertCanRead(operator, attachment.businessCode());
        DocumentManagementController controller = new DocumentManagementController(service, null, resolver, access);

        assertThatThrownBy(() -> controller.previewUrl(attachment.code(), "preview", request))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> controller.previewUrl(attachment.code(), "download", request))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void storageIntegrityRequiresCompanyManager() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        DocumentManagementController controller = new DocumentManagementController(service, null, resolver);
        when(resolver.requireSession(request)).thenReturn(
                new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.ALL));

        assertThatThrownBy(() -> controller.storageIntegrity(request))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> controller.reconcileAttachment("ATT-1", request))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void evidenceLinkPagePassesFiltersAndChecksBusinessScopeBeforeService() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        BusinessEvidenceService evidence = mock(BusinessEvidenceService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        EvidenceLinkPage page = new EvidenceLinkPage(List.of(), 18L, true);
        EvidencePageFilter filter = new EvidencePageFilter("2026-10-01", "2026-10-18", "image", "何琴");
        when(resolver.requireSession(request)).thenReturn(operator);
        when(evidence.queryLinkedPage(
                "MOTHER_ORDER", "MD-11E", "INTAKE", "inbound_confirm", "OP-11E", 17L, 7, operator, filter
        )).thenReturn(page);
        DocumentManagementController controller = new DocumentManagementController(
                service, null, resolver, access, evidence
        );

        var result = controller.evidenceLinksPage(
                "MOTHER_ORDER", "MD-11E", "INTAKE", "inbound_confirm", "OP-11E",
                "2026-10-01", "2026-10-18", "image", "何琴", 17L, 7, request
        );

        assertThat(result.data()).isSameAs(page);
        var order = inOrder(resolver, access, evidence);
        order.verify(resolver).requireSession(request);
        order.verify(access).assertCanRead(operator, "MD-11E");
        order.verify(evidence).queryLinkedPage(
                "MOTHER_ORDER", "MD-11E", "INTAKE", "inbound_confirm", "OP-11E", 17L, 7, operator, filter
        );
    }

    @Test
    void historicalEvidencePageUsesStableDefaultsAndBindsMvcRoute() throws Exception {
        DocumentManagementService service = mock(DocumentManagementService.class);
        BusinessEvidenceService evidence = mock(BusinessEvidenceService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        HistoricalEvidencePage page = new HistoricalEvidencePage(List.of(), null, false);
        when(resolver.requireSession(any(HttpServletRequest.class))).thenReturn(operator);
        when(evidence.queryHistoricalPage("MOTHER_ORDER", "MD-11E", 0L, 30, operator,
                EvidencePageFilter.empty())).thenReturn(page);
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentManagementController(
                service, null, resolver, access, evidence
        )).build();

        mvc.perform(get("/api/documents/evidence-links/historical/page")
                        .param("targetType", "MOTHER_ORDER")
                        .param("targetCode", "MD-11E"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.hasMore").value(false));

        verify(access).assertCanRead(operator, "MD-11E");
        verify(evidence).queryHistoricalPage("MOTHER_ORDER", "MD-11E", 0L, 30, operator,
                EvidencePageFilter.empty());
    }

    @Test
    void evidenceLinkPageRejectsOutOfScopeBeforeCallingService() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        BusinessEvidenceService evidence = mock(BusinessEvidenceService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        when(resolver.requireSession(request)).thenReturn(operator);
        doThrow(new PermissionDeniedException("无权读取该单据"))
                .when(access).assertCanRead(operator, "MD-OUTSIDE");
        DocumentManagementController controller = new DocumentManagementController(
                service, null, resolver, access, evidence
        );

        assertThatThrownBy(() -> controller.evidenceLinksPage(
                "MOTHER_ORDER", "MD-OUTSIDE", "", "", "", "", "", "", "", 0L, 30, request
        )).isInstanceOf(PermissionDeniedException.class);
        verify(evidence, never()).queryLinkedPage(
                "MOTHER_ORDER", "MD-OUTSIDE", "", "", "", 0L, 30, operator, EvidencePageFilter.empty()
        );
    }

    @Test
    void historicalEvidencePageDelegatesInvalidCursorAndLimitToService() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        BusinessEvidenceService evidence = mock(BusinessEvidenceService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        when(resolver.requireSession(request)).thenReturn(operator);
        when(evidence.queryHistoricalPage("MOTHER_ORDER", "MD-11E", -1L, 101, operator,
                EvidencePageFilter.empty()))
                .thenThrow(new com.zhubao.erp.common.error.BusinessException("分页参数无效"));
        DocumentManagementController controller = new DocumentManagementController(
                service, null, resolver, access, evidence
        );

        assertThatThrownBy(() -> controller.historicalEvidencePage(
                "MOTHER_ORDER", "MD-11E", "", "", "", "", -1L, 101, request
        )).isInstanceOf(com.zhubao.erp.common.error.BusinessException.class);
        verify(evidence).queryHistoricalPage("MOTHER_ORDER", "MD-11E", -1L, 101, operator,
                EvidencePageFilter.empty());
    }

    @Test
    void pagesOnlyAuthorizedAttachmentsAcrossInterleavedRowsAndKeepsCursorOnLastVisibleRow() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        EvidenceAttachment deniedFirst = attachment("ATT-DENY-001", "BUS-DENY-001");
        EvidenceAttachment allowedFirst = attachment("ATT-ALLOW-001", "BUS-ALLOW-001");
        EvidenceAttachment deniedMiddle = attachment("ATT-DENY-002", "BUS-DENY-002");
        EvidenceAttachment allowedSecond = attachment("ATT-ALLOW-002", "BUS-ALLOW-002");
        EvidenceAttachment allowedThird = attachment("ATT-ALLOW-003", "BUS-ALLOW-003");
        when(resolver.requireSession(request)).thenReturn(operator);
        when(access.canRead(operator, "BUS-DENY-001")).thenReturn(false);
        when(access.canRead(operator, "BUS-DENY-002")).thenReturn(false);
        when(access.canRead(operator, "BUS-ALLOW-001")).thenReturn(true);
        when(access.canRead(operator, "BUS-ALLOW-002")).thenReturn(true);
        when(access.canRead(operator, "BUS-ALLOW-003")).thenReturn(true);
        when(service.attachmentsPage("DOC-PAGE-001", 0, 3)).thenReturn(new DocumentAttachmentPage(List.of(
                new DocumentAttachmentPage.Entry(1, deniedFirst),
                new DocumentAttachmentPage.Entry(2, allowedFirst),
                new DocumentAttachmentPage.Entry(3, deniedMiddle)
        ), true));
        when(service.attachmentsPage("DOC-PAGE-001", 3, 3)).thenReturn(new DocumentAttachmentPage(List.of(
                new DocumentAttachmentPage.Entry(4, allowedSecond),
                new DocumentAttachmentPage.Entry(5, allowedThird)
        ), false));
        DocumentManagementController controller = new DocumentManagementController(service, null, resolver, access);

        var first = controller.attachmentPage("DOC-PAGE-001", 0, 2, request).data();
        assertThat(first.items()).extracting(EvidenceAttachment::code)
                .containsExactly("ATT-ALLOW-001", "ATT-ALLOW-002");
        assertThat(first.nextCursor()).isEqualTo(4L);
        assertThat(first.hasMore()).isTrue();

        when(service.attachmentsPage("DOC-PAGE-001", 4, 3)).thenReturn(new DocumentAttachmentPage(List.of(
                new DocumentAttachmentPage.Entry(5, allowedThird)
        ), false));
        var last = controller.attachmentPage("DOC-PAGE-001", first.nextCursor(), 2, request).data();
        assertThat(last.items()).extracting(EvidenceAttachment::code).containsExactly("ATT-ALLOW-003");
        assertThat(last.nextCursor()).isNull();
        assertThat(last.hasMore()).isFalse();
    }

    @Test
    void pagesOnlyAuthorizedPackagesAcrossInterleavedRowsAndKeepsCursorOnLastVisibleRow() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        DocumentPackage deniedFirst = packageEntry("DOC-DENY-001", "BUS-DENY-001");
        DocumentPackage allowedFirst = packageEntry("DOC-ALLOW-001", "BUS-ALLOW-001");
        DocumentPackage deniedMiddle = packageEntry("DOC-DENY-002", "BUS-DENY-002");
        DocumentPackage allowedSecond = packageEntry("DOC-ALLOW-002", "BUS-ALLOW-002");
        DocumentPackage allowedThird = packageEntry("DOC-ALLOW-003", "BUS-ALLOW-003");
        when(resolver.requireSession(request)).thenReturn(operator);
        when(access.canRead(operator, "BUS-DENY-001")).thenReturn(false);
        when(access.canRead(operator, "BUS-DENY-002")).thenReturn(false);
        when(access.canRead(operator, "BUS-ALLOW-001")).thenReturn(true);
        when(access.canRead(operator, "BUS-ALLOW-002")).thenReturn(true);
        when(access.canRead(operator, "BUS-ALLOW-003")).thenReturn(true);
        when(service.packagesPage(0, 3)).thenReturn(new DocumentPackagePage(List.of(
                new DocumentPackagePage.Entry(1, deniedFirst),
                new DocumentPackagePage.Entry(2, allowedFirst),
                new DocumentPackagePage.Entry(3, deniedMiddle)
        ), true));
        when(service.packagesPage(3, 3)).thenReturn(new DocumentPackagePage(List.of(
                new DocumentPackagePage.Entry(4, allowedSecond),
                new DocumentPackagePage.Entry(5, allowedThird)
        ), false));
        DocumentManagementController controller = new DocumentManagementController(service, null, resolver, access);

        var first = controller.packagePage(0, 2, request).data();
        assertThat(first.items()).extracting(DocumentPackage::code)
                .containsExactly("DOC-ALLOW-001", "DOC-ALLOW-002");
        assertThat(first.nextCursor()).isEqualTo(4L);
        assertThat(first.hasMore()).isTrue();

        when(service.packagesPage(4, 3)).thenReturn(new DocumentPackagePage(List.of(
                new DocumentPackagePage.Entry(5, allowedThird)
        ), false));
        var last = controller.packagePage(first.nextCursor(), 2, request).data();
        assertThat(last.items()).extracting(DocumentPackage::code).containsExactly("DOC-ALLOW-003");
        assertThat(last.nextCursor()).isNull();
        assertThat(last.hasMore()).isFalse();
    }

    @Test
    void filtersPackagePagesByKeywordBeforeApplyingBusinessReadScope() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        DocumentPackage deniedFirst = packageEntry("DOC-命中-拒绝", "BUS-DENY-001");
        DocumentPackage allowedFirst = packageEntry("DOC-命中-允许-001", "BUS-ALLOW-001");
        DocumentPackage deniedMiddle = packageEntry("DOC-命中-拒绝-002", "BUS-DENY-002");
        DocumentPackage allowedSecond = packageEntry("DOC-命中-允许-002", "BUS-ALLOW-002");
        DocumentPackage allowedThird = packageEntry("DOC-命中-允许-003", "BUS-ALLOW-003");
        when(resolver.requireSession(request)).thenReturn(operator);
        when(access.canRead(operator, "BUS-DENY-001")).thenReturn(false);
        when(access.canRead(operator, "BUS-DENY-002")).thenReturn(false);
        when(access.canRead(operator, "BUS-ALLOW-001")).thenReturn(true);
        when(access.canRead(operator, "BUS-ALLOW-002")).thenReturn(true);
        when(access.canRead(operator, "BUS-ALLOW-003")).thenReturn(true);
        when(service.packagesPage("命中", 0, 3)).thenReturn(new DocumentPackagePage(List.of(
                new DocumentPackagePage.Entry(1, deniedFirst),
                new DocumentPackagePage.Entry(2, allowedFirst),
                new DocumentPackagePage.Entry(3, deniedMiddle)
        ), true));
        when(service.packagesPage("命中", 3, 3)).thenReturn(new DocumentPackagePage(List.of(
                new DocumentPackagePage.Entry(4, allowedSecond),
                new DocumentPackagePage.Entry(5, allowedThird)
        ), false));
        DocumentManagementController controller = new DocumentManagementController(service, null, resolver, access);

        var first = controller.packagePage(" 命中 ", 0, 2, request).data();
        assertThat(first.items()).extracting(DocumentPackage::code)
                .containsExactly("DOC-命中-允许-001", "DOC-命中-允许-002");
        assertThat(first.nextCursor()).isEqualTo(4L);
        assertThat(first.hasMore()).isTrue();

        when(service.packagesPage("命中", 4, 3)).thenReturn(new DocumentPackagePage(List.of(
                new DocumentPackagePage.Entry(5, allowedThird)
        ), false));
        var last = controller.packagePage("命中", first.nextCursor(), 2, request).data();
        assertThat(last.items()).extracting(DocumentPackage::code)
                .containsExactly("DOC-命中-允许-003");
        assertThat(last.nextCursor()).isNull();
        assertThat(last.hasMore()).isFalse();
    }

    @Test
    void treatsBlankPackageKeywordAsNoFilter() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext operator = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.SELF);
        DocumentPackage firstPackage = packageEntry("DOC-BLANK-001", "BUS-BLANK-001");
        when(resolver.requireSession(request)).thenReturn(operator);
        when(access.canRead(operator, "BUS-BLANK-001")).thenReturn(true);
        when(service.packagesPage(0, 3)).thenReturn(new DocumentPackagePage(List.of(
                new DocumentPackagePage.Entry(1, firstPackage)
        ), false));
        DocumentManagementController controller = new DocumentManagementController(service, null, resolver, access);

        var page = controller.packagePage("  ", 0, 2, request).data();
        assertThat(page.items()).extracting(DocumentPackage::code).containsExactly("DOC-BLANK-001");
        assertThat(page.nextCursor()).isNull();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void rejectsInvalidAttachmentPageLimitAndCursor() {
        DocumentManagementController controller = new DocumentManagementController(mock(DocumentManagementService.class), null, null, mock(BusinessDataAccess.class));
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThatThrownBy(() -> controller.attachmentPage("DOC-PAGE-001", -1, 2, request))
                .isInstanceOf(com.zhubao.erp.common.error.BusinessException.class);
        assertThatThrownBy(() -> controller.attachmentPage("DOC-PAGE-001", 0, 101, request))
                .isInstanceOf(com.zhubao.erp.common.error.BusinessException.class);
    }

    @Test
    void rejectsInvalidPackagePageLimitAndCursor() {
        DocumentManagementController controller = new DocumentManagementController(mock(DocumentManagementService.class), null, null, mock(BusinessDataAccess.class));
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThatThrownBy(() -> controller.packagePage(-1, 2, request))
                .isInstanceOf(com.zhubao.erp.common.error.BusinessException.class);
        assertThatThrownBy(() -> controller.packagePage(0, 101, request))
                .isInstanceOf(com.zhubao.erp.common.error.BusinessException.class);
    }

    private EvidenceAttachment attachment(String code, String businessCode) {
        return new EvidenceAttachment(
                code,
                "DOC-PAGE-001",
                "分页测试",
                businessCode,
                code + ".jpg",
                "image/jpeg",
                "local://" + code,
                "hot 180d -> archive",
                "测试上传",
                "已上传",
                "测试员",
                "sha256:" + code,
                "2026-10-18 10:00",
                "无",
                "green",
                null
        );
    }

    private DocumentPackage packageEntry(String code, String businessCode) {
        return new DocumentPackage(
                code,
                "分页文档包",
                "分页测试",
                businessCode,
                "测试员",
                "验证文档包稳定游标分页。",
                2,
                1,
                "待补附件",
                "保留 3 年",
                "2026-10-18 10:00",
                "amber"
        );
    }
}
