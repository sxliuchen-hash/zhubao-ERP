import { afterEach, expect, it, vi } from 'vitest';
import { downloadEvidence } from './customerPortalApi';
import { setAuthToken } from '../../services/apiClient';

afterEach(() => { localStorage.clear(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });

it('获准文件下载取得Blob并触发当前文件保存', async () => {
  const blob = new Blob(['文件字节'], { type: 'image/png' });
  const fetch = vi.fn().mockResolvedValue({ ok: true, blob: async () => blob });
  vi.stubGlobal('fetch', fetch);
  const create = vi.fn().mockReturnValue('blob:customer-file');
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: create });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  setAuthToken('customer-token');
  const result = await downloadEvidence({ grantId: 'G1', attachmentCode: 'A1', fileName: '照片.png', mediaType: 'image/png', kind: 'IMAGE', downloadTicket: 'T1' });
  expect(result.fileName).toBe('照片.png');
  expect(create).toHaveBeenCalledWith(blob);
  expect(click).toHaveBeenCalledOnce();
  expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/evidence/downloads'), expect.objectContaining({
    headers: expect.objectContaining({ Authorization: 'Bearer customer-token' }),
    body: JSON.stringify({ grantId: 'G1', attachmentCode: 'A1', ticket: 'T1' }),
  }));
});
