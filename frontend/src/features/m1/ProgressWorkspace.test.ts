import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import ProgressWorkspace from './ProgressWorkspace.vue';
import { ApiRequestError } from '../../services/apiClient';
import { fetchProgress } from '../../services/progressApi';
import type { TraceTarget } from '../scan/traceTarget';

vi.mock('../../services/progressApi', () => ({
  fetchProgress: vi.fn(),
}));

vi.mock('../../services/processProgressApi', () => ({
  fetchProcessProgress: vi.fn().mockResolvedValue([]),
  openProcessProgress: vi.fn(),
  submitProcessProgress: vi.fn(),
}));

describe('ProgressWorkspace', () => {
  beforeEach(() => {
    vi.mocked(fetchProgress).mockReset();
  });

  it('没有单据时显示空表，不回退样例行', async () => {
    vi.mocked(fetchProgress).mockResolvedValueOnce([]);
    const wrapper = mount(ProgressWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('暂无在制单据');
    expect(wrapper.text()).not.toContain('恒信珠宝');
  });

  it('接口失败时显示原因', async () => {
    vi.mocked(fetchProgress).mockRejectedValueOnce(new Error('后端接口不可用'));
    const wrapper = mount(ProgressWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('后端接口不可用');
  });

  it('按客户筛选后只保留接口返回的行，点状态打开对应页面', async () => {
    vi.mocked(fetchProgress).mockResolvedValueOnce([
      {
        customerId: 1001,
        customerName: '恒信珠宝',
        motherOrderCode: 'MD-1',
        workOrderCode: 'ZD-1',
        currentProcess: '镶嵌',
        dueDate: '2099-01-01',
        overdue: false,
        qualityOutcome: '未质检',
        status: '在制',
        openTab: 'production',
      },
    ]);
    const opened: string[] = [];
    const trace = { value: '原来的关键字' };
    const target = ref<TraceTarget | null>(null);
    const wrapper = mount(ProgressWorkspace, {
      global: {
        provide: {
          'zhubao-open-tab': (tab: string) => opened.push(tab),
          'zhubao-trace-query': trace,
          'zhubao-trace-target': target,
        },
      },
    });
    await flushPromises();
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('ZD-1');
    expect(wrapper.text()).not.toContain('雅辰');
    await wrapper.get('button.table-action').trigger('click');
    expect(opened).toEqual(['production']);
    expect(target.value).toMatchObject({ objectType: 'WORK_ORDER', code: 'ZD-1', intent: 'PRODUCTION' });
    expect(trace.value).toBe('原来的关键字');
  });

  it('不拆母单打开母单目标，不把子单留空后改用列表第一行', async () => {
    vi.mocked(fetchProgress).mockResolvedValueOnce([
      {
        customerId: 1001,
        customerName: '恒信珠宝',
        motherOrderCode: 'MD-KEEP',
        workOrderCode: 'MD-KEEP',
        currentProcess: '来料',
        dueDate: '2099-01-01',
        overdue: false,
        qualityOutcome: '未质检',
        status: '待开工',
        openTab: 'inbound',
      },
    ]);
    const target = ref<TraceTarget | null>(null);
    const wrapper = mount(ProgressWorkspace, {
      global: {
        provide: {
          'zhubao-open-tab': () => {},
          'zhubao-trace-target': target,
        },
      },
    });
    await flushPromises();
    await wrapper.get('button.table-action').trigger('click');
    expect(target.value).toMatchObject({ objectType: 'MOTHER_ORDER', code: 'MD-KEEP', intent: 'INTAKE' });
  });

  it('进入详情再返回时保留筛选，无权限不显示成空成功', async () => {
    vi.mocked(fetchProgress).mockResolvedValueOnce([
      {
        customerId: 1001,
        customerName: '恒信珠宝',
        motherOrderCode: 'MD-1',
        workOrderCode: 'ZD-1',
        currentProcess: '镶嵌',
        dueDate: '2099-01-01',
        overdue: false,
        qualityOutcome: '未质检',
        status: '在制',
        openTab: 'production',
      },
    ]);
    const wrapper = mount(ProgressWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="进度客户"]').setValue('恒信');
    await wrapper.get('.desk-card').trigger('click');
    expect(wrapper.classes()).toContain('is-detail');
    expect(wrapper.find('.active-object-card > strong').text()).toBe('MD-1');
    await wrapper.get('.detail-back').trigger('click');
    expect(wrapper.classes()).not.toContain('is-detail');
    expect((wrapper.get('[aria-label="进度客户"]').element as HTMLInputElement).value).toBe('恒信');

    vi.mocked(fetchProgress).mockRejectedValueOnce(new ApiRequestError('无权查看进度', 403, 40301));
    const denied = mount(ProgressWorkspace);
    await flushPromises();
    expect(denied.text()).toContain('无权限');
    expect(denied.text()).toContain('无权查看进度');
    expect(denied.find('.active-object-card').exists()).toBe(false);
  });
});
