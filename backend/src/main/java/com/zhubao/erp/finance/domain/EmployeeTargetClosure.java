package com.zhubao.erp.finance.domain;

import com.zhubao.erp.business.domain.BusinessKind;

import java.math.BigDecimal;

/**
 * 某目标上可计酬的员工实作。外协数量和损耗分开列出，不并进员工产量。
 * 损耗未知时保持空，不写成 0。
 */
public record EmployeeTargetClosure(
        BusinessKind kind,
        EmployeeClosureKind closure,
        BigDecimal employeeQuantity,
        BigDecimal outsourcingQuantity,
        BigDecimal lossQuantity,
        String quantityUnit,
        java.util.List<EmployeeProcessWork> work,
        boolean workEvidenceComplete,
        BigDecimal finishedQuantity
) {
    public EmployeeTargetClosure(BusinessKind kind, EmployeeClosureKind closure, BigDecimal employeeQuantity,
                                 BigDecimal outsourcingQuantity, BigDecimal lossQuantity, String quantityUnit) {
        this(kind, closure, employeeQuantity, outsourcingQuantity, lossQuantity, quantityUnit, java.util.List.of(), false, null);
    }

    public EmployeeTargetClosure(BusinessKind kind, EmployeeClosureKind closure, BigDecimal employeeQuantity,
                                 BigDecimal outsourcingQuantity, BigDecimal lossQuantity, String quantityUnit,
                                 java.util.List<EmployeeProcessWork> work, boolean workEvidenceComplete) {
        this(kind, closure, employeeQuantity, outsourcingQuantity, lossQuantity, quantityUnit, work, workEvidenceComplete, null);
    }

    public EmployeeTargetClosure { work = java.util.List.copyOf(work); }
}
