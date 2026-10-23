import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import ChainReview from './ChainReview.vue';
import { queryScanContext } from '../../services/scanContextApi';
import { buildScanContext } from './scanContextFixture';
import type { TraceTarget } from './traceTarget';
import type { ScanContextResponse } from '../../services/scanContextApi';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn() };
});

function chainContext(input: {
  code?: string;
  customerName?: string;
  requiresSelection?: boolean;
  handoffs?: ScanContextResponse['progress']['handoffs'];
  shipments?: ScanContextResponse['progress']['shipments'];
}): ScanContextResponse {
  const base = buildScanContext({
    objectType: input.requiresSelection ? null : 'WORK_ORDER',
    code: input.requiresSelection ? null : (input.code ?? 'ZD-1'),
    customerName: input.customerName ?? '恒信珠宝',
    requiresSelection: input.requiresSelection,
    relatedTargets: input.requiresSelection
      ? [
        { objectType: 'WORK_ORDER', code: 'ZD-1', motherOrderCode: 'MD-1', requirement: '甲', quantity: 1, quantityUnit: '件' },
        { objectType: 'WORK_ORDER', code: 'ZD-2', motherOrderCode: 'MD-1', requirement: '乙', quantity: 1, quantityUnit: '件' },
      ]
      : [],
  });
  return {
    ...base,
    progress: {
      ...base.progress,
      handoffs: input.handoffs ?? [],
      shipments: input.shipments ?? [],
      quality: null,
    },
  };
}

describe('追溯回看', () => {
  beforeEach(() => {
    vi.mocked(queryScanContext).mockReset();
  });

  it('正式动作使用精确目标容器，成功通知只刷新本单的只读详情', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(chainContext({ code: 'WO-REAL' }));
    const refresh = ref(0);
    const target = ref<TraceTarget | null>({ objectType: 'WORK_ORDER', code: 'WO-REAL', intent: 'LOOKUP', token: 1 });
    const openProduction = vi.fn();
    const openShipping = vi.fn();
    const openTab = vi.fn();
    const wrapper = mount(ChainReview, { global: { provide: { 'zhubao-trace-target': target, 'zhubao-order-refresh': refresh, 'zhubao-open-production': openProduction, 'zhubao-open-shipping': openShipping, 'zhubao-open-tab': openTab } } });
    await flushPromises();
    await wrapper.get('[aria-label="去报工"]').trigger('click');
    expect(openProduction).toHaveBeenCalledWith({ objectType: 'WORK_ORDER', code: 'WO-REAL' }, 'station');
    await wrapper.get('[aria-label="去发货"]').trigger('click');
    expect(openShipping).toHaveBeenCalledWith({ objectType: 'WORK_ORDER', code: 'WO-REAL' });
    expect(openTab).not.toHaveBeenCalled();
    refresh.value += 1;
    await flushPromises();
    expect(queryScanContext).toHaveBeenLastCalledWith({ content: 'WO-REAL', objectType: 'WORK_ORDER', intent: 'LOOKUP' });
    expect(openProduction).toHaveBeenCalledOnce();
    expect(target.value?.intent).toBe('LOOKUP');
    wrapper.unmount();
  });

  it('按打开的单号回看链路，空事实不编造，跳转使用规范身份', async () => {
    const target = ref<TraceTarget | null>({ objectType: null, code: 'ZD-1', intent: 'LOOKUP', token: 1 });
    const opened: string[] = [];
    vi.mocked(queryScanContext).mockResolvedValue(chainContext({
      handoffs: [{ process: '雕刻', operator: '林海', ledgerCode: 'STK-1', createdAt: '2026-10-12' }],
      shipments: [{
        outboundCode: 'OUT-1', motherOrderCode: 'MD-1', workOrderCode: 'ZD-1',
        logisticsNo: 'YT1', status: 'SHIPPED', ledgerCode: 'STK-9',
      }],
    }));
    const wrapper = mount(ChainReview, {
      global: {
        provide: {
          'zhubao-trace-target': target,
          'zhubao-open-tab': (tab: string) => opened.push(tab),
        },
      },
    });
    await flushPromises();

    expect(queryScanContext).toHaveBeenCalledWith({ content: 'ZD-1', intent: 'LOOKUP' });
    await wrapper.get('.order-detail-tabs button:nth-child(2)').trigger('click');
    expect(wrapper.text()).toContain('雕刻 · STK-1');
    expect(wrapper.text()).toContain('OUT-1 · ZD-1');
    expect(wrapper.text()).not.toContain('暂无工序交付');
    await wrapper.get('[aria-label="去报工"]').trigger('click');
    expect(target.value).toMatchObject({ objectType: 'WORK_ORDER', code: 'ZD-1', intent: 'PRODUCTION' });
    expect(opened).toEqual(['production']);
  });

  it('已拆母单必须先选择，迟到的旧查询不能覆盖新单', async () => {
    let finishFirst: (value: ScanContextResponse) => void = () => {};
    vi.mocked(queryScanContext).mockImplementationOnce(() => new Promise((resolve) => {
      finishFirst = resolve;
    }));
    const target = ref<TraceTarget | null>({ objectType: null, code: 'MD-A', intent: 'LOOKUP', token: 1 });
    const wrapper = mount(ChainReview, {
      global: { provide: { 'zhubao-trace-target': target, 'zhubao-open-tab': () => {} } },
    });
    await flushPromises();
    vi.mocked(queryScanContext).mockResolvedValueOnce(chainContext({
      code: 'ZD-B',
      customerName: '雅辰饰品',
      requiresSelection: true,
    }));
    target.value = { objectType: null, code: 'MD-B', intent: 'LOOKUP', token: 2 };
    finishFirst(chainContext({ code: 'ZD-A', customerName: '恒信甲' }));
    await flushPromises();

    expect(wrapper.text()).toContain('雅辰饰品');
    expect(wrapper.text()).toContain('请先选择具体单据');
    expect(wrapper.text()).not.toContain('恒信甲');
    expect(wrapper.find('[aria-label="去报工"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('暂无工序交付、质检结论或发货事实');
  });
});
