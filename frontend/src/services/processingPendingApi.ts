import { apiGet, apiPost } from './apiClient';
export interface ProcessingIntakeDraft {
  customerId: number | null; customerName: string; sourceType: string; courierNo: string; processingRequirement: string; dueDate: string;
  lines: Array<{ materialCode: string; goodsName: string; quantity: string; quantityUnit: string; weight: string }>;
  requirementTemplateCode: string; requirementTemplateVersion: number; localDraftKey: string;
}
export interface ProcessingIntakePending { code: string; version: number; account: string; status: string; motherOrderCode: string; draftJson: string; createdAt: string; updatedAt: string }
export interface SaveProcessingPending { code: string; expectedVersion: number; requestId: string; draft: ProcessingIntakeDraft }
export const fetchProcessingPendings = () => apiGet<ProcessingIntakePending[]>('/api/business/operations/intake-pendings');
export const saveProcessingPending = (body: SaveProcessingPending) => apiPost<SaveProcessingPending, ProcessingIntakePending>('/api/business/operations/intake-pendings', body);
