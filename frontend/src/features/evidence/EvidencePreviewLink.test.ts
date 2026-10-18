import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchAttachmentPreviewUrl } from '../../services/documentManagementApi';
import EvidencePreviewLink from './EvidencePreviewLink.vue';

vi.mock('../../services/documentManagementApi', () => ({ fetchAttachmentPreviewUrl: vi.fn() }));

const image = {
  code: 'ATT-1', fileName: '现场.jpg', mediaType: 'image/jpeg',
  url: '/api/documents/attachments/ATT-1/content?expiresAt=99&purpose=preview&resolution=original&token=abc', expiresAt: '稍后',
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
    expect(fetchAttachmentPreviewUrl).toHaveBeenCalledWith('ATT-1', 'preview', 'original');
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

  it('下载时申请专用签名地址并保留原文件名', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockImplementation(async (_code, purpose) => ({
      ...image,
      url: image.url.replace('purpose=preview', `purpose=${purpose}`),
    }));
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
    expect(fetchAttachmentPreviewUrl).toHaveBeenNthCalledWith(2, 'ATT-1', 'download', 'original');
    expect(click).toHaveBeenCalledOnce();
    expect(downloaded[0]).toMatchObject({ name: '现场.jpg' });
    expect(downloaded[0].href).toContain('purpose=download&resolution=original');
    expect(downloaded[0].href).not.toContain('download=true');
    expect(document.querySelector('a[href*="purpose=download"]')).toBeNull();
  });

  it('拒绝将预览地址充当下载地址', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockResolvedValue(image);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click');
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-1' } });
    await wrapper.findAll('button')[1].trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('附件地址无效');
    expect(click).not.toHaveBeenCalled();
  });

  it('PDF 不在页面内预览，但可使用下载用途地址', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockImplementation(async (_code, purpose) => ({
      ...image,
      fileName: '报告.pdf',
      mediaType: 'application/pdf',
      url: image.url.replace('purpose=preview', `purpose=${purpose}`),
    }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-1' } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('请下载查看');
    expect(document.querySelector('.evidence-media-overlay')).toBeNull();
    await wrapper.findAll('button')[1].trigger('click');
    await flushPromises();
    expect(fetchAttachmentPreviewUrl).toHaveBeenLastCalledWith('ATT-1', 'download', 'original');
    expect(click).toHaveBeenCalledOnce();
  });

  it('拒绝非本站附件地址并显示错误', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockResolvedValue({ ...image, url: 'https://example.com/file' });
    const wrapper = mount(EvidencePreviewLink, { props: { attachmentCode: 'ATT-1' } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('附件地址无效');
  });

  it('图片先取缩略，点开轻量预览，再显式查看原图', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockImplementation(async (_code, purpose, resolution = 'original') => ({
      ...image,
      url: image.url.replace('purpose=preview', `purpose=${purpose}`)
        .replace('resolution=original', `resolution=${resolution}`),
    }));
    const wrapper = mount(EvidencePreviewLink, {
      props: { attachmentCode: 'ATT-1', mediaTypeHint: 'image/jpeg' },
    });
    await flushPromises();
    expect(wrapper.get('.evidence-thumbnail').attributes('src')).toContain('resolution=thumbnail');
    expect(fetchAttachmentPreviewUrl).toHaveBeenNthCalledWith(1, 'ATT-1', 'preview', 'thumbnail');

    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(document.querySelector('.evidence-media-overlay img')?.getAttribute('src')).toContain('resolution=preview');
    expect(fetchAttachmentPreviewUrl).toHaveBeenNthCalledWith(2, 'ATT-1', 'preview', 'preview');

    const originalButton = Array.from(document.querySelectorAll('.evidence-media-overlay button'))
      .find(button => button.textContent?.includes('查看原图')) as HTMLButtonElement;
    originalButton.click();
    await flushPromises();
    expect(document.querySelector('.evidence-media-overlay img')?.getAttribute('src')).toContain('resolution=original');
    expect(fetchAttachmentPreviewUrl).toHaveBeenNthCalledWith(3, 'ATT-1', 'preview', 'original');
  });

  it('视频列表取封面，播放仍取支持分段的原件地址', async () => {
    vi.mocked(fetchAttachmentPreviewUrl).mockImplementation(async (_code, purpose, resolution = 'original') => ({
      ...image,
      mediaType: resolution === 'poster' ? 'image/jpeg' : 'video/mp4',
      url: image.url.replace('purpose=preview', `purpose=${purpose}`)
        .replace('resolution=original', `resolution=${resolution}`),
    }));
    const wrapper = mount(EvidencePreviewLink, {
      props: { attachmentCode: 'ATT-1', mediaTypeHint: 'video/mp4' },
    });
    await flushPromises();
    expect(wrapper.get('.evidence-thumbnail').attributes('src')).toContain('resolution=poster');
    expect(fetchAttachmentPreviewUrl).toHaveBeenNthCalledWith(1, 'ATT-1', 'preview', 'poster');

    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(document.querySelector('.evidence-media-overlay video')?.getAttribute('src')).toContain('resolution=original');
    expect(document.querySelector('.evidence-media-overlay video')?.getAttribute('poster')).toContain('resolution=poster');
    expect(fetchAttachmentPreviewUrl).toHaveBeenNthCalledWith(2, 'ATT-1', 'preview', 'original');
  });
});
