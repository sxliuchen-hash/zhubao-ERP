import { apiGet, apiPost, clearAuthToken, getAuthToken, setAuthToken } from './apiClient';

export const authSessionChangedEvent = 'zhubao-auth-session-changed';

export interface AuthSession {
  token: string;
  username: string;
  operator: string;
  role: string;
  roleLabel: string;
  dataScope: string;
  dataScopeLabel: string;
  expiresAt: string;
  demoAccount: boolean;
  passwordChangedAt: string;
  passwordExpiresAt: string;
  passwordExpired: boolean;
  menuCodes: string[];
  menuTree: NavigationMenuItem[];
}

export interface NavigationMenuItem {
  code: string;
  label: string;
  module: string;
  route: string;
  buttonCodes: string[];
  children: NavigationMenuItem[];
}

export interface AuthLoginRequest {
  username: string;
  password: string;
}

export interface AuthAuditEvent {
  eventId: string;
  eventType: string;
  username: string;
  operator: string;
  role: string;
  result: string;
  reason: string;
  occurredAt: string;
  sessionTokenSuffix: string;
}

export interface AuthUserAccountSummary {
  username: string;
  operator: string;
  role: string;
  roleLabel: string;
  dataScope: string;
  dataScopeLabel: string;
  enabled: boolean;
  demoAccount: boolean;
  passwordChangedAt: string;
  passwordExpiresAt: string;
  passwordExpired: boolean;
  failedLoginCount: number;
  lockedUntil: string;
  locked: boolean;
}

export interface ManageAuthUserAccountRequest {
  username: string;
  password?: string;
  operator: string;
  role: string;
  dataScope: string;
  enabled: boolean;
  demoAccount: boolean;
}

export interface AuthUserAccountStatusRequest {
  enabled: boolean;
  reason?: string;
}

export interface AuthUserAccountUnlockRequest {
  reason?: string;
}

export interface AuthSessionChangedDetail {
  session: AuthSession | null;
}

export async function loginAuthSession(request: AuthLoginRequest): Promise<AuthSession> {
  const session = await apiPost<AuthLoginRequest, AuthSession>('/api/auth/login', request);
  setAuthToken(session.token);
  notifyAuthSessionChanged(session);
  return session;
}

export async function fetchAuthSession(): Promise<AuthSession | null> {
  if (!getAuthToken()) {
    return null;
  }
  return apiGet<AuthSession>('/api/auth/session');
}

export async function logoutAuthSession(): Promise<void> {
  try {
    if (getAuthToken()) {
      await apiPost<Record<string, never>, void>('/api/auth/logout', {});
    }
  } catch {
    // Local logout must still work when the backend session has already expired or is unreachable.
  } finally {
    clearAuthToken();
    notifyAuthSessionChanged(null);
  }
}

export async function fetchAuthDemoUsers(): Promise<string[]> {
  return apiGet<string[]>('/api/auth/demo-users');
}

export async function fetchAuthAuditEvents(): Promise<AuthAuditEvent[]> {
  return apiGet<AuthAuditEvent[]>('/api/auth/audit-events');
}

export async function fetchAuthAccounts(): Promise<AuthUserAccountSummary[]> {
  return apiGet<AuthUserAccountSummary[]>('/api/auth/accounts');
}

export async function upsertAuthAccount(request: ManageAuthUserAccountRequest): Promise<AuthUserAccountSummary> {
  return apiPost<ManageAuthUserAccountRequest, AuthUserAccountSummary>('/api/auth/accounts', request);
}

export async function updateAuthAccountStatus(
  username: string,
  request: AuthUserAccountStatusRequest,
): Promise<AuthUserAccountSummary> {
  return apiPost<AuthUserAccountStatusRequest, AuthUserAccountSummary>(
    `/api/auth/accounts/${encodeURIComponent(username)}/status`,
    request,
  );
}

export async function unlockAuthAccount(
  username: string,
  request: AuthUserAccountUnlockRequest,
): Promise<AuthUserAccountSummary> {
  return apiPost<AuthUserAccountUnlockRequest, AuthUserAccountSummary>(
    `/api/auth/accounts/${encodeURIComponent(username)}/unlock`,
    request,
  );
}

function notifyAuthSessionChanged(session: AuthSession | null): void {
  if (typeof globalThis.dispatchEvent !== 'function' || typeof globalThis.CustomEvent !== 'function') {
    return;
  }
  globalThis.dispatchEvent(new CustomEvent<AuthSessionChangedDetail>(authSessionChangedEvent, {
    detail: { session },
  }));
}
