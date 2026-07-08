import type { HardwareDevice, HardwarePocRecord, IntegrationTask, Tone } from '../data/m1PrototypeData';
import { apiGet } from './apiClient';

interface HardwareDeviceDto {
  code: string;
  name: string;
  scene: string;
  protocol: string;
  status: 'ONLINE' | 'DEGRADED' | 'MANUAL';
  fallback: string;
}

interface HardwarePocRecordDto {
  code: string;
  deviceCode: string;
  scene: string;
  testWindow: string;
  result: string;
  evidence: string;
  blocker: string;
  owner: string;
  nextAction: string;
}

interface HardwareSummaryDto {
  totalDevices: number;
  fallbackRequired: number;
  averageHealth: number;
  pocRecords: number;
  pocBlocked: number;
}

export interface HardwareAdapterPlan {
  deviceCode: string;
  deviceName: string;
  phase: string;
  primaryAdapter: string;
  fallbackAdapter: string;
  evidenceRequirement: string;
  nextIntegrationAction: string;
  riskLevel: string;
  tone: Tone;
}

export interface HardwarePocEvidenceSummary {
  pocCode: string;
  scene: string;
  result: string;
  documentCode: string;
  attachmentCount: number;
  missingEvidence: number;
  attachmentCodes: string[];
  latestUploadedAt: string;
  status: string;
  nextAction: string;
  tone: Tone;
}

export interface HardwareProtocolSample {
  deviceCode: string;
  deviceName: string;
  protocolType: string;
  sampleStatus: string;
  requiredParameters: string[];
  samplePayload: string;
  evidenceCode: string;
  captureMethod: string;
  nextReviewAction: string;
  riskLevel: string;
  tone: Tone;
}

export interface HardwareProtocolReviewItem {
  reviewCode: string;
  deviceCode: string;
  deviceName: string;
  protocolType: string;
  reviewStatus: string;
  deviceModelStatus: string;
  confirmedParameters: string[];
  pendingParameters: string[];
  evidenceCode: string;
  owner: string;
  reviewWindow: string;
  blocker: string;
  nextAction: string;
  riskLevel: string;
  tone: Tone;
}

export interface HardwareProtocolExecutionResult {
  executionCode: string;
  reviewCode: string;
  deviceCode: string;
  deviceName: string;
  protocolType: string;
  executionStatus: string;
  executedAt: string;
  executedBy: string;
  deviceModelSnapshot: string;
  modelFreezeStatus: string;
  acceptedParameters: string[];
  failedParameters: string[];
  evidenceCode: string;
  evidenceStatus: string;
  resultSummary: string;
  nextAction: string;
  riskLevel: string;
  tone: Tone;
}

export interface HardwareProtocolSignoffReadiness {
  signoffCode: string;
  executionCode: string;
  deviceCode: string;
  deviceName: string;
  signoffStatus: string;
  requiredAttachments: string[];
  missingAttachments: string[];
  signerRoles: string[];
  signedBy: string;
  signedAt: string;
  modelFreezeDecision: string;
  rolloutGate: string;
  closureSummary: string;
  nextAction: string;
  riskLevel: string;
  tone: Tone;
}

export interface HardwareWorkspaceModel {
  devices: HardwareDevice[];
  tasks: IntegrationTask[];
  adapterPlans: HardwareAdapterPlan[];
  protocolSamples: HardwareProtocolSample[];
  protocolReviews: HardwareProtocolReviewItem[];
  protocolExecutions: HardwareProtocolExecutionResult[];
  protocolSignoffs: HardwareProtocolSignoffReadiness[];
  pocRecords: HardwarePocRecord[];
  pocEvidence: HardwarePocEvidenceSummary[];
  summary: HardwareSummaryDto;
}

export async function fetchHardwareWorkspace(): Promise<HardwareWorkspaceModel> {
  const [
    devices,
    tasks,
    adapterPlans,
    protocolSamples,
    protocolReviews,
    protocolExecutions,
    protocolSignoffs,
    pocRecords,
    pocEvidence,
    summary,
  ] = await Promise.all([
    apiGet<HardwareDeviceDto[]>('/api/hardware/devices'),
    apiGet<IntegrationTask[]>('/api/hardware/integration-tasks'),
    apiGet<HardwareAdapterPlan[]>('/api/hardware/adapter-plans'),
    apiGet<HardwareProtocolSample[]>('/api/hardware/protocol-samples'),
    apiGet<HardwareProtocolReviewItem[]>('/api/hardware/protocol-reviews'),
    apiGet<HardwareProtocolExecutionResult[]>('/api/hardware/protocol-executions'),
    apiGet<HardwareProtocolSignoffReadiness[]>('/api/hardware/protocol-signoffs'),
    apiGet<HardwarePocRecordDto[]>('/api/hardware/poc-records'),
    apiGet<HardwarePocEvidenceSummary[]>('/api/hardware/poc-evidence'),
    apiGet<HardwareSummaryDto>('/api/hardware/summary'),
  ]);

  return {
    devices: devices.map(mapDevice),
    tasks,
    adapterPlans,
    protocolSamples,
    protocolReviews,
    protocolExecutions,
    protocolSignoffs,
    pocRecords: pocRecords.map(mapPocRecord),
    pocEvidence,
    summary,
  };
}

export function mapDevice(device: HardwareDeviceDto): HardwareDevice {
  return {
    ...device,
    status: device.status.toLowerCase() as HardwareDevice['status'],
  };
}

export function mapPocRecord(record: HardwarePocRecordDto): HardwarePocRecord {
  return {
    ...record,
    tone: pocTone(record.result),
  };
}

function pocTone(result: string): Tone {
  if (result.includes('阻断')) return 'red';
  if (result.includes('有条件')) return 'amber';
  return 'green';
}
