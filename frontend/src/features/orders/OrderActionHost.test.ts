import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, ref } from 'vue';
import type { MobileIntakeResult } from '../../services/mobileIntakeApi';
import OrderActionHost from './OrderActionHost.vue';

vi.mock('../intake/IntakePanel.vue', () => ({ default: defineComponent({
  name: 'IntakePanel', props: ['field', 'title', 'intro'], emits: ['saved'],
  setup() { return { draft: ref('') }; },
  template: '<div><input v-model="draft" aria-label="动作草稿" /></div>',
}) }));
vi.mock('../labels/OrderLabelPanel.vue', () => ({ default: defineComponent({
  name: 'OrderLabelPanel', props: ['initialTarget', 'active'], setup() { return { reason: ref('') }; },
  template: '<div><input v-model="reason" aria-label="原标签原因" /><span>{{ initialTarget?.code }}</span></div>',
}) }));
vi.mock('../finance/CustomerBillPanel.vue', () => ({ default: defineComponent({
  name: 'CustomerBillPanel', props: ['context', 'initialMode'], setup() { return { amount: ref('') }; },
  template: '<div><input v-model="amount" aria-label="原收款草稿" /><span>{{ context.customerId }} · {{ initialMode }}</span></div>',
}) }));

async function opened(wrapper: ReturnType<typeof mount<typeof OrderActionHost>>) {
  await flushPromises();
  await vi.waitFor(() => expect(document.querySelector('[data-action-active="true"] [aria-label="动作草稿"]')).not.toBeNull(), { timeout: 5000 });
}
const mountedHosts: Array<ReturnType<typeof mount<typeof OrderActionHost>>> = [];
function host(allowed = true) {
  const wrapper = mount(OrderActionHost, { props: { canIntake: allowed } });
  mountedHosts.push(wrapper);
  return wrapper;
}
function dom() { return new DOMWrapper(document.body); }
afterEach(() => { mountedHosts.forEach(wrapper => wrapper.unmount()); mountedHosts.length = 0; });
describe('单内动作容器', () => {
  it('账单按客户和月份缓存，跨客户返回恢复原收款实例，非数字客户及撤权不能办理', async () => {
    const wrapper = host(); await wrapper.setProps({ canBill: true });
    wrapper.vm.openBill({ customerId: 0, yearMonth: '2026-10' }); await flushPromises();
    expect(document.querySelector('[aria-label="原收款草稿"]')).toBeNull();
    wrapper.vm.openBill({ customerId: 1001, yearMonth: '2026-10' }, 'receipt'); await flushPromises();
    await vi.waitFor(() => expect(document.querySelector('[aria-label="原收款草稿"]')).not.toBeNull());
    await dom().get('[data-action-active="true"] input').setValue('100.25');
    const first = wrapper.getComponent({ name: 'CustomerBillPanel' }).vm.$;
    await dom().get('.order-action-dialog header button').trigger('click');
    wrapper.vm.openBill({ customerId: 1002, yearMonth: '2026-10' }); await flushPromises();
    expect(dom().get('[data-action-active="true"] input').element).toHaveProperty('value', '');
    wrapper.vm.openBill({ customerId: 1001, yearMonth: '2026-10', receivableCode: 'AR-2' }, 'statement'); await flushPromises();
    expect(wrapper.getComponent({ name: 'CustomerBillPanel' }).vm.$).toBe(first);
    expect(dom().get('[data-action-active="true"] input').element).toHaveProperty('value', '100.25');
    await wrapper.setProps({ canBill: false });
    expect(wrapper.emitted('active')?.at(-1)).toEqual([false]); expect(first.isUnmounted).toBe(false);
  });
  it('标签草稿按精确类型单号保留，收起和打开其他单据不销毁原请求，权限撤销收起', async () => {
    const wrapper = host();
    wrapper.vm.openLabel({ objectType: 'WORK_ORDER', code: 'WO-A' }); await flushPromises();
    expect(document.querySelector('[aria-label="原标签原因"]')).toBeNull();
    await wrapper.setProps({ canLabel: true });
    wrapper.vm.openLabel({ objectType: 'WORK_ORDER', code: 'WO-A' }); await flushPromises();
    await vi.waitFor(() => expect(document.querySelector('[aria-label="原标签原因"]')).not.toBeNull());
    const first = wrapper.getComponent({ name: 'OrderLabelPanel' }).vm.$;
    await dom().get('[data-action-active="true"] input').setValue('A 原因');
    await dom().get('.order-action-dialog header button').trigger('click');
    wrapper.vm.openLabel({ objectType: 'MOTHER_ORDER', code: 'MO-B' }); await flushPromises();
    wrapper.vm.openLabel({ objectType: 'WORK_ORDER', code: 'WO-A' }); await flushPromises();
    expect(wrapper.getComponent({ name: 'OrderLabelPanel' }).vm.$).toBe(first);
    expect(dom().get('[data-action-active="true"] input').element).toHaveProperty('value', 'A 原因');
    await wrapper.setProps({ canLabel: false });
    expect(wrapper.emitted('active')?.at(-1)).toEqual([false]);
    expect(first.isUnmounted).toBe(false);
  });
  it('原生传送层收起不销毁草稿', async () => {
    const wrapper = host();
    wrapper.vm.openIntake();
    await flushPromises();
    await vi.waitFor(() => expect(document.querySelector('[aria-label="动作草稿"]')).not.toBeNull(), { timeout: 5000 });
    const pane = wrapper.getComponent({ name: 'IntakePanel' }).vm;
    await new DOMWrapper(document.body).get('.order-action-dialog header button').trigger('click');
    expect(pane.$.isUnmounted).toBe(false);
  });
  it('收起与重新打开同一目标恢复同一实例，不同目标不会共用草稿', async () => {
    const wrapper = host();
    wrapper.vm.openIntake({ objectType: 'MOTHER_ORDER', code: 'MO-A' });
    await opened(wrapper);
    await dom().get('[data-action-active="true"] input').setValue('A 的原填写');
    const first = wrapper.getComponent({ name: 'IntakePanel' }).vm;
    await dom().get('.order-action-dialog header button').trigger('click');
    expect(first.$.isUnmounted).toBe(false);
    expect(dom().get('input').isVisible()).toBe(false);
    wrapper.vm.openIntake({ objectType: 'MOTHER_ORDER', code: 'MO-B' });
    await opened(wrapper);
    expect(dom().get('[data-action-active="true"] input').element).toHaveProperty('value', '');
    await dom().get('[data-action-active="true"] input').setValue('B 的填写');
    await dom().get('.order-action-dialog header button').trigger('click');
    wrapper.vm.openIntake({ objectType: 'MOTHER_ORDER', code: 'MO-A' });
    await opened(wrapper);
    expect(wrapper.getComponent({ name: 'IntakePanel' }).vm.$).toBe(first.$);
    expect(dom().get('[data-action-active="true"] input').element).toHaveProperty('value', 'A 的原填写');
    expect(wrapper.emitted('active')?.at(-1)).toEqual([true]);
  });
  it('关闭后收到真实回执仍转发成功，Escape 关闭不销毁原表单', async () => {
    const wrapper = host();
    wrapper.vm.openIntake();
    await opened(wrapper);
    const pane = wrapper.getComponent({ name: 'IntakePanel' });
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();
    expect(pane.vm.$.isUnmounted).toBe(false);
    const receipt = { motherOrderCode: 'MO-SAVED', ledgerCode: 'LED-1' } as MobileIntakeResult;
    pane.vm.$emit('saved', receipt);
    await flushPromises();
    expect(wrapper.emitted('saved')).toEqual([[receipt]]);
    expect(wrapper.emitted('active')?.at(-1)).toEqual([false]);
    wrapper.vm.openIntake();
    await opened(wrapper);
    expect(wrapper.getComponent({ name: 'IntakePanel' }).vm.$).toBe(pane.vm.$);
  });
  it('没有有效权限不打开表单，权限撤销后立即收起可提交内容', async () => {
    const wrapper = host(false);
    wrapper.vm.openIntake();
    await flushPromises();
    expect(dom().find('input').exists()).toBe(false);
    expect(wrapper.emitted('active')).toBeUndefined();
    await wrapper.setProps({ canIntake: true });
    wrapper.vm.openIntake();
    await opened(wrapper);
    await wrapper.setProps({ canIntake: false });
    expect(dom().get('input').isVisible()).toBe(false);
    expect(wrapper.emitted('active')?.at(-1)).toEqual([false]);
  });
});
