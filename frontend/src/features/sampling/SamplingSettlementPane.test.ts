import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import SamplingSettlementPane from './SamplingSettlementPane.vue';
import { deliverSampling, settleSamplingFee } from '../../services/samplingApi';

vi.mock('../../services/samplingApi', () => ({
  deliverSampling: vi.fn(),
  settleSamplingFee: vi.fn(),
}));

describe('打样分次交付和样费', () => {
  it('部分交付不显示完成，免收不提交零元', async () => {
    vi.mocked(deliverSampling).mockResolvedValue({
      deliveryId: 'SD-1',
      samplingCode: 'DY-20261031-0001',
      versionKind: 'MODEL',
      versionNo: 1,
      materialUseId: '',
      sourceOwner: '',
      quantity: 2,
      unit: '颗',
      credentialCode: 'ATT-D',
      deliveredOn: '2026-10-31',
      remainingQuantity: 3,
      orderClosed: false,
      orderStatus: 'OPEN',
      feeState: 'UNCONFIRMED',
    });
    vi.mocked(settleSamplingFee).mockResolvedValue({
      feeId: 'SF-1',
      deliveryId: 'SD-1',
      samplingCode: 'DY-20261031-0001',
      decision: 'WAIVE',
      amount: null,
      reason: '样品不收费',
      receivableCode: '',
      motherOrderCode: '',
      wagePosted: false,
    });
    const wrapper = mount(SamplingSettlementPane);
    await wrapper.get('[aria-label="交付打样号"]').setValue('DY-20261031-0001');
    await wrapper.get('[aria-label="交付数量"]').setValue('2');
    await wrapper.get('[aria-label="交付凭据"]').setValue('ATT-D');
    await wrapper.findAll('button').find((button) => button.text() === '交付')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('剩余 3');
    expect(wrapper.text()).not.toContain('整单完成');
    expect(wrapper.text()).toContain('样费未确认');

    await wrapper.get('[aria-label="样费"]').setValue('0');
    await wrapper.findAll('button').find((button) => button.text() === '确认样费')!.trigger('click');
    expect(wrapper.text()).toContain('未知金额不能写成 0');
    expect(settleSamplingFee).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="样费"]').setValue('');
    await wrapper.get('[aria-label="免收原因"]').setValue('样品不收费');
    await wrapper.findAll('button').find((button) => button.text() === '明确免收')!.trigger('click');
    await flushPromises();
    const body = vi.mocked(settleSamplingFee).mock.calls[0][2];
    expect(body.amount).toBeNull();
    expect(body.waive).toBe(true);
    expect(body.motherOrderCode).toBe('');
    expect(body.settleWages).toBe(false);
    expect(wrapper.text()).toContain('已免收：样品不收费');
  });
});
