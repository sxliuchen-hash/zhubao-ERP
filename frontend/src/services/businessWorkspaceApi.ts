import type {
  InboundCheckpoint,
  InboundOrder,
  InboundRisk,
  InventoryBalance,
  InventoryLedgerEntry,
  LossApproval,
  ProductionHandoff,
  ProductionStationLoad,
  QualityCase,
  QualityMetric,
  Tone,
  TraceEvent,
  TraceEvidence,
  WorkOrder,
} from '../data/m1PrototypeData';
import { apiGet } from './apiClient';
import type { BusinessOperationContext } from '../types/businessOperationContext';

interface ReviewStatDto {
  label: string;
  value: string;
  detail: string;
  tone: Tone;
}

interface ActionItemDto {
  code: string;
  title: string;
  detail: string;
  owner: string;
  action: string;
  tone: Tone;
}

interface InboundOrderDto {
  operationContext?: BusinessOperationContext;
  code: string;
  customer: string;
  material: string;
  inboundDate?: string;
  expressCompany?: string;
  courierNo: string;
  senderName?: string;
  receiverName?: string;
  unpackSigner?: string;
  goodsName?: string;
  quantity: number;
  quantityUnit?: string;
  weight: string;
  status: string;
  videoStatus: string;
  ownerType: string;
  remark?: string;
}

interface WorkOrderDto {
  operationContext?: BusinessOperationContext;
  code: string;
  customer: string;
  material: string;
  process: string;
  processCategory?: string;
  productCategory?: string;
  machineNo?: string;
  workItemName?: string;
  startedAt?: string;
  finishedAt?: string;
  completionStatus?: string;
  handoffAuditStatus?: string;
  assignee: string;
  plannedQuantity: number;
  finishedQuantity: number;
  status: string;
  progress: number;
}

interface ProductionHandoffDto {
  code: string;
  fromNode: string;
  toNode: string;
  inputWeight: string;
  outputWeight: string;
  lossRate: string;
  status: string;
  tone: Tone;
}

interface InventoryLedgerEntryDto {
  time: string;
  code: string;
  movement: string;
  beforeBalance: string;
  afterBalance: string;
  sourceDocument: string;
  operator: string;
}

export interface InboundWorkspaceModel {
  dataSource?: 'LIVE' | 'DEMO';
  checkpoints: InboundCheckpoint[];
  orders: InboundOrder[];
  risks: InboundRisk[];
}

export interface ProductionWorkspaceModel {
  dataSource?: 'LIVE' | 'DEMO';
  stationLoads: ProductionStationLoad[];
  workOrders: WorkOrder[];
  handoffs: ProductionHandoff[];
}

export interface QualityWorkspaceModel {
  dataSource?: 'LIVE' | 'DEMO';
  metrics: QualityMetric[];
  cases: QualityCase[];
  lossApprovals: LossApproval[];
}

export interface InventoryWorkspaceModel {
  dataSource?: 'LIVE' | 'DEMO';
  balances: InventoryBalance[];
  ledgerEntries: InventoryLedgerEntry[];
  timeline: TraceEvent[];
  evidence: TraceEvidence[];
}

interface InboundWorkspaceDto {
  dataSource?: 'LIVE' | 'DEMO';
  checkpoints: ReviewStatDto[];
  orders: InboundOrderDto[];
  risks: ActionItemDto[];
}

interface ProductionWorkspaceDto {
  dataSource?: 'LIVE' | 'DEMO';
  stationLoads: ReviewStatDto[];
  workOrders: WorkOrderDto[];
  handoffs: ProductionHandoffDto[];
}

interface QualityWorkspaceDto {
  dataSource?: 'LIVE' | 'DEMO';
  metrics: QualityMetric[];
  cases: QualityCase[];
  lossApprovals: LossApproval[];
}

interface InventoryWorkspaceDto {
  dataSource?: 'LIVE' | 'DEMO';
  balances: Array<Omit<InventoryBalance, 'qty'> & { quantity: number | string; quantityUnit?: string }>;
  ledgerEntries: InventoryLedgerEntryDto[];
  timeline: TraceEvent[];
  evidence: ActionItemDto[];
}

function splitStatValue(value: string): [string, string] {
  const [left, right] = value.split('/').map((part) => part.trim());
  return [left || value, right || ''];
}

