package com.zhubao.erp.document.infra;

/** 旧附件样本只在明确需要的测试中建立。 */
public final class DocumentRepositoryFixture {
    private DocumentRepositoryFixture() { }
    public static InMemoryDocumentManagementRepository seeded() { return seed(new InMemoryDocumentManagementRepository()); }
    public static <T extends InMemoryDocumentManagementRepository> T seed(T repository) {
        DocumentManagementSeedData.packages().forEach(repository::savePackage);
        DocumentManagementSeedData.attachments().forEach(repository::saveAttachment);
        DocumentManagementSeedData.auditEvents().forEach(repository::saveAuditEvent);
        return repository;
    }
}
