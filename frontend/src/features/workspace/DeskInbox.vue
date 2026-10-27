<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import { fetchOrderWorkspace } from '../../services/orderWorkspaceApi';
import { fetchPendingApprovalTasks } from '../../services/approvalApi';
import type { OrderWorkspacePage, OrderWorkspaceRow } from '../../services/orderWorkspaceTypes';
import type { ApprovalTask } from '../../data/approvalTaskData';
import type { TraceTarget } from '../scan/traceTarget';
import OrderKindMark from '../orders/OrderKindMark.vue';
import { orderFacetLabel, orderProductionStatus, orderQuantityText } from '../orders/orderWorkspaceLabels';

const tabs = inject<Ref<string[]>>('zhubao-visible-tabs', ref([]));
const openOrders = inject<() => void>('zhubao-open-orders', () => {});
const openOrder = inject<(target: Pick<TraceTarget, 'objectType' | 'code'>) => void>('zhubao-open-order', () => {});
const openApproval = inject<(code: string) => void>('zhubao-open-approval', () => {});
const canReadOrders = computed(() => tabs.value.some(tab => ['inbound', 'production', 'progress', 'quality', 'inventory'].includes(tab)));
const canReadApprovals = computed(() => tabs.value.includes('approvals'));
const orders = ref<OrderWorkspacePage | null>(null);
const approvals = ref<ApprovalTask[]>([]);
const orderError = ref('');
const approvalError = ref('');
const orderBusy = ref(false);
const approvalBusy = ref(false);
const facet = ref('ALL');
const facets = computed(() => [
  'ALL', ...(tabs.value.includes('inbound') ? ['UNSPLIT'] : []),
  ...(tabs.value.some(tab => tab === 'production' || tab === 'progress') ? ['WAITING_START', 'IN_PROCESS'] : []),
  ...(tabs.value.includes('quality') ? ['WAITING_QUALITY', 'REWORK'] : []),
  ...(tabs.value.includes('inventory') ? ['READY_TO_SHIP'] : []),
]);
let orderGeneration = 0;
let approvalGeneration = 0;
let disposed = false;

async function loadOrders() {
  const token = ++orderGeneration;
  orders.value = null;
  orderError.value = '';
  if (!canReadOrders.value) { orderBusy.value = false; return; }
  orderBusy.value = true;
  try {
    const page = await fetchOrderWorkspace({ view: 'ORDERS', facet: facet.value, page: 1, size: 5 });
    if (!disposed && token === orderGeneration) orders.value = page;
  } catch (error) {
    if (!disposed && token === orderGeneration) orderError.value = error instanceof Error ? error.message : '订单暂时读不到';
  } finally {
    if (!disposed && token === orderGeneration) orderBusy.value = false;
  }
}
async function loadApprovals() {
  const token = ++approvalGeneration;
  approvals.value = [];
  approvalError.value = '';
  if (!canReadApprovals.value) { approvalBusy.value = false; return; }
  approvalBusy.value = true;
  try {
    const rows = await fetchPendingApprovalTasks();
    if (!disposed && token === approvalGeneration) approvals.value = rows;
  } catch (error) {
    if (!disposed && token === approvalGeneration) approvalError.value = error instanceof Error ? error.message : '审批待办暂时读不到';
  } finally {
    if (!disposed && token === approvalGeneration) approvalBusy.value = false;
  }
}
function select(row: OrderWorkspaceRow) { openOrder({ objectType: row.objectType, code: row.code }); }
watch([canReadOrders, facet], () => { void loadOrders(); }, { immediate: true });
watch(canReadApprovals, () => { void loadApprovals(); }, { immediate: true });
onBeforeUnmount(() => { disposed = true; orderGeneration += 1; approvalGeneration += 1; });
</script>

