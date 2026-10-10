import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FinanceWorkspace from './FinanceWorkspace.vue';
import { fetchOperatingStatement, fetchStatement, generateBilling } from '../../services/financeApi';

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
    vi.mocked(fetchStatement).mockReset();
    vi.mocked(generateBilling).mockReset();
    vi.mocked(fetchOperatingStatement).mockReset();
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
