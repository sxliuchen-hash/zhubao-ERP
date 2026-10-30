import { apiPost } from './apiClient';

export function issueConsumable(body: {
  requestId: string;
  itemId: string;
  receiptId: string;
  workshopCode: string;
  quantity: string | null;
}) {
  return apiPost('/api/inventory/consumable-issues', body);
}

export function consumeConsumable(issueId: string, body: { requestId: string; version: number; quantity: string }) {
  return apiPost(`/api/inventory/consumable-issues/${issueId}/consumptions`, body);
}
