package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentAttachmentPage;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "zhubao.documents.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentManagementRepository implements DocumentManagementRepository {

    private final List<DocumentPackage> packages = new ArrayList<>(DocumentManagementSeedData.packages());
    private final Map<String, AttachmentEntry> attachments = new LinkedHashMap<>();
    private final List<DocumentAuditEvent> auditEvents = new ArrayList<>(DocumentManagementSeedData.auditEvents());
    private long nextAttachmentId = 1;

    public InMemoryDocumentManagementRepository() {
        for (EvidenceAttachment attachment : DocumentManagementSeedData.attachments()) {
            attachments.put(attachment.code(), new AttachmentEntry(nextAttachmentId++, attachment));
        }
    }

    @Override
    public synchronized List<DocumentPackage> packages() {
        return List.copyOf(packages);
    }

    @Override
    public synchronized List<EvidenceAttachment> attachments() {
        return attachments.values().stream().map(AttachmentEntry::attachment).toList();
    }

    @Override
    public synchronized DocumentAttachmentPage attachmentsPage(String documentCode, long afterId, int limit) {
        validatePage(documentCode, afterId, limit);
        List<DocumentAttachmentPage.Entry> page = new ArrayList<>();
        boolean hasMore = false;
        for (AttachmentEntry entry : attachments.values()) {
            if (entry.id() <= afterId || !documentCode.equals(entry.attachment().documentCode())) continue;
            if (page.size() >= limit) {
                hasMore = true;
                break;
            }
            page.add(new DocumentAttachmentPage.Entry(entry.id(), entry.attachment()));
        }
        return new DocumentAttachmentPage(page, hasMore);
    }

    @Override
    public synchronized List<DocumentAuditEvent> auditEvents() {
        return List.copyOf(auditEvents);
    }

    @Override
    public synchronized void savePackage(DocumentPackage documentPackage) {
        packages.removeIf(existing -> existing.code().equals(documentPackage.code()));
        packages.add(documentPackage);
    }

    @Override
    public synchronized void saveAttachment(EvidenceAttachment attachment) {
        AttachmentEntry existing = attachments.get(attachment.code());
        long id = existing == null ? nextAttachmentId++ : existing.id();
        attachments.put(attachment.code(), new AttachmentEntry(id, attachment));
    }

    @Override
    public synchronized void saveAuditEvent(DocumentAuditEvent auditEvent) {
        auditEvents.removeIf(existing -> existing.eventCode().equals(auditEvent.eventCode()));
        auditEvents.add(auditEvent);
    }

    private void validatePage(String documentCode, long afterId, int limit) {
        if (documentCode == null || documentCode.isBlank()) throw new IllegalArgumentException("文档包编号不能为空");
        if (afterId < 0) throw new IllegalArgumentException("附件游标不能小于 0");
        if (limit < 1 || limit > 101) throw new IllegalArgumentException("附件分页大小必须在 1 到 101 之间");
    }

    private record AttachmentEntry(long id, EvidenceAttachment attachment) {
    }
}
