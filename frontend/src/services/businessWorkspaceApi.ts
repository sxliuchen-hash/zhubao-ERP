import type {
  InboundCheckpoint,
  InboundOrder,
  InboundRisk,
  InventoryBalance,
  InventoryLedgerEntry,
  LossApproval,
  MobileCapability,
  MobileTask,
  OfflineDraft,
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
  code: string;
  customer: string;
  material: string;
  courierNo: string;
  quantity: number;
  weight: string;
  status: string;
  videoStatus: string;
  ownerType: string;
}

interface WorkOrderDto {
  code: string;
  customer: string;
  material: string;
  process: string;
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
  checkpoints: InboundCheckpoint[];
  orders: InboundOrder[];
  risks: InboundRisk[];
}

export interface ProductionWorkspaceModel {
  stationLoads: ProductionStationLoad[];
  workOrders: WorkOrder[];
  handoffs: ProductionHandoff[];
}

export interface QualityWorkspaceModel {
  metrics: QualityMetric[];
  cases: QualityCase[];
  lossApprovals: LossApproval[];
}

export interface InventoryWorkspaceModel {
  balances: InventoryBalance[];
  ledgerEntries: InventoryLedgerEntry[];
  timeline: TraceEvent[];
  evidence: TraceEvidence[];
}

export interface MobileWorkspaceModel {
  tasks: MobileTask[];
  capabilities: MobileCapability[];
  offlineDrafts: OfflineDraft[];
}

interface InboundWorkspaceDto {
  checkpoints: ReviewStatDto[];
  orders: InboundOrderDto[];
  risks: ActionItemDto[];
}

interface ProductionWorkspaceDto {
  stationLoads: ReviewStatDto[];
  workOrders: WorkOrderDto[];
  handoffs: ProductionHandoffDto[];
}

interface QualityWorkspaceDto {
  metrics: QualityMetric[];
  cases: QualityCase[];
  lossApprovals: LossApproval[];
}

interface InventoryWorkspaceDto {
  balances: Array<Omit<InventoryBalance, 'qty'> & { quantity: number }>;
  ledgerEntries: InventoryLedgerEntryDto[];
  timeline: TraceEvent[];
  evidence: ActionItemDto[];
}

interface MobileWorkspaceDto {
  tasks: MobileTask[];
  capabilities: ReviewStatDto[];
  offlineDrafts: ActionItemDto[];
}

function splitStatValue(value: string): [string, string] {
  const [left, right] = value.split('/').map((part) => part.trim());
  return [left || value, right || ''];
}

export function mapInboundWorkspace(dto: InboundWorkspaceDto): InboundWorkspaceModel {
  return {
    checkpoints: dto.checkpoints.map((item) => ({
      label: item.label,
      value: item.value,
      status: item.detail,
      tone: item.tone,
    })),
    orders: dto.orders.map((order) => ({
      code: order.code,
      customer: order.customer,
      material: order.material,
      courierNo: order.courierNo,
      qty: order.quantity,
      weight: order.weight,
      status: order.status,
      video: order.videoStatus,
      owner: order.ownerType,
    })),
    risks: dto.risks,
  };
}

export function mapProductionWorkspace(dto: ProductionWorkspaceDto): ProductionWorkspaceModel {
  return {
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
      customer: order.customer,
      material: order.material,
      process: order.process,
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
    balances: dto.balances.map((item) => ({
      owner: item.owner,
      batch: item.batch,
      material: item.material,
      location: item.location,
      status: item.status,
      qty: item.quantity,
      weight: item.weight,
      frozen: item.frozen,
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

export function mapMobileWorkspace(dto: MobileWorkspaceDto): MobileWorkspaceModel {
  return {
    tasks: dto.tasks,
    capabilities: dto.capabilities.map((item) => ({
      label: item.label,
      status: item.value,
      detail: item.detail,
      tone: item.tone,
    })),
    offlineDrafts: dto.offlineDrafts.map((item) => ({
      code: item.code,
      scene: item.title,
      savedAt: item.action,
      risk: item.detail,
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

export async function fetchMobileWorkspace(): Promise<MobileWorkspaceModel> {
  return mapMobileWorkspace(await apiGet<MobileWorkspaceDto>('/api/business/workspaces/mobile'));
}
