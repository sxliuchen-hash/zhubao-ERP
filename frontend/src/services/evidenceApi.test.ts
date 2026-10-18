import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchHistoricalEvidencePage, fetchLinkedEvidencePage } from './evidenceApi';

describe('证据分页接口', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('已关联页传递单据、阶段、操作与附件筛选并编码中文', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => ({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data: { items: [{ linkCode: 'LINK-1' }], nextCursor: 42, hasMore: true } }),
    } as Response));
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchLinkedEvidencePage({
      targetType: 'MOTHER_ORDER', targetCode: 'MD-珠 & 1', stage: 'INTAKE',
      operationType: 'inbound_confirm', operationCode: 'OP-1',
      uploadedFrom: '2026-10-01', uploadedTo: '2026-10-18',
      mediaCategory: 'image', ownerKeyword: '何 琴', afterId: 41, limit: 2,
    })).resolves.toMatchObject({ items: [{ linkCode: 'LINK-1' }], nextCursor: 42, hasMore: true });

    const url = new URL(String(fetchMock.mock.calls[0][0]), 'https://local.test');
    expect(url.pathname).toBe('/api/documents/evidence-links/page');
    expect(Object.fromEntries(url.searchParams)).toMatchObject({
      targetType: 'MOTHER_ORDER', targetCode: 'MD-珠 & 1', stage: 'INTAKE',
      operationType: 'inbound_confirm', operationCode: 'OP-1',
      uploadedFrom: '2026-10-01', uploadedTo: '2026-10-18',
      mediaCategory: 'image', ownerKeyword: '何 琴', afterId: '41', limit: '2',
    });
  });

  it('待关联页仅传目标与附件筛选，保留默认分页参数', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => ({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data: { items: [], nextCursor: null, hasMore: false } }),
    } as Response));
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchHistoricalEvidencePage({
      targetType: 'MOTHER_ORDER', targetCode: 'MD-1', stage: 'INTAKE',
      operationCode: 'OP-1', mediaCategory: 'video', ownerKeyword: '张',
    })).resolves.toEqual({ items: [], nextCursor: null, hasMore: false });

    const url = new URL(String(fetchMock.mock.calls[0][0]), 'https://local.test');
    expect(url.pathname).toBe('/api/documents/evidence-links/historical/page');
    expect(Object.fromEntries(url.searchParams)).toEqual({
      targetType: 'MOTHER_ORDER', targetCode: 'MD-1', mediaCategory: 'video', ownerKeyword: '张',
    });
  });
});
