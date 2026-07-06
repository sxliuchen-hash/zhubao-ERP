import { describe, expect, it } from 'vitest';
import type { InboundOrder } from '../data/m1PrototypeData';
import { buildConfirmInboundRequest } from './businessOperationApi';

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
});
