import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import FixedAssetDispositionPane from './FixedAssetDispositionPane.vue';
import { fetchAssetDispositions, recordAssetDisposition } from '../../services/fixedAssetApi';

vi.mock('../../services/fixedAssetApi', () => ({
  fetchAssetDispositions: vi.fn().mockResolvedValue([]),
  recordAssetDisposition: vi.fn(),
}));

describe('资产处置', () => {
  it('报废不显示已收款，没有收款单不能填写回款', async () => {
    vi.mocked(recordAssetDisposition).mockResolvedValue({
      dispositionId: 'FIN-DSP-1',
      assetCode: 'FIN-AST-1',
      originalValue: 1000,
      kind: 'SCRAP',
      kindLabel: '报废',
      occurredOn: '2026-10-20',
      basis: '无法修复',
      depreciationImpact: 'PENDING',
      depreciationImpactLabel: '待核',
      netValue: null,
      receiptCode: '',
      proceedsAmount: null,
      received: false,
    });
    const wrapper = mount(FixedAssetDispositionPane);
    expect(wrapper.text()).toContain('不扣员工工资');
    await wrapper.get('[aria-label="处置资产编码"]').setValue('FIN-AST-1');
    await wrapper.get('[aria-label="处置日期"]').setValue('2026-10-20');
    await wrapper.get('[aria-label="处置依据"]').setValue('无法修复');
    await wrapper.get('[aria-label="回款金额"]').setValue('300');
    await wrapper.get('form').trigger('submit');
    expect(recordAssetDisposition).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('必须另有真实资金来源');

    await wrapper.get('[aria-label="回款金额"]').setValue('');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(recordAssetDisposition).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'SCRAP',
      receiptCode: '',
      proceeds: null,
    }));
    expect(wrapper.text()).toContain('未收款，净值待核');
    expect(wrapper.text()).not.toContain('已收款');
    wrapper.unmount();
  });
});
