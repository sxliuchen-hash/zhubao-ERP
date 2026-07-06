import type { InboundOrder, QualityCase, WorkOrder } from '../data/m1PrototypeData';
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

export interface ProductionReportRequest {
  motherOrderCode: string;
  workOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  quantity: number;
  weight: string;
  process: string;
  operator: string;
  handoffCode?: string;
  remark?: string;
}

export interface ProductionReportResult {
  workOrderCode: string;
  ledgerCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  fromStockStatus: string;
  toStockStatus: string;
  quantity: number;
  weight: number;
  process: string;
  operator: string;
  handoffCode?: string;
}

export interface QualityFreezeRequest {
  qualityCaseCode: string;
  motherOrderCode: string;
  workOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  quantity: number;
  weight: string;
  process: string;
  inspector: string;
  defect: string;
  evidenceCode?: string;
  remark?: string;
}

export interface QualityFreezeResult {
  qualityCaseCode: string;
  workOrderCode: string;
  ledgerCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  fromStockStatus: string;
  toStockStatus: string;
  quantity: number;
  weight: number;
  process: string;
  inspector: string;
  evidenceCode?: string;
}

export interface ReworkDispatchRequest {
  reworkOrderCode: string;
  qualityCaseCode: string;
  motherOrderCode: string;
  workOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  quantity: number;
  weight: string;
  process: string;
  operator: string;
  reason: string;
  evidenceCode?: string;
  remark?: string;
}

export interface ReworkDispatchResult {
  reworkOrderCode: string;
  qualityCaseCode: string;
  workOrderCode: string;
  ledgerCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  fromStockStatus: string;
  toStockStatus: string;
  quantity: number;
  weight: number;
  process: string;
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

const workOrderMotherOrders: Record<string, string> = {
  'ZD-20260706-001': 'MD-20260706-001',
  'ZD-20260706-002': 'MD-20260706-001',
  'ZD-20260706-006': 'MD-20260706-002',
  'FG-20260706-002': 'MD-20260706-004',
};

const workOrderCustomers: Record<string, string> = {
  'ZD-20260706-001': '恒信珠宝',
  'ZD-20260706-002': '恒信珠宝',
  'ZD-20260706-006': '雅辰饰品',
  'FG-20260706-001': '瑞禾珠宝',
  'FG-20260706-002': '瑞禾珠宝',
};

const workOrderMaterials: Record<string, string> = {
  'ZD-20260706-001': '18K 金戒托',
  'ZD-20260706-002': '18K 金戒托',
  'ZD-20260706-006': '银链半成品',
  'FG-20260706-001': 'PT950 吊坠坯',
  'FG-20260706-002': 'PT950 吊坠坯',
};

const productionWeights: Record<string, string> = {
  'ZD-20260706-001': '84.950',
  'ZD-20260706-002': '121.150',
  'ZD-20260706-006': '478.240',
  'FG-20260706-002': '26.200',
};

const qualityFreezeQuantities: Record<string, number> = {
  'QC-20260706-021': 5,
  'QC-20260706-022': 6,
  'QC-20260706-023': 1,
};

const qualityFreezeWeights: Record<string, string> = {
  'QC-20260706-021': '7.200',
  'QC-20260706-022': '13.660',
  'QC-20260706-023': '1.250',
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

export function buildProductionReportRequest(order: WorkOrder, handoffCode?: string): ProductionReportRequest {
  const customerId = customerIds[order.customer] ?? 9000;

  return {
    motherOrderCode: workOrderMotherOrders[order.code] ?? order.code,
    workOrderCode: order.code,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[order.material] ?? `MAT-${order.code}`,
    fromWarehouseCode: 'WIP-LINE-01',
    toWarehouseCode: 'SEMI-FINISHED',
    quantity: order.finishedQty,
    weight: productionWeights[order.code] ?? String(order.finishedQty),
    process: order.process,
    operator: order.assignee,
    handoffCode,
    remark: '前端生产派工主控台报工',
  };
}

export async function reportProduction(order: WorkOrder, handoffCode?: string): Promise<ProductionReportResult> {
  return apiPost<ProductionReportRequest, ProductionReportResult>(
    '/api/business/operations/production-reports',
    buildProductionReportRequest(order, handoffCode),
  );
}

export function buildQualityFreezeRequest(qualityCase: QualityCase): QualityFreezeRequest {
  const customer = workOrderCustomers[qualityCase.workOrder] ?? '恒信珠宝';
  const material = workOrderMaterials[qualityCase.workOrder] ?? qualityCase.workOrder;
  const customerId = customerIds[customer] ?? 9000;

  return {
    qualityCaseCode: qualityCase.code,
    motherOrderCode: workOrderMotherOrders[qualityCase.workOrder] ?? qualityCase.workOrder,
    workOrderCode: qualityCase.workOrder,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[material] ?? `MAT-${qualityCase.workOrder}`,
    fromWarehouseCode: 'SEMI-FINISHED',
    toWarehouseCode: 'FROZEN-QC',
    quantity: qualityFreezeQuantities[qualityCase.code] ?? 1,
    weight: qualityFreezeWeights[qualityCase.code] ?? '1.000',
    process: qualityCase.process,
    inspector: qualityCase.inspector,
    defect: qualityCase.defect,
    evidenceCode: `PHOTO-${qualityCase.code}`,
    remark: '前端质检作业主控台冻结异常库存',
  };
}

export async function freezeQualityStock(qualityCase: QualityCase): Promise<QualityFreezeResult> {
  return apiPost<QualityFreezeRequest, QualityFreezeResult>(
    '/api/business/operations/quality-freezes',
    buildQualityFreezeRequest(qualityCase),
  );
}

export function buildReworkDispatchRequest(qualityCase: QualityCase): ReworkDispatchRequest {
  const customer = workOrderCustomers[qualityCase.workOrder] ?? '恒信珠宝';
  const material = workOrderMaterials[qualityCase.workOrder] ?? qualityCase.workOrder;
  const customerId = customerIds[customer] ?? 9000;

  return {
    reworkOrderCode: qualityCase.code.replace('QC-', 'FG-'),
    qualityCaseCode: qualityCase.code,
    motherOrderCode: workOrderMotherOrders[qualityCase.workOrder] ?? qualityCase.workOrder,
    workOrderCode: qualityCase.workOrder,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[material] ?? `MAT-${qualityCase.workOrder}`,
    fromWarehouseCode: 'FROZEN-QC',
    toWarehouseCode: 'REWORK-LINE-01',
    quantity: qualityFreezeQuantities[qualityCase.code] ?? 1,
    weight: qualityFreezeWeights[qualityCase.code] ?? '1.000',
    process: `返工${qualityCase.process}`,
    operator: '车间组长 马杰',
    reason: qualityCase.defect,
    evidenceCode: `PHOTO-${qualityCase.code}`,
    remark: '前端质检作业主控台生成返工工单',
  };
}

export async function dispatchRework(qualityCase: QualityCase): Promise<ReworkDispatchResult> {
  return apiPost<ReworkDispatchRequest, ReworkDispatchResult>(
    '/api/business/operations/rework-dispatches',
    buildReworkDispatchRequest(qualityCase),
  );
}

function parseWeight(weight: string): string {
  const value = weight.replace(/[^\d.]/g, '');
  return value || '0';
}
