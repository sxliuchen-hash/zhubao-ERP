import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAuthToken, getAuthToken, setAuthToken } from './apiClient';
import {
  authSessionChangedEvent,
  changeOwnPassword,
  draftAuthAccountPreApproval,
  fetchAuthAccountPreApprovalApplications,
  fetchAuthAuditIntegrity,
  fetchAuthAuditEvents,
  fetchAuthAccountPreApprovalReadiness,
  fetchAuthAccounts,
  fetchAuthSession,
  loginAuthSession,
  logoutAuthSession,
  submitAuthAccountPreApprovalApplication,
  unlockAuthAccount,
  updateAuthAccountStatus,
  upsertAuthAccount,
} from './authApi';

const authSession = {
  token: 'token-demo',
  username: 'leader',
  operator: '车间组长 马杰',
  role: 'WORKSHOP_LEADER',
  roleLabel: '车间组长',
  dataScope: 'TEAM',
  dataScopeLabel: '本班组/工位',
  expiresAt: '2026-07-07 20:00:00',
  demoAccount: true,
  passwordChangedAt: '2026-07-08 09:00:00',
  passwordExpiresAt: '2026-10-06 09:00:00',
  passwordExpired: false,
  customerScope: [],
  menuCodes: ['dashboard', 'production', 'quality', 'approvals'],
  menuTree: [{
    code: 'production',
    label: '生产工单',
    module: '排产 / 报工',
    route: '/production',
    buttonCodes: ['BUSINESS_PRODUCTION_REPORT', 'MACHINE_OUTPUT_SUBMIT'],
    children: [],
  }],
};

