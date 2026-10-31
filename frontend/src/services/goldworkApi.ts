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
