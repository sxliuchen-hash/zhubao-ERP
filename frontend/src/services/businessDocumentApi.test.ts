import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchMotherOrder, fetchOutboundOrder } from './businessDocumentApi';

describe('businessDocumentApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches structured mother order and outbound order snapshots', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            code: 'MD-REG-001',
            customerId: 1001,
            ownerCustomerId: 1001,
            materialCode: 'MAT-18K-RING',
            inboundDate: '2026-07-08',
            expressCompany: '顺丰',
            courierNo: 'SF-IN-001',
            senderName: '张先生',
            receiverName: '何琳',
            unpackSigner: '何琳',
            goodsName: '戒托来料',
            ownerType: 'CUSTOMER',
            customerOwned: true,
            warehouseCode: 'RAW-CUSTOMER',
            quantity: 12,
            quantityUnit: '件',
            weight: 18.5,
            stockStatus: 'RAW_WAITING',
            ledgerCode: 'STK-IN-001',
            sourceRegisterType: '签收入库登记表',
            sourceRegisterLineNo: 4,
            sourceRegisterSequence: '1',
            operator: '库管员 何琳',
            evidenceCode: 'ATT-IN-001',
            remark: '结构化母单测试',
            status: 'INBOUND_CONFIRMED',
            createdAt: '2026-07-08T10:00:00',
          },
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            code: 'OUT-REG-001',
            motherOrderCode: 'MD-REG-001',
            workOrderCode: 'WO-REG-001',
            customerId: 1001,
            ownerCustomerId: 1001,
            materialCode: 'MAT-18K-RING',
            fromWarehouseCode: 'FINISHED-CUSTOMER',
            toWarehouseCode: 'SHIPPED-CUSTOMER',
            deliveryDate: '2026-07-08',
            deliveryCategory: '成品',
            quantity: 6,
            quantityUnit: '件',
            weight: 9.2,
            expressCompany: '顺丰',
            logisticsNo: 'SF-OUT-001',
            paperExpressText: '顺丰 SF-OUT-001',
            handler: '何琳',
            sourceRegisterType: '交货情况登记表',
            sourceRegisterLineNo: 5,
            sourceRegisterSequence: '2',
            operator: '库管员 何琳',
            evidenceCode: 'ATT-OUT-001',
            remark: '结构化出库单测试',
            status: 'SHIPPED',
            ledgerCode: 'STK-OUT-001',
            createdAt: '2026-07-08T10:30:00',
          },
        }),
      } as Response);

    const motherOrder = await fetchMotherOrder('MD-REG-001');
    const outboundOrder = await fetchOutboundOrder('OUT-REG-001');

    expect(motherOrder.ledgerCode).toBe('STK-IN-001');
    expect(motherOrder.stockStatus).toBe('RAW_WAITING');
    expect(outboundOrder.logisticsNo).toBe('SF-OUT-001');
    expect(outboundOrder.status).toBe('SHIPPED');
    expect(fetchMock.mock.calls[0][0]).toContain('/api/business/documents/mother-orders/MD-REG-001');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/business/documents/outbound-orders/OUT-REG-001');
  });
});
