<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { ApiRequestError } from '../../services/apiClient';
import { fetchProgress, type ProgressRow } from '../../services/progressApi';
import { progressTraceTarget, type TraceTarget } from '../scan/traceTarget';
import type { Tone } from '../../data/m1PrototypeData';

type DeskTab = 'inbound' | 'production' | 'quality';

const openTab = inject<(tab: DeskTab) => void>('zhubao-open-tab', () => {});
const traceTarget = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
let traceToken = 0;
const rows = ref<ProgressRow[]>([]);
const loadError = ref('');
const accessDenied = ref(false);
const loading = ref(false);
const customer = ref('');
const processName = ref('');
const dueDate = ref('');
const status = ref('');
const advancedOpen = ref(false);
const detailOpen = ref(false);
const selectedKey = ref('');

const activeRow = computed(
  () => rows.value.find((row) => rowKey(row) === selectedKey.value) ?? rows.value[0],
);

onMounted(() => {
  void loadRows();
});

function rowKey(row: ProgressRow) {
  return `${row.motherOrderCode}|${row.workOrderCode}`;
}

function denied(error: unknown) {
  return error instanceof ApiRequestError && (error.status === 403 || error.code === 40301);
}

async function loadRows() {
  loading.value = true;
  loadError.value = '';
  accessDenied.value = false;
  try {
    rows.value = await fetchProgress({
      customer: customer.value.trim(),
      process: processName.value.trim(),
      dueDate: dueDate.value.trim(),
      status: status.value.trim(),
    });
    if (rows.value.length === 0) {
      loadError.value = '暂无在制单据';
    }
  } catch (error) {
    rows.value = [];
    accessDenied.value = denied(error);
    loadError.value = error instanceof Error ? error.message : '进度加载失败';
  } finally {
    loading.value = false;
    if (!rows.value.some((row) => rowKey(row) === selectedKey.value)) {
      selectedKey.value = activeRow.value ? rowKey(activeRow.value) : '';
    }
  }
}

function openRow(row: ProgressRow) {
  traceToken += 1;
  traceTarget.value = progressTraceTarget(row, traceToken);
  openTab(row.openTab);
}

function selectRow(row: ProgressRow) {
  selectedKey.value = rowKey(row);
  detailOpen.value = true;
}

function backToList() {
  detailOpen.value = false;
}

function childCode(row: ProgressRow) {
  return row.workOrderCode === row.motherOrderCode ? '—' : row.workOrderCode;
}

function statusTone(row: ProgressRow): Tone {
  if (row.overdue) return 'red';
  if (row.status.includes('待') || row.status.includes('返工')) return 'amber';
  if (row.status.includes('已')) return 'green';
  return 'blue';
}
</script>

