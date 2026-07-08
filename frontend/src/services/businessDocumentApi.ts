import { apiGet } from './apiClient';

export interface MotherOrderRecord {
  code: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  inboundDate: string;
  expressCompany: string;
  courierNo: string;
  senderName: string;
  receiverName: string;
  unpackSigner: string;
  goodsName: string;
  ownerType: string;
  customerOwned: boolean;
  warehouseCode: string;
  quantity: number | string;
  quantityUnit: string;
  weight: number | string;
  stockStatus: string;
  ledgerCode: string;
  sourceRegisterType: string;
  sourceRegisterLineNo?: number;
  sourceRegisterSequence: string;
  operator: string;
  evidenceCode: string;
  remark: string;
  status: string;
  createdAt: string;
}

export interface OutboundOrderRecord {
  code: string;
  motherOrderCode: string;
  workOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  deliveryDate: string;
  deliveryCategory: string;
  quantity: number | string;
  quantityUnit: string;
  weight: number | string;
  expressCompany: string;
  logisticsNo: string;
  paperExpressText: string;
  handler: string;
  sourceRegisterType: string;
  sourceRegisterLineNo?: number;
  sourceRegisterSequence: string;
  operator: string;
  evidenceCode: string;
  remark: string;
  status: string;
  ledgerCode: string;
  createdAt: string;
}

export async function fetchMotherOrder(code: string): Promise<MotherOrderRecord> {
  return apiGet<MotherOrderRecord>(`/api/business/documents/mother-orders/${encodeURIComponent(code)}`);
}

export async function fetchOutboundOrder(code: string): Promise<OutboundOrderRecord> {
  return apiGet<OutboundOrderRecord>(`/api/business/documents/outbound-orders/${encodeURIComponent(code)}`);
}
