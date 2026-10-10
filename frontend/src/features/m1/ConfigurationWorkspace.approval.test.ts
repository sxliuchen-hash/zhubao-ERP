import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ConfigurationWorkspace from './ConfigurationWorkspace.vue';
import { fetchAuthAccountPreApprovalApplications, fetchAuthAccountPreApprovalReadiness, fetchAuthAccounts, fetchAuthSession, upsertAuthAccount, unlockAuthAccount, updateAuthAccountStatus, type AuthAccountPreApprovalApplication, type AuthUserAccountSummary } from '../../services/authApi';
import { fetchApprovalTasks } from '../../services/approvalApi';
import type { ApprovalTask } from '../../data/approvalTaskData';

vi.mock('../../services/authApi', () => ({
  fetchAuthAccountPreApprovalApplications: vi.fn(), fetchAuthAccountPreApprovalReadiness: vi.fn(),
  fetchAuthAccounts: vi.fn(), fetchAuthSession: vi.fn(), upsertAuthAccount: vi.fn(), changeOwnPassword: vi.fn(),
  updateAuthAccountStatus: vi.fn(), unlockAuthAccount: vi.fn(),
  submitAuthAccountPreApprovalApplication: vi.fn(), uploadAuthAccountPreApprovalEvidence: vi.fn(),
}));
vi.mock('../../services/approvalApi', () => ({ fetchApprovalTasks: vi.fn(), submitApprovalDecision: vi.fn() }));
vi.mock('../../services/customerAuthorizationApi', () => ({ fetchCustomerAuthorizations: vi.fn().mockResolvedValue([]), grantCustomerAuthorization: vi.fn(), revokeCustomerAuthorization: vi.fn() }));
vi.mock('../../services/documentManagementApi', () => ({ fetchStoredContent: vi.fn().mockResolvedValue(null), retireStoredContent: vi.fn() }));

const application = {
  applicationCode: 'APP-CREATE-1', readinessCode: 'AUTH-PRE-CREATE-001', targetUsername: 'new-worker',
  targetOperator: '新员工', targetRole: 'OPERATOR', targetDataScope: 'SELF', targetCustomerScope: [],
  targetAllowedProcesses: [], statusAction: '',
  targetEnabled: true, demoAccount: false, readyForBlockingApproval: true, approvalTaskCode: 'APR-CREATE-1',
  reason: '入职', actionType: '创建生产账号', missingEvidence: [], missingCapabilities: [],
} as unknown as AuthAccountPreApprovalApplication;

describe('配置账号审批执行', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchAuthSession).mockResolvedValue(null);
    vi.mocked(fetchAuthAccounts).mockResolvedValue([]);
    vi.mocked(fetchAuthAccountPreApprovalApplications).mockResolvedValue([application]);
    vi.mocked(fetchAuthAccountPreApprovalReadiness).mockResolvedValue([]);
    vi.mocked(fetchApprovalTasks).mockResolvedValue([{
      approvalCode: 'APR-CREATE-1', status: 'APPROVED', scene: 'ACCOUNT_PRE_APPROVAL', businessCode: 'APP-CREATE-1',
    } as ApprovalTask]);
  });

  async function prepareAccount(wrapper: ReturnType<typeof mount>, operator: string) {
    const inputs = wrapper.get('.account-admin-form').findAll('input');
    await inputs[0].setValue('new-worker');
    await inputs[1].setValue(operator);
    await inputs[2].setValue('TemporaryPass2026');
    await wrapper.get('.account-admin-form').trigger('submit');
    await flushPromises();
  }

  it('准备账号操作不会直接创建，执行时携带已批准申请号', async () => {
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();
    await prepareAccount(wrapper, '新员工');
    expect(upsertAuthAccount).not.toHaveBeenCalled();
    await wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!.trigger('click');
    await flushPromises();
    expect(upsertAuthAccount).toHaveBeenCalledWith(expect.objectContaining({
      username: 'new-worker', operator: '新员工', password: 'TemporaryPass2026', preApprovalApplicationCode: 'APP-CREATE-1',
    }));
    expect(wrapper.text()).toContain('已执行 APP-CREATE-1 的账号操作');
  });

  it('申请目标与准备的操作不一致时阻止执行', async () => {
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();
    await prepareAccount(wrapper, '另一人');
    await wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!.trigger('click');
    await flushPromises();
    expect(upsertAuthAccount).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('已批准申请与当前准备的账号操作不一致');
  });

  it('岗位工序与批准快照不一致时拒绝执行', async () => {
    vi.mocked(fetchAuthAccountPreApprovalApplications).mockResolvedValue([{ ...application, targetAllowedProcesses: ['镶嵌'] }]);
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();
    await prepareAccount(wrapper, '新员工');
    await wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!.trigger('click');
    await flushPromises();
    expect(upsertAuthAccount).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('已批准申请与当前准备的账号操作不一致');
  });

  it('停用申请不能复用于解锁，具体动作必须一致', async () => {
    vi.mocked(fetchAuthAccounts).mockResolvedValue([{
      username: 'new-worker', operator: '新员工', role: 'OPERATOR', dataScope: 'SELF',
      customerScope: [], allowedProcesses: [], enabled: true, locked: true, demoAccount: false,
    } as unknown as AuthUserAccountSummary]);
    vi.mocked(fetchAuthAccountPreApprovalApplications).mockResolvedValue([{
      ...application, readinessCode: 'AUTH-PRE-STATUS-001', actionType: '账号启停/解锁', statusAction: 'DISABLE', targetEnabled: false,
    }]);
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '申请解锁')!.trigger('click');
    await wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!.trigger('click');
    await flushPromises();
    expect(unlockAuthAccount).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('已批准申请与当前准备的账号操作不一致');
    await wrapper.findAll('button').find(button => button.text() === '申请停用')!.trigger('click');
    await wrapper.findAll('button').find(button => button.text() === '执行已批准操作')!.trigger('click');
    await flushPromises();
    expect(updateAuthAccountStatus).toHaveBeenCalledWith('new-worker', {
      enabled: false, reason: '管理员停用', preApprovalApplicationCode: 'APP-CREATE-1',
    });
  });
});
