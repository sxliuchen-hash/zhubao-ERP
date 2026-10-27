package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 母单基础信息更正。换客户时冲正原收货并按新归属重新入账，保留历史事实。 */
@Service
public class MotherOrderAdminService {
    private final BusinessDocumentRepository documents;
    private final MasterDataRepository masterData;
    private final StockLedgerService stocks;
    private final FinanceRepository finance;
    private final OrderChangeRepository changes;
    private final BusinessWorkUnit workUnit;
    private final PermissionGuard permissions;

    public MotherOrderAdminService(BusinessDocumentRepository documents, MasterDataRepository masterData,
                                   StockLedgerService stocks, FinanceRepository finance, OrderChangeRepository changes,
                                   BusinessWorkUnit workUnit, PermissionGuard permissions) {
        this.documents = documents;
        this.masterData = masterData;
        this.stocks = stocks;
        this.finance = finance;
        this.changes = changes;
        this.workUnit = workUnit;
        this.permissions = permissions;
    }

    public record BasicInfo(Long customerId, String goodsName, String remark, String reason,
                            String requestId, String expectedRevision) { }

    public void correct(String code, BasicInfo command, OperatorContext operator) {
        permissions.assertAllowed(PermissionCode.ORDER_ADMIN_MAINTAIN, operator);
        String reason = required(command.reason(), "请填写修改原因");
        if (reason.length() > 200) throw new BusinessException("原因不能超过 200 字");
        if (text(command.goodsName()).length() > 120 || text(command.remark()).length() > 300) {
            throw new BusinessException("货品最多 120 字，备注最多 300 字");
        }
        required(command.expectedRevision(), "请重新打开母单后再保存");
        workUnit.execute(new CommandCall(required(command.requestId(), "修改必须带请求编号"), operator.operator(),
                "ADMIN_MOTHER_BASIC_INFO", "MOTHER", code, OrderMaintenanceRevision.of(command)), session -> {
            documents.lockMotherOrder(code, session);
            // 等待行锁后必须读取最新版本，不能使用命令回放查询已建立的旧事务快照。
            MotherOrderRecord current = documents.findMotherOrderAny(code, session)
                    .orElseThrow(() -> new BusinessException("母单不存在或已删除"));
            if (documents.motherRecycle(code, session).isPresent()) throw new BusinessException("母单已删除，请先恢复");
            if (!OrderMaintenanceRevision.of(current).equals(command.expectedRevision())) {
                throw new BusinessException("母单已被修改，请重新打开后再保存");
            }
            Long customerId = command.customerId() == null ? current.customerId() : command.customerId();
            boolean changed = !Objects.equals(customerId, current.customerId());
            String customerName = current.customerName();
            Long owner = current.ownerCustomerId();
            if (changed) {
                CustomerProfile customer = masterData.findCustomer(customerId)
                        .filter(CustomerProfile::enabled).orElseThrow(() -> new BusinessException("请选择启用的客户档案"));
                customerName = customer.name();
                assertCustomerChangeAllowed(current, session);
                if (current.customerId() != null && "CUSTOMER".equals(current.ownerType())
                        && Objects.equals(owner, current.customerId())) owner = customerId;
            }
            String ledgerCode = changed ? rebookReceipts(current, customerId, session) : current.ledgerCode();
            MotherOrderRecord next = new MotherOrderRecord(current.code(), customerId, owner, current.materialCode(),
                    current.inboundDate(), current.expressCompany(), current.courierNo(), current.senderName(),
                    current.receiverName(), current.unpackSigner(), required(command.goodsName(), "请填写货品"),
                    current.ownerType(), current.customerOwned(), current.warehouseCode(), current.quantity(),
                    current.quantityUnit(), current.weight(), current.stockStatus(), ledgerCode,
                    current.sourceRegisterType(), current.sourceRegisterLineNo(), current.sourceRegisterSequence(),
                    current.operator(), current.evidenceCode(), text(command.remark()), current.status(), current.createdAt(),
                    current.processingRequirement(), current.dueDate(), customerName);
            documents.correctMotherBasicInfo(next, new OrderAdjustment("MOTHER", code, snapshot(current), snapshot(next),
                    reason, operator.operator(), LocalDateTime.now().toString()), session);
            return code;
        });
    }

