import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, ref } from 'vue';
import type { InventoryBalance } from '../../data/m1PrototypeData';
import OrderActionHost from './OrderActionHost.vue';

vi.mock('../inventory/InventoryAftercarePanel.vue', () => ({ default: defineComponent({ name: 'InventoryAftercarePanel', props: ['row', 'active'], emits: ['saved', 'newRequest'], setup() { return { code: ref('') }; }, template: '<input v-model="code" aria-label="退售后单号草稿" />' }) }));
const hosts: Array<ReturnType<typeof mount<typeof OrderActionHost>>> = [];
afterEach(() => hosts.splice(0).forEach(wrapper => wrapper.unmount()));
async function ready() { await flushPromises(); await vi.waitFor(() => expect(document.querySelector('[data-action-active="true"] input')).not.toBeNull()); }
function row(warehouseCode: string): InventoryBalance { return { owner: '客户', batch: 'WO-1', material: '物料', location: warehouseCode, status: '已发', qty: 10.5, quantityUnit: '克', weight: '10.5 克', frozen: '0', operationContext: { customerId: 1, ownerCustomerId: 2, ownerType: 'CUSTOMER', motherOrderCode: 'MO-1', workOrderCode: 'WO-1', materialCode: 'MAT', warehouseCode, stockStatus: 'SHIPPED', quantity: 10.5, weight: 10.5 } }; }
describe('退售后动作缓存', () => {
  it('按完整维度保留实例，关闭后成功仍刷新；只有明确下一笔才新建草稿', async () => {
    const wrapper = mount(OrderActionHost, { props: { canIntake: false, canAftercare: false } }); hosts.push(wrapper);
    const original = row('WH-A');
    wrapper.vm.openAftercare(original);
    await flushPromises();
    expect(wrapper.emitted('active')).toBeUndefined();
    await wrapper.setProps({ canAftercare: true });
    wrapper.vm.openAftercare(original);
    await ready();
    const first = wrapper.getComponent({ name: 'InventoryAftercarePanel' });
    const dom = new DOMWrapper(document.body);
    await dom.get('[data-action-active="true"] input').setValue('RET-ORIGINAL');
    original.operationContext!.quantity = 999;
    expect(first.props('row').operationContext.quantity).toBe(10.5);
    await dom.get('.order-action-dialog header button').trigger('click');
    first.vm.$emit('saved');
    expect(wrapper.emitted('changed')).toHaveLength(1);
    wrapper.vm.openAftercare(row('WH-B'));
    await ready();
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', '');
    wrapper.vm.openAftercare(row('WH-A'));
    await ready();
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', 'RET-ORIGINAL');
    first.vm.$emit('newRequest', { ...row('WH-A'), qty: 2.25, operationContext: { ...row('WH-A').operationContext!, quantity: 2.25, weight: 2.25 } });
    await ready();
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', '');
    expect(first.vm.$.isUnmounted).toBe(false);
    await dom.get('[data-action-active="true"] input').setValue('RET-NEXT');
    await dom.get('.order-action-dialog header button').trigger('click');
    wrapper.vm.openAftercare(row('WH-A'));
    await ready();
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', 'RET-NEXT');
    await wrapper.setProps({ canAftercare: false });
    expect(dom.find('[data-action-active="true"]').exists()).toBe(false);
  });
});
