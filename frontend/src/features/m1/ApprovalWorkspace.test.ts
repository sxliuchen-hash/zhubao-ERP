import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApprovalTask } from '../../data/approvalTaskData';
import { ApiRequestError } from '../../services/apiClient';
import { fetchApprovalTasks, fetchPendingApprovalTasks } from '../../services/approvalApi';
import ApprovalWorkspace from './ApprovalWorkspace.vue';

vi.mock('../../services/approvalApi', () => ({
  fetchApprovalTasks: vi.fn(),
  fetchPendingApprovalTasks: vi.fn(),
  submitApprovalDecision: vi.fn(),
}));

const lossTask: ApprovalTask = {
  approvalCode: 'LOSS-20260706-009',
  scene: 'LOSS_APPROVAL',
  businessCode: 'ZD-20260706-001',
  title: '抛光工序超标损耗审批',
  status: 'WAITING_APPROVAL',
  applicant: '操作工 李华',
  approver: '车间组长 马杰',
  ownerRole: 'WORKSHOP_LEADER',
  severity: 'red',
  sourceStatus: 'WAITING_APPROVAL',
  sourceSummary: 'trialWage=284.60; formalPayroll=false',
  evidenceCode: 'EV-QC-LOSS-20260706-009',
  formalPayroll: false,
  createdAt: '2026-07-06T15:42:00+08:00',
  decidedAt: null,
  decisionOperator: null,
  decisionComment: null,
  locked: false,
};

const accountTask: ApprovalTask = {
  approvalCode: 'AUTH-ACC-APP-20260708-001',
  scene: 'ACCOUNT_MANAGEMENT',
  businessCode: 'planner',
  title: '生产账号变更审批摘要',
  status: 'WAITING_APPROVAL',
  applicant: '系统管理员',
  approver: '总经理',
  ownerRole: 'ADMIN',
  severity: 'amber',
  sourceStatus: 'WAITING_APPROVAL',
  sourceSummary: '生产账号变更',
  evidenceCode: 'EV-AUTH-001',
  formalPayroll: false,
  createdAt: '2026-07-08T10:00:00+08:00',
  decidedAt: null,
  decisionOperator: null,
  decisionComment: null,
  locked: false,
};

describe('ApprovalWorkspace', () => {
  beforeEach(() => {
    vi.mocked(fetchApprovalTasks).mockResolvedValue([lossTask, accountTask]);
    vi.mocked(fetchPendingApprovalTasks).mockResolvedValue([lossTask, accountTask]);
  });

  it('renders approval tasks returned by the api', async () => {
    const wrapper = mount(ApprovalWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('轻量审批工作台');
    expect(wrapper.text()).toContain('审批阻断队列');
    expect(wrapper.text()).toContain('审批任务池');
    expect(wrapper.find('table.approval-task-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('LOSS-20260706-009');
    expect(wrapper.text()).toContain('AUTH-ACC-APP-20260708-001');
    expect(wrapper.text()).toContain('ACCOUNT_MANAGEMENT');
    expect(wrapper.text()).toContain('生产账号变更审批摘要');
    expect(wrapper.text()).toContain('仅工资试算，不发放');
    expect(wrapper.text()).toContain('账号操作在用户管理页由管理员直接完成');
    expect(wrapper.text()).toContain('审批通过');
    expect(wrapper.text()).toContain('驳回补证');
  });

  it('筛选后返回仍保留关键字，无权限不显示成空成功', async () => {
    const wrapper = mount(ApprovalWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="审批查找"]').setValue('AUTH');
    expect(wrapper.get('table.approval-task-table').text()).toContain('AUTH-ACC-APP-20260708-001');
    expect(wrapper.get('table.approval-task-table').text()).not.toContain('LOSS-20260706-009');
    await wrapper.get('.desk-card').trigger('click');
    expect(wrapper.classes()).toContain('is-detail');
    await wrapper.get('.detail-back').trigger('click');
    expect(wrapper.classes()).not.toContain('is-detail');
    expect((wrapper.get('[aria-label="审批查找"]').element as HTMLInputElement).value).toBe('AUTH');

    vi.mocked(fetchApprovalTasks).mockRejectedValueOnce(new ApiRequestError('无权查看审批', 403, 40301));
    vi.mocked(fetchPendingApprovalTasks).mockRejectedValueOnce(new ApiRequestError('无权查看审批', 403, 40301));
    const denied = mount(ApprovalWorkspace);
    await flushPromises();
    expect(denied.text()).toContain('无权限');
    expect(denied.text()).toContain('无权查看审批');
    expect(denied.find('.active-object-card').exists()).toBe(false);
  });

  it('shows an empty approval list and the failure reason when the api is unavailable', async () => {
    vi.mocked(fetchApprovalTasks).mockRejectedValueOnce(new Error('审批接口不可用'));
    vi.mocked(fetchPendingApprovalTasks).mockRejectedValueOnce(new Error('审批接口不可用'));

    const wrapper = mount(ApprovalWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('审批接口不可用');
    expect(wrapper.text()).toContain('暂无审批任务');
    expect(wrapper.text()).not.toContain('LOSS-20260706-009');
  });
});
