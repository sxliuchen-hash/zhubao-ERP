import type { Tone } from './m1PrototypeData';

export interface OperationsCenterSummary {
  overallStatus: string;
  serviceCount: number;
  degradedServices: number;
  openErrors: number;
  criticalErrors: number;
  backupStatus: string;
  uptimeMinutes: number;
}

export interface OperationsServiceStatus {
  code: string;
  name: string;
  status: string;
  endpoint: string;
  detail: string;
  owner: string;
  checkedAt: string;
  tone: Tone;
}

export interface RuntimeMetric {
  code: string;
  label: string;
  value: string;
  unit: string;
  detail: string;
  tone: Tone;
}

export interface ErrorSummary {
  code: string;
  module: string;
  severity: string;
  count: number;
  latest: string;
  owner: string;
  nextAction: string;
  tone: Tone;
}

export interface RecentLogEntry {
  time: string;
  level: string;
  source: string;
  message: string;
  traceId: string;
  tone: Tone;
}

export interface BackupStatus {
  code: string;
  target: string;
  status: string;
  lastSuccessAt: string;
  nextDueAt: string;
  storage: string;
  fileSize: string;
  checksum: string;
  detail: string;
  tone: Tone;
}

export interface OperationChecklistItem {
  code: string;
  item: string;
  status: string;
  evidence: string;
  owner: string;
  tone: Tone;
}

export interface OperationsCenterSnapshot {
  generatedAt: string;
  summary: OperationsCenterSummary;
  services: OperationsServiceStatus[];
  runtimeMetrics: RuntimeMetric[];
  errorSummaries: ErrorSummary[];
  recentLogs: RecentLogEntry[];
  backupStatuses: BackupStatus[];
  checklist: OperationChecklistItem[];
}

