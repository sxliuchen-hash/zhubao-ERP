import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FinanceWorkspace from './FinanceWorkspace.vue';
import { fetchOperatingStatement, fetchStatement, generateBilling, saveConsumableItem, purchaseConsumable, issueConsumable, saveFixedAsset, runDepreciation, saveOutsource, fetchConsumables, fetchPayables } from '../../services/financeApi';

vi.mock('../../services/financeApi', () => ({
  fetchStatement: vi.fn(),
  savePriceAgreement: vi.fn(),
  generateBilling: vi.fn(),
  recordReceipt: vi.fn(),
  saveWorkerRate: vi.fn(),
  generatePayroll: vi.fn(),
  confirmPayroll: vi.fn(),
  fetchConsumables: vi.fn(),
  saveConsumableItem: vi.fn(),
  purchaseConsumable: vi.fn(),
  issueConsumable: vi.fn(),
  saveFixedAsset: vi.fn(),
  runDepreciation: vi.fn(),
  saveOutsource: vi.fn(),
  fetchPayables: vi.fn(),
  payPayable: vi.fn(),
  fetchOperatingStatement: vi.fn(),
}));

describe('FinanceWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchConsumables).mockResolvedValue([]);
    vi.mocked(fetchPayables).mockResolvedValue([]);
    vi.mocked(fetchStatement).mockReset();
    vi.mocked(generateBilling).mockReset();
    vi.mocked(fetchOperatingStatement).mockReset();
  });

  async function selectSection(wrapper: ReturnType<typeof mount>, label: string) {
    await wrapper.findAll('button').find(button => button.text() === label)?.trigger('click');
  }

  it('采购和领用使用输入值，空表单不会写入固定样例', async () => {
    const wrapper = mount(FinanceWorkspace);
    await selectSection(wrapper, '消耗品');
    await wrapper.get('[aria-label="消耗品采购登记"]').trigger('submit');
    await flushPromises();
    expect(saveConsumableItem).not.toHaveBeenCalled();
    for (const [label, value] of Object.entries({ 消耗品编码: 'WAX', 消耗品名称: '蜡', 计量单位: '盒', 采购数量: '7', 采购金额: '126.50', 采购供应商: '兴隆材料' })) {
      await wrapper.get(`[aria-label="${label}"]`).setValue(value);
    }
    await wrapper.get('[aria-label="财务月份"]').setValue('2026-09');
    await wrapper.get('[aria-label="消耗品采购登记"]').trigger('submit');
    await flushPromises();
    expect(saveConsumableItem).toHaveBeenCalledWith({ code: 'WAX', name: '蜡', unitName: '盒' });
    expect(purchaseConsumable).toHaveBeenCalledWith({ itemCode: 'WAX', quantity: 7, amount: 126.5, supplier: '兴隆材料', yearMonth: '2026-09' });
    await wrapper.get('[aria-label="领用数量"]').setValue('2');
    await wrapper.get('[aria-label="客户编号"]').setValue('1008');
    await wrapper.get('[aria-label="消耗品领用登记"]').trigger('submit');
    await flushPromises();
    expect(issueConsumable).toHaveBeenCalledWith({ itemCode: 'WAX', quantity: 2, customerId: 1008, yearMonth: '2026-09' });
  });

  it('资产登记保留实际残值和月数，不自动重复登记并计提', async () => {
    const wrapper = mount(FinanceWorkspace);
    await selectSection(wrapper, '固定资产');
    for (const [label, value] of Object.entries({ 资产名称: '激光机', 资产类别: '加工设备', 资产原值: '18000', 预计残值: '900', 开始折旧月份: '2026-08', 使用月数: '60' })) {
      await wrapper.get(`[aria-label="${label}"]`).setValue(value);
    }
    await wrapper.get('[aria-label="固定资产登记"]').trigger('submit');
    await flushPromises();
    expect(saveFixedAsset).toHaveBeenCalledWith({ name: '激光机', category: '加工设备', originalValue: 18000, residualValue: 900, startMonth: '2026-08', usefulMonths: 60 });
    expect(runDepreciation).not.toHaveBeenCalled();
  });

  it('外协独立录入供应商、金额和关联母单', async () => {
    const wrapper = mount(FinanceWorkspace);
    await selectSection(wrapper, '应付汇总');
    await wrapper.get('[aria-label="外协供应商"]').setValue('东兴镶嵌');
    await wrapper.get('[aria-label="外协金额"]').setValue('560');
    await wrapper.get('[aria-label="外协母单号"]').setValue('MO-1009');
    await wrapper.get('[aria-label="财务月份"]').setValue('2026-09');
    await wrapper.get('[aria-label="外协登记"]').trigger('submit');
    await flushPromises();
    expect(saveOutsource).toHaveBeenCalledWith({ supplier: '东兴镶嵌', amount: 560, yearMonth: '2026-09', customerId: null, motherOrderCode: 'MO-1009' });
  });

  it('没有账单时显示空表说明', async () => {
    vi.mocked(fetchStatement).mockResolvedValueOnce({
      customerId: 1001,
      customerName: '恒信珠宝',
      stockQuantity: 0,
      stockWeight: 0,
      receivableAmount: 0,
      receivedAmount: 0,
      outstandingAmount: 0,
      prepaymentAmount: 0,
      lines: [],
      receipts: [],
      stockNote: '实物为当前结存，不按月份回放',
    });

    const wrapper = mount(FinanceWorkspace);
    await wrapper.get('[aria-label="客户编号"]').setValue('1001');
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('暂无账单行');
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('实物为当前结存');
  });

  it('接口失败时显示原因，不回退样例金额', async () => {
    vi.mocked(fetchStatement).mockRejectedValueOnce(new Error('后端接口不可用'));

    const wrapper = mount(FinanceWorkspace);
    await wrapper.get('[aria-label="客户编号"]').setValue('1001');
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('后端接口不可用');
    expect(wrapper.text()).not.toContain('131.25');
  });

  it('可以切到月度经营并看到缺口说明', async () => {
    vi.mocked(fetchOperatingStatement).mockResolvedValueOnce({
      yearMonth: '2026-10',
      customerId: null,
      revenue: 0,
      wageCost: 0,
      consumableCost: 0,
      depreciationCost: 0,
      outsourceCost: 0,
      result: 0,
      gaps: ['工资未确认，成本按 0'],
    });

    const wrapper = mount(FinanceWorkspace);
    await wrapper.get('button[class="table-action"]').trigger('click');
    const operating = wrapper.findAll('button.table-action').find((button) => button.text() === '月度经营');
    await operating?.trigger('click');
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('工资未确认，成本按 0');
  });
});
