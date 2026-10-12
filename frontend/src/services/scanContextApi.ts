import { apiPost } from './apiClient';

/** 与 POST /api/scan/context 一致的对象类型。手输时只有用户明确选择才传。 */
export type ScanObjectType = 'MOTHER_ORDER' | 'WORK_ORDER' | 'OUTBOUND_ORDER' | 'REWORK' | 'EXPRESS_NO';

/** 当前页面的查询意图。不能代替服务端权限。 */
export type ScanIntent = 'LOOKUP' | 'INTAKE' | 'PRODUCTION' | 'QUALITY' | 'SHIPPING';

export interface ScanContextQuery {
  content: string;
  objectType?: ScanObjectType;
  intent?: ScanIntent;
}

export interface ScanContextTarget {
  objectType: ScanObjectType | null;
  code: string | null;
  motherOrderCode: string | null;
  workOrderCode: string | null;
  reworkOrderCode: string | null;
  outboundOrderCode: string | null;
  reference: string | null;
}

export interface ScanContextOrder {
  customerId: number | null;
  customerName: string | null;
  goodsName: string | null;
  materialCode: string | null;
  quantity: number | null;
  quantityUnit: string | null;
  weight: number | null;
  weightUnit: string | null;
  requirement: string | null;
  dueDate: string | null;
}

export interface ScanRequirementItem {
  source: string | null;
  process: string | null;
  text: string | null;
}

export interface ScanContextRequirements {
  motherRequirement: string | null;
  motherSource: string | null;
  workOrderRequirement: string | null;
  workOrderSource: string | null;
  stationRequirements: ScanRequirementItem[];
}

export interface ScanProgressRecord {
  code: string | null;
  targetType: string | null;
  targetCode: string | null;
  motherOrderCode: string | null;
  stage: string | null;
  process: string | null;
  clauseNo: string | null;
  note: string | null;
  operatorId: string | null;
  operatorName: string | null;
  operatorRole: string | null;
  supplementary: boolean;
  recordedAt: string | null;
  requestId: string | null;
}

export interface ScanQualityLineFact {
  lineNo: number;
  clause: string | null;
  verdict: string | null;
  reason: string | null;
}

export interface ScanQualityFact {
  qualityCaseCode: string | null;
  orderCode: string | null;
  outcome: string | null;
  frozen: boolean;
  reworkOrderCode: string | null;
  inspector: string | null;
  lines: ScanQualityLineFact[];
}

export interface ScanReinspectionStatus {
  available: boolean;
  reason: string | null;
}

export interface ScanHandoffFact {
  process: string | null;
  operator: string | null;
  ledgerCode: string | null;
  createdAt: string | null;
}

export interface ScanStockFact {
  materialCode: string | null;
  warehouseCode: string | null;
  ownerType: string | null;
  ownerCustomerId: number | null;
  workOrderCode: string | null;
  stockStatus: string | null;
  quantity: number | null;
  weight: number | null;
}

export interface ScanShipmentFact {
  outboundCode: string | null;
  motherOrderCode: string | null;
  workOrderCode: string | null;
  logisticsNo: string | null;
  status: string | null;
  ledgerCode: string | null;
}

export interface ScanContextProgress {
  handoffs: ScanHandoffFact[];
  quality: ScanQualityFact | null;
  reinspection: ScanReinspectionStatus | null;
  stock: ScanStockFact[];
  shipments: ScanShipmentFact[];
  progressRecords: ScanProgressRecord[];
}

export interface ScanRelatedTarget {
  objectType: ScanObjectType | null;
  code: string | null;
  motherOrderCode: string | null;
  requirement: string | null;
  quantity: number | null;
  quantityUnit: string | null;
}

export interface ScanContextAction {
  code: string;
  allowed: boolean;
  blockKind: string | null;
  blockReason: string | null;
}

export interface ScanBalanceCandidate {
  materialCode: string | null;
  warehouseCode: string | null;
  ownerType: string | null;
  ownerCustomerId: number | null;
  customerId: number | null;
  motherOrderCode: string | null;
  workOrderCode: string | null;
  stockStatus: string | null;
  quantity: number | null;
  weight: number | null;
}

export interface ScanOperationContext {
  formalOperations: string[];
  balanceCandidates: ScanBalanceCandidate[];
  balanceSelectionRequired: boolean;
  selectedBalance: ScanBalanceCandidate | null;
  factSnapshot: string | null;
}

export interface ScanContextResponse {
  target: ScanContextTarget;
  order: ScanContextOrder;
  requirements: ScanContextRequirements;
  progress: ScanContextProgress;
  relatedTargets: ScanRelatedTarget[];
  requiresSelection: boolean;
  actions: ScanContextAction[];
  operationContext: ScanOperationContext;
}

export const scanObjectTypeOptions: Array<{ value: ScanObjectType; label: string }> = [
  { value: 'MOTHER_ORDER', label: '母单' },
  { value: 'WORK_ORDER', label: '子单' },
  { value: 'OUTBOUND_ORDER', label: '出货单' },
  { value: 'REWORK', label: '返工单' },
  { value: 'EXPRESS_NO', label: '快递运单' },
];

export function scanObjectTypeLabel(objectType: ScanObjectType | null | undefined): string {
  return scanObjectTypeOptions.find((item) => item.value === objectType)?.label ?? '未定类型';
}

/** 只查询上下文，不写库存，也不提交收货、报工、质检或发货。 */
export function queryScanContext(input: ScanContextQuery): Promise<ScanContextResponse> {
  const body: ScanContextQuery = { content: input.content.trim() };
  if (input.objectType) body.objectType = input.objectType;
  if (input.intent) body.intent = input.intent;
  return apiPost<ScanContextQuery, ScanContextResponse>('/api/scan/context', body);
}

export type ProgressStage = 'PRODUCTION' | 'QUALITY' | 'SHIPPING';

/** 追加过程说明。操作人由服务端从会话读取，这里不能传姓名或岗位。 */
export interface SaveProgressRecordCommand {
  targetType: string;
  targetCode: string;
  motherOrderCode: string;
  stage: ProgressStage;
  process: string;
  clauseNo: string;
  note: string;
  requestId: string;
}

export function saveProgressRecord(command: SaveProgressRecordCommand): Promise<ScanProgressRecord> {
  return apiPost<SaveProgressRecordCommand, ScanProgressRecord>('/api/scan/progress-records', command);
}
