import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MotherAdminPanel from './MotherAdminPanel.vue';
import { correctMotherBasicInfo, recycleMaintainedOrder, searchMaintainedOrders, type MaintainedOrder } from '../../services/orderMaintenanceApi';
import { fetchCustomers, type CustomerProfile } from '../../services/masterDataApi';

vi.mock('../../services/orderMaintenanceApi', () => ({ correctMotherBasicInfo: vi.fn(), recycleMaintainedOrder: vi.fn(), searchMaintainedOrders: vi.fn() }));
vi.mock('../../services/masterDataApi', () => ({ fetchCustomers: vi.fn() }));
const mother = (): MaintainedOrder => ({ targetType: 'MOTHER', code: 'MD-1', motherOrderCode: 'MD-1',
  customerId: 1001, customerName: '原客户', goodsName: '戒指', remark: '原备注', materialCode: 'AU', quantity: '2', weight: '3',
  requirement: '原要求', dueDate: '', processes: '', copies: null, stationId: '', queueId: '', recycled: false, recycleReason: '',
  adjustments: [], revision: 'revision-1', postedFactsLocked: true });
beforeEach(() => {
  vi.mocked(searchMaintainedOrders).mockReset().mockResolvedValue([mother()]);
  vi.mocked(fetchCustomers).mockReset().mockResolvedValue([{ customerId: 1002, customerCode: 'C2', name: '新客户', enabled: true } as CustomerProfile]);
  vi.mocked(correctMotherBasicInfo).mockReset().mockResolvedValue({ ...mother(), customerId: 1002, customerName: '新客户', revision: 'revision-2' });
  vi.mocked(recycleMaintainedOrder).mockReset().mockResolvedValue();
});

describe('订单中心管理员母单维护', () => {
  it('入账母单可选稳定客户并保留备注和版本，未知结果重试复用原请求', async () => {
    vi.mocked(correctMotherBasicInfo).mockRejectedValueOnce(new Error('响应丢失'));
    const wrapper = mount(MotherAdminPanel, { props: { motherCode: 'MD-1' } });
    await flushPromises();
    expect(wrapper.get('[aria-label="母单备注"]').element).toHaveProperty('value', '原备注');
    await wrapper.get('[aria-label="母单客户"]').setValue('1002');
    await wrapper.get('[aria-label="母单操作原因"]').setValue('客户录错');
    await wrapper.get('form').trigger('submit'); await flushPromises();
    expect(wrapper.text()).toContain('响应丢失');
    await wrapper.get('form').trigger('submit'); await flushPromises();
    const calls = vi.mocked(correctMotherBasicInfo).mock.calls;
    expect(calls[0]).toEqual(calls[1]);
    expect(calls[0]).toEqual(['MD-1', expect.objectContaining({ customerId: 1002, remark: '原备注', expectedRevision: 'revision-1', reason: '客户录错' })]);
    expect(wrapper.emitted('saved')).toHaveLength(1);
    wrapper.unmount();
  });

  it('删除必须有原因并确认目标，成功后通知列表刷新', async () => {
    const wrapper = mount(MotherAdminPanel, { props: { motherCode: 'MD-1' } });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '删除母单')!.trigger('click');
    expect(recycleMaintainedOrder).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('确认删除母单 MD-1 及其子单');
    await wrapper.get('[aria-label="母单操作原因"]').setValue('测试单重复');
    await wrapper.findAll('button').find(button => button.text() === '确认删除至回收站')!.trigger('click');
    await flushPromises();
    expect(recycleMaintainedOrder).toHaveBeenCalledWith('MOTHER', 'MD-1', '测试单重复', expect.any(String));
    expect(wrapper.emitted('deleted')).toHaveLength(1);
    wrapper.unmount();
  });
});
