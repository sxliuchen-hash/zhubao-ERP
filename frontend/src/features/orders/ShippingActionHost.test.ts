import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, ref } from 'vue';
import type { ScanBalanceCandidate } from '../../services/scanContextApi';
import OrderActionHost from './OrderActionHost.vue';

vi.mock('./ShippingActionPane.vue', () => ({ default: defineComponent({
  name: 'ShippingActionPane', props: ['target', 'balance', 'active'], emits: ['changed'],
  setup() { return { draft: ref('') }; }, template: '<input v-model="draft" aria-label="发货动作草稿" />',
}) }));
vi.mock('./OrderActionLocator.vue', () => ({ default: defineComponent({ name: 'OrderActionLocator', props: ['active', 'initialTarget'], emits: ['selected'], template: '<div data-locator />' }) }));
const hosts: Array<ReturnType<typeof mount<typeof OrderActionHost>>> = [];
afterEach(() => hosts.splice(0).forEach(wrapper => wrapper.unmount()));
async function ready(selector: string) {
  await flushPromises();
  await vi.waitFor(() => expect(document.querySelector(`[data-action-active="true"] ${selector}`)).not.toBeNull());
}
const stock = (warehouseCode: string): ScanBalanceCandidate => ({ ownerType: 'CUSTOMER', customerId: 1, ownerCustomerId: 1, motherOrderCode: 'MO-1', workOrderCode: 'WO-1', materialCode: 'MAT-1', warehouseCode, stockStatus: 'FINISHED', quantity: 10.5, weight: 10.5 });

describe('发货单内容器', () => {
  it('旧单号定位后保留完整库存维度，相同单的不同仓库分别保留草稿', async () => {
    const wrapper = mount(OrderActionHost, { props: { canIntake: false, canShipping: true } });
    hosts.push(wrapper);
    wrapper.vm.openShipping({ objectType: null, code: 'WO-1', balance: stock('WH-A') });
    await ready('[data-locator]');
    wrapper.getComponent({ name: 'OrderActionLocator' }).vm.$emit('selected', { objectType: 'WORK_ORDER', code: 'WO-1' });
    await ready('input');
    const first = wrapper.getComponent({ name: 'ShippingActionPane' });
    expect(first.props('balance')).toEqual(stock('WH-A'));
    const dom = new DOMWrapper(document.body);
    await dom.get('[data-action-active="true"] input').setValue('A 原物流与数量');
    wrapper.vm.openShipping({ objectType: 'WORK_ORDER', code: 'WO-1', balance: stock('WH-B') });
    await ready('input');
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', '');
    wrapper.vm.openShipping({ objectType: 'WORK_ORDER', code: 'WO-1', balance: stock('WH-A') });
    await ready('input');
    expect(wrapper.findAllComponents({ name: 'ShippingActionPane' })[0].vm.$).toBe(first.vm.$);
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', 'A 原物流与数量');
    await dom.get('.order-action-dialog header button').trigger('click');
    first.vm.$emit('changed');
    expect(wrapper.emitted('changed')).toHaveLength(1);
    await wrapper.setProps({ canShipping: false });
    wrapper.vm.openShipping({ objectType: 'WORK_ORDER', code: 'WO-2' });
    await flushPromises();
    expect(dom.find('[data-action-active="true"]').exists()).toBe(false);
  });
});
