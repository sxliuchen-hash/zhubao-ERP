import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import DeskInbox from './DeskInbox.vue';
import { fetchOrderWorkspace } from '../../services/orderWorkspaceApi';
import { fetchPendingApprovalTasks } from '../../services/approvalApi';
import type { OrderWorkspaceRow } from '../../services/orderWorkspaceTypes';
import type { ApprovalTask } from '../../data/approvalTaskData';

vi.mock('../../services/orderWorkspaceApi', () => ({ fetchOrderWorkspace: vi.fn() }));
vi.mock('../../services/approvalApi', () => ({ fetchPendingApprovalTasks: vi.fn() }));
const row = { rowKey: 'WORK_ORDER|FG-1', code: 'FG-1', objectType: 'WORK_ORDER', motherOrderCode: 'MO-1', workOrderCode: 'WO-1', executionRoundCode: 'FG-1', goodsName: '原轮次目标', quantity: '10.5', quantityUnit: '克', dueDate: '2026-11-01', currentProcess: '抛光', businessStatus: '在制' } as OrderWorkspaceRow;
const task = { approvalCode: 'AP-2', title: '损耗待核对', businessCode: 'WO-1', applicant: '申请人', createdAt: '2026-10-23' } as ApprovalTask;
beforeEach(() => {
  vi.mocked(fetchOrderWorkspace).mockReset().mockResolvedValue({ items: [row], page: 1, size: 5, totalRows: 24, facetCounts: {}, availableViews: ['ORDERS'], generatedAt: '' });
  vi.mocked(fetchPendingApprovalTasks).mockReset().mockResolvedValue([task]);
});
describe('工作台事项预览', () => {
  it('订单和审批按真实菜单分别加载，保留服务端总数和轮次目标，只导航不完成业务', async () => {
    const openOrder = vi.fn();
    const openApproval = vi.fn();
    const wrapper = mount(DeskInbox, { global: { provide: { 'zhubao-visible-tabs': ref(['production', 'approvals']), 'zhubao-open-order': openOrder, 'zhubao-open-approval': openApproval } } });
    await flushPromises();
    expect(fetchOrderWorkspace).toHaveBeenCalledWith({ view: 'ORDERS', facet: 'ALL', page: 1, size: 5 });
    expect(wrapper.text()).toContain('当前分类共 24 项目标，本次预览 1 项');
    expect(wrapper.text()).toContain('10.5 克');
    expect(wrapper.text()).toContain('交期 2026-11-01');
    await wrapper.get('[aria-label="本岗订单预览"] .inbox-row').trigger('click');
    expect(openOrder).toHaveBeenCalledWith({ objectType: 'WORK_ORDER', code: 'FG-1' });
    await wrapper.get('[aria-label="待我核对的审批"] .inbox-row').trigger('click');
    expect(openApproval).toHaveBeenCalledWith('AP-2');
    expect(wrapper.text()).not.toContain('勾选完成');
    wrapper.unmount();
  });
  it('一个来源失败不隐藏另一个成功来源，没有授权的来源不查询', async () => {
    vi.mocked(fetchOrderWorkspace).mockRejectedValueOnce(new Error('订单读取失败'));
    const wrapper = mount(DeskInbox, { global: { provide: { 'zhubao-visible-tabs': ref(['production', 'approvals']) } } });
    await flushPromises();
    expect(wrapper.text()).toContain('订单读取失败');
    expect(wrapper.text()).toContain('损耗待核对');
    wrapper.unmount();
    vi.mocked(fetchOrderWorkspace).mockClear();
    vi.mocked(fetchPendingApprovalTasks).mockClear();
    const hidden = mount(DeskInbox, { global: { provide: { 'zhubao-visible-tabs': ref(['finance']) } } });
    await flushPromises();
    expect(fetchOrderWorkspace).not.toHaveBeenCalled();
    expect(fetchPendingApprovalTasks).not.toHaveBeenCalled();
    expect(hidden.find('section').exists()).toBe(false);
    hidden.unmount();
  });
  it('切换分类后丢弃旧订单响应，权限撤销后不残留预览', async () => {
    let finish!: (value: Awaited<ReturnType<typeof fetchOrderWorkspace>>) => void;
    vi.mocked(fetchOrderWorkspace).mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
    const tabs = ref(['production']);
    const wrapper = mount(DeskInbox, { global: { provide: { 'zhubao-visible-tabs': tabs } } });
    await wrapper.get('select').setValue('IN_PROCESS');
    await flushPromises();
    finish({ items: [{ ...row, goodsName: '旧客户单据' }], page: 1, size: 5, totalRows: 1, facetCounts: {}, availableViews: ['ORDERS'], generatedAt: '' });
    await flushPromises();
    expect(wrapper.text()).not.toContain('旧客户单据');
    tabs.value = ['finance'];
    await flushPromises();
    expect(wrapper.text()).not.toContain('原轮次目标');
    wrapper.unmount();
  });
});
