import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderMaintenancePanel from './OrderMaintenancePanel.vue';
import { correctMaintainedOrder, fetchRecycleBin, listMaintainedMothers, recycleMaintainedOrder, searchMaintainedOrders } from '../../services/orderMaintenanceApi';

vi.mock('../../services/orderMaintenanceApi', () => ({
  searchMaintainedOrders: vi.fn(),
  listMaintainedMothers: vi.fn(),
  fetchRecycleBin: vi.fn(),
  correctMaintainedOrder: vi.fn(),
  recycleMaintainedOrder: vi.fn(),
  restoreMaintainedOrder: vi.fn(),
}));

describe('单据维护', () => {
  beforeEach(() => {
    vi.mocked(listMaintainedMothers).mockResolvedValue({ items: [], total: 0, truncated: false });
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

  it('打开页面列出母单，并可按客户筛选后打开', async () => {
    vi.mocked(listMaintainedMothers).mockResolvedValue({
      items: [{
        code: 'MD-9', customerName: '马洋', operator: '收货员', orderDate: '2026-10-14',
        goodsName: '龙纹桃核', materialCode: 'MAT-MOBILE', quantity: '30', weight: '100',
      }],
      total: 1,
      truncated: false,
    });
    const wrapper = mount(OrderMaintenancePanel);
    await flushPromises();
    expect(wrapper.text()).toContain('MD-9');
    expect(wrapper.text()).toContain('马洋');
    expect(wrapper.text()).toContain('共 1 张未回收母单');
    await wrapper.get('[aria-label="客户筛选"]').setValue('马洋');
    await wrapper.get('[aria-label="开始日期"]').setValue('2026-10-01');
    await wrapper.get('[aria-label="筛选母单"]').trigger('click');
    await flushPromises();
    expect(listMaintainedMothers).toHaveBeenLastCalledWith({
      from: '2026-10-01', to: '', customer: '马洋', operator: '',
    });
    await wrapper.get('[aria-label="打开 MD-9"]').trigger('click');
    await flushPromises();
    expect(searchMaintainedOrders).toHaveBeenCalledWith('MD-9');
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
    expect(recycleMaintainedOrder).toHaveBeenCalledWith('MOTHER', 'MD-1', '录错', expect.stringMatching(/^maint-/));
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
    expect(wrapper.get('[aria-label="MD-1 数量"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[aria-label="MD-1 重量"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('[aria-label="MD-1 要求"]').setValue('改正要求');
    await save();
    await flushPromises();
    expect(vi.mocked(correctMaintainedOrder).mock.calls[2][2]).not.toBe(first);
  });

  it('打印目标只能在维护页修改份数', async () => {
    vi.mocked(searchMaintainedOrders).mockResolvedValueOnce([{
      targetType: 'PRINT_TARGET', code: 'J-1', motherOrderCode: 'MD-1', customerName: '',
      materialCode: '', goodsName: '', requirement: '', dueDate: '', quantity: '', weight: '',
      processes: '', copies: 1, stationId: 'A', queueId: 'Q1', recycled: false,
      recycleReason: '', adjustments: [], revision: 'print-revision',
    }]);
    const wrapper = mount(OrderMaintenancePanel);
    await flushPromises();
    await wrapper.get('[aria-label="维护单号"]').setValue('J-1');
    await wrapper.findAll('button').find((button) => button.text() === '查找')!.trigger('click');
    await flushPromises();
    expect(wrapper.get('[aria-label="J-1 份数"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[aria-label="J-1 工位"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[aria-label="J-1 队列"]').attributes('disabled')).toBeDefined();
  });
});
