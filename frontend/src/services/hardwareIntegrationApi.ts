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

export interface HardwareWorkspaceModel {
  devices: HardwareDevice[];
  tasks: IntegrationTask[];
  pocRecords: HardwarePocRecord[];
  summary: HardwareSummaryDto;
}

export async function fetchHardwareWorkspace(): Promise<HardwareWorkspaceModel> {
  const [devices, tasks, pocRecords, summary] = await Promise.all([
    apiGet<HardwareDeviceDto[]>('/api/hardware/devices'),
    apiGet<IntegrationTask[]>('/api/hardware/integration-tasks'),
    apiGet<HardwarePocRecordDto[]>('/api/hardware/poc-records'),
    apiGet<HardwareSummaryDto>('/api/hardware/summary'),
  ]);

  return {
    devices: devices.map(mapDevice),
    tasks,
    pocRecords: pocRecords.map(mapPocRecord),
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
