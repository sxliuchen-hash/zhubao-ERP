import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import CustomerArchiveWorkspace from './CustomerArchiveWorkspace.vue';
import { fetchStatement } from '../../services/financeApi';
import { fetchCustomers, fetchMaterials, saveCustomer, saveMaterial } from '../../services/masterDataApi';
import { fetchProgress } from '../../services/progressApi';

vi.mock('../../services/masterDataApi', () => ({
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

function mountArchive(role = 'RECEIVER', coverDuties: string[] = []) {
  return mount(CustomerArchiveWorkspace, {
    global: { provide: { 'zhubao-auth-session': ref({ role, coverDuties }) } },
  });
}

describe('客户档案', () => {
  beforeEach(() => {
    vi.mocked(fetchCustomers).mockResolvedValue([hengxin]);
    vi.mocked(fetchMaterials).mockResolvedValue([{
      materialCode: 'AU9999', name: '足金', aliases: '黄金', remark: '', enabled: true,
    }]);
    vi.mocked(fetchStatement).mockReset();
    vi.mocked(fetchProgress).mockReset();
    vi.mocked(saveCustomer).mockReset();
    vi.mocked(saveMaterial).mockReset();
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
      stockQuantity: 2,
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
});
