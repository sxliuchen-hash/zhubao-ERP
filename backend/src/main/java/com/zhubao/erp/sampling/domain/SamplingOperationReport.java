package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 打样生产报工。不推进普通加工，也不代替整袋工序。 */
public record SamplingOperationReport(
        String reportId,
        String samplingCode,
        String stepId,
        SamplingVersionKind versionKind,
        int versionNo,
        SamplingMakingChoice makingChoice,
        BigDecimal quantity,
        String unit,
        String attachmentCode,
        String workerName,
        String reportedOn
) {
    public SamplingOperationReport {
        if (reportId == null || reportId.isBlank()) {
            throw new BusinessException("报工编号无效");
        }
        reportId = reportId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(samplingCode);
        if (reference.kind() != BusinessKind.SAMPLING) {
            throw new BusinessException("报工必须挂在打样单上");
        }
        samplingCode = reference.code();
        if (stepId == null || stepId.isBlank()) {
            throw new BusinessException("请选择打样节点");
        }
        stepId = stepId.trim();
        if (versionKind == null || versionNo < 1) {
            throw new BusinessException("请选择已确认版本");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("请填写本次数量");
        }
        unit = unit == null ? "" : unit.trim();
        if (unit.isEmpty() || unit.length() > 16) {
            throw new BusinessException(unit.isEmpty() ? "请填写单位" : "单位超过可保存长度");
        }
        attachmentCode = attachmentCode == null ? "" : attachmentCode.trim();
        if (attachmentCode.isEmpty()) {
            throw new BusinessException("没有有效的现场照片");
        }
        workerName = workerName == null ? "" : workerName.trim();
        if (workerName.isEmpty() || workerName.length() > 64) {
            throw new BusinessException(workerName.isEmpty() ? "操作人不能为空" : "操作人超过可保存长度");
        }
        reportedOn = reportedOn == null ? "" : reportedOn.trim();
        if (!reportedOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("报工日期无效");
        }
    }
}
