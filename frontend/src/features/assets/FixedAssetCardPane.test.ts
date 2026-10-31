import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import FixedAssetCardPane from './FixedAssetCardPane.vue';
import { fetchFixedAssetCards, receiveFixedAsset } from '../../services/fixedAssetApi';

vi.mock('../../services/fixedAssetApi', () => ({
  fetchFixedAssetCards: vi.fn(),
  receiveFixedAsset: vi.fn(),
}));

const pending = { presence: 'PENDING', value: '', label: '待补' };

describe('资产卡片查询及购置', () => {
  it('缺位置显示待补，空原值不写成 0，切换卡片不带走金额', async () => {
    vi.mocked(fetchFixedAssetCards).mockResolvedValue([
      {
        code: 'FIN-AST-1',
        name: '抛光机',
        category: '设备',
        originalValue: 1000,
        residualValue: 100,
        startMonth: '2026-09',
        usefulMonths: 12,
        model: pending,
        serialNumber: pending,
        location: pending,
        custodian: pending,
        useStatus: pending,
        version: 0,
      },
      {
        code: 'FIN-AST-2',
        name: '空压机',
        category: '设备',
        originalValue: 2500,
        residualValue: 100,
        startMonth: '2026-09',
        usefulMonths: 12,
        model: pending,
        serialNumber: pending,
        location: { presence: 'RECORDED', value: '仓库', label: '仓库' },
        custodian: pending,
        useStatus: pending,
        version: 1,
      },
    ]);
    vi.mocked(receiveFixedAsset).mockResolvedValue({
      acquisitionId: 'FIN-ACQ-1',
      assetCode: '',
      source: 'PURCHASE',
      amountState: 'INCOMPLETE',
      originalValue: null,
      supplierLabel: '甲供应商',
      acquiredOnLabel: '2026-10-01',
      handoverLocationLabel: '待补',
      handoverReceiverLabel: '待补',
      payableCode: '',
      coveredPayableCode: '',
      paymentCode: '',
      paidAmount: null,
      paid: false,
    });
    const wrapper = mount(FixedAssetCardPane);
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('位置 待补');
    expect(wrapper.text()).not.toContain('位置 0');
    expect(wrapper.text()).toContain('原值 1000');

    await wrapper.get('[aria-label="购置原值"]').setValue('');
    await wrapper.get('[aria-label="供应商"]').setValue('甲供应商');
    await wrapper.get('[aria-label="购置日期"]').setValue('2026-10-01');
    await wrapper.get('[aria-label="购置名称"]').setValue('抛光机');
    await wrapper.get('[aria-label="购置分类"]').setValue('设备');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    const body = vi.mocked(receiveFixedAsset).mock.calls[0][0];
    expect(body.originalValue).toBeNull();
    expect(body.paymentAmount).toBeNull();
    expect(JSON.stringify(body)).not.toContain('"originalValue":0');
    expect(wrapper.text()).toContain('金额未齐');

    await wrapper.get('[aria-label="购置原值"]').setValue('800');
    await wrapper.get('[aria-label="付款金额"]').setValue('100');
    await wrapper.get('form').trigger('submit');
    expect(receiveFixedAsset).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('没有付款凭据');

    const firstCard = wrapper.findAll('li button')[0];
    await firstCard.trigger('click');
    expect((wrapper.get('[aria-label="购置原值"]').element as HTMLInputElement).value).toBe('');
    expect((wrapper.get('[aria-label="付款金额"]').element as HTMLInputElement).value).toBe('');

    await wrapper.get('[aria-label="购置来源"]').setValue('EXISTING_CARD');
    await wrapper.get('[aria-label="已有资产编码"]').setValue('FIN-AST-1');
    await wrapper.get('[aria-label="购置日期"]').setValue('2026-10-02');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(vi.mocked(receiveFixedAsset).mock.calls.at(-1)?.[0]).toEqual(expect.objectContaining({
      source: 'EXISTING_CARD',
      assetCode: 'FIN-AST-1',
      originalValue: null,
      paymentAmount: null,
    }));
    wrapper.unmount();
  });
});
