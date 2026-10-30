import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import DeliveryAftercarePane from './DeliveryAftercarePane.vue';

describe('交付更正与退货', () => {
  it('不覆盖原单，超量退货不提交，退回后仍显示待复核', async () => {
    const correct = vi.fn().mockRejectedValue(new Error('不能以取消重开抹平历史'));
    const receive = vi.fn().mockResolvedValue(undefined);
    const wrapper = mount(DeliveryAftercarePane, {
      props: {
        deliveryId: 'FH-20261030-0001',
        originalQuantity: '80',
        returnedQuantity: '30',
        hasCharge: true,
        hasRefund: true,
        correct,
        receive,
      },
    });
    expect(wrapper.text()).toContain('原发货单 FH-20261030-0001');
    expect(wrapper.text()).toContain('已确认费用仍保留');
    expect(wrapper.text()).toContain('已确认退款仍保留');
    await wrapper.findAll('button').find(button => button.text() === '取消并重开')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('不能以取消重开抹平历史');
    expect(wrapper.text()).toContain('FH-20261030-0001');
    await wrapper.get('[aria-label="退货数量"]').setValue('60');
    await wrapper.findAll('button').find(button => button.text() === '办理退货')!.trigger('click');
    expect(receive).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('退货数量不得超过有效原交付');
    await wrapper.get('[aria-label="退货数量"]').setValue('50');
    await wrapper.findAll('button').find(button => button.text() === '办理退货')!.trigger('click');
    await flushPromises();
    expect(receive).toHaveBeenCalledWith('50');
    expect(wrapper.text()).toContain('待复核');
    expect(wrapper.text()).not.toContain('已合格');
    wrapper.unmount();
  });
});