describe('authApi', () => {
  beforeEach(() => {
    clearAuthToken();
    vi.restoreAllMocks();
  });

  it('stores token after login', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response(authSession));
    const authListener = vi.fn();
    window.addEventListener(authSessionChangedEvent, authListener);

    const session = await loginAuthSession({ username: 'leader', password: 'leader123' });

    expect(session.role).toBe('WORKSHOP_LEADER');
    expect(getAuthToken()).toBe('token-demo');
    expect(authListener).toHaveBeenCalledWith(expect.objectContaining({
      detail: { session: authSession },
    }));
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
    }));
    window.removeEventListener(authSessionChangedEvent, authListener);
  });

  it('uses stored bearer token when fetching current session', async () => {
    setAuthToken('token-demo');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response(authSession));

    const session = await fetchAuthSession();

    expect(session?.operator).toBe('车间组长 马杰');
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/session', expect.objectContaining({
      headers: { Authorization: 'Bearer token-demo' },
    }));
  });

  it('clears token after logout even when backend is unavailable', async () => {
    setAuthToken('token-demo');
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new Error('offline'));
    const authListener = vi.fn();
    window.addEventListener(authSessionChangedEvent, authListener);

    await logoutAuthSession();

    expect(getAuthToken()).toBe('');
    expect(authListener).toHaveBeenCalledWith(expect.objectContaining({
      detail: { session: null },
    }));
    window.removeEventListener(authSessionChangedEvent, authListener);
  });

  it('fetches authentication audit events without requiring password data', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response([{
      eventId: 'AUTH-AUD-0001',
      eventType: 'LOGIN',
      username: 'leader',
      operator: '车间组长 马杰',
      role: 'WORKSHOP_LEADER',
      result: 'SUCCESS',
      reason: '演示账号登录成功',
      occurredAt: '2026-07-08 10:00:00',
      sessionTokenSuffix: 'abc123',
      previousEventHash: '',
      eventHash: 'a'.repeat(64),
    }]));

    const events = await fetchAuthAuditEvents();

    expect(events[0].eventType).toBe('LOGIN');
    expect(events[0].sessionTokenSuffix).toBe('abc123');
    expect(events[0].eventHash).toHaveLength(64);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/audit-events');
  });

  it('fetches authentication audit integrity summary', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response({
      checkedEvents: 3,
      verified: true,
      latestEventId: 'AUTH-AUD-0003',
      latestEventHash: 'b'.repeat(64),
      brokenEventId: '',
      message: '最近认证审计摘要链校验通过',
    }));

    const summary = await fetchAuthAuditIntegrity();

    expect(summary.verified).toBe(true);
    expect(summary.latestEventHash).toHaveLength(64);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/audit-integrity');
  });

  it('fetches account management summaries with bearer token', async () => {
    setAuthToken('admin-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response([authAccount]));

    const accounts = await fetchAuthAccounts();

    expect(accounts[0].username).toBe('planner');
    expect(accounts[0].failedLoginCount).toBe(2);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/accounts', expect.objectContaining({
      headers: { Authorization: 'Bearer admin-token' },
    }));
  });

  it('fetches account pre-approval readiness with bearer token', async () => {
    setAuthToken('admin-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response([authPreApprovalReadiness]));

    const readiness = await fetchAuthAccountPreApprovalReadiness();

    expect(readiness[0].readinessCode).toBe('AUTH-PRE-CREATE-001');
    expect(readiness[0].enforcementStatus).toBe('生产配置可强制申请单号');
    expect(readiness[0].missingCapabilities).not.toContain('全局强制申请单号');
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/accounts/pre-approval-readiness', expect.objectContaining({
      headers: { Authorization: 'Bearer admin-token' },
    }));
  });

  it('drafts account pre-approval evaluation with bearer token', async () => {
    setAuthToken('admin-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response(authPreApprovalDraft));

    const draft = await draftAuthAccountPreApproval({
      actionType: 'CREATE_ACCOUNT',
      targetUsername: 'planner',
      targetOperator: '计划员 陈敏',
      targetRole: 'SALES',
      targetDataScope: 'CUSTOMER_RANGE',
      targetCustomerScope: ['恒信珠宝'],
      targetEnabled: true,
      demoAccount: false,
      reason: '新增业务账号',
      providedEvidence: ['账号申请单：AUTH-REQ-001'],
    });

    expect(draft.draftCode).toBe('AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER');
    expect(draft.missingEvidence).toContain('直属负责人审批');
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/accounts/pre-approval-drafts', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer admin-token' }),
      body: expect.stringContaining('"actionType":"CREATE_ACCOUNT"'),
    }));
  });

  it('wraps account pre-approval application list and submission requests', async () => {
    setAuthToken('admin-token');
    const application = {
      applicationCode: 'AUTH-PRE-APP-202607090001-0001',
      draftCode: 'AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER',
      readinessCode: 'AUTH-PRE-CREATE-001',
      actionType: '创建生产账号',
      targetUsername: 'planner',
      applicant: '系统管理员',
      applicantRole: 'ADMIN',
      targetOperator: '计划员 陈敏',
      targetRole: 'SALES',
      targetDataScope: 'CUSTOMER_RANGE',
      targetCustomerScope: ['恒信珠宝'],
      targetEnabled: true,
      demoAccount: false,
      reason: '新增业务账号前置审批申请',
      requiredEvidence: ['账号申请单'],
      providedEvidence: ['账号申请单：AUTH-REQ-001'],
      missingEvidence: ['直属负责人审批'],
      missingCapabilities: ['阻断式审批状态'],
      approverRoles: ['GENERAL_MANAGER', 'ADMIN'],
      blockingStatus: '缺少前置材料',
      applicationStatus: 'WAITING_EVIDENCE',
      readyForBlockingApproval: false,
      sourceSummary: 'applicationBoundary=stored pre-approval application',
      nextAction: '补齐前置材料：直属负责人审批',
      riskLevel: '高',
      tone: 'red',
      submittedAt: '2026-07-09 09:00:00',
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response([application]))
      .mockResolvedValueOnce(response(application));

    const applications = await fetchAuthAccountPreApprovalApplications();
    const submitted = await submitAuthAccountPreApprovalApplication({
      actionType: 'CREATE_ACCOUNT',
      targetUsername: 'planner',
      targetOperator: '计划员 陈敏',
      targetRole: 'SALES',
      targetDataScope: 'CUSTOMER_RANGE',
      targetCustomerScope: ['恒信珠宝'],
      targetEnabled: true,
      demoAccount: false,
      reason: '新增业务账号前置审批申请',
      providedEvidence: ['账号申请单：AUTH-REQ-001'],
    });

    expect(applications[0].applicationStatus).toBe('WAITING_EVIDENCE');
    expect(submitted.applicationCode).toBe('AUTH-PRE-APP-202607090001-0001');
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/accounts/pre-approval-applications', expect.objectContaining({
      headers: { Authorization: 'Bearer admin-token' },
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/accounts/pre-approval-applications', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer admin-token' }),
      body: expect.stringContaining('"reason":"新增业务账号前置审批申请"'),
    }));
  });

  it('wraps account upsert status and unlock requests', async () => {
    setAuthToken('admin-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response(authAccount))
      .mockResolvedValueOnce(response({ ...authAccount, enabled: false }))
      .mockResolvedValueOnce(response({ ...authAccount, failedLoginCount: 0, lockedUntil: '', locked: false }));

    await upsertAuthAccount({
      username: 'planner',
      password: 'Secure-2026-Plan!',
      operator: '计划员 陈敏',
      role: 'SALES',
      dataScope: 'CUSTOMER_RANGE',
      customerScope: ['恒信珠宝', '雅辰饰品'],
      enabled: true,
      demoAccount: false,
      preApprovalApplicationCode: 'AUTH-PRE-APP-READY-001',
    });
    await updateAuthAccountStatus('planner', {
      enabled: false,
      reason: '离职交接',
      preApprovalApplicationCode: 'AUTH-PRE-APP-READY-STATUS-001',
    });
    await unlockAuthAccount('planner', {
      reason: '现场核实本人',
      preApprovalApplicationCode: 'AUTH-PRE-APP-READY-UNLOCK-001',
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/accounts', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer admin-token' }),
      body: expect.stringContaining('"username":"planner"'),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/accounts', expect.objectContaining({
      body: expect.stringContaining('"customerScope":["恒信珠宝","雅辰饰品"]'),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/accounts', expect.objectContaining({
      body: expect.stringContaining('"preApprovalApplicationCode":"AUTH-PRE-APP-READY-001"'),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/accounts/planner/status', expect.objectContaining({
      method: 'POST',
      body: expect.stringContaining('"preApprovalApplicationCode":"AUTH-PRE-APP-READY-STATUS-001"'),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/auth/accounts/planner/unlock', expect.objectContaining({
      method: 'POST',
      body: expect.stringContaining('"preApprovalApplicationCode":"AUTH-PRE-APP-READY-UNLOCK-001"'),
    }));
  });

  it('changes the current user password with bearer token and broadcasts the refreshed session', async () => {
    setAuthToken('token-demo');
    const refreshedSession = {
      ...authSession,
      passwordChangedAt: '2026-07-08 11:30:00',
      passwordExpiresAt: '2026-10-06 11:30:00',
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response(refreshedSession));
    const authListener = vi.fn();
    window.addEventListener(authSessionChangedEvent, authListener);

    const session = await changeOwnPassword({
      currentPassword: 'leader123',
      newPassword: 'Secure-2026-Leader!',
    });

    expect(session.passwordChangedAt).toBe('2026-07-08 11:30:00');
    expect(getAuthToken()).toBe('token-demo');
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/password', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer token-demo' }),
      body: expect.stringContaining('"newPassword":"Secure-2026-Leader!"'),
    }));
    expect(authListener).toHaveBeenCalledWith(expect.objectContaining({
      detail: { session: refreshedSession },
    }));
    window.removeEventListener(authSessionChangedEvent, authListener);
  });
});

