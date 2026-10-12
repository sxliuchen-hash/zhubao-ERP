import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from './apiClient';
import { fetchFinanceDashboard, fetchFinanceDetails, fetchStatement, generateBilling } from './financeApi';

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

  it('经营总览只发 GET，并带上月份和客户', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ code: 0, message: 'ok', data: { yearMonth: '2026-10' } }),
    } as Response);

    await fetchFinanceDashboard('2026-10', '1008');

    expect(String(fetchMock.mock.calls[0]?.[0])).toBe('/api/finance/dashboard?yearMonth=2026-10&customerId=1008');
    expect(fetchMock.mock.calls[0]?.[1]?.method ?? 'GET').not.toBe('POST');
  });

  it('明细查询使用种类、月份、客户和分页', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ code: 0, message: 'ok', data: { kind: 'RECEIVABLE', totalAmount: 80, rows: [] } }),
    } as Response);

    await fetchFinanceDetails({ kind: 'RECEIVABLE', yearMonth: '2026-08', customerId: '42', page: 2, size: 20 });

    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      '/api/finance/dashboard/details?kind=RECEIVABLE&yearMonth=2026-08&customerId=42&page=2&size=20',
    );
  });

  it('无权限时保留 40301，不当成普通失败', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 403,
      json: async () => ({ code: 40301, message: '无权查看该客户的财务', data: null }),
    } as Response);

    const error = await fetchFinanceDashboard('2026-10', '9').catch((reason: unknown) => reason);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).toMatchObject({
      message: '无权查看该客户的财务',
      status: 403,
      code: 40301,
    });
  });
});
