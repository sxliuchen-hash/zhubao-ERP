<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import DeskInbox from '../workspace/DeskInbox.vue';
import type { TraceTarget } from '../scan/traceTarget';
import type { InboundOrder, QualityCase, Tone, WorkOrder } from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  fetchProductionWorkspace,
  fetchQualityWorkspace,
} from '../../services/businessWorkspaceApi';

type DeskTab = 'inbound' | 'production' | 'quality' | 'progress' | 'label';

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
  quantity: string;
  weight: string;
  reference: string;
  detail: string;
};

const traceQuery = inject<Ref<string>>('zhubao-trace-query', ref(''));
const traceTarget = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
let traceToken = 0;
const openTab = inject<(tab: DeskTab) => void>('zhubao-open-tab', () => {});
const openOrder = inject<((target: Pick<TraceTarget, 'objectType' | 'code'>) => void) | null>('zhubao-open-order', null);
const openOrders = inject<(() => void) | null>('zhubao-open-orders', null);
const openIntake = inject<(() => void) | null>('zhubao-open-intake', null);
const legacyOpen = ref(!openOrders);
function toggleLegacy() {
  legacyOpen.value = !legacyOpen.value;
  if (legacyOpen.value) void loadDispatchRows();
}
const openLabel = inject<(code: string) => void>('zhubao-open-label', () => {});
const visibleTabs = inject<Ref<string[]>>('zhubao-visible-tabs', ref(['inbound', 'production', 'quality', 'progress', 'label']));
const selectedCode = ref('');
const dispatchRows = ref<DispatchRow[]>([]);
const loadError = ref('');
const demoData = ref(false);
const loading = ref(false);
const refreshedAt = ref('');
const canOpen = (tab: DeskTab) => visibleTabs.value.includes(tab);
const quickSession = inject<Ref<{ effectivePermissions?: string[] } | null>>('zhubao-auth-session', ref(null));
const canStartInbound = computed(() => canOpen('inbound') && (!quickSession.value?.effectivePermissions || quickSession.value.effectivePermissions.includes('BUSINESS_INBOUND_CONFIRM')));
const canReadOrders = computed(() => visibleTabs.value.some(tab => ['inbound', 'production', 'progress', 'quality', 'inventory'].includes(tab)));

onMounted(() => {
  if (legacyOpen.value) void loadDispatchRows();
});

