import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import FixedAssetRepairPane from './FixedAssetRepairPane.vue';
import { confirmAssetRepair, fetchAssetRepairs, payAssetRepair, recordAssetRepair } from '../../services/fixedAssetApi';

vi.mock('../../services/fixedAssetApi', () => ({
  fetchAssetRepairs: vi.fn(),
  recordAssetRepair: vi.fn(),
  confirmAssetRepair: vi.fn(),
  payAssetRepair: vi.fn(),
}));

describe('资产维修凭据', () => {
  it('未确认费用显示未齐，无凭据不能付款，切换资产不带走金额', async () => {
    vi.mocked(fetchAssetRepairs).mockResolvedValue([
      {
        repairId: 'FIN-RPR-1',
        assetCode: 'FIN-AST-1',
        originalValue: 1000,
        repairedOn: '2026-10-08',
        handoverLabel: '待补',
        evidenceLabel: '待补',
        state: 'RECORDED',
        stateLabel: '维修中',
        payableCode: '',
        costAmount: null,
        paidAmount: null,
        paid: false,
      payments: [],
      },
    ]);
    vi.mocked(recordAssetRepair).mockResolvedValue({
      repairId: 'FIN-RPR-1',
      assetCode: 'FIN-AST-1',
      originalValue: 1000,
      repairedOn: '2026-10-08',
      handoverLabel: '待补',
      evidenceLabel: '待补',
      state: 'RECORDED',
      stateLabel: '维修中',
      payableCode: '',
      costAmount: null,
      paidAmount: null,
      paid: false,
      payments: [],
    });
    vi.mocked(confirmAssetRepair).mockResolvedValue({
      repairId: 'FIN-RPR-1',
      assetCode: 'FIN-AST-1',
      originalValue: 1000,
      repairedOn: '2026-10-08',
      handoverLabel: '待补',
      evidenceLabel: 'EV-1',
      state: 'CONFIRMED',
      stateLabel: '费用已确认',
      payableCode: 'FIN-AP-1',
      costAmount: 200,
      paidAmount: 0,
      paid: false,
      payments: [],
    });
    vi.mocked(payAssetRepair).mockResolvedValue({
      repairId: 'FIN-RPR-1',
      assetCode: 'FIN-AST-1',
      originalValue: 1000,
      repairedOn: '2026-10-08',
      handoverLabel: '待补',
      evidenceLabel: 'EV-1',
      state: 'CONFIRMED',
      stateLabel: '费用已确认',
      payableCode: 'FIN-AP-1',
      costAmount: 200,
      paidAmount: 80,
      paid: true,
      payments: [{ paymentCode: "PMT1", voucher: "PV-1" }],
    });
    const wrapper = mount(FixedAssetRepairPane);
    await wrapper.get('[aria-label="维修资产编码"]').setValue('FIN-AST-1');
    await wrapper.get('[aria-label="查询维修"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('维修中');
    expect(wrapper.text()).toContain('费用 未齐');
    expect(wrapper.text()).toContain('未付');
    expect(wrapper.text()).not.toContain('报废');

    await wrapper.get('[aria-label="确认维修费用"]').trigger('submit');
    expect(confirmAssetRepair).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请选择维修记录');

    await wrapper.get('[aria-label="维修单号"]').setValue('FIN-RPR-1');
    await wrapper.get('[aria-label="确认维修费用"]').trigger('submit');
    expect(wrapper.text()).toContain('金额未齐');
    expect(confirmAssetRepair).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="维修费用"]').setValue('200');
    await wrapper.get('[aria-label="确认维修费用"]').trigger('submit');
    expect(wrapper.text()).toContain('必须有凭据');

    await wrapper.get('[aria-label="维修凭据"]').setValue('EV-1');
    await wrapper.get('[aria-label="确认维修费用"]').trigger('submit');
    await flushPromises();
    expect(confirmAssetRepair).toHaveBeenCalledWith(expect.objectContaining({
      repairId: 'FIN-RPR-1',
      amount: 200,
      evidenceCode: 'EV-1',
    }));
    expect(wrapper.text()).toContain('尚未付款');

    await wrapper.get('[aria-label="支付维修费"]').trigger('submit');
    expect(payAssetRepair).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('没有付款凭据');

    await wrapper.get('[aria-label="维修付款金额"]').setValue('80');
    await wrapper.get('[aria-label="维修付款日期"]').setValue('2026-10-09');
    await wrapper.get('[aria-label="维修付款凭据"]').setValue('PV-1');
    await wrapper.get('[aria-label="支付维修费"]').trigger('submit');
    await flushPromises();
    expect(payAssetRepair).toHaveBeenCalledWith(expect.objectContaining({ amount: 80, voucher: 'PV-1' }));
    expect(wrapper.text()).toContain('成本仍是 200');

    await wrapper.get('[aria-label="维修资产编码"]').setValue('FIN-AST-2');
    await wrapper.get('[aria-label="维修资产编码"]').trigger('change');
    expect((wrapper.get('[aria-label="维修费用"]').element as HTMLInputElement).value).toBe('');
    expect((wrapper.get('[aria-label="维修付款金额"]').element as HTMLInputElement).value).toBe('');
    wrapper.unmount();
  });
});
