package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ToolCustodyRepository {

    void addStock(String itemId, String warehouseCode, BigDecimal quantity, String unit, WorkSession session);

    BigDecimal stock(String itemId, String warehouseCode);

    void placePiece(String pieceCode, String itemId, String warehouseCode, WorkSession session);

    void loan(ToolLoan loan, WorkSession session);

    Optional<ToolLoan> find(String loanId);

    List<ToolLoan> loansOf(String itemId);

    /** 按持有人稳定身份汇总未还数量，不按姓名合并。 */
    BigDecimal outstanding(String itemId, String holderId);
}
