import { afterEach, describe, expect, it, vi } from 'vitest';
import { correctMaintainedOrder, type MaintainedOrder } from './orderMaintenanceApi';

describe('orderMaintenanceApi', () => {
  afterEach(() => vi.restoreAllMocks());

  it('母单和子单保存都带原快照版本及请求编号', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data: {} }),
    } as Response);
    const base: MaintainedOrder = {
      targetType: 'MOTHER', code: 'MD-1', motherOrderCode: 'MD-1', customerName: '张三',
      materialCode: 'AU', goodsName: '戒指', requirement: '抛光', dueDate: '2026-11-01',
      quantity: '2', weight: '10', processes: '', copies: null, stationId: '', queueId: '',
      recycled: false, recycleReason: '', adjustments: [], revision: 'snapshot-hash',
    };

    await correctMaintainedOrder(base, '核对改正', 'req-maint-0001');
    await correctMaintainedOrder({ ...base, targetType: 'WORK_ORDER', code: 'ZD-1', processes: '镶嵌' },
      '核对改正', 'req-maint-0002');

    const motherBody = JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string);
    const childBody = JSON.parse((fetchMock.mock.calls[1][1] as RequestInit).body as string);
    expect(motherBody).toMatchObject({ requestId: 'req-maint-0001', expectedRevision: 'snapshot-hash' });
    expect(childBody).toMatchObject({ requestId: 'req-maint-0002', expectedRevision: 'snapshot-hash' });
  });

  it('打印任务保存也带请求编号和旧快照版本', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data: {} }),
    } as Response);
    await correctMaintainedOrder({
      targetType: 'PRINT_TARGET', code: 'J-1', motherOrderCode: 'MD-1', customerName: '',
      materialCode: '', goodsName: '', requirement: '', dueDate: '', quantity: '', weight: '',
      processes: '', copies: 2, stationId: 'A', queueId: 'Q1', recycled: false,
      recycleReason: '', adjustments: [], revision: 'print-snapshot',
    }, '改派核对', 'req-print-1');
    const body = JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string);
    expect(body).toMatchObject({ requestId: 'req-print-1', expectedRevision: 'print-snapshot' });
  });
});
