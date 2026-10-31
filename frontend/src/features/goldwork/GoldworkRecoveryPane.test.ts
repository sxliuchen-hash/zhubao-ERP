import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import GoldworkRecoveryPane from './GoldworkRecoveryPane.vue';
import { issueGoldworkSupplement, recordGoldworkRecovery, returnGoldworkSupplement } from '../../services/goldworkApi';

vi.mock('../../services/goldworkApi', () => ({
  recordGoldworkRecovery: vi.fn(),
  issueGoldworkSupplement: vi.fn(),
  returnGoldworkSupplement: vi.fn(),
}));

describe('金工回收和补料', () => {
  it('回收不折抵费用，补料不改最初来料', async () => {
    vi.mocked(recordGoldworkRecovery).mockResolvedValue({
      recoveryId: 'GR-1',
      goldworkCode: 'GJ-20261031-0001',
      sourceLineId: 'GL-1',
      outputMovementId: 'GM-1',
      kind: 'RECOVERABLE',
      owner: 'COMPANY',
      netWeight: 0.23456,
      precisionState: 'UNDECIDED',
      postedToLedger: false,
      feeOffset: false,
    });
    vi.mocked(issueGoldworkSupplement).mockResolvedValue({
      supplementId: 'GS-1',
      goldworkCode: 'GJ-20261031-0001',
      originalLineId: 'GL-1',
      priorSupplementId: '',
      direction: 'ISSUE',
      owner: 'COMPANY',
      sourceId: 'RC-2',
      netWeight: 0.3,
      postedToLedger: false,
      feePosted: false,
    });
    vi.mocked(returnGoldworkSupplement).mockResolvedValue({
      supplementId: 'GS-2',
      goldworkCode: 'GJ-20261031-0001',
      originalLineId: 'GL-1',
      priorSupplementId: 'GS-1',
      direction: 'RETURN',
      owner: 'COMPANY',
      sourceId: 'RC-2',
      netWeight: 0.1,
      postedToLedger: false,
      feePosted: false,
    });
    const wrapper = mount(GoldworkRecoveryPane);
    await wrapper.get('[aria-label="回收金工号"]').setValue('GJ-20261031-0001');
    await wrapper.get('[aria-label="回收来源明细"]').setValue('GL-1');
    await wrapper.get('[aria-label="成品产出"]').setValue('GM-1');
    await wrapper.get('[aria-label="回收净重"]').setValue('0');
    await wrapper.findAll('button').find((button) => button.text() === '记录回收')!.trigger('click');
    expect(wrapper.text()).toContain('未知重量不能写成 0');
    expect(recordGoldworkRecovery).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="回收净重"]').setValue('0.23456');
    await wrapper.get('[aria-label="回收成色"]').setValue('足金');
    await wrapper.findAll('button').find((button) => button.text() === '记录回收')!.trigger('click');
    await flushPromises();
    const recovery = vi.mocked(recordGoldworkRecovery).mock.calls[0][1];
    expect(recovery.clearAsLoss).toBe(false);
    expect(recovery.addToFinished).toBe(false);
    expect(recovery.feeOffset).toBeNull();
    expect(recovery.autoReturn).toBe(false);
    expect(recovery.inferWeight).toBe(false);
    expect(wrapper.text()).toContain('未折抵费用');

    await wrapper.get('[aria-label="最初来料"]').setValue('GL-1');
    await wrapper.get('[aria-label="补料来源"]').setValue('RC-2');
    await wrapper.get('[aria-label="补料用途"]').setValue('加厚');
    await wrapper.get('[aria-label="回收净重"]').setValue('0.3');
    await wrapper.findAll('button').find((button) => button.text() === '补料')!.trigger('click');
    await flushPromises();
    const issued = vi.mocked(issueGoldworkSupplement).mock.calls[0][1];
    expect(issued.addOntoOriginal).toBe(false);
    expect(issued.changeOwner).toBe(false);
    expect(issued.feeAmount).toBeNull();
    expect(issued.originalLineId).toBe('GL-1');
    expect(issued.sourceId).toBe('RC-2');

    await wrapper.get('[aria-label="回收净重"]').setValue('0.1');
    await wrapper.findAll('button').find((button) => button.text() === '退补')!.trigger('click');
    await flushPromises();
    const returned = vi.mocked(returnGoldworkSupplement).mock.calls[0][1];
    expect(returned.priorSupplementId).toBe('GS-1');
    expect(returned.originalLineId).toBe('GL-1');
    expect(returned.feeAmount).toBeNull();
    expect(wrapper.text()).toContain('已退补');
  });
});
