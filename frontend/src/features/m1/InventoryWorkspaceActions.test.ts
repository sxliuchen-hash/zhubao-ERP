import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, ref } from 'vue';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import { fetchInventoryWorkspace } from '../../services/businessWorkspaceApi';

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchInventoryWorkspace: vi.fn() }));
vi.mock('../../services/businessOperationApi', () => ({ returnCustomerShipment: vi.fn(), reviewAfterSalesReturn: vi.fn() }));
vi.mock('../shipping/ShippingPanel.vue', () => ({ default: { template: '<div data-legacy-shipping />' } }));
vi.mock('./PrepStockPanel.vue', () => ({ default: defineComponent({ name: 'PrepStockPanel', props: ['scope'], setup() { return { draft: ref('') }; }, template: '<input v-model="draft" :aria-label="scope" />' }) }));
const context = { customerId: 1, ownerCustomerId: 2, motherOrderCode: 'MO-1', workOrderCode: 'WO-1', materialCode: 'MAT-1', warehouseCode: 'WH-B', ownerType: 'CUSTOMER', stockStatus: 'FINISHED', quantity: 10.5, weight: 20.5 };
const row = { owner: '客户甲', batch: 'WO-1', material: '物料', location: '库位乙', status: '成品', qty: 10.5, weight: '20.5 克', frozen: '0', operationContext: context };
beforeEach(() => { vi.mocked(fetchInventoryWorkspace).mockReset().mockResolvedValue({ balances: [row], ledgerEntries: [], timeline: [], evidence: [] }); });

describe('库存动作入口', () => {
  it('列表入口核对有效权限，发货保留所选余额完整维度，成功通知只读刷新不重开', async () => {
    const open = vi.fn();
    const session = ref({ effectivePermissions: [] as string[] });
    const refresh = ref(0);
    const wrapper = mount(InventoryTraceWorkspace, { global: { provide: { 'zhubao-open-shipping': open, 'zhubao-auth-session': session, 'zhubao-order-refresh': refresh } } });
    await flushPromises();
    expect(wrapper.find('[data-legacy-shipping]').exists()).toBe(false);
    await wrapper.findAll('button').find(button => button.text() === '加工库存')!.trigger('click');
    expect(wrapper.find('.module-side-panel').exists()).toBe(false);
    await wrapper.findAll('button').find(button => button.text() === '看台账')!.trigger('click');
    expect(wrapper.findAll('button').some(button => button.text() === '从这笔库存发货')).toBe(false);
    session.value.effectivePermissions.push('BUSINESS_OUTBOUND_SHIPMENT');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '从这笔库存发货')!.trigger('click');
    expect(open).toHaveBeenCalledWith({ objectType: null, code: 'WO-1', balance: context });
    refresh.value += 1;
    await flushPromises();
    expect(fetchInventoryWorkspace).toHaveBeenCalledTimes(2);
    expect(open).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
  it('公司料与客户备料切换保留各自挂载的草稿，不把未完成的请求组件销毁', async () => {
    const wrapper = mount(InventoryTraceWorkspace, { global: { provide: { 'zhubao-open-shipping': vi.fn() } } });
    await flushPromises();
    await wrapper.get('[aria-label="company"]').setValue('公司料原草稿');
    const company = wrapper.getComponent({ name: 'PrepStockPanel' }).vm.$;
    await wrapper.findAll('button').find(button => button.text() === '客户寄存备料')!.trigger('click');
    await wrapper.get('[aria-label="prep"]').setValue('客户料原草稿');
    expect(company.isUnmounted).toBe(false);
    await wrapper.findAll('button').find(button => button.text() === '公司自有物料')!.trigger('click');
    expect(wrapper.get('[aria-label="company"]').element).toHaveProperty('value', '公司料原草稿');
    wrapper.unmount();
  });
});
