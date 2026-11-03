package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 金工节点报工。照片属于本金工单，不写库存或工资。 */
public record GoldworkOperationReport(
        String reportId,
        String goldworkCode,
        String stepId,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        String weightUnit,
        String purity,
        String attachmentCode,
        String workerName,
        String reportedOn,
        String precisionState
) {
    public GoldworkOperationReport {
        if (reportId == null || reportId.isBlank()) {
            throw new BusinessException("报工编号无效");
        }
        reportId = reportId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(goldworkCode);
        if (reference.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("报工必须挂在金工单上");
        }
        goldworkCode = reference.code();
        stepId = required(stepId, "请选择金工工序");
        pieceCount = GoldworkMeasurement.declared(pieceCount, "未知件数不能写成 0");
        netWeight = GoldworkMeasurement.declared(netWeight, "未知重量不能写成 0");
        weightUnit = weightUnit == null ? "" : weightUnit.trim();
        if (netWeight != null && weightUnit.isEmpty()) {
            throw new BusinessException("请填写重量单位");
        }
        purity = purity == null ? "" : purity.trim();
        attachmentCode = required(attachmentCode, "没有有效的现场照片");
        workerName = required(workerName, "操作人不能为空");
        reportedOn = reportedOn == null ? "" : reportedOn.trim();
        if (!reportedOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("报工日期无效");
        }
        if (!"UNDECIDED".equals(precisionState)) {
            throw new BusinessException("本版金工重量只作记录，不能按库存精度入账");
        }
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }
}
