package com.zhubao.erp.document.domain;

import java.util.List;
import java.util.Optional;

public interface DocumentManagementRepository {

    List<DocumentPackage> packages();

    default DocumentPackagePage packagesPage(long afterId, int limit) {
        throw new UnsupportedOperationException("文档包分页未实现");
    }

    default DocumentPackagePage packagesPage(String keyword, long afterId, int limit) {
        throw new UnsupportedOperationException("文档包关键字分页未实现");
    }

    List<EvidenceAttachment> attachments();

    default List<EvidenceAttachment> attachmentsByBusinessCode(String businessCode) {
        if (businessCode == null) {
            return List.of();
        }
        return attachments().stream()
                .filter(attachment -> businessCode.equals(attachment.businessCode()))
                .toList();
    }

    default Optional<EvidenceAttachment> findAttachmentByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return attachments().stream()
                .filter(attachment -> code.equals(attachment.code()))
                .findFirst();
    }

    default DocumentAttachmentPage attachmentsPage(String documentCode, long afterId, int limit) {
        throw new UnsupportedOperationException("附件分页未实现");
    }

    List<DocumentAuditEvent> auditEvents();

    void savePackage(DocumentPackage documentPackage);

    void saveAttachment(EvidenceAttachment attachment);

    void saveAuditEvent(DocumentAuditEvent auditEvent);
}
