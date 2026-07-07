import { apiGet } from './apiClient';
import type { RegisterImportSummary, RegisterImportTemplate } from '../data/registerImportTemplates';

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
