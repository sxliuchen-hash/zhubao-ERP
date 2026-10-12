import { apiGet, apiPost } from './apiClient';

export interface QualityCheckLine {
  lineNo: number;
  clause: string;
  verdict: string;
  reason: string;
}

export interface QualityRoundView {
  roundCode: string;
  roundNo: number;
  roundType: string;
  reworkOrderCode: string;
  reworkReportLedger: string;
  outcome: string;
  frozen: boolean;
  ledgerCode: string;
  inspector: string;
  createdAt: string;
  lines: QualityCheckLine[];
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
  roundCode?: string;
  roundNo?: number;
  roundType?: string;
  reworkReported?: boolean;
  canRecheck?: boolean;
  recheckBlock?: string;
  canRelease?: boolean;
  releaseBlock?: string;
  rounds?: QualityRoundView[];
}

export interface QualityCheckSubmitLine {
  lineNo: number;
  verdict: string;
  reason: string;
}

export interface QualityCheckSubmit {
  code: string;
  lines: QualityCheckSubmitLine[];
  requestId?: string;
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
  roundCode?: string;
  roundNo?: number;
  roundType?: string;
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

export function openQualityRework(code: string, requestId?: string): Promise<QualityReworkResult> {
  return apiPost<{ code: string; requestId?: string }, QualityReworkResult>(
    '/api/business/operations/quality-checks/rework',
    { code, ...(requestId ? { requestId } : {}) },
  );
}

export function reportQualityRework(code: string, requestId?: string): Promise<{ ledgerCode: string; reworkOrderCode: string }> {
  return apiPost<{ code: string; requestId?: string }, { ledgerCode: string; reworkOrderCode: string }>(
    '/api/business/operations/quality-checks/rework-reports',
    { code, ...(requestId ? { requestId } : {}) },
  );
}

export function submitQualityRecheck(body: QualityCheckSubmit): Promise<QualityCheckResult> {
  return apiPost<QualityCheckSubmit, QualityCheckResult>('/api/business/operations/quality-checks/rechecks', body);
}

export function releaseQualityRound(code: string, requestId?: string): Promise<{ ledgerCode: string; releaseResult: string }> {
  return apiPost<{ code: string; requestId?: string }, { ledgerCode: string; releaseResult: string }>(
    '/api/business/operations/quality-checks/releases',
    { code, ...(requestId ? { requestId } : {}) },
  );
}
