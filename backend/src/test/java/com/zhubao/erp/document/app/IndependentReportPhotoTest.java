package com.zhubao.erp.document.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.document.infra.*;
import com.zhubao.erp.document.web.IndependentReportPhotoController;
import com.zhubao.erp.goldwork.app.*;
import com.zhubao.erp.goldwork.infra.*;
import com.zhubao.erp.sampling.app.*;
import com.zhubao.erp.sampling.infra.*;
import com.zhubao.erp.security.app.*;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentReportPhotoTest {
    @TempDir Path files;
    @ParameterizedTest
    @ValueSource(strings = {"SAMPLING", "GOLDWORK"})
    void 真实照片上传后可报工且不能越客户或跨单使用(String type) throws Exception {
        var actor = new OperatorContext("技工", RoleCode.ADMIN, DataScope.ALL);
        var guard = new PermissionGuard();
        var unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        var customers = new InMemoryMasterDataRepository();
        var customer = customers.saveCustomer(new CustomerProfile(0, "", "客户", "", "", "", "", true));
        var samples = new InMemorySamplingOrderRepository();
        var gold = new InMemoryGoldworkOrderRepository();
        String code = "SAMPLING".equals(type)
                ? new SamplingOrderService(customers, samples, unit, guard).open("photo-sampling-order", actor, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "技工", "", "").samplingCode()
                : new GoldworkOrderService(customers, gold, unit, guard).open("photo-goldwork-order", actor, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "技工", "", "", "").goldworkCode();
        var documents = new InMemoryDocumentManagementRepository();
        var settings = new AttachmentStorageProperties(); settings.setLocalRoot(files.toString());
        var storage = new LocalAttachmentObjectStorage(settings);
        var content = new DocumentManagementService(documents, storage);
        var captures = new DocumentCaptureService(documents, storage, content, new InMemoryBusinessDocumentRepository(),
                new DocumentCaptureProperties(), null, null, samples, gold, guard);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] bytes = output.toByteArray();
        var denied = new OperatorContext("受限", RoleCode.ADMIN, DataScope.CUSTOMER_RANGE, false, "", List.of("999999"));
        assertThatThrownBy(() -> captures.storeVerified(type, code, "PROCESS", "photo.png", "image/png", new java.io.ByteArrayInputStream(bytes), bytes.length, denied))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> captures.storeVerified(type, code, "PROCESS", "photo.png", "image/png", new java.io.ByteArrayInputStream(bytes), bytes.length,
                new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL))).isInstanceOf(PermissionDeniedException.class);
        var actors = mock(OperatorContextResolver.class);
        String module = "SAMPLING".equals(type) ? "sampling" : "goldwork";
        var unauthorized = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/" + module + "/orders/" + code + "/photos");
        when(actors.resolve(unauthorized)).thenReturn(new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL));
        assertThatThrownBy(() -> new com.zhubao.erp.security.web.PermissionGuardInterceptor(actors, guard)
                .preHandle(unauthorized, new org.springframework.mock.web.MockHttpServletResponse(), new Object()))
                .isInstanceOf(PermissionDeniedException.class);
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(actors.requireSession(request)).thenReturn(actor);
        var controller = new IndependentReportPhotoController(captures, actors);
        var file = new MockMultipartFile("file", "photo.png", "image/png", bytes);
        var saved = ("SAMPLING".equals(type) ? controller.sampling(code, file, request) : controller.goldwork(code, file, request)).data();
        var stored = documents.findAttachmentByCode(saved.code()).orElseThrow();
        assertThat(stored.objectType()).isEqualTo(type);
        assertThat(stored.businessCode()).isEqualTo(code);
        assertThat(content.content(saved.code()).content()).isEqualTo(bytes);
        assertThat(captures.storeVerified(type, code, "PROCESS", "photo.png", "image/png", new java.io.ByteArrayInputStream(bytes), bytes.length, actor).code()).isEqualTo(saved.code());
        if ("SAMPLING".equals(type)) {
            var versions = new InMemorySamplingVersionRepository();
            var version = new SamplingVersionService(samples, versions, unit, guard);
            version.add("photo-version-add", actor, code, "MODEL", "尺寸", "", "", "", "", "", "", null);
            version.confirm("photo-version-confirm", actor, code, "MODEL", 1, "", "");
            var reports = new InMemorySamplingOperationRepository();
            var service = new SamplingOperationService(samples, versions, reports, documents, unit, guard);
            String other = new SamplingOrderService(customers, samples, unit, guard).open("photo-other-sampling", actor,
                    customer.customerId(), "CUSTOMER_SUPPLIED", "另一份样品", "技工", "", "").samplingCode();
            version.add("photo-other-version", actor, other, "MODEL", "尺寸", "", "", "", "", "", "", null);
            version.confirm("photo-other-confirm", actor, other, "MODEL", 1, "", "");
            assertThatThrownBy(() -> service.report("photo-wrong-sampling", actor, other, "SAMPLE_MAKE", "MODEL", 1,
                    List.of(), BigDecimal.ONE, "颗", saved.code())).isInstanceOf(com.zhubao.erp.common.error.BusinessException.class);
            assertThat(reports.list(other)).isEmpty();
            service.report("photo-report-sampling", actor, code, "SAMPLE_MAKE", "MODEL", 1, List.of(), BigDecimal.ONE, "颗", saved.code());
            assertThat(reports.list(code)).hasSize(1);
        } else {
            var reports = new InMemoryGoldworkOperationRepository();
            var service = new GoldworkOperationService(gold, reports, documents, unit, guard);
            String other = new GoldworkOrderService(customers, gold, unit, guard).open("photo-other-goldwork", actor,
                    customer.customerId(), "CUSTOMER_SUPPLIED", "另一份戒托", "技工", "", "", "").goldworkCode();
            assertThatThrownBy(() -> service.report("photo-wrong-goldwork", actor, other, "GOLD_CAST", null,
                    BigDecimal.ONE, "克", "足金", saved.code(), false, false, false)).isInstanceOf(com.zhubao.erp.common.error.BusinessException.class);
            assertThat(reports.list(other)).isEmpty();
            service.report("photo-report-goldwork", actor, code, "GOLD_CAST", null, BigDecimal.ONE, "克", "足金", saved.code(), false, false, false);
            assertThat(reports.list(code)).hasSize(1);
        }
    }
}
