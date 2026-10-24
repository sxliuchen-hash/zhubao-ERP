<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, reactive, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { ApiRequestError } from '../../services/apiClient';
import { fetchOrderWorkspace } from '../../services/orderWorkspaceApi';
import type { OrderWorkspacePage, OrderWorkspaceQuery, OrderWorkspaceRow } from '../../services/orderWorkspaceTypes';
import { scanObjectTypeLabel, type ScanContextQuery, type ScanRelatedTarget } from '../../services/scanContextApi';
import ScanInput from '../scan/ScanInput.vue';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import { useTraceTarget } from '../scan/useTraceTarget';
import type { TraceTarget } from '../scan/traceTarget';
import { useWorkspaceContext } from '../workspace/useWorkspaceContext';
import type { TabKey } from '../workspace/workspaceNavigation';
import { workspaceTabs } from '../workspace/workspaceNavigation';
import OrderList from './OrderList.vue';
import OrderDetail from './OrderDetail.vue';
import type { OpenIntakeAction, OpenLabelAction, OpenOrderChangeAction, OpenProductionAction, OpenQualityAction, OpenShippingAction, ProductionMode } from './orderActions';
import { orderFacetLabel } from './orderWorkspaceLabels';

const workspace = useWorkspaceContext();
const saved = workspace.returnLocation.value;
const filters = reactive({ query: saved?.query ?? '', customerId: saved?.customerId ?? '', process: saved?.process ?? '',
  facet: saved?.facet ?? 'ALL', dueBefore: saved?.dueBefore ?? '' });
const view = ref<NonNullable<OrderWorkspaceQuery['view']>>(workspace.orderView.value ?? saved?.view ?? 'ORDERS');
const page = ref(saved?.page ?? 1);
const result = ref<OrderWorkspacePage | null>(null);
const loading = ref(false);
const error = ref('');
const accessDenied = ref(false);
const detailOpen = ref(false);
const selectedRow = ref<OrderWorkspaceRow | null>(null);
const detailError = ref('');
const scanCode = ref('');
const scanOpen = ref(false);
const scan = reactive(useScanContext());
const identity = computed(() => canonicalTarget(scan.context));
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const tabs = inject<Ref<TabKey[]>>('zhubao-visible-tabs', ref([]));
const openTab = inject<(tab: TabKey) => void>('zhubao-open-tab', () => {});
const openIntake = inject<OpenIntakeAction | null>('zhubao-open-intake', null);
const openProduction = inject<OpenProductionAction | null>('zhubao-open-production', null);
const openOrderChange = inject<OpenOrderChangeAction | null>('zhubao-open-order-change', null);
const openQuality = inject<OpenQualityAction | null>('zhubao-open-quality', null);
const openShipping = inject<OpenShippingAction | null>('zhubao-open-shipping', null);
const openLabel = inject<OpenLabelAction | null>('zhubao-open-label-target', null);
const canLabel = computed(() => Boolean(identity.value && ['MOTHER_ORDER', 'WORK_ORDER'].includes(identity.value.objectType)
  && tabs.value.includes('label') && (!session.value?.effectivePermissions || session.value.effectivePermissions.includes('LABEL_PRINT'))));
const canShip = computed(() => Boolean(identity.value && ['MOTHER_ORDER', 'WORK_ORDER', 'OUTBOUND_ORDER'].includes(identity.value.objectType)
  && tabs.value.some(tab => ['inventory', 'mobile'].includes(tab)) && (!session.value?.effectivePermissions || session.value.effectivePermissions.includes('BUSINESS_OUTBOUND_SHIPMENT'))));
const canInspect = computed(() => Boolean(identity.value && ['MOTHER_ORDER', 'WORK_ORDER', 'REWORK'].includes(identity.value.objectType)
  && tabs.value.some(tab => ['quality', 'mobile'].includes(tab))
  && (!session.value?.effectivePermissions || ['BUSINESS_QUALITY_FREEZE', 'BUSINESS_REWORK_DISPATCH', 'BUSINESS_REWORK_REPORT', 'BUSINESS_QUALITY_RELEASE'].some(permission => session.value?.effectivePermissions?.includes(permission)))));
const canWork = computed(() => Boolean(identity.value && ['MOTHER_ORDER', 'WORK_ORDER'].includes(identity.value.objectType)
  && tabs.value.some(tab => ['production', 'progress', 'mobile'].includes(tab))
  && (!session.value?.effectivePermissions || session.value.effectivePermissions.includes('BUSINESS_PRODUCTION_REPORT'))));
