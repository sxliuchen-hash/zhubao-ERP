import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchDocumentAuditEvents,
  fetchDocumentPackages,
  fetchDocumentPackagesPage,
  fetchDocumentSummary,
  fetchEvidenceAttachments,
  fetchEvidenceAttachmentsPage,
  fetchAttachmentPreviewUrl,
  uploadEvidenceAttachment,
} from './documentManagementApi';

describe('documentManagementApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('loads document management endpoints through the shared api client', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input).split('?')[0];
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
          url: '/api/documents/attachments/ATT-UP-001/content?expiresAt=1783374000&purpose=preview&resolution=original&token=signed-preview-token',
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
              previewUrl: '/api/documents/attachments/ATT-UP-001/content?expiresAt=1783374000&purpose=preview&resolution=original&token=signed-preview-token',
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
      url: '/api/documents/attachments/ATT-UP-001/content?expiresAt=1783374000&purpose=preview&resolution=original&token=signed-preview-token',
    });
    expect(fetchMock).toHaveBeenCalledTimes(6);
    expect(String(fetchMock.mock.calls[5][0])).toContain('/preview-url?purpose=preview&requestId=');
    await fetchAttachmentPreviewUrl('ATT-UP-001', 'download');
    expect(String(fetchMock.mock.calls[6][0])).toContain('/preview-url?purpose=download&requestId=');
  });

  it('loads the first attachment page with encoded document code and parses the page response', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      expect(String(input)).toBe(
        '/api/documents/attachments/page?documentCode=DOC-%E7%8F%A0+%26+%E5%AE%A2%3F%2F%E4%B8%AD%E6%96%87',
      );
      return {
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            items: [{ code: 'ATT-PAGE-001', documentCode: 'DOC-珠 & 客?/中文' }],
            nextCursor: 42,
            hasMore: true,
          },
        }),
      } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchEvidenceAttachmentsPage({ documentCode: 'DOC-珠 & 客?/中文' })).resolves.toEqual({
      items: [{ code: 'ATT-PAGE-001', documentCode: 'DOC-珠 & 客?/中文' }],
      nextCursor: 42,
      hasMore: true,
    });
  });

  it('loads the first package page without optional parameters and parses the page response', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      expect(String(input)).toBe('/api/documents/packages/page');
      return {
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            items: [{ code: 'DOC-PAGE-001', title: '分页文档包' }],
            nextCursor: 17,
            hasMore: true,
          },
        }),
      } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchDocumentPackagesPage()).resolves.toEqual({
      items: [{ code: 'DOC-PAGE-001', title: '分页文档包' }],
      nextCursor: 17,
      hasMore: true,
    });
  });

  it('loads a continuation package page with cursor and limit parameters', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      expect(String(input)).toBe('/api/documents/packages/page?afterId=17&limit=20');
      return {
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: { items: [], nextCursor: null, hasMore: false },
        }),
      } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchDocumentPackagesPage({ afterId: 17, limit: 20 })).resolves.toEqual({
      items: [],
      nextCursor: null,
      hasMore: false,
    });
  });

  it('loads a continuation attachment page with cursor and limit parameters', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      expect(String(input)).toBe('/api/documents/attachments/page?documentCode=DOC-PAGE-001&afterId=42&limit=20');
      return {
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: { items: [], nextCursor: null, hasMore: false },
        }),
      } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchEvidenceAttachmentsPage({ documentCode: 'DOC-PAGE-001', afterId: 42, limit: 20 })).resolves.toEqual({
      items: [],
      nextCursor: null,
      hasMore: false,
    });
  });
});
