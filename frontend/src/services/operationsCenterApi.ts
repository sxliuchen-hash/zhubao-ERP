import type {
  OperationsCenterSnapshot,
  OperationsServiceStatus,
  RestoreDrillCheck,
  RestoreDrillRecord,
} from '../data/operationsCenterData';
import type { Tone } from '../data/m1PrototypeData';
import { apiGet } from './apiClient';

type OperationsServiceStatusDto = Omit<OperationsServiceStatus, 'tone'> & { tone: string };
type RestoreDrillRecordDto = Omit<RestoreDrillRecord, 'tone' | 'checks'> & {
  tone: string;
  checks: Array<Omit<RestoreDrillCheck, 'tone'> & { tone: string }>;
};
type OperationsCenterSnapshotDto = Omit<OperationsCenterSnapshot, 'services' | 'restoreDrillRecords'> & {
  services: OperationsServiceStatusDto[];
  restoreDrillRecords?: RestoreDrillRecordDto[];
};

export async function fetchOperationsCenterSnapshot(): Promise<OperationsCenterSnapshot> {
  const snapshot = await apiGet<OperationsCenterSnapshotDto>('/api/operations/center');
  return {
    ...snapshot,
    services: snapshot.services.map((service) => ({
      ...service,
      tone: mapTone(service.tone),
    })),
    runtimeMetrics: snapshot.runtimeMetrics.map((metric) => ({ ...metric, tone: mapTone(metric.tone) })),
    errorSummaries: snapshot.errorSummaries.map((error) => ({ ...error, tone: mapTone(error.tone) })),
    recentLogs: snapshot.recentLogs.map((entry) => ({ ...entry, tone: mapTone(entry.tone) })),
    backupStatuses: snapshot.backupStatuses.map((backup) => ({ ...backup, tone: mapTone(backup.tone) })),
    restoreDrillRecords: (snapshot.restoreDrillRecords ?? []).map((record) => ({
      ...record,
      tone: mapTone(record.tone),
      checks: record.checks.map((check) => ({ ...check, tone: mapTone(check.tone) })),
    })),
    checklist: snapshot.checklist.map((item) => ({ ...item, tone: mapTone(item.tone) })),
  };
}

function mapTone(value: string): Tone {
  if (['blue', 'green', 'amber', 'red', 'violet', 'gray'].includes(value)) {
    return value as Tone;
  }
  return 'gray';
}
