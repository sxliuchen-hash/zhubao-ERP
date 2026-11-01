import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import FixedAssetDepreciationPane from './FixedAssetDepreciationPane.vue';
import { continueAssetDepreciation } from '../../services/fixedAssetApi';

vi.mock('../../services/fixedAssetApi', () => ({
  continueAssetDepreciation: vi.fn(),
}));

describe('资产折旧延续', () => {
  it('不提交未核定的生效月，并说明只是管理算法', async () => {
    vi.mocked(continueAssetDepreciation).mockResolvedValue({
      yearMonth: '2026-10',
      algorithm: 'USEFUL_MONTHS_WITH_FINAL_TAIL',
      awaitingAssetCodes: ['STOPPED'],
      entries: [{ code: 'FIN-DEP-1', assetCode: 'FIN-AST-1', yearMonth: '2026-10', amount: 333.33 }],
    });
    const wrapper = mount(FixedAssetDepreciationPane);
    expect(wrapper.text()).toContain('不是全部会计或税务处理');
    await wrapper.get('[aria-label="停用生效月"]').setValue('2026-11');
    await wrapper.get('[aria-label="折旧月份"]').setValue('2026-10');
    await wrapper.get('form').trigger('submit');
    expect(continueAssetDepreciation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('尚未核定');

    await wrapper.get('[aria-label="停用生效月"]').setValue('');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(continueAssetDepreciation).toHaveBeenCalledWith(expect.objectContaining({
      yearMonth: '2026-10',
      stopEffectiveMonth: null,
    }));
    expect(wrapper.text()).toContain('不是税务结论');
    expect(wrapper.text()).toContain('333.33');
    expect(wrapper.text()).toContain('STOPPED');
    expect(wrapper.text()).toContain('本次未新增');
    wrapper.unmount();
  });
});
