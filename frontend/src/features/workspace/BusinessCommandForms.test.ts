import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, expect, it, vi } from 'vitest';
import SamplingSettlementPane from '../sampling/SamplingSettlementPane.vue';
import GoldworkSettlementPane from '../goldwork/GoldworkSettlementPane.vue';
import { deliverSampling } from '../../services/samplingApi';
import { deliverGoldwork } from '../../services/goldworkApi';
import { setAuthToken } from '../../services/apiClient';

vi.mock('../../services/samplingApi', () => ({ deliverSampling: vi.fn(), settleSamplingFee: vi.fn() }));
vi.mock('../../services/goldworkApi', () => ({ deliverGoldwork: vi.fn() }));
afterEach(() => { localStorage.clear(); vi.clearAllMocks(); });

const delivered = { deliveryId: 'SD1', remainingQuantity: 3, orderClosed: false } as Awaited<ReturnType<typeof deliverSampling>>;

async function sampling(token: string) {
  setAuthToken(token);
  const view = mount(SamplingSettlementPane);
  await view.get('[aria-label="交付打样号"]').setValue('DY-20261101-0001');
  await view.get('[aria-label="交付数量"]').setValue('2');
  await view.get('[aria-label="交付凭据"]').setValue('PHOTO-DELIVERY');
  return view;
}

it('打样等待时不发第二笔，成功后明确开始下一笔才生成新请求', async () => {
  let finish!: (value: typeof delivered) => void;
  vi.mocked(deliverSampling).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; })).mockResolvedValue(delivered);
  const view = await sampling('draft-double-click');
  const button = view.findAll('button').find(item => item.text() === '交付')!;
  await button.trigger('click');
  await button.trigger('click');
  expect(deliverSampling).toHaveBeenCalledTimes(1);
  expect(view.get('fieldset').attributes('disabled')).toBeDefined();
  finish(delivered);
  await flushPromises();
  await button.trigger('click');
  expect(deliverSampling).toHaveBeenCalledTimes(1);
  await view.findAll('button').find(item => item.text() === '开始下一笔')!.trigger('click');
  await button.trigger('click');
  await flushPromises();
  expect(deliverSampling).toHaveBeenCalledTimes(2);
  expect(vi.mocked(deliverSampling).mock.calls[1][1].requestId).not.toBe(vi.mocked(deliverSampling).mock.calls[0][1].requestId);
  view.unmount();
});

it('响应丢失后保留原请求，改量先恢复原稿，切单不串草稿', async () => {
  vi.mocked(deliverSampling).mockRejectedValueOnce(new Error('响应未收到')).mockResolvedValue(delivered);
  const view = await sampling('draft-response-lost');
  const button = view.findAll('button').find(item => item.text() === '交付')!;
  await button.trigger('click');
  await flushPromises();
  const requestId = vi.mocked(deliverSampling).mock.calls[0][1].requestId;
  await view.get('[aria-label="交付数量"]').setValue('3');
  await button.trigger('click');
  expect(deliverSampling).toHaveBeenCalledTimes(1);
  expect(view.text()).toContain('结果待确认');
  await view.findAll('button').find(item => item.text() === '开始下一笔')!.trigger('click');
  expect(view.text()).toContain('先确认上次');
  await view.findAll('button').find(item => item.text() === '恢复上次填写')!.trigger('click');
  expect((view.get('[aria-label="交付数量"]').element as HTMLInputElement).value).toBe('2');
  await button.trigger('click');
  await flushPromises();
  expect(vi.mocked(deliverSampling).mock.calls[1][1].requestId).toBe(requestId);
  await view.get('[aria-label="交付打样号"]').setValue('DY-20261101-0002');
  expect((view.get('[aria-label="交付数量"]').element as HTMLInputElement).value).toBe('');
  await view.get('[aria-label="交付数量"]').setValue('7');
  await view.get('[aria-label="交付打样号"]').setValue('DY-20261101-0001');
  expect((view.get('[aria-label="交付数量"]').element as HTMLInputElement).value).toBe('2');
  await button.trigger('click');
  expect(deliverSampling).toHaveBeenCalledTimes(2);
  view.unmount();
});

it('金工交付复用同样的等待与原请求恢复规则', async () => {
  setAuthToken('gold-response-lost');
  vi.mocked(deliverGoldwork).mockRejectedValueOnce(new Error('连接中断')).mockResolvedValue({ deliveryId: 'GD1', feeState: 'UNCONFIRMED' } as Awaited<ReturnType<typeof deliverGoldwork>>);
  const view = mount(GoldworkSettlementPane);
  await view.get('[aria-label="交付金工号"]').setValue('GJ-20261101-0001');
  await view.get('[aria-label="放行份额"]').setValue('GQ1');
  await view.get('[aria-label="交付净重"]').setValue('0.2');
  const button = view.findAll('button').find(item => item.text() === '交付')!;
  await button.trigger('click');
  await flushPromises();
  await button.trigger('click');
  await flushPromises();
  expect(deliverGoldwork).toHaveBeenCalledTimes(2);
  expect(vi.mocked(deliverGoldwork).mock.calls[1][1].requestId).toBe(vi.mocked(deliverGoldwork).mock.calls[0][1].requestId);
  expect(view.text()).toContain('已交付 GD1');
  view.unmount();
});
