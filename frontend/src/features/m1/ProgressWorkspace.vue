<script setup lang="ts">
import { inject, onMounted, ref } from 'vue';
import { fetchProgress, type ProgressRow } from '../../services/progressApi';

type DeskTab = 'inbound' | 'production' | 'quality';

const openTab = inject<(tab: DeskTab) => void>('zhubao-open-tab', () => {});
const traceQuery = inject<{ value: string }>('zhubao-trace-query', ref(''));
const rows = ref<ProgressRow[]>([]);
const loadError = ref('');
const loading = ref(false);
const customer = ref('');
const processName = ref('');
const dueDate = ref('');
const status = ref('');

onMounted(() => {
  void loadRows();
});

async function loadRows() {
  loading.value = true;
  loadError.value = '';
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
    loadError.value = error instanceof Error ? error.message : '进度加载失败';
  } finally {
    loading.value = false;
  }
}

function openRow(row: ProgressRow) {
  traceQuery.value = row.workOrderCode || row.motherOrderCode;
  openTab(row.openTab);
}
</script>

<template>
  <section class="section-block progress-board">
    <header>
      <strong>进度一览</strong>
      <p>在制单据来自来料、子单、工序交付和质检。这里不显示金额。</p>
    </header>
    <form class="finance-filters" @submit.prevent="loadRows">
      <label>
        客户
        <input v-model="customer" aria-label="进度客户" placeholder="客户编号或名称" />
      </label>
      <label>
        工序
        <select v-model="processName" aria-label="进度工序">
          <option value="">全部</option>
          <option>来料</option>
          <option>雕刻</option>
          <option>镶嵌</option>
          <option>抛光</option>
        </select>
      </label>
      <label>
        期限
        <input v-model="dueDate" aria-label="进度期限" placeholder="2026-10-31" />
      </label>
      <label>
        状态
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
      <button class="primary-button" type="submit" :disabled="loading">筛选</button>
    </form>
    <p v-if="loadError" class="operation-feedback" :class="{ danger: rows.length === 0 && loadError !== '暂无在制单据' }">
      {{ loadError }}
    </p>
    <table v-if="rows.length">
      <thead>
        <tr>
          <th>客户</th>
          <th>母单</th>
          <th>子单</th>
          <th>当前工序</th>
          <th>期限</th>
          <th>超期</th>
          <th>质检</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="`${row.motherOrderCode}|${row.workOrderCode}`">
          <td>{{ row.customerName || row.customerId }}</td>
          <td>{{ row.motherOrderCode }}</td>
          <td>{{ row.workOrderCode === row.motherOrderCode ? '—' : row.workOrderCode }}</td>
          <td>{{ row.currentProcess }}</td>
          <td>{{ row.dueDate || '—' }}</td>
          <td>{{ row.overdue ? '超期' : '未超期' }}</td>
          <td>{{ row.qualityOutcome }}</td>
          <td>
            <button class="table-action" type="button" @click="openRow(row)">{{ row.status }}</button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
