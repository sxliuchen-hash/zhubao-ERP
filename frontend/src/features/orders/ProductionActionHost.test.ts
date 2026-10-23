import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, ref } from 'vue';
import OrderActionHost from './OrderActionHost.vue';

vi.mock('./ProductionActionPane.vue', () => ({ default: defineComponent({
  name: 'ProductionActionPane', props: ['target', 'active', 'initialMode', 'entryRevision'], emits: ['changed', 'selected'],
  setup() { return { draft: ref('') }; },
  template: '<div><input v-model="draft" aria-label="工序动作草稿" /><span>{{ initialMode }}</span></div>',
}) }));
vi.mock('./OrderActionLocator.vue', () => ({ default: defineComponent({ name: 'OrderActionLocator', props: ['active', 'initialTarget'], emits: ['selected'], template: '<div data-locator />' }) }));
vi.mock('./OrderChangePanel.vue', () => ({ default: defineComponent({ name: 'OrderChangePanel', props: ['target'], emits: ['changed'], template: '<div data-change>{{ target.orderCode }} · {{ target.motherOrderCode }}</div>' }) }));
vi.mock('./QualityActionPane.vue', () => ({ default: defineComponent({
  name: 'QualityActionPane', props: ['target', 'active'], emits: ['changed', 'selected'],
  setup() { return { reason: ref('') }; }, template: '<input v-model="reason" aria-label="质检动作草稿" />',
}) }));
const hosts: Array<ReturnType<typeof mount<typeof OrderActionHost>>> = [];
function host(canProduction = true, canChange = true, canQuality = false) {
  const wrapper = mount(OrderActionHost, { props: { canIntake: false, canProduction, canChange, canQuality } });
  hosts.push(wrapper);
  return wrapper;
}
async function ready(selector: string) {
  await flushPromises();
  await vi.waitFor(() => expect(document.querySelector(`[data-action-active="true"] ${selector}`)).not.toBeNull());
}
afterEach(() => { hosts.splice(0).forEach(wrapper => wrapper.unmount()); });

describe('工序与改单动作容器', () => {
  it('质检按独立权限打开，收起与跨单返回保留原实例和未确认的原因', async () => {
    const wrapper = host(false, false, false);
    wrapper.vm.openQuality({ objectType: 'WORK_ORDER', code: 'WO-Q1' });
    await flushPromises();
    expect(wrapper.emitted('active')).toBeUndefined();
    await wrapper.setProps({ canQuality: true });
    wrapper.vm.openQuality({ objectType: 'WORK_ORDER', code: 'WO-Q1' });
    await ready('[aria-label="质检动作草稿"]');
    const first = wrapper.getComponent({ name: 'QualityActionPane' });
    const dom = new DOMWrapper(document.body);
    await dom.get('[data-action-active="true"] input').setValue('原单缺陷与原照片');
    await dom.get('.order-action-dialog header button').trigger('click');
    first.vm.$emit('changed');
    expect(wrapper.emitted('changed')).toHaveLength(1);
    wrapper.vm.openQuality({ objectType: 'WORK_ORDER', code: 'WO-Q2' });
    await ready('[aria-label="质检动作草稿"]');
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', '');
    wrapper.vm.openQuality({ objectType: 'WORK_ORDER', code: 'WO-Q1' });
    await ready('[aria-label="质检动作草稿"]');
    expect(wrapper.findAllComponents({ name: 'QualityActionPane' })[0].vm.$).toBe(first.vm.$);
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', '原单缺陷与原照片');
    await wrapper.setProps({ canQuality: false });
    expect(dom.find('[data-action-active="true"]').exists()).toBe(false);
    expect(wrapper.emitted('active')?.at(-1)).toEqual([false]);
  });
  it('未分类旧单先规范定位，精确目标两种入口保留同一草稿，迟到回执仍转发', async () => {
    const wrapper = host();
    wrapper.vm.openProduction({ objectType: null, code: 'WO-A' }, 'station');
    await ready('[data-locator]');
    expect(wrapper.findComponent({ name: 'ProductionActionPane' }).exists()).toBe(false);
    wrapper.getComponent({ name: 'OrderActionLocator' }).vm.$emit('selected', { objectType: 'WORK_ORDER', code: 'WO-A' });
    await ready('input');
    const first = wrapper.getComponent({ name: 'ProductionActionPane' });
    expect(first.props('initialMode')).toBe('station');
    const dom = new DOMWrapper(document.body);
    await dom.get('[data-action-active="true"] input').setValue('A 的进度与文件');
    await dom.get('.order-action-dialog header button').trigger('click');
    first.vm.$emit('changed');
    expect(wrapper.emitted('changed')).toHaveLength(1);
    wrapper.vm.openProduction({ objectType: 'WORK_ORDER', code: 'WO-B' });
    await ready('input');
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', '');
    wrapper.vm.openProduction({ objectType: 'WORK_ORDER', code: 'WO-A' }, 'progress');
    await ready('input');
    expect(wrapper.findAllComponents({ name: 'ProductionActionPane' })[0].vm.$).toBe(first.vm.$);
    expect(first.props('initialMode')).toBe('progress');
    expect(first.props('entryRevision')).toBe(2);
    expect(dom.get('[data-action-active="true"] input').element).toHaveProperty('value', 'A 的进度与文件');
    first.vm.$emit('selected', { objectType: 'WORK_ORDER', code: 'WO-CHILD' }, 'station');
    await ready('input');
    expect(wrapper.findAllComponents({ name: 'ProductionActionPane' }).at(-1)?.props('target')).toEqual({ objectType: 'WORK_ORDER', code: 'WO-CHILD' });
  });
  it('工序和改单独立核对能力，拒绝不支持对象与缺母单目标，撤权后收起', async () => {
    const wrapper = host(false, false);
    wrapper.vm.openProduction();
    wrapper.vm.openOrderChange({ objectType: 'WORK_ORDER', code: 'WO-1', motherOrderCode: 'MO-1' });
    await flushPromises();
    expect(wrapper.emitted('active')).toBeUndefined();
    await wrapper.setProps({ canProduction: true, canChange: true });
    wrapper.vm.openProduction({ objectType: 'REWORK', code: 'REWORK-1' });
    wrapper.vm.openOrderChange({ objectType: 'WORK_ORDER', code: 'WO-1', motherOrderCode: '' });
    await flushPromises();
    expect(wrapper.emitted('active')).toBeUndefined();
    wrapper.vm.openOrderChange({ objectType: 'WORK_ORDER', code: 'WO-1', motherOrderCode: 'MO-1' });
    await ready('[data-change]');
    expect(new DOMWrapper(document.body).get('[data-change]').text()).toBe('WO-1 · MO-1');
    await wrapper.setProps({ canChange: false });
    expect(wrapper.emitted('active')?.at(-1)).toEqual([false]);
    expect(new DOMWrapper(document.body).get('[data-change]').isVisible()).toBe(false);
  });
});
