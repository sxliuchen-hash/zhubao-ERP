import { apiGet, apiPostForm } from './apiClient';
import type {
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
