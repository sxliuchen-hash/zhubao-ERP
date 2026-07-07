import type { InboundOrder, InventoryBalance, LossApproval, QualityCase, WorkOrder } from '../data/m1PrototypeData';
import { apiPost } from './apiClient';

export interface ConfirmInboundRequest {
  motherOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  inboundDate?: string;
  expressCompany?: string;
  courierNo?: string;
  senderName?: string;
  receiverName?: string;
  unpackSigner?: string;
  goodsName?: string;
  sourceRegisterType?: string;
  sourceRegisterLineNo?: number;
  sourceRegisterSequence?: string;
  warehouseCode: string;
  quantity: number;
  quantityUnit?: string;
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
  quantityUnit?: string;
  weight: string;
  workDate?: string;
  process: string;
  processCategory?: string;
  productCategory?: string;
  machineNo?: string;
  workItemName?: string;
  startedAt?: string;
  finishedAt?: string;
  completionStatus?: string;
  handoffAuditStatus?: string;
  sourceRegisterType?: string;
  sourceRegisterLineNo?: number;
  sourceRegisterSequence?: string;
  operator: string;
  operatorSignatureName?: string;
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
  deliveryDate?: string;
  deliveryCategory?: string;
  quantity: number;
  quantityUnit?: string;
  weight: string;
  expressCompany?: string;
  logisticsNo: string;
  paperExpressText?: string;
  sourceRegisterType?: string;
  sourceRegisterLineNo?: number;
  sourceRegisterSequence?: string;
  handler?: string;
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

export interface CustomerReturnRequest {
  returnCode: string;
  originalShipmentCode: string;
  motherOrderCode: string;
  workOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  quantity: number;
  weight: string;
  returnLogisticsNo: string;
  reason: string;
  operator: string;
  evidenceCode?: string;
  remark?: string;
}

export interface CustomerReturnResult {
  returnCode: string;
  originalShipmentCode: string;
  workOrderCode: string;
  ledgerCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  fromStockStatus: string;
  toStockStatus: string;
  quantity: number;
  weight: number;
  returnLogisticsNo: string;
  reason: string;
  operator: string;
  evidenceCode?: string;
}

export type AfterSalesReviewDisposition = 'RELEASE' | 'REWORK' | 'SCRAP' | 'FREEZE';

export interface AfterSalesReviewRequest {
  reviewCode: string;
  returnCode: string;
  originalShipmentCode: string;
  motherOrderCode: string;
  workOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  quantity: number;
  weight: string;
  disposition: AfterSalesReviewDisposition;
  inspector: string;
  finding: string;
  evidenceCode?: string;
  remark?: string;
}

export interface AfterSalesReviewResult {
  reviewCode: string;
  returnCode: string;
  originalShipmentCode: string;
  workOrderCode: string;
  ledgerCode: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  fromStockStatus: string;
  toStockStatus: string;
  quantity: number;
  weight: number;
  disposition: AfterSalesReviewDisposition;
  inspector: string;
  finding: string;
  evidenceCode?: string;
}

export interface LossApprovalRequest {
  approvalCode: string;
  motherOrderCode: string;
  workOrderCode: string;
  customerId: number;
  ownerCustomerId?: number;
  materialCode: string;
  process: string;
  operator: string;
  approver: string;
  inputWeight: string;
  outputWeight: string;
  reasonableTransferWeight: string;
  standardLossRatePercent: string;
  acceptedQuantity: number;
  pieceRate: string;
  lossDeductionUnitPrice: string;
  evidenceCode?: string;
  remark?: string;
}

export interface LossApprovalResult {
  approvalCode: string;
  motherOrderCode: string;
  workOrderCode: string;
  process: string;
  inputWeight: number;
  outputWeight: number;
  reasonableTransferWeight: number;
  lossWeight: number;
  allowedLossWeight: number;
  excessLossWeight: number;
  standardLossRatePercent: number;
  actualLossRatePercent: number;
  overLimit: boolean;
  approvalStatus: string;
  acceptedQuantity: number;
  pieceRate: number;
  pieceWage: number;
  lossDeduction: number;
  trialWage: number;
  formalPayroll: boolean;
  operator: string;
  approver: string;
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
  'ZD-20260706-009': 'MD-20260706-001',
  'FG-20260706-001': 'MD-20260706-004',
  'FG-20260706-002': 'MD-20260706-004',
};

const workOrderCustomers: Record<string, string> = {
  'ZD-20260706-001': '恒信珠宝',
  'ZD-20260706-002': '恒信珠宝',
  'ZD-20260706-006': '雅辰饰品',
  'ZD-20260706-009': '恒信珠宝',
  'FG-20260706-001': '瑞禾珠宝',
  'FG-20260706-002': '瑞禾珠宝',
};

const workOrderMaterials: Record<string, string> = {
  'ZD-20260706-001': '18K 金戒托',
  'ZD-20260706-002': '18K 金戒托',
  'ZD-20260706-006': '银链半成品',
  'ZD-20260706-009': '18K 金戒托',
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
  'ZD-20260706-009': '128.600',
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

const lossAcceptedQuantities: Record<string, number> = {
  'LOSS-20260706-009': 56,
  'LOSS-20260706-010': 210,
};

const processPieceRates: Record<string, string> = {
  抛光: '0.80',
  雕刻: '0.65',
  焊接: '0.72',
  镶石: '1.20',
  返工抛光: '0.00',
};

const lossWeightFallbacks: Record<string, { inputWeight: string; outputWeight: string }> = {
  'LOSS-20260706-009': { inputWeight: '121.150', outputWeight: '116.550' },
  'LOSS-20260706-010': { inputWeight: '684.400', outputWeight: '683.200' },
};

function normalizeBusinessDate(value: string | undefined, fallbackCode?: string): string | undefined {
  if (value?.match(/^\d{4}-\d{2}-\d{2}$/)) {
    return value;
  }
  const codeDate = fallbackCode?.match(/20\d{6}/)?.[0];
  if (codeDate) {
    return `${codeDate.slice(0, 4)}-${codeDate.slice(4, 6)}-${codeDate.slice(6, 8)}`;
  }
  if (value?.match(/^\d{2}-\d{2}$/)) {
    return `2026-${value}`;
  }
  return value;
}

export function buildConfirmInboundRequest(order: InboundOrder): ConfirmInboundRequest {
  const customerId = customerIds[order.customer] ?? 9000;

  return {
    motherOrderCode: order.code,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[order.material] ?? `MAT-${order.code}`,
    inboundDate: normalizeBusinessDate(order.inboundDate, order.code),
    expressCompany: order.expressCompany,
    courierNo: order.courierNo,
    senderName: order.senderName,
    receiverName: order.receiverName,
    unpackSigner: order.unpackSigner,
    goodsName: order.goodsName,
    sourceRegisterType: '签收入库登记表',
    sourceRegisterLineNo: 4,
    sourceRegisterSequence: '1',
    warehouseCode: 'RAW-CUSTOMER',
    quantity: order.qty,
    quantityUnit: '件',
    weight: parseWeight(order.weight),
    operator: '库管员 何琳',
    evidenceCode: order.courierNo,
    remark: order.remark || '前端来料作业主控台确认',
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
    quantityUnit: '件',
    weight: productionWeights[order.code] ?? String(order.finishedQty),
    workDate: normalizeBusinessDate(undefined, order.code),
    process: order.process,
    processCategory: order.processCategory,
    productCategory: order.productCategory,
    machineNo: order.machineNo,
    workItemName: order.workItemName,
    startedAt: order.startedAt,
    finishedAt: order.finishedAt,
    completionStatus: order.completionStatus,
    handoffAuditStatus: order.handoffAuditStatus,
    sourceRegisterType: order.process === '雕刻' ? '雕刻机统计单（日）' : '工作项目记录单（日）',
    sourceRegisterLineNo: order.process === '雕刻' ? 6 : 12,
    sourceRegisterSequence: '1',
    operator: order.assignee,
    operatorSignatureName: order.process === '雕刻' || order.processCategory === '设备' ? order.assignee : undefined,
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
    deliveryDate: '2026-07-06',
    deliveryCategory: balance.status.includes('返工') ? '返修件' : '成品',
    quantity: balance.qty,
    quantityUnit: '件',
    weight: parseWeight(balance.weight),
    expressCompany: '顺丰',
    logisticsNo: `SF-OUT-${shortCode}`,
    paperExpressText: `顺丰 SF-OUT-${shortCode}`,
    sourceRegisterType: '交货情况登记表',
    sourceRegisterLineNo: 3,
    sourceRegisterSequence: '1',
    handler: '何琳',
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

export function buildCustomerReturnRequest(balance: InventoryBalance): CustomerReturnRequest {
  const customerId = customerIds[balance.owner] ?? 9000;
  const workOrderCode = inventoryBatchWorkOrders[balance.batch] ?? balance.batch;
  const shortCode = balance.batch.replace(/[^0-9]/g, '') || '20260706001';

  return {
    returnCode: `RET-${shortCode}`,
    originalShipmentCode: `OUT-${shortCode}`,
    motherOrderCode: balance.batch.startsWith('MD-') ? balance.batch : (workOrderMotherOrders[workOrderCode] ?? balance.batch),
    workOrderCode,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[balance.material] ?? `MAT-${balance.batch}`,
    fromWarehouseCode: 'SHIPPED-CUSTOMER',
    toWarehouseCode: 'RETURN-CHECK',
    quantity: balance.qty,
    weight: parseWeight(balance.weight),
    returnLogisticsNo: `SF-RET-${shortCode}`,
    reason: '客户退回复核',
    operator: '库管员 何琳',
    evidenceCode: `ATT-RET-${shortCode}`,
    remark: '前端库存追溯主控台客户退货入库',
  };
}

export async function returnCustomerShipment(balance: InventoryBalance): Promise<CustomerReturnResult> {
  return apiPost<CustomerReturnRequest, CustomerReturnResult>(
    '/api/business/operations/customer-returns',
    buildCustomerReturnRequest(balance),
  );
}

export function buildAfterSalesReviewRequest(balance: InventoryBalance): AfterSalesReviewRequest {
  const customerId = customerIds[balance.owner] ?? 9000;
  const workOrderCode = inventoryBatchWorkOrders[balance.batch] ?? balance.batch;
  const shortCode = balance.batch.replace(/[^0-9]/g, '') || '20260706001';

  return {
    reviewCode: `ASR-${shortCode}`,
    returnCode: `RET-${shortCode}`,
    originalShipmentCode: `OUT-${shortCode}`,
    motherOrderCode: balance.batch.startsWith('MD-') ? balance.batch : (workOrderMotherOrders[workOrderCode] ?? balance.batch),
    workOrderCode,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[balance.material] ?? `MAT-${balance.batch}`,
    fromWarehouseCode: 'RETURN-CHECK',
    toWarehouseCode: 'REWORK-LINE-01',
    quantity: Math.min(balance.qty, 2),
    weight: parseWeight(balance.weight),
    disposition: 'REWORK',
    inspector: '周娜',
    finding: '售后复核发现外观/工艺问题，转返工处理',
    evidenceCode: `PHOTO-ASR-${shortCode}`,
    remark: '前端库存追溯主控台售后复核分流',
  };
}

export async function reviewAfterSalesReturn(balance: InventoryBalance): Promise<AfterSalesReviewResult> {
  return apiPost<AfterSalesReviewRequest, AfterSalesReviewResult>(
    '/api/business/operations/after-sales-reviews',
    buildAfterSalesReviewRequest(balance),
  );
}

export function buildLossApprovalRequest(approval: LossApproval): LossApprovalRequest {
  const customer = workOrderCustomers[approval.workOrder] ?? '恒信珠宝';
  const material = workOrderMaterials[approval.workOrder] ?? approval.workOrder;
  const customerId = customerIds[customer] ?? 9000;
  const weights = parseLossEvidenceWeights(approval);

  return {
    approvalCode: approval.code,
    motherOrderCode: workOrderMotherOrders[approval.workOrder] ?? approval.workOrder,
    workOrderCode: approval.workOrder,
    customerId,
    ownerCustomerId: customerId,
    materialCode: materialCodes[material] ?? `MAT-${approval.workOrder}`,
    process: approval.process,
    operator: approval.workOrder === 'ZD-20260706-001' ? '李华' : '王强',
    approver: '车间组长 马杰',
    inputWeight: weights.inputWeight,
    outputWeight: weights.outputWeight,
    reasonableTransferWeight: '0.000',
    standardLossRatePercent: parsePercent(approval.standard),
    acceptedQuantity: lossAcceptedQuantities[approval.code] ?? 1,
    pieceRate: processPieceRates[approval.process] ?? '0.80',
    lossDeductionUnitPrice: '6.00',
    evidenceCode: weights.evidenceCode,
    remark: '前端质检作业主控台提交损耗审批和工资试算',
  };
}

export async function approveLoss(approval: LossApproval): Promise<LossApprovalResult> {
  return apiPost<LossApprovalRequest, LossApprovalResult>(
    '/api/business/operations/loss-approvals',
    buildLossApprovalRequest(approval),
  );
}

function parseWeight(weight: string): string {
  const value = weight.replace(/[^\d.]/g, '');
  return value || '0';
}

function parsePercent(percent: string): string {
  const value = percent.replace(/[^\d.]/g, '');
  return value || '0';
}

function parseLossEvidenceWeights(approval: LossApproval): { inputWeight: string; outputWeight: string; evidenceCode: string } {
  const input = approval.evidence.match(/投入\s*([\d.]+)g/);
  const output = approval.evidence.match(/产出\s*([\d.]+)g/);
  const fallback = lossWeightFallbacks[approval.code] ?? { inputWeight: '1.000', outputWeight: '1.000' };

  return {
    inputWeight: input?.[1] ?? fallback.inputWeight,
    outputWeight: output?.[1] ?? fallback.outputWeight,
    evidenceCode: approval.evidence.includes('HO-') ? approval.evidence : 'HO-20260706-021',
  };
}
