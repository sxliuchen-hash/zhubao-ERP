import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  approveLoss, buildAfterSalesReviewRequest, buildConfirmInboundRequest, buildCustomerReturnRequest,
  buildLossApprovalRequest, buildOutboundShipmentRequest, shipFinishedStock,
} from './businessOperationApi';
import { fetchAuthSession } from './authApi';
import { apiPost } from './apiClient';
import type { AuthSession } from './authApi';
import type { InboundOrder, InventoryBalance, LossApproval } from '../data/m1PrototypeData';

vi.mock('./authApi', () => ({ fetchAuthSession: vi.fn() }));
vi.mock('./apiClient', () => ({ apiPost: vi.fn() }));

const operationContext = {
  customerId: 57, ownerCustomerId: 57, motherOrderCode: 'MD-REAL', workOrderCode: 'ZD-REAL',
  materialCode: 'MAT-REAL', warehouseCode: 'REAL-WAREHOUSE', ownerType: 'CUSTOMER',
  stockStatus: 'FINISHED', quantity: 12, weight: 25.75,
};
const inventory: InventoryBalance = { owner: '实际客户', batch: 'ZD-REAL', material: '实际物料',
  location: '实际库位', status: '成品', qty: 12, weight: '25.75 g', frozen: '0', operationContext };
const loss: LossApproval = { code: 'LOSS-REAL', workOrder: 'ZD-REAL', process: '抛光', standard: '2%',
  actual: '3%', evidence: '缺少称重', action: '待审批', tone: 'red', operationContext };

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchAuthSession).mockResolvedValue({ operator: '当前账号' } as AuthSession);
  vi.mocked(apiPost).mockResolvedValue({ ledgerCode: 'STK-REAL' });
});

describe('正式单据操作', () => {
  it('按正式库存维度出库并原样保留明确填写的数量和重量', () => {
    expect(buildOutboundShipmentRequest(inventory, { shipmentCode: 'OUT-REAL', logisticsNo: 'YT123',
      quantity: 2, weight: '6.20', operator: '实际库管', evidenceCode: 'EVIDENCE-REAL',
    })).toMatchObject({ customerId: 57, ownerCustomerId: 57, motherOrderCode: 'MD-REAL',
      workOrderCode: 'ZD-REAL', materialCode: 'MAT-REAL', fromWarehouseCode: 'REAL-WAREHOUSE',
      shipmentCode: 'OUT-REAL', logisticsNo: 'YT123', quantity: 2, weight: '6.20', evidenceCode: 'EVIDENCE-REAL' });
    expect(() => buildOutboundShipmentRequest(inventory, { shipmentCode: 'OUT-REAL', logisticsNo: 'YT123', operator: '库管', quantity: 2 }))
      .toThrow('必须同时填写');
    expect(() => buildOutboundShipmentRequest(inventory, { shipmentCode: 'OUT-REAL', logisticsNo: 'YT123', operator: '库管', weight: '6.20' }))
      .toThrow('必须同时填写');
    expect(() => buildOutboundShipmentRequest(inventory, { shipmentCode: 'OUT-REAL', logisticsNo: 'YT123', operator: '库管' }))
      .toThrow('留空不会自动沿用整批重量');
  });

  it('缺少正式上下文或物流信息时拒绝构造命令，不回退样例客户', () => {
    expect(() => buildOutboundShipmentRequest({ ...inventory, operationContext: undefined })).toThrow('正式操作上下文');
    expect(() => buildOutboundShipmentRequest(inventory, {
      shipmentCode: 'OUT-REAL', operator: '库管', quantity: 12, weight: '25.75',
    })).toThrow('发货运单号');
  });

  it('登录账号决定实际操作人，未登录不得提交', async () => {
    await shipFinishedStock(inventory, {
      shipmentCode: 'OUT-REAL', logisticsNo: 'YT123', operator: '伪造操作人', quantity: 12, weight: '25.75', requestId: 'req-ship-0001',
    });
    expect(apiPost).toHaveBeenCalledWith('/api/business/operations/outbound-shipments', expect.objectContaining({
      operator: '当前账号', requestId: 'req-ship-0001', shipmentCode: 'OUT-REAL', quantity: 12, weight: '25.75', logisticsNo: 'YT123',
    }));
    vi.mocked(fetchAuthSession).mockResolvedValue(null);
    vi.mocked(apiPost).mockClear();
    await expect(shipFinishedStock(inventory)).rejects.toThrow('请登录');
    expect(apiPost).not.toHaveBeenCalled();
  });

  it('退货必须明确原出库与本次物流，售后处理由用户选择', () => {
    expect(() => buildCustomerReturnRequest(inventory, { returnCode: 'RET-REAL', quantity: 12, weight: '25.75' })).toThrow('原出库单号');
    expect(buildCustomerReturnRequest(inventory, { returnCode: 'RET-REAL', originalShipmentCode: 'OUT-9',
      returnLogisticsNo: 'RET-TRACK', reason: '客户退回', operator: '库管', quantity: 12, weight: '25.75',
    })).toMatchObject({ originalShipmentCode: 'OUT-9', returnLogisticsNo: 'RET-TRACK', quantity: 12, weight: '25.75' });
    expect(buildAfterSalesReviewRequest(inventory, { reviewCode: 'ASR-1', returnCode: 'RET-REAL',
      originalShipmentCode: 'OUT-9', disposition: 'RELEASE', inspector: '质检', finding: '复核合格', quantity: 3, weight: '6.20',
    })).toMatchObject({ disposition: 'RELEASE', toWarehouseCode: 'FINISHED-CUSTOMER', quantity: 3, weight: '6.20' });
  });

  it('损耗试算必须有完整实测和计价输入，审批人取当前会话', async () => {
    expect(() => buildLossApprovalRequest(loss, { operator: '报工人', approver: '组长' })).toThrow('投入重量');
    await approveLoss(loss, { operator: '实际报工人', inputWeight: '30', outputWeight: '29', reasonableTransferWeight: '0',
      standardLossRatePercent: '2', acceptedQuantity: 12, pieceRate: '1.2', lossDeductionUnitPrice: '4', approver: '冒名审批人' });
    expect(apiPost).toHaveBeenCalledWith('/api/business/operations/loss-approvals', expect.objectContaining({
      operator: '实际报工人', approver: '当前账号', inputWeight: '30', outputWeight: '29', pieceRate: '1.2' }));
  });

  it('来料不生成纸质行号、样例日期或称重', () => {
    const inbound = { code: 'MD-REAL', customer: '实际客户', inboundDate: '2026-10-10', operationContext } as InboundOrder;
    expect(() => buildConfirmInboundRequest(inbound, { operator: '收货人', processingRequirement: '镜面抛光', dueDate: '2026-10-20' }))
      .toThrow('留空不会自动沿用整批重量');
    const request = buildConfirmInboundRequest(inbound, {
      operator: '收货人', processingRequirement: '镜面抛光', dueDate: '2026-10-20', quantity: 12, weight: '25.75',
    });
    expect(request).toMatchObject({ customerId: 57, materialCode: 'MAT-REAL', inboundDate: '2026-10-10', quantity: 12, weight: '25.75' });
    expect(request).not.toHaveProperty('sourceRegisterLineNo');
  });
});
