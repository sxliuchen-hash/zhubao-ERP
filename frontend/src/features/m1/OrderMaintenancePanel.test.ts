import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderMaintenancePanel from './OrderMaintenancePanel.vue';
import { correctMaintainedOrder, fetchRecycleBin, recycleMaintainedOrder, searchMaintainedOrders } from '../../services/orderMaintenanceApi';

vi.mock('../../services/orderMaintenanceApi', () => ({
  searchMaintainedOrders: vi.fn(),
  fetchRecycleBin: vi.fn(),
  correctMaintainedOrder: vi.fn(),
  recycleMaintainedOrder: vi.fn(),
  restoreMaintainedOrder: vi.fn(),
}));

describe('单据维护', () => {
  beforeEach(() => {
    vi.mocked(fetchRecycleBin).mockResolvedValue([]);
    vi.mocked(searchMaintainedOrders).mockResolvedValue([{
      targetType: 'MOTHER',
      code: 'MD-1',
      motherOrderCode: 'MD-1',
      customerName: '张三',
      materialCode: 'AU',
      goodsName: '戒指',
      requirement: '抛光',
      dueDate: '2026-11-01',
      quantity: '2',
      weight: '10',
      processes: '',
      copies: null,
      stationId: '',
      queueId: '',
      recycled: false,
      recycleReason: '',
      adjustments: [],
      revision: 'revision-1',
      postedFactsLocked: true,
    }]);
    vi.mocked(recycleMaintainedOrder).mockResolvedValue();
  });

  it('按单号查找后可以把母单放入回收站', async () => {
    const wrapper = mount(OrderMaintenancePanel);
    await flushPromises();
    await wrapper.get('[aria-label="维护单号"]').setValue('MD-1');
    await wrapper.get('[aria-label="维护原因"]').setValue('录错');
    const find = wrapper.findAll('button').find((button) => button.text() === '查找');
    await find!.trigger('click');
    await flushPromises();
    expect(searchMaintainedOrders).toHaveBeenCalledWith('MD-1');
    const recycle = wrapper.findAll('button').find((button) => button.text() === '放入回收站');
    await recycle!.trigger('click');
    await flushPromises();
    expect(recycleMaintainedOrder).toHaveBeenCalledWith('MOTHER', 'MD-1', '录错');
  });

  it('相同内容重试沿用请求编号，修改内容后换编号', async () => {
    vi.mocked(correctMaintainedOrder).mockRejectedValueOnce(new Error('网络中断'))
      .mockResolvedValue({} as Awaited<ReturnType<typeof correctMaintainedOrder>>);
    const wrapper = mount(OrderMaintenancePanel);
    await flushPromises();
    await wrapper.get('[aria-label="维护单号"]').setValue('MD-1');
    await wrapper.get('[aria-label="维护原因"]').setValue('核对后改正');
    await wrapper.findAll('button').find((button) => button.text() === '查找')!.trigger('click');
    await flushPromises();
    const save = () => wrapper.findAll('button').find((button) => button.text() === '保存修改')!.trigger('click');
    await save();
    await flushPromises();
    await save();
    await flushPromises();
    const first = vi.mocked(correctMaintainedOrder).mock.calls[0][2];
    const second = vi.mocked(correctMaintainedOrder).mock.calls[1][2];
    expect(first).toBe(second);
    expect(wrapper.get('[aria-label="MD-1 货品"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[aria-label="MD-1 要求"]').setValue('改正要求');
    await save();
    await flushPromises();
    expect(vi.mocked(correctMaintainedOrder).mock.calls[2][2]).not.toBe(first);
  });
});
