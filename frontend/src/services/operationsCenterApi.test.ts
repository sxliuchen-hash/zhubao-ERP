import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchOperationsCenterSnapshot } from './operationsCenterApi';

describe('operationsCenterApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches the operations center snapshot and maps tone values', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: {
          generatedAt: '2026-07-07 08:00:00',
          summary: {
            overallStatus: 'ATTENTION',
            serviceCount: 2,
            degradedServices: 1,
            openErrors: 1,
            criticalErrors: 0,
            backupStatus: '需配置',
            uptimeMinutes: 12,
          },
          services: [{
            code: 'BACKEND',
            name: '后端 API',
            status: 'ONLINE',
            endpoint: '/api/operations/center',
            detail: 'ok',
            owner: '平台负责人',
            checkedAt: '2026-07-07 08:00:00',
            tone: 'green',
          }],
          runtimeMetrics: [{
            code: 'JVM_MEMORY',
            label: 'JVM 内存',
            value: '40',
            unit: '%',
            detail: '400 MiB',
            tone: 'green',
          }],
          errorSummaries: [{
            code: 'ERR-DB-001',
            module: '数据库备份',
            severity: 'P1',
            count: 1,
            latest: '2026-07-07 07:00:00',
            owner: '运维负责人',
            nextAction: '配置备份目录',
            tone: 'amber',
          }],
          recentLogs: [{
            time: '2026-07-07 07:50:00',
            level: 'INFO',
            source: 'business-operation',
            message: 'ok',
            traceId: 'TRACE-OPS-001',
            tone: 'green',
          }],
          backupStatuses: [{
            code: 'DB-BACKUP-POC',
            target: 'MySQL 逻辑备份',
            status: '未接入真实目录',
            lastSuccessAt: '评审样例',
            nextDueAt: '2026-07-07 23:30:00',
            storage: 'not configured',
            detail: '配置备份目录',
            tone: 'amber',
          }],
          checklist: [{
            code: 'OPS-CHK-001',
            item: '后端 API 探活',
            status: '已接入',
            evidence: '/api/operations/center',
            owner: '平台负责人',
            tone: 'green',
          }],
        },
      }),
    } as Response);

    const snapshot = await fetchOperationsCenterSnapshot();

    expect(snapshot.services[0].tone).toBe('green');
    expect(snapshot.errorSummaries[0].tone).toBe('amber');
    expect(snapshot.backupStatuses[0].code).toBe('DB-BACKUP-POC');
    expect(fetchMock).toHaveBeenCalledWith('/api/operations/center');
  });
});