async function loadDispatchRows() {
  loadError.value = '';
  demoData.value = false;
  loading.value = true;
  const errors: string[] = [];
  const rows: DispatchRow[] = [];

  if (canOpen('inbound')) try {
    const inbound = await fetchInboundWorkspace();
    demoData.value ||= inbound.dataSource === 'DEMO';
    rows.push(...inbound.orders.map(inboundRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '来料单据加载失败');
  }

  if (canOpen('production')) try {
    const production = await fetchProductionWorkspace();
    demoData.value ||= production.dataSource === 'DEMO';
    rows.push(...production.workOrders.map(workOrderRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '工单加载失败');
  }

  if (canOpen('quality')) try {
    const quality = await fetchQualityWorkspace();
    demoData.value ||= quality.dataSource === 'DEMO';
    rows.push(...quality.cases.map(qualityRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '质检单加载失败');
  }

  dispatchRows.value = rows;
  if (!rows.some((row) => row.code === selectedCode.value)) {
    selectedCode.value = rows[0]?.code ?? '';
  }
  loadError.value = errors.join('；');
  loading.value = false;
  refreshedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function inboundRow(order: InboundOrder): DispatchRow {
  const blocked = order.video === '失败' || order.status.includes('异常');
  return {
    code: order.code,
    type: '母单',
    object: order.customer,
    material: order.material,
    node: order.status,
    owner: order.receiverName || '收货',
    action: '继续处理母单',
    tab: 'inbound',
    priority: blocked ? 'P0' : 'P1',
    tone: inboundTone(order.status, order.video),
    quantity: `${order.qty} 件`,
    weight: order.weight,
    reference: [order.inboundDate, order.expressCompany, order.courierNo].filter(Boolean).join(' · '),
    detail: [order.remark, `附件：${order.video}`].filter(Boolean).join('；'),
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
    quantity: `${order.finishedQty} / ${order.plannedQty} 件`,
    weight: '',
    reference: order.operationContext?.motherOrderCode ?? '',
    detail: `${order.status}；进度 ${order.progress}%`,
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
    quantity: '',
    weight: '',
    reference: item.workOrder,
    detail: item.defect || '未记录缺陷',
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

const listKeyword = ref('');
const detailOpen = ref(false);
const listedRows = computed(() => {
  const text = listKeyword.value.trim().toLowerCase();
  if (!text) return visibleRows.value;
  return visibleRows.value.filter((row) => `${row.code} ${row.type} ${row.object} ${row.owner}`.toLowerCase().includes(text));
});

const selectedRow = computed(() =>
  visibleRows.value.find((row) => row.code === selectedCode.value) ?? visibleRows.value[0],
);

const commandStats = computed(() => [
  { label: '母单记录', value: countType('母单'), unit: '单', tone: 'amber' as Tone, tab: 'inbound' as DeskTab },
  { label: '工序工单', value: countType('工单'), unit: '张', tone: 'blue' as Tone, tab: 'production' as DeskTab },
  { label: '质检记录', value: countType('质检'), unit: '批', tone: 'amber' as Tone, tab: 'quality' as DeskTab },
  { label: '异常提示', value: dispatchRows.value.filter(row => row.tone === 'red').length, unit: '项', tone: 'red' as Tone },
].filter(item => !item.tab || canOpen(item.tab)));

function countType(type: string) {
  return dispatchRows.value.filter((row) => row.type === type).length;
}

function selectRow(code: string) {
  selectedCode.value = code;
  detailOpen.value = true;
}

function backToDesk() {
  detailOpen.value = false;
}

function openSelected() {
  if (selectedRow.value) {
    if (openOrder) {
      const row = selectedRow.value;
      openOrder({ objectType: row.type === '母单' ? 'MOTHER_ORDER' : null, code: row.type === '质检' ? row.reference : row.code });
      return;
    }
    if (selectedRow.value.type === '母单') {
      traceToken += 1;
      traceTarget.value = { objectType: 'MOTHER_ORDER', code: selectedRow.value.code, intent: 'INTAKE', token: traceToken };
    }
    openTab(selectedRow.value.tab);
  }
}

function startInbound() {
  if (openIntake) { openIntake(); return; }
  traceQuery.value = '';
  traceTarget.value = null;
  openTab('inbound');
}

function printSelected() {
  if (selectedRow.value?.type === '母单') openLabel(selectedRow.value.code);
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
    <p v-if="demoData" class="operation-feedback warning">当前待办含评审样例，仅用于演示。</p>
    <section class="module-master-panel dashboard-start" aria-label="开始业务">
      <div class="floor-toolbar">
        <div>
          <h2>今天的工作</h2>
          <p class="empty-hint">先看待办和本岗订单，再进入单据核对与办理。</p>
        </div>
        <div class="active-action-grid">
          <button v-if="canStartInbound" class="primary-button" type="button" @click="startInbound">新建收货／母单</button>
          <button v-if="canOpen('label') && !openOrders" class="secondary-button" type="button" @click="openTab('label')">标签打印</button>
          <button v-if="openOrders && canReadOrders" class="secondary-button" type="button" @click="openOrders">查看订单</button>
          <button v-else-if="canOpen('progress')" class="secondary-button" type="button" @click="openTab('progress')">查看在制进度</button>
        </div>
      </div>
      <p v-if="legacyOpen && !loading && !loadError && countType('母单') === 0 && canOpen('inbound')" class="operation-feedback">
        还没有母单。点击“新建收货／母单”，填写客户、运单、来料数量和重量，保存后即可打印母单标签，再核对加工要求和期限、确认拆单方式。
      </p>
    </section>
    <DeskInbox v-if="openOrders" />
    <button v-if="openOrders" class="secondary-button dashboard-history-toggle" type="button" :aria-expanded="legacyOpen" @click="toggleLegacy">{{ legacyOpen ? '收起其他单据记录' : '其他单据记录' }}</button>
    <section v-if="legacyOpen" class="mission-kpi-grid lean-kpis" aria-label="权限范围内业务概览">
      <article v-for="item in commandStats" :key="item.label" :class="`tone-${item.tone}`">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
      </article>
    </section>
    <p v-if="legacyOpen" class="empty-hint">本次旧列表返回的可见记录数，不随查找变化。{{ refreshedAt ? `更新于 ${refreshedAt}` : '正在读取业务数据' }}</p>

    <section v-if="legacyOpen" class="production-desk-grid command-grid-v2 lean-desk desk-page" :class="{ 'is-detail': detailOpen }" aria-label="业务单据和当前处理单">
      <main class="dispatch-console-v3 dispatch-console-v2 module-master-panel">
        <div class="floor-toolbar">
          <h2>业务单据</h2>
          <label>
            <span>查找</span>
            <input v-model="listKeyword" aria-label="待办查找" placeholder="单号、类型或责任人" />
          </label>
          <button class="secondary-button" type="button" :disabled="loading" @click="loadDispatchRows">{{ loading ? '刷新中…' : '刷新单据' }}</button>
        </div>
        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="erp-table-wrap command-table-wrap desk-table-wrap">
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
                v-for="row in listedRows"
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
              <tr v-if="listedRows.length === 0">
                <td colspan="7">{{ loading ? '正在读取单据…' : loadError ? '暂无待办单据，请刷新重试' : dispatchRows.length === 0 ? '暂无业务单据' : '没有匹配的单据' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="desk-cards">
          <p v-if="listedRows.length === 0" class="empty-hint">{{ loading ? '正在读取单据…' : loadError ? '暂无待办单据，请刷新重试' : dispatchRows.length === 0 ? '暂无业务单据' : '没有匹配的单据' }}</p>
          <button v-for="row in listedRows" :key="`card-${row.type}-${row.code}`" class="desk-card" type="button" @click="selectRow(row.code)">
            <span class="desk-card-top">
              <strong class="code">{{ row.code }}</strong>
              <StatusBadge :label="row.priority" :tone="row.tone" />
            </span>
            <span>{{ row.type }} · {{ row.object }}</span>
            <span>{{ row.node }} · {{ row.owner }}</span>
            <span v-if="row.quantity || row.weight">{{ [row.quantity, row.weight].filter(Boolean).join(' · ') }}</span>
          </button>
        </div>
      </main>

      <aside class="active-record-panel module-side-panel">
        <div class="panel-title-row">
          <button class="secondary-button detail-back" type="button" @click="backToDesk">返回列表</button>
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
            <template v-if="selectedRow.quantity">
              <dt>{{ selectedRow.type === '工单' ? '已完／计划' : '数量' }}</dt>
              <dd>{{ selectedRow.quantity }}</dd>
            </template>
            <template v-if="selectedRow.weight">
              <dt>重量</dt>
              <dd>{{ selectedRow.weight }}</dd>
            </template>
            <template v-if="selectedRow.reference">
              <dt>{{ selectedRow.type === '母单' ? '收货／运单' : '关联单号' }}</dt>
              <dd>{{ selectedRow.reference }}</dd>
            </template>
            <dt>处理信息</dt>
            <dd>{{ selectedRow.detail }}</dd>
          </dl>
          <p v-if="selectedRow.type === '母单'" class="empty-hint">母单已建立后可直接打印标签；继续处理可核对加工要求、完成期限和拆单方式。</p>
          <div class="active-action-grid">
            <button class="primary-button" type="button" @click="openSelected">{{ openOrder ? '查看订单详情' : selectedRow.action }}</button>
            <button v-if="selectedRow.type === '母单' && canOpen('label')" class="secondary-button" type="button" @click="printSelected">打印此母单标签</button>
          </div>
        </template>
        <p v-else class="empty-hint">{{ loadError ? '暂无待办单据' : '选择单据查看数量、状态和下一步操作' }}</p>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.command-center-v3 { display: flex; flex-direction: column; }
.command-center-v3 > * { order: 3; }
.dashboard-start { order: 0; }
.desk-task-panel { order: 1; }
.desk-inbox { order: 2; }
.dashboard-history-toggle { align-self: start; min-height: 44px; }
</style>
