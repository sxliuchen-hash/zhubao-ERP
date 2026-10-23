import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import OrderChangePanel from './OrderChangePanel.vue';
import { apiGet, apiPost } from '../../services/apiClient';

vi.mock('../../services/apiClient', () => ({ apiGet: vi.fn(), apiPost: vi.fn() }));
vi.mock('../evidence/EvidenceCapture.vue', () => ({ default: { template: '<div>原单证据</div>' } }));
const row = { id: 'CHANGE-1', motherOrderCode: 'MO-1', status: 'PENDING_FINANCE', expectedVersion: 2, versionNo: 3, oldRequirement: '原要求', newRequirement: '新要求', customerUnitPrice: 1899, workerFee: 800, customerDelta: 200 };
beforeEach(() => { vi.mocked(apiGet).mockReset().mockResolvedValue([row]); vi.mocked(apiPost).mockReset(); });

describe('独立改单面板', () => {
  it('普通岗位可核对要求差异，但没有财务确认字段或未确认就生效的入口', async () => {
    const wrapper = mount(OrderChangePanel, { props: { target: { orderCode: 'WO-1', motherOrderCode: 'MO-1' } }, global: { provide: { 'zhubao-auth-session': ref({ role: 'OPERATOR', effectivePermissions: [] }) } } });
    await wrapper.findAll('button').find(button => button.text() === '刷新改单')!.trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().includes('CHANGE-1'))!.trigger('click');
    expect(wrapper.text()).toContain('原要求');
    expect(wrapper.text()).toContain('新要求');
    expect(wrapper.text()).not.toContain('1899');
    expect(wrapper.find('[aria-label="改单客户单价"]').exists()).toBe(false);
    expect(wrapper.findAll('button').some(button => button.text() === '确认金额不变')).toBe(false);
    expect(wrapper.findAll('button').find(button => button.text() === '生效改单')!.attributes('disabled')).toBeDefined();
    expect(apiPost).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it('申请成功后的只读刷新失败保留真实改单号与成功提示', async () => {
    vi.mocked(apiPost).mockResolvedValue({ id: 'CHANGE-SAVED' });
    vi.mocked(apiGet).mockRejectedValue(new Error('查询断网'));
    const wrapper = mount(OrderChangePanel, { props: { target: { orderCode: 'WO-1', motherOrderCode: 'MO-1' } } });
    await wrapper.get('[aria-label="改单新要求"]').setValue('保留本次要求');
    await wrapper.findAll('button').find(button => button.text() === '保存改单申请')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('已保存改单 CHANGE-SAVED');
    expect(wrapper.text()).toContain('只读刷新失败：查询断网');
    expect(apiPost).toHaveBeenCalledOnce();
    expect(apiPost).toHaveBeenCalledWith('/api/business/order-changes', expect.objectContaining({ motherOrderCode: 'MO-1', workOrderCode: 'WO-1', newRequirement: '保留本次要求' }));
    wrapper.unmount();
  });
});
