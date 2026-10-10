import { apiGet } from './apiClient';

export interface ProgressRow {
  customerId: number | null;
  customerName: string;
  motherOrderCode: string;
  workOrderCode: string;
  currentProcess: string;
  dueDate: string;
  overdue: boolean;
  qualityOutcome: string;
  status: string;
  openTab: 'inbound' | 'production' | 'quality';
}

export function fetchProgress(query: {
  customer?: string;
  process?: string;
  dueDate?: string;
  status?: string;
}): Promise<ProgressRow[]> {
  const params = new URLSearchParams();
  if (query.customer) params.set('customer', query.customer);
  if (query.process) params.set('process', query.process);
  if (query.dueDate) params.set('dueDate', query.dueDate);
  if (query.status) params.set('status', query.status);
  const suffix = params.toString();
  return apiGet(`/api/business/operations/progress${suffix ? `?${suffix}` : ''}`);
}