const refresh = inject<Ref<number>>('zhubao-order-refresh', ref(0));
const canCreate = computed(() => openIntake && tabs.value.includes('inbound') && session.value?.effectivePermissions?.includes('BUSINESS_INBOUND_CONFIRM'));
const trace = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
const legacyTabs = computed(() => workspaceTabs.filter(tab => ['inbound', 'production', 'progress', 'quality'].includes(tab.key) && tabs.value.includes(tab.key)));
const legacyLabel: Partial<Record<TabKey, string>> = { inbound: '来料记录、母单与拆单', production: '生产记录与设备产量', progress: '原进度查询与历史', quality: '质检记录、损耗与工资试算' };
const serverViews = ref<OrderWorkspacePage['availableViews']>(['ORDERS', 'PRODUCTION', 'QUALITY']);
const allowedViews = computed(() => serverViews.value.filter(item =>
  item === 'ORDERS' || (item === 'PRODUCTION' ? tabs.value.some(key => key === 'production' || key === 'progress') : tabs.value.includes('quality'))));
const facets = ['ALL', 'UNSPLIT', 'WAITING_START', 'IN_PROCESS', 'WAITING_QUALITY', 'REWORK', 'READY_TO_SHIP', 'SHIPPED', 'UNKNOWN'];
let generation = 0;
let disposed = false;
let scrollTop = saved?.scrollTop ?? 0;

function remember() {
  workspace.rememberReturn({ section: 'orders', ...filters, view: view.value, page: page.value, scrollTop });
}
function clearDetail() {
  scan.invalidate();
  detailError.value = '';
  detailOpen.value = false;
  selectedRow.value = null;
  workspace.selection.value = null;
}
function invalidateList() {
  generation += 1;
  loading.value = false;
  result.value = null;
  error.value = '';
  accessDenied.value = false;
  clearDetail();
}
watch([filters, view], () => { invalidateList(); page.value = 1; remember(); }, { flush: 'sync' });
watch(scanCode, () => { clearDetail(); }, { flush: 'sync' });
watch(view, value => { workspace.orderView.value = value; }, { immediate: true });
watch(workspace.orderView, requested => { if (requested && requested !== view.value) { view.value = requested; void load(1); } });
watch(refresh, () => { void load(); });

async function load(requestedPage = page.value) {
  clearDetail();
  const token = ++generation;
  page.value = requestedPage;
  remember();
  loading.value = true;
  error.value = '';
  accessDenied.value = false;
  result.value = null;
  try {
    const response = await fetchOrderWorkspace({ ...filters, view: view.value, page: page.value, size: 20 });
    if (disposed || token !== generation) return;
    serverViews.value = response.availableViews;
    result.value = response;
  } catch (caught) {
    if (disposed || token !== generation) return;
    accessDenied.value = caught instanceof ApiRequestError && (caught.status === 403 || caught.code === 40301);
    error.value = caught instanceof Error ? caught.message : '订单加载失败';
  } finally {
    if (!disposed && token === generation) loading.value = false;
  }
}

