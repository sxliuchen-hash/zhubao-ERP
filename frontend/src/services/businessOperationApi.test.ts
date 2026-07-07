import { describe, expect, it } from 'vitest';
import type { InboundOrder, InventoryBalance, LossApproval, QualityCase, WorkOrder } from '../data/m1PrototypeData';
import {
  buildAfterSalesReviewRequest,
  buildLossApprovalRequest,
  buildConfirmInboundRequest,
  buildCustomerReturnRequest,
  buildOutboundShipmentRequest,
  buildQualityFreezeRequest,
  buildQualityReleaseRequest,
  buildProductionReportRequest,
  buildReworkDispatchRequest,
  buildReworkReportRequest,
} from './businessOperationApi';

describe('businessOperationApi', () => {
  it('builds an inbound confirmation command with customer-owned raw stock dimension', () => {
    const order: InboundOrder = {
      code: 'MD-20260706-001',
      customer: '恒信珠宝',
      material: '18K 金戒托',
      inboundDate: '07-06',
      expressCompany: '顺丰',
      courierNo: 'SF1048329001',
      senderName: '张先生',
      receiverName: '何琳',
      unpackSigner: '何琳',
      goodsName: '戒托来料',
      qty: 320,
      weight: '486.25 g',
      status: '待拆包',
      video: '待开始',
      owner: '客户所有',
      remark: '拆箱签字待复核',
    };

    expect(buildConfirmInboundRequest(order)).toMatchObject({
      motherOrderCode: 'MD-20260706-001',
      customerId: 1001,
      ownerCustomerId: 1001,
      materialCode: 'MAT-18K-RING',
      inboundDate: '2026-07-06',
      expressCompany: '顺丰',
      senderName: '张先生',
      receiverName: '何琳',
      unpackSigner: '何琳',
      goodsName: '戒托来料',
      warehouseCode: 'RAW-CUSTOMER',
      quantity: 320,
      weight: '486.25',
      operator: '库管员 何琳',
      evidenceCode: 'SF1048329001',
    });
  });

  it('builds a production report command that keeps the same work order dimension', () => {
    const order: WorkOrder = {
      code: 'ZD-20260706-006',
      customer: '雅辰饰品',
      material: '银链半成品',
      process: '焊接',
      processCategory: '人工',
      productCategory: '银链',
      machineNo: 'WELD-02',
      workItemName: '焊接',
      startedAt: '15:00',
      finishedAt: '进行中',
      completionStatus: '称重待补',
      handoffAuditStatus: '待交单审核',
      assignee: '王强',
      plannedQty: 300,
      finishedQty: 210,
      status: '称重待补',
      progress: 70,
    };

    expect(buildProductionReportRequest(order, 'HO-20260706-018')).toMatchObject({
      motherOrderCode: 'MD-20260706-002',
      workOrderCode: 'ZD-20260706-006',
      customerId: 1002,
      ownerCustomerId: 1002,
      materialCode: 'MAT-SILVER-CHAIN',
      fromWarehouseCode: 'WIP-LINE-01',
      toWarehouseCode: 'SEMI-FINISHED',
      quantity: 210,
      weight: '478.240',
      workDate: '2026-07-06',
      process: '焊接',
      processCategory: '人工',
      productCategory: '银链',
      machineNo: 'WELD-02',
      workItemName: '焊接',
      handoffAuditStatus: '待交单审核',
      operator: '王强',
      handoffCode: 'HO-20260706-018',
    });
  });

  it('keeps engraving operator signature separate from generic production fields', () => {
    const order: WorkOrder = {
      code: 'ZD-20260706-009',
      customer: '恒信珠宝',
      material: '18K 金戒托',
      process: '雕刻',
      processCategory: '设备',
      productCategory: '戒托',
      machineNo: 'ENG-03',
      workItemName: '雕刻',
      startedAt: '09:00',
      finishedAt: '10:15',
      completionStatus: '已完成 168 件',
      handoffAuditStatus: '操机签字待补',
      assignee: '周凯',
      plannedQty: 200,
      finishedQty: 168,
      status: '待交单审核',
      progress: 84,
    };

    expect(buildProductionReportRequest(order)).toMatchObject({
      workOrderCode: 'ZD-20260706-009',
      workDate: '2026-07-06',
      process: '雕刻',
      processCategory: '设备',
      productCategory: '戒托',
      machineNo: 'ENG-03',
      operator: '周凯',
      operatorSignatureName: '周凯',
    });
  });

  it('builds a quality freeze command that only changes stock status and warehouse', () => {
    const qualityCase: QualityCase = {
      code: 'QC-20260706-022',
      workOrder: 'ZD-20260706-006',
      process: '焊接',
      inspector: '周娜',
      defect: '焊点偏移 6 件',
      action: '复检',
      status: '待确认称重',
    };

    expect(buildQualityFreezeRequest(qualityCase)).toMatchObject({
      qualityCaseCode: 'QC-20260706-022',
      motherOrderCode: 'MD-20260706-002',
      workOrderCode: 'ZD-20260706-006',
      customerId: 1002,
      ownerCustomerId: 1002,
      materialCode: 'MAT-SILVER-CHAIN',
      fromWarehouseCode: 'SEMI-FINISHED',
      toWarehouseCode: 'FROZEN-QC',
      quantity: 6,
      weight: '13.660',
      process: '焊接',
      inspector: '周娜',
      defect: '焊点偏移 6 件',
      evidenceCode: 'PHOTO-QC-20260706-022',
    });
  });

  it('builds a rework dispatch command from frozen stock to rework stock', () => {
    const qualityCase: QualityCase = {
      code: 'QC-20260706-022',
      workOrder: 'ZD-20260706-006',
      process: '焊接',
      inspector: '周娜',
      defect: '焊点偏移 6 件',
      action: '复检',
      status: '待确认称重',
    };

    expect(buildReworkDispatchRequest(qualityCase)).toMatchObject({
      reworkOrderCode: 'FG-20260706-022',
      qualityCaseCode: 'QC-20260706-022',
      motherOrderCode: 'MD-20260706-002',
      workOrderCode: 'ZD-20260706-006',
      customerId: 1002,
      ownerCustomerId: 1002,
      materialCode: 'MAT-SILVER-CHAIN',
      fromWarehouseCode: 'FROZEN-QC',
      toWarehouseCode: 'REWORK-LINE-01',
      quantity: 6,
      weight: '13.660',
      process: '返工焊接',
      operator: '车间组长 马杰',
      reason: '焊点偏移 6 件',
      evidenceCode: 'PHOTO-QC-20260706-022',
    });
  });

  it('builds a rework report command from rework stock back to semi-finished stock', () => {
    const qualityCase: QualityCase = {
      code: 'QC-20260706-022',
      workOrder: 'ZD-20260706-006',
      process: '焊接',
      inspector: '周娜',
      defect: '焊点偏移 6 件',
      action: '复检',
      status: '待确认称重',
    };

    expect(buildReworkReportRequest(qualityCase)).toMatchObject({
      reworkOrderCode: 'FG-20260706-022',
      qualityCaseCode: 'QC-20260706-022',
      motherOrderCode: 'MD-20260706-002',
      workOrderCode: 'ZD-20260706-006',
      customerId: 1002,
      ownerCustomerId: 1002,
      materialCode: 'MAT-SILVER-CHAIN',
      fromWarehouseCode: 'REWORK-LINE-01',
      toWarehouseCode: 'SEMI-FINISHED',
      quantity: 6,
      weight: '13.660',
      process: '返工焊接',
      operator: '王强',
      evidenceCode: 'PHOTO-REWORK-QC-20260706-022',
    });
  });

  it('builds a quality release command from semi-finished stock to finished stock', () => {
    const qualityCase: QualityCase = {
      code: 'QC-20260706-023',
      workOrder: 'FG-20260706-001',
      process: '返工抛光',
      inspector: '刘洋',
      defect: '返工通过',
      action: '放行',
      status: '已完成',
    };

    expect(buildQualityReleaseRequest(qualityCase)).toMatchObject({
      qualityCaseCode: 'QC-20260706-023',
      motherOrderCode: 'MD-20260706-004',
      workOrderCode: 'FG-20260706-001',
      customerId: 1004,
      ownerCustomerId: 1004,
      materialCode: 'MAT-PT950-PENDANT',
      fromWarehouseCode: 'SEMI-FINISHED',
      toWarehouseCode: 'FINISHED-CUSTOMER',
      quantity: 1,
      weight: '1.250',
      process: '复检返工抛光',
      inspector: '刘洋',
      releaseResult: '复检合格，允许成品入库',
      evidenceCode: 'PHOTO-RELEASE-QC-20260706-023',
    });
  });

  it('builds an outbound shipment command from finished stock to shipped stock', () => {
    const balance: InventoryBalance = {
      owner: '雅辰饰品',
      batch: 'MD-20260706-002',
      material: '银链半成品',
      location: '成品库 F-02',
      status: '成品',
      qty: 620,
      weight: '1,451.20 g',
      frozen: '200 件',
    };

    expect(buildOutboundShipmentRequest(balance)).toMatchObject({
      shipmentCode: 'OUT-20260706002',
      motherOrderCode: 'MD-20260706-002',
      workOrderCode: 'ZD-20260706-006',
      customerId: 1002,
      ownerCustomerId: 1002,
      materialCode: 'MAT-SILVER-CHAIN',
      fromWarehouseCode: 'FINISHED-CUSTOMER',
      toWarehouseCode: 'SHIPPED-CUSTOMER',
      deliveryCategory: '成品',
      quantity: 620,
      weight: '1451.20',
      expressCompany: '顺丰',
      logisticsNo: 'SF-OUT-20260706002',
      handler: '何琳',
      operator: '库管员 何琳',
      evidenceCode: 'ATT-OUT-20260706002',
    });
  });

  it('builds a customer return command from shipped stock back to return check stock', () => {
    const balance: InventoryBalance = {
      owner: '雅辰饰品',
      batch: 'MD-20260706-002',
      material: '银链半成品',
      location: '成品库 F-02',
      status: '成品',
      qty: 620,
      weight: '1,451.20 g',
      frozen: '200 件',
    };

    expect(buildCustomerReturnRequest(balance)).toMatchObject({
      returnCode: 'RET-20260706002',
      originalShipmentCode: 'OUT-20260706002',
      motherOrderCode: 'MD-20260706-002',
      workOrderCode: 'ZD-20260706-006',
      customerId: 1002,
      ownerCustomerId: 1002,
      materialCode: 'MAT-SILVER-CHAIN',
      fromWarehouseCode: 'SHIPPED-CUSTOMER',
      toWarehouseCode: 'RETURN-CHECK',
      quantity: 620,
      weight: '1451.20',
      returnLogisticsNo: 'SF-RET-20260706002',
      reason: '客户退回复核',
      operator: '库管员 何琳',
      evidenceCode: 'ATT-RET-20260706002',
    });
  });

  it('builds an after-sales review command from return check stock to rework stock', () => {
    const balance: InventoryBalance = {
      owner: '雅辰饰品',
      batch: 'MD-20260706-002',
      material: '银链半成品',
      location: '退货复核区 R-CHECK',
      status: '退货复核',
      qty: 2,
      weight: '4.55 g',
      frozen: '0',
    };

    expect(buildAfterSalesReviewRequest(balance)).toMatchObject({
      reviewCode: 'ASR-20260706002',
      returnCode: 'RET-20260706002',
      originalShipmentCode: 'OUT-20260706002',
      motherOrderCode: 'MD-20260706-002',
      workOrderCode: 'ZD-20260706-006',
      customerId: 1002,
      ownerCustomerId: 1002,
      materialCode: 'MAT-SILVER-CHAIN',
      fromWarehouseCode: 'RETURN-CHECK',
      toWarehouseCode: 'REWORK-LINE-01',
      quantity: 2,
      weight: '4.55',
      disposition: 'REWORK',
      inspector: '周娜',
      evidenceCode: 'PHOTO-ASR-20260706002',
    });
  });

  it('builds a loss approval command with wage trial snapshot inputs', () => {
    const approval: LossApproval = {
      code: 'LOSS-20260706-009',
      workOrder: 'ZD-20260706-001',
      process: '抛光',
      standard: '2.0%',
      actual: '3.8%',
      evidence: '投入 121.15g / 产出 116.55g / 缺陷照片 5 张',
      action: '待组长审批',
      tone: 'red',
    };

    expect(buildLossApprovalRequest(approval)).toMatchObject({
      approvalCode: 'LOSS-20260706-009',
      motherOrderCode: 'MD-20260706-001',
      workOrderCode: 'ZD-20260706-001',
      customerId: 1001,
      ownerCustomerId: 1001,
      materialCode: 'MAT-18K-RING',
      process: '抛光',
      operator: '李华',
      approver: '车间组长 马杰',
      inputWeight: '121.15',
      outputWeight: '116.55',
      reasonableTransferWeight: '0.000',
      standardLossRatePercent: '2.0',
      acceptedQuantity: 56,
      pieceRate: '0.80',
      lossDeductionUnitPrice: '6.00',
      evidenceCode: 'HO-20260706-021',
    });
  });
});
