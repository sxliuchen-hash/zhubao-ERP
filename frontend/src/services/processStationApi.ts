import { apiGet, apiPost } from './apiClient';

export interface ProcessStationCard {
  processName: string;
  requirement: string;
  dueDate: string;
  previousProcessName: string;
  previousSubmitted: boolean;
  submitted: boolean;
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
): Promise<ProcessStationReport> {
  return apiPost<{ code: string; process: string; requestId?: string }, ProcessStationReport>(
    '/api/business/operations/process-stations',
    { code, process, ...(requestId ? { requestId } : {}) },
  );
}