async function locate(input: ScanContextQuery, row: OrderWorkspaceRow | null = null) {
  const fromList = !detailOpen.value;
  clearDetail();
  if (fromList) scrollTop = window.scrollY;
  remember();
  selectedRow.value = row;
  detailOpen.value = true;
  const context = await scan.lookup({ ...input, intent: 'LOOKUP' });
  if (!context) return;
  const target = canonicalTarget(context);
  // 列表明确的目标不允许被解析成原单或其他轮次，含糊结果只保留人工选择。
  if (row && target && (target.objectType !== row.objectType || target.code !== row.code
    || (target.motherOrderCode && target.motherOrderCode !== row.motherOrderCode))) {
    scan.invalidate();
    detailError.value = '当前目标尚不能准确解析，请核对原单与执行轮次；未打开正式操作。';
    return;
  }
  if (target) workspace.rememberSelection({ objectType: target.objectType, code: target.code,
    motherOrderCode: target.motherOrderCode, workOrderCode: target.workOrderCode,
    executionRoundCode: row?.executionRoundCode ?? null });
}
function selectRow(row: OrderWorkspaceRow) {
  void locate({ content: row.code, objectType: row.objectType }, row);
}
function choose(item: ScanRelatedTarget) {
  if (item.code && item.objectType) void locate({ content: item.code, objectType: item.objectType });
}
async function back() {
  clearDetail();
  if (trace.value?.intent === 'LOOKUP') trace.value = null;
  await nextTick();
  window.scrollTo(0, scrollTop);
}
function canUse(tab: TabKey, permission: string, action?: string) {
  return Boolean(identity.value && tabs.value.includes(tab)
    && (!session.value?.effectivePermissions || session.value.effectivePermissions.includes(permission))
    && (!action || scan.context?.actions.some(item => item.code === action && item.allowed)));
}
function go(tab: TabKey, intent: TraceTarget['intent'], permission: string, action?: string) {
  if (!identity.value || !canUse(tab, permission, action)) return;
  trace.value = { objectType: identity.value.objectType, code: identity.value.code, intent, token: (trace.value?.token ?? 0) + 1 };
  openTab(tab);
}
function work(mode: ProductionMode) {
  if (!identity.value || !canWork.value) return;
  if (openProduction) openProduction({ objectType: identity.value.objectType, code: identity.value.code }, mode);
  else go('production', 'PRODUCTION', 'BUSINESS_PRODUCTION_REPORT', 'REPORT_PROCESS');
}
function changeOrder() {
  if (!identity.value?.motherOrderCode || !tabs.value.includes('progress')) return;
  openOrderChange?.({ objectType: identity.value.objectType, code: identity.value.code, motherOrderCode: identity.value.motherOrderCode });
}
function inspect() {
  if (!identity.value || !canInspect.value) return;
  if (openQuality) openQuality({ objectType: identity.value.objectType, code: identity.value.code });
  else go('quality', 'QUALITY', 'BUSINESS_QUALITY_FREEZE');
}
function ship() {
  if (identity.value && canShip.value) openShipping?.({ objectType: identity.value.objectType, code: identity.value.code });
}
function label() { if (identity.value && canLabel.value) openLabel?.({ objectType: identity.value.objectType, code: identity.value.code }); }
function chooseFacet(facet: string) {
  filters.facet = facet;
  void load(1);
}
function facetCount(facet: string) {
  const count = result.value?.facetCounts?.[facet];
  return count == null ? '' : String(count);
}

interface DetailAction { key: string; label: string; run: () => void; rank: number }
const detailActions = computed(() => {
  const items: DetailAction[] = [];
  const facet = selectedRow.value?.facet ?? '';
  const current = identity.value;
  if (!current) return items;
  if (current.objectType === 'MOTHER_ORDER' && canUse('inbound', 'BUSINESS_INBOUND_CONFIRM')) {
    items.push({ key: 'intake', label: '收货与拆单资料', run: () => go('inbound', 'INTAKE', 'BUSINESS_INBOUND_CONFIRM'), rank: 45 });
  }
  if (openProduction && canWork.value) {
    items.push({ key: 'progress', label: '更新工序进度', run: () => work('progress'), rank: 5 });
  }
  if (canWork.value && scan.context?.actions.some(action => action.code === 'REPORT_PROCESS' && action.allowed)) {
    items.push({ key: 'station', label: '打开岗位交工', run: () => work('station'), rank: 6 });
  }
  if (openOrderChange && current.motherOrderCode && tabs.value.includes('progress') && ['MOTHER_ORDER', 'WORK_ORDER'].includes(current.objectType)) {
    items.push({ key: 'change', label: '改单办理', run: changeOrder, rank: 50 });
  }
  if (canInspect.value) {
    items.push({ key: 'inspect', label: '质检、返工与放行', run: inspect, rank: facet === 'WAITING_QUALITY' || facet === 'REWORK' || view.value === 'QUALITY' ? 1 : 25 });
  }
  if (openShipping && canShip.value) {
    items.push({ key: 'ship', label: '核对库存与发货', run: ship, rank: facet === 'READY_TO_SHIP' ? 0 : 30 });
  }
  if (openLabel && canLabel.value) {
    items.push({ key: 'label', label: '预览本单标签', run: label, rank: 40 });
  }
  return items.sort((left, right) => left.rank - right.rank);
});
const foregroundActions = computed(() => {
  const ordered = detailActions.value;
  const primary = ordered[0];
  const station = primary?.key === 'progress' ? ordered.find(item => item.key === 'station') : undefined;
  return [primary, station].filter((item): item is DetailAction => Boolean(item));
});
const moreActions = computed(() => detailActions.value.filter(item => !foregroundActions.value.includes(item)));

void load().then(async () => {
  if (!disposed && saved) { await nextTick(); window.scrollTo(0, saved.scrollTop); }
});
useTraceTarget(['LOOKUP'], target => { void locate({ content: target.code, ...(target.objectType ? { objectType: target.objectType } : {}) }); });
onBeforeUnmount(() => { disposed = true; generation += 1; workspace.selection.value = null; });
</script>

