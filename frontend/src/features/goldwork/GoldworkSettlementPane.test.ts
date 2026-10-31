import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import GoldworkSettlementPane from './GoldworkSettlementPane.vue';
import { deliverGoldwork } from '../../services/goldworkApi';

vi.mock('../../services/goldworkApi', () => ({
  deliverGoldwork: vi.fn(),
}));

describe('金工交付', () => {
  it('不提交金价或金额，费用保持未确认', async () => {
    vi.mocked(deliverGoldwork).mockResolvedValue({
      deliveryId: 'GD-1',
      goldworkCode: 'GJ-20261031-0001',
      qualityId: 'GQ-1',
      netWeight: 0.23456,
      purity: '足金',
      feeState: 'UNCONFIRMED',
      receivablePosted: false,
      salesPosted: false,
      ordinaryShipment: false,
    });
    const wrapper = mount(GoldworkSettlementPane);
    expect(wrapper.text()).toContain('不能按克数乘金价');
    expect(wrapper.find('[aria-label="金价"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="金额"]').exists()).toBe(false);
    await wrapper.get('[aria-label="交付金工号"]').setValue('GJ-20261031-0001');
    await wrapper.get('[aria-label="放行份额"]').setValue('GQ-1');
    await wrapper.get('[aria-label="交付净重"]').setValue('0');
    await wrapper.get('button').trigger('click');
    expect(wrapper.text()).toContain('未知重量不能写成 0');
    expect(deliverGoldwork).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="交付净重"]').setValue('0.23456');
    await wrapper.get('button').trigger('click');
    await flushPromises();
    const body = vi.mocked(deliverGoldwork).mock.calls[0][1];
    expect(body.qualityId).toBe('GQ-1');
    expect(body.netWeight).toBe(0.23456);
    expect(body.goldPrice).toBeNull();
    expect(body.amount).toBeNull();
    expect(body.shipAsProcessing).toBe(false);
    expect(body.countCustomerGoldAsSales).toBe(false);
    expect(wrapper.text()).toContain('费用未确认');
    expect(wrapper.text()).not.toContain('应收 0');
  });
});
