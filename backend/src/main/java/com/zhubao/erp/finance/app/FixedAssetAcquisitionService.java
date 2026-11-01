package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetAcquisition;
import com.zhubao.erp.finance.domain.FixedAssetAcquisitionRepository;
import com.zhubao.erp.finance.domain.FixedAssetAcquisitionSource;
import com.zhubao.erp.finance.domain.FixedAssetAcquisitionView;
import com.zhubao.erp.finance.domain.FixedAssetAmountState;
import com.zhubao.erp.finance.domain.FixedAssetPhysical;
import com.zhubao.erp.finance.domain.FixedAssetPhysicalRepository;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 记录资产购置来源，并在同一次提交里关联应付或付款。
 * 已有卡片不补一笔采购。付款不改原值，也不记折旧。没有凭据不能标成已付。
 */
@Service
public class FixedAssetAcquisitionService {

    private final FinanceRepository finance;
    private final FixedAssetAcquisitionRepository acquisitions;
    private final FixedAssetPhysicalRepository physical;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;
    private final AtomicLong sequence = new AtomicLong();

    public FixedAssetAcquisitionService(FinanceRepository finance, FixedAssetAcquisitionRepository acquisitions,
                                        FixedAssetPhysicalRepository physical, BusinessWorkUnit unit,
                                        PermissionGuard permissions) {
        this.finance = finance;
        this.acquisitions = acquisitions;
        this.physical = physical;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<FixedAssetAcquisitionView> list(OperatorContext actor, String assetCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        String code = text(assetCode);
        return acquisitions.list().stream()
                .filter(row -> code.isEmpty() || code.equals(row.assetCode()))
                .map(this::view)
                .toList();
    }

    public FixedAssetAcquisitionView receive(String requestId, OperatorContext actor, String sourceText, String assetCode,
                                             String name, String category, BigDecimal originalValue, BigDecimal residualValue,
                                             String startMonth, Integer usefulMonths, String supplierName, String acquiredOn,
                                             String handoverLocation, String handoverReceiver, String coveredPayableCode,
                                             BigDecimal paymentAmount, String paymentDate, String paymentVoucher) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        FixedAssetAcquisitionSource source = source(sourceText);
        String code = text(assetCode);
        String supplier = fact(supplierName, "供应商", false);
        String day = text(acquiredOn);
        String location = fact(handoverLocation, "位置", true);
        String receiver = fact(handoverReceiver, "保管人", true);
        String covered = text(coveredPayableCode);
        String voucher = fact(paymentVoucher, "付款凭据", true);
        String paidOn = text(paymentDate);
        BigDecimal original = original(originalValue);
        BigDecimal paying = payment(paymentAmount);
        if (paymentAttempt(paying, paidOn, voucher)) {
            if (voucher.isEmpty() || paidOn.isEmpty() || paying == null) {
                throw new BusinessException(voucher.isEmpty()
                        ? "没有付款凭据，不能标成已付"
                        : "付款凭据不完整，不能标成已付");
            }
            FinanceTexts.day(paidOn);
        }
        if (source != FixedAssetAcquisitionSource.EXISTING_CARD || paymentAttempt(paying, paidOn, voucher)) {
            RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_PAYABLE, actor);
        }
        String payload = source + "|" + mark(code) + "|" + mark(name) + "|" + mark(category) + "|"
                + markMoney(original) + "|" + markMoney(residualValue) + "|" + mark(startMonth) + "|"
                + (usefulMonths == null ? "-" : usefulMonths) + "|" + mark(supplier) + "|" + mark(day) + "|"
                + mark(location) + "|" + mark(receiver) + "|" + mark(covered) + "|"
                + markMoney(paying) + "|" + mark(paidOn) + "|" + mark(voucher);
        String id = unit.execute(new CommandCall(
                requestId, actor.operator(), "RECEIVE_FIXED_ASSET", "FIXED_ASSET",
                code.isEmpty() ? "NEW" : code, payload
        ), session -> apply(session, actor, source, code, name, category, original, residualValue, startMonth,
                usefulMonths, supplier, day, location, receiver, covered, paying, paidOn, voucher));
        return view(acquisitions.find(id).orElseThrow(() -> new BusinessException("资产购置来源不存在")));
    }

    private String apply(WorkSession session, OperatorContext actor, FixedAssetAcquisitionSource source, String assetCode,
                         String name, String category, BigDecimal original, BigDecimal residual, String startMonth,
                         Integer usefulMonths, String supplier, String day, String location, String receiver,
                         String covered, BigDecimal paying, String paidOn, String voucher) {
        if (source == FixedAssetAcquisitionSource.EXISTING_CARD) {
            return adoptExisting(session, actor, assetCode, original, supplier, day, location, receiver, paying, paidOn, voucher);
        }
        if (source == FixedAssetAcquisitionSource.COVERED_PAYABLE) {
            return coverPayable(session, actor, assetCode, name, category, original, residual, startMonth, usefulMonths,
                    supplier, day, location, receiver, covered, paying, paidOn, voucher);
        }
        return purchase(session, actor, assetCode, name, category, original, residual, startMonth, usefulMonths,
                supplier, day, location, receiver, covered, paying, paidOn, voucher);
    }

    private String adoptExisting(WorkSession session, OperatorContext actor, String assetCode, BigDecimal original,
                                 String supplier, String day, String location, String receiver,
                                 BigDecimal paying, String paidOn, String voucher) {
        if (paymentAttempt(paying, paidOn, voucher)) {
            throw new BusinessException("已有资产不补记采购或付款");
        }
        FixedAsset asset = requireAsset(assetCode);
        if (original != null && original.compareTo(asset.originalValue()) != 0) {
            throw new BusinessException("不能改原值");
        }
        if (!day.isEmpty()) {
            FinanceTexts.day(day);
        }
        if (acquisitions.lockByAsset(asset.code(), session).isPresent()) {
            throw new BusinessException("该资产已经记录购置来源，不能再补一笔采购");
        }
        String id = FinanceTexts.code("FIN-ACQ", sequence);
        fillHandover(asset.code(), location, receiver, session);
        acquisitions.insert(new FixedAssetAcquisition(
                id, asset.code(), FixedAssetAcquisitionSource.EXISTING_CARD, FixedAssetAmountState.KNOWN,
                supplier, day, blankToNull(location), blankToNull(receiver), "", "", "", ""
        ), session);
        return id;
    }

    private String purchase(WorkSession session, OperatorContext actor, String assetCode, String name, String category,
                            BigDecimal original, BigDecimal residual, String startMonth, Integer usefulMonths,
                            String supplier, String day, String location, String receiver, String covered,
                            BigDecimal paying, String paidOn, String voucher) {
        if (!assetCode.isEmpty()) {
            if (findAsset(assetCode) != null) {
                throw new BusinessException("已有资产不自动补一笔采购");
            }
            throw new BusinessException("新购置不能指定资产编码");
        }
        if (!covered.isEmpty()) {
            throw new BusinessException("新购置不能同时指定已有应付");
        }
        required(supplier, "请填写供应商");
        FinanceTexts.day(day);
        if (paymentAttempt(paying, paidOn, voucher) && original == null) {
            throw new BusinessException("金额未齐，不能付款");
        }
        String id = FinanceTexts.code("FIN-ACQ", sequence);
        if (original == null) {
            acquisitions.insert(new FixedAssetAcquisition(
                    id, "", FixedAssetAcquisitionSource.PURCHASE, FixedAssetAmountState.INCOMPLETE,
                    supplier, day, blankToNull(location), blankToNull(receiver), "", "", "", ""
            ), session);
            return id;
        }
        FixedAsset asset = createAsset(session, actor, name, category, original, residual, startMonth, usefulMonths);
        String payableCode = FinanceTexts.code("FIN-AP", sequence);
        finance.savePayable(new Payable(
                payableCode, "ASSET", day.substring(0, 7), supplier, "AST|" + id, null, "",
                original, FinanceTexts.ZERO, actor.operator(), FinanceTexts.now()
        ), session);
        String paymentCode = pay(session, actor, payableCode, paying, paidOn, voucher);
        fillHandover(asset.code(), location, receiver, session);
        acquisitions.insert(new FixedAssetAcquisition(
                id, asset.code(), FixedAssetAcquisitionSource.PURCHASE, FixedAssetAmountState.KNOWN,
                supplier, day, blankToNull(location), blankToNull(receiver), payableCode, "", paymentCode, voucher
        ), session);
        return id;
    }

    private String coverPayable(WorkSession session, OperatorContext actor, String assetCode, String name, String category,
                                BigDecimal original, BigDecimal residual, String startMonth, Integer usefulMonths,
                                String supplier, String day, String location, String receiver, String covered,
                                BigDecimal paying, String paidOn, String voucher) {
        if (covered.isEmpty()) {
            throw new BusinessException("请选择已经存在的应付");
        }
        FinanceTexts.day(day);
        Payable payable = finance.findPayable(covered).orElseThrow(() -> new BusinessException("应付不存在"));
        assertAssetPayable(payable);
        if (acquisitions.coversPayable(payable.code(), session)) {
            throw new BusinessException("这笔应付已经对应过资产");
        }
        if (original != null && original.compareTo(payable.amount()) != 0) {
            throw new BusinessException("不能改已有应付金额");
        }
        String storedSupplier = supplier.isEmpty() ? payable.payee() : supplier;
        FixedAsset asset;
        if (!assetCode.isEmpty()) {
            asset = requireAsset(assetCode);
            if (asset.originalValue().compareTo(payable.amount()) != 0) {
                throw new BusinessException("应付金额与资产原值不一致");
            }
            if (acquisitions.lockByAsset(asset.code(), session).isPresent()) {
                throw new BusinessException("该资产已经记录购置来源，不能再补一笔采购");
            }
        } else {
            asset = createAsset(session, actor, name, category, payable.amount(), residual, startMonth, usefulMonths);
        }
        String paymentCode = pay(session, actor, payable.code(), paying, paidOn, voucher);
        String id = FinanceTexts.code("FIN-ACQ", sequence);
        fillHandover(asset.code(), location, receiver, session);
        acquisitions.insert(new FixedAssetAcquisition(
                id, asset.code(), FixedAssetAcquisitionSource.COVERED_PAYABLE, FixedAssetAmountState.KNOWN,
                storedSupplier, day, blankToNull(location), blankToNull(receiver), "", payable.code(), paymentCode, voucher
        ), session);
        return id;
    }

    private String pay(WorkSession session, OperatorContext actor, String payableCode, BigDecimal paying, String paidOn, String voucher) {
        if (!paymentAttempt(paying, paidOn, voucher)) {
            return "";
        }
        String paymentCode = FinanceTexts.code("FIN-PMT", sequence);
        finance.registerPayablePayment(new PayablePayment(
                paymentCode, payableCode, paying, paidOn, actor.operator(), FinanceTexts.now()
        ), "", session);
        return paymentCode;
    }

    private FixedAsset createAsset(WorkSession session, OperatorContext actor, String name, String category,
                                   BigDecimal original, BigDecimal residual, String startMonth, Integer usefulMonths) {
        String assetName = required(text(name), "请填写资产名称");
        String assetCategory = required(text(category), "请填写资产分类");
        if (residual == null) {
            throw new BusinessException("请填写残值");
        }
        if (residual.signum() < 0) {
            throw new BusinessException("金额不能为负");
        }
        if (residual.scale() > 2) {
            throw new BusinessException("金额最多两位小数");
        }
        BigDecimal residualValue = residual.setScale(2, RoundingMode.HALF_UP);
        if (residualValue.compareTo(original) >= 0) {
            throw new BusinessException("残值必须小于原值");
        }
        if (usefulMonths == null || usefulMonths < 1) {
            throw new BusinessException("请填写使用月数");
        }
        FinanceTexts.month(startMonth);
        FixedAsset asset = new FixedAsset(
                FinanceTexts.code("FIN-AST", sequence), assetName, assetCategory, original, residualValue,
                startMonth.trim(), usefulMonths, actor.operator(), FinanceTexts.now()
        );
        finance.saveAsset(asset, session);
        return asset;
    }

    private void fillHandover(String assetCode, String location, String receiver, WorkSession session) {
        if (location.isEmpty() && receiver.isEmpty()) {
            return;
        }
        FixedAssetPhysical current = physical.lock(assetCode, session).orElse(null);
        String nextLocation = mergeFact(current == null ? null : current.locationName(), location, "位置");
        String nextReceiver = mergeFact(current == null ? null : current.custodianName(), receiver, "保管人");
        if (current == null) {
            physical.insert(new FixedAssetPhysical(
                    assetCode, null, null, blankToNull(nextLocation), blankToNull(nextReceiver), null, 1
            ), session);
            return;
        }
        if (!differs(current.locationName(), nextLocation) && !differs(current.custodianName(), nextReceiver)) {
            return;
        }
        physical.replace(new FixedAssetPhysical(
                assetCode, current.modelName(), current.serialNumber(), blankToNull(nextLocation),
                blankToNull(nextReceiver), current.useStatus(), current.version() + 1
        ), current.version(), session);
    }

    private FixedAssetAcquisitionView view(FixedAssetAcquisition row) {
        FixedAsset asset = row.assetCode().isEmpty() ? null : requireAsset(row.assetCode());
        String payableCode = !row.payableCode().isEmpty() ? row.payableCode() : row.coveredPayableCode();
        Payable payable = payableCode.isEmpty()
                ? null
                : finance.findPayable(payableCode).orElseThrow(() -> new BusinessException("应付不存在"));
        BigDecimal paidAmount = payable == null ? null : payable.paidAmount();
        return new FixedAssetAcquisitionView(
                row.acquisitionId(),
                row.assetCode(),
                row.source().name(),
                row.amountState().name(),
                asset == null ? null : asset.originalValue(),
                label(row.supplierName()),
                label(row.acquiredOn()),
                label(row.handoverLocation()),
                label(row.handoverReceiver()),
                row.payableCode(),
                row.coveredPayableCode(),
                row.paymentCode(),
                paidAmount,
                paidAmount != null && paidAmount.signum() > 0
        );
    }

    private FixedAsset requireAsset(String assetCode) {
        FixedAsset asset = findAsset(assetCode);
        if (asset == null) {
            throw new BusinessException(assetCode == null || assetCode.isBlank() ? "请选择已有资产" : "找不到固定资产");
        }
        return asset;
    }

    private FixedAsset findAsset(String assetCode) {
        if (assetCode == null || assetCode.isBlank()) {
            return null;
        }
        return finance.listAssets().stream().filter(asset -> assetCode.equals(asset.code())).findFirst().orElse(null);
    }

    private static void assertAssetPayable(Payable payable) {
        String kind = payable.kind() == null ? "" : payable.kind();
        String source = payable.sourceCode() == null ? "" : payable.sourceCode();
        if (!(kind.equals("ASSET") || kind.equals("设备") || kind.equals("固定资产"))
                || source.startsWith("REPAIR|") || source.startsWith("CONSUMABLE|") || source.startsWith("CMR|")
                || source.startsWith("SETTLEMENT|") || source.startsWith("employee-target:")) {
            throw new BusinessException("工资、外协或物料应付不能当作资产原值");
        }
    }

    private static FixedAssetAcquisitionSource source(String value) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException("请选择购置来源");
        }
        try {
            return FixedAssetAcquisitionSource.valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("购置来源无效");
        }
    }

    private static BigDecimal original(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            throw new BusinessException("未知金额不能写成 0");
        }
        if (value.signum() < 0) {
            throw new BusinessException("金额不能为负");
        }
        if (value.scale() > 2) {
            throw new BusinessException("金额最多两位小数");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal payment(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            throw new BusinessException("未知金额不能写成 0");
        }
        if (value.signum() < 0) {
            throw new BusinessException("金额不能为负");
        }
        if (value.scale() > 2) {
            throw new BusinessException("金额最多两位小数");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean paymentAttempt(BigDecimal amount, String day, String voucher) {
        return amount != null || !day.isEmpty() || !voucher.isEmpty();
    }

    private static String mergeFact(String current, String incoming, String label) {
        if (incoming == null || incoming.isEmpty()) {
            return current == null ? "" : current;
        }
        if (current == null || current.isEmpty()) {
            return incoming;
        }
        if (current.equals(incoming)) {
            return current;
        }
        throw new BusinessException("已登记的" + label + "不能在购置交接里改写");
    }

    private static String fact(String value, String label, boolean optional) {
        String text = text(value);
        if (text.isEmpty()) {
            return "";
        }
        if (text.equals("待补") || text.equals("未补") || text.equals("未知") || text.equals("待核")) {
            throw new BusinessException("不能把待补写成" + label);
        }
        if (text.length() > 80) {
            throw new BusinessException(label + "过长");
        }
        if (!optional && text.isEmpty()) {
            throw new BusinessException("请填写" + label);
        }
        return text;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? "待补" : value;
    }

    private static String mark(String value) {
        String text = text(value);
        return text.isEmpty() ? "-" : text;
    }

    private static String markMoney(BigDecimal value) {
        return value == null ? "-" : value.toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static boolean differs(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        return !a.equals(b);
    }
}