export function mapInboundWorkspace(dto: InboundWorkspaceDto): InboundWorkspaceModel {
  return {
    dataSource: dto.dataSource,
    checkpoints: dto.checkpoints.map((item) => ({
      label: item.label,
      value: item.value,
      status: item.detail,
      tone: item.tone,
    })),
    orders: dto.orders.map((order) => ({
      code: order.code,
      operationContext: order.operationContext,
      customer: order.customer,
      material: order.material,
      inboundDate: order.inboundDate ?? '',
      expressCompany: order.expressCompany ?? '',
      courierNo: order.courierNo,
      senderName: order.senderName ?? '',
      receiverName: order.receiverName ?? '',
      unpackSigner: order.unpackSigner ?? '',
      goodsName: order.goodsName ?? order.material,
      qty: order.quantity,
      quantityUnit: order.quantityUnit ?? '',
      weight: order.weight,
      status: order.status,
      video: order.videoStatus,
      owner: order.ownerType,
      remark: order.remark ?? '',
    })),
    risks: dto.risks,
  };
}

export function mapProductionWorkspace(dto: ProductionWorkspaceDto): ProductionWorkspaceModel {
  return {
    dataSource: dto.dataSource,
    stationLoads: dto.stationLoads.map((stat) => {
      const [queue, capacity] = splitStatValue(stat.value);
      return {
        station: stat.label,
        queue,
        capacity,
        bottleneck: stat.detail,
        tone: stat.tone,
      };
    }),
    workOrders: dto.workOrders.map((order) => ({
      code: order.code,
      operationContext: order.operationContext,
      customer: order.customer,
      material: order.material,
      process: order.process,
      processCategory: order.processCategory ?? '',
      productCategory: order.productCategory ?? '',
      machineNo: order.machineNo ?? '',
      workItemName: order.workItemName ?? order.process,
      startedAt: order.startedAt ?? '',
      finishedAt: order.finishedAt ?? '',
      completionStatus: order.completionStatus ?? order.status,
      handoffAuditStatus: order.handoffAuditStatus ?? '',
      assignee: order.assignee,
      plannedQty: order.plannedQuantity,
      finishedQty: order.finishedQuantity,
      status: order.status,
      progress: order.progress,
    })),
    handoffs: dto.handoffs.map((handoff) => ({
      code: handoff.code,
      from: handoff.fromNode,
      to: handoff.toNode,
      inputWeight: handoff.inputWeight,
      outputWeight: handoff.outputWeight,
      lossRate: handoff.lossRate,
      status: handoff.status,
      tone: handoff.tone,
    })),
  };
}

export function mapQualityWorkspace(dto: QualityWorkspaceDto): QualityWorkspaceModel {
  return dto;
}

export function mapInventoryWorkspace(dto: InventoryWorkspaceDto): InventoryWorkspaceModel {
  return {
    dataSource: dto.dataSource,
    balances: dto.balances.map((item) => ({
      operationContext: item.operationContext,
      owner: item.owner,
      batch: item.batch,
      material: item.material,
      location: item.location,
      status: item.status,
      qty: item.quantity,
      weight: item.weight,
      frozen: item.frozen,
      quantityUnit: item.quantityUnit ?? '',
    })),
    ledgerEntries: dto.ledgerEntries.map((entry) => ({
      time: entry.time,
      bizCode: entry.code,
      movement: entry.movement,
      before: entry.beforeBalance,
      after: entry.afterBalance,
      source: entry.sourceDocument,
      operator: entry.operator,
    })),
    timeline: dto.timeline,
    evidence: dto.evidence.map((item) => ({
      label: item.title,
      status: item.action,
      link: item.detail,
      tone: item.tone,
    })),
  };
}

export async function fetchInboundWorkspace(): Promise<InboundWorkspaceModel> {
  return mapInboundWorkspace(await apiGet<InboundWorkspaceDto>('/api/business/workspaces/inbound'));
}

export async function fetchProductionWorkspace(): Promise<ProductionWorkspaceModel> {
  return mapProductionWorkspace(await apiGet<ProductionWorkspaceDto>('/api/business/workspaces/production'));
}

export async function fetchQualityWorkspace(): Promise<QualityWorkspaceModel> {
  return mapQualityWorkspace(await apiGet<QualityWorkspaceDto>('/api/business/workspaces/quality'));
}

export async function fetchInventoryWorkspace(): Promise<InventoryWorkspaceModel> {
  return mapInventoryWorkspace(await apiGet<InventoryWorkspaceDto>('/api/business/workspaces/inventory'));
}
