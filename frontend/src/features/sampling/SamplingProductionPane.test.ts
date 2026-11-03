import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import SamplingProductionPane from './SamplingProductionPane.vue';
import { pickSamplingMaterial, reportSamplingOperation } from '../../services/samplingApi';
import { uploadReportPhoto, type StoredEvidenceFile } from '../../services/evidenceApi';
vi.mock('../../services/evidenceApi', () => ({ uploadReportPhoto: vi.fn() }));

vi.mock('../../services/samplingApi', () => ({
  pickSamplingMaterial: vi.fn(),
  reportSamplingOperation: vi.fn(),
}));

describe('打样材料与生产拍照', () => {
  it('领用真实来源，没有照片不能报工', async () => {
    vi.mocked(pickSamplingMaterial).mockResolvedValue({
      useId: 'SU-1',
      samplingCode: 'DY-20261031-0001',
      owner: 'COMPANY',
      sourceId: 'RC-1',
      sourceLineNo: null,
      stockMoveId: 'LY-1',
      materialCode: 'ITEM',
      batchCode: 'B1',
      quantity: 2,
      unit: '颗',
      weightState: 'UNKNOWN',
      weightValue: null,
      returnedQuantity: 0,
      inputQuantity: null,
      version: 1,
    });
    const wrapper = mount(SamplingProductionPane);
    expect(wrapper.find('input[type="file"]').exists()).toBe(true);
    await wrapper.get('[aria-label="生产打样号"]').setValue('DY-20261031-0001');
    await wrapper.get('[aria-label="公司收货"]').setValue('RC-1');
    await wrapper.get('[aria-label="车间"]').setValue('WS-DY');
    await wrapper.get('[aria-label="生产数量"]').setValue('2');
    await wrapper.findAll('button').find((button) => button.text() === '领用')!.trigger('click');
    await flushPromises();
    const picked = vi.mocked(pickSamplingMaterial).mock.calls[0][1];
    expect(picked.receiptId).toBe('RC-1');
    expect(picked.weight).toBeNull();
    expect(JSON.stringify(picked)).not.toContain('MD-');
    expect(wrapper.text()).toContain('权属 COMPANY');

    await wrapper.findAll('button').find((button) => button.text() === '报工')!.trigger('click');
    expect(wrapper.text()).toContain('没有有效的现场照片');
    expect(reportSamplingOperation).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="加工选择"]').setValue('BOTH');
    vi.mocked(uploadReportPhoto).mockResolvedValue({ code: 'ATT-1' } as StoredEvidenceFile);
    const photo = wrapper.findAll('input[type="file"]')[0];
    Object.defineProperty(photo.element, 'files', { configurable: true, value: [new File(['photo'], 'photo.png', { type: 'image/png' })] });
    await photo.trigger('change');
    await flushPromises();
    expect(uploadReportPhoto).toHaveBeenCalledWith('SAMPLING', 'DY-20261031-0001', expect.any(File));
    await wrapper.findAll('button').find((button) => button.text() === '报工')!.trigger('click');
    expect(reportSamplingOperation).toHaveBeenCalledWith('DY-20261031-0001', expect.objectContaining({
      makingChoices: ['CARVE', 'SHAPE'],
    }));
  });
});
