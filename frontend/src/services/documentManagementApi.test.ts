import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchDocumentAuditEvents,
  fetchDocumentPackages,
  fetchDocumentSummary,
  fetchEvidenceAttachments,
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
      };

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
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });
});