    private void assertCustomerChangeAllowed(MotherOrderRecord current, WorkSession session) {
        if (documents.motherHasDownstream(current.code(), session)
                || !documents.listProcessBatches(current.code(), session).isEmpty()
                || documents.findQualityRequirementCheck(current.code(), session).isPresent()
                || finance.hasMotherPosting(current.code()) || !changes.listByMother(current.code()).isEmpty()) {
            throw new BusinessException("母单已有拆单、交工、质检、改单或财务记录，不能直接更换客户；请办理业务更正");
        }
    }

    private String rebookReceipts(MotherOrderRecord current, Long customerId, WorkSession session) {
        List<StockLedgerEntry> ledgers = stocks.ledgersForMother(current.code(), session);
        if (ledgers.isEmpty()) {
            if (!text(current.ledgerCode()).isEmpty()) throw new BusinessException("原收货流水缺失，不能更换客户");
            return current.ledgerCode();
        }
        if (ledgers.stream().anyMatch(row -> !Set.of("inbound_confirm", "inbound_reversal").contains(row.documentType()))) {
            throw new BusinessException("母单已有其他库存事实，不能直接更换客户");
        }
        Set<String> reversed = ledgers.stream().filter(row -> "inbound_reversal".equals(row.documentType()))
                .map(StockLedgerEntry::sourceLedgerCode).collect(Collectors.toSet());
        List<StockLedgerEntry> receipts = ledgers.stream().filter(row -> "inbound_confirm".equals(row.documentType())
                && !reversed.contains(row.code())).toList();
        if (receipts.isEmpty()
                || receipts.stream().map(StockLedgerEntry::quantity).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(current.quantity()) != 0
                || receipts.stream().map(StockLedgerEntry::weight).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(current.weight()) != 0) {
            throw new BusinessException("收货流水与母单数量重量不一致，不能更换客户");
        }
        String ledgerCode = current.ledgerCode();
        for (StockLedgerEntry receipt : receipts) {
            StockDimension from = receipt.to();
            if (from == null || !current.code().equals(receipt.documentCode()) || from.stockStatus() != StockStatus.RAW_WAITING
                    || !Objects.equals(from.customerId(), current.customerId())
                    || (from.workOrderCode() != null && !from.workOrderCode().isBlank()) || !from.batchCode().isBlank()) {
                throw new BusinessException("原收货归属不一致，不能更换客户");
            }
            StockDimension to = new StockDimension(from.ownerType(),
                    from.ownerType() == OwnerType.CUSTOMER && current.customerId() != null
                            && Objects.equals(from.ownerCustomerId(), current.customerId()) ? customerId : from.ownerCustomerId(),
                    customerId, from.motherOrderCode(), from.workOrderCode(), from.materialCode(),
                    from.warehouseCode(), from.stockStatus(), from.batchCode());
            stocks.reverseInbound(new ReceiptEntryReversal(receipt.code(), current.code(), from,
                    receipt.quantity(), receipt.weight(), "管理员更正母单客户"), session);
            StockLedgerEntry corrected = stocks.transfer(new StockTransferCommand("inbound_confirm", current.code(), null,
                    to, receipt.quantity(), receipt.weight(), "管理员更正母单客户，原流水：" + receipt.code()), session);
            if (receipt.code().equals(current.ledgerCode()) || receipts.size() == 1) ledgerCode = corrected.code();
            for (ReceiptLine line : documents.listStoredReceiptLines(current.code(), session)) {
                if (receipt.code().equals(line.ledgerCode())) documents.replaceReceiptLine(current.code(),
                        new ReceiptLine(line.lineNo(), line.materialCode(), line.materialName(), line.goodsName(), line.quantity(),
                                line.quantityUnit(), line.weight(), corrected.code(), line.storageRequirements()), session);
            }
        }
        return ledgerCode;
    }

    private String snapshot(MotherOrderRecord order) {
        return "客户：" + order.customerName() + "（" + order.customerId() + "）；货品：" + order.goodsName() + "；备注：" + text(order.remark());
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String required(String value, String message) {
        String result = text(value);
        if (result.isEmpty()) throw new BusinessException(message);
        return result;
    }
}
