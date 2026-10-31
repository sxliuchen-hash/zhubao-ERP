package com.zhubao.erp.sampling.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.CompanyMaterialIssueService;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentKinds;
import com.zhubao.erp.inventory.domain.StockDocumentLine;
import com.zhubao.erp.inventory.domain.StockDocumentRepository;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.sampling.domain.SamplingMaterialIntent;
import com.zhubao.erp.sampling.domain.SamplingMaterialRepository;
import com.zhubao.erp.sampling.domain.SamplingMaterialUse;
import com.zhubao.erp.sampling.domain.SamplingMaterialUseView;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 把打样领退料接到真实来源。客户来料和公司备料分开，不另建实物账，也不改权属。
 */
@Service
public class SamplingMaterialService {

    private final SamplingOrderRepository orders;
    private final SamplingMaterialRepository uses;
    private final CompanyMaterialReceiptRepository companyReceipts;
    private final CompanyMaterialIssueService companyIssues;
    private final StockDocumentRepository documents;
    private final StockLedgerService ledger;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public SamplingMaterialService(SamplingOrderRepository orders, SamplingMaterialRepository uses,
                                   CompanyMaterialReceiptRepository companyReceipts, CompanyMaterialIssueService companyIssues,
                                   StockDocumentRepository documents, StockLedgerService ledger,
                                   BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.uses = uses;
        this.companyReceipts = companyReceipts;
        this.companyIssues = companyIssues;
        this.documents = documents;
        this.ledger = ledger;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<SamplingMaterialUseView> list(OperatorContext actor, String samplingCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_VIEW, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        return uses.list(order.samplingCode()).stream().map(this::view).toList();
    }

    public SamplingMaterialUseView pick(String requestId, OperatorContext actor, String samplingCode, String receiptId,
                                        String sourceDocumentCode, Integer lineNo, String workshopCode,
                                        BigDecimal quantity, BigDecimal weight, String unitName) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        BigDecimal counted = positive(quantity, "领用数量必须大于零");
        String unitText = required(unitName, "请填写单位");
        String payload = order.materialIntent().name() + "|" + mark(receiptId) + "|" + mark(sourceDocumentCode) + "|"
                + (lineNo == null ? "" : lineNo) + "|" + mark(workshopCode) + "|" + counted.toPlainString() + "|"
                + mark(weight == null ? "" : weight.toPlainString()) + "|" + unitText;
        String useId = unit.execute(new CommandCall(
                requestId, actor.operator(), "PICK_SAMPLING_MATERIAL", "SAMPLING_MATERIAL", order.samplingCode(), payload
        ), session -> order.materialIntent() == SamplingMaterialIntent.COMPANY_PREPARED
                ? pickCompany(session, actor, order, receiptId, sourceDocumentCode, workshopCode, counted, weight, unitText)
                : pickCustomer(session, actor, order, receiptId, sourceDocumentCode, lineNo, counted, weight, unitText));
        return view(uses.find(useId).orElseThrow(() -> new BusinessException("领用不存在")));
    }

