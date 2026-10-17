package com.zhubao.erp.document.web;

import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.AttachmentContent;
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
import static org.mockito.Mockito.when;

class DocumentManagementControllerTest {

    @Test
    void servesSignedContentInlineOrAsDownloadWithoutCaching() {
        DocumentManagementService service = mock(DocumentManagementService.class);
        byte[] bytes = "photo".getBytes();
        when(service.previewContent("ATT-1", 123L, "signed"))
                .thenReturn(new AttachmentContent("ATT-1", "现场照片.jpg", "image/jpeg", bytes));
        DocumentManagementController controller = new DocumentManagementController(service, null, null);

        var preview = controller.content("ATT-1", 123L, "signed", false);
        assertThat(preview.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(preview.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
        assertThat(preview.getBody()).isEqualTo(bytes);

        var download = controller.content("ATT-1", 123L, "signed", true);
        assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("现场照片.jpg");
        assertThat(download.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
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
