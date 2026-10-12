import { defineComponent } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { queryScanContext, type ScanContextQuery } from '../../services/scanContextApi';
import { buildScanContext } from './scanContextFixture';
import { useScanContext } from './useScanContext';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn() };
});

const labels = [
  { content: 'JG|mother|MD-1', objectType: 'MOTHER_ORDER' as const, code: 'MD-1' },
  { content: 'JG|work_order|ZD-1', objectType: 'WORK_ORDER' as const, code: 'ZD-1' },
  { content: 'JG|outbound|OUT-1', objectType: 'OUTBOUND_ORDER' as const, code: 'OUT-1' },
  { content: 'JG|rework|FG-1', objectType: 'REWORK' as const, code: 'FG-1' },
  { content: 'JG|EXP|SF1048329001', objectType: 'EXPRESS_NO' as const, code: 'SF1048329001' },
];

function mountContext() {
  let api: ReturnType<typeof useScanContext> | null = null;
  const wrapper = mount(defineComponent({
    setup() {
      api = useScanContext();
      return () => null;
    },
  }));
  return { wrapper, get api() { return api!; } };
}

beforeEach(() => {
  vi.mocked(queryScanContext).mockReset();
});

describe('统一扫码上下文', () => {
  it('五类标签都把原始内容交给同一次查询，不在本地补对象类型', async () => {
    const { api } = mountContext();
    for (const label of labels) {
      vi.mocked(queryScanContext).mockResolvedValueOnce(buildScanContext({
        objectType: label.objectType,
        code: label.code,
        customerName: label.code,
      }));
      const result = await api.lookup({ content: label.content, intent: 'LOOKUP' });
      expect(queryScanContext).toHaveBeenCalledWith({ content: label.content, intent: 'LOOKUP' });
      expect(result?.target.objectType).toBe(label.objectType);
      expect(result?.target.code).toBe(label.code);
      expect(api.identity.value?.code).toBe(label.code);
      expect(result?.progress.progressRecords).toEqual([]);
    }
  });

  it('手输业务编码只在用户选定类型时带上 objectType', async () => {
    const { api } = mountContext();
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'MOTHER_ORDER',
      code: 'SF1048329001',
    }));

    await api.lookup({ content: 'SF1048329001', intent: 'INTAKE' });
    expect(queryScanContext).toHaveBeenLastCalledWith({ content: 'SF1048329001', intent: 'INTAKE' });
    const body = vi.mocked(queryScanContext).mock.calls.at(-1)?.[0] as ScanContextQuery;
    expect(body.objectType).toBeUndefined();

    await api.lookup({ content: 'SF1048329001', objectType: 'MOTHER_ORDER', intent: 'INTAKE' });
    expect(queryScanContext).toHaveBeenLastCalledWith({
      content: 'SF1048329001',
      objectType: 'MOTHER_ORDER',
      intent: 'INTAKE',
    });
  });

  it('扫到 B 后立即丢掉 A，A 的迟到响应不能覆盖 B', async () => {
    const { api } = mountContext();
    let resolveA: (value: ReturnType<typeof buildScanContext>) => void = () => {};
    let resolveB: (value: ReturnType<typeof buildScanContext>) => void = () => {};
    vi.mocked(queryScanContext).mockImplementation((input) => new Promise((resolve) => {
      if (input.content.endsWith('A')) resolveA = resolve;
      else resolveB = resolve;
    }));

    const first = api.lookup({ content: 'JG|work_order|A', intent: 'PRODUCTION' });
    expect(api.context.value).toBeNull();
    expect(api.identity.value).toBeNull();
    const second = api.lookup({ content: 'JG|work_order|B', intent: 'PRODUCTION' });
    expect(api.context.value).toBeNull();

    resolveA(buildScanContext({ objectType: 'WORK_ORDER', code: 'A', customerName: '客户A' }));
    await first;
    expect(api.context.value?.target.code ?? null).not.toBe('A');

    resolveB(buildScanContext({ objectType: 'WORK_ORDER', code: 'B', customerName: '客户B' }));
    await second;
    await flushPromises();
    expect(api.context.value?.target.code).toBe('B');
    expect(api.identity.value?.code).toBe('B');
  });

  it('目标变化后旧结果失效，查询失败也不保留上一张单', async () => {
    const { api } = mountContext();
    vi.mocked(queryScanContext).mockResolvedValueOnce(buildScanContext({
      objectType: 'WORK_ORDER',
      code: 'ZD-1',
      customerName: '应被清掉',
    }));
    await api.lookup({ content: 'JG|work_order|ZD-1', intent: 'QUALITY' });
    expect(api.identity.value?.code).toBe('ZD-1');

    api.invalidate();
    expect(api.context.value).toBeNull();
    expect(api.identity.value).toBeNull();

    vi.mocked(queryScanContext).mockRejectedValueOnce(new Error('未找到对应单据'));
    const missed = await api.lookup({ content: 'NO-SUCH', intent: 'QUALITY' });
    expect(missed).toBeNull();
    expect(api.context.value).toBeNull();
    expect(api.identity.value).toBeNull();
    expect(api.error.value).toBe('未找到对应单据');
  });

  it('已拆母单要求选择时没有可提交身份', async () => {
    const { api } = mountContext();
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'MOTHER_ORDER',
      code: 'MD-1',
      requiresSelection: true,
      relatedTargets: [
        { objectType: 'WORK_ORDER', code: 'ZD-1', motherOrderCode: 'MD-1', requirement: '第一张', quantity: 1, quantityUnit: '件' },
        { objectType: 'WORK_ORDER', code: 'ZD-2', motherOrderCode: 'MD-1', requirement: '第二张', quantity: 1, quantityUnit: '件' },
      ],
    }));
    const result = await api.lookup({ content: 'JG|mother|MD-1', intent: 'PRODUCTION' });
    expect(result?.requiresSelection).toBe(true);
    expect(api.identity.value).toBeNull();
  });

  it('离开页面后迟到响应不再写回', async () => {
    const { wrapper, api } = mountContext();
    let resolveLate: (value: ReturnType<typeof buildScanContext>) => void = () => {};
    vi.mocked(queryScanContext).mockImplementation(() => new Promise((resolve) => {
      resolveLate = resolve;
    }));
    const pending = api.lookup({ content: 'JG|mother|MD-LATE', intent: 'LOOKUP' });
    wrapper.unmount();
    resolveLate(buildScanContext({ objectType: 'MOTHER_ORDER', code: 'MD-LATE', customerName: '迟到客户' }));
    await pending;
    await flushPromises();
    expect(api.context.value).toBeNull();
    expect(api.identity.value).toBeNull();
  });
});
