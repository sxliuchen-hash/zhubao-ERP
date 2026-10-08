import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApprovalTask } from '../../data/approvalTaskData';
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
    expect(wrapper.text()).toContain('轻量审批不等于完整工作流');
    expect(wrapper.text()).toContain('审批通过');
    expect(wrapper.text()).toContain('驳回补证');
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
