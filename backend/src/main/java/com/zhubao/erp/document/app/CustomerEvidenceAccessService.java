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
import com.zhubao.erp.security.app.CustomerPortalIdentityService;
import com.zhubao.erp.security.domain.AuthTokenDigest;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * 客户证据只读。未获准的照片和文件不展示，模型引用保持内部。不改附件原件，也不提供工资或成本。
 */
@Service
public class CustomerEvidenceAccessService {

    static final String VISIBILITY_UNDECIDED = "UNDECIDED";

    private final DocumentManagementRepository documents;
    private final CustomerEvidenceGrantRepository grants;
    private final CustomerPortalIdentityService identities;

    public CustomerEvidenceAccessService(DocumentManagementRepository documents, CustomerEvidenceGrantRepository grants,
                                         CustomerPortalIdentityService identities) {
        this.documents = documents;
        this.grants = grants;
        this.identities = identities;
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
        String kind = kindOf(attachment);
        CustomerEvidenceGrant existing = grants.find(customerId, attachment.code()).orElse(null);
        if (existing != null) {
            return existing;
        }
        CustomerEvidenceGrant grant = new CustomerEvidenceGrant(
                "CEG" + UUID.randomUUID().toString().replace("-", "").substring(0, 32),
                customerId, attachment.code(), kind);
        grants.save(grant);
        return grant;
    }

    public CustomerEvidenceDetail detail(OperatorContext operator) {
        long customerId = customerId(operator);
        return new CustomerEvidenceDetail(customerId, VISIBILITY_UNDECIDED, grants.listByCustomer(customerId).stream()
                .map(grant -> item(grant, requireAttachment(grant.attachmentCode())))
                .toList());
    }

    public CustomerEvidenceDownload download(OperatorContext operator, String grantId, String attachmentCode, String ticket) {
        long customerId = customerId(operator);
        CustomerEvidenceGrant grant = grants.findById(grantId)
                .orElseThrow(() -> new PermissionDeniedException("不能绕过获准详情直接下载"));
        if (grant.customerId() != customerId) {
            throw new PermissionDeniedException("不能下载其他客户的证据");
        }
        String code = attachmentCode == null ? "" : attachmentCode.trim();
        if (!grant.attachmentCode().equals(code) || ticket == null || !ticket.equals(ticketOf(grant))) {
            throw new PermissionDeniedException("附件与获准详情不一致");
        }
        EvidenceAttachment attachment = requireAttachment(code);
        rejectSensitive(attachment);
        if (!kindOf(attachment).equals(grant.kind())) {
            throw new PermissionDeniedException("附件与获准详情不一致");
        }
        return new CustomerEvidenceDownload(grant.grantId(), attachment.code(), attachment.fileName(),
                attachment.mediaType(), grant.kind());
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
