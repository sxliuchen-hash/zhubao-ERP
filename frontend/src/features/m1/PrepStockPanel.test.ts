import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PrepStockPanel from './PrepStockPanel.vue';
import { apiGet, apiPost } from '../../services/apiClient';

vi.mock('../../services/apiClient', () => ({ apiGet: vi.fn(), apiPost: vi.fn() }));

describe('PrepStockPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(apiGet).mockResolvedValue([]);
  });

  async function fill(wrapper: ReturnType<typeof mount>) {
    await wrapper.get('[aria-label="备料物料"]').setValue('AU9999');
    await wrapper.get('[aria-label="备料仓库"]').setValue('RAW-CUSTOMER');
    await wrapper.get('[aria-label="备料批次"]').setValue('BATCH-AUDIT-1');
    await wrapper.get('[aria-label="领用母单"]').setValue('MD-PREP-001');
    await wrapper.get('[aria-label="备料数量"]').setValue('2');
    await wrapper.get('[aria-label="备料重量"]').setValue('3');
  }

  it('收货、领用和退回在失败重试时复用请求编号，内容变化才换号', async () => {
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('响应丢失')).mockResolvedValue('已登记');
    const wrapper = mount(PrepStockPanel);
    await fill(wrapper);
    const receive = () => wrapper.findAll('button').find((button) => button.text() === '客户备料收货')!;
    await receive().trigger('click');
    await flushPromises();
    await receive().trigger('click');
    await flushPromises();
    const first = (vi.mocked(apiPost).mock.calls[0][1] as { requestId: string }).requestId;
    const second = (vi.mocked(apiPost).mock.calls[1][1] as { requestId: string }).requestId;
    expect(second).toBe(first);
    expect(vi.mocked(apiPost).mock.calls[0][0]).toBe('/api/inventory/prep-stock/customer-receipts');

    await wrapper.get('[aria-label="备料数量"]').setValue('4');
    vi.mocked(apiPost).mockResolvedValueOnce('已登记');
    await receive().trigger('click');
    await flushPromises();
    const third = (vi.mocked(apiPost).mock.calls[2][1] as { requestId: string }).requestId;
    expect(third).not.toBe(first);

    const issue = wrapper.findAll('button').find((button) => button.text() === '分批领用')!;
    const back = wrapper.findAll('button').find((button) => button.text() === '退回备料')!;
    await issue.trigger('click');
    await flushPromises();
    await issue.trigger('click');
    await flushPromises();
    const issueFirst = (vi.mocked(apiPost).mock.calls[3][1] as { requestId: string }).requestId;
    const issueSecond = (vi.mocked(apiPost).mock.calls[4][1] as { requestId: string }).requestId;
    expect(issueSecond).toBe(issueFirst);
    expect(vi.mocked(apiPost).mock.calls[3][0]).toBe('/api/inventory/prep-stock/issues');

    await back.trigger('click');
    await flushPromises();
    await back.trigger('click');
    await flushPromises();
    const returnFirst = (vi.mocked(apiPost).mock.calls[5][1] as { requestId: string }).requestId;
    const returnSecond = (vi.mocked(apiPost).mock.calls[6][1] as { requestId: string }).requestId;
    expect(returnSecond).toBe(returnFirst);
    expect(vi.mocked(apiPost).mock.calls[5][0]).toBe('/api/inventory/prep-stock/returns');
    wrapper.unmount();
  });

  it('提交进行中再次点击不会发出第二次请求', async () => {
    let release: (value: string) => void = () => {};
    vi.mocked(apiPost).mockImplementation(() => new Promise((resolve) => {
      release = resolve;
    }));
    const wrapper = mount(PrepStockPanel);
    await fill(wrapper);
    const receive = wrapper.findAll('button').find((button) => button.text() === '客户备料收货')!;
    const pending = receive.trigger('click');
    await Promise.resolve();
    await receive.trigger('click');
    expect(apiPost).toHaveBeenCalledTimes(1);
    release('已登记');
    await pending;
    await flushPromises();
    wrapper.unmount();
  });
});
