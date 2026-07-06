import type { InboundOrder, InventoryBalance, QualityCase, WorkOrder } from '../data/m1PrototypeData';
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

export interface ReworkReportRequest {
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
  evidenceCode?: string;
  remark?: string;
}

export interface ReworkReportResult {
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

export interface QualityReleaseRequest {
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
  releaseResult: string;
  evidenceCode?: string;
  remark?: string;
}

export interface QualityReleaseResult {
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
  releaseResult: string;
  evidenceCode?: string;
}

export interface OutboundShipmentRequest {
  shipmentCode: string;
  motherOrderCode: string;
  workOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  quantity: number;
  weight: string;
  logisticsNo: string;
  operator: string;
  evidenceCode?: string;
  remark?: string;
}

export interface OutboundShipmentResult {
  shipmentCode: string;
  workOrderCode: string;
  ledgerCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  fromStockStatus: string;
  toStockStatus: string;
  quantity: number;
  weight: number;
  logisticsNo: string;
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
  'FG-20260706-001': 'MD-20260706-004',
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

const inventoryBatchWorkOrders: Record<string, string> = {
  'MD-20260706-001': 'ZD-20260706-001',
  'MD-20260706-002': 'ZD-20260706-006',
  'ZD-20260706-001': 'ZD-20260706-001',
  'FG-20260706-002': 'FG-20260706-002',
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

export function buildReworkReportRequest(qualityCase: QualityCase): ReworkReportRequest {
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
    fromWarehouseCode: 'REWORK-LINE-01',
    toWarehouseCode: 'SEMI-FINISHED',
    quantity: qualityFreezeQuantities[qualityCase.code] ?? 1,
    weight: qualityFreezeWeights[qualityCase.code] ?? '1.000',
    process: `返工${qualityCase.process}`,
    operator: '王强',
    evidenceCode: `PHOTO-REWORK-${qualityCase.code}`,
    remark: '前端质检作业主控台提交返工报工',
  };
}

export async function reportRework(qualityCase: QualityCase): Promise<ReworkReportResult> {
  return apiPost<ReworkReportRequest, ReworkReportResult>(
    '/api/business/operations/rework-reports',
    buildReworkReportRequest(qualityCase),
  );
}

export function buildQualityReleaseRequest(qualityCase: QualityCase): QualityReleaseRequest {
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
    toWarehouseCode: 'FINISHED-CUSTOMER',
    quantity: qualityFreezeQuantities[qualityCase.code] ?? 1,
    weight: qualityFreezeWeights[qualityCase.code] ?? '1.000',
    process: qualityCase.process.includes('复检') ? qualityCase.process : `复检${qualityCase.process}`,
    inspector: qualityCase.inspector,
    releaseResult: qualityCase.action === '放行' ? '复检合格，允许成品入库' : `${qualityCase.action}后复检放行`,
    evidenceCode: `PHOTO-RELEASE-${qualityCase.code}`,
    remark: '前端质检作业主控台复检放行',
  };
}

export async function releaseQualityStock(qualityCase: QualityCase): Promise<QualityReleaseResult> {
  return apiPost<QualityReleaseRequest, QualityReleaseResult>(
    '/api/business/operations/quality-releases',
    buildQualityReleaseRequest(qualityCase),
  );
}

export function buildOutboundShipmentRequest(balance: InventoryBalance): OutboundShipmentRequest {
  const customerId = customerIds[balance.owner] ?? 9000;
  const workOrderCode = inventoryBatchWorkOrders[balance.batch] ?? balance.batch;
  const shortCode = balance.batch.replace(/[^0-9]/g, '') || '20260706001';

  return {
    shipmentCode: `OUT-${shortCode}`,
    motherOrderCode: balance.batch.startsWith('MD-') ? balance.batch : (workOrderMotherOrders[workOrderCode] ?? balance.batch),
    workOrderCode,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[balance.material] ?? `MAT-${balance.batch}`,
    fromWarehouseCode: 'FINISHED-CUSTOMER',
    toWarehouseCode: 'SHIPPED-CUSTOMER',
    quantity: balance.qty,
    weight: parseWeight(balance.weight),
    logisticsNo: `SF-OUT-${shortCode}`,
    operator: '库管员 何琳',
    evidenceCode: `ATT-OUT-${shortCode}`,
    remark: '前端库存追溯主控台确认出库',
  };
}

export async function shipFinishedStock(balance: InventoryBalance): Promise<OutboundShipmentResult> {
  return apiPost<OutboundShipmentRequest, OutboundShipmentResult>(
    '/api/business/operations/outbound-shipments',
    buildOutboundShipmentRequest(balance),
  );
}

function parseWeight(weight: string): string {
  const value = weight.replace(/[^\d.]/g, '');
  return value || '0';
}
