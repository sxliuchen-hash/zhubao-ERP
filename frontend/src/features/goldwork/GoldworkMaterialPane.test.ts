import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import GoldworkMaterialPane from './GoldworkMaterialPane.vue';
import { receiveGoldworkMaterial, recordGoldworkMovement } from '../../services/goldworkApi';

vi.mock('../../services/goldworkApi', () => ({
  receiveGoldworkMaterial: vi.fn(),
  recordGoldworkMovement: vi.fn(),
}));

describe('金料金料计量和投入产出', () => {
  it('空重量不提交成 0，毛重不写成净金', async () => {
    vi.mocked(receiveGoldworkMaterial).mockResolvedValue({
      lineId: 'GL-1',
      goldworkCode: 'GJ-20261031-0001',
      owner: 'COMPANY',
      sourceId: 'RC-1',
      sourceLineNo: null,
      pieceCount: null,
      netWeight: 1.23456,
      grossWeight: null,
      weightUnit: '克',
      purity: '足金',
      precisionState: 'UNDECIDED',
      postedToLedger: false,
    });
    vi.mocked(recordGoldworkMovement).mockResolvedValue({
      movementId: 'GM-1',
      goldworkCode: 'GJ-20261031-0001',
      stepId: 'GOLD_CAST',
      kind: 'OUTPUT',
      sourceLineId: 'GL-1',
      owner: 'COMPANY',
      pieceCount: null,
      netWeight: null,
      grossWeight: 3.1,
      weightUnit: '克',
      purity: '足金',
      disposition: '',
      conversionNote: '',
      precisionState: 'UNDECIDED',
      postedToLedger: false,
    });
    const wrapper = mount(GoldworkMaterialPane);
    expect(wrapper.text()).toContain('不把毛重写成净金');
    expect(wrapper.find('option[value="POLISH_BEAD"]').exists()).toBe(false);
    await wrapper.get('[aria-label="金料金工号"]').setValue('GJ-20261031-0001');
    await wrapper.get('[aria-label="来源编号"]').setValue('RC-1');
    await wrapper.get('[aria-label="净重"]').setValue('0');
    await wrapper.get('[aria-label="成色"]').setValue('足金');
    await wrapper.findAll('button').find((button) => button.text() === '接收金料')!.trigger('click');
    expect(wrapper.text()).toContain('未知重量不能写成 0');
    expect(receiveGoldworkMaterial).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="净重"]').setValue('1.23456');
    await wrapper.findAll('button').find((button) => button.text() === '接收金料')!.trigger('click');
    await flushPromises();
    const received = vi.mocked(receiveGoldworkMaterial).mock.calls[0][1];
    expect(received.netWeight).toBe(1.23456);
    expect(received.pieceCount).toBeNull();
    expect(received.grossWeight).toBeNull();
    expect(wrapper.text()).toContain('未入账');

    await wrapper.get('[aria-label="净重"]').setValue('');
    await wrapper.get('[aria-label="毛重"]').setValue('3.1');
    await wrapper.findAll('button').find((button) => button.text() === '记录投入产出')!.trigger('click');
    await flushPromises();
    const movement = vi.mocked(recordGoldworkMovement).mock.calls[0][1];
    expect(movement.netWeight).toBeNull();
    expect(movement.grossWeight).toBe(3.1);
    expect(movement.useGrossAsNet).toBe(false);
    expect(movement.inferShortage).toBe(false);
    expect(movement.conversionFactor).toBeNull();
    expect(movement.enforcePieceConservation).toBe(false);
    expect(movement.stepId).toBe('GOLD_CAST');
    expect(wrapper.text()).toContain('净重未填');
  });
});
