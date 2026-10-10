<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  type ErrorSummary,
  type OperationsCenterSnapshot,
  type OperationsServiceStatus,
} from '../../data/operationsCenterData';
import type { Tone } from '../../data/m1PrototypeData';
import { fetchOperationsCenterSnapshot } from '../../services/operationsCenterApi';

const snapshot = ref<OperationsCenterSnapshot>({
  generatedAt: '',
  summary: { overallStatus: '未知', serviceCount: 0, degradedServices: 0, openErrors: 0, criticalErrors: 0, openAlerts: 0, criticalAlerts: 0, backupStatus: '未知', uptimeMinutes: 0 },
  services: [], runtimeMetrics: [], errorSummaries: [], recentLogs: [], backupStatuses: [], restoreDrillRecords: [], alerts: [], checklist: [], hardwareNodes: [], storageVolumes: [],
});
const hasSnapshot = ref(false);
const loading = ref(false);
const loadingError = ref('');

const activeService = computed(
  () => snapshot.value.services.find((service) => service.tone === 'red' || service.tone === 'amber') ?? snapshot.value.services[0],
);

const activeError = computed(
  () => snapshot.value.errorSummaries.find((error) => error.tone === 'red' || error.tone === 'amber') ?? snapshot.value.errorSummaries[0],
);

const activeAlert = computed(
  () => snapshot.value.alerts.find((alert) => alert.tone === 'red' || alert.tone === 'amber') ?? snapshot.value.alerts[0],
);

const hardwareNodes = computed(() => snapshot.value.hardwareNodes ?? []);
const storageVolumes = computed(() => snapshot.value.storageVolumes ?? []);
const onlineHardwareCount = computed(() => hardwareNodes.value.filter((node) => node.status === '在线').length);

const heroStats = computed(() => [
  {
    label: '服务',
    value: snapshot.value.summary.serviceCount,
    unit: '项',
    detail: `${snapshot.value.summary.degradedServices} 项需关注`,
    tone: snapshot.value.summary.degradedServices > 0 ? 'amber' as Tone : 'green' as Tone,
  },
  {
    label: '报错',
    value: snapshot.value.summary.openErrors,
    unit: '条',
    detail: `${snapshot.value.summary.criticalErrors} 条 P0/P1`,
    tone: snapshot.value.summary.criticalErrors > 0 ? 'red' as Tone : 'amber' as Tone,
  },
  {
    label: '告警',
    value: snapshot.value.summary.openAlerts,
    unit: '项',
    detail: `${snapshot.value.summary.criticalAlerts} 项 P1`,
    tone: snapshot.value.summary.criticalAlerts > 0 ? 'red' as Tone : snapshot.value.summary.openAlerts > 0 ? 'amber' as Tone : 'green' as Tone,
  },
  {
    label: '备份',
    value: snapshot.value.summary.backupStatus,
    unit: '',
    detail: '只展示状态，不触发命令',
    tone: snapshot.value.summary.backupStatus.includes('正常') ? 'green' as Tone : 'amber' as Tone,
  },
  {
    label: '运行',
    value: snapshot.value.summary.uptimeMinutes,
    unit: '分钟',
    detail: snapshot.value.generatedAt,
    tone: 'blue' as Tone,
  },
]);

onMounted(async () => {
  await loadOperationsCenter();
});

async function loadOperationsCenter() {
  loading.value = true;
  loadingError.value = '';

  try {
    snapshot.value = await fetchOperationsCenterSnapshot();
    hasSnapshot.value = true;
  } catch (error) {
    loadingError.value = error instanceof Error ? error.message : '运维中心接口不可用';

  } finally {
    loading.value = false;
  }
}

function serviceTone(service: OperationsServiceStatus): Tone {
  return service.tone;
}

function errorTone(error: ErrorSummary): Tone {
  return error.tone;
}
</script>

