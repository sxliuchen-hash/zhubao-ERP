import { apiGet, apiPost } from './apiClient';

export interface BagAvailability {
  bagId: string;
  bagVersion: number;
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
  expectedStepId: string;
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

export interface QuantityDispositionBody {
  requestId: string; bagId: string; bagVersion: number; handoff: number; loss: number;
  scrap: number; returned: number; pending: number; attachmentCode: string;
  exitWeight: { state: 'UNKNOWN'; value: null }; stockFrom: null; workQuantity: number;
}
export function submitQuantityDisposition(body: QuantityDispositionBody): Promise<{ handoffBagId: string; remainderBagId: string }> {
  return apiPost('/api/business/production-quality/dispositions', body);
}

export interface ActivityBag { id: string; targetCode: string; version: number; quantity: number; requirementVersion: string; materialCode: string; sourceKey: string; weight: { state: string; value: number | null } }
export interface ProductionTargetView { targetCode: string; customerId: number; quantityUnit: string; requirement: string; defaultSteps: { id: string; name: string }[]; route: { stepIds: string[]; version: number } | null; hasExecution?: boolean; bags: { bag: ActivityBag; availability: BagAvailability; shippable: ShippablePortion }[] }
export interface BagDetail { bag: ActivityBag; inspections: { pass: number; rework: number; scrap: number; pending: number }[]; hold: { released: number; frozen: number }; weight: { state: string; value: number | null } }
export const fetchProductionTarget = (target: string) => apiGet<ProductionTargetView>(`/api/business/production-targets/${encodeURIComponent(target)}`);
export const fetchBagDetail = (id: string) => apiGet<BagDetail>(`/api/business/production-targets/bags/${encodeURIComponent(id)}`);
export const startProductionTarget = (target: string, body: { requestId: string; steps: string[] }) => apiPost<typeof body, ProductionTargetView>(`/api/business/production-targets/${encodeURIComponent(target)}/starts`, body);
export const finishBagStock = (id: string, body: { requestId: string; version: number }) => apiPost(`/api/business/production-targets/bags/${encodeURIComponent(id)}/stock-releases`, body);
export const splitActivityBag = (id: string, body: { requestId: string; quantities: number[] }) => apiPost<typeof body, string>(`/api/business/production-targets/bags/${encodeURIComponent(id)}/splits`, body);
export const weighActivityBag = (body: { requestId: string; bagId: string; bagVersion: number; weight: string }) => apiPost('/api/business/production-quality/weights', body);

export const changeBagFreeze = (action: 'freezes' | 'unfreezes', body: { requestId: string; bagId: string; bagVersion: number; quantity: number }) => apiPost(`/api/business/production-quality/${action}`, body);
