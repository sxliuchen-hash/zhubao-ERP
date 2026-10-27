import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAuthToken, getAuthToken, setAuthToken } from './apiClient';
import {
  authSessionChangedEvent,
  changeOwnPassword,
  deleteAuthAccount,
  fetchAuthAccountOptions,
  fetchAuthAccounts,
  fetchAuthSession,
  loginAuthSession,
  logoutAuthSession,
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
  passwordChangeRequired: false,
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

  it('读取管理员角色权限目录并编码删除目标账号', async () => {
    setAuthToken('admin-token');
    const options = { roles: [{ value: 'OPERATOR', label: '操作工', permissions: [] }] };
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response(options))
      .mockResolvedValueOnce(response(null));
    expect(await fetchAuthAccountOptions()).toEqual(options);
    await deleteAuthAccount('员工/01');
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/account-options', expect.objectContaining({ headers: { Authorization: 'Bearer admin-token' } }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/accounts/%E5%91%98%E5%B7%A5%2F01/delete', expect.objectContaining({ method: 'POST', body: '{}' }));
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
      passwordChangeRequired: false,
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
  passwordChangeRequired: true,
  failedLoginCount: 2,
  lockedUntil: '',
  locked: false,
};

function response<T>(data: T): Response {
  return {
    ok: true,
    json: async () => ({ code: 0, message: 'success', data }),
  } as Response;
}
