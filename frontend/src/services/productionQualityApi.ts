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

export interface ShippablePortion {
  bagId: string;
  quantity: number;
  blockingReasons: string[];
}

export function fetchShippablePortion(bagId: string): Promise<ShippablePortion> {
  return apiGet<ShippablePortion>(`/api/business/production-quality/bags/${encodeURIComponent(bagId)}/shippable`);
}

export function submitPortionRelease(body: { requestId: string; bagId: string; bagVersion: number; quantity: number }): Promise<{ released: number; frozen: number }> {
  return apiPost('/api/business/production-quality/releases', body);
}

export function submitPortionRework(body: { requestId: string; bagId: string; bagVersion: number; round: number }): Promise<{ reworkBagId: string; targetCode: string }> {
  return apiPost('/api/business/production-quality/reworks', body);
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
