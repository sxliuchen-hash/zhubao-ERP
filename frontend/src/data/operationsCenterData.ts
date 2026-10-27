import type { Tone } from './m1PrototypeData';

export interface OperationsCenterSummary {
  overallStatus: string;
  serviceCount: number;
  degradedServices: number;
  openErrors: number;
  criticalErrors: number;
  openAlerts: number;
  criticalAlerts: number;
  backupStatus: string;
  uptimeMinutes: number;
}

export interface OperationsServiceStatus {
  code: string;
  name: string;
  status: string;
  endpoint: string;
  detail: string;
  owner: string;
  checkedAt: string;
  tone: Tone;
}

export interface RuntimeMetric {
  code: string;
  label: string;
  value: string;
  unit: string;
  detail: string;
  tone: Tone;
}

export interface ErrorSummary {
  code: string;
  module: string;
  severity: string;
  count: number;
  latest: string;
  owner: string;
  nextAction: string;
  tone: Tone;
}

export interface RecentLogEntry {
  time: string;
  level: string;
  source: string;
  message: string;
  traceId: string;
  tone: Tone;
}

export interface BackupStatus {
  code: string;
  target: string;
  status: string;
  lastSuccessAt: string;
  nextDueAt: string;
  storage: string;
  fileSize: string;
  checksum: string;
  detail: string;
  tone: Tone;
}

export interface RestoreDrillCheck {
  name: string;
  status: string;
  detail: string;
  tone: Tone;
}

export interface RestoreDrillRecord {
  code: string;
  status: string;
  checkedAt: string;
  backupFile: string;
  backupChecksum: string;
  restoreDatabase: string;
  minimumTableCount: number;
  restoredTableCount: number;
  operator: string;
  durationSeconds: number;
  keepDatabase: boolean;
  evidenceFile: string;
  detail: string;
  tone: Tone;
  checks: RestoreDrillCheck[];
}

export interface OperationChecklistItem {
  code: string;
  item: string;
  status: string;
  evidence: string;
  owner: string;
  tone: Tone;
}

export interface OperationAlert {
  code: string;
  title: string;
  severity: string;
  source: string;
  status: string;
  owner: string;
  raisedAt: string;
  nextAction: string;
  evidence: string;
  tone: Tone;
}

export interface HardwarePresence {
  code: string;
  name: string;
  scene: string;
  status: string;
  protocol: string;
  detail: string;
  checkedAt: string;
  tone: Tone;
}

export interface StorageVolume {
  code: string;
  name: string;
  path: string;
  used: string;
  capacity: string;
  usedPercent: number;
  detail: string;
  tone: Tone;
}

export interface OperationsCenterSnapshot {
  generatedAt: string;
  summary: OperationsCenterSummary;
  services: OperationsServiceStatus[];
  runtimeMetrics: RuntimeMetric[];
  errorSummaries: ErrorSummary[];
  recentLogs: RecentLogEntry[];
  backupStatuses: BackupStatus[];
  restoreDrillRecords: RestoreDrillRecord[];
  alerts: OperationAlert[];
  checklist: OperationChecklistItem[];
  hardwareNodes: HardwarePresence[];
  storageVolumes: StorageVolume[];
}
