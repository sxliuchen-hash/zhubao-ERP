import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../../services/apiClient';
import FinanceWorkspace from './FinanceWorkspace.vue';
import {
  confirmPayroll,
  fetchConsumables,
  fetchFinanceDashboard,
  fetchFinanceDetails,
  fetchOperatingStatement,
  fetchPayables,
  fetchStatement,
  generateBilling,
  generatePayroll,
  issueConsumable,
  payPayable,
  purchaseConsumable,
  recordReceipt,
  runDepreciation,
  saveConsumableItem,
  saveFixedAsset,
  saveOutsource,
  savePriceAgreement,
  saveWorkerRate,
  type FinanceDashboard,
} from '../../services/financeApi';

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
  fetchFinanceDashboard: vi.fn(),
  fetchFinanceDetails: vi.fn(),
}));

function dashboardOf(patch: {
  revenue?: number;
  result?: number;
  gaps?: string[];
  kind?: string;
  note?: string;
  outstanding?: number | null;
  unpaid?: number | null;
  limitation?: string;
  ranking?: FinanceDashboard['receivableRanking'];
  capabilities?: FinanceDashboard['capabilities'];
  trend?: FinanceDashboard['trend'];
  yearMonth?: string;
} = {}): FinanceDashboard {
  const yearMonth = patch.yearMonth ?? '2026-10';
  const operating = {
    yearMonth,
    customerId: patch.kind === 'CUSTOMER' ? 1008 : null,
    revenue: patch.revenue ?? 128600.5,
    wageCost: 42300,
    consumableCost: 16800,
    depreciationCost: 6500,
    outsourceCost: 18000,
    result: patch.result ?? 45000.5,
    gaps: patch.gaps ?? [],
  };
  return {
    yearMonth,
    generatedAt: '2026-10-12T22:17:00',
    scope: {
      kind: patch.kind ?? 'COMPANY',
      customerIds: patch.kind === 'CUSTOMER' ? [1008] : [],
      note: patch.note ?? '全公司，含折旧和未能按客户归集的成本',
    },
    operating,
    trend: patch.trend === undefined ? [{ yearMonth, throughQueryTime: true, operating }] : patch.trend,
    currentBalances: {
      asOf: '2026-10-12T22:17:00',
      outstandingAmount: patch.outstanding === undefined ? 62500 : patch.outstanding,
      unpaidAmount: patch.unpaid === undefined ? 35800 : patch.unpaid,
      limitation: patch.limitation ?? '',
    },
    receivableRanking: patch.ranking === undefined
      ? { total: 1, returned: 1, truncated: false, rows: [{ customerId: 3, customerName: '恒信珠宝', outstandingAmount: 62500 }] }
      : patch.ranking,
    capabilities: patch.capabilities ?? [
      { part: 'operating', available: true, reason: '' },
      { part: 'trend', available: true, reason: '含选择月在内的六个月。当前自然月只截至本次查询，不与完整月计算增幅' },
      { part: 'currentBalances', available: true, reason: '截至本次查询的当前余额，不随所选月份改变' },
      { part: 'receivableRanking', available: true, reason: '' },
    ],
  };
}