<template>
  <div class="desk-inbox">
    <section v-if="canReadOrders" class="inbox-panel" aria-label="本岗订单预览">
      <header><div><h2>订单事项</h2><p class="inbox-intro">按交期优先预览；选定单据后核对要求并办理本岗事项。</p></div><button class="secondary-button inbox-text-action" type="button" @click="openOrders">全部订单</button></header>
      <div class="inbox-tools"><label>分类 <select v-model="facet" aria-label="工作台订单分类"><option v-for="item in facets" :key="item" :value="item">{{ orderFacetLabel(item) }}</option></select></label><button class="secondary-button" type="button" :disabled="orderBusy" @click="loadOrders">刷新订单</button></div>
      <p v-if="orderBusy" role="status">正在读取订单…</p>
      <p v-if="orderError" role="alert">{{ orderError }}，可单独重试订单。</p>
      <template v-if="orders">
        <p class="inbox-count">当前分类共 {{ orders.totalRows }} 项目标，本次预览 {{ orders.items.length }} 项。</p>
        <p v-if="!orders.items.length">当前分类暂无订单</p>
        <button v-for="row in orders.items" :key="row.rowKey" type="button" class="inbox-row" @click="select(row)">
          <span><strong class="inbox-title"><OrderKindMark :object-type="row.objectType" />{{ row.goodsName || '货品未填写' }}</strong><small>{{ row.code }}<template v-if="row.executionRoundCode"> · 执行轮次 {{ row.executionRoundCode }}</template></small><small>{{ row.currentProcess || '工序未提供' }} · {{ orderQuantityText(row.quantity, row.quantityUnit) }}</small></span>
          <span><b>{{ orderProductionStatus(row) }}</b><small>交期 {{ row.dueDate || '未填写' }}</small><small class="inbox-row-hint">查看要求与下一步</small></span>
        </button>
      </template>
    </section>
    <section v-if="canReadApprovals" class="inbox-panel" aria-label="待我核对的审批">
      <header><div><h2>审批待办</h2><p class="inbox-intro">先查看原单、证据和申请原因，再办理审批决定。</p></div><button class="secondary-button inbox-text-action" type="button" :disabled="approvalBusy" @click="loadApprovals">刷新审批</button></header>
      <p v-if="approvalBusy" role="status">正在读取审批…</p>
      <p v-else-if="approvalError" role="alert">{{ approvalError }}，其他事项仍可办理。</p>
      <template v-else>
        <p class="inbox-count">当前可见待审批 {{ approvals.length }} 项，预览前 {{ Math.min(approvals.length, 5) }} 项。</p>
        <p v-if="!approvals.length">当前没有可见的待审批事项</p>
        <button v-for="task in approvals.slice(0, 5)" :key="task.approvalCode" type="button" class="inbox-row" @click="openApproval(task.approvalCode)">
          <span><strong>{{ task.title }}</strong><small>{{ task.approvalCode }} · {{ task.businessCode }}</small><small>{{ task.applicant }} · {{ task.createdAt }}</small></span><span>核对并审批</span>
        </button>
        <button class="secondary-button inbox-text-action" type="button" @click="openApproval('')">全部审批</button>
      </template>
    </section>
  </div>
</template>

<style scoped>
.desk-inbox { display: grid; gap: 16px; min-width: 0; }
.inbox-panel { background: var(--content); border: 1px solid var(--line); border-radius: 12px; padding: 18px; min-width: 0; }
.inbox-panel header, .inbox-tools { display: flex; justify-content: space-between; align-items: start; gap: 12px; flex-wrap: wrap; }
.inbox-panel h2 { margin: 0; font-size: 17px; }
.inbox-panel p, .inbox-row small { color: var(--muted); }
.inbox-tools { align-items: center; margin: 14px 0; }
.inbox-tools select { padding: 10px; border: 1px solid var(--line); border-radius: 8px; }
.inbox-row { display: flex; justify-content: space-between; gap: 16px; padding: 16px 0; border: 0; border-bottom: 1px solid var(--line); text-align: left; width: 100%; background: transparent; color: var(--ink); cursor: pointer; overflow-wrap: anywhere; }
.inbox-row > span { display: grid; gap: 6px; min-width: 0; }
.inbox-title { display: inline-flex; align-items: center; gap: 6px; }
.inbox-row > span:last-child { text-align: right; }
.inbox-count { font-size: 12px; }
@media (max-width: 900px) {
  .desk-inbox { gap: 20px; }
  .inbox-panel { padding: 0; border: 0; border-radius: 0; background: transparent; }
  .inbox-panel header { align-items: center; flex-wrap: nowrap; gap: 8px; }
  .inbox-panel h2 { font-size: 15px; }
  .inbox-intro, .inbox-row-hint { display: none; }
  .inbox-panel button, .inbox-tools select { min-height: 44px; }
  .inbox-text-action { flex-shrink: 0; padding: 0 2px 0 12px; border: 0; background: transparent; font-size: 13px; }
  .inbox-tools { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; margin: 8px 0; }
  .inbox-tools label { display: flex; align-items: center; gap: 8px; min-width: 0; color: var(--muted); font-size: 12px; }
  .inbox-tools select { flex: 1; min-width: 0; width: 100%; font-size: 16px; background: var(--content); padding: 8px; }
  .inbox-tools button { padding: 8px 10px; font-size: 13px; }
  .inbox-count { display: none; }
  .inbox-row { gap: 10px; padding: 14px; margin-top: 8px; border: 1px solid var(--line); border-radius: 12px; background: var(--content); font-size: 13px; }
  .inbox-row > span { gap: 5px; }
  .inbox-row > span:first-child { flex: 1; }
  .inbox-row > span:last-child { flex: 0 0 100px; font-size: 12px; }
  .inbox-row small { font-size: 12px; }
  .inbox-row b { color: var(--action); font-weight: 500; }
  .inbox-panel > p:not(.inbox-count) { margin-top: 8px; font-size: 13px; }
}
</style>
