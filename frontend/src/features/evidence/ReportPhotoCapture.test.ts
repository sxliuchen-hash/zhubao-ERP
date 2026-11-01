import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, expect, it, vi } from 'vitest';
import ReportPhotoCapture from './ReportPhotoCapture.vue';
import { uploadReportPhoto, type StoredEvidenceFile } from '../../services/evidenceApi';
import { setAuthToken } from '../../services/apiClient';
vi.mock('../../services/evidenceApi', () => ({ uploadReportPhoto: vi.fn() }));
afterEach(() => { localStorage.clear(); vi.clearAllMocks(); });

it('自动上传失败保留同一照片，重试成功自动填入报工照片', async () => {
  setAuthToken('photo-retry');
  vi.mocked(uploadReportPhoto).mockRejectedValueOnce(new Error('网络断开')).mockResolvedValue({ code: 'PHOTO-1' } as StoredEvidenceFile);
  const view = mount(ReportPhotoCapture, { props: { businessType: 'SAMPLING', businessCode: 'DY-1', modelValue: '' } });
  const file = new File(['真实选中照片'], '拍照.png', { type: 'image/png' });
  const input = view.findAll('input[type="file"]')[0];
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] });
  await input.trigger('change');
  await flushPromises();
  expect(view.text()).toContain('网络断开');
  await view.get('button').trigger('click');
  await flushPromises();
  expect(uploadReportPhoto).toHaveBeenCalledTimes(2);
  expect(vi.mocked(uploadReportPhoto).mock.calls[0][2]).toBe(vi.mocked(uploadReportPhoto).mock.calls[1][2]);
  expect(view.emitted('update:modelValue')?.at(-1)).toEqual(['PHOTO-1']);
  view.unmount();
});

it('切单后旧照片上传结果不填入新单，切回仍保留旧单照片', async () => {
  setAuthToken('photo-target-switch');
  let finish!: (value: StoredEvidenceFile) => void;
  vi.mocked(uploadReportPhoto).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = mount(ReportPhotoCapture, { props: { businessType: 'GOLDWORK', businessCode: 'GJ-1', modelValue: '' } });
  const input = view.findAll('input[type="file"]')[0];
  Object.defineProperty(input.element, 'files', { configurable: true, value: [new File(['图'], 'photo.png', { type: 'image/png' })] });
  await input.trigger('change');
  await flushPromises();
  await view.setProps({ businessCode: 'GJ-2' });
  finish({ code: 'OLD-PHOTO' } as StoredEvidenceFile);
  await flushPromises();
  expect(view.emitted('update:modelValue')?.at(-1)).toEqual(['']);
  expect(view.text()).not.toContain('照片已上传');
  await view.setProps({ businessCode: 'GJ-1' });
  expect(view.emitted('update:modelValue')?.at(-1)).toEqual(['OLD-PHOTO']);
  view.unmount();
});
