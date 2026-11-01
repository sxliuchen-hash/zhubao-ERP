package com.zhubao.erp.inventory.app;

import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.security.domain.OperatorContext;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

/** 本厂物品查询仅返回实物管理字段，采购金额及应付仍走财务权限。 */
@Service
public class FactoryInventoryQuery {
    private final FactoryItemService items;
    private final CompanyMaterialReceiptRepository receipts;
    private final CompanyMaterialIssueRepository issues;
    private final ToolCustodyRepository tools;
    public FactoryInventoryQuery(FactoryItemService items,CompanyMaterialReceiptRepository receipts,CompanyMaterialIssueRepository issues,ToolCustodyRepository tools) {
        this.items=items; this.receipts=receipts; this.issues=issues; this.tools=tools;
    }
    public Position read(OperatorContext actor,String itemId,String warehouse) {
        var item=items.find(actor,itemId);
        return new Position(item, receipts.balancesOf(itemId), receipts.receiptsOf(itemId).stream()
                .map(row -> new Receipt(row.receiptId(),row.batchCode(),row.warehouseCode(),row.quantity(),row.unit(),row.weight(),row.businessDate())).toList(),
                issues.issuesOf(itemId),tools.loansOf(itemId),tools.piecesOf(itemId),
                item.category()==FactoryItemCategory.REUSABLE_TOOL && item.custodyMode()==CustodyMode.QUANTITY && warehouse!=null && !warehouse.isBlank() ? tools.stock(itemId,warehouse) : null);
    }
    public record Receipt(String receiptId,String batchCode,String warehouseCode,BigDecimal quantity,String unit,StockMeasurement.Weight weight,String businessDate) {}
    public record Position(FactoryItem item,List<CompanyMaterialBalance> balances,List<Receipt> receipts,List<CompanyMaterialIssue> issues,List<ToolLoan> loans,List<ToolPiece> pieces,BigDecimal toolQuantity) {}
}
