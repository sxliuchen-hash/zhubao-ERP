package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 外协任务的位置查询。在外份额不是厂内现货。查询本身不写发外或收回。
 */
public record OutsourcingPositionView(
        String taskId,
        String targetCode,
        String nodeId,
        String partyId,
        String partyName,
        String ownerType,
        String ownerRef,
        BigDecimal inHouseQuantity,
        BigDecimal outQuantity,
        BigDecimal pendingQuantity,
        boolean outsourcingCountsAsInHouseStock,
        List<DispatchLine> dispatches,
        List<ReturnLine> returns,
        List<SourceLine> sources
) {
    public record DispatchLine(String dispatchId, int version, BigDecimal quantity, BigDecimal stillOut) {
    }

    public record ReturnLine(
            String dispatchId,
            BigDecimal receivedQty,
            BigDecimal unprocessedQty,
            BigDecimal lossQty,
            BigDecimal stillOut
    ) {
    }

    public record SourceLine(
            String bagId,
            BigDecimal quantity,
            int version,
            String holder,
            boolean inHouseStock,
            String sourceKey
    ) {
    }
}
