<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  operationsCenterFallback,
  type ErrorSummary,
  type OperationsCenterSnapshot,
  type OperationsServiceStatus,
} from '../../data/operationsCenterData';
import type { Tone } from '../../data/m1PrototypeData';
import { fetchOperationsCenterSnapshot } from '../../services/operationsCenterApi';

const snapshot = ref<OperationsCenterSnapshot>(operationsCenterFallback);
const loading = ref(false);
const loadingError = ref('');

const activeService = computed(
  () => snapshot.value.services.find((service) => service.tone === 'red' || service.tone === 'amber') ?? snapshot.value.services[0],
);

const activeError = computed(
  () => snapshot.value.errorSummaries.find((error) => error.tone === 'red' || error.tone === 'amber') ?? snapshot.value.errorSummaries[0],
);

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
  } catch (error) {
    loadingError.value = error instanceof Error ? error.message : '运维中心接口不可用，已使用本地评审样例';
    snapshot.value = operationsCenterFallback;
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
          <StatusBadge label="M3.60 恢复演练可见性" tone="green" />
        </div>
        <div class="operations-title-row">
          <div>
            <h2>运维中心</h2>
            <p>集中查看日志、服务器运行指标、报错汇总、数据库备份情况，以及数据库、前端、后端和软硬件联动的运行状态。</p>
          </div>
          <button class="secondary-button" type="button" :disabled="loading" @click="loadOperationsCenter">
            {{ loading ? '刷新中' : '刷新状态' }}
          </button>
        </div>

        <div class="operations-hero-grid" aria-label="运维关键指标">
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
        <dl v-if="activeError" class="operations-focus-grid">
          <dt>当前错误</dt>
          <dd>{{ activeError.module }} / {{ activeError.severity }}</dd>
          <dt>责任人</dt>
          <dd>{{ activeError.owner }}</dd>
          <dt>动作</dt>
          <dd>{{ activeError.nextAction }}</dd>
        </dl>
      </aside>
    </section>

    <p v-if="loadingError" class="operation-feedback danger">
      <strong>接口兜底</strong>
      <span>{{ loadingError }}</span>
    </p>

    <section class="operations-grid" aria-label="运行状态和服务器指标">
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

    <section class="operations-grid lower" aria-label="报错、日志和备份">
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

    <section class="operations-grid backup" aria-label="备份与上线检查">
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
      </div>

      <aside class="operations-panel checklist-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">RUNBOOK</span>
            <h2>运维接入清单</h2>
          </div>
          <StatusBadge label="M3.60" tone="green" />
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

    <section class="operations-source-note" aria-label="运维中心设计依据">
      <article>
        <strong>设计边界</strong>
        <span>当前运维中心复用 Spring Boot Actuator/Micrometer 的健康和指标思路，但只开放业务聚合 API；备份只读状态，不从页面执行 `mysqldump`。</span>
      </article>
      <article>
        <strong>下一步</strong>
        <span>接入真实登录权限、PWA 运行态细节、日志平台、恢复演练记录结构化入库和告警通知。</span>
      </article>
    </section>
  </div>
</template>
