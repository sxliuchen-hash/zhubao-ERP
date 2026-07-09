import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  fetchCustomerAuthorizations,
  grantCustomerAuthorization,
  revokeCustomerAuthorization,
} from './customerAuthorizationApi';

describe('customerAuthorizationApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists, grants and revokes customer authorizations', async () => {
    const grant = {
      username: 'sales01',
      customerCode: 'CUST-1001',
      customerName: '恒信珠宝',
      grantedBy: '总经理',
      grantedAt: '2026-07-09T10:00:00',
    };
    const ok = (data: unknown) => ({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data }),
    } as Response);
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(ok([grant]))
      .mockResolvedValueOnce(ok(grant))
      .mockResolvedValueOnce(ok({ username: 'sales01', customerCode: 'CUST-1001', removed: true }));

    const list = await fetchCustomerAuthorizations('sales01');
    const granted = await grantCustomerAuthorization({
      username: 'sales01',
      customerCode: 'CUST-1001',
      customerName: '恒信珠宝',
    });
    const revoked = await revokeCustomerAuthorization({ username: 'sales01', customerCode: 'CUST-1001' });

    expect(list[0].customerCode).toBe('CUST-1001');
    expect(granted.customerName).toBe('恒信珠宝');
    expect(revoked.removed).toBe(true);
    expect(fetchMock.mock.calls[0][0]).toContain('/api/security/customer-authorizations?username=sales01');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/security/customer-authorizations');
    expect(fetchMock.mock.calls[2][0]).toContain('/api/security/customer-authorizations/revocations');
    expect(fetchMock.mock.calls[1][1]?.method).toBe('POST');
  });
});
