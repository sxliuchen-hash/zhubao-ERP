import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAuthToken, getAuthToken, setAuthToken } from './apiClient';
import {
  authSessionChangedEvent,
  fetchAuthAuditEvents,
  fetchAuthSession,
  loginAuthSession,
  logoutAuthSession,
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
});

function response<T>(data: T): Response {
  return {
    ok: true,
    json: async () => ({ code: 0, message: 'success', data }),
  } as Response;
}
