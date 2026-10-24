import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import CustomerArchiveWorkspace from './CustomerArchiveWorkspace.vue';
import { fetchStatement } from '../../services/financeApi';
import { fetchCustomers, fetchMaterials, saveCustomer, saveMaterial } from '../../services/masterDataApi';
import { fetchProgress } from '../../services/progressApi';

vi.mock('../../services/masterDataApi', async () => ({
  ...await vi.importActual<typeof import('../../services/masterDataApi')>('../../services/masterDataApi'),
  fetchCustomers: vi.fn(),
  fetchMaterials: vi.fn(),
  saveCustomer: vi.fn(),
  saveMaterial: vi.fn(),
}));
vi.mock('../../services/financeApi', () => ({ fetchStatement: vi.fn() }));
vi.mock('../../services/progressApi', () => ({ fetchProgress: vi.fn() }));

const hengxin = {
  customerId: 1001,
  customerCode: 'C-1001',
  name: '恒信珠宝',
  aliases: '',
  contact: '林姐',
  phone: '13800000000',
  remark: '老客户',
  enabled: true,
};
const cuiyu = { ...hengxin, customerId: 1002, customerCode: 'C-1002', name: '翠玉坊' };
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
function statement(customerId: number, amount: number) { return { customerId, customerName: customerId === 1001 ? hengxin.name : cuiyu.name, stockQuantity: 0, stockWeight: 0, receivableAmount: amount, receivedAmount: 0, outstandingAmount: amount, prepaymentAmount: 0, lines: [], receipts: [], stockNote: '' }; }

function mountArchive(role = 'RECEIVER', coverDuties: string[] = []) {
  return mount(CustomerArchiveWorkspace, {
    global: { provide: { 'zhubao-auth-session': ref({ role, coverDuties }) } },
  });
}

