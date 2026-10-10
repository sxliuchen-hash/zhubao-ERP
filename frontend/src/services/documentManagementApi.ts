import { apiGet, apiPost, apiPostForm } from './apiClient';
import type {
  DocumentAuditEvent,
  DocumentManagementSummary,
  DocumentPackage,
  EvidenceAttachment,
} from '../data/documentManagementData';

export interface EvidenceAttachmentUploadInput {
  documentCode: string;
  businessType: string;
  businessCode: string;
  source: string;
  owner: string;
  fallbackAction?: string;
  file: File;
}

export interface EvidenceAttachmentUploadResult {
  code: string;
  documentCode: string;
  fileName: string;
  mediaType: string;
  size: number;
  storageLocation: string;
  checksum: string;
  uploadedAt: string;
  previewUrl: string;
}

export interface AttachmentPreviewUrl {
  code: string;
  fileName: string;
  mediaType: string;
  url: string;
  expiresAt: string;
}

export interface StoredContentItem {
  code: string;
  fileName: string;
  businessType: string;
  businessCode: string;
  owner: string;
  storageLocation: string;
  mediaType: string;
  status: string;
  uploadedAt: string;
  sizeBytes: number;
  sizeLabel: string;
}

export interface StoredContentInventory {
  provider: string;
  rootPath: string;
  fileCount: number;
  usedBytes: number;
  usedLabel: string;
  diskTotalBytes: number;
  diskFreeBytes: number;
  diskUsedPercent: number;
  items: StoredContentItem[];
}

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

export async function uploadEvidenceAttachment(
  input: EvidenceAttachmentUploadInput,
): Promise<EvidenceAttachmentUploadResult> {
  const body = new FormData();
  body.append('documentCode', input.documentCode);
  body.append('businessType', input.businessType);
  body.append('businessCode', input.businessCode);
  body.append('source', input.source);
  body.append('owner', input.owner);
  body.append('fallbackAction', input.fallbackAction ?? '无');
  body.append('file', input.file);

  return apiPostForm<EvidenceAttachmentUploadResult>('/api/documents/attachments/uploads', body);
}

export async function fetchStoredContent(): Promise<StoredContentInventory> {
  return apiGet<StoredContentInventory>('/api/documents/storage');
}

export async function retireStoredContent(code: string): Promise<StoredContentItem> {
  return apiPost<Record<string, never>, StoredContentItem>(
    `/api/documents/attachments/${encodeURIComponent(code)}/retirements`,
    {},
  );
}

export async function fetchAttachmentPreviewUrl(code: string): Promise<AttachmentPreviewUrl> {
  return apiGet<AttachmentPreviewUrl>(`/api/documents/attachments/${code}/preview-url`);
}
