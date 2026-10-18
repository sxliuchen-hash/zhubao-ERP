import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderMaintenancePanel from './OrderMaintenancePanel.vue';
import { fetchRecycleBin, recycleMaintainedOrder, searchMaintainedOrders } from '../../services/orderMaintenanceApi';

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
});
