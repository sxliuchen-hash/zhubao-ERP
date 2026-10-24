// 业务单据类型。来料、生产、质检、库存和审批列表只显示接口返回的单据，这里不再放样例清单。
export type Tone = 'blue' | 'green' | 'amber' | 'red' | 'violet' | 'gray';

export interface Metric {
  label: string;
  value: string;
  delta: string;
  tone: Tone;
}

export interface WorkQueueItem {
  id: string;
  title: string;
  subtitle: string;
  owner: string;
  due: string;
  severity: 'high' | 'medium' | 'normal';
}

export interface InboundOrder {
  operationContext?: import('../types/businessOperationContext').BusinessOperationContext;
  code: string;
  customer: string;
  material: string;
  inboundDate: string;
  expressCompany: string;
  courierNo: string;
  senderName: string;
  receiverName: string;
  unpackSigner: string;
  goodsName: string;
  qty: number;
  quantityUnit?: string;
  weight: string;
  status: string;
  video: string;
  owner: string;
  remark: string;
}

export interface WorkOrder {
  operationContext?: import('../types/businessOperationContext').BusinessOperationContext;
  code: string;
  customer: string;
  material: string;
  process: string;
  processCategory: string;
  productCategory: string;
  machineNo: string;
  workItemName: string;
  startedAt: string;
  finishedAt: string;
  completionStatus: string;
  handoffAuditStatus: string;
  assignee: string;
  plannedQty: number;
  finishedQty: number;
  status: string;
  progress: number;
}

export interface QualityCase {
  operationContext?: import('../types/businessOperationContext').BusinessOperationContext;
  code: string;
  workOrder: string;
  process: string;
  inspector: string;
  defect: string;
  action: string;
  status: string;
}

export interface InventoryBalance {
  operationContext?: import('../types/businessOperationContext').BusinessOperationContext;
  owner: string;
  batch: string;
  material: string;
  location: string;
  status: string;
  qty: number | string;
  weight: string;
  frozen: string;
  quantityUnit?: string;
}

export interface TraceEvent {
  time: string;
  node: string;
  bizCode: string;
  operator: string;
  evidence: string;
}

export interface HardwareDevice {
  code: string;
  name: string;
  scene: string;
  protocol: string;
  status: 'online' | 'degraded' | 'manual';
  fallback: string;
}

export interface IntegrationTask {
  id: string;
  title: string;
  target: string;
  health: number;
  lastRun: string;
  nextAction: string;
  owner: string;
}

export interface HardwarePocRecord {
  code: string;
  deviceCode: string;
  scene: string;
  testWindow: string;
  result: string;
  evidence: string;
  blocker: string;
  owner: string;
  nextAction: string;
  tone: Tone;
}

export interface InboundCheckpoint {
  label: string;
  value: string;
  status: string;
  tone: Tone;
}

export interface InboundRisk {
  code: string;
  title: string;
  detail: string;
  owner: string;
  action: string;
  tone: Tone;
}

export interface ProductionStationLoad {
  station: string;
  queue: string;
  capacity: string;
  bottleneck: string;
  tone: Tone;
}

export interface ProductionHandoff {
  code: string;
  from: string;
  to: string;
  inputWeight: string;
  outputWeight: string;
  lossRate: string;
  status: string;
  tone: Tone;
}

export interface QualityMetric {
  label: string;
  value: string;
  detail: string;
  tone: Tone;
}

export interface LossApproval {
  operationContext?: import('../types/businessOperationContext').BusinessOperationContext;
  code: string;
  workOrder: string;
  process: string;
  standard: string;
  actual: string;
  evidence: string;
  action: string;
  tone: Tone;
}

export interface InventoryLedgerEntry {
  time: string;
  bizCode: string;
  movement: string;
  before: string;
  after: string;
  source: string;
  operator: string;
}

export interface TraceEvidence {
  label: string;
  status: string;
  link: string;
  tone: Tone;
}