const authAccount = {
  username: 'planner',
  operator: '计划员 陈敏',
  role: 'SALES',
  roleLabel: '业务员',
  dataScope: 'CUSTOMER_RANGE',
  dataScopeLabel: '授权客户范围',
  customerScope: ['恒信珠宝', '雅辰饰品'],
  enabled: true,
  demoAccount: false,
  passwordChangedAt: '2026-07-08 09:00:00',
  passwordExpiresAt: '2026-10-06 09:00:00',
  passwordExpired: false,
  failedLoginCount: 2,
  lockedUntil: '',
  locked: false,
};

const authPreApprovalReadiness = {
  readinessCode: 'AUTH-PRE-CREATE-001',
  actionType: '创建生产账号',
  scenario: '新增非 demo 账号或授予敏感角色前必须先完成申请与审批',
  enforcementStatus: '生产配置可强制申请单号',
  requiredBeforeAction: ['账号申请单', '直属负责人审批'],
  missingCapabilities: ['阻断式审批状态'],
  approverRoles: ['GENERAL_MANAGER', 'ADMIN'],
  auditEvidence: '当前可保存并校验前置申请单号，同时写 ACCOUNT_MANAGEMENT 审计和 ACCOUNT_MANAGEMENT 轻量审批任务',
  rolloutGate: '生产上线前禁止直接创建生产账号',
  nextAction: '补强制阻断开关、审批通过态和账号创建命令放行',
  riskLevel: '高',
  tone: 'red',
};

const authPreApprovalDraft = {
  draftCode: 'AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER',
  readinessCode: 'AUTH-PRE-CREATE-001',
  actionType: '创建生产账号',
  targetUsername: 'planner',
  applicant: '系统管理员',
  applicantRole: 'ADMIN',
  requiredEvidence: ['账号申请单', '直属负责人审批'],
  providedEvidence: ['账号申请单：AUTH-REQ-001'],
  missingEvidence: ['直属负责人审批'],
  missingCapabilities: ['阻断式审批状态'],
  approverRoles: ['GENERAL_MANAGER', 'ADMIN'],
  blockingStatus: '缺少前置材料',
  readyForBlockingApproval: false,
  sourceSummary: 'action=创建生产账号; boundary=pre-action draft only, not a blocking IAM workflow',
  nextAction: '补齐前置材料：直属负责人审批',
  riskLevel: '高',
  tone: 'red',
};

function response<T>(data: T): Response {
  return {
    ok: true,
    json: async () => ({ code: 0, message: 'success', data }),
  } as Response;
}
