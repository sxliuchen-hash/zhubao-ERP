import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import ProductionWorkspace from './ProductionWorkspace.vue';
import { fetchInventoryWorkspace, fetchProductionWorkspace } from '../../services/businessWorkspaceApi';
import { reportProduction, shipFinishedStock } from '../../services/businessOperationApi';

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInventoryWorkspace: vi.fn(),
  fetchProductionWorkspace: vi.fn(),
}));
vi.mock('../../services/businessOperationApi', () => ({
  shipFinishedStock: vi.fn(),
  returnCustomerShipment: vi.fn(),
  reviewAfterSalesReturn: vi.fn(),
  reportProduction: vi.fn(),
}));
vi.mock('../../services/machineOutputApi', () => ({
  fetchMachineOutputSummary: vi.fn().mockRejectedValue(new Error('无产量记录')),
  fetchMachineOutputMachineMatchSuggestions: vi.fn().mockResolvedValue([]),
  bindMachineOutputMachine: vi.fn(),
}));

const context = {
  customerId: 57, ownerCustomerId: 57, motherOrderCode: 'MD-REAL', workOrderCode: 'ZD-REAL',
  materialCode: 'MAT-REAL', warehouseCode: 'REAL-WAREHOUSE', ownerType: 'CUSTOMER',
  stockStatus: 'FINISHED', quantity: 12, weight: 25.75,
};

beforeEach(() => vi.clearAllMocks());

describe('可见的数量重量', () => {
  it('整批数量重量显示在输入框，只改数量不能提交，确认后的重量原样保留', async () => {
    vi.mocked(fetchInventoryWorkspace).mockResolvedValue({
      balances: [{ owner: '客户甲', batch: 'ZD-REAL', material: '物料', location: '成品库', status: '成品', qty: 12, weight: '25.75 g', frozen: '0', operationContext: context }],
      ledgerEntries: [], timeline: [], evidence: [], dataSource: 'LIVE',
    });
    const wrapper = mount(InventoryTraceWorkspace);
    await flushPromises();
    expect((wrapper.get('[aria-label="本次数量"]').element as HTMLInputElement).value).toBe('12');
    expect((wrapper.get('[aria-label="本次重量（克）"]').element as HTMLInputElement).value).toBe('25.75');
    expect(wrapper.text()).toContain('已填入整批 12 件、25.75 克');

    await wrapper.get('[aria-label="出库单号"]').setValue('OUT-PART');
    await wrapper.get('[aria-label="发货运单号"]').setValue('YT-PART');
    await wrapper.get('[aria-label="本次数量"]').setValue('2');
    await wrapper.findAll('button').find((button) => button.text() === '确认出库')!.trigger('click');
    await flushPromises();
    expect(shipFinishedStock).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('重新确认本次重量');

    await wrapper.get('[aria-label="本次重量（克）"]').setValue('6.20');
    await wrapper.findAll('button').find((button) => button.text() === '确认出库')!.trigger('click');
    await flushPromises();
    expect(shipFinishedStock).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({
      quantity: 2, weight: '6.20', shipmentCode: 'OUT-PART', logisticsNo: 'YT-PART',
    }));
    const sent = vi.mocked(shipFinishedStock).mock.calls[0][1] as { weight?: string };
    expect(sent.weight).toBe('6.20');
  });

  it('报工只填数量时不提交，两项都填写时原样传出', async () => {
    vi.mocked(fetchProductionWorkspace).mockResolvedValue({
      stationLoads: [],
      workOrders: [{
        code: 'ZD-REAL', customer: '客户', material: '物料', process: '抛光', processCategory: '', productCategory: '',
        machineNo: '', workItemName: '', startedAt: '', finishedAt: '', completionStatus: '', handoffAuditStatus: '',
        assignee: '', plannedQty: 10, finishedQty: 0, status: '加工中', progress: 0, operationContext: context,
      }],
      handoffs: [],
      dataSource: 'LIVE',
    });
    const wrapper = mount(ProductionWorkspace);
    await flushPromises();
    expect((wrapper.get('[aria-label="本次完成数量"]').element as HTMLInputElement).value).toBe('12');
    await wrapper.get('[aria-label="本次完成数量"]').setValue('3');
    await wrapper.findAll('button').find((button) => button.text() === '提交报工')!.trigger('click');
    await flushPromises();
    expect(reportProduction).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('重新确认本次重量');
    await wrapper.get('[aria-label="本次产出重量（克）"]').setValue('7.125');
    await wrapper.findAll('button').find((button) => button.text() === '提交报工')!.trigger('click');
    await flushPromises();
    expect(reportProduction).toHaveBeenCalledWith(expect.anything(), undefined, { quantity: 3, weight: '7.125' });
  });
});