<template>
  <div class="order-workspace page-stack" :class="{ 'has-detail': detailOpen }">
    <section class="order-list-panel" aria-label="订单查询列表">
      <details class="order-legacy-pages"><summary>其他业务记录与工具</summary><div class="order-legacy-actions"><button v-for="tab in legacyTabs" :key="tab.key" type="button" class="secondary-button" @click="openTab(tab.key)">{{ legacyLabel[tab.key] || tab.label }}</button></div></details>
      <div class="order-facet-tabs" role="tablist" aria-label="订单分类">
        <button v-for="facet in facets" :key="facet" type="button" class="order-facet-tab" :aria-selected="filters.facet === facet" @click="chooseFacet(facet)">{{ orderFacetLabel(facet) }}<small v-if="facetCount(facet)">{{ facetCount(facet) }}</small></button>
      </div>
      <form class="order-filters" @submit.prevent="load(1)">
        <label class="order-search"><span>搜索</span><input v-model="filters.query" maxlength="100" aria-label="订单搜索" placeholder="单号、运单、货品或客户" /></label>
        <label v-if="allowedViews.length > 1"><span>视图</span><select v-model="view" aria-label="订单视图"><option v-for="item in allowedViews" :key="item" :value="item">{{ item === 'PRODUCTION' ? '生产作业' : item === 'QUALITY' ? '质量作业' : '全部订单' }}</option></select></label>
        <label><span>工序</span><input v-model="filters.process" aria-label="订单工序" placeholder="工序名称" /></label>
        <label><span>交期到</span><input v-model="filters.dueBefore" aria-label="订单截止交期" type="date" /></label>
        <details class="order-customer-filter"><summary>客户</summary><label><span>客户档案 ID</span><input v-model="filters.customerId" aria-label="订单客户ID" inputmode="numeric" placeholder="稳定客户 ID" /></label></details>
        <button class="primary-button" type="submit" :disabled="loading">查询</button>
        <button v-if="canCreate" type="button" class="secondary-button" @click="openIntake?.()">新建收货</button>
      </form>
      <details v-if="!detailOpen" class="order-scan-tools" :open="scanOpen" @toggle="scanOpen = ($event.target as HTMLDetailsElement).open"><summary>扫码或手动定位单据</summary><ScanInput v-model="scanCode" field-text="扫码定位单据" input-aria-label="订单扫码单号" submit-label="查看单据" bind-mobile-scan :busy="scan.loading" @activate-camera="scanOpen = true" @draft="clearDetail" @recognize="draft => locate(draft)" /></details>
      <p v-if="loading" role="status">正在加载订单…</p>
      <p v-if="error" role="alert" class="operation-feedback danger">{{ accessDenied ? '无权限：' : '' }}{{ error }}</p>
      <template v-if="result">
        <p class="order-count">共 {{ result.totalRows }} 项目标 · 本页 {{ result.items.length }} 项</p>
        <OrderList :rows="result.items" :selected-key="selectedRow?.rowKey ?? ''" @select="selectRow" />
        <p v-if="!result.items.length">当前条件下暂无订单</p>
        <nav class="order-pagination" aria-label="订单分页">
          <button type="button" class="secondary-button" :disabled="result.page <= 1" @click="load(result.page - 1)">上一页</button>
          <span>第 {{ result.page }} 页</span>
          <button type="button" class="secondary-button" :disabled="result.page * result.size >= result.totalRows" @click="load(result.page + 1)">下一页</button>
        </nav>
      </template>
    </section>
    <aside v-if="detailOpen" class="order-detail-panel" aria-label="订单详情">
      <button type="button" class="secondary-button" @click="back">返回列表</button>
      <p v-if="scan.loading" role="status">正在定位单据…</p>
      <p v-if="scan.error || detailError" role="alert" class="operation-feedback danger">{{ scan.error || detailError }}</p>
      <template v-if="scan.context?.requiresSelection">
        <p>请选择具体单据，不会自动打开第一张。</p>
        <button v-for="item in scan.context.relatedTargets" :key="`${item.objectType}:${item.code}`" type="button" class="secondary-button" @click="choose(item)">{{ scanObjectTypeLabel(item.objectType) }} {{ item.code }}</button>
      </template>
      <template v-if="identity && scan.context">
        <h2>{{ scanObjectTypeLabel(identity.objectType) }} {{ identity.code }}</h2>
        <p v-if="selectedRow?.executionRoundCode">执行轮次 {{ selectedRow.executionRoundCode }} · 原子单 {{ selectedRow.workOrderCode || '未提供' }}</p>
        <OrderDetail :key="`${identity.objectType}:${identity.code}`" :context="scan.context">
          <template #summary>
            <p v-if="selectedRow">{{ selectedRow.currentProcess || '未提供工序' }} · {{ selectedRow.processPercent === null ? '未记录进度' : `工序进度 ${selectedRow.processPercent}%` }} · {{ selectedRow.processFormallyDelivered === null ? '交工状态未提供' : selectedRow.processFormallyDelivered ? '已正式交工' : '未正式交工' }}</p>
          </template>
        </OrderDetail>
        <div class="order-detail-actions" aria-label="原业务办理入口">
          <button v-for="(action, index) in foregroundActions" :key="action.key" :class="index === 0 ? 'primary-button' : 'secondary-button'" type="button" @click="action.run">{{ action.label }}</button>
          <details v-if="moreActions.length" class="order-more-actions">
            <summary>更多操作</summary>
            <div>
              <button v-for="action in moreActions" :key="action.key" class="secondary-button" type="button" @click="action.run">{{ action.label }}</button>
            </div>
          </details>
        </div>
      </template>
    </aside>
  </div>
