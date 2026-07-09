package com.zhubao.erp.document.domain;

import java.util.List;

public interface DocumentManagementRepository {

    List<DocumentPackage> packages();

    List<EvidenceAttachment> attachments();

    List<DocumentAuditEvent> auditEvents();

    void savePackage(DocumentPackage documentPackage);

    void saveAttachment(EvidenceAttachment attachment);

    void saveAuditEvent(DocumentAuditEvent auditEvent);
}
