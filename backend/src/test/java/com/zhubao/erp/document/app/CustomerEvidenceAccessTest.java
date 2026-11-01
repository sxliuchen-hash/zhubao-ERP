package com.zhubao.erp.document.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.CustomerEvidenceDetail;
import com.zhubao.erp.document.domain.CustomerEvidenceGrant;
import com.zhubao.erp.document.domain.DesignArtifactReference;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryCustomerEvidenceGrantRepository;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.CustomerPortalIdentityService;
import com.zhubao.erp.security.app.NavigationMenuService;
import com.zhubao.erp.security.app.PasswordExpiryPolicy;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.app.PasswordPolicy;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthLoginSessionRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerPortalBindingRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerPortalIdentityActionRepository;
import com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 客户只能下载明确获准的图片、PDF 或确认凭据。未获准、跨客户、改号和工资成本都不能读到。 */
class CustomerEvidenceAccessTest {
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path files;

    @Test
    void 客户下载返回真实文件字节并且下线和撤销对旧票据生效() throws Exception {
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        var storageSettings = new com.zhubao.erp.document.infra.AttachmentStorageProperties();
        storageSettings.setLocalRoot(files.toString());
        var storage = new com.zhubao.erp.document.infra.LocalAttachmentObjectStorage(storageSettings);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        byte[] original = bytes.toByteArray();
        var saved = storage.put("DOC-CUSTOMER", "ATT-BYTES", "照片.png", "image/png", original);
        var photo = attachment("ATT-BYTES", "照片.png", "image/png", "PHOTO", saved.storageLocation());
        documents.saveAttachment(photo);
        long customerId = bind("portal-download", "下载客户", "DOWNLOAD");
        var service = new CustomerEvidenceAccessService(documents, grants, identities, new DocumentManagementService(documents, storage));
        var grant = service.grant(admin(), customerId, photo.code());
        var item = service.detail(portal("portal-download")).items().get(0);
        var actors = org.mockito.Mockito.mock(com.zhubao.erp.security.app.OperatorContextResolver.class);
        var request = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(actors.requireSession(request)).thenReturn(portal("portal-download"));
        var controller = new com.zhubao.erp.document.web.CustomerEvidenceController(service, actors);
        var response = controller.download(new com.zhubao.erp.document.web.CustomerEvidenceController.DownloadRequest(
                grant.grantId(), photo.code(), item.downloadTicket()), request);
        var output = new java.io.ByteArrayOutputStream();
        response.getBody().writeTo(output);
        assertThat(output.toByteArray()).isEqualTo(original);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        documents.saveAttachment(photo.withStatus("已下线", "gray"));
        assertThat(service.detail(portal("portal-download")).items()).isEmpty();
        assertThatThrownBy(() -> service.downloadContent(portal("portal-download"), grant.grantId(), photo.code(), item.downloadTicket()))
                .isInstanceOf(PermissionDeniedException.class);
        documents.saveAttachment(photo);
        var pendingStream = service.downloadContent(portal("portal-download"), grant.grantId(), photo.code(), item.downloadTicket());
        service.revoke(admin(), grant.grantId());
        assertThatThrownBy(() -> pendingStream.write(0, pendingStream.size(), new java.io.ByteArrayOutputStream()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(service.detail(portal("portal-download")).items()).isEmpty();
        var regrant = service.grant(admin(), customerId, photo.code());
        assertThat(regrant.grantId()).isNotEqualTo(grant.grantId());
        assertThatThrownBy(() -> service.downloadContent(portal("portal-download"), grant.grantId(), photo.code(), item.downloadTicket()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void 获准后才能下载且不能改号或看到工资() {
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        EvidenceAttachment photo = attachment("ATT-PHOTO", "bead.jpg", "image/jpeg", "PHOTO",
                "s3://evidence/bead.jpg");
        EvidenceAttachment hidden = attachment("ATT-HIDDEN", "other.jpg", "image/jpeg", "PHOTO",
                "s3://evidence/other.jpg");
        EvidenceAttachment payroll = attachment("ATT-PAY", "工资表.pdf", "application/pdf", "PAYROLL",
                "s3://evidence/payroll.pdf");
        EvidenceAttachment cost = attachment("ATT-COST", "quote.pdf", "application/pdf", "成本单",
                "s3://evidence/cost.pdf");
        documents.saveAttachment(photo);
        documents.saveAttachment(hidden);
        documents.saveAttachment(payroll);
        documents.saveAttachment(cost);
        long customerId = bind("portal-a", "恒信珠宝", "HX");
        long otherId = bind("portal-b", "雅辰饰品", "YC");
        CustomerEvidenceAccessService service = new CustomerEvidenceAccessService(documents, grants, identities);

        CustomerEvidenceGrant grant = service.grant(admin(), customerId, photo.code());
        EvidenceAttachment stored = documents.findAttachmentByCode(photo.code()).orElseThrow();
        assertThat(stored.storageLocation()).isEqualTo(photo.storageLocation());
        assertThat(stored.checksum()).isEqualTo(photo.checksum());

        CustomerEvidenceDetail detail = service.detail(portal("portal-a"));
        assertThat(detail.visibility()).isEqualTo("UNDECIDED");
        assertThat(detail.items()).extracting(CustomerEvidenceDetail.Item::attachmentCode).containsExactly(photo.code());
        assertThat(detail.items().get(0).toString()).doesNotContain("s3://");
        String ticket = detail.items().get(0).downloadTicket();
        assertThat(service.download(portal("portal-a"), grant.grantId(), photo.code(), ticket).fileName()).isEqualTo("bead.jpg");
        assertThat(service.detail(portal("portal-b")).items()).isEmpty();
        assertThatThrownBy(() -> service.download(portal("portal-b"), grant.grantId(), photo.code(), ticket))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("其他客户");
        assertThatThrownBy(() -> service.download(portal("portal-a"), grant.grantId(), hidden.code(), ticket))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("不一致");
        assertThatThrownBy(() -> service.downloadByAttachmentCode(portal("portal-a"), photo.code()))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("不能绕过获准详情直接下载");
        assertThatThrownBy(() -> service.grant(admin(), customerId, payroll.code()))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("工资或成本");
        assertThatThrownBy(() -> service.grant(admin(), customerId, cost.code()))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("工资或成本");
        assertThat(otherId).isPositive();
    }

    @Test
    void 未核定类型和模型引用不默认开放() {
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        documents.saveAttachment(attachment("ATT-DOC", "note.txt", "text/plain", "NOTE", "s3://evidence/note.txt"));
        documents.saveAttachment(attachment("ATT-MODEL", "ring.stl", "application/octet-stream", "DESIGN",
                "s3://evidence/ring.stl"));
        long customerId = bind("portal-c", "明辉珠宝", "MH");
        CustomerEvidenceAccessService service = new CustomerEvidenceAccessService(documents, grants, identities);
        assertThatThrownBy(() -> service.grant(admin(), customerId, "ATT-DOC"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("尚未核定");
        assertThatThrownBy(() -> service.grant(admin(), customerId, "ATT-MODEL"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("默认内部");
        var view = service.modelView(new DesignArtifactReference("ring.stl", "STL", "v3", "柜内第三层", "ATT-MODEL"));
        assertThat(view.audience()).isEqualTo("INTERNAL");
        assertThat(view.fileName()).isEqualTo("ring.stl");
        assertThat(view.toString()).doesNotContain("柜内第三层");
        assertThat(service.detail(portal("portal-c")).items()).isEmpty();
    }

    private static EvidenceAttachment attachment(String code, String fileName, String media, String objectType, String location) {
        return new EvidenceAttachment(code, "DOC-1", "来料母单", "MD-20261030-0001", fileName, media, location,
                "hot", "上传", "已上传", "库管", "sha256:" + code, "2026-10-31 00:00", "无", "green", objectType);
    }

    private static long bind(String username, String name, String code) {
        if (identities == null) {
            identities = new CustomerPortalIdentityService(accounts, bindings, customers,
                    new InMemoryCustomerPortalIdentityActionRepository(accounts, bindings, new InMemoryAuthAuditRepository()),
                    sessions());
        }
        CustomerProfile saved = customers.saveCustomer(new CustomerProfile(0, code, name, "", "", "", "", true));
        identities.bind(admin(), username, saved.customerId());
        return saved.customerId();
    }

    private static AuthSessionService sessions() {
        AuthSessionService sessions = new AuthSessionService(
                new NavigationMenuService(new InMemoryNavigationMenuRepository()),
                new InMemoryAuthAuditRepository(), accounts, new PasswordHasher(), new PasswordExpiryPolicy(),
                new PasswordPolicy(), null, new InMemoryAuthLoginSessionRepository(), new PermissionGuard());
        sessions.setCustomerPortalBindings(bindings);
        return sessions;
    }

    private static OperatorContext admin() {
        return new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL, false, "", List.of());
    }

    private static OperatorContext portal(String username) {
        return new OperatorContext(username, RoleCode.CUSTOMER_PORTAL, DataScope.CUSTOMER_RANGE, false, "",
                List.of(), List.of(), username, "", "");
    }

    private static CustomerPortalIdentityService identities;
    private static final InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
    private static final InMemoryCustomerPortalBindingRepository bindings = new InMemoryCustomerPortalBindingRepository();
    private static final InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
    private static final InMemoryCustomerEvidenceGrantRepository grants = new InMemoryCustomerEvidenceGrantRepository();
}
