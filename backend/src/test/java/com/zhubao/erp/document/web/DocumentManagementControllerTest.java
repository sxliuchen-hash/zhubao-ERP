package com.zhubao.erp.document.web;

import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.AttachmentContent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class DocumentManagementControllerTest {

    @Test
    void servesSignedContentWithPurposeAndSafeHeaders() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        byte[] bytes = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        when(service.previewContent("ATT-1", 123L, "signed", "preview", "original"))
                .thenReturn(new AttachmentContent("ATT-1", "现场照片.jpg", "image/jpeg", bytes));
        when(service.previewContent("ATT-1", 123L, "download-signed", "download", "original"))
                .thenReturn(new AttachmentContent("ATT-1", "现场照片.jpg", "image/jpeg", bytes));
        DocumentManagementController controller = new DocumentManagementController(service, null, null);

        var preview = controller.content("ATT-1", 123L, "signed", "preview", "original");
        assertThat(preview.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(preview.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(preview.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
        assertThat(preview.getBody()).isEqualTo(bytes);

        var download = controller.content("ATT-1", 123L, "download-signed", "download", "original");
        assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("现场照片.jpg");
        assertThat(download.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void treatsPdfAndUnknownTypeAsAttachment() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        when(service.previewContent("PDF-1", 123L, "signed", "preview", "original"))
                .thenReturn(new AttachmentContent("PDF-1", "证据.pdf", "application/pdf", "%PDF-1.7".getBytes()));
        when(service.previewContent("HTML-1", 123L, "signed", "preview", "original"))
                .thenReturn(new AttachmentContent("HTML-1", "旧附件.html", "text/html", new byte[]{2}));
        when(service.previewContent("FALSE-JPEG", 123L, "signed", "preview", "original"))
                .thenReturn(new AttachmentContent("FALSE-JPEG", "伪装照片.jpg", "image/jpeg", "<html>".getBytes()));
        DocumentManagementController controller = new DocumentManagementController(service, null, null);

        var pdf = controller.content("PDF-1", 123L, "signed", "preview", "original");
        assertThat(pdf.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(pdf.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        var unknown = controller.content("HTML-1", 123L, "signed", "preview", "original");
        assertThat(unknown.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(unknown.getHeaders().getContentType().toString()).isEqualTo("application/octet-stream");
        assertThat(unknown.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        var disguised = controller.content("FALSE-JPEG", 123L, "signed", "preview", "original");
        assertThat(disguised.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(disguised.getHeaders().getContentType().toString()).isEqualTo("application/octet-stream");
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
}
