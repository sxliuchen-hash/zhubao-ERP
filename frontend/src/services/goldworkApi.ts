import { apiGet, apiPost } from './apiClient';

export interface GoldworkOrderView {
  goldworkCode: string;
  businessReference: string;
  customerId: number;
  customerName: string;
  materialIntent: string;
  materialIntentLabel: string;
  requirement: string;
  ownerName: string;
  routeStepIds: string[];
  routeStepLabels: string[];
  status: string;
  statusLabel: string;
  version: number;
  openedBy: string;
  openedOn: string;
}

export function fetchGoldworkOrders(customerId?: number): Promise<GoldworkOrderView[]> {
  const query = customerId == null ? '' : `?customerId=${customerId}`;
  return apiGet<GoldworkOrderView[]>(`/api/goldwork/orders${query}`);
}

export interface GoldworkMaterialLineView {
  lineId: string;
  goldworkCode: string;
  owner: string;
  sourceId: string;
  sourceLineNo: number | null;
  pieceCount: number | null;
  netWeight: number | null;
  grossWeight: number | null;
  weightUnit: string;
  purity: string;
  precisionState: string;
  postedToLedger: boolean;
}

export function receiveGoldworkMaterial(goldworkCode: string, body: {
  requestId: string;
  sourceId: string;
  sourceLineNo: number | null;
  pieceCount: number | null;
  netWeight: number | null;
  grossWeight: number | null;
  weightUnit: string;
  purity: string;
}): Promise<GoldworkMaterialLineView> {
  return apiPost(`/api/goldwork/orders/${encodeURIComponent(goldworkCode)}/materials`, body);
}

export interface GoldworkMovementView {
  movementId: string;
  goldworkCode: string;
  stepId: string;
  kind: string;
  sourceLineId: string;
  owner: string;
  pieceCount: number | null;
  netWeight: number | null;
  grossWeight: number | null;
  weightUnit: string;
  purity: string;
  disposition: string;
  conversionNote: string;
  precisionState: string;
  postedToLedger: boolean;
}

export function recordGoldworkMovement(goldworkCode: string, body: {
  requestId: string;
  stepId: string;
  kind: string;
  sourceLineId: string;
  pieceCount: number | null;
  netWeight: number | null;
  grossWeight: number | null;
  weightUnit: string;
  purity: string;
  disposition: string;
  conversionNote: string;
  useGrossAsNet: boolean;
  inferShortage: boolean;
  conversionFactor: number | null;
  enforcePieceConservation: boolean;
}): Promise<GoldworkMovementView> {
  return apiPost(`/api/goldwork/orders/${encodeURIComponent(goldworkCode)}/movements`, body);
}

export interface GoldworkRecoveryView {
  recoveryId: string;
  goldworkCode: string;
  sourceLineId: string;
  outputMovementId: string;
  kind: string;
  owner: string;
  netWeight: number | null;
  precisionState: string;
  postedToLedger: boolean;
  feeOffset: boolean;
}

export function recordGoldworkRecovery(goldworkCode: string, body: {
  requestId: string;
  sourceLineId: string;
  outputMovementId: string;
  kind: string;
  pieceCount: number | null;
  netWeight: number | null;
  grossWeight: number | null;
  weightUnit: string;
  purity: string;
  clearAsLoss: boolean;
  addToFinished: boolean;
  feeOffset: number | null;
  autoReturn: boolean;
  inferWeight: boolean;
}): Promise<GoldworkRecoveryView> {
  return apiPost(`/api/goldwork/orders/${encodeURIComponent(goldworkCode)}/recoveries`, body);
}

export interface GoldworkSupplementView {
  supplementId: string;
  goldworkCode: string;
  originalLineId: string;
  priorSupplementId: string;
  direction: string;
  owner: string;
  sourceId: string;
  netWeight: number | null;
  postedToLedger: boolean;
  feePosted: boolean;
}

export function issueGoldworkSupplement(goldworkCode: string, body: {
  requestId: string;
  originalLineId: string;
  sourceId: string;
  sourceLineNo: number | null;
  purpose: string;
  pieceCount: number | null;
  netWeight: number | null;
  grossWeight: number | null;
  weightUnit: string;
  purity: string;
  addOntoOriginal: boolean;
  changeOwner: boolean;
  feeAmount: number | null;
}): Promise<GoldworkSupplementView> {
  return apiPost(`/api/goldwork/orders/${encodeURIComponent(goldworkCode)}/supplements`, body);
}

export function returnGoldworkSupplement(goldworkCode: string, body: {
  requestId: string;
  originalLineId: string;
  priorSupplementId: string;
  purpose: string;
  pieceCount: number | null;
  netWeight: number | null;
  grossWeight: number | null;
  weightUnit: string;
  purity: string;
  addOntoOriginal: boolean;
  changeOwner: boolean;
  feeAmount: number | null;
}): Promise<GoldworkSupplementView> {
  return apiPost(`/api/goldwork/orders/${encodeURIComponent(goldworkCode)}/supplements/returns`, body);
}

export interface GoldworkOperationView {
  reportId: string;
  goldworkCode: string;
  stepId: string;
  stepLabel: string;
  netWeight: number | null;
  attachmentCode: string;
  postedToLedger: boolean;
  payrollPosted: boolean;
}

export function reportGoldworkOperation(goldworkCode: string, body: {
  requestId: string;
  stepId: string;
  pieceCount: number | null;
  netWeight: number | null;
  weightUnit: string;
  purity: string;
  attachmentCode: string;
  settleWage: boolean;
  postStock: boolean;
  closeWip: boolean;
}): Promise<GoldworkOperationView> {
  return apiPost(`/api/goldwork/orders/${encodeURIComponent(goldworkCode)}/operations`, body);
}

export interface GoldworkQualityView {
  qualityId: string;
  goldworkCode: string;
  reportId: string;
  priorQualityId: string;
  roundNo: number;
  outcome: string;
  netWeight: number | null;
  purity: string;
  note: string;
  purityChanged: boolean;
  feeChanged: boolean;
}

export function decideGoldworkQuality(goldworkCode: string, body: {
  requestId: string;
  reportId: string;
  priorQualityId: string;
  outcome: string;
  pieceCount: number | null;
  netWeight: number | null;
  weightUnit: string;
  purity: string;
  note: string;
  useBeadDefault: boolean;
  changePurity: boolean;
  changeFee: boolean;
}): Promise<GoldworkQualityView> {
  return apiPost(`/api/goldwork/orders/${encodeURIComponent(goldworkCode)}/qualities`, body);
}

export interface GoldworkDeliveryView {
  deliveryId: string;
  goldworkCode: string;
  qualityId: string;
  netWeight: number | null;
  purity: string;
  feeState: string;
  receivablePosted: boolean;
  salesPosted: boolean;
  ordinaryShipment: boolean;
}

export function deliverGoldwork(goldworkCode: string, body: {
  requestId: string;
  qualityId: string;
  pieceCount: number | null;
  netWeight: number | null;
  weightUnit: string;
  goldPrice: number | null;
  amount: number | null;
  shipAsProcessing: boolean;
  countCustomerGoldAsSales: boolean;
}): Promise<GoldworkDeliveryView> {
  return apiPost(`/api/goldwork/orders/${encodeURIComponent(goldworkCode)}/deliveries`, body);
}

export function openGoldworkOrder(body: {
  requestId: string;
  customerId: number;
  materialIntent: string;
  requirement: string;
  ownerName: string;
  routeSteps?: string;
}): Promise<GoldworkOrderView> {
  return apiPost('/api/goldwork/orders', body);
}
