import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import MultiSourceShippingPane from './MultiSourceShippingPane.vue';

const bags = [
  { bagId: 'BAG-A', version: 1, quantity: '120', unit: '颗', shippable: true, reason: '' },
  { bagId: 'BAG-B', version: 2, quantity: '30', unit: '颗', shippable: false, reason: '外协未回' },
];

describe('多来源发货', () => {
  it('核对后一次确认，失败保留原请求，打印不出库', async () => {
    const confirm = vi.fn()
      .mockRejectedValueOnce(new Error('网络失败'))
      .mockResolvedValueOnce({ deliveryId: 'FH-20261030-0001' });
    const wrapper = mount(MultiSourceShippingPane, { props: { orderCode: 'MD-1', bags, confirm } });
    expect(wrapper.text()).not.toContain('发货单号');
    await wrapper.get('[aria-label="选择BAG-A"]').setValue(true);
    await wrapper.get('[aria-label="已核对所选袋"]').setValue(true);
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('网络失败');
    expect(wrapper.text()).not.toContain('FH-20261030-0001');
    const firstRequest = confirm.mock.calls[0][0];
    await wrapper.get('button[type="button"]:nth-of-type(2)').trigger('click');
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('打印不会出库');
    const buttons = wrapper.findAll('button');
    await buttons[buttons.length - 1].trigger('click');
    await flushPromises();
    expect(confirm.mock.calls[1][0]).toBe(firstRequest);
    expect(confirm.mock.calls[1][1]).toEqual(['BAG-A']);
    expect(wrapper.text()).toContain('发货单号 FH-20261030-0001');

    await wrapper.setProps({ orderCode: 'MD-2' });
    await nextTick();
    expect(wrapper.text()).not.toContain('FH-20261030-0001');
    wrapper.unmount();
  });

  it('不可发袋核对后也不能确认', async () => {
    const confirm = vi.fn();
    const wrapper = mount(MultiSourceShippingPane, { props: { orderCode: 'MD-1', bags, confirm } });
    await wrapper.get('[aria-label="选择BAG-B"]').setValue(true);
    await wrapper.get('[aria-label="已核对所选袋"]').setValue(true);
    expect(wrapper.text()).toContain('外协未回');
    const button = wrapper.findAll('button').find(item => item.text() === '确认发货')!;
    expect(button.attributes('disabled')).toBeDefined();
    await button.trigger('click');
    expect(confirm).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
