import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import type { LabelPayload } from '../../services/labelApi';
import { renderLabelPreviewImage } from './labelPreviewImage';
import LabelPreview from './LabelPreview.vue';

vi.mock('./labelPreviewImage', () => ({ renderLabelPreviewImage: vi.fn() }));
const payload: LabelPayload = { type: 'MOTHER', typeLabel: '母单', primaryCode: 'MD-1', qrContent: 'JG|mother|MD-1|0|LI1', fields: [], footerLeft: '', footerRight: '', printedAt: '', brandTitle: '', templateCode: '', printerSetting: { widthMm: 40, heightMm: 30, dpi: 300, printerName: '', copies: 1 } };

describe('最终标签画面', () => {
  it('显示、下载和传给打印任务的图片使用相同字节', async () => {
    const image = { snapshotHash: 'a'.repeat(64), pngBase64: 'same-png-bytes' };
    vi.mocked(renderLabelPreviewImage).mockResolvedValueOnce(image);
    const wrapper = mount(LabelPreview, { props: { payload } });
    await flushPromises();
    expect(wrapper.get('img').attributes('src')).toBe(`data:image/png;base64,${image.pngBase64}`);
    expect(wrapper.get('a').attributes('href')).toBe(wrapper.get('img').attributes('src'));
    expect(wrapper.emitted('ready')).toEqual([[null], [image]]);
    wrapper.unmount();
  });

  it('切换标签后丢弃迟到的旧画面，生成失败不保留旧图', async () => {
    let finish!: (value: { snapshotHash: string; pngBase64: string }) => void;
    vi.mocked(renderLabelPreviewImage).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
      .mockRejectedValueOnce(new Error('画面生成失败'));
    const wrapper = mount(LabelPreview, { props: { payload } });
    await wrapper.setProps({ payload: { ...payload, primaryCode: 'MD-2' } });
    await flushPromises();
    finish({ snapshotHash: '', pngBase64: 'old-image' });
    await flushPromises();
    expect(wrapper.find('img').exists()).toBe(false);
    expect(wrapper.get('[role=alert]').text()).toBe('画面生成失败');
    expect(wrapper.emitted('ready')).toEqual([[null], [null]]);
    wrapper.unmount();
  });
});
