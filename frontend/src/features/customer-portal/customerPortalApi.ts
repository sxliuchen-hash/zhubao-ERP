import { loginAuthSession } from '../../services/authApi';
import { apiGet, apiPostBlob } from '../../services/apiClient';
import type { CustomerEvidenceDetail, CustomerEvidenceItem, CustomerOwnedStock, CustomerStatement } from './customerStock';

export function loginCustomer(username: string, password: string) {
  return loginAuthSession({ username, password });
}

export function loadOwnedStock(): Promise<CustomerOwnedStock> {
  return apiGet<CustomerOwnedStock>('/api/customer-portal/owned-stock');
}

export function loadStatement(cutoff: string): Promise<CustomerStatement> {
  return apiGet<CustomerStatement>(`/api/customer-portal/statements?cutoff=${encodeURIComponent(cutoff)}`);
}

export function loadEvidence(): Promise<CustomerEvidenceDetail> {
  return apiGet<CustomerEvidenceDetail>('/api/customer-portal/evidence');
}

export async function downloadEvidence(item: CustomerEvidenceItem) {
  const blob = await apiPostBlob(
    '/api/customer-portal/evidence/downloads',
    {
      grantId: item.grantId,
      attachmentCode: item.attachmentCode,
      ticket: item.downloadTicket,
    },
  );
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = item.fileName;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
  return { fileName: item.fileName };
}
