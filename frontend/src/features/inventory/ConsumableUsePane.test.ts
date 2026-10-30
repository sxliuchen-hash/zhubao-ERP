import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import ConsumableUsePane from './ConsumableUsePane.vue';
import { consumeConsumable, issueConsumable } from '../../services/consumableStockApi';

vi.mock('../../services/consumableStockApi', () => ({
  issueConsumable: vi.fn().mockResolvedValue({}),
  consumeConsumable: vi.fn().mockResolvedValue({}),
}));

describe('耗材实际耗用', () => {
  it('未选定不提交，空金额不写成 0，切换物品不带走金额', async () => {
    const wrapper = mount(ConsumableUsePane, {
      props: { itemId: 'FI-G', receiptId: 'GS-1', issueId: 'LY-1', issueVersion: 1, timing: 'UNDECIDED' },
    });
    await wrapper.get('[aria-label="耗用数量"]').setValue('2');
    await wrapper.find('button').trigger('click');
    expect(issueConsumable).not.toHaveBeenCalled();
    expect(consumeConsumable).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('尚未选定');

    await wrapper.setProps({ timing: 'ON_ISSUE' });
    await wrapper.get('[aria-label="耗用金额"]').setValue('');
    await wrapper.find('button').trigger('click');
    await flushPromises();
    expect(issueConsumable).toHaveBeenCalledWith(expect.objectContaining({ itemId: 'FI-G', quantity: '2' }));
    expect(JSON.stringify(vi.mocked(issueConsumable).mock.calls[0][0])).not.toContain('"quantity":"0"');
    expect(wrapper.text()).toContain('不生成客户收费');
    expect((wrapper.get('[aria-label="耗用金额"]').element as HTMLInputElement).value).toBe('');

    await wrapper.setProps({ itemId: 'FI-H', timing: 'ON_ACTUAL_USE' });
    expect((wrapper.get('[aria-label="耗用金额"]').element as HTMLInputElement).value).toBe('');
    expect((wrapper.get('[aria-label="耗用数量"]').element as HTMLInputElement).value).toBe('');
    await wrapper.get('[aria-label="耗用数量"]').setValue('3');
    await wrapper.find('button').trigger('click');
    await flushPromises();
    expect(consumeConsumable).toHaveBeenCalledWith('LY-1', expect.objectContaining({ quantity: '3', version: 1 }));
    expect(wrapper.text()).toContain('金额未齐');
    wrapper.unmount();
  });
});
