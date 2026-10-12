package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;

/** 复制业务命令时仅以服务端会话替换提交身份，保留原始业务字段。 */
public final class BusinessCommandActors {

    private BusinessCommandActors() {
    }

    public static ConfirmInboundCommand withActor(ConfirmInboundCommand command, String actor) {
        return new ConfirmInboundCommand(
                command.motherOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.inboundDate(),
                command.expressCompany(),
                command.courierNo(),
                command.senderName(),
                command.receiverName(),
                command.unpackSigner(),
                command.goodsName(),
                command.sourceRegisterType(),
                command.sourceRegisterLineNo(),
                command.sourceRegisterSequence(),
                command.warehouseCode(),
                command.quantity(),
                command.quantityUnit(),
                command.weight(),
                actor,
                command.evidenceCode(),
                command.remark(),
                command.processingRequirement(),
                command.dueDate(),
                command.customerName(),
                command.requestId()
        );
    }

    public static ReportProductionCommand withActor(ReportProductionCommand command, String actor) {
        return new ReportProductionCommand(
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                command.quantity(),
                command.quantityUnit(),
                command.weight(),
                command.workDate(),
                command.process(),
                command.processCategory(),
                command.productCategory(),
                command.machineNo(),
                command.workItemName(),
                command.startedAt(),
                command.finishedAt(),
                command.completionStatus(),
                command.handoffAuditStatus(),
                command.sourceRegisterType(),
                command.sourceRegisterLineNo(),
                command.sourceRegisterSequence(),
                actor,
                command.operatorSignatureName(),
                command.handoffCode(),
                command.remark()
        );
    }

    public static FreezeQualityStockCommand withActor(FreezeQualityStockCommand command, String actor) {
        return new FreezeQualityStockCommand(
                command.qualityCaseCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                command.quantity(),
                command.weight(),
                command.process(),
                actor,
                command.defect(),
                command.evidenceCode(),
                command.remark()
        );
    }

    public static DispatchReworkCommand withActor(DispatchReworkCommand command, String actor) {
        return new DispatchReworkCommand(
                command.reworkOrderCode(),
                command.qualityCaseCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                command.quantity(),
                command.weight(),
                command.process(),
                actor,
                command.reason(),
                command.evidenceCode(),
                command.remark()
        );
    }

    public static ReportReworkCommand withActor(ReportReworkCommand command, String actor) {
        return new ReportReworkCommand(
                command.reworkOrderCode(),
                command.qualityCaseCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                command.quantity(),
                command.weight(),
                command.process(),
                actor,
                command.evidenceCode(),
                command.remark()
        );
    }

    public static ReleaseQualityStockCommand withActor(ReleaseQualityStockCommand command, String actor) {
        return new ReleaseQualityStockCommand(
                command.qualityCaseCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                command.quantity(),
                command.weight(),
                command.process(),
                actor,
                command.releaseResult(),
                command.evidenceCode(),
                command.remark()
        );
    }

    public static ShipFinishedStockCommand withActor(ShipFinishedStockCommand command, String actor) {
        return new ShipFinishedStockCommand(
                command.shipmentCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                command.deliveryDate(),
                command.deliveryCategory(),
                command.quantity(),
                command.quantityUnit(),
                command.weight(),
                command.expressCompany(),
                command.logisticsNo(),
                command.paperExpressText(),
                command.sourceRegisterType(),
                command.sourceRegisterLineNo(),
                command.sourceRegisterSequence(),
                command.handler(),
                actor,
                command.evidenceCode(),
                command.remark()
        );
    }

    public static CustomerReturnCommand withActor(CustomerReturnCommand command, String actor) {
        return new CustomerReturnCommand(
                command.returnCode(),
                command.originalShipmentCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                command.quantity(),
                command.weight(),
                command.returnLogisticsNo(),
                command.reason(),
                actor,
                command.evidenceCode(),
                command.remark()
        );
    }

    public static AfterSalesReviewCommand withActor(AfterSalesReviewCommand command, String actor) {
        return new AfterSalesReviewCommand(
                command.reviewCode(),
                command.returnCode(),
                command.originalShipmentCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                command.quantity(),
                command.weight(),
                command.disposition(),
                actor,
                command.finding(),
                command.evidenceCode(),
                command.remark()
        );
    }

    public static ApproveLossCommand withActor(ApproveLossCommand command, String actor) {
        return new ApproveLossCommand(
                command.approvalCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId(),
                command.materialCode(),
                command.process(),
                actor,
                actor,
                command.inputWeight(),
                command.outputWeight(),
                command.reasonableTransferWeight(),
                command.standardLossRatePercent(),
                command.acceptedQuantity(),
                command.pieceRate(),
                command.lossDeductionUnitPrice(),
                command.evidenceCode(),
                command.remark()
        );
    }
}
