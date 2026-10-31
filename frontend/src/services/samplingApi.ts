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

export interface SamplingMaterialUseView {
  useId: string;
  samplingCode: string;
  owner: string;
  sourceId: string;
  sourceLineNo: number | null;
  stockMoveId: string;
  materialCode: string;
  batchCode: string;
  quantity: number;
  unit: string;
  weightState: string;
  weightValue: number | null;
  returnedQuantity: number;
  inputQuantity: number | null;
  version: number;
}

export function pickSamplingMaterial(samplingCode: string, body: {
  requestId: string;
  receiptId: string;
  sourceDocumentCode: string;
  lineNo: number | null;
  workshopCode: string;
  quantity: number;
  weight: number | null;
  unit: string;
}): Promise<SamplingMaterialUseView> {
  return apiPost(`/api/sampling/orders/${encodeURIComponent(samplingCode)}/material-uses`, body);
}

export interface SamplingOperationView {
  reportId: string;
  samplingCode: string;
  stepId: string;
  stepName: string;
  versionKind: string;
  versionNo: number;
  makingChoice: string;
  makingChoiceLabel: string;
  quantity: number;
  unit: string;
  attachmentCode: string;
  workerName: string;
  reportedOn: string;
}

export function reportSamplingOperation(samplingCode: string, body: {
  requestId: string;
  stepId: string;
  versionKind: string;
  versionNo: number;
  makingChoices: string[];
  quantity: number;
  unit: string;
  attachmentCode: string;
}): Promise<SamplingOperationView> {
  return apiPost(`/api/sampling/orders/${encodeURIComponent(samplingCode)}/operations`, body);
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
