import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import { fetchInventoryWorkspace } from '../../services/businessWorkspaceApi';

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchInventoryWorkspace: vi.fn() }));
vi.mock('../../services/businessOperationApi', () => ({
  shipFinishedStock: vi.fn(), returnCustomerShipment: vi.fn(), reviewAfterSalesReturn: vi.fn(),
}));

const first = {
  owner: '客户甲', batch: 'MD-1', material: '物料甲', location: '库位甲', status: '成品', qty: 12, weight: '25 g', frozen: '0',
  operationContext: { customerId: 1, ownerCustomerId: 1, motherOrderCode: 'MD-1', workOrderCode: 'ZD-1', materialCode: 'MAT', warehouseCode: 'WH-A', ownerType: 'CUSTOMER', stockStatus: 'FINISHED', quantity: 12, weight: 25 },
};
const second = {
  ...first, location: '库位乙', qty: 4, weight: '8 g',
  operationContext: { ...first.operationContext, warehouseCode: 'WH-B', quantity: 4, weight: 8 },
};

beforeEach(() => vi.clearAllMocks());

describe('同批次跨仓库存选择', () => {
  it('排序刷新保留完整仓库选择，原仓消失后清空选择，且没有重复 key', async () => {
    const warnings: string[] = [];
    const spy = vi.spyOn(console, 'warn').mockImplementation((message) => warnings.push(String(message)));
    vi.mocked(fetchInventoryWorkspace).mockResolvedValue({ balances: [first, second], ledgerEntries: [], timeline: [], evidence: [], dataSource: 'LIVE' });
    const wrapper = mount(InventoryTraceWorkspace);
    await flushPromises();
    expect(wrapper.findAll('tbody tr')).toHaveLength(2);
    expect(wrapper.find('tr.is-selected').exists()).toBe(false);
    await wrapper.findAll('button').filter((button) => button.text() === '看台账')[1].trigger('click');
    expect(wrapper.get('tr.is-selected').text()).toContain('库位乙');

    vi.mocked(fetchInventoryWorkspace).mockResolvedValue({ balances: [second, first], ledgerEntries: [], timeline: [], evidence: [], dataSource: 'LIVE' });
    await (wrapper.vm as unknown as { reloadInventory: () => Promise<void> }).reloadInventory();
    await flushPromises();
    expect(wrapper.get('tr.is-selected').text()).toContain('库位乙');

    vi.mocked(fetchInventoryWorkspace).mockResolvedValue({ balances: [second], ledgerEntries: [], timeline: [], evidence: [], dataSource: 'LIVE' });
    await (wrapper.vm as unknown as { reloadInventory: () => Promise<void> }).reloadInventory();
    await flushPromises();
    expect(wrapper.get('tr.is-selected').text()).toContain('库位乙');

    vi.mocked(fetchInventoryWorkspace).mockResolvedValue({ balances: [first], ledgerEntries: [], timeline: [], evidence: [], dataSource: 'LIVE' });
    await (wrapper.vm as unknown as { reloadInventory: () => Promise<void> }).reloadInventory();
    await flushPromises();
    expect(wrapper.find('tr.is-selected').exists()).toBe(false);
    expect(wrapper.find('.module-side-panel').exists()).toBe(false);
    expect(warnings.join('\n')).not.toContain('Duplicate keys');
    spy.mockRestore();
  });
});
