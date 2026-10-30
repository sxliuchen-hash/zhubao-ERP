import { apiPost } from './apiClient';

export function confirmStocktake(stocktakeId: string, body: { requestId: string; version: number }) {
  return apiPost(`/api/inventory/stocktakes/${stocktakeId}/confirmations`, body);
}
