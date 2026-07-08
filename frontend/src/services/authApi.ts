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
  customerScope: string[];
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

export interface ChangeOwnPasswordRequest {
  currentPassword: string;
  newPassword: string;
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
  previousEventHash: string;
  eventHash: string;
}

export interface AuthAuditIntegritySummary {
  checkedEvents: number;
  verified: boolean;
  latestEventId: string;
  latestEventHash: string;
  brokenEventId: string;
  message: string;
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
  failedLoginCount: number;
  lockedUntil: string;
  locked: boolean;
}

export interface AuthAccountPreApprovalReadiness {
  readinessCode: string;
  actionType: string;
  scenario: string;
  enforcementStatus: string;
  requiredBeforeAction: string[];
  missingCapabilities: string[];
  approverRoles: string[];
  auditEvidence: string;
  rolloutGate: string;
  nextAction: string;
  riskLevel: string;
  tone: string;
}

export interface AuthAccountPreApprovalDraftRequest {
  actionType: string;
  targetUsername: string;
  targetOperator?: string;
  targetRole?: string;
  targetDataScope?: string;
  targetCustomerScope?: string[];
  targetEnabled?: boolean;
  demoAccount?: boolean;
  reason: string;
  providedEvidence?: string[];
}

export interface AuthAccountPreApprovalDraft {
  draftCode: string;
  readinessCode: string;
  actionType: string;
  targetUsername: string;
  applicant: string;
  applicantRole: string;
  requiredEvidence: string[];
  providedEvidence: string[];
  missingEvidence: string[];
  missingCapabilities: string[];
  approverRoles: string[];
  blockingStatus: string;
  readyForBlockingApproval: boolean;
  sourceSummary: string;
  nextAction: string;
  riskLevel: string;
  tone: string;
}

export interface AuthAccountPreApprovalApplicationRequest {
  actionType: string;
  targetUsername: string;
  targetOperator?: string;
  targetRole?: string;
  targetDataScope?: string;
  targetCustomerScope?: string[];
  targetEnabled?: boolean;
  demoAccount?: boolean;
  reason: string;
  providedEvidence?: string[];
}

export interface AuthAccountPreApprovalApplication {
  applicationCode: string;
  draftCode: string;
  readinessCode: string;
  actionType: string;
  targetUsername: string;
  applicant: string;
  applicantRole: string;
  targetOperator: string;
  targetRole: string;
  targetDataScope: string;
  targetCustomerScope: string[];
  targetEnabled: boolean | null;
  demoAccount: boolean | null;
  reason: string;
  requiredEvidence: string[];
  providedEvidence: string[];
  missingEvidence: string[];
  missingCapabilities: string[];
  approverRoles: string[];
  blockingStatus: string;
  applicationStatus: string;
  readyForBlockingApproval: boolean;
  sourceSummary: string;
  nextAction: string;
  riskLevel: string;
  tone: string;
  submittedAt: string;
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

export async function changeOwnPassword(request: ChangeOwnPasswordRequest): Promise<AuthSession> {
  const session = await apiPost<ChangeOwnPasswordRequest, AuthSession>('/api/auth/password', request);
  setAuthToken(session.token);
  notifyAuthSessionChanged(session);
  return session;
}

export async function fetchAuthDemoUsers(): Promise<string[]> {
  return apiGet<string[]>('/api/auth/demo-users');
}

export async function fetchAuthAuditEvents(): Promise<AuthAuditEvent[]> {
  return apiGet<AuthAuditEvent[]>('/api/auth/audit-events');
}

export async function fetchAuthAuditIntegrity(): Promise<AuthAuditIntegritySummary> {
  return apiGet<AuthAuditIntegritySummary>('/api/auth/audit-integrity');
}

export async function fetchAuthAccounts(): Promise<AuthUserAccountSummary[]> {
  return apiGet<AuthUserAccountSummary[]>('/api/auth/accounts');
}

export async function fetchAuthAccountPreApprovalReadiness(): Promise<AuthAccountPreApprovalReadiness[]> {
  return apiGet<AuthAccountPreApprovalReadiness[]>('/api/auth/accounts/pre-approval-readiness');
}

export async function draftAuthAccountPreApproval(
  request: AuthAccountPreApprovalDraftRequest,
): Promise<AuthAccountPreApprovalDraft> {
  return apiPost<AuthAccountPreApprovalDraftRequest, AuthAccountPreApprovalDraft>(
    '/api/auth/accounts/pre-approval-drafts',
    request,
  );
}

export async function fetchAuthAccountPreApprovalApplications(): Promise<AuthAccountPreApprovalApplication[]> {
  return apiGet<AuthAccountPreApprovalApplication[]>('/api/auth/accounts/pre-approval-applications');
}

export async function submitAuthAccountPreApprovalApplication(
  request: AuthAccountPreApprovalApplicationRequest,
): Promise<AuthAccountPreApprovalApplication> {
  return apiPost<AuthAccountPreApprovalApplicationRequest, AuthAccountPreApprovalApplication>(
    '/api/auth/accounts/pre-approval-applications',
    request,
  );
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
