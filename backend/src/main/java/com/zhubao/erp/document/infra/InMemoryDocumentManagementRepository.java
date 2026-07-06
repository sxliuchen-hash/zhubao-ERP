package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.documents.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentManagementRepository implements DocumentManagementRepository {

    private final List<DocumentPackage> packages = new ArrayList<>(DocumentManagementSeedData.packages());
    private final List<EvidenceAttachment> attachments = new ArrayList<>(DocumentManagementSeedData.attachments());
    private final List<DocumentAuditEvent> auditEvents = new ArrayList<>(DocumentManagementSeedData.auditEvents());

    @Override
    public synchronized List<DocumentPackage> packages() {
        return List.copyOf(packages);
    }

    @Override
    public synchronized List<EvidenceAttachment> attachments() {
        return List.copyOf(attachments);
    }

    @Override
    public synchronized List<DocumentAuditEvent> auditEvents() {
        return List.copyOf(auditEvents);
    }

    @Override
    public synchronized void saveAttachment(EvidenceAttachment attachment) {
        attachments.removeIf(existing -> existing.code().equals(attachment.code()));
        attachments.add(attachment);
    }

    @Override
    public synchronized void saveAuditEvent(DocumentAuditEvent auditEvent) {
        auditEvents.removeIf(existing -> existing.eventCode().equals(auditEvent.eventCode()));
        auditEvents.add(auditEvent);
    }
}
