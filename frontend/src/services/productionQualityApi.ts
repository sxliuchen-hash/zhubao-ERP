import { apiGet, apiPost } from './apiClient';

export interface BagAvailability {
  bagId: string;
  targetCode: string;
  stepId: string;
  stepName: string;
  availableQuantity: number;
  nextStepId: string;
  nextStepName: string;
  blockingReasons: string[];
}

export interface WholeBagReportBody {
  requestId: string;
  bagId: string;
  bagVersion: number;
  quantity: { confirmedQuantity: number };
  attachmentCode: string;
  progressPercent: number | null;
}

export interface WholeBagReportResponse {
  batchCode: string;
  requestId: string;
  bagId: string;
  stepId: string;
  quantity: number;
}

export function fetchBagAvailability(bagId: string): Promise<BagAvailability> {
  return apiGet<BagAvailability>(`/api/business/production-quality/bags/${encodeURIComponent(bagId)}/availability`);
}

export function submitWholeBagReport(body: WholeBagReportBody): Promise<WholeBagReportResponse> {
  return apiPost<WholeBagReportBody, WholeBagReportResponse>('/api/business/production-quality/whole-bag-reports', body);
}

export interface PortionQualityView {
  bagId: string;
  quantity: number;
  clauses: string[];
  released: number;
  frozen: number;
  reworkBagId: string;
  blockingReasons: string[];
}

export function fetchPortionQuality(bagId: string): Promise<PortionQualityView> {
  return apiGet<PortionQualityView>(`/api/business/production-quality/bags/${encodeURIComponent(bagId)}/shippable`);
}

export function submitPortionInspection(body: {
  requestId: string;
  bagId: string;
  bagVersion: number;
  requirementVersion: string;
  pass: number;
  rework: number;
  scrap: number;
  pending: number;
  failedClauses: string[];
}): Promise<{ inspectionId: string }> {
  return apiPost('/api/business/production-quality/inspections', body);
}
