import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchStatement, generateBilling } from './financeApi';

describe('financeApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('按客户和月份读取账单', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: {
          customerId: 1001,
          customerName: '恒信珠宝',
          stockQuantity: 1,
          stockWeight: 2,
          receivableAmount: 10,
          receivedAmount: 0,
          outstandingAmount: 10,
          prepaymentAmount: 0,
          lines: [],
          receipts: [],
          stockNote: '实物为当前结存，不按月份回放',
        },
      }),
    } as Response);

    const statement = await fetchStatement('1001', '2026-10');

    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      '/api/finance/statements?customerId=1001&yearMonth=2026-10',
    );
    expect(statement.customerName).toBe('恒信珠宝');
    expect(statement.lines).toEqual([]);
  });

  it('生成本月账单时把空客户留空', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: { createdCount: 0, unpriced: [] },
      }),
    } as Response);

    await generateBilling('2026-10', '');

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/finance/billing-runs');
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      yearMonth: '2026-10',
      customerId: null,
    });
  });
});
