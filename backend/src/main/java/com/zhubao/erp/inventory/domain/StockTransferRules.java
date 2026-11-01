package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
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
        if (isSamplingCustomer(entry)) {
            verifySamplingCustomer(entry);
            return;
        }
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

    /**
     * 加工损耗或退回只扣在制数量。必须有已称重量。
     * 不关联原收货流水，也不能写成退还客户或收货冲正。
     */
    public static void verifyProcessConsumption(StockLedgerEntry entry) {
        Objects.requireNonNull(entry, "库存台账不能为空");
        if (!"process_loss".equals(entry.documentType()) && !"process_return".equals(entry.documentType())) {
            throw new IllegalArgumentException("加工损耗或退回的业务类型不正确");
        }
        requireText(entry.code(), "库存台账号不能为空");
        requireText(entry.documentCode(), "库存业务单号不能为空");
        if (entry.to() != null || entry.from() == null) {
            throw new IllegalArgumentException("加工损耗或退回只扣减在制余额");
        }
        if (entry.sourceLedgerCode() != null && !entry.sourceLedgerCode().isBlank()) {
            throw new IllegalArgumentException("加工损耗或退回不能写成收货冲正");
        }
        if (entry.quantity() == null || entry.quantity().signum() <= 0 || entry.weight() == null || entry.weight().signum() <= 0) {
            throw new IllegalArgumentException("加工损耗或退回必须有正数数量和已称重量，未知重量不能按 0 入账");
        }
        verifyPrecision(entry.quantity());
        verifyPrecision(entry.weight());
        verifyDimension(entry.from());
    }

    /** 实物离厂或冲减只扣来源余额，不转入另一种库存状态。 */
    public static void verifyDeparture(StockLedgerEntry entry) {
        Objects.requireNonNull(entry, "库存台账不能为空");
        requireText(entry.code(), "库存台账号不能为空");
        Objects.requireNonNull(entry.bizTime(), "库存业务时间不能为空");
        if (!"prep_customer_return".equals(entry.documentType()) && !"stock_correction".equals(entry.documentType()) && !"stock_reweigh".equals(entry.documentType())) {
            throw new IllegalArgumentException("离厂或冲减的业务类型不正确");
        }
        requireText(entry.documentCode(), "库存业务单号不能为空");
        if (entry.to() != null || entry.from() == null) {
            throw new IllegalArgumentException("离厂或冲减只能扣减来源余额");
        }
        if (!"stock_reweigh".equals(entry.documentType()) && (entry.sourceLedgerCode() == null || entry.sourceLedgerCode().isBlank())) {
            throw new IllegalArgumentException("离厂或冲减必须关联原收货");
        }
        Objects.requireNonNull(entry.quantity(), "库存数量不能为空");
        Objects.requireNonNull(entry.weight(), "库存重量不能为空");
        if (entry.quantity().signum() < 0 || entry.weight().signum() < 0) {
            throw new IllegalArgumentException("库存转移数量和重量不能为负数");
        }
        if (entry.quantity().signum() == 0 && entry.weight().signum() == 0) {
            throw new IllegalArgumentException("库存转移数量和重量不能同时为零");
        }
        if ("stock_reweigh".equals(entry.documentType()) && (entry.quantity().signum()!=0 || entry.weight().signum()<=0))
            throw new IllegalArgumentException("复称只能调整重量，不能改数量");
        verifyPrecision(entry.quantity());
        verifyPrecision(entry.weight());
        verifyDimension(entry.from());
    }

    /** 未称重的开工、损耗和退出只流转实际数量，重量继续待核。 */
    public static void verifyUnweighedProcess(StockLedgerEntry entry) {
        if (entry.from()==null || entry.weight()!=null || entry.quantity()==null || entry.quantity().signum()<=0)
            throw new IllegalArgumentException("未称流转须有来源及正数数量，重量必须保持未知");
        verifyPrecision(entry.quantity()); verifyDimension(entry.from());
        String type=entry.documentType();
        if (entry.to()==null) {
            if (!java.util.Set.of("outsourcing_loss","process_loss","process_return").contains(type)) throw new IllegalArgumentException("未称退出类型不正确");
            if (!"outsourcing_loss".equals(type) && entry.from().stockStatus()!=StockStatus.WIP) throw new IllegalArgumentException("加工退出必须扣减在制");
        } else {
            boolean start="production_start".equals(type) && entry.from().stockStatus()==StockStatus.RAW_WAITING && entry.to().stockStatus()==StockStatus.WIP;
            boolean scrap="process_scrap".equals(type) && entry.from().stockStatus()==StockStatus.WIP && entry.to().stockStatus()==StockStatus.SCRAP;
            if (!start && !scrap) throw new IllegalArgumentException("未称流转类型或状态不正确");
            verify(new StockLedgerEntry(entry.code(),entry.bizTime(),type,entry.documentCode(),entry.from(),entry.to(),entry.quantity(),BigDecimal.ZERO,entry.remark()));
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

    private static boolean isSamplingCustomer(StockLedgerEntry entry) {
        return "sampling_customer_issue".equals(entry.documentType())
                || "sampling_customer_return".equals(entry.documentType());
    }

    /**
     * 客户来料领到打样或退回来源。权属、客户、批次和仓库不变，母单号保持空白。
     */
    private static void verifySamplingCustomer(StockLedgerEntry entry) {
        boolean issue = "sampling_customer_issue".equals(entry.documentType());
        StockDimension from = entry.from();
        StockDimension to = entry.to();
        if (from.ownerType() != OwnerType.CUSTOMER || to.ownerType() != OwnerType.CUSTOMER
                || !Objects.equals(from.customerId(), to.customerId())
                || !Objects.equals(from.ownerCustomerId(), to.ownerCustomerId())
                || !from.materialCode().equals(to.materialCode())
                || !from.batchCode().equals(to.batchCode())
                || !from.warehouseCode().equals(to.warehouseCode())) {
            throw new IllegalArgumentException("打样领料不能改变客户、物料、批次或仓库");
        }
        if (!from.motherOrderCode().isBlank() || !to.motherOrderCode().isBlank()) {
            throw new IllegalArgumentException("打样领料不能占用加工母单");
        }
        StockDimension holding = issue ? to : from;
        StockDimension source = issue ? from : to;
        if (source.stockStatus() != StockStatus.RAW_WAITING || holding.stockStatus() != StockStatus.WIP) {
            throw new IllegalArgumentException("打样领料只在客户待加工和打样在制之间移动");
        }
        if (source.workOrderCode() != null && !source.workOrderCode().isBlank()) {
            throw new IllegalArgumentException("客户来料来源不能已经挂在其他工单");
        }
        if (holding.workOrderCode() == null || holding.workOrderCode().isBlank()
                || BusinessTargetReference.fromCode(holding.workOrderCode()).kind() != BusinessKind.SAMPLING) {
            throw new IllegalArgumentException("打样领料只能挂到打样单");
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
        return ("work_order_split".equals(entry.documentType()) || "production_start".equals(entry.documentType()))
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
