import type { InboundOrder } from '../data/m1PrototypeData';
import { apiPost } from './apiClient';

export interface ConfirmInboundRequest {
  motherOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  warehouseCode: string;
  quantity: number;
  weight: string;
  operator: string;
  evidenceCode?: string;
  remark?: string;
}

export interface InboundConfirmationResult {
  motherOrderCode: string;
  ledgerCode: string;
  warehouseCode: string;
  stockStatus: string;
  quantity: number;
  weight: number;
  operator: string;
  evidenceCode?: string;
}

const customerIds: Record<string, number> = {
  恒信珠宝: 1001,
  雅辰饰品: 1002,
  璟瑞工贸: 1003,
  瑞禾珠宝: 1004,
};

const materialCodes: Record<string, string> = {
  '18K 金戒托': 'MAT-18K-RING',
  银链半成品: 'MAT-SILVER-CHAIN',
  宝石托坯: 'MAT-GEM-BASE',
  'PT950 吊坠坯': 'MAT-PT950-PENDANT',
};

export function buildConfirmInboundRequest(order: InboundOrder): ConfirmInboundRequest {
  const customerId = customerIds[order.customer] ?? 9000;

  return {
    motherOrderCode: order.code,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[order.material] ?? `MAT-${order.code}`,
    warehouseCode: 'RAW-CUSTOMER',
    quantity: order.qty,
    weight: parseWeight(order.weight),
    operator: '库管员 何琳',
    evidenceCode: order.courierNo,
    remark: '前端来料作业主控台确认',
  };
}

export async function confirmInbound(order: InboundOrder): Promise<InboundConfirmationResult> {
  return apiPost<ConfirmInboundRequest, InboundConfirmationResult>(
    '/api/business/operations/inbound-confirmations',
    buildConfirmInboundRequest(order),
  );
}

function parseWeight(weight: string): string {
  const value = weight.replace(/[^\d.]/g, '');
  return value || '0';
}
