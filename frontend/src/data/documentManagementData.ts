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

export const documentSummary: DocumentManagementSummary = {
  packages: 4,
  attachments: 5,
  missingEvidence: 3,
  manualFallbacks: 3,
  auditEvents: 4,
};

export const documentPackages: DocumentPackage[] = [
  {
    code: 'DOC-INBOUND-20260706-001',
    title: '来料母单证据包',
    category: '业务单据',
    businessCode: 'MD-20260706-001',
    owner: '库管员 何琳',
    purpose: '归档快递单、拆包称重、标签和入库台账，支撑客户来料追溯。',
    requiredEvidence: 5,
    linkedEvidence: 4,
    status: '缺称重照片',
    retentionPolicy: '业务附件保留 3 年，视频片段保留 180 天',
    lastUpdated: '2026-07-06 15:30',
    tone: 'amber',
  },
  {
    code: 'DOC-QC-20260706-021',
    title: '质检返工证据包',
    category: '质检证据',
    businessCode: 'QC-20260706-021',
    owner: '质检员 周娜',
    purpose: '归档缺陷照片、返工原因、返工派发和返工报工记录。',
    requiredEvidence: 6,
    linkedEvidence: 6,
    status: '已归档',
    retentionPolicy: '质检附件保留 3 年',
    lastUpdated: '2026-07-06 16:05',
    tone: 'green',
  },
  {
    code: 'DOC-HW-POC-20260706-001',
    title: '硬件 POC 证据包',
    category: '软硬件联动',
    businessCode: 'POC-SCAN-20260706-001',
    owner: '实施负责人',
    purpose: '归档 NVR、标签打印、PWA 扫码和电子秤验证证据。',
    requiredEvidence: 4,
    linkedEvidence: 3,
    status: '待补失败日志',
    retentionPolicy: 'POC 记录保留到项目验收后 1 年',
    lastUpdated: '2026-07-06 17:20',
    tone: 'amber',
  },
  {
    code: 'DOC-CONFIG-20260706-001',
    title: '配置变更评审包',
    category: '配置/构型',
    businessCode: 'CFG-CR-20260706-001',
    owner: '生产负责人',
    purpose: '归档配置变更、审批意见、影响范围和回滚说明。',
    requiredEvidence: 4,
    linkedEvidence: 2,
    status: '待审批补全',
    retentionPolicy: '配置审计长期保留',
    lastUpdated: '2026-07-06 16:40',
    tone: 'red',
  },
];

export const evidenceAttachments: EvidenceAttachment[] = [
  {
    code: 'ATT-IN-20260706-001',
    documentCode: 'DOC-INBOUND-20260706-001',
    businessType: '来料母单',
    businessCode: 'MD-20260706-001',
    fileName: 'SF1048329001-waybill.jpg',
    mediaType: 'image/jpeg',
    storageLocation: 's3://zhubao-evidence/inbound/20260706/SF1048329001-waybill.jpg',
    storagePolicy: 'hot 180d -> archive',
    source: 'PWA 拍照上传',
    status: '已关联',
    owner: '库管员 何琳',
    checksum: 'sha256:inbound-waybill-demo',
    uploadedAt: '2026-07-06 08:45',
    fallbackAction: '无',
    tone: 'green',
  },
  {
    code: 'ATT-IN-20260706-002',
    documentCode: 'DOC-INBOUND-20260706-001',
    businessType: '称重交接',
    businessCode: 'HO-20260706-014',
    fileName: 'HO-20260706-014-weight.jpg',
    mediaType: 'image/jpeg',
    storageLocation: '待上传',
    storagePolicy: 'hot 180d -> archive',
    source: '人工补传',
    status: '缺失',
    owner: '库管员 何琳',
    checksum: '待生成',
    uploadedAt: '待上传',
    fallbackAction: 'PWA 或 PC 后台补传称重照片',
    tone: 'red',
  },
  {
    code: 'ATT-QC-20260706-021',
    documentCode: 'DOC-QC-20260706-021',
    businessType: '质检记录',
    businessCode: 'QC-20260706-021',
    fileName: 'QC-20260706-021-defects.zip',
    mediaType: 'application/zip',
    storageLocation: 's3://zhubao-evidence/quality/20260706/QC-20260706-021-defects.zip',
    storagePolicy: 'hot 365d -> archive',
    source: '质检台上传',
    status: '已归档',
    owner: '质检员 周娜',
    checksum: 'sha256:qc-defect-demo',
    uploadedAt: '2026-07-06 15:12',
    fallbackAction: '无',
    tone: 'green',
  },
  {
    code: 'ATT-HW-20260706-001',
    documentCode: 'DOC-HW-POC-20260706-001',
    businessType: '硬件 POC',
    businessCode: 'POC-SCAN-20260706-001',
    fileName: 'pwa-low-light-scan.mp4',
    mediaType: 'video/mp4',
    storageLocation: 'nas://evidence/poc/20260706/pwa-low-light-scan.mp4',
    storagePolicy: 'hot 90d -> archive',
    source: '现场录屏',
    status: '有条件通过',
    owner: '实施负责人',
    checksum: 'sha256:poc-video-demo',
    uploadedAt: '2026-07-06 17:08',
    fallbackAction: '弱光失败时允许手工录入编码',
    tone: 'amber',
  },
  {
    code: 'ATT-CFG-20260706-001',
    documentCode: 'DOC-CONFIG-20260706-001',
    businessType: '配置变更',
    businessCode: 'CFG-CR-20260706-001',
    fileName: 'loss-rate-change-review.pdf',
    mediaType: 'application/pdf',
    storageLocation: '待审批后归档',
    storagePolicy: 'audit long-term',
    source: '配置评审',
    status: '待审批',
    owner: '生产负责人',
    checksum: '待生成',
    uploadedAt: '待归档',
    fallbackAction: '审批完成后锁定版本',
    tone: 'amber',
  },
];

export const documentAuditEvents: DocumentAuditEvent[] = [
  {
    time: '2026-07-06 08:45',
    eventCode: 'DOC-AUD-001',
    documentCode: 'DOC-INBOUND-20260706-001',
    action: '上传附件',
    operator: '库管员 何琳',
    detail: '上传快递单照片并绑定母单。',
    tone: 'green',
  },
  {
    time: '2026-07-06 15:12',
    eventCode: 'DOC-AUD-002',
    documentCode: 'DOC-QC-20260706-021',
    action: '归档质检证据',
    operator: '质检员 周娜',
    detail: '上传缺陷照片压缩包并关联返工工单。',
    tone: 'green',
  },
  {
    time: '2026-07-06 17:08',
    eventCode: 'DOC-AUD-003',
    documentCode: 'DOC-HW-POC-20260706-001',
    action: '登记 POC 证据',
    operator: '实施负责人',
    detail: '关联 PWA 弱光扫码录屏和兜底说明。',
    tone: 'amber',
  },
  {
    time: '2026-07-06 17:20',
    eventCode: 'DOC-AUD-004',
    documentCode: 'DOC-CONFIG-20260706-001',
    action: '创建评审包',
    operator: '生产负责人',
    detail: '配置变更单等待审批，附件暂不锁定。',
    tone: 'red',
  },
];
