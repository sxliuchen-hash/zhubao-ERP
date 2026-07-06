import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchDocumentAuditEvents,
  fetchDocumentPackages,
  fetchDocumentSummary,
  fetchEvidenceAttachments,
  fetchAttachmentPreviewUrl,
  uploadEvidenceAttachment,
} from './documentManagementApi';

describe('documentManagementApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('loads document management endpoints through the shared api client', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const dataByUrl: Record<string, unknown> = {
        '/api/documents/summary': {
          packages: 4,
          attachments: 5,
          missingEvidence: 3,
          manualFallbacks: 3,
          auditEvents: 4,
        },
        '/api/documents/packages': [
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
        ],
        '/api/documents/attachments': [
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
        ],
        '/api/documents/audit-events': [
          {
            time: '2026-07-06 15:12',
            eventCode: 'DOC-AUD-002',
            documentCode: 'DOC-QC-20260706-021',
            action: '归档质检证据',
            operator: '质检员 周娜',
            detail: '上传缺陷照片压缩包并关联返工工单。',
            tone: 'green',
          },
        ],
        '/api/documents/attachments/ATT-UP-001/preview-url': {
          code: 'ATT-UP-001',
          fileName: 'weight-photo.jpg',
          mediaType: 'image/jpeg',
          url: '/api/documents/attachments/ATT-UP-001/content?token=preview-demo',
          expiresAt: '2026-07-07 00:20',
        },
      };
      if (url === '/api/documents/attachments/uploads') {
        return {
          ok: true,
          json: async () => ({
            code: 0,
            message: 'ok',
            data: {
              code: 'ATT-UP-001',
              documentCode: 'DOC-INBOUND-20260706-001',
              fileName: 'weight-photo.jpg',
              mediaType: 'image/jpeg',
              size: 12,
              storageLocation: 'local://evidence/DOC-INBOUND-20260706-001/ATT-UP-001/weight-photo.jpg',
              checksum: 'sha256:demo',
              uploadedAt: '2026-07-07 00:05',
              previewUrl: '/api/documents/attachments/ATT-UP-001/content?token=preview-demo',
            },
          }),
        } as Response;
      }

      return {
        ok: true,
        json: async () => ({ code: 0, message: 'ok', data: dataByUrl[url] }),
      } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchDocumentSummary()).resolves.toMatchObject({ packages: 4, attachments: 5 });
    await expect(fetchDocumentPackages()).resolves.toHaveLength(1);
    await expect(fetchEvidenceAttachments()).resolves.toHaveLength(1);
    await expect(fetchDocumentAuditEvents()).resolves.toHaveLength(1);
    await expect(
      uploadEvidenceAttachment({
        documentCode: 'DOC-INBOUND-20260706-001',
        businessType: '称重交接',
        businessCode: 'HO-20260706-014',
        source: 'PC 后台上传',
        owner: '库管员 何琳',
        file: new File(['demo content'], 'weight-photo.jpg', { type: 'image/jpeg' }),
      }),
    ).resolves.toMatchObject({ code: 'ATT-UP-001', checksum: 'sha256:demo' });
    await expect(fetchAttachmentPreviewUrl('ATT-UP-001')).resolves.toMatchObject({
      url: '/api/documents/attachments/ATT-UP-001/content?token=preview-demo',
    });
    expect(fetchMock).toHaveBeenCalledTimes(6);
  });
});
