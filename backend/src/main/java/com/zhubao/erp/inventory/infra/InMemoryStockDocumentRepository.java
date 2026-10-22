package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.inventory.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryStockDocumentRepository implements StockDocumentRepository {

    private final ConcurrentHashMap<String, StockDocument> documents = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(StockDocument document, WorkSession session) {
        if (session == null) {
            throw new BusinessException("库存单据必须处于业务工作单元");
        }
        StockDocument previous = documents.get(document.documentCode());
        documents.put(document.documentCode(), document);
        session.onRollback(() -> {
            synchronized (this) {
                if (previous == null) {
                    documents.remove(document.documentCode(), document);
                } else {
                    documents.put(document.documentCode(), previous);
                }
            }
        });
    }

    @Override
    public Optional<StockDocument> find(String documentCode) {
        return Optional.ofNullable(documents.get(documentCode));
    }

    @Override
    public synchronized Optional<StockDocument> lock(String documentCode, WorkSession session) {
        if (session == null) {
            throw new BusinessException("库存单据必须处于业务工作单元");
        }
        return find(documentCode);
    }

    @Override
    public List<StockDocument> list() {
        return documents.values().stream()
                .sorted(Comparator.comparing(StockDocument::createdAt).thenComparing(StockDocument::documentCode))
                .toList();
    }

    public synchronized void clear() {
        documents.clear();
    }

    @Override
    public List<StockDocument> related(String sourceDocumentCode, WorkSession session) {
        if (session == null) throw new BusinessException("库存单据必须处于业务工作单元");
        return list().stream().filter(document -> sourceDocumentCode.equals(document.sourceDocumentCode())).toList();
    }
}
