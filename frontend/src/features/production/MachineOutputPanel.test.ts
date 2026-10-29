import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import MachineOutputPanel from './MachineOutputPanel.vue';
import { bindMachineOutput, fetchMachineOutputMachineMatchSuggestions, fetchMachineOutputSummary } from '../../services/machineOutputApi';

vi.mock('../../services/machineOutputApi', () => ({ bindMachineOutput: vi.fn(), fetchMachineOutputMachineMatchSuggestions: vi.fn(), fetchMachineOutputSummary: vi.fn() }));
const candidate = { machineNoSnapshot: 'M-1', productCategory: '戒托', latestWorkDate: '2026-10-29', unmatchedRecords: 1, matchedRecords: 0, candidateMachineIds: [7], readiness: 'READY_TO_BIND' as const, suggestedAction: '人工核对', totalRecords: 1, sourceRegisterType: '雕刻', sourceMachineLineNo: '1' };
beforeEach(() => {
  vi.mocked(bindMachineOutput).mockReset();
  vi.mocked(fetchMachineOutputSummary).mockReset().mockResolvedValue({ totalRecords: 2, totalQuantity: 3.5, quantityUnit: '克', unmatchedMachineRecords: 1, signatureMissingRecords: 1, byMachine: [{ machineNoSnapshot: 'M-1', quantity: 3.5, records: 2 }], byWorkDate: [] });
  vi.mocked(fetchMachineOutputMachineMatchSuggestions).mockReset().mockResolvedValue([candidate]);
});
it('保留设备来源、原小数单位和显式唯一绑定，不自动改档案', async () => {
  let finish!: (value: Awaited<ReturnType<typeof bindMachineOutput>>) => void;
  vi.mocked(bindMachineOutput).mockReturnValueOnce(new Promise(done => { finish = done; }));
  const wrapper = mount(MachineOutputPanel); await flushPromises();
  expect(wrapper.text()).toContain('3.5克');
  expect(bindMachineOutput).not.toHaveBeenCalled();
  const button = wrapper.findAll('button').find(item => item.text().includes('绑定到机器档案'))!;
  await button.trigger('click'); await button.trigger('click');
  expect(bindMachineOutput).toHaveBeenCalledOnce();
  expect(bindMachineOutput).toHaveBeenCalledWith({ machineNoSnapshot: 'M-1', machineId: 7 });
  finish({ machineNoSnapshot: 'M-1', machineId: 7, boundRecords: 1, operator: '操机员' }); await flushPromises();
  expect(wrapper.text()).toContain('已将 M-1 的 1 条记录绑定');
});
it('读取失败明确显示并可重试，权限和候选唯一性同时限制绑定', async () => {
  vi.mocked(fetchMachineOutputSummary).mockRejectedValueOnce(new Error('设备统计不可用'));
  const session = ref({ effectivePermissions: [] as string[] });
  const wrapper = mount(MachineOutputPanel, { global: { provide: { 'zhubao-auth-session': session } } }); await flushPromises();
  expect(wrapper.text()).toContain('设备统计不可用');
  expect(wrapper.find('.machine-output-summary-grid').exists()).toBe(false);
  expect(wrapper.text()).not.toContain('绑定到机器档案');
  session.value.effectivePermissions.push('MACHINE_OUTPUT_SUBMIT');
  vi.mocked(fetchMachineOutputMachineMatchSuggestions).mockResolvedValueOnce([{ ...candidate, candidateMachineIds: [7, 8] }]);
  await wrapper.findAll('button').find(item => item.text() === '刷新设备统计与匹配建议')!.trigger('click'); await flushPromises();
  expect(wrapper.text()).toContain('3.5克');
  expect(wrapper.text()).not.toContain('绑定到机器档案');
  expect(bindMachineOutput).not.toHaveBeenCalled();
});
