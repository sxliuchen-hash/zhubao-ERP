package com.zhubao.erp.document.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.CustomerEvidenceDetail;
import com.zhubao.erp.document.domain.CustomerEvidenceDownload;
import com.zhubao.erp.document.domain.CustomerEvidenceGrant;
import com.zhubao.erp.document.domain.CustomerEvidenceGrantRepository;
import com.zhubao.erp.document.domain.CustomerModelReferenceView;
import com.zhubao.erp.document.domain.DesignArtifactReference;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.AttachmentStreamContent;
import com.zhubao.erp.security.app.CustomerPortalIdentityService;
import com.zhubao.erp.security.domain.AuthTokenDigest;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;
import java.util.UUID;

/**
 * 本版不向客户开放照片和文件。模型引用保持内部。不改附件原件，也不提供工资或成本。
 */
@Service
public class CustomerEvidenceAccessService {

    static final String VISIBILITY_CLOSED = "CLOSED";

    private final DocumentManagementRepository documents;
    private final CustomerEvidenceGrantRepository grants;
    private final CustomerPortalIdentityService identities;
    private final DocumentManagementService content;

    public CustomerEvidenceAccessService(DocumentManagementRepository documents, CustomerEvidenceGrantRepository grants,
                                         CustomerPortalIdentityService identities) {
        this(documents, grants, identities, null);
    }

    @Autowired
    public CustomerEvidenceAccessService(DocumentManagementRepository documents, CustomerEvidenceGrantRepository grants,
                                         CustomerPortalIdentityService identities, DocumentManagementService content) {
        this.documents = documents;
        this.grants = grants;
        this.identities = identities;
        this.content = content;
    }

    /** 管理员逐张获准。不是按照片类别默认开放。 */
    public CustomerEvidenceGrant grant(OperatorContext operator, long customerId, String attachmentCode) {
        if (operator == null || operator.role() != RoleCode.ADMIN) {
            throw new PermissionDeniedException("只有管理员可以获准客户证据");
        }
        if (customerId <= 0) {
            throw new BusinessException("客户绑定不能为空");
        }
        EvidenceAttachment attachment = requireAttachment(attachmentCode);
        assertActive(attachment);
        rejectSensitive(attachment);
        if (isModel(attachment)) {
            throw new BusinessException("模型外部引用默认内部，不能获准给客户");
        }
        throw new BusinessException("本版不向客户开放照片和文件");
    }

    public CustomerEvidenceDetail detail(OperatorContext operator) {
        long customerId = customerId(operator);
        return new CustomerEvidenceDetail(customerId, VISIBILITY_CLOSED, java.util.List.of());
    }

    public CustomerEvidenceDownload download(OperatorContext operator, String grantId, String attachmentCode, String ticket) {
        customerId(operator);
        throw new BusinessException("本版不向客户开放照片和文件");
    }

    public AttachmentStreamContent downloadContent(OperatorContext operator, String grantId, String attachmentCode, String ticket) {
        download(operator, grantId, attachmentCode, ticket);
        AttachmentStreamContent stream = content.contentStream(attachmentCode);
        if (!com.zhubao.erp.document.domain.AttachmentMediaPolicy.matchesHeader(stream.mediaType(), stream.header(), stream.header().length)) {
            throw new BusinessException("附件内容与文件类型不符，暂不可下载");
        }
        return new AttachmentStreamContent(stream.code(), stream.fileName(), stream.mediaType(), stream.size(), stream.header(),
                (offset, length, output) -> {
                    download(operator, grantId, attachmentCode, ticket);
                    stream.write(offset, length, output);
                });
    }

    public void revoke(OperatorContext operator, String grantId) {
        if (operator == null || operator.role() != RoleCode.ADMIN) {
            throw new PermissionDeniedException("只有管理员可以撤销客户证据授权");
        }
        grants.revoke(grantId);
    }

    private static void assertActive(EvidenceAttachment attachment) {
        if (!java.util.Set.of("已上传", "已关联", "已归档").contains(attachment.status())) {
            throw new PermissionDeniedException("附件已下线或待核对，暂不可访问");
        }
    }

