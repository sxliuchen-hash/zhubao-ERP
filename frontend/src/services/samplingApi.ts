import { apiGet, apiPost } from './apiClient';

export interface SamplingOrderView {
  samplingCode: string;
  businessReference: string;
  customerId: number;
  customerName: string;
  materialIntent: string;
  materialIntentLabel: string;
  requirement: string;
  ownerName: string;
  status: string;
  statusLabel: string;
  version: number;
  openedBy: string;
  openedOn: string;
}

export function fetchSamplingOrders(customerId?: number): Promise<SamplingOrderView[]> {
  const query = customerId == null ? '' : `?customerId=${customerId}`;
  return apiGet<SamplingOrderView[]>(`/api/sampling/orders${query}`);
}

export interface SamplingVersionView {
  samplingCode: string;
  kind: string;
  kindLabel: string;
  versionNo: number;
  parameterText: string;
  evidenceCode: string;
  modelFileName: string;
  modelFormat: string;
  modelVersion: string;
  modelLocationNote: string;
  modelEvidenceCode: string;
  modelReferenced: boolean;
  sourceCustody: string;
  backupClaim: string;
  decision: string;
  decisionLabel: string;
  decisionReason: string;
  decisionCredential: string;
  decidedBy: string;
  decidedOn: string;
}

export function fetchSamplingVersions(samplingCode: string): Promise<SamplingVersionView[]> {
  return apiGet<SamplingVersionView[]>(`/api/sampling/orders/${encodeURIComponent(samplingCode)}/versions`);
}

export function addSamplingVersion(samplingCode: string, body: {
  requestId: string;
  kind: string;
  parameterText: string;
  evidenceCode: string;
  modelFileName: string;
  modelFormat: string;
  modelVersion: string;
  modelLocationNote: string;
  modelEvidenceCode: string;
}): Promise<SamplingVersionView> {
  return apiPost(`/api/sampling/orders/${encodeURIComponent(samplingCode)}/versions`, body);
}

export function confirmSamplingVersion(samplingCode: string, body: {
  requestId: string;
  kind: string;
  versionNo: number;
  note: string;
  credentialCode: string;
}): Promise<SamplingVersionView> {
  return apiPost(`/api/sampling/orders/${encodeURIComponent(samplingCode)}/versions/confirmations`, body);
}

export function rejectSamplingVersion(samplingCode: string, body: {
  requestId: string;
  kind: string;
  versionNo: number;
  note: string;
  credentialCode: string;
}): Promise<SamplingVersionView> {
  return apiPost(`/api/sampling/orders/${encodeURIComponent(samplingCode)}/versions/rejections`, body);
}

export function openSamplingOrder(body: {
  requestId: string;
  customerId: number;
  materialIntent: string;
  requirement: string;
  ownerName: string;
}): Promise<SamplingOrderView> {
  return apiPost('/api/sampling/orders', body);
}
