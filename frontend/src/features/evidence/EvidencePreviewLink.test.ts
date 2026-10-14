import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchAttachmentPreviewUrl } from '../../services/documentManagementApi';
import EvidencePreviewLink from './EvidencePreviewLink.vue';

vi.mock('../../services/documentManagementApi', () => ({ fetchAttachmentPreviewUrl: vi.fn() }));

describe('证据预览', () => {
  afterEach(() => vi.restoreAllMocks());

  it('点击时重新生成签名链接，在浏览器新标签页打开', async () => {
    const opened = { closed: false, opener: window, location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window);
    vi.mocked(fetchAttachmentPreviewUrl).mockResolvedValue({
      code: 'ATT-1', fileName: '现场.jpg', mediaType: 'image/jpeg',
      url: '/api/documents/attachments/ATT-1/content?expiresAt=99&token=abc', expiresAt: '稍后',
    });
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-1' } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(fetchAttachmentPreviewUrl).toHaveBeenCalledWith('ATT-1');
    expect(window.open).toHaveBeenCalledWith('', '_blank');
    expect(opened.opener).toBeNull();
    expect(opened.location.href).toBe(`${window.location.origin}/api/documents/attachments/ATT-1/content?expiresAt=99&token=abc`);
  });

  it('获取失败时关闭空白页并展示错误', async () => {
    const opened = { closed: false, opener: window, location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window);
    vi.mocked(fetchAttachmentPreviewUrl).mockRejectedValue(new Error('附件已下线'));
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-2' } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(opened.close).toHaveBeenCalledOnce();
    expect(wrapper.get('[role="alert"]').text()).toContain('附件已下线');
  });
});
