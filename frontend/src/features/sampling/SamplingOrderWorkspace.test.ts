import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import SamplingOrderWorkspace from './SamplingOrderWorkspace.vue';
import { fetchSamplingOrders, openSamplingOrder } from '../../services/samplingApi';

vi.mock('../../services/samplingApi', () => ({
  fetchSamplingOrders: vi.fn(),
  openSamplingOrder: vi.fn(),
}));

describe('打样单查询和建单', () => {
  it('查询打样号和进行中状态，建单不提交母单号', async () => {
    vi.mocked(fetchSamplingOrders).mockResolvedValue([
      {
        samplingCode: 'DY-20261031-0001',
        businessReference: 'SAMPLING|DOCUMENT|DY-20261031-0001',
        customerId: 1,
        customerName: '甲客户',
        materialIntent: 'CUSTOMER_SUPPLIED',
        materialIntentLabel: '客户来料',
        requirement: '18 毫米珠型',
        ownerName: '张三',
        status: 'OPEN',
        statusLabel: '进行中',
        version: 1,
        openedBy: '打样员',
        openedOn: '2026-10-31',
      },
    ]);
    vi.mocked(openSamplingOrder).mockResolvedValue({
      samplingCode: 'DY-20261031-0002',
      businessReference: 'SAMPLING|DOCUMENT|DY-20261031-0002',
      customerId: 2,
      customerName: '乙客户',
      materialIntent: 'COMPANY_PREPARED',
      materialIntentLabel: '公司自备',
      requirement: '公司备料',
      ownerName: '李四',
      status: 'OPEN',
      statusLabel: '进行中',
      version: 1,
      openedBy: '打样员',
      openedOn: '2026-10-31',
    });
    const wrapper = mount(SamplingOrderWorkspace);
    expect(wrapper.text()).toContain('登记导入');
    expect(wrapper.text()).not.toContain('普通加工母单号');
    await wrapper.findAll('button').find((button) => button.text() === '查询打样单')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('DY-20261031-0001');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).not.toContain('已完成');
    expect(wrapper.text()).not.toContain('样费 0');

    await wrapper.get('[aria-label="客户编号"]').setValue('2');
    await wrapper.get('[aria-label="材料安排"]').setValue('COMPANY_PREPARED');
    await wrapper.get('[aria-label="样品要求"]').setValue('公司备料');
    await wrapper.get('[aria-label="负责人"]').setValue('李四');
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    const body = vi.mocked(openSamplingOrder).mock.calls[0][0];
    expect(body.customerId).toBe(2);
    expect(body.materialIntent).toBe('COMPANY_PREPARED');
    expect(body.requirement).toBe('公司备料');
    expect(body.ownerName).toBe('李四');
    expect(body).not.toHaveProperty('motherOrderCode');
    expect(body).not.toHaveProperty('samplingCode');
    expect(JSON.stringify(body)).not.toContain('MD-');
  });
});
