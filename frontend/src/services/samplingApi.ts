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

export function openSamplingOrder(body: {
  requestId: string;
  customerId: number;
  materialIntent: string;
  requirement: string;
  ownerName: string;
}): Promise<SamplingOrderView> {
  return apiPost('/api/sampling/orders', body);
}
