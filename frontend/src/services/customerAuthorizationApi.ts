import { apiGet, apiPost } from './apiClient';

export interface CustomerAuthorization {
  username: string;
  customerCode: string;
  customerName: string;
  grantedBy: string;
  grantedAt: string;
}

export interface GrantCustomerAuthorizationRequest {
  username: string;
  customerCode: string;
  customerName?: string;
}

export interface RevokeCustomerAuthorizationRequest {
  username: string;
  customerCode: string;
}

export interface RevokeCustomerAuthorizationResult {
  username: string;
  customerCode: string;
  removed: boolean;
}

export async function fetchCustomerAuthorizations(username?: string): Promise<CustomerAuthorization[]> {
  const query = username ? `?username=${encodeURIComponent(username)}` : '';
  return apiGet<CustomerAuthorization[]>(`/api/security/customer-authorizations${query}`);
}

export async function grantCustomerAuthorization(
  request: GrantCustomerAuthorizationRequest,
): Promise<CustomerAuthorization> {
  return apiPost<GrantCustomerAuthorizationRequest, CustomerAuthorization>(
    '/api/security/customer-authorizations',
    request,
  );
}

export async function revokeCustomerAuthorization(
  request: RevokeCustomerAuthorizationRequest,
): Promise<RevokeCustomerAuthorizationResult> {
  return apiPost<RevokeCustomerAuthorizationRequest, RevokeCustomerAuthorizationResult>(
    '/api/security/customer-authorizations/revocations',
    request,
  );
}
