import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PrepStockPanel from './PrepStockPanel.vue';
import { ApiRequestError, apiGet, apiPost } from '../../services/apiClient';

vi.mock('../../services/masterDataApi', () => ({
  fetchCustomers: vi.fn().mockResolvedValue([]),
  fetchMaterials: vi.fn().mockResolvedValue([]),
}));

vi.mock('../../services/apiClient', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  ApiRequestError: class ApiRequestError extends Error {
    status: number;
    code: number;
    constructor(message: string, status = 500, code = 50000) {
      super(message);
      this.status = status;
      this.code = code;
    }
  },
}));

describe('PrepStockPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(apiGet).mockResolvedValue([]);
  });

  async function openReceipt(wrapper: ReturnType<typeof mount>) {
    await wrapper.get('button.primary-button').trigger('click');
    await wrapper.get('[aria-label="公司物料"]').setValue('AU9999');
    await wrapper.get('[aria-label="公司仓库"]').setValue('WH');
    await wrapper.get('[aria-label="公司单位"]').setValue('克');
    await wrapper.get('[aria-label="公司数量"]').setValue('10.500');
    await wrapper.get('[aria-label="公司重量"]').setValue('10.500');
  }

  it('入库失败重试复用请求编号，数量变化才换号', async () => {
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('响应丢失')).mockResolvedValue('GS-1');
    const wrapper = mount(PrepStockPanel, { props: { scope: 'company' } });
    await flushPromises();
    await openReceipt(wrapper);
    const receive = () => wrapper.findAll('button').find((button) => button.text() === '确认入库')!;
    await receive().trigger('click');
    await flushPromises();
    await receive().trigger('click');
    await flushPromises();
    const first = (vi.mocked(apiPost).mock.calls[0][1] as { requestId: string }).requestId;
    const second = (vi.mocked(apiPost).mock.calls[1][1] as { requestId: string }).requestId;
    expect(second).toBe(first);
    expect(vi.mocked(apiPost).mock.calls[0][0]).toBe('/api/inventory/material-stock/company-receipts');
    expect(String((vi.mocked(apiPost).mock.calls[0][1] as { lines: Array<{ quantity: string }> }).lines[0].quantity)).toBe('10.500');

    await wrapper.findAll('button').find((button) => button.text() === '入库')!.trigger('click');
    await wrapper.get('[aria-label="公司数量"]').setValue('4');
    vi.mocked(apiPost).mockResolvedValueOnce('GS-2');
    await receive().trigger('click');
    await flushPromises();
    const third = (vi.mocked(apiPost).mock.calls[2][1] as { requestId: string }).requestId;
    expect(third).not.toBe(first);
    wrapper.unmount();
  });

  it('公司料查询失败不会被当成没有库存', async () => {
    vi.mocked(apiGet).mockRejectedValue(new Error('库存服务暂不可用'));
    const wrapper = mount(PrepStockPanel, { props: { scope: 'company' } });
    await flushPromises();
    expect(wrapper.text()).toContain('库存服务暂不可用');
    expect(wrapper.text()).not.toContain('没有符合条件的库存');
    wrapper.unmount();
  });

  it('公司料总览按单位显示小数，选中批次能看到领用流水', async () => {
    const batch = {
      scope: 'company', ownerLabel: '公司自有', ownerType: 'COMPANY', customerId: null,
      materialCode: 'AU9999', batchCode: 'B-1', warehouseCode: 'WH', motherOrderCode: '', workOrderCode: '',
      status: '可用', quantityUnit: '克', bookQuantity: '10.5', bookWeight: '10.5',
      frozenQuantity: '0', frozenWeight: '0', availableQuantity: '7.375', availableWeight: '7.375',
      issuedQuantity: '3.125', issuedWeight: '3.125', weightText: '10.5', pending: false,
      sourceMissing: false, documentCode: 'GS-1', externalRef: '', updatedAt: '2026-10-22', lineNo: 1,
    };
    vi.mocked(apiGet).mockImplementation(async (url: string) => {
      if (url.includes('/overview')) return { scope: 'company', batchCount: 1, quantities: [{ unit: '克', quantity: '10.5' }], knownWeight: '10.5', frozenCount: 0, pendingCount: 0 };
      if (url.includes('/batches')) return url.includes('materialCode=STONE') ? [] : [batch];
      if (url.includes('/movements')) return [{ time: '2026-10-22T10:00:00Z', kindLabel: '公司料领用', documentCode: 'LY-1', source: 'B-1 待加工', target: 'B-1 在制', quantity: '-4.25', weight: '4.25', beforeQuantity: '10.5', afterQuantity: '6.25', operatorName: '库管员' }];
      if (url.includes('/documents/')) return { documentCode: 'GS-1', sourceNote: '采购到货', handoverPerson: '仓管', purpose: '补货', recipient: '张三', targetOrderCode: '', lines: [] };
      return [];
    });
    const wrapper = mount(PrepStockPanel, { props: { scope: 'company' } });
    await flushPromises();
    expect(wrapper.text()).toContain('领用只表示转入在制');
    expect(wrapper.get('[aria-label="库存总览"]').text()).toContain('10.5 克');
    expect(wrapper.text()).toContain('已领用 3.125');
    await wrapper.get('button.stock-card-button').trigger('click');
    await flushPromises();
    expect(wrapper.get('[aria-label="批次流水"]').text()).toContain('公司料领用 LY-1');
    expect(wrapper.text()).toContain('前 10.5 · 后 6.25');
    expect(wrapper.text()).toContain('用途 补货');
    expect(wrapper.text()).toContain('领用人 张三');

    await wrapper.get('[aria-label="筛选物料"]').setValue('STONE');
    await wrapper.findAll('button').find((button) => button.text() === '应用筛选')!.trigger('click');
    await flushPromises();
    expect(vi.mocked(apiGet).mock.calls.some((call) => String(call[0]).includes('materialCode=STONE'))).toBe(true);
    expect(wrapper.text()).toContain('没有符合条件的库存');
    wrapper.unmount();
  });

  it('无权限和迟到的旧响应都不会显示成空库存', async () => {
    const pending: Array<(value: unknown) => void> = [];
    let phase = 'old';
    vi.mocked(apiGet).mockImplementation((url: string) => {
      if (phase === 'old' && (String(url).includes('/overview') || String(url).includes('/batches'))) {
        return new Promise((resolve) => pending.push(resolve));
      }
      if (String(url).includes('/overview')) return Promise.resolve({ scope: 'company', batchCount: 0, quantities: [], knownWeight: '', frozenCount: 0, pendingCount: 0 });
      if (String(url).includes('/batches')) return Promise.resolve([]);
      return Promise.resolve([]);
    });
    const wrapper = mount(PrepStockPanel, { props: { scope: 'company' } });
    await flushPromises();
    phase = 'new';
    await wrapper.findAll('button').find((button) => button.text() === '应用筛选')!.trigger('click');
    await flushPromises();
    pending.forEach((resolve) => resolve({ scope: 'company', batchCount: 1, quantities: [{ unit: '克', quantity: '99' }], batchCode: '过期批次' }));
    await flushPromises();
    expect(wrapper.text()).toContain('没有符合条件的库存');
    expect(wrapper.text()).not.toContain('过期批次');
    expect(wrapper.text()).not.toContain('99 克');
    wrapper.unmount();

    vi.mocked(apiGet).mockRejectedValue(new ApiRequestError('公司自有物料仅仓库、财务、管理员或总经理可以查看', 403, 40301));
    const denied = mount(PrepStockPanel, { props: { scope: 'company' } });
    await flushPromises();
    expect(denied.text()).toContain('无权限查看这部分库存');
    expect(denied.text()).not.toContain('没有符合条件的库存');
    denied.unmount();
  });

  it('提交进行中再次点击不会发出第二次请求', async () => {
    let release: (value: string) => void = () => {};
    vi.mocked(apiPost).mockImplementation(() => new Promise((resolve) => { release = resolve; }));
    const wrapper = mount(PrepStockPanel, { props: { scope: 'company' } });
    await flushPromises();
    await openReceipt(wrapper);
    const receive = wrapper.findAll('button').find((button) => button.text() === '确认入库')!;
    const pending = receive.trigger('click');
    await Promise.resolve();
    await receive.trigger('click');
    expect(apiPost).toHaveBeenCalledTimes(1);
    release('GS-1');
    await pending;
    await flushPromises();
    wrapper.unmount();
  });
});