    private static boolean visible(CustomerEvidenceGrant grant, EvidenceAttachment attachment) {
        try {
            assertActive(attachment);
            return kindOf(attachment).equals(grant.kind());
        } catch (BusinessException | PermissionDeniedException ex) {
            return false;
        }
    }

    /** 只拿附件编号不能下载。必须先读获准详情里的票据。 */
    public CustomerEvidenceDownload downloadByAttachmentCode(OperatorContext operator, String attachmentCode) {
        customerId(operator);
        throw new PermissionDeniedException("不能绕过获准详情直接下载");
    }

    public CustomerModelReferenceView modelView(DesignArtifactReference reference) {
        if (reference == null) {
            throw new BusinessException("缺少模型引用");
        }
        return new CustomerModelReferenceView(reference.fileName(), reference.format(), reference.version(),
                reference.audience());
    }

    private CustomerEvidenceDetail.Item item(CustomerEvidenceGrant grant, EvidenceAttachment attachment) {
        return new CustomerEvidenceDetail.Item(grant.grantId(), attachment.code(), attachment.fileName(),
                attachment.mediaType(), grant.kind(), ticketOf(grant));
    }

    private static String ticketOf(CustomerEvidenceGrant grant) {
        return AuthTokenDigest.sha256(grant.grantId() + "|" + grant.attachmentCode() + "|" + grant.customerId());
    }

    private EvidenceAttachment requireAttachment(String attachmentCode) {
        String code = attachmentCode == null ? "" : attachmentCode.trim();
        if (code.isEmpty()) {
            throw new BusinessException("附件不存在");
        }
        return documents.findAttachmentByCode(code).orElseThrow(() -> new BusinessException("附件不存在"));
    }

    private long customerId(OperatorContext operator) {
        OperatorContext portal = identities.portalOperator(operator == null ? "" : operator.username());
        if (portal.role() != RoleCode.CUSTOMER_PORTAL || portal.dataScope() != DataScope.CUSTOMER_RANGE) {
            throw new PermissionDeniedException("不是客户身份");
        }
        return Long.parseLong(portal.customerScope().get(0));
    }

    private static boolean isModel(EvidenceAttachment attachment) {
        String objectType = text(attachment.objectType());
        String fileName = text(attachment.fileName()).toLowerCase(Locale.ROOT);
        return objectType.contains("DESIGN") || objectType.contains("MODEL") || fileName.endsWith(".stl")
                || fileName.endsWith(".obj") || fileName.endsWith(".3dm");
    }

    private static String kindOf(EvidenceAttachment attachment) {
        rejectSensitive(attachment);
        String objectType = text(attachment.objectType());
        String businessType = text(attachment.businessType());
        String fileName = text(attachment.fileName()).toLowerCase(Locale.ROOT);
        if (objectType.contains("DESIGN") || objectType.contains("MODEL") || fileName.endsWith(".stl")
                || fileName.endsWith(".obj") || fileName.endsWith(".3dm")) {
            throw new BusinessException("模型外部引用默认内部，不能获准给客户");
        }
        String media = text(attachment.mediaType()).toLowerCase(Locale.ROOT);
        boolean image = media.startsWith("image/") && !media.equals("image/svg+xml");
        boolean pdf = "application/pdf".equals(media);
        boolean confirmation = "CONFIRMATION".equals(objectType) || businessType.contains("确认凭据");
        if (confirmation && (image || pdf)) {
            return "CONFIRMATION";
        }
        if (image) {
            return "IMAGE";
        }
        if (pdf) {
            return "PDF";
        }
        throw new BusinessException("对外照片和文件可见范围尚未核定，不能默认开放");
    }

    private static void rejectSensitive(EvidenceAttachment attachment) {
        String haystack = (text(attachment.fileName()) + "\n" + text(attachment.businessType()) + "\n"
                + text(attachment.objectType()) + "\n" + text(attachment.documentCode())).toLowerCase(Locale.ROOT);
        if (haystack.contains("工资") || haystack.contains("成本") || haystack.contains("工费")
                || haystack.contains("payroll") || haystack.contains("cost")) {
            throw new PermissionDeniedException("不能向客户提供工资或成本");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
