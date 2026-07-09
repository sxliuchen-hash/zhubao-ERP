import { apiGet, apiPost } from './apiClient';

export interface MachineOutputRecord {
  code: string;
  workDate: string;
  customerId?: number;
  customerNameSnapshot: string;
  productCategory: string;
  machineId?: number;
  machineNoSnapshot: string;
  workOrderCode: string;
  workProcessId?: number;
  workReportCode: string;
  outputQuantity: number | string;
  quantityUnit: string;
  operatorEmployeeId?: number;
  operatorSignatureName: string;
  signatureMediaId?: number;
  sourceRegisterType: string;
  sourceLineNo?: number;
  machineLineNo: string;
  sourceSequenceNo: string;
  importBatchNo: string;
  ledgerCode: string;
  status: string;
  remark: string;
  createdBy: string;
  createdAt: string;
}

export interface MachineOutputMachineSummary {
  machineNoSnapshot: string;
  quantity: number | string;
  records: number;
}

export interface MachineOutputWorkDateSummary {
  workDate: string;
  quantity: number | string;
  records: number;
}

export interface MachineOutputSummary {
  totalRecords: number;
  totalQuantity: number | string;
  quantityUnit: string;
  byMachine: MachineOutputMachineSummary[];
  byWorkDate: MachineOutputWorkDateSummary[];
  unmatchedMachineRecords: number;
  signatureMissingRecords: number;
}

export interface MachineOutputMachineMatchSuggestion {
  machineNoSnapshot: string;
  productCategory: string;
  candidateMachineIds: number[];
  matchedRecords: number;
  unmatchedRecords: number;
  totalRecords: number;
  latestWorkDate: string;
  sourceRegisterType: string;
  sourceMachineLineNo: string;
  readiness: 'READY_TO_BIND' | 'NEEDS_MASTER_DATA' | 'CONFLICT';
  suggestedAction: string;
}

export interface SubmitMachineOutputRequest {
  code?: string;
  workDate: string;
  customerId?: number;
  customerNameSnapshot?: string;
  productCategory: string;
  machineId?: number;
  machineNoSnapshot: string;
  workOrderCode?: string;
  workProcessId?: number;
  workReportCode?: string;
  outputQuantity: number | string;
  quantityUnit?: string;
  operatorEmployeeId?: number;
  operatorSignatureName: string;
  signatureMediaId?: number;
  sourceRegisterType?: string;
  sourceLineNo?: number;
  machineLineNo?: string;
  sourceSequenceNo?: string;
  importBatchNo?: string;
  ledgerCode?: string;
  status?: string;
  remark?: string;
  operator?: string;
}

export async function fetchMachineOutputs(limit = 20): Promise<MachineOutputRecord[]> {
  return apiGet<MachineOutputRecord[]>(`/api/machine-outputs?limit=${limit}`);
}

export async function fetchMachineOutputSummary(limit = 100): Promise<MachineOutputSummary> {
  return apiGet<MachineOutputSummary>(`/api/machine-outputs/summary?limit=${limit}`);
}

export async function fetchMachineOutputMachineMatchSuggestions(
  limit = 100,
): Promise<MachineOutputMachineMatchSuggestion[]> {
  return apiGet<MachineOutputMachineMatchSuggestion[]>(
    `/api/machine-outputs/machine-match-suggestions?limit=${limit}`,
  );
}

export async function fetchMachineOutputsByImportBatch(importBatchNo: string): Promise<MachineOutputRecord[]> {
  return apiGet<MachineOutputRecord[]>(
    `/api/machine-outputs/import-batches/${encodeURIComponent(importBatchNo)}`,
  );
}

export async function submitMachineOutput(request: SubmitMachineOutputRequest): Promise<MachineOutputRecord> {
  return apiPost<SubmitMachineOutputRequest, MachineOutputRecord>('/api/machine-outputs', request);
}
