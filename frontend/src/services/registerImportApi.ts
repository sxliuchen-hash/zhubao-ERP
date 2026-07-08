import { apiGet, apiPost, apiPostForm } from './apiClient';
import type {
  RegisterImportBatch,
  RegisterImportPostingExecutionResult,
  RegisterImportPostingPlan,
  RegisterImportPreview,
  RegisterImportSummary,
  RegisterImportTemplate,
} from '../data/registerImportTemplates';

export interface RegisterImportWorkspaceModel {
  templates: RegisterImportTemplate[];
  summary: RegisterImportSummary;
}

export async function fetchRegisterImportWorkspace(): Promise<RegisterImportWorkspaceModel> {
  const [templates, summary] = await Promise.all([
    apiGet<RegisterImportTemplate[]>('/api/register-import/templates'),
    apiGet<RegisterImportSummary>('/api/register-import/summary'),
  ]);

  return { templates, summary };
}

export interface PreviewRegisterImportRequest {
  templateCode: string;
  businessDate?: string;
  file: Blob;
  fileName?: string;
}

export async function previewRegisterImport(request: PreviewRegisterImportRequest): Promise<RegisterImportPreview> {
  const body = new FormData();
  body.append('templateCode', request.templateCode);
  if (request.businessDate) {
    body.append('businessDate', request.businessDate);
  }
  if (request.fileName) {
    body.append('file', request.file, request.fileName);
  } else {
    body.append('file', request.file);
  }
  return apiPostForm<RegisterImportPreview>('/api/register-import/previews', body);
}

export interface ConfirmRegisterImportRequest {
  preview: RegisterImportPreview;
  operator?: string;
  confirmRemark?: string;
}

export async function confirmRegisterImport(request: ConfirmRegisterImportRequest): Promise<RegisterImportBatch> {
  return apiPost<ConfirmRegisterImportRequest, RegisterImportBatch>('/api/register-import/batches/confirm', request);
}

export async function fetchRegisterImportBatches(limit = 10): Promise<RegisterImportBatch[]> {
  return apiGet<RegisterImportBatch[]>(`/api/register-import/batches?limit=${limit}`);
}

export async function fetchRegisterImportBatch(importBatchNo: string): Promise<RegisterImportBatch> {
  return apiGet<RegisterImportBatch>(`/api/register-import/batches/${encodeURIComponent(importBatchNo)}`);
}

export async function fetchRegisterImportPostingPlan(importBatchNo: string): Promise<RegisterImportPostingPlan> {
  return apiGet<RegisterImportPostingPlan>(
    `/api/register-import/batches/${encodeURIComponent(importBatchNo)}/posting-plan`,
  );
}

export interface CorrectRegisterImportRowRequest {
  fields: Record<string, string>;
  operator?: string;
  correctionRemark?: string;
}

export async function correctRegisterImportRow(
  importBatchNo: string,
  sourceLineNo: number,
  request: CorrectRegisterImportRowRequest,
): Promise<RegisterImportBatch> {
  return apiPost<CorrectRegisterImportRowRequest, RegisterImportBatch>(
    `/api/register-import/batches/${encodeURIComponent(importBatchNo)}/rows/${sourceLineNo}/corrections`,
    request,
  );
}

export interface PostRegisterImportBatchRequest {
  allowWarnings?: boolean;
  operator?: string;
  postingRemark?: string;
}

export async function postRegisterImportBatch(
  importBatchNo: string,
  request: PostRegisterImportBatchRequest = {},
): Promise<RegisterImportPostingExecutionResult> {
  return apiPost<PostRegisterImportBatchRequest, RegisterImportPostingExecutionResult>(
    `/api/register-import/batches/${encodeURIComponent(importBatchNo)}/posting-commands`,
    request,
  );
}
