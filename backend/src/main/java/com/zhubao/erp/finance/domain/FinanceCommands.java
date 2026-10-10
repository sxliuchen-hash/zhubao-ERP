package com.zhubao.erp.finance.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务经营核算命令。金额和月份在服务里再做业务校验。
 */
public final class FinanceCommands {

    private FinanceCommands() {
    }

    public record PriceLineCommand(
            String processName,
            @NotBlank String unit,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal unitPrice
    ) {
    }

    public record SavePriceAgreementCommand(
            @NotNull Long customerId,
            @NotBlank String customerName,
            @NotBlank String mode,
            @NotBlank String effectiveFrom,
            String effectiveTo,
            @NotEmpty @Valid List<PriceLineCommand> lines
    ) {
    }

    public record BillingRunCommand(
            @NotBlank String yearMonth,
            Long customerId
    ) {
    }

    public record ReverseReceivableCommand(
            @NotBlank String receivableCode,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank String reason
    ) {
    }

    public record ReceiptAllocationCommand(
            @NotBlank String receivableCode,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount
    ) {
    }

    public record RecordReceiptCommand(
            @NotNull Long customerId,
            @NotBlank String customerName,
            @NotBlank String receivedOn,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Valid List<ReceiptAllocationCommand> allocations
    ) {
    }

    public record SaveWorkerRateCommand(
            @NotBlank String processName,
            @NotBlank String unit,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal unitPrice,
            @NotBlank String effectiveFrom,
            String effectiveTo
    ) {
    }

    public record PayrollRunCommand(
            @NotBlank String yearMonth
    ) {
    }

    public record SaveConsumableItemCommand(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String unitName
    ) {
    }

    public record PurchaseConsumableCommand(
            @NotBlank String itemCode,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank String supplier,
            @NotBlank String yearMonth
    ) {
    }

    public record IssueConsumableCommand(
            @NotBlank String itemCode,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
            Long customerId,
            @NotBlank String yearMonth
    ) {
    }

    public record SaveFixedAssetCommand(
            @NotBlank String name,
            @NotBlank String category,
            @NotNull @DecimalMin(value = "0.01") BigDecimal originalValue,
            @NotNull @DecimalMin(value = "0.00") BigDecimal residualValue,
            @NotBlank String startMonth,
            @NotNull @Min(1) Integer usefulMonths
    ) {
    }

    public record DepreciationRunCommand(
            @NotBlank String yearMonth
    ) {
    }

    public record OutsourceBillCommand(
            @NotBlank String supplier,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank String yearMonth,
            Long customerId,
            String motherOrderCode
    ) {
    }

    public record PayablePaymentCommand(
            @NotBlank String payableCode,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank String paidOn
    ) {
    }
}
