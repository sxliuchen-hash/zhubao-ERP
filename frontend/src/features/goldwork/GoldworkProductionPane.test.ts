import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import GoldworkProductionPane from './GoldworkProductionPane.vue';
import { decideGoldworkQuality, reportGoldworkOperation } from '../../services/goldworkApi';

vi.mock('../../services/goldworkApi', () => ({
  reportGoldworkOperation: vi.fn(),
  decideGoldworkQuality: vi.fn(),
}));

describe('金工报工和质量', () => {
  it('没有照片不报工，质量不改成色和收费', async () => {
    vi.mocked(reportGoldworkOperation).mockResolvedValue({
      reportId: 'GO-1',
      goldworkCode: 'GJ-20261031-0001',
      stepId: 'POLISH_GOLD',
      stepLabel: '抛光（金）',
      netWeight: 1.23456,
      attachmentCode: 'PH-1',
      postedToLedger: false,
      payrollPosted: false,
    });
    vi.mocked(decideGoldworkQuality).mockResolvedValue({
      qualityId: 'GQ-1',
      goldworkCode: 'GJ-20261031-0001',
      reportId: 'GO-1',
      priorQualityId: '',
      roundNo: 1,
      outcome: 'RELEASE',
      netWeight: 0.2,
      purity: '足金',
      note: '',
      purityChanged: false,
      feeChanged: false,
    });
    const wrapper = mount(GoldworkProductionPane);
    expect(wrapper.find('input[type="file"]').exists()).toBe(false);
    expect(wrapper.find('option[value="POLISH_BEAD"]').exists()).toBe(false);
    await wrapper.get('[aria-label="报工金工号"]').setValue('GJ-20261031-0001');
    await wrapper.findAll('button').find((button) => button.text() === '报工')!.trigger('click');
    expect(wrapper.text()).toContain('没有有效的现场照片');
    expect(reportGoldworkOperation).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="报工工序"]').setValue('POLISH_GOLD');
    await wrapper.get('[aria-label="报工净重"]').setValue('1.23456');
    await wrapper.get('[aria-label="报工成色"]').setValue('足金');
    await wrapper.get('[aria-label="照片编号"]').setValue('PH-1');
    await wrapper.findAll('button').find((button) => button.text() === '报工')!.trigger('click');
    await flushPromises();
    const reported = vi.mocked(reportGoldworkOperation).mock.calls[0][1];
    expect(reported.stepId).toBe('POLISH_GOLD');
    expect(reported.attachmentCode).toBe('PH-1');
    expect(reported.settleWage).toBe(false);
    expect(reported.postStock).toBe(false);
    expect(reported.closeWip).toBe(false);
    expect(wrapper.text()).toContain('未记工资');

    await wrapper.get('[aria-label="报工净重"]').setValue('0.2');
    await wrapper.findAll('button').find((button) => button.text() === '记录质量')!.trigger('click');
    await flushPromises();
    const decided = vi.mocked(decideGoldworkQuality).mock.calls[0][1];
    expect(decided.reportId).toBe('GO-1');
    expect(decided.purity).toBe('');
    expect(decided.useBeadDefault).toBe(false);
    expect(decided.changePurity).toBe(false);
    expect(decided.changeFee).toBe(false);
    expect(wrapper.text()).toContain('收费未改');
  });
});
