import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import ProductionWorkspace from './ProductionWorkspace.vue';
import { fetchInventoryWorkspace, fetchProductionWorkspace } from '../../services/businessWorkspaceApi';
import { returnCustomerShipment } from '../../services/businessOperationApi';

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInventoryWorkspace: vi.fn(),
  fetchProductionWorkspace: vi.fn(),
}));
vi.mock('../../services/businessOperationApi', () => ({
  shipFinishedStock: vi.fn(),
  returnCustomerShipment: vi.fn(),
  reviewAfterSalesReturn: vi.fn(),
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

    await wrapper.get('[aria-label="退货单号"]').setValue('RET-PART');
    await wrapper.get('[aria-label="原出库单号"]').setValue('OUT-PART');
    await wrapper.get('[aria-label="退货运单号"]').setValue('YT-PART');
    await wrapper.get('[aria-label="退货原因"]').setValue('退回');
    await wrapper.get('[aria-label="本次数量"]').setValue('2');
    await wrapper.findAll('button').find((button) => button.text() === '退货入库')!.trigger('click');
    await flushPromises();
    expect(returnCustomerShipment).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('重新确认本次重量');

    await wrapper.get('[aria-label="本次重量（克）"]').setValue('6.20');
    await wrapper.findAll('button').find((button) => button.text() === '退货入库')!.trigger('click');
    await flushPromises();
    expect(returnCustomerShipment).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({
      quantity: 2, weight: '6.20', returnCode: 'RET-PART', originalShipmentCode: 'OUT-PART',
    }));
    const sent = vi.mocked(returnCustomerShipment).mock.calls[0][1] as { weight?: string };
    expect(sent.weight).toBe('6.20');
  });

  it('生产页工单详情不再收集旧报工数量', async () => {
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
    expect(wrapper.find('[aria-label="本次完成数量"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('提交报工');
  });
});
