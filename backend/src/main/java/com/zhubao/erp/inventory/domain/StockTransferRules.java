package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.Objects;
import java.math.BigDecimal;

/** 内存和 JDBC 共用的库存转移规则。 */
public final class StockTransferRules {

    private StockTransferRules() {
    }

    public static void verify(StockLedgerEntry entry) {
        Objects.requireNonNull(entry, "库存台账不能为空");
        requireText(entry.code(), "库存台账号不能为空");
        Objects.requireNonNull(entry.bizTime(), "库存业务时间不能为空");
        requireText(entry.documentType(), "库存业务类型不能为空");
        requireText(entry.documentCode(), "库存业务单号不能为空");
        Objects.requireNonNull(entry.quantity(), "库存数量不能为空");
        Objects.requireNonNull(entry.weight(), "库存重量不能为空");
        if (entry.quantity().signum() < 0 || entry.weight().signum() < 0) {
            throw new IllegalArgumentException("库存转移数量和重量不能为负数");
        }
        if (entry.quantity().signum() == 0 && entry.weight().signum() == 0) {
            throw new IllegalArgumentException("库存转移数量和重量不能同时为零");
        }
        verifyPrecision(entry.quantity());
        verifyPrecision(entry.weight());
        verifyDimension(entry.to());
        if (entry.from() == null) {
            return;
        }
        verifyDimension(entry.from());
        if (isPrepIssue(entry) || isPrepReturn(entry)) {
            verifyPrepBridge(entry);
            return;
        }
        StockDimension from = entry.from();
        StockDimension to = entry.to();
        if (from.ownerType() != to.ownerType()
                || !Objects.equals(from.ownerCustomerId(), to.ownerCustomerId())
                || !Objects.equals(from.customerId(), to.customerId())
                || !Objects.equals(from.motherOrderCode(), to.motherOrderCode())
                || !Objects.equals(from.materialCode(), to.materialCode())) {
            throw new IllegalArgumentException("库存转移不能改变权属、客户、母单或物料维度");
        }
        if (!Objects.equals(from.workOrderCode(), to.workOrderCode()) && !isInitialWorkOrderSplit(entry)) {
            throw new IllegalArgumentException("仅原料首次拆单转入在制时允许设置工单维度");
        }
    }

    public static void requireSufficient(StockBalance balance, StockLedgerEntry entry) {
        if (balance.quantity().compareTo(entry.quantity()) < 0) {
            throw new BusinessException("来源库存数量不足");
        }
        if (balance.weight().compareTo(entry.weight()) < 0) {
            throw new BusinessException("来源库存重量不足");
        }
    }

    private static boolean isPrepIssue(StockLedgerEntry entry) {
        return "prep_issue".equals(entry.documentType());
    }

    private static boolean isPrepReturn(StockLedgerEntry entry) {
        return "prep_return".equals(entry.documentType());
    }

    /** 只允许同一客户、同一物料从备料批次转入真实加工母单，或按原批次退回。 */
    private static void verifyPrepBridge(StockLedgerEntry entry) {
        StockDimension from = entry.from();
        StockDimension to = entry.to();
        if (from.ownerType() != OwnerType.CUSTOMER || to.ownerType() != OwnerType.CUSTOMER
                || !Objects.equals(from.customerId(), to.customerId())
                || !Objects.equals(from.materialCode(), to.materialCode())) {
            throw new IllegalArgumentException("备料领用不能改变客户或物料");
        }
        boolean issue = isPrepIssue(entry);
        StockDimension batchSide = issue ? from : to;
        StockDimension orderSide = issue ? to : from;
        if (batchSide.batchCode().isBlank() || !batchSide.motherOrderCode().isBlank()) {
            throw new IllegalArgumentException("备料批次必须独立于加工母单");
        }
        if (!orderSide.batchCode().isBlank() || orderSide.motherOrderCode().isBlank()) {
            throw new IllegalArgumentException("备料只能领用到真实加工母单");
        }
        if (orderSide.stockStatus() != StockStatus.RAW_WAITING || batchSide.stockStatus() != StockStatus.RAW_WAITING) {
            throw new IllegalArgumentException("备料领用只转入待加工");
        }
    }

    private static boolean isInitialWorkOrderSplit(StockLedgerEntry entry) {
        StockDimension from = entry.from();
        StockDimension to = entry.to();
        return "work_order_split".equals(entry.documentType())
                && from.stockStatus() == StockStatus.RAW_WAITING
                && (from.workOrderCode() == null || from.workOrderCode().isBlank())
                && to.stockStatus() == StockStatus.WIP
                && to.workOrderCode() != null && !to.workOrderCode().isBlank();
    }

    private static void verifyDimension(StockDimension dimension) {
        Objects.requireNonNull(dimension, "库存维度不能为空");
        Objects.requireNonNull(dimension.ownerType(), "库存权属不能为空");
        Objects.requireNonNull(dimension.stockStatus(), "库存状态不能为空");
        if (dimension.batchCode() == null || dimension.batchCode().isBlank()) {
            requireText(dimension.motherOrderCode(), "库存母单不能为空");
        } else if (dimension.motherOrderCode() != null && !dimension.motherOrderCode().isBlank()) {
            throw new IllegalArgumentException("批次库存不能同时绑定加工母单");
        }
        requireText(dimension.materialCode(), "库存物料不能为空");
        requireText(dimension.warehouseCode(), "库存仓库不能为空");
    }

    private static void verifyPrecision(BigDecimal value) {
        // 与余额和台账的 DECIMAL(18,3) 一致，禁止数据库单独舍入扣减和增量。
        if (value.stripTrailingZeros().scale() > 3 || value.compareTo(new BigDecimal("999999999999999.999")) > 0) {
            throw new IllegalArgumentException("库存数量和重量最多保留三位小数且不得超过数据库范围");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
