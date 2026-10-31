import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import FixedAssetCustodyPane from './FixedAssetCustodyPane.vue';
import { fetchAssetCustody, transferAssetCustody } from '../../services/fixedAssetApi';

vi.mock('../../services/fixedAssetApi', () => ({
  fetchAssetCustody: vi.fn(),
  transferAssetCustody: vi.fn(),
}));

describe('资产位置交接', () => {
  it('前后位置可查，空项不提交，切换资产不带走位置', async () => {
    vi.mocked(fetchAssetCustody).mockResolvedValue([
      {
        transferId: 'FIN-MOV-1',
        assetCode: 'FIN-AST-1',
        originalValue: 1000,
        fromLocationLabel: '待补',
        toLocationLabel: '抛光间',
        fromCustodianLabel: '待补',
        toCustodianLabel: '甲',
        fromStatusLabel: '待补',
        toStatusLabel: '在用',
        version: 1,
      },
    ]);
    vi.mocked(transferAssetCustody).mockResolvedValue({
      transferId: 'FIN-MOV-2',
      assetCode: 'FIN-AST-1',
      originalValue: 1000,
      fromLocationLabel: '抛光间',
      toLocationLabel: '仓库',
      fromCustodianLabel: '甲',
      toCustodianLabel: '甲',
      fromStatusLabel: '在用',
      toStatusLabel: '在用',
      version: 2,
    });
    const wrapper = mount(FixedAssetCustodyPane);
    await wrapper.get('[aria-label="交接资产编码"]').setValue('FIN-AST-1');
    await wrapper.get('[aria-label="查询交接"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('待补 到 抛光间');
    expect(wrapper.text()).toContain('原值 1000');
    expect((wrapper.get('[aria-label="交接版本"]').element as HTMLInputElement).value).toBe('1');

    await wrapper.get('form').trigger('submit');
    expect(transferAssetCustody).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('交接没有变化');

    await wrapper.get('[aria-label="新位置"]').setValue('仓库');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    const body = vi.mocked(transferAssetCustody).mock.calls[0][0];
    expect(body).toEqual(expect.objectContaining({
      assetCode: 'FIN-AST-1',
      expectedVersion: 1,
      location: '仓库',
      custodian: '',
      useStatus: '',
    }));
    expect(JSON.stringify(body)).not.toContain('originalValue');
    expect(wrapper.text()).not.toContain('报废');

    await wrapper.get('[aria-label="交接资产编码"]').setValue('FIN-AST-2');
    await wrapper.get('[aria-label="交接资产编码"]').trigger('change');
    expect((wrapper.get('[aria-label="新位置"]').element as HTMLInputElement).value).toBe('');
    wrapper.unmount();
  });
});
