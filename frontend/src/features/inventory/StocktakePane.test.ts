import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import StocktakePane from './StocktakePane.vue';
import { confirmStocktake } from '../../services/stocktakeApi';

vi.mock('../../services/stocktakeApi', () => ({
  confirmStocktake: vi.fn().mockResolvedValue({}),
}));

describe('实盘和差异复核', () => {
  it('账面和未还分开，重复确认不重复提交', async () => {
    const denied = mount(StocktakePane, {
      props: { stocktakeId: 'PD-1', version: 2, bookQuantity: '4', outstandingQuantity: '6', unit: '把', canConfirm: false },
    });
    expect(denied.text()).toContain('账面 4把');
    expect(denied.text()).toContain('未还 6把，不计入本次实盘');
    expect(denied.find('[aria-label="总余额"]').exists()).toBe(false);
    await denied.get('[aria-label="实盘数量"]').setValue('5');
    await denied.find('button').trigger('click');
    expect(confirmStocktake).not.toHaveBeenCalled();
    expect(denied.text()).toContain('没有差异确认权限');
    denied.unmount();

    const wrapper = mount(StocktakePane, {
      props: { stocktakeId: 'PD-1', version: 2, bookQuantity: '4', outstandingQuantity: '6', unit: '把', canConfirm: true },
    });
    await wrapper.find('button').trigger('click');
    expect(confirmStocktake).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请填写实盘数量');
    await wrapper.get('[aria-label="实盘数量"]').setValue('5');
    await wrapper.find('button').trigger('click');
    await flushPromises();
    expect(confirmStocktake).toHaveBeenCalledTimes(1);
    expect(confirmStocktake).toHaveBeenCalledWith('PD-1', { requestId: 'count-PD-1', version: 2 });
    expect(wrapper.text()).toContain('原账面保留');
    await wrapper.find('button').trigger('click');
    expect(confirmStocktake).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('盘点已经确认');
    wrapper.unmount();
  });
});
