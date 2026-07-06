import { describe, expect, it } from 'vitest';
import type { InboundOrder, QualityCase, WorkOrder } from '../data/m1PrototypeData';
import {
  buildConfirmInboundRequest,
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
      courierNo: 'SF1048329001',
      qty: 320,
      weight: '486.25 g',
      status: '待拆包',
      video: '待开始',
      owner: '客户所有',
    };

    expect(buildConfirmInboundRequest(order)).toMatchObject({
      motherOrderCode: 'MD-20260706-001',
      customerId: 1001,
      ownerCustomerId: 1001,
      materialCode: 'MAT-18K-RING',
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
      process: '焊接',
      operator: '王强',
      handoffCode: 'HO-20260706-018',
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
});
