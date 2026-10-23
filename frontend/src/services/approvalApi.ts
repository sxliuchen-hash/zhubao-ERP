import type { ApprovalDecisionCommand, ApprovalTask } from '../data/approvalTaskData';
import { apiGet, apiPost } from './apiClient';

export function fetchApprovalTasks(): Promise<ApprovalTask[]> {
  return apiGet<ApprovalTask[]>('/api/approvals/tasks');
}

export function fetchPendingApprovalTasks(): Promise<ApprovalTask[]> {
  return apiGet<ApprovalTask[]>('/api/approvals/tasks/pending');
}

export function fetchApprovalTask(approvalCode: string): Promise<ApprovalTask> {
  return apiGet<ApprovalTask>(`/api/approvals/tasks/${encodeURIComponent(approvalCode)}`);
}

export function submitApprovalDecision(
  approvalCode: string,
  command: ApprovalDecisionCommand,
): Promise<ApprovalTask> {
  return apiPost<ApprovalDecisionCommand, ApprovalTask>(
    `/api/approvals/tasks/${encodeURIComponent(approvalCode)}/decisions`,
    command,
  );
}
