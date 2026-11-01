package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.Optional;

/** 公司料入库和对应批次余额。查询不写应付。 */
public interface CompanyMaterialReceiptRepository {
    default java.util.Optional<CompanyMaterialBalance> lockBalance(String itemId, String batchCode, String warehouseCode,
                                                                  WorkSession session) {
        return balance(itemId, batchCode, warehouseCode);
    }

    void insert(CompanyMaterialReceipt receipt, WorkSession session);

    void add(CompanyMaterialBalance incoming, WorkSession session);

    /** 从原批次扣减。重量未传时剩余重量保持未知，不写成 0。 */
    void take(String itemId, String batchCode, String warehouseCode, BigDecimal quantity, String unit,
              StockMeasurement.Weight weight, WorkSession session);

    Optional<CompanyMaterialReceipt> find(String receiptId);

    Optional<CompanyMaterialBalance> balance(String itemId, String batchCode, String warehouseCode);

    boolean coversPayable(String payableCode, WorkSession session);
}
