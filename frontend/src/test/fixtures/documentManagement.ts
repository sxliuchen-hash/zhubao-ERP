import type {
  DocumentAuditEvent,
  DocumentManagementSummary,
  DocumentPackage,
  EvidenceAttachment,
} from '../../data/documentManagementData';

function documentPackage(code: string, title: string, businessCode: string): DocumentPackage {
  return {
    code,
    title,
    category: '业务单据',
    businessCode,
    owner: '测试库管',
    purpose: '核对文档包与附件归属',
    requiredEvidence: 2,
    linkedEvidence: 1,
    status: '待补附件',
    retentionPolicy: '保留原件',
    lastUpdated: '2026-07-06 15:30',
    tone: 'amber',
  };
}

export const documentPackages = [
  documentPackage('DOC-INBOUND-001', '来料母单证据包', 'MD-001'),
  documentPackage('DOC-QC-001', '质检返工证据包', 'QC-001'),
  documentPackage('DOC-HW-001', '硬件证据包', 'HW-001'),
];

function attachment(code: string, documentIndex: number, fileName: string): EvidenceAttachment {
  const document = documentPackages[documentIndex];
  return {
    code,
    documentCode: document.code,
    businessType: '来料母单',
    businessCode: document.businessCode,
    fileName,
    mediaType: 'image/jpeg',
    storageLocation: `local://${code}.jpg`,
    storagePolicy: '保留原件',
    source: '现场拍照',
    status: '已关联',
    owner: document.owner,
    checksum: `sha256:${code}`,
    uploadedAt: '2026-07-06 15:30',
    fallbackAction: '无',
    tone: 'green',
  };
}

export const evidenceAttachments = [
  attachment('ATT-IN-001', 0, 'waybill.jpg'),
  attachment('ATT-IN-002', 0, 'weight.jpg'),
  attachment('ATT-QC-001', 1, 'quality.jpg'),
];

export const documentAuditEvents: DocumentAuditEvent[] = [{
  time: '2026-07-06 15:30',
  eventCode: 'DOC-AUD-001',
  documentCode: documentPackages[0].code,
  action: '上传附件',
  operator: '测试库管',
  detail: '绑定现场照片',
  tone: 'green',
}];

export const documentSummary: DocumentManagementSummary = {
  packages: documentPackages.length,
  attachments: evidenceAttachments.length,
  missingEvidence: 3,
  manualFallbacks: 0,
  auditEvents: documentAuditEvents.length,
};