describe('FinanceWorkspace', () => {
  it('收起经营分析仍保留核心金额，读取失败也在默认内容中明确显示', async () => {
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();
    const revenue = wrapper.get('[data-metric="revenue"]');
    expect(revenue.element.closest('details')).toBeNull();
    const analysis = wrapper.get('.finance-analysis');
    (analysis.element as HTMLDetailsElement).open = true;
    await analysis.trigger('toggle');
    (analysis.element as HTMLDetailsElement).open = false;
    await analysis.trigger('toggle');
    expect(wrapper.get('[data-metric="revenue"]').text()).toContain('128,600.5');
    expect(fetchFinanceDashboard).toHaveBeenCalledTimes(1);
    wrapper.unmount();

    vi.mocked(fetchFinanceDashboard).mockRejectedValueOnce(new Error('经营数据读取失败'));
    const failed = mount(FinanceWorkspace);
    await flushPromises();
    const alert = failed.get('[role="alert"]');
    expect(alert.text()).toContain('经营数据读取失败');
    expect(alert.element.closest('details')).toBeNull();
    failed.unmount();
  });
  it('财务入口将客户和月份交给共享账单容器，不在原页面直接收款', async () => {
    const open = vi.fn();
    const wrapper = mount(FinanceWorkspace, { global: { provide: { 'zhubao-open-bill': open } } });
    await selectSection(wrapper, '收款'); await wrapper.get('[aria-label="客户编号"]').setValue('1002'); await wrapper.get('[aria-label="财务月份"]').setValue('2026-09');
    await wrapper.findAll('button').find(button => button.text() === '核对客户账单与收款')!.trigger('click');
    expect(open).toHaveBeenCalledWith({ customerId: 1002, yearMonth: '2026-09' }, 'receipt');
    expect(wrapper.find('[aria-label="收款金额"]').exists()).toBe(false); expect(recordReceipt).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it('财务账单切客户立即撤下旧结果，迟到的另一客户结果不回写', async () => {
    let resolveFirst!: (value: Awaited<ReturnType<typeof fetchStatement>>) => void;
    const first = new Promise<Awaited<ReturnType<typeof fetchStatement>>>(done => { resolveFirst = done; });
    const base = { customerId: 1002, customerName: '客户乙', stockQuantity: 130.5, stockWeight: 0, receivableAmount: 20, receivedAmount: 0, outstandingAmount: 20, prepaymentAmount: 0, lines: [], receipts: [], stockNote: '' };
    vi.mocked(fetchStatement).mockReturnValueOnce(first).mockResolvedValueOnce(base);
    const wrapper = mount(FinanceWorkspace); await selectSection(wrapper, '客户账单'); await wrapper.get('[aria-label="客户编号"]').setValue('1001');
    await wrapper.findAll('button').find(button => button.text() === '查询账单')!.trigger('click');
    await wrapper.get('[aria-label="客户编号"]').setValue('1002');
    await wrapper.findAll('button').find(button => button.text() === '查询账单')!.trigger('click'); await flushPromises();
    resolveFirst({ ...base, customerId: 1001, customerName: '客户甲旧账单', outstandingAmount: 9999 }); await flushPromises();
    expect(wrapper.text()).toContain('客户乙'); expect(wrapper.text()).not.toContain('客户甲旧账单'); expect(wrapper.text()).not.toContain('9,999');
    expect(wrapper.text()).not.toContain('130.5'); expect(wrapper.text()).toContain('按物料与单位分别核对'); wrapper.unmount();
  });
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchConsumables).mockResolvedValue([]);
    vi.mocked(fetchPayables).mockResolvedValue([]);
    vi.mocked(fetchStatement).mockReset();
    vi.mocked(generateBilling).mockReset();
    vi.mocked(fetchOperatingStatement).mockReset();
    vi.mocked(fetchFinanceDashboard).mockResolvedValue(dashboardOf());
    vi.mocked(fetchFinanceDetails).mockResolvedValue({
      kind: 'REVENUE',
      asOf: '2026-10-12T22:17:00',
      yearMonth: '2026-10',
      page: 1,
      size: 20,
      total: 0,
      totalAmount: 0,
      rows: [],
    });
  });

  async function selectSection(wrapper: ReturnType<typeof mount>, label: string) {
    await flushPromises();
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
    await selectSection(wrapper, '客户账单');
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
    await selectSection(wrapper, '客户账单');
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

  it('付款后刷新失败仍保留幂等键，再次点击不换键', async () => {
    vi.mocked(payPayable).mockResolvedValue({ code: 'FIN-PMT-1' });
    vi.mocked(fetchPayables).mockRejectedValueOnce(new Error('应付列表刷新失败')).mockResolvedValueOnce([]);
    const wrapper = mount(FinanceWorkspace);
    await selectSection(wrapper, '应付汇总');
    await wrapper.get('[aria-label="应付单号"]').setValue('FIN-AP-1');
    await wrapper.get('[aria-label="付款金额"]').setValue('60');
    await wrapper.get('[aria-label="付款日期"]').setValue('2026-10-10');
    const pay = wrapper.findAll('button').find((button) => button.text() === '登记付款');
    await pay?.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('应付列表刷新失败');
    const firstKey = vi.mocked(payPayable).mock.calls[0][0].idempotencyKey;
    expect(firstKey).toBeTruthy();
    await pay?.trigger('click');
    await flushPromises();
    expect(vi.mocked(payPayable).mock.calls[1][0].idempotencyKey).toBe(firstKey);
    expect(wrapper.text()).toContain('付款已登记');
  });

  it('收款成功后查询失败时保留单号，并只重试查询', async () => {
    vi.mocked(recordReceipt).mockResolvedValue({
      code: 'FIN-RC-9',
      amount: 80,
      receivedOn: '2026-10-10',
      allocations: [],
    });
    vi.mocked(fetchStatement)
      .mockResolvedValueOnce({ customerId: 1008, customerName: '恒信珠宝', stockQuantity: 0, stockWeight: 0, receivableAmount: 0, receivedAmount: 0, outstandingAmount: 0, prepaymentAmount: 0, lines: [], receipts: [], stockNote: '' })
      .mockRejectedValueOnce(new Error('查询账单失败'))
      .mockResolvedValueOnce({
        customerId: 1008,
        customerName: '恒信珠宝',
        stockQuantity: 0,
        stockWeight: 0,
        receivableAmount: 0,
        receivedAmount: 80,
        outstandingAmount: 0,
        prepaymentAmount: 80,
        lines: [],
        receipts: [],
        stockNote: '实物为当前结存',
      });
    const wrapper = mount(FinanceWorkspace);
    await selectSection(wrapper, '收款');
    await wrapper.get('[aria-label="客户编号"]').setValue('1008');
    await wrapper.get('[aria-label="收款金额"]').setValue('80');
    await wrapper.get('[aria-label="收款日期"]').setValue('2026-10-10');
    const register = wrapper.findAll('button').find((button) => button.text() === '确认登记收款');
    await register?.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('收款单号 FIN-RC-9');
    expect(wrapper.text()).toContain('请只重试查询，不要再次登记');
    expect(register?.element.closest('fieldset')?.disabled).toBe(true);
    expect(recordReceipt).toHaveBeenCalledTimes(1);
    await wrapper.findAll('button').find((button) => button.text() === '重新查询账单')?.trigger('click');
    await flushPromises();
    expect(recordReceipt).toHaveBeenCalledTimes(1);
    expect(fetchStatement).toHaveBeenCalledTimes(3);
    expect(wrapper.text()).not.toContain('请只重试查询，不要再次登记');
    expect(wrapper.text()).toContain('收款单号 FIN-RC-9');
  });

  function expectNoPosting() {
    expect(generateBilling).not.toHaveBeenCalled();
    expect(recordReceipt).not.toHaveBeenCalled();
    expect(payPayable).not.toHaveBeenCalled();
    expect(generatePayroll).not.toHaveBeenCalled();
    expect(confirmPayroll).not.toHaveBeenCalled();
    expect(savePriceAgreement).not.toHaveBeenCalled();
    expect(saveWorkerRate).not.toHaveBeenCalled();
    expect(saveConsumableItem).not.toHaveBeenCalled();
    expect(purchaseConsumable).not.toHaveBeenCalled();
    expect(issueConsumable).not.toHaveBeenCalled();
    expect(saveFixedAsset).not.toHaveBeenCalled();
    expect(runDepreciation).not.toHaveBeenCalled();
    expect(saveOutsource).not.toHaveBeenCalled();
  }

  it('进入经营总览只读取仪表盘，不写账', async () => {
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('经营总览');
    expect(wrapper.text()).toContain('128,600.50');
    expect(wrapper.text()).toContain('截至 2026-10-12 22:17');
    expect(fetchFinanceDashboard).toHaveBeenCalledTimes(1);
    const month = (wrapper.get('[aria-label="财务月份"]').element as HTMLInputElement).value;
    expect(fetchFinanceDashboard).toHaveBeenCalledWith(month, '');
    expectNoPosting();
  });

  it('缺项标暂估，客户范围不写成全公司净利润', async () => {
    vi.mocked(fetchFinanceDashboard).mockResolvedValueOnce(dashboardOf({
      kind: 'CUSTOMER',
      note: '只含该客户可归集的收入和成本，不含公司折旧和未分配成本',
      gaps: ['工资未确认，成本按 0'],
      result: -1200.5,
    }));
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('暂估');
    expect(wrapper.text()).toContain('工资未确认，成本按 0');
    expect(wrapper.text()).toContain('只含该客户可归集的收入和成本');
    expect(wrapper.text()).toContain('不能相加当作全公司结余');
    expect(wrapper.text()).toContain('-1,200.50');
    expect(wrapper.text()).not.toContain('净利润');
  });

  it('公司应付未归集时显示 limitation，不显示成 0', async () => {
    vi.mocked(fetchFinanceDashboard).mockResolvedValueOnce(dashboardOf({
      kind: 'CUSTOMERS',
      note: '授权客户集合内可归集的收入和成本，不含公司折旧，也不包含未授权客户',
      unpaid: 0,
      outstanding: 80,
      limitation: '公司工资、采购或未归集外协不计入该范围应付',
    }));
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();

    const payable = wrapper.get('[data-metric="payable"]');
    expect(payable.text()).toContain('公司工资、采购或未归集外协不计入该范围应付');
    expect(payable.text()).not.toContain('0.00');
    expect(wrapper.get('[data-metric="outstanding"]').text()).toContain('80.00');
  });

  it('真实零值和空排行分开显示，失败和无权限不变成 0', async () => {
    vi.mocked(fetchFinanceDashboard).mockResolvedValueOnce(dashboardOf({
      revenue: 0,
      result: 0,
      outstanding: 0,
      unpaid: 0,
      ranking: { total: 0, returned: 0, truncated: false, rows: [] },
      trend: [{
        yearMonth: '2026-10',
        throughQueryTime: false,
        operating: {
          yearMonth: '2026-10',
          customerId: null,
          revenue: 0,
          wageCost: 0,
          consumableCost: 0,
          depreciationCost: 0,
          outsourceCost: 0,
          result: 0,
          gaps: [],
        },
      }],
    }));
    const zero = mount(FinanceWorkspace);
    await flushPromises();
    expect(zero.text()).toContain('0.00');
    expect(zero.text()).toContain('当前没有未收款');
    expect(zero.text()).not.toContain('读取失败');
    zero.unmount();

    vi.mocked(fetchFinanceDashboard).mockRejectedValueOnce(new ApiRequestError('无权查看该客户的财务', 403, 40301));
    const denied = mount(FinanceWorkspace);
    await flushPromises();
    expect(denied.text()).toContain('无权限');
    expect(denied.text()).toContain('无权查看该客户的财务');
    expect(denied.text()).not.toContain('0.00');
    expect(denied.text()).not.toContain('读取失败');
    denied.unmount();

    vi.mocked(fetchFinanceDashboard).mockRejectedValueOnce(new Error('后端接口不可用'));
    const failed = mount(FinanceWorkspace);
    await flushPromises();
    expect(failed.text()).toContain('读取失败');
    expect(failed.text()).toContain('后端接口不可用');
    expect(failed.get('button.finance-retry').text()).toBe('重试');
    expect(failed.text()).not.toContain('0.00');
    vi.mocked(fetchFinanceDashboard).mockResolvedValueOnce(dashboardOf({ revenue: 66 }));
    await failed.get('button.finance-retry').trigger('click');
    await flushPromises();
    expect(failed.text()).toContain('66.00');
  });

  it('排行不可用时不显示成没有未收款，顺序保持接口返回', async () => {
    vi.mocked(fetchFinanceDashboard).mockResolvedValueOnce(dashboardOf({
      capabilities: [
        { part: 'operating', available: true, reason: '' },
        { part: 'trend', available: true, reason: '' },
        { part: 'currentBalances', available: true, reason: '' },
        { part: 'receivableRanking', available: false, reason: '客户未收排行暂不可用' },
      ],
      ranking: null,
    }));
    const blocked = mount(FinanceWorkspace);
    await flushPromises();
    expect(blocked.text()).toContain('客户未收排行暂不可用');
    expect(blocked.text()).toContain('128,600.50');
    expect(blocked.text()).not.toContain('当前没有未收款');
    blocked.unmount();

    const longName = '粤港澳珠宝定制客户名称超出普通列宽';
    vi.mocked(fetchFinanceDashboard).mockResolvedValueOnce(dashboardOf({
      revenue: 123456789.12,
      ranking: {
        total: 3,
        returned: 2,
        truncated: true,
        rows: [
          { customerId: 3, customerName: longName, outstandingAmount: 10 },
          { customerId: 1, customerName: '乙客户', outstandingAmount: 90 },
        ],
      },
    }));
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();
    expect(wrapper.findAll('.finance-rank-row').map((row) => row.attributes('data-customer-id'))).toEqual(['3', '1']);
    expect(wrapper.text()).toContain(longName);
    expect(wrapper.text()).toContain('123,456,789.12');
    expect(wrapper.text()).toContain('共 3 名客户有未收，这里显示前 2 名');
  });

  it('隐藏金额时卡片、图表、明细和无障碍文本都不带数字', async () => {
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="隐藏金额"]').trigger('click');

    expect(wrapper.html()).not.toContain('128,600.50');
    expect(wrapper.html()).not.toContain('45,000.50');
    expect(wrapper.html()).not.toContain('42,300.00');
    expect(wrapper.html()).not.toContain('62,500.00');
    expect(wrapper.get('[data-metric="revenue"]').attributes('aria-label')).toContain('金额已隐藏');
    expect(wrapper.find('.finance-line-chart').exists()).toBe(false);
    expect(wrapper.text()).toContain('金额已隐藏');
  });

  it('较慢的旧月份响应不能覆盖新筛选', async () => {
    let releaseOlder: (value: FinanceDashboard) => void = () => {};
    const older = new Promise<FinanceDashboard>((resolve) => {
      releaseOlder = resolve;
    });
    const initial = dashboardOf({ revenue: 101, yearMonth: '2026-10' });
    const newer = dashboardOf({ revenue: 222, yearMonth: '2026-08' });
    vi.mocked(fetchFinanceDashboard).mockReset();
    vi.mocked(fetchFinanceDashboard)
      .mockResolvedValueOnce(initial)
      .mockReturnValueOnce(older)
      .mockResolvedValueOnce(newer);
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="财务月份"]').setValue('2026-07');
    await wrapper.get('[aria-label="财务月份"]').setValue('2026-08');
    await flushPromises();

    expect(wrapper.text()).toContain('222.00');
    expect(wrapper.text()).not.toContain('111.00');
    releaseOlder(dashboardOf({ revenue: 111, yearMonth: '2026-07' }));
    await flushPromises();
    expect(wrapper.text()).toContain('222.00');
    expect(wrapper.text()).not.toContain('111.00');
    expect(fetchFinanceDashboard).toHaveBeenLastCalledWith('2026-08', '');
  });

  it('点击指标和客户打开明细，返回后保留月份和客户', async () => {
    vi.mocked(fetchFinanceDetails).mockResolvedValue({
      kind: 'REVENUE',
      asOf: '2026-10-12T22:17:00',
      yearMonth: '2026-06',
      page: 1,
      size: 20,
      total: 2,
      totalAmount: 80,
      rows: [
        { sourceType: 'RECEIVABLE', sourceCode: 'A', customerId: 1008, customerName: '甲', yearMonth: '2026-06', amount: 10, note: '加工费账单' },
        { sourceType: 'RECEIVABLE', sourceCode: 'B', customerId: 1008, customerName: '甲', yearMonth: '2026-06', amount: 20, note: '加工费账单' },
      ],
    });
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="财务月份"]').setValue('2026-06');
    await wrapper.get('[aria-label="客户编号"]').setValue('1008');
    await flushPromises();
    await wrapper.get('[data-metric="revenue"]').trigger('click');
    await flushPromises();

    expect(fetchFinanceDetails).toHaveBeenCalledWith({
      kind: 'REVENUE',
      yearMonth: '2026-06',
      customerId: '1008',
      page: 1,
      size: 20,
    });
    expect(wrapper.text()).toContain('全部合计 80.00');
    expect(wrapper.text()).not.toContain('30.00');
    await wrapper.get('[aria-label="返回经营总览"]').trigger('click');
    await flushPromises();
    expect((wrapper.get('[aria-label="财务月份"]').element as HTMLInputElement).value).toBe('2026-06');
    expect((wrapper.get('[aria-label="客户编号"]').element as HTMLInputElement).value).toBe('1008');
    expect(wrapper.find('[aria-label="已入账加工收入明细"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('经营总览');

    await wrapper.get('[data-customer-id="3"]').trigger('click');
    await flushPromises();
    expect(fetchFinanceDetails).toHaveBeenLastCalledWith(expect.objectContaining({
      kind: 'RECEIVABLE',
      yearMonth: '2026-06',
      customerId: '3',
    }));
    expect((wrapper.get('[aria-label="客户编号"]').element as HTMLInputElement).value).toBe('1008');
    expectNoPosting();
  });

  it('明细失败单独重试，不把合计写成 0', async () => {
    vi.mocked(fetchFinanceDetails)
      .mockRejectedValueOnce(new Error('明细读取失败'))
      .mockResolvedValueOnce({
        kind: 'COST',
        asOf: '2026-10-12T22:17:00',
        yearMonth: '2026-10',
        page: 1,
        size: 20,
        total: 1,
        totalAmount: 18,
        rows: [{ sourceType: 'OUTSOURCE', sourceCode: 'OS-1', customerId: null, customerName: '', yearMonth: '2026-10', amount: 18, note: '外协' }],
      });
    const wrapper = mount(FinanceWorkspace);
    await flushPromises();
    await wrapper.get('[data-metric="result"]').trigger('click');
    await flushPromises();

    const detail = wrapper.get('[aria-label="已记录成本明细"]');
    expect(detail.text()).toContain('读取失败');
    expect(detail.text()).toContain('明细读取失败');
    expect(detail.text()).not.toContain('0.00');
    await detail.get('button.finance-retry').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('全部合计 18.00');
    expect(fetchFinanceDetails).toHaveBeenLastCalledWith(expect.objectContaining({ kind: 'COST', page: 1, size: 20 }));
  });
});
