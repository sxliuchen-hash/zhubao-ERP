import type { InboundOrder, InventoryBalance, LossApproval } from '../data/m1PrototypeData';
import { apiPost } from './apiClient';
import { fetchAuthSession } from './authApi';
import type { BusinessOperationContext } from '../types/businessOperationContext';
import { confirmedQuantityAndWeight } from './operationAmountPolicy';

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
  processingRequirement: string;
  dueDate: string;
  customerName?: string;
  requestId?: string;
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
  requestId?: string;
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

type OperationRow = { operationContext?: BusinessOperationContext | null };
type AmountInput = { quantity?: number | string; weight?: number | string };

function context(row: OperationRow) {
  const value = row.operationContext;
  if (!value || !value.customerId || !value.motherOrderCode || !value.materialCode || !value.warehouseCode) {
    throw new Error('单据缺少正式操作上下文，请刷新单据后重试');
  }
  if (value.ownerType !== 'CUSTOMER') {
    throw new Error('此操作仅适用于客户所有库存');
  }
  return {
    motherOrderCode: value.motherOrderCode,
    workOrderCode: value.workOrderCode,
    customerId: value.customerId,
    ownerCustomerId: value.ownerCustomerId ?? undefined,
    materialCode: value.materialCode,
    fromWarehouseCode: value.warehouseCode,
    quantity: value.quantity,
    weight: String(value.weight),
  };
}

/** 用调用方明确提交的数量和重量覆盖整批快照，避免只改数量时悄悄带走整批重量。 */
function withConfirmedAmounts<T extends object>(
  row: OperationRow,
  extra: AmountInput,
  fields: (stock: ReturnType<typeof context>) => T,
) {
  const stock = context(row);
  const amounts = confirmedQuantityAndWeight(extra);
  return { ...stock, ...fields(stock), ...extra, ...amounts };
}

function requireFields<T extends object>(request: T, fields: Array<[keyof T, string]>): T {
  for (const [key, label] of fields) {
    const value = request[key];
    if (value === undefined || value === null || String(value).trim() === '') {
      throw new Error(`请填写${label}`);
    }
  }
  const numericFields: Array<[string, string]> = [
    ['quantity', '本次数量'], ['weight', '本次重量'], ['acceptedQuantity', '验收数量'], ['inputWeight', '投入重量'],
    ['outputWeight', '产出重量'], ['reasonableTransferWeight', '合理转移重量'], ['pieceRate', '计件单价'],
    ['standardLossRatePercent', '标准损耗率'], ['lossDeductionUnitPrice', '损耗扣减单价'],
  ];
  for (const [key, label] of numericFields) {
    const value = (request as Record<string, unknown>)[key];
    if (value === undefined) continue;
    if (String(value).trim() === '' || !Number.isFinite(Number(value)) || Number(value) < 0) {
      throw new Error(`${label}必须为非负数`);
    }
  }
  return request;
}

async function actor(): Promise<string> {
  const session = await fetchAuthSession();
  if (!session?.operator) throw new Error('请登录后再提交业务操作');
  return session.operator;
}

export function buildConfirmInboundRequest(order: InboundOrder, extra: Partial<ConfirmInboundRequest> = {}): ConfirmInboundRequest {
  return requireFields(withConfirmedAmounts(order, extra, (stock) => ({
    warehouseCode: stock.fromWarehouseCode,
    inboundDate: order.inboundDate, expressCompany: order.expressCompany, courierNo: order.courierNo,
    senderName: order.senderName, receiverName: order.receiverName, unpackSigner: order.unpackSigner,
    goodsName: order.goodsName, customerName: order.customer, quantityUnit: '件',
    operator: '', processingRequirement: '', dueDate: '',
  })), [['operator', '操作人'], ['processingRequirement', '加工要求'], ['dueDate', '完成期限']]);
}

export async function confirmInbound(order: InboundOrder, extra: Partial<ConfirmInboundRequest> = {}): Promise<InboundConfirmationResult> {
  return apiPost('/api/business/operations/inbound-confirmations', buildConfirmInboundRequest(order, { ...extra, operator: await actor() }));
}

