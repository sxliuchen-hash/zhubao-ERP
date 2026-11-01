import { apiGet, apiPost } from './apiClient';

export interface OutsourcingSourceLine {
  bagId: string;
  quantity: number;
  version: number;
  holder: string;
  inHouseStock: boolean;
  sourceKey: string;
}

export interface OutsourcingDispatchLine {
  dispatchId: string;
  version: number;
  quantity: number;
  stillOut: number;
}

export interface OutsourcingReturnLine {
  dispatchId: string;
  receivedQty: number;
  unprocessedQty: number;
  lossQty: number;
  stillOut: number;
}

export interface OutsourcingPosition {
  taskId: string;
  targetCode: string;
  nodeId: string;
  partyId: string;
  partyName: string;
  ownerType: string;
  ownerRef: string;
  inHouseQuantity: number;
  outQuantity: number;
  pendingQuantity: number;
  outsourcingCountsAsInHouseStock: boolean;
  dispatches: OutsourcingDispatchLine[];
  returns: OutsourcingReturnLine[];
  sources: OutsourcingSourceLine[];
}

export function fetchOutsourcingPosition(taskId: string): Promise<OutsourcingPosition> {
  return apiGet<OutsourcingPosition>(`/api/business/outsourcing/tasks/${encodeURIComponent(taskId)}`);
}

export function submitOutsourcingDispatch(body: {
  requestId: string;
  taskId: string;
  bagId: string;
  bagVersion: number;
  quantity: number;
}): Promise<{ dispatchId: string }> {
  return apiPost('/api/business/outsourcing/dispatches', body);
}

export function submitOutsourcingReturn(body: {
  requestId: string;
  dispatchId: string;
  dispatchVersion: number;
  received: number;
  unprocessed: number;
  loss: number;
  weight: number | null;
}): Promise<{ returnId: string }> {
  return apiPost('/api/business/outsourcing/returns', body);
}

export interface OutsourcingReturnCompletion { returned: { returnId: string; receivedQty: number; unprocessedQty: number }; completed: boolean }
export const fetchOutsourcingReturns = (taskId: string) => apiGet<OutsourcingReturnCompletion[]>(`/api/business/outsourcing/tasks/${encodeURIComponent(taskId)}/returns`);
export const confirmOutsourcingCompletion = (body: { requestId: string; returnId: string }) => apiPost('/api/business/outsourcing/completions', body);
