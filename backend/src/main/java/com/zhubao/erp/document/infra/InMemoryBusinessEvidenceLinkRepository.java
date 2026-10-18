package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.BusinessEvidenceLink;
import com.zhubao.erp.document.domain.BusinessEvidenceLinkPage;
import com.zhubao.erp.document.domain.BusinessEvidenceLinkRepository;
import com.zhubao.erp.document.domain.EvidenceLinkKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.documents.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryBusinessEvidenceLinkRepository implements BusinessEvidenceLinkRepository {

    private final List<LinkEntry> links = new ArrayList<>();
    private long nextId = 1;

    @Override
    public synchronized List<BusinessEvidenceLink> findAll() {
        return links.stream().map(LinkEntry::link).toList();
    }

    @Override
    public synchronized BusinessEvidenceLinkPage findPageByTarget(
            String targetType,
            String targetCode,
            long afterId,
            int limit
    ) {
        validatePage(afterId, limit);
        List<BusinessEvidenceLinkPage.Entry> page = new ArrayList<>();
        boolean hasMore = false;
        for (LinkEntry entry : links) {
            BusinessEvidenceLink link = entry.link();
            if (entry.id() <= afterId
                    || targetType == null
                    || targetCode == null
                    || !targetType.equals(link.targetType())
                    || !targetCode.equals(link.targetCode())) {
                continue;
            }
            if (page.size() >= limit) {
                hasMore = true;
                break;
            }
            page.add(new BusinessEvidenceLinkPage.Entry(entry.id(), link));
        }
        return new BusinessEvidenceLinkPage(page, hasMore);
    }

    @Override
    public synchronized List<BusinessEvidenceLink> findByTarget(String targetType, String targetCode) {
        if (targetType == null || targetCode == null) {
            return List.of();
        }
        return links.stream()
                .map(LinkEntry::link)
                .filter(link -> targetType.equals(link.targetType()) && targetCode.equals(link.targetCode()))
                .toList();
    }

    @Override
    public synchronized Optional<BusinessEvidenceLink> findByKeyHash(String linkKeyHash) {
        return links.stream()
                .map(LinkEntry::link)
                .filter(link -> link.linkKeyHash().equals(linkKeyHash))
                .findFirst();
    }

    @Override
    public synchronized void insert(BusinessEvidenceLink link) {
        boolean duplicate = links.stream().anyMatch(existing ->
                existing.link().linkCode().equals(link.linkCode())
                        || existing.link().linkKeyHash().equals(link.linkKeyHash())
                        || EvidenceLinkKey.sameDimensions(existing.link(), link));
        if (duplicate) {
            throw new IllegalStateException("证据引用已存在");
        }
        links.add(new LinkEntry(nextId++, link));
    }

    private void validatePage(long afterId, int limit) {
        if (afterId < 0) {
            throw new IllegalArgumentException("证据引用游标不能小于 0");
        }
        if (limit < 1 || limit > 101) {
            throw new IllegalArgumentException("证据引用分页大小必须在 1 到 101 之间");
        }
    }

    private record LinkEntry(long id, BusinessEvidenceLink link) {
    }
}
