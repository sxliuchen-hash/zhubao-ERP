import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAuthToken, getAuthToken, setAuthToken } from './apiClient';
import {
  authSessionChangedEvent,
  fetchAuthAuditEvents,
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
    }]));

    const events = await fetchAuthAuditEvents();

    expect(events[0].eventType).toBe('LOGIN');
    expect(events[0].sessionTokenSuffix).toBe('abc123');
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/audit-events');
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
      password: 'planner-pass',
      operator: '计划员 陈敏',
      role: 'SALES',
      dataScope: 'CUSTOMER_RANGE',
      enabled: true,
      demoAccount: false,
    });
    await updateAuthAccountStatus('planner', { enabled: false, reason: '离职交接' });
    await unlockAuthAccount('planner', { reason: '现场核实本人' });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/accounts', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer admin-token' }),
      body: expect.stringContaining('"username":"planner"'),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/accounts/planner/status', expect.objectContaining({
      method: 'POST',
      body: expect.stringContaining('"enabled":false'),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/auth/accounts/planner/unlock', expect.objectContaining({
      method: 'POST',
      body: expect.stringContaining('现场核实本人'),
    }));
  });
});

const authAccount = {
  username: 'planner',
  operator: '计划员 陈敏',
  role: 'SALES',
  roleLabel: '业务员',
  dataScope: 'CUSTOMER_RANGE',
  dataScopeLabel: '授权客户范围',
  enabled: true,
  demoAccount: false,
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
