import { apiPost } from './apiClient';

export interface FactoryIssueRequest {
  requestId: string;
  itemId: string;
  batchCode: string;
  fromWarehouse: string;
  workshopCode: string;
  quantity: string;
  weight: string | null;
  unit: string;
  targetCode: string;
}

export interface FactoryReturnRequest {
  requestId: string;
  version: number;
  quantity: string;
  weight: string | null;
  unit: string;
}

/** 领用只提交原批次和数量，不提交可手改的总余额。 */
export function issueFactoryMaterial(body: FactoryIssueRequest) {
  return apiPost<FactoryIssueRequest, { issueId: string }>('/api/inventory/company-material-issues', body);
}

/** 退回按原领用单提交，不改总余额。 */
export function returnFactoryMaterial(issueId: string, body: FactoryReturnRequest) {
  return apiPost<FactoryReturnRequest, { returnId: string }>(`/api/inventory/company-material-issues/${issueId}/returns`, body);
}
