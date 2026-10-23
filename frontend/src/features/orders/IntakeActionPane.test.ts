import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { defineComponent, inject, ref, type Ref } from 'vue';
import IntakeActionPane from './IntakeActionPane.vue';
import type { TraceTarget } from '../scan/traceTarget';

vi.mock('../intake/IntakePanel.vue', () => ({ default: defineComponent({
  name: 'IntakePanel', emits: ['saved'], setup() {
    return { trace: inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null)), openLabel: inject<(code: string) => void>('zhubao-open-label', () => {}) };
  }, template: '<div><span>{{ trace?.code || "独立新草稿" }}</span><button @click="openLabel(\'MO-SAVED\')">标签</button></div>',
}) }));

describe('收货动作的定位隔离', () => {
  it('目标来自本草稿，全局换单不会改写已缓存的单据定位', async () => {
    const globalTarget = ref<TraceTarget | null>({ objectType: 'WORK_ORDER', code: 'GLOBAL-OTHER', intent: 'LOOKUP', token: 8 });
    const wrapper = mount(IntakeActionPane, { props: { target: { objectType: 'MOTHER_ORDER', code: 'MO-OWN' } }, global: { provide: { 'zhubao-trace-target': globalTarget } } });
    expect(wrapper.text()).toContain('MO-OWN');
    globalTarget.value = { objectType: 'MOTHER_ORDER', code: 'MO-NEXT', intent: 'INTAKE', token: 9 };
    expect(wrapper.text()).not.toContain('MO-NEXT');
    expect(wrapper.text()).toContain('MO-OWN');
    wrapper.unmount();
  });
  it('保存回执原样向上转交，打开标签先收起容器再按真实单号导航', async () => {
    const openLabel = vi.fn();
    const wrapper = mount(IntakeActionPane, { props: { target: null }, global: { provide: { 'zhubao-open-label': openLabel } } });
    const receipt = { motherOrderCode: 'MO-SAVED', ledgerCode: 'LED-1' };
    wrapper.getComponent({ name: 'IntakePanel' }).vm.$emit('saved', receipt);
    expect(wrapper.emitted('saved')).toEqual([[receipt]]);
    await wrapper.get('button').trigger('click');
    expect(wrapper.emitted('navigate')).toHaveLength(1);
    expect(openLabel).toHaveBeenCalledWith('MO-SAVED');
    wrapper.unmount();
  });
});
