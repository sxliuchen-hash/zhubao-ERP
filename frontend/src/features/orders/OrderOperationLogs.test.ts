import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import OrderDetail from './OrderDetail.vue';
import OrderOperationLogs from './OrderOperationLogs.vue';
import { fetchOrderOperationLogs } from '../../services/orderMaintenanceApi';
import { buildScanContext } from '../scan/scanContextFixture';

vi.mock('../../services/orderMaintenanceApi', () => ({ fetchOrderOperationLogs: vi.fn() }));
beforeEach(() => { vi.mocked(fetchOrderOperationLogs).mockReset().mockResolvedValue([
  { targetCode: 'MD-1', action: '创建母单', operator: '收货员', createdAt: '2026-10-27', detail: '戒指' },
]); });
describe('管理员操作日志', () => {
  it('管理员点击流转后才读取日志，普通人员不请求且不展示', async () => {
    const auth = ref({ role: 'ADMIN' });
    const wrapper = mount(OrderDetail, { props: { context: buildScanContext({ objectType: 'MOTHER_ORDER', code: 'MD-1', motherOrderCode: 'MD-1' }) },
      global: { provide: { 'zhubao-auth-session': auth } } });
    expect(fetchOrderOperationLogs).not.toHaveBeenCalled();
    await wrapper.get('.order-detail-tabs button:nth-child(2)').trigger('click'); await flushPromises();
    expect(fetchOrderOperationLogs).toHaveBeenCalledWith('MD-1');
    expect(wrapper.get('[aria-label="订单操作日志"]').text()).toContain('收货员');
    auth.value = { role: 'BUSINESS_MANAGER' }; await flushPromises();
    expect(wrapper.find('[aria-label="订单操作日志"]').exists()).toBe(false);
    expect(fetchOrderOperationLogs).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });
  it('换单后迟到日志不能泄露到新单', async () => {
    let resolve!: (rows: Awaited<ReturnType<typeof fetchOrderOperationLogs>>) => void;
    vi.mocked(fetchOrderOperationLogs).mockReturnValueOnce(new Promise(done => { resolve = done; }));
    const wrapper = mount(OrderOperationLogs, { props: { code: 'MD-OLD' }, global: { provide: { 'zhubao-auth-session': ref({ role: 'ADMIN' }) } } });
    await wrapper.setProps({ code: 'MD-NEW' }); await flushPromises();
    resolve([{ targetCode: 'MD-OLD', action: '修改客户', operator: '旧账号', createdAt: '', detail: '旧客户私有内容' }]);
    await flushPromises();
    expect(wrapper.text()).not.toContain('旧客户私有内容');
    expect(fetchOrderOperationLogs).toHaveBeenLastCalledWith('MD-NEW');
    wrapper.unmount();
  });
});
