package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.app.ProcessingTargetResolver;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.scan.domain.ScanContextRequest;
import com.zhubao.erp.scan.domain.ScanContextResponse;
import com.zhubao.erp.scan.domain.ScanIntent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不拆母单同时读取空执行号和等于母单号的成品，不改原重量。
 */
class KeptMotherOperationContextTest extends ScanContextServiceTest {

    @Test
    void 母单号执行的120颗成品可定位且重量不变() {
        inbound("MD-20261029-0003", "120", "7.260", "不拆成品");
        var source = stocks.balances().stream()
                .filter(balance -> "MD-20261029-0003".equals(balance.dimension().motherOrderCode()))
                .findFirst()
                .orElseThrow();
        StockDimension from = source.dimension();
        StockDimension making = new StockDimension(
                from.ownerType(), from.ownerCustomerId(), from.customerId(),
                from.motherOrderCode(), "MD-20261029-0003", from.materialCode(),
                from.warehouseCode(), StockStatus.WIP);
        stocks.transfer(new StockTransferCommand(
                "work_order_split", "MD-20261029-0003", from, making,
                new BigDecimal("120"), new BigDecimal("7.260"), "整单在制"));
        StockDimension motherExecution = new StockDimension(
                making.ownerType(), making.ownerCustomerId(), making.customerId(),
                making.motherOrderCode(), making.workOrderCode(), making.materialCode(),
                making.warehouseCode(), StockStatus.FINISHED);
        stocks.transfer(new StockTransferCommand(
                "manual_finish", "MD-20261029-0003", making, motherExecution,
                new BigDecimal("120"), new BigDecimal("7.260"), "整单成品"));

        ScanContextResponse response = context.query(
                new ScanContextRequest("JG|mother|MD-20261029-0003", null, ScanIntent.SHIPPING),
                warehouse());

        assertThat(response.progress().stock())
                .anyMatch(stock -> "120".equals(stock.quantity().stripTrailingZeros().toPlainString())
                        || stock.quantity().compareTo(new BigDecimal("120")) == 0);
        assertThat(response.progress().stock())
                .filteredOn(stock -> stock.quantity().compareTo(new BigDecimal("120")) == 0)
                .allSatisfy(stock -> assertThat(stock.weight()).isEqualByComparingTo("7.260"));
        assertThat(ProcessingTargetResolver.identity("MD-20261029-0003", "MD-20261029-0003"))
                .isEqualTo(ProcessingTargetResolver.ExecutionIdentity.MOTHER);
        assertThat(ProcessingTargetResolver.identity("MD-20261029-0003", ""))
                .isEqualTo(ProcessingTargetResolver.ExecutionIdentity.EMPTY);
        assertThat(stocks.balances().stream()
                .filter(balance -> "MD-20261029-0003".equals(balance.dimension().workOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.FINISHED)
                .findFirst().orElseThrow().weight()).isEqualByComparingTo("7.260");
    }
}
