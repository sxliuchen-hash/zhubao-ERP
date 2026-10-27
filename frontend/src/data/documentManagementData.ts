import type { Tone } from './m1PrototypeData';

export interface DocumentPackage {
  code: string;
  title: string;
  category: string;
  businessCode: string;
  owner: string;
  purpose: string;
  requiredEvidence: number;
  linkedEvidence: number;
  status: string;
  retentionPolicy: string;
  lastUpdated: string;
  tone: Tone;
}

export interface EvidenceAttachment {
  code: string;
  documentCode: string;
  businessType: string;
  businessCode: string;
  fileName: string;
  mediaType: string;
  storageLocation: string;
  storagePolicy: string;
  source: string;
  status: string;
  owner: string;
  checksum: string;
  uploadedAt: string;
  fallbackAction: string;
  tone: Tone;
}

export interface DocumentAuditEvent {
  time: string;
  eventCode: string;
  documentCode: string;
  action: string;
  operator: string;
  detail: string;
  tone: Tone;
}

export interface DocumentManagementSummary {
  packages: number;
  attachments: number;
  missingEvidence: number;
  manualFallbacks: number;
  auditEvents: number;
}
