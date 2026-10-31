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
