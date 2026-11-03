import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import GoldworkOrderWorkspace from './GoldworkOrderWorkspace.vue';
import { fetchGoldworkOrders, openGoldworkOrder } from '../../services/goldworkApi';

vi.mock('../../services/goldworkApi', () => ({
  fetchGoldworkOrders: vi.fn(),
  openGoldworkOrder: vi.fn(),
}));

const opened = {
  goldworkCode: 'GJ-20261031-0001',
  businessReference: 'GOLDWORK|DOCUMENT|GJ-20261031-0001',
  customerId: 1,
  customerName: '甲客户',
  materialIntent: 'CUSTOMER_SUPPLIED',
  materialIntentLabel: '客户来料',
  requirement: '足金戒托',
  ownerName: '张三',
  routeStepIds: ['GOLD_FLAT', 'GOLD_MODEL', 'GOLD_CAST', 'GOLD_FINISH', 'POLISH_GOLD'],
  routeStepLabels: ['平设', '建模', '倒模', '执模', '抛光（金）'],
  status: 'OPEN',
  statusLabel: '进行中',
  version: 1,
  openedBy: '金工',
  openedOn: '2026-10-31',
};

describe('金工单查询和建单', () => {
  it('展示冻结要求和抛光金路线，建单不提交母单号或打样号', async () => {
    vi.mocked(fetchGoldworkOrders).mockResolvedValue([opened]);
    vi.mocked(openGoldworkOrder).mockResolvedValue({
      ...opened,
      goldworkCode: 'GJ-20261031-0002',
      customerId: 2,
      customerName: '乙客户',
      materialIntent: 'COMPANY_PREPARED',
      materialIntentLabel: '公司自备',
      requirement: '公司备料金托',
      ownerName: '李四',
    });
    const wrapper = mount(GoldworkOrderWorkspace);
    expect(wrapper.text()).toContain('登记导入');
    expect(wrapper.text()).toContain('打样');
    expect(wrapper.text()).toContain('页面不上传模型');
    expect(wrapper.find('input[accept*="3dm"]').exists()).toBe(false);
    await wrapper.findAll('button').find((button) => button.text() === '查询金工单')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('GJ-20261031-0001');
    expect(wrapper.text()).toContain('足金戒托');
    expect(wrapper.text()).toContain('抛光（金）');
    expect(wrapper.text()).not.toContain('抛光（珠）');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).not.toContain('已完成');

    await wrapper.get('[aria-label="客户编号"]').setValue('2');
    await wrapper.get('[aria-label="材料安排"]').setValue('COMPANY_PREPARED');
    await wrapper.get('[aria-label="金工要求"]').setValue('公司备料金托');
    await wrapper.get('[aria-label="负责人"]').setValue('李四');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    const body = vi.mocked(openGoldworkOrder).mock.calls[0][0];
    expect(body.customerId).toBe(2);
    expect(body.materialIntent).toBe('COMPANY_PREPARED');
    expect(body.requirement).toBe('公司备料金托');
    expect(body.ownerName).toBe('李四');
    expect(body).not.toHaveProperty('motherOrderCode');
    expect(body).not.toHaveProperty('samplingCode');
    expect(body).not.toHaveProperty('routeSteps');
    expect(JSON.stringify(body)).not.toContain('MD-');
    expect(JSON.stringify(body)).not.toContain('DY-');
  });
});