<template>
  <div class="operations-center-page page-stack">
    <section class="operations-command-surface" aria-label="运维中心总览">
      <div class="operations-command-main">
        <div class="console-title-line">
          <span class="eyebrow">OPERATIONS CENTER</span>
        </div>
        <div class="operations-title-row">
          <div>
            <h2>运维中心</h2>
            <p>查看硬件是否在线、磁盘和附件目录占用、服务状态、日志、报错和数据库备份。</p>
          </div>
          <button class="secondary-button" type="button" :disabled="loading" @click="loadOperationsCenter">
            {{ loading ? '刷新中' : '刷新状态' }}
          </button>
        </div>

        <div v-if="hasSnapshot" class="operations-hero-grid" aria-label="运维关键指标">
          <article v-for="item in heroStats" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>
      </div>

      <aside class="operations-focus-panel">
        <div>
          <span class="eyebrow">FOCUS</span>
          <strong>{{ activeService?.name }}</strong>
          <StatusBadge v-if="activeService" :label="activeService.status" :tone="serviceTone(activeService)" />
        </div>
        <p>{{ activeService?.detail }}</p>
        <dl v-if="activeAlert || activeError" class="operations-focus-grid">
          <dt>当前告警</dt>
          <dd>{{ activeAlert?.title ?? `${activeError?.module} / ${activeError?.severity}` }}</dd>
          <dt>责任人</dt>
          <dd>{{ activeAlert?.owner ?? activeError?.owner }}</dd>
          <dt>动作</dt>
          <dd>{{ activeAlert?.nextAction ?? activeError?.nextAction }}</dd>
        </dl>
      </aside>
    </section>

    <section v-if="hasSnapshot" class="operations-grid" aria-label="硬件在线和存储占用">
      <div class="operations-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">HARDWARE</span>
            <h2>硬件在线</h2>
          </div>
          <StatusBadge
            :label="hardwareNodes.length ? `${onlineHardwareCount} / ${hardwareNodes.length} 在线` : '未接入'"
            :tone="hardwareNodes.length && onlineHardwareCount === hardwareNodes.length ? 'green' : 'amber'"
          />
        </div>
        <p v-if="hardwareNodes.length === 0" class="empty-hint">还没有接入现场设备或网关设备。</p>
        <div v-else class="service-status-grid">
          <article v-for="node in hardwareNodes" :key="node.code" :class="`tone-${node.tone}`">
            <div>
              <strong>{{ node.name }}</strong>
              <span>{{ node.code }} · {{ node.scene }}</span>
            </div>
            <StatusBadge :label="node.status" :tone="node.tone" />
            <small>{{ node.protocol }}。{{ node.detail }}</small>
            <em>{{ node.checkedAt }}</em>
          </article>
        </div>
      </div>

      <aside class="operations-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">STORAGE</span>
            <h2>存储占用</h2>
          </div>
        </div>
        <div class="backup-status-list">
          <article v-for="volume in storageVolumes" :key="volume.code" :class="`tone-${volume.tone}`">
            <div>
              <strong>{{ volume.name }}</strong>
              <small>{{ volume.code }}</small>
              <span>{{ volume.path }}</span>
            </div>
            <StatusBadge :label="`${volume.usedPercent}%`" :tone="volume.tone" />
            <dl>
              <dt>已用</dt>
              <dd>{{ volume.used }}</dd>
              <dt>容量</dt>
              <dd>{{ volume.capacity }}</dd>
              <dt>说明</dt>
              <dd>{{ volume.detail }}</dd>
            </dl>
            <div class="storage-meter" aria-hidden="true">
              <span :style="{ width: `${Math.min(volume.usedPercent, 100)}%` }" />
            </div>
          </article>
        </div>
      </aside>
    </section>

    <p v-if="loadingError" class="operation-feedback danger">
      <strong>状态读取失败</strong>
      <span>{{ loadingError }}</span>
      <small>{{ hasSnapshot ? `保留最后一次真实采集结果：${snapshot.generatedAt}` : '尚未取得真实运行状态' }}</small>
    </p>

    <section v-if="hasSnapshot" class="operations-grid alerts" aria-label="告警汇总和运行状态">
      <div class="operations-panel alert-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">ALERTS</span>
            <h2>告警汇总</h2>
          </div>
          <StatusBadge :label="`${snapshot.summary.openAlerts} 项`" :tone="snapshot.summary.criticalAlerts > 0 ? 'red' : snapshot.summary.openAlerts > 0 ? 'amber' : 'green'" />
        </div>
        <div class="ops-alert-list">
          <article v-for="alert in snapshot.alerts" :key="alert.code" :class="`tone-${alert.tone}`">
            <div>
              <strong>{{ alert.title }}</strong>
              <small>{{ alert.code }} / {{ alert.source }} / {{ alert.status }}</small>
            </div>
            <StatusBadge :label="alert.severity" :tone="alert.tone" />
            <dl>
              <dt>责任人</dt>
              <dd>{{ alert.owner }}</dd>
              <dt>触发时间</dt>
              <dd>{{ alert.raisedAt }}</dd>
              <dt>证据</dt>
              <dd>{{ alert.evidence }}</dd>
              <dt>下一步</dt>
              <dd>{{ alert.nextAction }}</dd>
            </dl>
          </article>
        </div>
      </div>

      <aside class="operations-panel metric-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">SERVER METRICS</span>
            <h2>服务器运行指标</h2>
          </div>
        </div>
        <div class="runtime-metric-grid">
          <article v-for="metric in snapshot.runtimeMetrics" :key="metric.code" :class="`tone-${metric.tone}`">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}<small>{{ metric.unit }}</small></strong>
            <em>{{ metric.detail }}</em>
          </article>
        </div>
      </aside>
    </section>

    <section v-if="hasSnapshot" class="operations-grid" aria-label="运行状态">
      <div class="operations-panel service-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">SERVICE STATUS</span>
            <h2>前后端/数据库运行情况</h2>
          </div>
          <StatusBadge :label="snapshot.summary.overallStatus" :tone="snapshot.summary.degradedServices > 0 ? 'amber' : 'green'" />
        </div>
        <div class="service-status-grid">
          <article v-for="service in snapshot.services" :key="service.code" :class="`tone-${service.tone}`">
            <div>
              <strong>{{ service.name }}</strong>
              <span>{{ service.endpoint }}</span>
            </div>
            <StatusBadge :label="service.status" :tone="serviceTone(service)" />
            <small>{{ service.detail }}</small>
            <em>{{ service.owner }} / {{ service.checkedAt }}</em>
          </article>
        </div>
      </div>
    </section>

    <section v-if="hasSnapshot" class="operations-grid lower" aria-label="报错、日志和备份">
      <div class="operations-panel error-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">ERROR SUMMARY</span>
            <h2>报错汇总</h2>
          </div>
          <StatusBadge :label="`${snapshot.summary.openErrors} 条`" tone="amber" />
        </div>
        <div class="ops-table-wrap">
          <table class="ops-data-table">
            <thead>
              <tr>
                <th>模块</th>
                <th>等级</th>
                <th>次数</th>
                <th>最近发生</th>
                <th>责任人</th>
                <th>下一步</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="error in snapshot.errorSummaries" :key="error.code">
                <td>
                  <span class="cell-stack">
                    <b>{{ error.module }}</b>
                    <small>{{ error.code }}</small>
                  </span>
                </td>
                <td><StatusBadge :label="error.severity" :tone="errorTone(error)" /></td>
                <td>{{ error.count }}</td>
                <td>{{ error.latest }}</td>
                <td>{{ error.owner }}</td>
                <td>{{ error.nextAction }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside class="operations-panel log-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">RECENT LOGS</span>
            <h2>近期日志</h2>
          </div>
          <StatusBadge label="只读" tone="blue" />
        </div>
        <div class="log-stream">
          <article v-for="entry in snapshot.recentLogs" :key="`${entry.traceId}-${entry.time}`" :class="`tone-${entry.tone}`">
            <time>{{ entry.time }}</time>
            <StatusBadge :label="entry.level" :tone="entry.tone" />
            <strong>{{ entry.source }}</strong>
            <span>{{ entry.message }}</span>
            <small>{{ entry.traceId }}</small>
          </article>
        </div>
      </aside>
    </section>

    <section v-if="hasSnapshot" class="operations-grid backup" aria-label="备份与上线检查">
      <div class="operations-panel backup-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">DATABASE BACKUP</span>
            <h2>数据库备份情况</h2>
          </div>
          <StatusBadge :label="snapshot.summary.backupStatus" :tone="snapshot.summary.backupStatus.includes('正常') ? 'green' : 'amber'" />
        </div>
        <div class="backup-status-list">
          <article v-for="backup in snapshot.backupStatuses" :key="backup.code" :class="`tone-${backup.tone}`">
            <div>
              <strong>{{ backup.target }}</strong>
              <small>{{ backup.code }}</small>
              <span>{{ backup.storage }}</span>
            </div>
            <StatusBadge :label="backup.status" :tone="backup.tone" />
            <dl>
              <dt>最近成功</dt>
              <dd>{{ backup.lastSuccessAt }}</dd>
              <dt>下次应完成</dt>
              <dd>{{ backup.nextDueAt }}</dd>
              <dt>文件大小</dt>
              <dd>{{ backup.fileSize }}</dd>
              <dt>SHA-256</dt>
              <dd class="checksum-value">{{ backup.checksum }}</dd>
              <dt>说明</dt>
              <dd>{{ backup.detail }}</dd>
            </dl>
          </article>
        </div>

        <div class="restore-drill-section">
          <div class="section-heading compact">
            <div>
              <span class="eyebrow">RESTORE DRILL</span>
              <h3>结构化恢复演练记录</h3>
            </div>
            <StatusBadge :label="`${snapshot.restoreDrillRecords.length} 条`" :tone="snapshot.restoreDrillRecords.length > 0 ? 'green' : 'amber'" />
          </div>

          <div v-if="snapshot.restoreDrillRecords.length > 0" class="restore-drill-records">
            <article
              v-for="record in snapshot.restoreDrillRecords"
              :key="record.code"
              :class="`tone-${record.tone}`"
            >
              <div class="restore-drill-title">
                <div>
                  <strong>{{ record.restoreDatabase }}</strong>
                  <small>{{ record.code }}</small>
                </div>
                <StatusBadge :label="record.status" :tone="record.tone" />
              </div>
              <dl>
                <dt>演练时间</dt>
                <dd>{{ record.checkedAt }}</dd>
                <dt>恢复表数</dt>
                <dd>{{ record.restoredTableCount }} / {{ record.minimumTableCount }}</dd>
                <dt>耗时</dt>
                <dd>{{ record.durationSeconds }} 秒</dd>
                <dt>操作者</dt>
                <dd>{{ record.operator }}</dd>
                <dt>临时库保留</dt>
                <dd>{{ record.keepDatabase ? '是' : '否' }}</dd>
                <dt>备份文件</dt>
                <dd>{{ record.backupFile }}</dd>
                <dt>备份 SHA-256</dt>
                <dd class="checksum-value">{{ record.backupChecksum }}</dd>
                <dt>证据文件</dt>
                <dd>{{ record.evidenceFile }}</dd>
              </dl>
              <div class="restore-drill-checks">
                <span v-for="check in record.checks" :key="`${record.code}-${check.name}`" :class="`tone-${check.tone}`">
                  <b>{{ check.name }}</b>
                  <em>{{ check.detail }}</em>
                </span>
              </div>
            </article>
          </div>
          <p v-else class="restore-drill-empty">尚未发现 `verify-restore-*.json`，运维中心会继续使用旧版标记文件展示演练可见性。</p>
        </div>
      </div>

      <aside class="operations-panel checklist-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">RUNBOOK</span>
            <h2>运维接入清单</h2>
          </div>
        </div>
        <div class="ops-checklist">
          <article v-for="item in snapshot.checklist" :key="item.code" :class="`tone-${item.tone}`">
            <StatusBadge :label="item.status" :tone="item.tone" />
            <div>
              <strong>{{ item.item }}</strong>
              <span>{{ item.evidence }}</span>
              <small>{{ item.code }} / {{ item.owner }}</small>
            </div>
          </article>
        </div>
      </aside>
    </section>

  </div>
</template>
