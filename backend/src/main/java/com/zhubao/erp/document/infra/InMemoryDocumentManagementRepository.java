package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentAttachmentPage;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.DocumentPackagePage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.documents.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentManagementRepository implements DocumentManagementRepository {

    private final Map<String, PackageEntry> packages = new LinkedHashMap<>();
    private final Map<String, AttachmentEntry> attachments = new LinkedHashMap<>();
    private final List<DocumentAuditEvent> auditEvents = new ArrayList<>(DocumentManagementSeedData.auditEvents());
    private long nextPackageId = 1;
    private long nextAttachmentId = 1;

    public InMemoryDocumentManagementRepository() {
        for (DocumentPackage documentPackage : DocumentManagementSeedData.packages()) {
            packages.put(documentPackage.code(), new PackageEntry(nextPackageId++, documentPackage));
        }
        for (EvidenceAttachment attachment : DocumentManagementSeedData.attachments()) {
            attachments.put(attachment.code(), new AttachmentEntry(nextAttachmentId++, attachment));
        }
    }

    @Override
    public synchronized List<DocumentPackage> packages() {
        return packages.values().stream().map(PackageEntry::documentPackage).toList();
    }

    @Override
    public synchronized DocumentPackagePage packagesPage(long afterId, int limit) {
        return packagesPage("", afterId, limit);
    }

    @Override
    public synchronized DocumentPackagePage packagesPage(String keyword, long afterId, int limit) {
        validatePage(afterId, limit);
        String normalizedKeyword = normalizeKeyword(keyword);
        List<DocumentPackagePage.Entry> page = new ArrayList<>();
        boolean hasMore = false;
        for (PackageEntry entry : packages.values()) {
            if (entry.id() <= afterId || !matches(entry.documentPackage(), normalizedKeyword)) continue;
            if (page.size() >= limit) {
                hasMore = true;
                break;
            }
            page.add(new DocumentPackagePage.Entry(entry.id(), entry.documentPackage()));
        }
        return new DocumentPackagePage(page, hasMore);
    }

    @Override
    public synchronized List<EvidenceAttachment> attachments() {
        return attachments.values().stream().map(AttachmentEntry::attachment).toList();
    }

    @Override
    public synchronized List<EvidenceAttachment> attachmentsByBusinessCode(String businessCode) {
        if (businessCode == null) {
            return List.of();
        }
        return attachments.values().stream()
                .map(AttachmentEntry::attachment)
                .filter(attachment -> businessCode.equals(attachment.businessCode()))
                .toList();
    }

    @Override
    public synchronized Optional<EvidenceAttachment> findAttachmentByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        AttachmentEntry entry = attachments.get(code);
        return entry == null ? Optional.empty() : Optional.of(entry.attachment());
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
    public synchronized DocumentAttachmentPage attachmentsPageByBusinessCode(
            String businessCode,
            long afterId,
            int limit
    ) {
        validateBusinessCodePage(businessCode, afterId, limit);
        List<DocumentAttachmentPage.Entry> page = new ArrayList<>();
        boolean hasMore = false;
        for (AttachmentEntry entry : attachments.values()) {
            if (entry.id() <= afterId || !businessCode.equals(entry.attachment().businessCode())) continue;
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
        PackageEntry existing = packages.get(documentPackage.code());
        long id = existing == null ? nextPackageId++ : existing.id();
        packages.put(documentPackage.code(), new PackageEntry(id, documentPackage));
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

    private void validateBusinessCodePage(String businessCode, long afterId, int limit) {
        if (businessCode == null || businessCode.isBlank()) throw new IllegalArgumentException("业务单号不能为空");
        if (afterId < 0) throw new IllegalArgumentException("附件游标不能小于 0");
        if (limit < 1 || limit > 101) throw new IllegalArgumentException("附件分页大小必须在 1 到 101 之间");
    }

    private void validatePage(long afterId, int limit) {
        if (afterId < 0) throw new IllegalArgumentException("文档包游标不能小于 0");
        if (limit < 1 || limit > 101) throw new IllegalArgumentException("文档包分页大小必须在 1 到 101 之间");
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(DocumentPackage documentPackage, String keyword) {
        if (keyword.isEmpty()) return true;
        return contains(documentPackage.code(), keyword)
                || contains(documentPackage.title(), keyword)
                || contains(documentPackage.businessCode(), keyword)
                || contains(documentPackage.owner(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private record PackageEntry(long id, DocumentPackage documentPackage) {
    }

    private record AttachmentEntry(long id, EvidenceAttachment attachment) {
    }
}
