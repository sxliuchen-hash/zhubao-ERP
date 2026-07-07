import { apiGet, apiPost, clearAuthToken, getAuthToken, setAuthToken } from './apiClient';

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
  menuCodes: string[];
}

export interface AuthLoginRequest {
  username: string;
  password: string;
}

export async function loginAuthSession(request: AuthLoginRequest): Promise<AuthSession> {
  const session = await apiPost<AuthLoginRequest, AuthSession>('/api/auth/login', request);
  setAuthToken(session.token);
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
  }
}

export async function fetchAuthDemoUsers(): Promise<string[]> {
  return apiGet<string[]>('/api/auth/demo-users');
}
