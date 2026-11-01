package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.FixedAssetRepairPayment;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetRepair;
import com.zhubao.erp.finance.domain.FixedAssetRepairRepository;
import com.zhubao.erp.finance.domain.FixedAssetRepairState;
import com.zhubao.erp.finance.domain.FixedAssetRepairView;
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
 * 登记资产维修，并在明确确认后关联唯一费用来源。
 * 付款只增加已付，不另记成本。维修不报废资产，也不改原值或折旧。
 */
@Service
public class FixedAssetRepairService {

    private final FinanceRepository finance;
    private final FixedAssetRepairRepository repairs;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;
    private final AtomicLong sequence = new AtomicLong();

    public FixedAssetRepairService(FinanceRepository finance, FixedAssetRepairRepository repairs,
                                   BusinessWorkUnit unit, PermissionGuard permissions) {
        this.finance = finance;
        this.repairs = repairs;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<FixedAssetRepairView> list(OperatorContext actor, String assetCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        FixedAsset asset = requireAsset(assetCode);
        return repairs.list(asset.code()).stream().map(row -> view(row, asset)).toList();
    }

    public FixedAssetRepairView record(String requestId, OperatorContext actor, String assetCode, String repairedOn,
                                       String handoverName, String evidenceCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        String code = required(assetCode, "请选择固定资产");
        String day = FinanceTexts.day(repairedOn).toString();
        String handover = fact(handoverName, "交接");
        String evidence = fact(evidenceCode, "凭据");
        String payload = code + "|" + day + "|" + mark(handover) + "|" + mark(evidence);
        String id = unit.execute(new CommandCall(
                requestId, actor.operator(), "RECORD_ASSET_REPAIR", "FIXED_ASSET", code, payload
        ), session -> {
            requireAsset(code);
            String repairId = FinanceTexts.code("FIN-RPR", sequence);
            repairs.insert(new FixedAssetRepair(
                    repairId, code, day, handover, evidence, FixedAssetRepairState.RECORDED, ""
            ), session);
            return repairId;
        });
        return view(repairs.find(id).orElseThrow(() -> new BusinessException("维修记录不存在")), requireAsset(code));
    }

    public FixedAssetRepairView confirm(String requestId, OperatorContext actor, String repairId, BigDecimal amount, String evidenceCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_PAYABLE, actor);
        BigDecimal cost = money(amount);
        if (cost == null) {
            throw new BusinessException("金额未齐，不能确认维修费用");
        }
        String evidence = fact(evidenceCode, "凭据");
        String id = required(repairId, "请选择维修记录");
        String payload = id + "|" + cost.toPlainString() + "|" + mark(evidence);
        unit.execute(new CommandCall(
                requestId, actor.operator(), "CONFIRM_ASSET_REPAIR", "FIXED_ASSET", id, payload
        ), session -> confirmIn(session, actor, id, cost, evidence));
        FixedAssetRepair repair = repairs.find(id).orElseThrow(() -> new BusinessException("维修记录不存在"));
        return view(repair, requireAsset(repair.assetCode()));
    }

    public FixedAssetRepairView pay(String requestId, OperatorContext actor, String repairId, BigDecimal amount,
                                    String paidOn, String voucher) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_PAYABLE, actor);
        BigDecimal paying = money(amount);
        String day = text(paidOn);
        String proof = fact(voucher, "付款凭据");
        if (paying == null || day.isEmpty() || proof.isEmpty()) {
            throw new BusinessException(proof.isEmpty() ? "没有付款凭据，不能标成已付" : "付款凭据不完整，不能标成已付");
        }
        FinanceTexts.day(day);
        String id = required(repairId, "请选择维修记录");
        String payload = id + "|" + paying.toPlainString() + "|" + day + "|" + proof;
        unit.execute(new CommandCall(
                requestId, actor.operator(), "PAY_ASSET_REPAIR", "FIXED_ASSET", id, payload
        ), session -> payIn(session, actor, id, paying, day, proof));
        FixedAssetRepair repair = repairs.find(id).orElseThrow(() -> new BusinessException("维修记录不存在"));
        return view(repair, requireAsset(repair.assetCode()));
    }

    private String confirmIn(WorkSession session, OperatorContext actor, String repairId, BigDecimal cost, String evidence) {
        FixedAssetRepair repair = repairs.lock(repairId, session).orElseThrow(() -> new BusinessException("维修记录不存在"));
        requireAsset(repair.assetCode());
        if (repair.state() == FixedAssetRepairState.CONFIRMED) {
            throw new BusinessException("维修费用已经确认");
        }
        String proof = evidence.isEmpty() ? repair.evidenceCode() : evidence;
        if (proof.isEmpty()) {
            throw new BusinessException("维修费用必须有凭据");
        }
        String payableCode = FinanceTexts.code("FIN-AP", sequence);
        finance.savePayable(new Payable(
                payableCode, "ASSET_REPAIR", repair.repairedOn().substring(0, 7),
                repair.handoverName(),
                "REPAIR|" + repair.repairId(), null, "", cost, FinanceTexts.ZERO,
                actor.operator(), FinanceTexts.now()
        ), session);
        repairs.confirm(repair.repairId(), proof, payableCode, session);
        return repair.repairId();
    }

    private String payIn(WorkSession session, OperatorContext actor, String repairId, BigDecimal paying, String paidOn, String proof) {
        FixedAssetRepair repair = repairs.lock(repairId, session).orElseThrow(() -> new BusinessException("维修记录不存在"));
        if (repair.state() != FixedAssetRepairState.CONFIRMED || repair.payableCode().isEmpty()) {
            throw new BusinessException("费用尚未确认，不能付款");
        }
        int before = finance.listPayables().size();
        PayablePayment payment = finance.registerPayablePayment(new PayablePayment(
                FinanceTexts.code("FIN-PMT", sequence), repair.payableCode(), paying, paidOn,
                actor.operator(), FinanceTexts.now()
        ), "", session);
        repairs.savePayment(repairId, new FixedAssetRepairPayment(payment.code(), proof), session);
        if (finance.listPayables().size() != before) {
            throw new BusinessException("付款不能再记一份维修成本");
        }
        return repair.repairId();
    }

    private FixedAssetRepairView view(FixedAssetRepair repair, FixedAsset asset) {
        Payable payable = repair.payableCode().isEmpty()
                ? null
                : finance.findPayable(repair.payableCode()).orElseThrow(() -> new BusinessException("应付不存在"));
        BigDecimal paidAmount = payable == null ? null : payable.paidAmount();
        return new FixedAssetRepairView(
                repair.repairId(),
                repair.assetCode(),
                asset.originalValue(),
                repair.repairedOn(),
                label(repair.handoverName()),
                label(repair.evidenceCode()),
                repair.state().name(),
                repair.state() == FixedAssetRepairState.RECORDED ? "维修中" : "费用已确认",
                repair.payableCode(),
                payable == null ? null : payable.amount(),
                paidAmount,
                paidAmount != null && paidAmount.signum() > 0,
                repairs.payments(repair.repairId())
        );
    }

    private FixedAsset requireAsset(String assetCode) {
        String code = required(assetCode, "请选择固定资产");
        return finance.listAssets().stream()
                .filter(asset -> code.equals(asset.code()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到固定资产"));
    }

    private static BigDecimal money(BigDecimal value) {
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

    private static String fact(String value, String label) {
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
        return text;
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? "待补" : value;
    }

    private static String required(String value, String message) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        if (text.length() > 80) {
            throw new BusinessException("编号过长");
        }
        return text;
    }

    private static String mark(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
