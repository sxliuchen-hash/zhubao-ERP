import type { HardwarePocRecord, Tone } from '../data/m1PrototypeData';
import { apiGet } from './apiClient';

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
  pocRecords: number;
  pocBlocked: number;
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

export interface HardwareWorkspaceModel {
  pocRecords: HardwarePocRecord[];
  pocEvidence: HardwarePocEvidenceSummary[];
  summary: HardwareSummaryDto;
}

export async function fetchHardwareWorkspace(): Promise<HardwareWorkspaceModel> {
  const [pocRecords, pocEvidence, summary] = await Promise.all([
    apiGet<HardwarePocRecordDto[]>('/api/hardware/poc-records'),
    apiGet<HardwarePocEvidenceSummary[]>('/api/hardware/poc-evidence'),
    apiGet<HardwareSummaryDto>('/api/hardware/summary'),
  ]);

  return {
    pocRecords: pocRecords.map(mapPocRecord),
    pocEvidence,
    summary,
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
