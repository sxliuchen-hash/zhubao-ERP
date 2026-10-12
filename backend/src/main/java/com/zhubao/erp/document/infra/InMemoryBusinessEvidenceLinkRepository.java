package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.BusinessEvidenceLink;
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

    private final List<BusinessEvidenceLink> links = new ArrayList<>();

    @Override
    public synchronized List<BusinessEvidenceLink> findAll() {
        return List.copyOf(links);
    }

    @Override
    public synchronized Optional<BusinessEvidenceLink> findByKeyHash(String linkKeyHash) {
        return links.stream().filter(link -> link.linkKeyHash().equals(linkKeyHash)).findFirst();
    }

    @Override
    public synchronized void insert(BusinessEvidenceLink link) {
        boolean duplicate = links.stream().anyMatch(existing ->
                existing.linkCode().equals(link.linkCode())
                        || existing.linkKeyHash().equals(link.linkKeyHash())
                        || EvidenceLinkKey.sameDimensions(existing, link));
        if (duplicate) {
            throw new IllegalStateException("证据引用已存在");
        }
        links.add(link);
    }
}
