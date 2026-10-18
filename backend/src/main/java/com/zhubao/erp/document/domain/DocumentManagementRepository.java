package com.zhubao.erp.document.domain;

import java.util.List;

public interface DocumentManagementRepository {

    List<DocumentPackage> packages();

    default DocumentPackagePage packagesPage(long afterId, int limit) {
        throw new UnsupportedOperationException("文档包分页未实现");
    }

    List<EvidenceAttachment> attachments();

    default DocumentAttachmentPage attachmentsPage(String documentCode, long afterId, int limit) {
        throw new UnsupportedOperationException("附件分页未实现");
    }

    List<DocumentAuditEvent> auditEvents();

    void savePackage(DocumentPackage documentPackage);

    void saveAttachment(EvidenceAttachment attachment);

    void saveAuditEvent(DocumentAuditEvent auditEvent);
}
