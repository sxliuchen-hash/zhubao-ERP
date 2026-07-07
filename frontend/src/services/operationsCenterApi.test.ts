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
            openAlerts: 1,
            criticalAlerts: 0,
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
            fileSize: '未计算',
            checksum: '未计算',
            detail: '配置备份目录',
            tone: 'amber',
          }],
          restoreDrillRecords: [{
            code: 'RESTORE-DRILL-VERIFY-RESTORE-20260707-090000',
            status: 'passed',
            checkedAt: '2026-07-07T09:05:30+08:00',
            backupFile: '/backups/zhubao.sql',
            backupChecksum: 'sha256:sample',
            restoreDatabase: 'zhubao_restore_check_20260707_090000',
            minimumTableCount: 8,
            restoredTableCount: 12,
            operator: 'ops-user',
            durationSeconds: 36,
            keepDatabase: false,
            evidenceFile: '/backups/verify-restore-20260707_090000.json',
            detail: 'Non-destructive restore verification passed.',
            tone: 'green',
            checks: [{
              name: 'table_count',
              status: 'passed',
              detail: 'Restored 12 tables.',
              tone: 'green',
            }],
          }],
          alerts: [{
            code: 'OPS-ALERT-BACKUP-DB-BACKUP-POC',
            title: 'MySQL 逻辑备份需处理',
            severity: 'P2',
            source: 'BACKUP',
            status: 'OPEN',
            owner: '运维负责人',
            raisedAt: '2026-07-07 08:00:00',
            nextAction: '执行 backup-mysql 脚本。',
            evidence: '未发现备份目录。',
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
    expect(snapshot.backupStatuses[0].checksum).toBe('未计算');
    expect(snapshot.restoreDrillRecords[0].restoreDatabase).toBe('zhubao_restore_check_20260707_090000');
    expect(snapshot.restoreDrillRecords[0].checks[0].tone).toBe('green');
    expect(snapshot.alerts[0].code).toBe('OPS-ALERT-BACKUP-DB-BACKUP-POC');
    expect(snapshot.alerts[0].tone).toBe('amber');
    expect(fetchMock).toHaveBeenCalledWith('/api/operations/center');
  });
});