</template>

<style scoped>
.order-workspace { display: grid; grid-template-columns: minmax(0, 1fr); align-items: start; font-size: 14px; }
.order-list-panel, .order-detail-panel { min-width: 0; background: var(--content); border: 1px solid var(--line); border-radius: 12px; padding: 16px; }
.order-detail-panel { display: grid; gap: 14px; overflow-wrap: anywhere; }
.order-detail-panel h2 { margin: 0; font-size: 18px; }
.order-facet-tabs { display: flex; gap: 18px; overflow: auto; border-bottom: 1px solid var(--line); margin: 0 0 12px; }
.order-facet-tab { display: inline-flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 2px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--muted); white-space: nowrap; cursor: pointer; }
.order-facet-tab small { font-size: 11px; }
.order-facet-tab[aria-selected="true"] { color: var(--action); border-bottom-color: var(--action); font-weight: 700; }
.order-filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: end; margin-bottom: 12px; }
.order-filters label { display: grid; gap: 4px; min-width: 0; flex: 0 1 180px; max-width: 220px; }
.order-filters .order-search { flex: 1 1 220px; max-width: 320px; }
.order-filters input, .order-filters select { box-sizing: border-box; width: 100%; min-width: 0; padding: 8px 10px; border: 1px solid var(--line); border-radius: 8px; }
.order-filters details { flex: 0 0 auto; }
.order-filters summary { cursor: pointer; min-height: 36px; align-content: center; }
.order-customer-filter { position: relative; }
.order-customer-filter[open] { flex: 0 1 180px; }
.order-count { color: var(--muted); }
.order-scan-tools { margin: 8px 0 16px; }
.order-scan-tools > summary { cursor: pointer; min-height: 36px; align-content: center; }
.order-more-actions { flex: 1 1 100%; }
.order-more-actions > div { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.order-pagination, .order-legacy-actions, .order-detail-actions { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.order-pagination { justify-content: end; margin-top: 16px; }
@media (min-width: 1200px) { .has-detail { grid-template-columns: minmax(0, 1fr) minmax(340px, 38%); gap: 16px; } }
@media (min-width: 901px) and (max-width: 1199px) { .has-detail .order-list-panel { display: none; } }
@media (max-width: 900px) {
  .has-detail .order-list-panel { display: none; }
  .order-filters label,
  .order-filters .order-search { flex-basis: calc(50% - 10px); max-width: none; }
  .order-detail-panel { display: flex; flex-direction: column; }
  .order-detail-panel > button { order: -2; align-self: start; border: 0; background: transparent; padding-left: 0; }
  .order-detail-actions { order: -1; position: sticky; top: 0; z-index: 4; margin: 0 -12px; padding: 8px 12px; background: var(--content); border-bottom: 1px solid var(--line); }
  .order-detail-actions > button { flex: 1 1 120px; }
  .order-list-panel, .order-detail-panel { padding: 12px; }
  .order-filters input, .order-filters select { font-size: 16px; min-height: 44px; }
  .order-filters button, .order-pagination button { min-height: 44px; }
  .order-detail-panel button, .order-legacy-actions button { min-height: 44px; }
}
</style>