export function buildOutboundShipmentRequest(
  row: InventoryBalance,
  extra: Partial<Omit<OutboundShipmentRequest, 'quantity' | 'weight'>> & AmountInput = {},
): OutboundShipmentRequest {
  return requireFields(withConfirmedAmounts(row, extra, () => ({
    toWarehouseCode: 'SHIPPED-CUSTOMER', shipmentCode: '', logisticsNo: '',
    deliveryCategory: '成品', operator: '',
  })), [['shipmentCode', '出库单号'], ['logisticsNo', '发货运单号'], ['operator', '操作人']]);
}

export async function shipFinishedStock(
  row: InventoryBalance,
  extra: Partial<Omit<OutboundShipmentRequest, 'quantity' | 'weight'>> & AmountInput = {},
): Promise<OutboundShipmentResult> {
  return apiPost('/api/business/operations/outbound-shipments', buildOutboundShipmentRequest(row, { ...extra, operator: await actor() }));
}

export function buildCustomerReturnRequest(row: InventoryBalance, extra: Partial<CustomerReturnRequest> = {}): CustomerReturnRequest {
  return requireFields(withConfirmedAmounts(row, extra, () => ({
    toWarehouseCode: 'RETURN-CHECK', returnCode: '', originalShipmentCode: '',
    returnLogisticsNo: '', reason: '', operator: '',
  })), [['returnCode', '退货单号'], ['originalShipmentCode', '原出库单号'], ['returnLogisticsNo', '退货运单号'], ['reason', '退货原因'], ['operator', '操作人']]);
}

export async function returnCustomerShipment(row: InventoryBalance, extra: Partial<CustomerReturnRequest> = {}): Promise<CustomerReturnResult> {
  return apiPost('/api/business/operations/customer-returns', buildCustomerReturnRequest(row, { ...extra, operator: await actor() }));
}

export function buildAfterSalesReviewRequest(row: InventoryBalance, extra: Partial<AfterSalesReviewRequest> = {}): AfterSalesReviewRequest {
  const target = { RELEASE: 'FINISHED-CUSTOMER', REWORK: 'REWORK-LINE-01', SCRAP: 'SCRAP', FREEZE: 'FROZEN-QC' };
  return requireFields(withConfirmedAmounts(row, extra, () => ({
    toWarehouseCode: extra.disposition ? target[extra.disposition] : '',
    reviewCode: '', returnCode: '', originalShipmentCode: '', disposition: extra.disposition as AfterSalesReviewDisposition,
    inspector: '', finding: '',
  })), [['reviewCode', '售后复核单号'], ['returnCode', '退货单号'], ['originalShipmentCode', '原出库单号'],
    ['disposition', '处理结论'], ['inspector', '质检员'], ['finding', '复核说明']]);
}

export async function reviewAfterSalesReturn(row: InventoryBalance, extra: Partial<AfterSalesReviewRequest> = {}): Promise<AfterSalesReviewResult> {
  return apiPost('/api/business/operations/after-sales-reviews', buildAfterSalesReviewRequest(row, { ...extra, inspector: await actor() }));
}

export function buildLossApprovalRequest(row: LossApproval, extra: Partial<LossApprovalRequest> = {}): LossApprovalRequest {
  return requireFields({ ...context(row), approvalCode: row.code, process: row.process, operator: '', approver: '',
    inputWeight: '', outputWeight: '', reasonableTransferWeight: '', standardLossRatePercent: '',
    acceptedQuantity: undefined as unknown as number, pieceRate: '', lossDeductionUnitPrice: '', ...extra,
  }, [['approvalCode', '损耗申请单号'], ['operator', '报工人'], ['approver', '审批人'], ['process', '损耗所属工序'], ['inputWeight', '投入重量'], ['outputWeight', '产出重量'],
    ['reasonableTransferWeight', '合理转移重量'], ['standardLossRatePercent', '标准损耗率'],
    ['acceptedQuantity', '验收数量'], ['pieceRate', '计件单价'], ['lossDeductionUnitPrice', '损耗扣减单价']]);
}

export async function approveLoss(row: LossApproval, extra: Partial<LossApprovalRequest> = {}): Promise<LossApprovalResult> {
  return apiPost('/api/business/operations/loss-approvals', buildLossApprovalRequest(row, { ...extra, approver: await actor() }));
}