    public SamplingMaterialUseView returnMaterial(String requestId, OperatorContext actor, String samplingCode, String useId,
                                                  BigDecimal quantity, BigDecimal weight, String unitName) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        SamplingMaterialUse current = requireUse(order.samplingCode(), useId);
        assertStock(actor, current.owner() == OwnerType.COMPANY
                ? PermissionCode.STOCK_COMPANY_RETURN : PermissionCode.STOCK_PREP_RETURN);
        BigDecimal counted = positive(quantity, "退回数量必须大于零");
        String unitText = required(unitName, "请填写单位");
        String payload = current.useId() + "|" + counted.toPlainString() + "|"
                + mark(weight == null ? "" : weight.toPlainString()) + "|" + unitText;
        unit.execute(new CommandCall(
                requestId, actor.operator(), "RETURN_SAMPLING_MATERIAL", "SAMPLING_MATERIAL", current.useId(), payload
        ), session -> {
            SamplingMaterialUse locked = uses.lock(current.useId(), session)
                    .orElseThrow(() -> new BusinessException("领用不存在"));
            SamplingMaterialUse next = locked.returnMore(counted);
            if (!locked.unit().equals(unitText)) {
                throw new BusinessException("退回单位必须与原领用一致");
            }
            if (locked.owner() == OwnerType.COMPANY) {
                companyIssues.returnWithin(session, locked.stockMoveId(), companyIssues.currentVersion(locked.stockMoveId()),
                        counted, weight, unitText);
            } else {
                returnCustomer(session, locked, counted, weight);
            }
            uses.save(next, locked.version(), session);
            return locked.useId();
        });
        return view(uses.find(current.useId()).orElseThrow(() -> new BusinessException("领用不存在")));
    }

    public SamplingMaterialUseView recordInput(String requestId, OperatorContext actor, String samplingCode, String useId,
                                               BigDecimal quantity) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        SamplingMaterialUse current = requireUse(order.samplingCode(), useId);
        BigDecimal counted = positive(quantity, "实际投入必须大于零");
        unit.execute(new CommandCall(
                requestId, actor.operator(), "RECORD_SAMPLING_INPUT", "SAMPLING_MATERIAL", current.useId(),
                current.useId() + "|" + counted.toPlainString()
        ), session -> {
            SamplingMaterialUse locked = uses.lock(current.useId(), session)
                    .orElseThrow(() -> new BusinessException("领用不存在"));
            uses.save(locked.recordInput(counted), locked.version(), session);
            return locked.useId();
        });
        return view(uses.find(current.useId()).orElseThrow(() -> new BusinessException("领用不存在")));
    }

    private String pickCompany(WorkSession session, OperatorContext actor, SamplingOrder order, String receiptId,
                               String sourceDocumentCode, String workshopCode, BigDecimal quantity, BigDecimal weight, String unitName) {
        assertStock(actor, PermissionCode.STOCK_COMPANY_ISSUE);
        if (!text(sourceDocumentCode).isEmpty()) {
            throw new BusinessException("公司自备不能使用客户收货单");
        }
        CompanyMaterialReceipt receipt = companyReceipts.find(text(receiptId))
                .orElseThrow(() -> new BusinessException("公司收货不存在"));
        if (!receipt.unit().equals(unitName)) {
            throw new BusinessException("单位必须与原批次一致");
        }
        String issueId = companyIssues.issueWithin(session, receipt.itemId(), receipt.batchCode(), receipt.warehouseCode(),
                workshopCode, quantity, weight, unitName, order.samplingCode());
        StockMeasurement.Weight measured = weight == null ? StockMeasurement.unknown() : StockMeasurement.known(StockMeasurement.processingScale(weight));
        if (weight != null && weight.signum() == 0) {
            throw new BusinessException("未知重量不能写成 0");
        }
        String useId = token();
        uses.insert(new SamplingMaterialUse(
                useId, order.samplingCode(), OwnerType.COMPANY, receipt.receiptId(), null, issueId, receipt.itemId(),
                receipt.batchCode(), quantity, unitName, measured, BigDecimal.ZERO, null, 1
        ), session);
        return useId;
    }

    private String pickCustomer(WorkSession session, OperatorContext actor, SamplingOrder order, String receiptId,
                                String sourceDocumentCode, Integer lineNo, BigDecimal quantity, BigDecimal weight, String unitName) {
        assertStock(actor, PermissionCode.STOCK_PREP_ISSUE);
        if (!text(receiptId).isEmpty()) {
            throw new BusinessException("客户来料不能领用公司批次");
        }
        if (lineNo == null || lineNo < 1) {
            throw new BusinessException("请选择收货明细");
        }
        StockDocument document = documents.lock(text(sourceDocumentCode), session)
                .orElseThrow(() -> new BusinessException("客户收货不存在"));
        StockDocumentLine line = customerLine(document, order, lineNo, unitName);
        BigDecimal measured = knownWeight(weight, line.weight());
        BigDecimal already = uses.lockSource(document.documentCode(), line.lineNo(), session).stream()
                .map(use -> use.quantity().subtract(use.returnedQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (already.add(quantity).compareTo(line.quantity()) > 0) {
            throw new BusinessException("来源数量不足");
        }
        String useId = token();
        StockDimension from = sourceDimension(order.customerId(), line.materialCode(), document.warehouseCode(), line.batchCode());
        StockDimension to = holdingDimension(order.customerId(), order.samplingCode(), line.materialCode(), document.warehouseCode(), line.batchCode());
        StockLedgerEntry entry = ledger.transfer(new StockTransferCommand(
                "sampling_customer_issue", useId, from, to, quantity, measured, "打样领用客户来料"), session);
        uses.insert(new SamplingMaterialUse(
                useId, order.samplingCode(), OwnerType.CUSTOMER, document.documentCode(), line.lineNo(), entry.code(),
                line.materialCode(), line.batchCode(), quantity, unitName, StockMeasurement.known(measured),
                BigDecimal.ZERO, null, 1
        ), session);
        return useId;
    }

    private void returnCustomer(WorkSession session, SamplingMaterialUse use, BigDecimal quantity, BigDecimal weight) {
        BigDecimal measured = knownWeight(weight, use.weight().value());
        StockDocument document = documents.lock(use.sourceId(), session)
                .orElseThrow(() -> new BusinessException("客户收货不存在"));
        StockDimension holding = holdingDimension(document.customerId(), use.samplingCode(), use.materialCode(),
                document.warehouseCode(), use.batchCode());
        StockDimension source = sourceDimension(document.customerId(), use.materialCode(), document.warehouseCode(), use.batchCode());
        ledger.transfer(new StockTransferCommand(
                "sampling_customer_return", use.useId(), holding, source, quantity, measured, "打样退回客户来料"), session);
    }

    private StockDocumentLine customerLine(StockDocument document, SamplingOrder order, int lineNo, String unitName) {
        if (!StockDocumentKinds.PREP_RECEIPT.equals(document.kind()) || !StockDocumentKinds.POSTED.equals(document.status())
                || !OwnerType.CUSTOMER.name().equals(document.ownerType())) {
            throw new BusinessException("来源不是客户收货");
        }
        if (document.customerId() == null || document.customerId() != order.customerId()) {
            throw new BusinessException("来源客户与打样单不一致");
        }
        StockDocumentLine line = document.lines().stream().filter(item -> item.lineNo() == lineNo).findFirst()
                .orElseThrow(() -> new BusinessException("收货明细不存在"));
        if (!line.counted() || line.batchCode().isBlank()) {
            throw new BusinessException("来源尚未入账，不能领用");
        }
        if (!line.quantityUnit().equals(unitName)) {
            throw new BusinessException("单位必须与原批次一致");
        }
        return line;
    }

    private SamplingMaterialUseView view(SamplingMaterialUse use) {
        return new SamplingMaterialUseView(
                use.useId(), use.samplingCode(), use.owner().name(), use.sourceId(), use.sourceLineNo(), use.stockMoveId(),
                use.materialCode(), use.batchCode(), use.quantity(), use.unit(), use.weight().state().name(), use.weight().value(),
                use.returnedQuantity(), use.inputQuantity(), use.version()
        );
    }

    private SamplingOrder requireVisible(OperatorContext actor, String samplingCode) {
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE
                && (actor.customerScope() == null || actor.customerScope().isEmpty())) {
            throw new PermissionDeniedException("客户范围账号没有绑定客户");
        }
        SamplingOrder order = orders.find(samplingCode == null ? "" : samplingCode.trim())
                .orElseThrow(() -> new BusinessException("打样单不存在"));
        if (!CustomerScopeRules.allowed(actor, order.customerId(), order.customerName())) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
        return order;
    }

    private SamplingMaterialUse requireUse(String samplingCode, String useId) {
        SamplingMaterialUse use = uses.find(text(useId)).orElseThrow(() -> new BusinessException("领用不存在"));
        if (!use.samplingCode().equals(samplingCode)) {
            throw new BusinessException("领用不属于这张打样单");
        }
        return use;
    }

    private static void assertStock(PermissionGuard guard, PermissionCode permission, OperatorContext actor) {
        RefactorPermissionContract.assertAction(guard, permission, actor);
    }

    private void assertStock(OperatorContext actor, PermissionCode permission) {
        assertStock(permissions, permission, actor);
    }

    private static BigDecimal knownWeight(BigDecimal requested, BigDecimal sourceWeight) {
        if (sourceWeight == null) {
            throw new BusinessException("来源重量未齐，不能按 0 领用");
        }
        if (requested == null || requested.signum() == 0) {
            throw new BusinessException("未知重量不能写成 0");
        }
        if (requested.signum() < 0) {
            throw new BusinessException("重量不能为负");
        }
        BigDecimal measured = StockMeasurement.processingScale(requested);
        if (measured.compareTo(sourceWeight) > 0) {
            throw new BusinessException("来源库存重量不足");
        }
        return measured;
    }

    private static BigDecimal positive(BigDecimal quantity, String message) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException(message);
        }
        return StockMeasurement.processingScale(quantity);
    }

    private static StockDimension sourceDimension(long customerId, String material, String warehouse, String batch) {
        return new StockDimension(OwnerType.CUSTOMER, customerId, customerId, "", null, material, warehouse,
                StockStatus.RAW_WAITING, batch);
    }

    private static StockDimension holdingDimension(long customerId, String samplingCode, String material, String warehouse, String batch) {
        return new StockDimension(OwnerType.CUSTOMER, customerId, customerId, "", samplingCode, material, warehouse,
                StockStatus.WIP, batch);
    }

    private static String token() {
        return "SU-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String required(String value, String message) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }

    private static String mark(String value) {
        return text(value).replace('|', '/');
    }
}
