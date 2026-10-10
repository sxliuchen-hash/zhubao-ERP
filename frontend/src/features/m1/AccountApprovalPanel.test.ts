import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AccountApprovalPanel from './AccountApprovalPanel.vue';
import {
  fetchAuthAccountPreApprovalApplications, fetchAuthAccountPreApprovalReadiness,
  submitAuthAccountPreApprovalApplication, type AuthAccountPreApprovalApplication,
} from '../../services/authApi';
import { fetchApprovalTasks, submitApprovalDecision } from '../../services/approvalApi';
import type { ApprovalTask } from '../../data/approvalTaskData';

vi.mock('../../services/authApi', () => ({
  fetchAuthAccountPreApprovalApplications: vi.fn(), fetchAuthAccountPreApprovalReadiness: vi.fn(),
  submitAuthAccountPreApprovalApplication: vi.fn(), uploadAuthAccountPreApprovalEvidence: vi.fn(),
}));
vi.mock('../../services/approvalApi', () => ({ fetchApprovalTasks: vi.fn(), submitApprovalDecision: vi.fn() }));

const application: AuthAccountPreApprovalApplication = {
  applicationCode: 'APP-001', draftCode: 'DRAFT-001', readinessCode: 'AUTH-PRE-CREATE-001',
  actionType: '创建生产账号', targetUsername: 'worker01', applicant: '管理员', applicantRole: 'ADMIN',
  targetOperator: '王工', targetRole: 'OPERATOR', targetDataScope: 'SELF', targetCustomerScope: [],
  targetAllowedProcesses: ['雕刻'], statusAction: '',
  targetEnabled: true, demoAccount: false, reason: '新岗位入职', requiredEvidence: ['账号申请单'],
  providedEvidence: ['账号申请单'], missingEvidence: [], missingCapabilities: ['正式电子签字'],
  approverRoles: ['ADMIN'], blockingStatus: '可进入审批', applicationStatus: 'READY_FOR_BLOCKING_APPROVAL',
  readyForBlockingApproval: true, sourceSummary: '', nextAction: '', riskLevel: '高', tone: 'red',
  approvalTaskCode: 'APR-001', submittedAt: '2026-10-10',
};
const approvalTask: ApprovalTask = {
  approvalCode: 'APR-001', scene: 'ACCOUNT_PRE_APPROVAL', businessCode: 'APP-001', title: '新建账号',
  status: 'WAITING_APPROVAL', applicant: '管理员', approver: '', ownerRole: 'ADMIN', severity: 'red',
  sourceStatus: '', sourceSummary: '', evidenceCode: '', formalPayroll: false, createdAt: '', locked: false,
};

describe('账号申请与审批', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchAuthAccountPreApprovalApplications).mockResolvedValue([]);
    vi.mocked(fetchAuthAccountPreApprovalReadiness).mockResolvedValue([{
      readinessCode: 'AUTH-PRE-CREATE-001', actionType: '创建生产账号', scenario: '', enforcementStatus: '',
      requiredBeforeAction: ['账号申请单'], missingCapabilities: [], approverRoles: ['ADMIN'],
      auditEvidence: '', rolloutGate: '', nextAction: '', riskLevel: '高', tone: 'red',
    }]);
    vi.mocked(fetchApprovalTasks).mockResolvedValue([approvalTask]);
  });

  it('提交申请后等待批准，批准后才能显式执行', async () => {
    vi.mocked(submitAuthAccountPreApprovalApplication).mockResolvedValue(application);
    vi.mocked(submitApprovalDecision).mockResolvedValue({ ...approvalTask, status: 'APPROVED' });
    const wrapper = mount(AccountApprovalPanel, { props: { operator: '李经理', request: {
      actionType: 'CREATE_ACCOUNT', targetUsername: 'worker01', targetOperator: '王工', targetAllowedProcesses: ['雕刻'], reason: '',
    } } });
    await flushPromises();
    await wrapper.get('[aria-label="账号申请原因"]').setValue('新岗位入职');
    await wrapper.get('input[type="checkbox"]').setValue(true);
    await wrapper.get('[aria-label="账号操作申请"]').trigger('submit');
    await flushPromises();
    expect(submitAuthAccountPreApprovalApplication).toHaveBeenCalledWith({
      actionType: 'CREATE_ACCOUNT', targetUsername: 'worker01', targetOperator: '王工', targetAllowedProcesses: ['雕刻'], reason: '新岗位入职', providedEvidence: ['账号申请单'],
    });
    const execute = wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!;
    expect(execute.attributes('disabled')).toBeDefined();
    await wrapper.get('[aria-label="账号审批意见"]').setValue('岗位和材料已核对');
    await wrapper.findAll('button').find(button => button.text() === '批准')!.trigger('click');
    await flushPromises();
    expect(submitApprovalDecision).toHaveBeenCalledWith('APR-001', {
      decision: 'APPROVE', operator: '李经理', comment: '岗位和材料已核对',
    });
    expect(wrapper.emitted('execute')).toBeUndefined();
    await execute.trigger('click');
    expect(wrapper.emitted('execute')?.[0]).toEqual([application]);
  });

  it('驳回和缺材料申请都不能执行', async () => {
    vi.mocked(fetchAuthAccountPreApprovalApplications).mockResolvedValue([{ ...application, readyForBlockingApproval: false, missingEvidence: ['账号申请单'] }]);
    vi.mocked(fetchApprovalTasks).mockResolvedValue([{ ...approvalTask, status: 'REJECTED' }]);
    const wrapper = mount(AccountApprovalPanel, { props: { operator: '李经理', request: null } });
    await flushPromises();
    expect(wrapper.text()).toContain('已驳回');
    expect(wrapper.text()).toContain('缺少材料：账号申请单');
    expect(wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!.attributes('disabled')).toBeDefined();
    expect(wrapper.emitted('execute')).toBeUndefined();
  });

  it('批准任务绑定其他申请时不能执行', async () => {
    vi.mocked(fetchAuthAccountPreApprovalApplications).mockResolvedValue([application]);
    vi.mocked(fetchApprovalTasks).mockResolvedValue([{ ...approvalTask, status: 'APPROVED', businessCode: 'OTHER-APP' }]);
    const wrapper = mount(AccountApprovalPanel, { props: { operator: '李经理', request: null } });
    await flushPromises();
    expect(wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!.attributes('disabled')).toBeDefined();
  });

  it('已经执行的申请显示已执行且不能再次放行', async () => {
    vi.mocked(fetchAuthAccountPreApprovalApplications).mockResolvedValue([{ ...application, applicationStatus: 'EXECUTED', readyForBlockingApproval: false }]);
    vi.mocked(fetchApprovalTasks).mockResolvedValue([{ ...approvalTask, status: 'APPROVED' }]);
    const wrapper = mount(AccountApprovalPanel, { props: { operator: '李经理', request: null } });
    await flushPromises();
    expect(wrapper.text()).toContain('已执行');
    expect(wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!.attributes('disabled')).toBeDefined();
  });
});
