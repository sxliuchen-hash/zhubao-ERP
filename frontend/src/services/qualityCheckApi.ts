import { apiGet, apiPost } from './apiClient';

export interface QualityCheckLine {
  lineNo: number;
  clause: string;
  verdict: string;
  reason: string;
}

export interface QualityCheckView {
  orderCode: string;
  motherOrderCode: string;
  customerName: string;
  dueDate: string;
  processingRequirement: string;
  ready: boolean;
  blockReason: string;
  inspector: string;
  qualityCaseCode: string;
  outcome: string;
  frozen: boolean;
  reworkOrderCode: string;
  lines: QualityCheckLine[];
}

export interface QualityCheckSubmitLine {
  lineNo: number;
  verdict: string;
  reason: string;
}

export interface QualityCheckSubmit {
  code: string;
  lines: QualityCheckSubmitLine[];
}

export interface QualityCheckResult {
  qualityCaseCode: string;
  orderCode: string;
  motherOrderCode: string;
  inspector: string;
  outcome: string;
  frozen: boolean;
  ledgerCode: string;
  reworkOrderCode: string;
  lines: QualityCheckLine[];
}

export interface QualityReworkResult {
  reworkOrderCode: string;
  qualityCaseCode: string;
  orderCode: string;
  motherOrderCode: string;
  ledgerCode: string;
  fromStockStatus: string;
  toStockStatus: string;
}

export function lookupQualityCheck(code: string): Promise<QualityCheckView> {
  return apiGet<QualityCheckView>(
    `/api/business/operations/quality-checks?code=${encodeURIComponent(code)}`,
  );
}

export function submitQualityCheck(body: QualityCheckSubmit): Promise<QualityCheckResult> {
  return apiPost<QualityCheckSubmit, QualityCheckResult>('/api/business/operations/quality-checks', body);
}

export function openQualityRework(code: string): Promise<QualityReworkResult> {
  return apiPost<{ code: string }, QualityReworkResult>(
    '/api/business/operations/quality-checks/rework',
    { code },
  );
}
