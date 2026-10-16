import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchAttachmentPreviewUrl } from '../../services/documentManagementApi';
import EvidencePreviewLink from './EvidencePreviewLink.vue';

vi.mock('../../services/documentManagementApi', () => ({ fetchAttachmentPreviewUrl: vi.fn() }));

const image = {
  code: 'ATT-1', fileName: '现场.jpg', mediaType: 'image/jpeg',
  url: '/api/documents/attachments/ATT-1/content?expiresAt=99&token=abc', expiresAt: '稍后',
};

describe('证据媒体预览与下载', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.querySelectorAll('.evidence-media-overlay').forEach(element => element.remove());
  });

  it('点击时申请新地址，在当前页面显示照片；收起后再次预览重新申请', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockResolvedValue(image);
    const open = vi.spyOn(window, 'open');
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-1' } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(document.querySelector('.evidence-media-overlay img')?.getAttribute('src')).toContain('/api/documents/attachments/ATT-1/content');
    expect(open).not.toHaveBeenCalled();
    await wrapper.get('button').trigger('click');
    expect(document.querySelector('.evidence-media-overlay')).toBeNull();
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(fetchAttachmentPreviewUrl).toHaveBeenCalledTimes(2);
  });

  it('视频加载失败时重新获取一次地址', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockResolvedValueOnce({ ...image, mediaType: 'video/mp4' })
      .mockResolvedValueOnce({ ...image, mediaType: 'video/mp4', url: image.url.replace('abc', 'new') });
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-1' } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    document.querySelector('video')?.dispatchEvent(new Event('error'));
    await flushPromises();
    expect(document.querySelector('video')?.getAttribute('src')).toContain('token=new');
    expect(fetchAttachmentPreviewUrl).toHaveBeenCalledTimes(2);
  });

  it('下载时单独获取新地址并设置下载参数和文件名', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockResolvedValue(image);
    const downloaded: { href: string; name: string }[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloaded.push({ href: this.href, name: this.download });
    });
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-1' } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    await wrapper.findAll('button')[1].trigger('click');
    await flushPromises();
    expect(fetchAttachmentPreviewUrl).toHaveBeenCalledTimes(2);
    expect(click).toHaveBeenCalledOnce();
    expect(downloaded[0]).toMatchObject({ name: '现场.jpg' });
    expect(downloaded[0].href).toContain('download=true');
    expect(document.querySelector('a[href*="download=true"]')).toBeNull();
  });

  it('拒绝非本站附件地址并显示错误', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockResolvedValue({ ...image, url: 'https://example.com/file' });
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-1' } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('附件地址无效');
  });
});
