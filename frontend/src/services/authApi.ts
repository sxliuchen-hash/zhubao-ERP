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
  passwordChangeRequired: boolean;
  customerScope: string[];
  menuCodes: string[];
  menuTree: NavigationMenuItem[];
  allowedProcesses?: string[];
  coverDuties?: string[];
  teamCode?: string;
  departmentCode?: string;
  effectivePermissions?: string[];
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

export interface ChangeOwnPasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AuthUserAccountSummary {
  username: string;
  operator: string;
  role: string;
  roleLabel: string;
  dataScope: string;
  dataScopeLabel: string;
  customerScope: string[];
  enabled: boolean;
  demoAccount: boolean;
  passwordChangedAt: string;
  passwordExpiresAt: string;
  passwordExpired: boolean;
  passwordChangeRequired: boolean;
  failedLoginCount: number;
  lockedUntil: string;
  locked: boolean;
  allowedProcesses?: string[];
  coverDuties?: string[];
  teamCode?: string;
  departmentCode?: string;
}

export interface ManageAuthUserAccountRequest {
  username: string;
  password?: string;
  operator: string;
  role: string;
  dataScope: string;
  customerScope?: string[];
  enabled: boolean;
  demoAccount: boolean;
  preApprovalApplicationCode?: string;
  allowedProcesses?: string[];
  coverDuties?: string[];
  teamCode?: string;
  departmentCode?: string;
}

export interface AuthAccountOptions {
  roles: Array<{ value: string; label: string; permissions: Array<{ code: string; label: string }> }>;
}

export async function fetchAuthAccountOptions(): Promise<AuthAccountOptions> {
  return apiGet<AuthAccountOptions>('/api/auth/account-options');
}

export async function deleteAuthAccount(username: string): Promise<void> {
  return apiPost<Record<string, never>, void>(`/api/auth/accounts/${encodeURIComponent(username)}/delete`, {});
}

export interface AuthUserAccountStatusRequest {
  enabled: boolean;
  reason?: string;
  preApprovalApplicationCode?: string;
}

export interface AuthUserAccountUnlockRequest {
  reason?: string;
  preApprovalApplicationCode?: string;
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
    // 后端会话过期或暂时不可用时，仍需清理本地登录状态。
  } finally {
    clearAuthToken();
    notifyAuthSessionChanged(null);
  }
}

export async function changeOwnPassword(request: ChangeOwnPasswordRequest): Promise<AuthSession> {
  const session = await apiPost<ChangeOwnPasswordRequest, AuthSession>('/api/auth/password', request);
  setAuthToken(session.token);
  notifyAuthSessionChanged(session);
  return session;
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
