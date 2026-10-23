import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import type { ApprovalTask } from '../../data/approvalTaskData';
import { ApiRequestError } from '../../services/apiClient';
import { fetchApprovalTask, fetchApprovalTasks, fetchPendingApprovalTasks, submitApprovalDecision } from '../../services/approvalApi';
import { createWorkspaceState } from '../workspace/workspaceContext';
import type { AuthSession } from '../../services/authApi';
import ApprovalWorkspace from './ApprovalWorkspace.vue';

vi.mock('../../services/approvalApi', () => ({
  fetchApprovalTasks: vi.fn(),
  fetchApprovalTask: vi.fn(),
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
const mounted: ReturnType<typeof mount<typeof ApprovalWorkspace>>[] = [];
function mountReview(workspace = createWorkspaceState(), actor: Partial<AuthSession> = {}) {
  const wrapper = mount(ApprovalWorkspace, { global: { provide: { 'zhubao-workspace-context': workspace, 'zhubao-auth-session': ref({ role: 'ADMIN', username: 'reviewer', operator: '复核员', ...actor }) } } });
  mounted.push(wrapper); return wrapper;
}
async function choose(wrapper: ReturnType<typeof mountReview>, code = lossTask.approvalCode) { await flushPromises(); await wrapper.get('[aria-label="查看审批 ' + code + '"]').trigger('click'); }
async function approve(wrapper: ReturnType<typeof mountReview>) { await wrapper.get('.module-side-panel .primary-button').trigger('click'); await flushPromises(); }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
const approved: ApprovalTask = { ...lossTask, status: 'APPROVED', locked: true, decisionOperator: '复核员', decisionComment: '核对通过' };
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

describe('ApprovalWorkspace', () => {
  beforeEach(() => {
    vi.mocked(fetchApprovalTasks).mockReset().mockResolvedValue([lossTask, accountTask]);
    vi.mocked(fetchPendingApprovalTasks).mockReset().mockResolvedValue([lossTask, accountTask]);
    vi.mocked(fetchApprovalTask).mockReset();
    vi.mocked(submitApprovalDecision).mockReset();
  });

  it('从待办打开指定审批，范围内找不到时不回退到第一条', async () => {
    const requested = ref(accountTask.approvalCode);
    const wrapper = mount(ApprovalWorkspace, { global: { provide: { 'zhubao-approval-code': requested } } });
    await flushPromises();
    expect(wrapper.get('.module-side-panel').text()).toContain(accountTask.approvalCode);
    requested.value = 'AP-NOT-VISIBLE';
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('没有改为另一条审批');
    expect(wrapper.find('.module-side-panel .primary-button').exists()).toBe(false);
    wrapper.unmount();
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
    expect(wrapper.find('.module-side-panel').exists()).toBe(false);
    await wrapper.get('[aria-label="查看审批 LOSS-20260706-009"]').trigger('click');
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
  it('从列表明确选任务，意见为空时不预填证据结论，也不发送决定', async () => {
    const wrapper = mountReview(); await flushPromises();
    expect(wrapper.find('.module-side-panel').exists()).toBe(false);
    expect(wrapper.findAll('button').some(button => button.text() === '审批通过')).toBe(false);
    await choose(wrapper); expect(wrapper.get('[aria-label="本次审批意见"]').element).toHaveProperty('value', '');
    await approve(wrapper); expect(submitApprovalDecision).not.toHaveBeenCalled(); expect(wrapper.text()).toContain('请填写本次实际审批意见');
  });
  it('自批禁用，未分配给该组长的任务也不能办理', async () => {
    const self = mountReview(createWorkspaceState(), { operator: lossTask.applicant }); await choose(self);
    expect(self.text()).toContain('申请人不能审批自己提交'); await approve(self); expect(submitApprovalDecision).not.toHaveBeenCalled();
    const other = mountReview(createWorkspaceState(), { role: 'WORKSHOP_LEADER', operator: '另一组长' }); await choose(other);
    expect(other.text()).toContain('未分配给当前审批人'); expect(other.get('.module-side-panel .primary-button').attributes('disabled')).toBeDefined();
  });
  it('决定提交中不能切任务，回执保留原单，陈旧列表不会重新开放审批', async () => {
    const writing = deferred<ApprovalTask>(); vi.mocked(submitApprovalDecision).mockReturnValueOnce(writing.promise);
    const wrapper = mountReview(); await choose(wrapper); await wrapper.get('[aria-label="本次审批意见"]').setValue('核对通过');
    await wrapper.get('.module-side-panel .primary-button').trigger('click');
    await wrapper.get('[aria-label="查看审批 ' + accountTask.approvalCode + '"]').trigger('click');
    expect(wrapper.get('.module-side-panel').text()).toContain(lossTask.approvalCode);
    writing.resolve(approved); await flushPromises();
    expect(submitApprovalDecision).toHaveBeenCalledWith(lossTask.approvalCode, { decision: 'APPROVE', operator: '复核员', comment: '核对通过', evidenceCode: lossTask.evidenceCode });
    expect(wrapper.get('.module-side-panel').text()).toContain('已通过'); await approve(wrapper); expect(submitApprovalDecision).toHaveBeenCalledTimes(1);
  });
  it('未知结果切页后仍保留原决定，只读核对仍待审批时不能再次发送', async () => {
    const workspace = createWorkspaceState(); vi.mocked(submitApprovalDecision).mockRejectedValueOnce(new Error('响应丢失'));
    const first = mountReview(workspace); await choose(first); await first.get('[aria-label="本次审批意见"]').setValue('核对通过'); await approve(first); first.unmount();
    const second = mountReview(workspace); await choose(second);
    expect(second.get('[aria-label="原审批请求核对"]').text()).toContain('核对通过');
    await approve(second); expect(submitApprovalDecision).toHaveBeenCalledTimes(1);
    vi.mocked(fetchApprovalTask).mockResolvedValueOnce(lossTask);
    await second.findAll('button').find(button => button.text() === '只读核对审批结果')!.trigger('click'); await flushPromises();
    expect(second.text()).toContain('结果尚不能确认');
    vi.mocked(fetchApprovalTask).mockResolvedValueOnce({ ...approved, decisionOperator: '实际决定人' });
    await second.findAll('button').find(button => button.text() === '只读核对审批结果')!.trigger('click'); await flushPromises();
    expect(second.text()).toContain('只读核对原任务当前结果'); expect(second.text()).toContain('实际决定人'); expect(submitApprovalDecision).toHaveBeenCalledTimes(1);
  });
  it('页面卸载后迟到成功写入同一账号回执，返回中的新页面随完成重新读取', async () => {
    const workspace = createWorkspaceState(); const writing = deferred<ApprovalTask>(); vi.mocked(submitApprovalDecision).mockReturnValueOnce(writing.promise);
    const first = mountReview(workspace); await choose(first); await first.get('[aria-label="本次审批意见"]').setValue('核对通过');
    await first.get('.module-side-panel .primary-button').trigger('click'); first.unmount();
    const second = mountReview(workspace); await flushPromises(); expect(second.text()).toContain('原决定提交中');
    writing.resolve(approved); await flushPromises();
    expect(second.get('[aria-label="本次审批回执"]').text()).toContain(lossTask.approvalCode);
    expect(second.get('table').text()).toContain(lossTask.approvalCode); expect(workspace.approvalDecisions.value[lossTask.approvalCode].outcome).toBe('succeeded');
  });
  it('退出清理后迟到响应不回填另一账号的缓存', async () => {
    const workspace = createWorkspaceState(); const writing = deferred<ApprovalTask>(); vi.mocked(submitApprovalDecision).mockReturnValueOnce(writing.promise);
    const wrapper = mountReview(workspace); await choose(wrapper); await wrapper.get('[aria-label="本次审批意见"]').setValue('账号甲的意见');
    await wrapper.get('.module-side-panel .primary-button').trigger('click'); wrapper.unmount(); workspace.clear();
    writing.resolve(approved); await flushPromises(); expect(workspace.approvalDecisions.value).toEqual({}); expect(workspace.approvalComments.value).toEqual({});
  });
  it('筛选使旧任务不可见时撤下详情，回到原任务仍保留独立意见', async () => {
    const wrapper = mountReview(); await choose(wrapper); await wrapper.get('[aria-label="本次审批意见"]').setValue('原任务意见');
    await wrapper.get('[aria-label="审批查找"]').setValue('AUTH'); expect(wrapper.find('.module-side-panel').exists()).toBe(false);
    await choose(wrapper, accountTask.approvalCode); expect(wrapper.get('[aria-label="本次审批意见"]').element).toHaveProperty('value', '');
    await wrapper.get('[aria-label="审批查找"]').setValue(''); await choose(wrapper); expect(wrapper.get('[aria-label="本次审批意见"]').element).toHaveProperty('value', '原任务意见');
  });
  it('另一任务或决定的回执视为未知，不把它写成本次成功', async () => {
    vi.mocked(submitApprovalDecision).mockResolvedValueOnce({ ...approved, businessCode: 'OTHER' });
    const wrapper = mountReview(); await choose(wrapper); await wrapper.get('[aria-label="本次审批意见"]').setValue('原任务意见'); await approve(wrapper);
    expect(wrapper.get('[aria-label="原审批请求核对"]').text()).toContain('与原任务或决定不一致');
    expect(wrapper.find('[aria-label="本次审批回执"]').exists()).toBe(false);
    await approve(wrapper); expect(submitApprovalDecision).toHaveBeenCalledTimes(1);
  });
});
