package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ToolCustodyRepository {

    void addStock(String itemId, String warehouseCode, BigDecimal quantity, String unit, WorkSession session);

    BigDecimal stock(String itemId, String warehouseCode);

    default BigDecimal lockStock(String itemId, String warehouseCode, WorkSession session) {
        return stock(itemId, warehouseCode);
    }

    void settlePiece(ToolLoan loan, boolean scrap, WorkSession session);

    void placePiece(String pieceCode, String itemId, String warehouseCode, WorkSession session);

    void loan(ToolLoan loan, WorkSession session);

    Optional<ToolLoan> find(String loanId);

    ToolLoan settle(String loanId, BigDecimal quantity, int expectedVersion, WorkSession session);

    void record(ToolDisposition disposition, WorkSession session);

    List<ToolLoan> loansOf(String itemId);

    /** 按持有人稳定身份汇总未还数量，不按姓名合并。 */
    BigDecimal outstanding(String itemId, String holderId);
}
