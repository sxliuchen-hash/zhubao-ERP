import { apiGet } from './apiClient';
import type {
  DocumentAuditEvent,
  DocumentManagementSummary,
  DocumentPackage,
  EvidenceAttachment,
} from '../data/documentManagementData';

export async function fetchDocumentSummary(): Promise<DocumentManagementSummary> {
  return apiGet<DocumentManagementSummary>('/api/documents/summary');
}

export async function fetchDocumentPackages(): Promise<DocumentPackage[]> {
  return apiGet<DocumentPackage[]>('/api/documents/packages');
}

export async function fetchEvidenceAttachments(): Promise<EvidenceAttachment[]> {
  return apiGet<EvidenceAttachment[]>('/api/documents/attachments');
}

export async function fetchDocumentAuditEvents(): Promise<DocumentAuditEvent[]> {
  return apiGet<DocumentAuditEvent[]>('/api/documents/audit-events');
}
