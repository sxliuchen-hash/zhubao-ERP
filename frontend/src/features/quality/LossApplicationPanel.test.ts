import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import LossApplicationPanel from './LossApplicationPanel.vue';
import { approveLoss } from '../../services/businessOperationApi';
import { ApiRequestError } from '../../services/apiClient';
import type { LossApproval } from '../../data/m1PrototypeData';

vi.mock('../../services/businessOperationApi', () => ({ approveLoss: vi.fn() }));
const application: LossApproval = { code: '', workOrder: 'WO-1', process: '抛光', standard: '', actual: '', evidence: '', action: '', tone: 'amber', operationContext: { motherOrderCode: 'MD-1', workOrderCode: 'WO-1', customerId: 1, ownerCustomerId: 1, materialCode: 'MAT', warehouseCode: 'RAW', ownerType: 'CUSTOMER', stockStatus: 'SEMI_FINISHED', quantity: 1, weight: 10 } };
async function fill(wrapper: ReturnType<typeof mount>) {
  for (const [label, value] of [['损耗申请单号', 'LOSS-1'], ['投入重量（克）', '10'], ['产出重量（克）', '9'], ['合理转移重量（克）', '0'], ['标准损耗率（%）', '2']]) await wrapper.get(`[aria-label="${label}"]`).setValue(value);
}
beforeEach(() => vi.mocked(approveLoss).mockReset());

it('仅填实物事实，未知结果锁定并按原内容重试，成功后不重复申请', async () => {
  vi.mocked(approveLoss).mockRejectedValueOnce(new ApiRequestError('超时', 408, 408));
  const wrapper = mount(LossApplicationPanel, { props: { application } });
  await fill(wrapper);
  await wrapper.get('fieldset button').trigger('click'); await flushPromises();
  const command = JSON.parse(JSON.stringify(vi.mocked(approveLoss).mock.calls[0]));
  expect(command[1]?.requestId).toMatch(/^loss-/);
  expect(command[1]).not.toHaveProperty('pieceRate');
  expect(wrapper.get('fieldset').element).toHaveProperty('disabled', true);
  await wrapper.get('[aria-label="投入重量（克）"]').setValue('99');
  vi.mocked(approveLoss).mockResolvedValueOnce({ approvalCode: 'LOSS-1', workOrderCode: 'WO-1', approvalStatus: 'WAITING_APPROVAL', lossWeight: 1, excessLossWeight: 0.8 } as Awaited<ReturnType<typeof approveLoss>>);
  await wrapper.findAll('button').find(button => button.text() === '原损耗请求重试')!.trigger('click'); await flushPromises();
  expect(vi.mocked(approveLoss).mock.calls[1]).toEqual(command);
  expect(wrapper.text()).toContain('损耗申请已记录');
  await wrapper.get('fieldset button').trigger('click');
  expect(approveLoss).toHaveBeenCalledTimes(2);
  expect(wrapper.emitted('changed')).toHaveLength(1);
});

it('明确拒绝后可修正并生成新请求，缺少权限不能申请', async () => {
  const account = ref({ effectivePermissions: [] as string[] });
  const wrapper = mount(LossApplicationPanel, { props: { application }, global: { provide: { 'zhubao-auth-session': account } } });
  await fill(wrapper); await wrapper.get('fieldset button').trigger('click');
  expect(approveLoss).not.toHaveBeenCalled();
  account.value.effectivePermissions.push('BUSINESS_LOSS_APPROVAL'); await flushPromises();
  vi.mocked(approveLoss).mockRejectedValueOnce(new ApiRequestError('单号冲突', 400, 400));
  await wrapper.get('fieldset button').trigger('click'); await flushPromises();
  const first = vi.mocked(approveLoss).mock.calls[0][1]?.requestId;
  expect(wrapper.get('fieldset').element).toHaveProperty('disabled', false);
  await wrapper.get('[aria-label="损耗申请单号"]').setValue('LOSS-2');
  vi.mocked(approveLoss).mockRejectedValueOnce(new ApiRequestError('单号冲突', 400, 400));
  await wrapper.get('fieldset button').trigger('click'); await flushPromises();
  expect(vi.mocked(approveLoss).mock.calls[1][1]?.requestId).not.toBe(first);
});
