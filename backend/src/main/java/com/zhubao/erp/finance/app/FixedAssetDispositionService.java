package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.FixedAssetDisposition;
import com.zhubao.erp.finance.domain.FixedAssetDispositionRepository;
import com.zhubao.erp.finance.domain.FixedAssetDispositionView;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 记录停用、报废或处置。原卡保留。没有真实收款就不记回款。
 * 净值、费用归属和折旧生效月尚未核定，不填默认值，也不扣员工工资。
 */
@Service
public class FixedAssetDispositionService {

    private final FinanceRepository finance;
    private final FixedAssetDispositionRepository dispositions;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;
    private final AtomicLong sequence = new AtomicLong();

    public FixedAssetDispositionService(FinanceRepository finance, FixedAssetDispositionRepository dispositions,
                                        BusinessWorkUnit unit, PermissionGuard permissions) {
        this.finance = finance;
        this.dispositions = dispositions;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<FixedAssetDispositionView> list(OperatorContext actor, String assetCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        FixedAsset asset = requireAsset(assetCode);
        return dispositions.list(asset.code()).stream().map(row -> view(row, asset)).toList();
    }

    public FixedAssetDispositionView record(String requestId, OperatorContext actor, String assetCode, String kind,
                                            String occurredOn, String basis, String receiptCode, BigDecimal proceeds) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_ASSET, actor);
        String code = required(assetCode, "请选择固定资产");
        String action = kind(kind);
        String day = FinanceTexts.day(occurredOn).toString();
        String reason = required(basis, "请填写依据");
        if (reason.equals("待补") || reason.equals("未知")) {
            throw new BusinessException("不能把待补写成依据");
        }
        String receipt = receiptCode == null ? "" : receiptCode.trim();
        if (proceeds != null && proceeds.signum() == 0) {
            throw new BusinessException("未知金额不能写成 0");
        }
        if (proceeds != null && receipt.isEmpty()) {
            throw new BusinessException("处置回款必须另有真实资金来源");
        }
        if (!receipt.isEmpty()) {
            RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_RECEIPT, actor);
        }
        String payload = code + "|" + action + "|" + day + "|" + reason + "|" + (receipt.isEmpty() ? "-" : receipt)
                + "|" + (proceeds == null ? "-" : proceeds.toPlainString());
        String id = unit.execute(new CommandCall(
                requestId, actor.operator(), "RECORD_ASSET_DISPOSITION", "FIXED_ASSET", code, payload
        ), session -> {
            FixedAsset asset = requireAsset(code);
            CustomerReceipt source = null;
            if (!receipt.isEmpty()) {
                source = finance.listReceipts().stream()
                        .filter(item -> receipt.equals(item.code()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("找不到资金来源"));
                if (proceeds != null && proceeds.compareTo(source.amount()) != 0) {
                    throw new BusinessException("不能改已有回款金额");
                }
                if (dispositions.coversReceipt(source.code(), session)) {
                    throw new BusinessException("这笔回款已经对应过处置");
                }
            }
            String dispositionId = FinanceTexts.code("FIN-DSP", sequence);
            dispositions.insert(new FixedAssetDisposition(
                    dispositionId, asset.code(), action, day, reason, source == null ? "" : source.code()
            ), session);
            return dispositionId;
        });
        FixedAsset asset = requireAsset(code);
        return dispositions.list(code).stream()
                .filter(row -> id.equals(row.dispositionId()))
                .findFirst()
                .map(row -> view(row, asset))
                .orElseThrow(() -> new BusinessException("资产处置不存在"));
    }

    private FixedAssetDispositionView view(FixedAssetDisposition row, FixedAsset asset) {
        CustomerReceipt receipt = row.receiptCode().isEmpty()
                ? null
                : finance.listReceipts().stream().filter(item -> row.receiptCode().equals(item.code())).findFirst().orElse(null);
        return new FixedAssetDispositionView(
                row.dispositionId(),
                row.assetCode(),
                asset.originalValue(),
                row.kind(),
                switch (row.kind()) {
                    case FixedAssetDisposition.STOP -> "停用";
                    case FixedAssetDisposition.SCRAP -> "报废";
                    case FixedAssetDisposition.DISPOSE -> "处置";
                    default -> row.kind();
                },
                row.occurredOn(),
                row.basis(),
                FixedAssetDisposition.IMPACT_PENDING,
                "待核",
                null,
                row.receiptCode(),
                receipt == null ? null : receipt.amount(),
                receipt != null
        );
    }

    private FixedAsset requireAsset(String assetCode) {
        String code = required(assetCode, "请选择固定资产");
        return finance.listAssets().stream()
                .filter(asset -> code.equals(asset.code()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到固定资产"));
    }

    private static String kind(String value) {
        String text = value == null ? "" : value.trim();
        if (text.equals(FixedAssetDisposition.STOP) || text.equals("停用")) {
            return FixedAssetDisposition.STOP;
        }
        if (text.equals(FixedAssetDisposition.SCRAP) || text.equals("报废")) {
            return FixedAssetDisposition.SCRAP;
        }
        if (text.equals(FixedAssetDisposition.DISPOSE) || text.equals("处置")) {
            return FixedAssetDisposition.DISPOSE;
        }
        throw new BusinessException("请选择停用、报废或处置");
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        if (text.length() > 200) {
            throw new BusinessException("内容过长");
        }
        return text;
    }
}
