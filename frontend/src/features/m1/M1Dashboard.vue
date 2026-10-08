<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import type { InboundOrder, QualityCase, Tone, WorkOrder } from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  fetchProductionWorkspace,
  fetchQualityWorkspace,
} from '../../services/businessWorkspaceApi';

type DeskTab = 'inbound' | 'production' | 'quality' | 'inventory' | 'approvals';

type DispatchRow = {
  code: string;
  type: string;
  object: string;
  material: string;
  node: string;
  owner: string;
  action: string;
  tab: DeskTab;
  priority: string;
  tone: Tone;
};

const traceQuery = inject<Ref<string>>('zhubao-trace-query', ref(''));
const openTab = inject<(tab: DeskTab) => void>('zhubao-open-tab', () => {});
const selectedCode = ref('');
const dispatchRows = ref<DispatchRow[]>([]);
const loadError = ref('');

onMounted(() => {
  void loadDispatchRows();
});

async function loadDispatchRows() {
  loadError.value = '';
  const errors: string[] = [];
  const rows: DispatchRow[] = [];

  try {
    const inbound = await fetchInboundWorkspace();
    rows.push(...inbound.orders.map(inboundRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '来料单据加载失败');
  }

  try {
    const production = await fetchProductionWorkspace();
    rows.push(...production.workOrders.map(workOrderRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '工单加载失败');
  }

  try {
    const quality = await fetchQualityWorkspace();
    rows.push(...quality.cases.map(qualityRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '质检单加载失败');
  }

  dispatchRows.value = rows;
  if (!rows.some((row) => row.code === selectedCode.value)) {
    selectedCode.value = rows[0]?.code ?? '';
  }
  if (rows.length === 0) {
    loadError.value = errors[0] ?? '接口没有返回待办单据';
  } else if (errors.length > 0) {
    loadError.value = errors.join('；');
  }
}

function inboundRow(order: InboundOrder): DispatchRow {
  const blocked = order.video === '失败' || order.status.includes('异常');
  return {
    code: order.code,
    type: blocked ? '异常' : '母单',
    object: order.customer,
    material: order.material,
    node: order.status,
    owner: order.receiverName || '收货',
    action: '去收货',
    tab: 'inbound',
    priority: blocked ? 'P0' : 'P1',
    tone: inboundTone(order.status, order.video),
  };
}

function workOrderRow(order: WorkOrder): DispatchRow {
  const blocked = order.status.includes('称重') || order.status.includes('阻断');
  return {
    code: order.code,
    type: '工单',
    object: order.customer,
    material: order.material,
    node: order.process,
    owner: order.assignee,
    action: '去报工',
    tab: 'production',
    priority: blocked ? 'P0' : 'P2',
    tone: workOrderTone(order.status),
  };
}

function qualityRow(item: QualityCase): DispatchRow {
  return {
    code: item.code,
    type: '质检',
    object: item.process,
    material: item.defect,
    node: item.status,
    owner: item.inspector,
    action: '去质检',
    tab: 'quality',
    priority: item.status.includes('待') ? 'P1' : 'P2',
    tone: qualityTone(item.status),
  };
}

const visibleRows = computed(() => {
  const query = traceQuery.value.trim().toLowerCase();
  if (!query) {
    return dispatchRows.value;
  }
  return dispatchRows.value.filter((row) =>
    `${row.code} ${row.type} ${row.object} ${row.material} ${row.owner} ${row.node}`.toLowerCase().includes(query),
  );
});

const selectedRow = computed(() =>
  visibleRows.value.find((row) => row.code === selectedCode.value) ?? visibleRows.value[0],
);

const commandStats = computed(() => [
  { label: '来料待处理', value: countType('母单'), unit: '单', tone: 'amber' as Tone },
  { label: '在制工单', value: countType('工单'), unit: '张', tone: 'blue' as Tone },
  { label: '质检队列', value: countType('质检'), unit: '批', tone: 'amber' as Tone },
  { label: '异常', value: countType('异常'), unit: '项', tone: 'red' as Tone },
]);

function countType(type: string) {
  return visibleRows.value.filter((row) => row.type === type).length;
}

function selectRow(code: string) {
  selectedCode.value = code;
}

function openSelected() {
  if (selectedRow.value) {
    openTab(selectedRow.value.tab);
  }
}

function workOrderTone(status: string): Tone {
  if (status.includes('称重') || status.includes('阻断')) return 'red';
  if (status.includes('待') || status.includes('返工')) return 'amber';
  return 'green';
}

function inboundTone(status: string, video: string): Tone {
  if (video === '失败' || status.includes('异常')) return 'red';
  if (status.includes('待') || video === '截取中') return 'amber';
  return 'green';
}

function qualityTone(status: string): Tone {
  if (status.includes('待')) return 'amber';
  if (status.includes('完成')) return 'green';
  return 'blue';
}
</script>

<template>
  <div class="command-center-v3 page-stack">
    <section class="mission-kpi-grid lean-kpis" aria-label="今日待办">
      <article v-for="item in commandStats" :key="item.label" :class="`tone-${item.tone}`">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
      </article>
    </section>

    <section class="production-desk-grid command-grid-v2 lean-desk" aria-label="今日单据和当前处理单">
      <main class="dispatch-console-v3 dispatch-console-v2">
        <div class="floor-toolbar">
          <h2>今日待办</h2>
        </div>
        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="erp-table-wrap command-table-wrap">
          <table class="erp-table floor-dispatch-table command-table-v2">
            <thead>
              <tr>
                <th>优先级</th>
                <th>单号</th>
                <th>类型</th>
                <th>业务对象</th>
                <th>节点</th>
                <th>责任人</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in visibleRows"
                :key="`${row.type}-${row.code}`"
                :class="{ 'is-selected': selectedRow?.code === row.code }"
                @click="selectRow(row.code)"
              >
                <td><StatusBadge :label="row.priority" :tone="row.tone" /></td>
                <td><strong>{{ row.code }}</strong></td>
                <td>{{ row.type }}</td>
                <td>
                  <span class="cell-stack">
                    <b>{{ row.object }}</b>
                    <small>{{ row.material }}</small>
                  </span>
                </td>
                <td>{{ row.node }}</td>
                <td>{{ row.owner }}</td>
                <td>
                  <button class="table-action" type="button" @click.stop="selectRow(row.code); openSelected()">
                    {{ row.action }}
                  </button>
                </td>
              </tr>
              <tr v-if="visibleRows.length === 0">
                <td colspan="7">{{ loadError ? '暂无待办单据' : '没有匹配的单据' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </main>

      <aside class="active-record-panel">
        <div class="panel-title-row">
          <h2>当前处理单</h2>
          <StatusBadge v-if="selectedRow" :label="selectedRow.priority" :tone="selectedRow.tone" />
        </div>
        <template v-if="selectedRow">
          <div class="active-record-code">
            <strong>{{ selectedRow.code }}</strong>
            <span>{{ selectedRow.object }} / {{ selectedRow.material }}</span>
          </div>
          <dl class="detail-grid active-record-grid">
            <dt>类型</dt>
            <dd>{{ selectedRow.type }}</dd>
            <dt>节点</dt>
            <dd>{{ selectedRow.node }}</dd>
            <dt>责任人</dt>
            <dd>{{ selectedRow.owner }}</dd>
          </dl>
          <div class="active-action-grid">
            <button class="primary-button" type="button" @click="openSelected">{{ selectedRow.action }}</button>
          </div>
        </template>
        <p v-else class="empty-hint">暂无待办单据</p>
      </aside>
    </section>
  </div>
</template>
