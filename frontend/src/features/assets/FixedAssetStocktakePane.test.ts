import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import FixedAssetStocktakePane from './FixedAssetStocktakePane.vue';
import { confirmAssetStocktake, countAssetStocktake, openAssetStocktake } from '../../services/fixedAssetApi';

vi.mock('../../services/fixedAssetApi', () => ({
  openAssetStocktake: vi.fn(),
  countAssetStocktake: vi.fn(),
  confirmAssetStocktake: vi.fn(),
}));

const opened = {
  stocktakeId: 'FIN-PD-1',
  assetCode: 'FIN-AST-1',
  countedAt: '2026-10-20T09:00:00',
  bookLocation: null,
  bookCustodian: null,
  bookStatus: null,
  bookVersion: 0,
  seen: null,
  countLocation: null,
  countCustodian: null,
  exceptionText: '',
  status: 'OPEN',
  version: 1,
};

describe('资产逐件盘点', () => {
  it('未见不报废，未登记不能确认，切换资产不带走位置', async () => {
    vi.mocked(openAssetStocktake).mockResolvedValue(opened);
    vi.mocked(countAssetStocktake).mockResolvedValue({ ...opened, seen: 'ABSENT', version: 2 });
    vi.mocked(confirmAssetStocktake).mockResolvedValue({ ...opened, seen: 'ABSENT', status: 'CONFIRMED', version: 3 });
    const wrapper = mount(FixedAssetStocktakePane);
    expect(wrapper.text()).toContain('不报废');
    await wrapper.findAll('button').find((button) => button.text() === '确认差异')?.trigger('click');
    expect(confirmAssetStocktake).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请先登记实盘');

    await wrapper.get('[aria-label="盘点资产编码"]').setValue('FIN-AST-1');
    await wrapper.get('[aria-label="盘点时点"]').setValue('2026-10-20T09:00:00');
    await wrapper.get('[aria-label="打开盘点"]').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('账面位置待补');

    await wrapper.get('[aria-label="实盘结果"]').setValue('ABSENT');
    await wrapper.get('[aria-label="实盘位置"]').setValue('仓库');
    await wrapper.get('[aria-label="登记实盘"]').trigger('submit');
    await flushPromises();
    expect(countAssetStocktake).toHaveBeenCalledWith(expect.objectContaining({
      seen: 'ABSENT',
      location: '仓库',
      expectedVersion: 1,
    }));
    expect(wrapper.text()).toContain('不会自动报废');

    await wrapper.get('[aria-label="盘点资产编码"]').setValue('FIN-AST-2');
    await wrapper.get('[aria-label="盘点资产编码"]').trigger('change');
    expect((wrapper.get('[aria-label="实盘位置"]').element as HTMLInputElement).value).toBe('');

    await wrapper.findAll('button').find((button) => button.text() === '确认差异')?.trigger('click');
    await flushPromises();
    expect(confirmAssetStocktake).toHaveBeenCalledWith(expect.objectContaining({
      stocktakeId: 'FIN-PD-1',
      expectedVersion: 2,
    }));
    expect(wrapper.text()).toContain('原卡仍在');
    wrapper.unmount();
  });
});