<template>
  <div class="progress-board page-stack desk-page" :class="{ 'is-detail': detailOpen }">
    <section class="module-work-grid" aria-label="在制进度">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>进度一览</h2>
          </div>
          <button class="primary-button" type="button" :disabled="loading" @click="loadRows">
            {{ loading ? '筛选中' : '筛选' }}
          </button>
        </div>
        <form class="module-tools finance-filters" @submit.prevent="loadRows">
          <label>
            <span>客户</span>
            <input v-model="customer" aria-label="进度客户" placeholder="客户编号或名称" />
          </label>
          <label>
            <span>工序</span>
            <select v-model="processName" aria-label="进度工序">
              <option value="">全部</option>
              <option>来料</option>
              <option>雕刻</option>
              <option>镶嵌</option>
              <option>抛光</option>
            </select>
          </label>
          <button class="secondary-button" type="button" :aria-expanded="advancedOpen ? 'true' : 'false'" @click="advancedOpen = !advancedOpen">
            {{ advancedOpen ? '收起筛选' : '更多筛选' }}
          </button>
          <label v-if="advancedOpen">
            <span>期限</span>
            <input v-model="dueDate" aria-label="进度期限" placeholder="2026-10-31" />
          </label>
          <label v-if="advancedOpen">
            <span>状态</span>
            <select v-model="status" aria-label="进度状态">
              <option value="">全部</option>
              <option>待拆分</option>
              <option>待开工</option>
              <option>在制</option>
              <option>待质检</option>
              <option>已质检</option>
              <option>返工</option>
            </select>
          </label>
        </form>
        <details class="production-note">
          <summary>进度说明</summary>
          <p>在制单据来自来料、子单、工序交付和质检。这里不显示金额。</p>
        </details>
        <p v-if="loading" class="empty-hint">正在加载进度</p>
        <p v-if="accessDenied" class="operation-feedback danger">无权限</p>
        <p v-if="loadError" class="operation-feedback" :class="{ danger: rows.length === 0 && loadError !== '暂无在制单据' }">
          {{ loadError }}
        </p>
        <div class="module-table-wrap refined-table-wrap desk-table-wrap">
          <table class="module-table progress-table">
            <thead>
              <tr>
                <th>客户</th>
                <th>母单</th>
                <th>子单</th>
                <th>当前工序</th>
                <th>期限</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in rows"
                :key="rowKey(row)"
                :class="{ 'is-selected': activeRow && rowKey(activeRow) === rowKey(row) }"
                :aria-selected="activeRow && rowKey(activeRow) === rowKey(row)"
                @click="selectRow(row)"
              >
                <td>{{ row.customerName || row.customerId }}</td>
                <td class="code">{{ row.motherOrderCode }}</td>
                <td class="code">{{ childCode(row) }}</td>
                <td>{{ row.currentProcess }}</td>
                <td>{{ row.dueDate || '—' }}</td>
                <td>
                  <button class="table-action" type="button" @click.stop="openRow(row)">{{ row.status }}</button>
                </td>
              </tr>
              <tr v-if="!loading && rows.length === 0">
                <td colspan="6">暂无在制单据</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="desk-cards">
          <p v-if="!loading && rows.length === 0" class="empty-hint">暂无在制单据</p>
          <button
            v-for="row in rows"
            :key="rowKey(row)"
            class="desk-card"
            type="button"
            @click="selectRow(row)"
          >
            <span class="desk-card-top">
              <strong class="code">{{ row.motherOrderCode }}</strong>
              <StatusBadge :label="row.overdue ? '超期' : row.status" :tone="statusTone(row)" />
            </span>
            <span>{{ row.customerName || row.customerId }} · {{ row.currentProcess }}</span>
            <span>{{ childCode(row) }} · {{ row.dueDate || '—' }}</span>
          </button>
        </div>
      </div>

      <aside v-if="activeRow" class="module-side-panel">
        <section class="active-object-card tone-blue">
          <div class="panel-title-row">
            <button class="secondary-button detail-back" type="button" @click="backToList">返回列表</button>
            <StatusBadge :label="activeRow.status" :tone="statusTone(activeRow)" />
          </div>
          <strong>{{ activeRow.motherOrderCode }}</strong>
          <dl class="detail-grid">
            <dt>客户</dt>
            <dd>{{ activeRow.customerName || activeRow.customerId }}</dd>
            <dt>子单</dt>
            <dd>{{ childCode(activeRow) }}</dd>
            <dt>当前工序</dt>
            <dd>{{ activeRow.currentProcess }}</dd>
            <dt>期限</dt>
            <dd>{{ activeRow.dueDate || '—' }}</dd>
            <dt>超期</dt>
            <dd>{{ activeRow.overdue ? '超期' : '未超期' }}</dd>
            <dt>质检</dt>
            <dd>{{ activeRow.qualityOutcome }}</dd>
          </dl>
          <div class="detail-actions">
            <button class="primary-button" type="button" @click="openRow(activeRow)">打开{{ activeRow.status }}</button>
          </div>
        </section>
      </aside>
    </section>
  </div>
</template>
