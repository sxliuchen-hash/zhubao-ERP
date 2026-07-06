import { describe, expect, it } from 'vitest';
import type { InboundOrder, WorkOrder } from '../data/m1PrototypeData';
import {
  buildConfirmInboundRequest,
  buildProductionReportRequest,
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
});
