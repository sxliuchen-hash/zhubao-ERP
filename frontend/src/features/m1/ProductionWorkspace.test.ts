import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import ProductionWorkspace from './ProductionWorkspace.vue';
import type { TraceTarget } from '../scan/traceTarget';

vi.mock('../production/ProductionPanel.vue', () => ({ default: { template: '<div data-form />' } }));
vi.mock('../../services/businessWorkspaceApi', () => ({ fetchProductionWorkspace: vi.fn().mockResolvedValue({ stationLoads: [], workOrders: [], handoffs: [] }) }));
vi.mock('../../services/machineOutputApi', () => ({
  bindMachineOutput: vi.fn(), fetchMachineOutputMachineMatchSuggestions: vi.fn().mockResolvedValue([]),
  fetchMachineOutputSummary: vi.fn().mockResolvedValue({ totalRecords: 0, totalQuantity: 0, quantityUnit: '件', byMachine: [], byWorkDate: [], unmatchedMachineRecords: 0, signatureMissingRecords: 0 }),
}));

describe('生产岗位页动作入口', () => {
  it('容器接入后默认先看列表，扫码入口及旧追溯目标分别交给同一动作容器', async () => {
    const open = vi.fn();
    const target = ref<TraceTarget | null>(null);
    const wrapper = mount(ProductionWorkspace, { global: { provide: { 'zhubao-open-production': open, 'zhubao-trace-target': target } } });
    await flushPromises();
    expect(wrapper.find('[data-form]').exists()).toBe(false);
    expect(open).not.toHaveBeenCalled();
    await wrapper.findAll('button').find(button => button.text() === '扫码或手输工序单据')!.trigger('click');
    expect(open).toHaveBeenLastCalledWith(null, 'station');
    target.value = { objectType: 'WORK_ORDER', code: 'WO-LEGACY', intent: 'PRODUCTION', token: 1 };
    await flushPromises();
    expect(open).toHaveBeenLastCalledWith({ objectType: 'WORK_ORDER', code: 'WO-LEGACY' }, 'station');
    expect(wrapper.find('[data-form]').exists()).toBe(false);
    wrapper.unmount();
  });
});
