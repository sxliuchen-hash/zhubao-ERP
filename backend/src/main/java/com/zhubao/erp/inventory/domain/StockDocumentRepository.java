package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface StockDocumentRepository {

    void save(StockDocument document, WorkSession session);

    Optional<StockDocument> find(String documentCode);

    /** 在当前工作单元里锁定单据，确认入账时避免两笔同时过账。 */
    Optional<StockDocument> lock(String documentCode, WorkSession session);

    List<StockDocument> list();

    /** 原单锁定后，在同一事务中读取最新的后续记录，避免重复退回或解除冻结。 */
    List<StockDocument> related(String sourceDocumentCode, WorkSession session);
}
