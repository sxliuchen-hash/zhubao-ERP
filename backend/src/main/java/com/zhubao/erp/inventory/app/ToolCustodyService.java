package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CustodyMode;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.FactoryItemRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.ToolCustodyRepository;
import com.zhubao.erp.inventory.domain.ToolDisposition;
import com.zhubao.erp.inventory.domain.ToolLoan;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * 工具放入库位并借给稳定人员。借用只改保管。
 */
@Service
public class ToolCustodyService {

    private final FactoryItemRepository items;
    private final ToolCustodyRepository tools;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public ToolCustodyService(FactoryItemRepository items, ToolCustodyRepository tools, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.items = items;
        this.tools = tools;
        this.unit = unit;
        this.permissions = permissions;
    }

    public void place(String requestId, OperatorContext actor, String itemId, String warehouseCode, BigDecimal quantity) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RECEIVE, actor);
        FactoryItem item = tool(itemId, CustodyMode.QUANTITY);
        BigDecimal counted = positive(quantity);
        String warehouse = required(warehouseCode, "请填写位置", 40);
        unit.execute(new CommandCall(
                requestId, actor.operator(), "PLACE_TOOL", "TOOL", item.itemId(),
                item.itemId() + "\n" + warehouse + "\n" + counted.toPlainString()
        ), session -> {
            tools.addStock(item.itemId(), warehouse, counted, item.unit(), session);
            return item.itemId();
        });
    }

    public void placePiece(String requestId, OperatorContext actor, String itemId, String warehouseCode, String pieceCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RECEIVE, actor);
        FactoryItem item = tool(itemId, CustodyMode.PIECE);
        String warehouse = required(warehouseCode, "请填写位置", 40);
        String piece = required(pieceCode, "请填写工具编号", 40);
        unit.execute(new CommandCall(
                requestId, actor.operator(), "PLACE_TOOL_PIECE", "TOOL", piece,
                item.itemId() + "\n" + warehouse + "\n" + piece
        ), session -> {
            tools.placePiece(piece, item.itemId(), warehouse, session);
            return piece;
        });
    }

    public ToolLoan borrow(String requestId, OperatorContext actor, String itemId, String warehouseCode,
                           String holderId, String holderName, BigDecimal quantity, String pieceCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_ISSUE, actor);
        FactoryItem item = reusable(itemId);
        if (item.custodyMode() == CustodyMode.PIECE || (pieceCode != null && !pieceCode.isBlank())) {
            throw new BusinessException("本版工具按数量借还");
        }
        String holder = required(holderId, "请选择持有人", 40);
        String name = required(holderName, "请填写持有人姓名", 80);
        String warehouse = required(warehouseCode, "请填写位置", 40);
        String piece = "";
        BigDecimal counted = positive(quantity);
        String loanId = unit.execute(new CommandCall(
                requestId, actor.operator(), "BORROW_TOOL", "TOOL", item.itemId(),
                item.itemId() + "\n" + warehouse + "\n" + holder + "\n" + name + "\n" + counted.toPlainString() + "\n" + piece
        ), session -> {
            tools.addStock(item.itemId(), warehouse, counted.negate(), item.unit(), session);
            String id = "TL-" + token();
            tools.loan(new ToolLoan(id, item.itemId(), holder, name, counted, BigDecimal.ZERO, item.unit(), piece, warehouse, 1), session);
            return id;
        });
        return tools.find(loanId).orElseThrow(() -> new BusinessException("借出单不存在"));
    }

    public ToolLoan giveBack(String requestId, OperatorContext actor, String loanId, int version, BigDecimal quantity) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_RETURN, actor);
        BigDecimal counted = positive(quantity);
        String id = text(loanId);
        String saved = unit.execute(new CommandCall(
                requestId, actor.operator(), "RETURN_TOOL", "TOOL_LOAN", id,
                id + "\n" + version + "\n" + counted.toPlainString()
        ), session -> {
            ToolLoan current = tools.find(id).orElseThrow(() -> new BusinessException("借出单不存在"));
            assertPieceQuantity(current, counted);
            tools.settle(id, counted, version, session);
            if (current.pieceCode().isEmpty()) {
                tools.addStock(current.itemId(), current.warehouseCode(), counted, current.unit(), session);
            } else {
                tools.settlePiece(current, false, session);
            }
            return id;
        });
        return tools.find(saved).orElseThrow(() -> new BusinessException("借出单不存在"));
    }

    public ToolDisposition scrap(String requestId, OperatorContext actor, String loanId, int version, BigDecimal quantity, String reason) {
        return dispose(requestId, actor, loanId, version, quantity, reason, ToolDisposition.SCRAP, true);
    }

    public ToolDisposition damage(String requestId, OperatorContext actor, String loanId, BigDecimal quantity, String reason) {
        return dispose(requestId, actor, loanId, 0, quantity, reason, ToolDisposition.DAMAGE, false);
    }

    private ToolDisposition dispose(String requestId, OperatorContext actor, String loanId, int version, BigDecimal quantity,
                                    String reason, String kind, boolean closeQuantity) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.STOCK_COMPANY_ISSUE, actor);
        BigDecimal counted = positive(quantity);
        String why = required(reason, "请填写原因", 80);
        String id = text(loanId);
        String saved = unit.execute(new CommandCall(
                requestId, actor.operator(), kind + "_TOOL", "TOOL_LOAN", id,
                id + "\n" + version + "\n" + counted.toPlainString() + "\n" + why
        ), session -> {
            ToolLoan current = tools.find(id).orElseThrow(() -> new BusinessException("借出单不存在"));
            assertPieceQuantity(current, counted);
            if (current.outstanding().compareTo(counted) < 0) {
                throw new BusinessException("处置数量不能超过未还");
            }
            if (closeQuantity) {
                tools.settle(id, counted, version, session);
                if (!current.pieceCode().isEmpty()) {
                    tools.settlePiece(current, true, session);
                }
            }
            String dispositionId = "TD-" + token();
            tools.record(new ToolDisposition(dispositionId, id, current.holderId(), kind, counted, why), session);
            return dispositionId;
        });
        ToolLoan loan = tools.find(id).orElseThrow(() -> new BusinessException("借出单不存在"));
        return new ToolDisposition(saved, id, loan.holderId(), kind, counted, why);
    }

    public BigDecimal stock(String itemId, String warehouseCode) {
        return tools.stock(itemId, warehouseCode);
    }

    private static void assertPieceQuantity(ToolLoan loan, BigDecimal quantity) {
        if (!loan.pieceCode().isEmpty() && quantity.compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException("逐件工具须整件归还或处置");
        }
    }

    public BigDecimal outstanding(String itemId, String holderId) {
        return tools.outstanding(itemId, holderId);
    }

    private FactoryItem reusable(String itemId) {
        FactoryItem found = items.find(text(itemId)).orElseThrow(() -> new BusinessException("物品档案不存在"));
        if (found.category() != FactoryItemCategory.REUSABLE_TOOL) {
            throw new BusinessException("只有重复工具可以借出");
        }
        return found;
    }

    private FactoryItem tool(String itemId, CustodyMode mode) {
        FactoryItem found = reusable(itemId);
        if (mode == CustodyMode.QUANTITY && found.custodyMode() == CustodyMode.UNDECIDED) {
            return found;
        }
        if (found.custodyMode() != mode) {
            throw new BusinessException(mode == CustodyMode.PIECE ? "这件工具不按逐件管理" : "这件工具不按数量管理");
        }
        return found;
    }

    private static BigDecimal positive(BigDecimal quantity) {
        if (quantity == null) {
            throw new BusinessException("请填写数量");
        }
        BigDecimal counted = StockMeasurement.processingScale(quantity);
        if (counted.signum() <= 0) {
            throw new BusinessException("数量必须大于零");
        }
        return counted;
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String required(String value, String message, int max) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        if (text.length() > max) {
            throw new BusinessException(message.replace("请填写", "").replace("请选择", "") + "过长");
        }
        return text;
    }
}
