import { apiGet, apiPost } from './apiClient';

export type DeskTaskKind = 'MODELING' | 'DESIGN' | 'OTHER';
export type DeskTaskStatus = 'OPEN' | 'DONE' | 'CANCELLED';
export type DeskTaskView = 'assigned' | 'issued' | 'open';

export interface DeskTask {
  taskCode: string;
  kind: DeskTaskKind;
  title: string;
  note: string;
  customerId: number | null;
  customerName: string;
  motherOrderCode: string;
  assigneeUsername: string;
  assigneeName: string;
  dueDate: string;
  status: DeskTaskStatus;
  creatorUsername: string;
  createdAt: string;
  requestId: string;
  version: number;
  completedAt: string;
  completedBy: string;
  resultNote: string;
  completeRequestId: string;
  cancelledAt: string;
  cancelledBy: string;
  cancelReason: string;
  cancelRequestId: string;
}

export interface DeskAssignee {
  username: string;
  operator: string;
}

export interface CreateDeskTaskInput {
  kind: DeskTaskKind;
  title: string;
  note: string;
  customerId: number | null;
  motherOrderCode: string;
  assigneeUsername: string;
  dueDate: string;
  requestId: string;
}

export interface CompleteDeskTaskInput {
  requestId: string;
  expectedVersion: number;
  resultNote: string;
}

export interface CancelDeskTaskInput {
  requestId: string;
  expectedVersion: number;
  reason: string;
}

export const fetchDeskTasks = (view: DeskTaskView) =>
  apiGet<DeskTask[]>(`/api/business/desk-tasks?view=${view}`);

export const fetchDeskAssignees = () => apiGet<DeskAssignee[]>('/api/business/desk-tasks/assignees');

export const createDeskTask = (body: CreateDeskTaskInput) =>
  apiPost<CreateDeskTaskInput, DeskTask>('/api/business/desk-tasks', body);

export const completeDeskTask = (taskCode: string, body: CompleteDeskTaskInput) =>
  apiPost<CompleteDeskTaskInput, DeskTask>(`/api/business/desk-tasks/${encodeURIComponent(taskCode)}/completions`, body);

export const cancelDeskTask = (taskCode: string, body: CancelDeskTaskInput) =>
  apiPost<CancelDeskTaskInput, DeskTask>(`/api/business/desk-tasks/${encodeURIComponent(taskCode)}/cancellations`, body);