describe('客户档案', () => {
  beforeEach(() => {
    vi.mocked(fetchCustomers).mockReset().mockResolvedValue([hengxin]);
    vi.mocked(fetchMaterials).mockResolvedValue([{
      materialCode: 'AU9999', name: '足金', aliases: '黄金', remark: '', enabled: true,
    }]);
    vi.mocked(fetchStatement).mockReset();
    vi.mocked(fetchProgress).mockReset();
    vi.mocked(saveCustomer).mockReset();
    vi.mocked(saveMaterial).mockReset();
  });

  it('客户订单按具体子单进入统一详情，客户名称不作为目标', async () => {
    vi.mocked(fetchStatement).mockRejectedValue(new Error('无权查看财务'));
    vi.mocked(fetchProgress).mockResolvedValue([{ customerId: 1001, customerName: '恒信珠宝', motherOrderCode: 'MD-1', workOrderCode: 'ZD-1', currentProcess: '抛光', dueDate: '', overdue: false, qualityOutcome: '', status: '在制', openTab: 'production' }]);
    const openOrder = vi.fn();
    const wrapper = mount(CustomerArchiveWorkspace, { global: { provide: { 'zhubao-open-order': openOrder } } });
    await flushPromises();
    await wrapper.get('[aria-label="选择客户 恒信珠宝"]').trigger('click');
    await flushPromises();
    await wrapper.get('[aria-label="客户订单进度"] button.table-action').trigger('click');
    expect(openOrder).toHaveBeenCalledWith(expect.objectContaining({ objectType: 'WORK_ORDER', code: 'ZD-1' }));
    wrapper.unmount();
  });

  it('新建客户后选入列表', async () => {
    vi.mocked(saveCustomer).mockResolvedValue({
      customerId: 2088, customerCode: 'C-2088', name: '翠玉坊', aliases: '', contact: '', phone: '', remark: '', enabled: true,
    });
    vi.mocked(fetchStatement).mockResolvedValue({
      customerId: 2088, customerName: '翠玉坊', stockQuantity: 0, stockWeight: 0,
      receivableAmount: 0, receivedAmount: 0, outstandingAmount: 0, prepaymentAmount: 0,
      lines: [], receipts: [], stockNote: '',
    });
    vi.mocked(fetchProgress).mockResolvedValue([]);
    const wrapper = mountArchive();
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '新建客户')!.trigger('click');
    await wrapper.get('[aria-label="保存客户"]').trigger('click');
    expect(wrapper.text()).toContain('请填写客户名称');

    await wrapper.get('[aria-label="客户名称"]').setValue('翠玉坊');
    await wrapper.get('[aria-label="保存客户"]').trigger('click');
    await flushPromises();
    expect(saveCustomer).toHaveBeenCalledWith(expect.objectContaining({ name: '翠玉坊', customerId: 0, enabled: true }));
    expect(wrapper.text()).toContain('已保存客户「翠玉坊」');
    expect(wrapper.text()).toContain('C-2088');
    wrapper.unmount();
  });

  it('选中客户后查看账单和在制进度', async () => {
    vi.mocked(fetchStatement).mockResolvedValueOnce({
      customerId: 1001,
      customerName: '恒信珠宝',
      stockQuantity: 130.5,
      stockWeight: 8,
      receivableAmount: 120,
      receivedAmount: 20,
      outstandingAmount: 100,
      prepaymentAmount: 0,
      lines: [{
        code: 'AR-1', customerId: 1001, customerName: '恒信珠宝', yearMonth: '2026-10',
        sourceType: 'PROCESS', motherOrderCode: 'MD-1', workOrderCode: 'ZD-1', processName: '抛光',
        quantity: 1, unit: '件', unitPrice: 120, amount: 120, reversesCode: '', remark: '',
      }],
      receipts: [],
      stockNote: '在库按当前结余',
    });
    vi.mocked(fetchProgress).mockResolvedValueOnce([{
      customerId: 1001, customerName: '恒信珠宝', motherOrderCode: 'MD-1', workOrderCode: 'ZD-1',
      currentProcess: '抛光', dueDate: '2026-10-20', overdue: true, qualityOutcome: '', status: '在制', openTab: 'production',
    }]);
    const wrapper = mountArchive('FINANCE_STATISTICIAN');
    await flushPromises();
    await wrapper.get('[aria-label="选择客户 恒信珠宝"]').trigger('click');
    await flushPromises();
    expect(fetchStatement).toHaveBeenCalledWith('1001', expect.stringMatching(/^\d{4}-\d{2}$/));
    expect(fetchProgress).toHaveBeenCalledWith({ customer: '1001' });
    expect(wrapper.get('[aria-label="客户账单"]').text()).toContain('100.00');
    expect(wrapper.get('[aria-label="客户账单"]').text()).toContain('抛光');
    expect(wrapper.get('[aria-label="客户账单"]').text()).not.toContain('130.5');
    expect(wrapper.get('[aria-label="客户账单"]').text()).toContain('按物料与单位分别核对');
    expect(wrapper.get('[aria-label="客户账单"]').text()).toContain('1 件');
    expect(wrapper.get('[aria-label="客户订单进度"]').text()).toContain('MD-1');
    expect(wrapper.get('[aria-label="客户订单进度"]').text()).toContain('逾期');
    expect(wrapper.text()).not.toContain('保存客户');
    wrapper.unmount();
  });

  it('账单无权限时保留原因，进度为空时说明', async () => {
    vi.mocked(fetchStatement).mockRejectedValueOnce(new Error('无权查看该客户的财务'));
    vi.mocked(fetchProgress).mockResolvedValueOnce([]);
    const wrapper = mountArchive('SALES');
    await flushPromises();
    expect(wrapper.find('[aria-label="保存客户"]').exists()).toBe(false);
    await wrapper.get('[aria-label="选择客户 恒信珠宝"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('无权查看该客户的财务');
    expect(wrapper.text()).toContain('这家客户暂无在制单据');
    wrapper.unmount();
  });

  it('保存星月的冷藏真空与完整说明', async () => {
    vi.mocked(saveMaterial).mockImplementation(async body => body);
    const wrapper = mountArchive();
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '材质管理')!.trigger('click');
    await wrapper.findAll('button').find(button => button.text() === '新建材质')!.trigger('click');
    await wrapper.get('[aria-label="材质名称"]').setValue('星月');
    await wrapper.get('[aria-label="冷藏"]').setValue(true);
    await wrapper.get('[aria-label="真空"]').setValue(true);
    await wrapper.get('[aria-label="保存说明"]').setValue('按本单核对密封状态');
    await wrapper.get('[aria-label="保存材质"]').trigger('click');
    await flushPromises();
    expect(saveMaterial).toHaveBeenCalledWith(expect.objectContaining({ storageConditions: ['COLD', 'VACUUM'], storageInstruction: '按本单核对密封状态' }));
    expect(wrapper.text()).toContain('未配置不代表常温');
    wrapper.unmount();
  });

  it('材质页可以新建常见材质', async () => {
    vi.mocked(saveMaterial).mockResolvedValue({
      materialCode: 'MAT-JADE', name: '翡翠', aliases: '', remark: '', enabled: true,
    });
    const wrapper = mountArchive();
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '材质管理')!.trigger('click');
    expect(wrapper.text()).toContain('足金');
    expect(wrapper.text()).toContain('来料新建母单时');
    await wrapper.findAll('button').find((button) => button.text() === '新建材质')!.trigger('click');
    await wrapper.get('[aria-label="材质名称"]').setValue('翡翠');
    await wrapper.get('[aria-label="保存材质"]').trigger('click');
    await flushPromises();
    expect(saveMaterial).toHaveBeenCalledWith(expect.objectContaining({ name: '翡翠', materialCode: '', enabled: true }));
    expect(wrapper.text()).toContain('已保存材质「翡翠」');
    expect(wrapper.text()).toContain('MAT-JADE');
    wrapper.unmount();
  });
  it('切客户立即撤下旧账单及在制，迟到响应不能写入新客户详情', async () => {
    vi.mocked(fetchCustomers).mockResolvedValue([hengxin, cuiyu]);
    const firstStatement = deferred<ReturnType<typeof statement>>();
    const firstProgress = deferred<Awaited<ReturnType<typeof fetchProgress>>>();
    vi.mocked(fetchStatement).mockReturnValueOnce(firstStatement.promise).mockResolvedValueOnce(statement(1002, 222));
    vi.mocked(fetchProgress).mockReturnValueOnce(firstProgress.promise).mockResolvedValueOnce([]);
    const wrapper = mountArchive('FINANCE_STATISTICIAN'); await flushPromises();
    await wrapper.get('[aria-label="选择客户 恒信珠宝"]').trigger('click');
    await wrapper.get('[aria-label="选择客户 翠玉坊"]').trigger('click'); await flushPromises();
    firstStatement.resolve(statement(1001, 9999));
    firstProgress.resolve([{ customerId: 1001, customerName: hengxin.name, motherOrderCode: 'MO-A-OLD', workOrderCode: 'WO-A-OLD', currentProcess: '抛光', dueDate: '', overdue: false, qualityOutcome: '', status: '在制', openTab: 'production' }]);
    await flushPromises();
    expect(wrapper.get('[aria-label="客户资料"]').text()).toContain('翠玉坊');
    expect(wrapper.get('[aria-label="客户账单"]').text()).toContain('222.00');
    expect(wrapper.text()).not.toContain('9999.00'); expect(wrapper.text()).not.toContain('MO-A-OLD');
    wrapper.unmount();
  });
  it('月份改变立刻清旧金额，旧月份查询迟到也不能写回', async () => {
    const first = deferred<ReturnType<typeof statement>>();
    vi.mocked(fetchStatement).mockReturnValueOnce(first.promise).mockResolvedValueOnce(statement(1001, 120));
    vi.mocked(fetchProgress).mockResolvedValue([]);
    const wrapper = mountArchive('FINANCE_STATISTICIAN'); await flushPromises();
    await wrapper.get('[aria-label="选择客户 恒信珠宝"]').trigger('click');
    await wrapper.get('[aria-label="账单月份"]').setValue('2026-09'); await flushPromises();
    first.resolve(statement(1001, 8888)); await flushPromises();
    expect(fetchStatement).toHaveBeenLastCalledWith('1001', '2026-09');
    expect(wrapper.get('[aria-label="客户账单"]').text()).toContain('120.00');
    expect(wrapper.text()).not.toContain('8888.00'); wrapper.unmount();
  });
  it('有效权限未含财务读取时不请求或渲染金额，客户编辑也不能靠角色扩大', async () => {
    vi.mocked(fetchStatement).mockResolvedValue(statement(1001, 123456)); vi.mocked(fetchProgress).mockResolvedValue([]);
    const wrapper = mount(CustomerArchiveWorkspace, { global: { provide: { 'zhubao-auth-session': ref({ role: 'ADMIN', effectivePermissions: [] }) } } });
    await flushPromises(); await wrapper.get('[aria-label="选择客户 恒信珠宝"]').trigger('click'); await flushPromises();
    expect(fetchStatement).not.toHaveBeenCalled(); expect(wrapper.find('[aria-label="客户账单"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('123456'); expect(wrapper.find('[aria-label="保存客户"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('编辑客户资料'); wrapper.unmount();
  });
  it('选择客户默认核对资料，编辑按需打开，保存期间不能切到另一客户', async () => {
    vi.mocked(fetchCustomers).mockResolvedValue([hengxin, cuiyu]); vi.mocked(fetchStatement).mockResolvedValue(statement(1001, 0)); vi.mocked(fetchProgress).mockResolvedValue([]);
    const saved = deferred<typeof hengxin>(); vi.mocked(saveCustomer).mockReturnValue(saved.promise);
    const wrapper = mountArchive(); await flushPromises();
    await wrapper.get('[aria-label="选择客户 恒信珠宝"]').trigger('click'); await flushPromises();
    expect(wrapper.get('[aria-label="保存客户"]').isVisible()).toBe(false);
    await wrapper.findAll('button').find(button => button.text() === '编辑客户资料')!.trigger('click');
    await wrapper.get('[aria-label="客户名称"]').setValue('恒信新名'); await wrapper.get('[aria-label="保存客户"]').trigger('click');
    const other = wrapper.get('[aria-label="选择客户 翠玉坊"]'); expect(other.attributes('disabled')).toBeDefined();
    await other.trigger('click');
    saved.resolve({ ...hengxin, name: '恒信新名' }); await flushPromises();
    expect(saveCustomer).toHaveBeenCalledWith(expect.objectContaining({ customerId: 1001, name: '恒信新名' }));
    expect(wrapper.get('[aria-label="客户资料"]').text()).toContain('恒信新名');
    expect(wrapper.get('[aria-label="选择客户 恒信新名"]').attributes('aria-pressed')).toBe('true'); wrapper.unmount();
  });
  it('客户账单交给共享容器时只传稳定编号、月份和原应收行，不传客户名称或总额', async () => {
    const bill = { ...statement(1001, 10), lines: [{ code: 'AR-1', customerId: 1001, customerName: hengxin.name, yearMonth: '2026-10', sourceType: 'PROCESS', motherOrderCode: 'MO-1', workOrderCode: 'WO-1', processName: '抛光', quantity: 1, unit: '件', unitPrice: 10, amount: 10, reversesCode: '', remark: '' }] };
    vi.mocked(fetchStatement).mockResolvedValue(bill); vi.mocked(fetchProgress).mockResolvedValue([]);
    const open = vi.fn();
    const wrapper = mount(CustomerArchiveWorkspace, { global: { provide: { 'zhubao-open-bill': open, 'zhubao-visible-tabs': ref(['finance']), 'zhubao-auth-session': ref({ effectivePermissions: ['FINANCE_CUSTOMER_READ', 'FINANCE_RECEIPT'] }) } } });
    await flushPromises(); await wrapper.get('[aria-label="选择客户 恒信珠宝"]').trigger('click'); await flushPromises();
    await wrapper.get('[aria-label="账单月份"]').setValue('2026-10'); await flushPromises();
    await wrapper.get('[aria-label="办理应收行 AR-1"]').trigger('click');
    expect(open).toHaveBeenCalledWith({ customerId: 1001, yearMonth: '2026-10', receivableCode: 'AR-1' }, 'receipt'); wrapper.unmount();
  });
});
