import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import ProductionWorkspace from './ProductionWorkspace.vue';
import QualityWorkspace from './QualityWorkspace.vue';
import { fetchInventoryWorkspace, fetchProductionWorkspace, fetchQualityWorkspace } from '../../services/businessWorkspaceApi';
import { returnCustomerShipment, approveLoss } from '../../services/businessOperationApi';

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchInventoryWorkspace: vi.fn(), fetchProductionWorkspace: vi.fn(), fetchQualityWorkspace: vi.fn() }));
vi.mock('../../services/businessOperationApi', () => ({
  shipFinishedStock: vi.fn(), returnCustomerShipment: vi.fn(), reviewAfterSalesReturn: vi.fn(),
  approveLoss: vi.fn(),
}));
vi.mock('../../services/qualityCheckApi', () => ({ lookupQualityCheck: vi.fn(), openQualityRework: vi.fn(), submitQualityCheck: vi.fn() }));
vi.mock('../../services/machineOutputApi', () => ({
  fetchMachineOutputSummary: vi.fn().mockRejectedValue(new Error('无产量记录')),
  fetchMachineOutputMachineMatchSuggestions: vi.fn().mockResolvedValue([]), bindMachineOutputMachine: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

describe('业务操作真实输入', () => {
  it('没有历史损耗审批时，也能为当前质检单提交首次申请', async () => {
    const qualityCase = { code: 'QC-REAL', workOrder: 'ZD-REAL', process: '抛光', inspector: '质检员', defect: '缺陷', action: '待处理', status: '待质检' };
    vi.mocked(fetchQualityWorkspace).mockResolvedValue({ metrics: [], cases: [qualityCase], lossApprovals: [], dataSource: 'LIVE' });
    const wrapper = mount(QualityWorkspace);
    await flushPromises();
    const submit = () => wrapper.findAll('button').find(button => button.text() === '提交损耗申请')!.trigger('click');
    await submit();
    await flushPromises();
    expect(approveLoss).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请填写损耗申请单号');
    await wrapper.get('[aria-label="损耗申请单号"]').setValue('LOSS-REAL');
    await wrapper.get('[aria-label="投入重量（克）"]').setValue('10');
    await submit();
    await flushPromises();
    expect(approveLoss).toHaveBeenCalledWith(expect.objectContaining({ code: 'LOSS-REAL', workOrder: 'ZD-REAL', process: '抛光' }), { approvalCode: 'LOSS-REAL', inputWeight: '10' });
  });

  it('退货使用输入的单号和本次数量重量，页面不再提供旧确认出库', async () => {
    const balances = [
      { owner: '客户甲', batch: 'MD-1', material: '物料甲', location: '库位甲', status: '成品', qty: 12, weight: '25 g', frozen: '0' },
      { owner: '客户甲', batch: 'MD-1', material: '物料甲', location: '库位乙', status: '成品', qty: 4, weight: '8 g', frozen: '0' },
    ];
    vi.mocked(fetchInventoryWorkspace).mockResolvedValue({ balances, ledgerEntries: [], timeline: [], evidence: [], dataSource: 'LIVE' });
    const wrapper = mount(InventoryTraceWorkspace);
    await flushPromises();
    expect(wrapper.text()).not.toContain('确认出库');
    await wrapper.get('[aria-label="退货单号"]').setValue('RET-USER');
    await wrapper.get('[aria-label="原出库单号"]').setValue('OUT-USER');
    await wrapper.get('[aria-label="退货运单号"]').setValue('YT-USER');
    await wrapper.get('[aria-label="退货原因"]').setValue('客户退回');
    await wrapper.get('[aria-label="本次数量"]').setValue('2');
    await wrapper.get('[aria-label="本次重量（克）"]').setValue('4.25');
    await wrapper.findAll('button').find(button => button.text() === '退货入库')!.trigger('click');
    await flushPromises();
    expect(returnCustomerShipment).toHaveBeenCalledWith(balances[0], expect.objectContaining({
      returnCode: 'RET-USER', originalShipmentCode: 'OUT-USER', returnLogisticsNo: 'YT-USER', reason: '客户退回', quantity: 2, weight: '4.25',
    }));
  });

  it('生产页不再提供工单池旧报工', async () => {
    const workOrder = { code: 'ZD-REAL', customer: '实际客户', material: '物料', process: '抛光', processCategory: '', productCategory: '',
      machineNo: '', workItemName: '', startedAt: '', finishedAt: '', completionStatus: '', handoffAuditStatus: '',
      assignee: '', plannedQty: 10, finishedQty: 0, status: '加工中', progress: 0 };
    vi.mocked(fetchProductionWorkspace).mockResolvedValue({ stationLoads: [], workOrders: [workOrder], handoffs: [], dataSource: 'LIVE' });
    const wrapper = mount(ProductionWorkspace);
    await flushPromises();
    expect(wrapper.text()).not.toContain('提交报工');
    expect(wrapper.find('[aria-label="本次完成数量"]').exists()).toBe(false);
  });
});
