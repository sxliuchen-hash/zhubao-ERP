import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import ToolLoanPane from './ToolLoanPane.vue';
import { borrowTool, returnTool } from '../../services/toolLoanApi';

vi.mock('../../services/toolLoanApi', () => ({
  borrowTool: vi.fn().mockResolvedValue({}),
  returnTool: vi.fn().mockResolvedValue({}),
}));

describe('工具借还', () => {
  it('同名人员分开显示，归还不把未还改成 0', async () => {
    const wrapper = mount(ToolLoanPane, {
      props: {
        itemId: 'FI-T',
        warehouseCode: '柜-1',
        stockQuantity: '4',
        unit: '把',
        loanId: 'TL-1',
        loanVersion: 1,
        holders: [
          { holderId: 'P-01', holderName: '张三', outstanding: '6', unit: '把' },
          { holderId: 'P-02', holderName: '张三', outstanding: '3', unit: '把' },
        ],
      },
    });
    expect(wrapper.text()).toContain('库内 柜-1 4把');
    expect(wrapper.text()).toContain('未还 P-01 张三 6把');
    expect(wrapper.text()).toContain('未还 P-02 张三 3把');
    expect(wrapper.findAll('button').some(button => button.text() === '未还改成0')).toBe(false);
    await wrapper.get('[aria-label="持有人编号"]').setValue('P-02');
    await wrapper.get('[aria-label="持有人姓名"]').setValue('张三');
    await wrapper.get('[aria-label="借出数量"]').setValue('1');
    await wrapper.findAll('button').find(button => button.text() === '借出')!.trigger('click');
    await flushPromises();
    expect(borrowTool).toHaveBeenCalledWith(expect.objectContaining({ holderId: 'P-02', holderName: '张三', quantity: '1' }));
    await wrapper.get('[aria-label="归还数量"]').setValue('4');
    await wrapper.findAll('button').find(button => button.text() === '归还')!.trigger('click');
    await flushPromises();
    expect(returnTool).toHaveBeenCalledWith('TL-1', expect.objectContaining({ quantity: '4', version: 1 }));
    expect(wrapper.text()).not.toContain('未还 0');
    wrapper.unmount();
  });
});
