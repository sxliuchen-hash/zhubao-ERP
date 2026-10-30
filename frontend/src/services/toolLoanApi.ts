import { apiPost } from './apiClient';

export function borrowTool(body: {
  requestId: string;
  itemId: string;
  warehouseCode: string;
  holderId: string;
  holderName: string;
  quantity: string;
  pieceCode: string;
}) {
  return apiPost('/api/inventory/tool-loans', body);
}

export function returnTool(loanId: string, body: { requestId: string; version: number; quantity: string }) {
  return apiPost(`/api/inventory/tool-loans/${loanId}/returns`, body);
}