export const operationsCenterFallback: OperationsCenterSnapshot = {
  generatedAt: '2026-07-07 08:00:00',
  summary: {
    overallStatus: 'ATTENTION',
    serviceCount: 6,
    degradedServices: 5,
    openErrors: 3,
    criticalErrors: 0,
    backupStatus: '需配置',
    uptimeMinutes: 128,
  },
  services: [
    {
      code: 'BACKEND',
      name: '后端 API',
      status: 'ONLINE',
      endpoint: '/api/operations/center',
      detail: 'Spring Boot API 运行中，运维中心聚合指标可访问。',
      owner: '平台负责人',
      checkedAt: '2026-07-07 08:00:00',
      tone: 'green',
    },
    {
      code: 'FRONTEND',
      name: '前端 PWA',
      status: 'NOT_CONFIGURED',
      endpoint: 'http://localhost:5173/',
      detail: '评审环境默认未配置前端探活；Docker 部署可设置 ZHUBAO_FRONTEND_PROBE_URL=http://frontend/。',
      owner: '前端负责人',
      checkedAt: '2026-07-07 08:00:00',
      tone: 'amber',
    },
    {
      code: 'PWA_ASSETS',
      name: 'PWA 静态资源',
      status: 'NOT_CONFIGURED',
      endpoint: 'manifest.webmanifest / sw.js',
      detail: '评审环境默认未配置前端探活，无法检查 Manifest 和 Service Worker；Docker 部署后跟随 ZHUBAO_FRONTEND_PROBE_URL 探测。',
      owner: '前端负责人',
      checkedAt: '2026-07-07 08:00:00',
      tone: 'amber',
    },
    {
      code: 'DATABASE',
      name: '数据库连接',
      status: 'MEMORY_MODE',
      endpoint: 'memory repositories',
      detail: '默认内存仓储运行，MySQL profile 未启用。',
      owner: '后端负责人',
      checkedAt: '2026-07-07 08:00:00',
      tone: 'amber',
    },
    {
      code: 'BACKUP',
      name: '数据库备份',
      status: 'NEEDS_CONFIG',
      endpoint: 'zhubao.operations.backup-root 未配置',
      detail: '展示备份状态，不从页面触发备份命令。',
      owner: '运维负责人',
      checkedAt: '2026-07-07 08:00:00',
      tone: 'amber',
    },
    {
      code: 'COS_BACKUP',
      name: '腾讯云 COS 备份',
      status: 'DISABLED',
      endpoint: '未启用',
      detail: '本地部署可选；腾讯云部署应启用并配置 Bucket、Region、Prefix、凭证模式和生命周期/保留策略。',
      owner: '运维负责人',
      checkedAt: '2026-07-07 08:00:00',
      tone: 'blue',
    },
  ],
  runtimeMetrics: [
    { code: 'JVM_MEMORY', label: 'JVM 内存', value: '42', unit: '%', detail: '420 / 1024 MiB', tone: 'green' },
    { code: 'THREADS', label: '线程数', value: '38', unit: '个', detail: '峰值 47', tone: 'blue' },
    { code: 'PROCESSORS', label: 'CPU 核心', value: '8', unit: '核', detail: '系统负载 1.24', tone: 'blue' },
    { code: 'UPTIME', label: '后端运行', value: '128', unit: '分钟', detail: '评审环境样例', tone: 'green' },
  ],
  errorSummaries: [
    {
      code: 'ERR-NVR-001',
      module: '视频截取',
      severity: 'P1',
      count: 1,
      latest: '2026-07-07 07:20:00',
      owner: '现场实施负责人',
      nextAction: '检查 NVR 通道映射，失败允许人工上传视频片段。',
      tone: 'amber',
    },
    {
      code: 'ERR-DB-001',
      module: '数据库备份',
      severity: 'P1',
      count: 1,
      latest: '2026-07-07 07:00:00',
      owner: '运维负责人',
      nextAction: '配置备份目录、checksum、恢复演练记录和告警阈值。',
      tone: 'amber',
    },
    {
      code: 'ERR-AUTH-001',
      module: '登录权限',
      severity: 'P2',
      count: 1,
      latest: '2026-07-07 06:00:00',
      owner: '平台负责人',
      nextAction: '接入真实登录、菜单权限和数据范围过滤。',
      tone: 'blue',
    },
  ],
  recentLogs: [
    {
      time: '2026-07-07 07:53:00',
      level: 'INFO',
      source: 'business-operation',
      message: '生产报工写入库存台账，workOrder=ZD-20260706-021。',
      traceId: 'TRACE-OPS-001',
      tone: 'green',
    },
    {
      time: '2026-07-07 07:54:00',
      level: 'WARN',
      source: 'hardware-video',
      message: 'NVR 视频截取任务失败，已转人工上传兜底。',
      traceId: 'TRACE-OPS-002',
      tone: 'amber',
    },
    {
      time: '2026-07-07 07:56:00',
      level: 'INFO',
      source: 'document-storage',
      message: '附件元数据写入仓储，storage=local://evidence。',
      traceId: 'TRACE-OPS-003',
      tone: 'blue',
    },
  ],
  backupStatuses: [
    {
      code: 'DB-BACKUP-POC',
      target: 'MySQL 逻辑备份',
      status: '未接入真实目录',
      lastSuccessAt: '评审样例：2026-07-06 23:30:00',
      nextDueAt: '2026-07-07 23:30:00',
      storage: 'zhubao.operations.backup-root 未配置',
      fileSize: '未计算',
      checksum: '未计算',
      detail: '生产环境建议记录 mysqldump 输出、备份大小、checksum 和恢复演练结果。',
      tone: 'amber',
    },
    {
      code: 'COS-BACKUP-DISABLED',
      target: '腾讯云 COS 异地备份',
      status: '未启用',
      lastSuccessAt: '由本地备份同步任务记录',
      nextDueAt: '2026-07-07 23:30:00',
      storage: '未启用',
      fileSize: '不适用',
      checksum: '沿用本地备份 SHA-256',
      detail: '运维中心只展示 COS 备份配置状态，不保存密钥、不执行上传或删除命令。',
      tone: 'blue',
    },
  ],
  checklist: [
    {
      code: 'OPS-CHK-001',
      item: '后端 API 探活',
      status: '已接入',
      evidence: '/api/operations/center',
      owner: '平台负责人',
      tone: 'green',
    },
    {
      code: 'OPS-CHK-002',
      item: '数据库连接探测',
      status: 'MEMORY_MODE',
      evidence: '默认内存仓储运行',
      owner: '后端负责人',
      tone: 'amber',
    },
    {
      code: 'OPS-CHK-003',
      item: '前端 URL 探活',
      status: 'NOT_CONFIGURED',
      evidence: 'Docker 可配置 ZHUBAO_FRONTEND_PROBE_URL=http://frontend/',
      owner: '前端负责人',
      tone: 'amber',
    },
    {
      code: 'OPS-CHK-004',
      item: 'PWA 静态资源探活',
      status: 'NOT_CONFIGURED',
      evidence: '需要通过前端探活地址读取 manifest.webmanifest 和 sw.js',
      owner: '前端负责人',
      tone: 'amber',
    },
    {
      code: 'OPS-CHK-005',
      item: '日志来源',
      status: '评审样例',
      evidence: '未配置 zhubao.operations.log-file',
      owner: '运维负责人',
      tone: 'amber',
    },
    {
      code: 'OPS-CHK-007',
      item: '腾讯云 COS 异地备份',
      status: 'DISABLED',
      evidence: '本地部署可选；腾讯云部署应配置 ZHUBAO_COS_BACKUP_*',
      owner: '运维负责人',
      tone: 'blue',
    },
  ],
};
