import { apiGet, apiPost } from './apiClient';

export interface ProcessStationCard {
  processName: string;
  requirement: string;
  dueDate: string;
  previousProcessName: string;
  previousSubmitted: boolean;
  submitted: boolean;
  totalQuantity?: number;
  deliveredQuantity?: number;
  availableQuantity?: number;
  finalProcess?: boolean;
  batches?: Array<{ batchCode: string; quantity: number; finalWeight: number | null; operator: string; createdAt: string }>;
}

export interface ProcessStationView {
  orderCode: string;
  customerName: string;
  dueDate: string;
  processingRequirement: string;
  stations: ProcessStationCard[];
  message: string;
}

export interface ProcessStationReport {
  orderCode: string;
  processName: string;
  ledgerCode: string;
  movedToSemiFinished: boolean;
  stockStatus: string;
  batchCode?: string;
  batchQuantity?: number;
  deliveredQuantity?: number;
  remainingQuantity?: number;
  processCompleted?: boolean;
  finalWeight?: number | null;
}

export function lookupProcessStation(code: string): Promise<ProcessStationView> {
  return apiGet<ProcessStationView>(
    `/api/business/operations/process-stations?code=${encodeURIComponent(code)}`,
  );
}

export function submitProcessStation(
  code: string,
  process: string,
  requestId?: string,
  batch?: { quantity: string; finalWeight?: string },
): Promise<ProcessStationReport> {
  return apiPost<{ code: string; process: string; requestId?: string; quantity?: string; finalWeight?: string }, ProcessStationReport>(
    '/api/business/operations/process-stations',
    { code, process, ...(requestId ? { requestId } : {}), ...batch },
  );
}
