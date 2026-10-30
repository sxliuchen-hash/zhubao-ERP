import { loginAuthSession } from '../../services/authApi';
import { apiGet, apiPost } from '../../services/apiClient';
import type { CustomerEvidenceDetail, CustomerEvidenceItem, CustomerOwnedStock } from './customerStock';

export function loginCustomer(username: string, password: string) {
  return loginAuthSession({ username, password });
}

export function loadOwnedStock(): Promise<CustomerOwnedStock> {
  return apiGet<CustomerOwnedStock>('/api/customer-portal/owned-stock');
}

export function loadEvidence(): Promise<CustomerEvidenceDetail> {
  return apiGet<CustomerEvidenceDetail>('/api/customer-portal/evidence');
}

export function downloadEvidence(item: CustomerEvidenceItem) {
  return apiPost<{ grantId: string; attachmentCode: string; ticket: string }, { fileName: string }>(
    '/api/customer-portal/evidence/downloads',
    {
      grantId: item.grantId,
      attachmentCode: item.attachmentCode,
      ticket: item.downloadTicket,
    },
  );
}
