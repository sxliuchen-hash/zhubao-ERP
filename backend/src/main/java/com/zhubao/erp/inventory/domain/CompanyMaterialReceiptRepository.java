package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

/** 公司料入库和对应批次余额。查询不写应付。 */
public interface CompanyMaterialReceiptRepository {

    void insert(CompanyMaterialReceipt receipt, WorkSession session);

    void add(CompanyMaterialBalance incoming, WorkSession session);

    Optional<CompanyMaterialReceipt> find(String receiptId);

    Optional<CompanyMaterialBalance> balance(String itemId, String batchCode, String warehouseCode);

    boolean coversPayable(String payableCode, WorkSession session);
}
