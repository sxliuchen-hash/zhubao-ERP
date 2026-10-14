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
  percent: number;
  version: number;
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

export function fetchProcessProgress(query = '', process = '', dueBefore = '', page = 1, size = 20): Promise<ProcessProgressRow[]> {
  const params = new URLSearchParams();
  if (query.trim()) params.set('query', query.trim());
  if (process.trim()) params.set('process', process.trim());
  if (dueBefore.trim()) params.set('dueBefore', dueBefore.trim());
  params.set('page', String(page));
  params.set('size', String(size));
  return apiGet<ProcessProgressRow[]>(`/api/business/operations/process-progress?${params.toString()}`);
}

export function fetchProcessProgressHistory(orderCode: string, process: string): Promise<{ oldPercent: number; newPercent: number; operator: string; createdAt: string; reason: string }[]> {
  const params = new URLSearchParams({ orderCode, process });
  return apiGet(`/api/business/operations/process-progress/history?${params.toString()}`);
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
