import { apiGet, apiPost } from './apiClient';

export interface ProcessProgressChildOption {
  code: string;
  customerName: string;
  processes: string;
}

export interface ProcessProgressTarget {
  selectChild: boolean;
  orderCode: string;
  motherOrderCode: string;
  customerName: string;
  processingRequirement: string;
  dueDate: string;
  currentProcess: string;
  formallyDelivered: boolean;
  children: ProcessProgressChildOption[];
}

export interface ProcessProgressHead {
  orderCode: string;
  processName: string;
  percent: number;
  version: number;
  note: string;
  operator: string;
  updatedAt: string;
}

export interface ProcessProgressRow {
  orderCode: string;
  motherOrderCode: string;
  customerName: string;
  currentProcess: string;
  businessStatus: string;
  percent: number;
  version: number;
  operator: string;
  updatedAt: string;
  dueDate: string;
}

export function fetchProcessProgress(query = ''): Promise<ProcessProgressRow[]> {
  const suffix = query.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
  return apiGet<ProcessProgressRow[]>(`/api/business/operations/process-progress${suffix}`);
}

export function openProcessProgress(code: string): Promise<ProcessProgressTarget> {
  return apiGet<ProcessProgressTarget>(`/api/business/operations/process-progress/target?code=${encodeURIComponent(code)}`);
}

export function submitProcessProgress(body: {
  code: string;
  percent: number;
  expectedVersion: number;
  note: string;
  reason: string;
  requestId: string;
}): Promise<ProcessProgressHead> {
  return apiPost<typeof body, ProcessProgressHead>('/api/business/operations/process-progress', body);
}
