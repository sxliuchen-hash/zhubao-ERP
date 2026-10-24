<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import type { OpenAftercareAction, OpenShippingAction } from '../orders/orderActions';
import { useTraceTarget } from '../scan/useTraceTarget';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import PrepStockPanel from './PrepStockPanel.vue';
import ShippingPanel from '../shipping/ShippingPanel.vue';
import StockLedgerPanel from '../inventory/StockLedgerPanel.vue';
import InventoryAftercarePanel from '../inventory/InventoryAftercarePanel.vue';
import type { InventoryBalance, Tone } from '../../data/m1PrototypeData';
import { ApiRequestError } from '../../services/apiClient';
import { fetchInventoryWorkspace, type InventoryWorkspaceModel } from '../../services/businessWorkspaceApi';

const emptyInventory = (): InventoryWorkspaceModel => ({ balances: [], ledgerEntries: [], timeline: [], evidence: [] });
const workspace = ref<InventoryWorkspaceModel>(emptyInventory());
const openShipping = inject<OpenShippingAction | null>('zhubao-open-shipping', null);
const openAftercare = inject<OpenAftercareAction | null>('zhubao-open-aftercare', null);
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canShip = computed(() => !session.value?.effectivePermissions || session.value.effectivePermissions.includes('BUSINESS_OUTBOUND_SHIPMENT'));
const manualCreated = ref(!openShipping);
const manualOpen = ref(!openShipping);
const stockTab = ref<'company' | 'prep' | 'processing' | 'ledger'>('company');
const ledgerCreated = ref(false);
watch(stockTab, tab => { if (tab === 'ledger') ledgerCreated.value = true; });
function openManual() {
  if (openShipping) { if (canShip.value) openShipping(); return; }
  manualCreated.value = true;
  manualOpen.value = !manualOpen.value;
}
useTraceTarget(['SHIPPING'], target => {
  if (openShipping) { if (canShip.value) openShipping({ objectType: target.objectType, code: target.code }); }
  else { stockTab.value = 'processing'; manualCreated.value = true; manualOpen.value = true; }
});
const refresh = inject<Ref<number>>('zhubao-order-refresh', ref(0));
watch(refresh, () => { void loadInventoryWorkspace(); });
const loadError = ref('');
const accessDenied = ref(false);
const loading = ref(false);
const statusKeyword = ref('');
const advancedOpen = ref(false);
const detailOpen = ref(false);
const selectedBalanceKey = ref('');
const { keyword, filtered: filteredBalances } = useDeskFilter(
  () => workspace.value.balances,
  item => `${item.owner} ${item.batch} ${item.material} ${item.location} ${item.status}`,
);
const visibleBalances = computed(() => {
  const status = statusKeyword.value.trim();
  return filteredBalances.value.filter(item => !status || item.status.includes(status));
});
const activeBalance = computed(() => workspace.value.balances.find(item => balanceKey(item) === selectedBalanceKey.value));
const latestLedger = computed(() => workspace.value.ledgerEntries.find(entry => entry.source === activeBalance.value?.batch));
watch([keyword, statusKeyword], () => { selectedBalanceKey.value = ''; detailOpen.value = false; });
function balanceKey(item: InventoryBalance) {
  const context = item.operationContext;
  return JSON.stringify(context ? [context.customerId, context.ownerCustomerId, context.motherOrderCode, context.workOrderCode, context.materialCode, context.warehouseCode, context.ownerType, context.stockStatus]
    : [item.owner, item.batch, item.material, item.location, item.status]);
}
function inventoryBalanceTone(status: string): Tone {
  if (status.includes('返工')) return 'violet';
  if (status.includes('在制')) return 'amber';
  if (status.includes('成品')) return 'green';
  return 'blue';
}
let generation = 0;
let disposed = false;
onMounted(() => { void loadInventoryWorkspace(); });
onBeforeUnmount(() => { disposed = true; generation += 1; });
async function loadInventoryWorkspace() {
  const token = ++generation;
  loadError.value = ''; accessDenied.value = false; loading.value = true;
  try {
    const loaded = await fetchInventoryWorkspace();
    if (disposed || token !== generation) return;
    workspace.value = loaded;
    if (!loaded.balances.some(item => balanceKey(item) === selectedBalanceKey.value)) { selectedBalanceKey.value = ''; detailOpen.value = false; }
  } catch (caught) {
    if (disposed || token !== generation) return;
    accessDenied.value = caught instanceof ApiRequestError && (caught.status === 403 || caught.code === 40301);
    if (accessDenied.value) { workspace.value = emptyInventory(); selectedBalanceKey.value = ''; detailOpen.value = false; }
    loadError.value = caught instanceof Error ? caught.message : '库存读取失败';
  } finally { if (!disposed && token === generation) loading.value = false; }
}
defineExpose({ reloadInventory: loadInventoryWorkspace });
function backToList() { detailOpen.value = false; }
function selectBalance(item: InventoryBalance) { selectedBalanceKey.value = balanceKey(item); detailOpen.value = true; }
function shipSelected() {
  const context = activeBalance.value?.operationContext;
  if (!openShipping || !canShip.value || !context || loading.value || loadError.value || context.stockStatus !== 'FINISHED' || context.ownerType !== 'CUSTOMER') return;
  openShipping({ objectType: null, code: context.workOrderCode || context.motherOrderCode, balance: { ...context } });
}
const localAftercare = ref<Array<{ key: string; row: InventoryBalance }>>([]);
const localAftercareKey = ref('');
let localAttempt = 0;
const canOpenAftercare = computed(() => {
  const context = activeBalance.value?.operationContext;
  if (context?.ownerType !== 'CUSTOMER') return false;
  const permission = context.stockStatus === 'SHIPPED' ? 'BUSINESS_CUSTOMER_RETURN' : context.stockStatus === 'FINISHED' ? 'BUSINESS_AFTER_SALES_REVIEW' : '';
  return Boolean(permission && (!session.value?.effectivePermissions || session.value.effectivePermissions.includes(permission)));
});
function localAftercareDraft(row: InventoryBalance, newAttempt = false) {
  const baseKey = balanceKey(row);
  const key = newAttempt ? `${baseKey}:attempt-${++localAttempt}` : localAftercare.value.filter(draft => draft.key === baseKey || draft.key.startsWith(`${baseKey}:attempt-`)).at(-1)?.key ?? baseKey;
  if (!localAftercare.value.some(draft => draft.key === key)) localAftercare.value.push({ key, row: JSON.parse(JSON.stringify(row)) as InventoryBalance });
  localAftercareKey.value = key;
}
function aftercareSelected() {
  const row = activeBalance.value;
  if (!row || !canOpenAftercare.value || loading.value || loadError.value) return;
  if (openAftercare) { openAftercare(row); return; }
  localAftercareDraft(row);
}
</script>

<template>
  <div class="inventory-trace-desk page-stack desk-page" :class="{ 'is-detail': detailOpen }">
    <p v-if="workspace.dataSource === 'DEMO'" class="operation-feedback warning">当前显示评审样例，仅用于演示。</p>
    <div class="finance-sections" role="tablist" aria-label="库存分区">
      <button type="button" class="table-action" :class="{ active: stockTab === 'company' }" :aria-selected="stockTab === 'company'" @click="stockTab = 'company'">公司自有物料</button>
      <button type="button" class="table-action" :class="{ active: stockTab === 'prep' }" :aria-selected="stockTab === 'prep'" @click="stockTab = 'prep'">客户寄存备料</button>
      <button type="button" class="table-action" :class="{ active: stockTab === 'processing' }" :aria-selected="stockTab === 'processing'" @click="stockTab = 'processing'">加工库存</button>
      <button type="button" class="table-action" :class="{ active: stockTab === 'ledger' }" :aria-selected="stockTab === 'ledger'" @click="stockTab = 'ledger'">出入库记录</button>
    </div>
    <KeepAlive><PrepStockPanel v-if="stockTab === 'company' || stockTab === 'prep'" :key="stockTab" :scope="stockTab" /></KeepAlive>
    <div v-show="stockTab === 'processing'">
    <button v-if="canShip" class="secondary-button" type="button" @click="openManual">扫码或手输发货单据</button>
    <ShippingPanel
      v-if="manualCreated"
      v-show="manualOpen"
      title="确认发货"
      intro="扫成品标签。选择完整库存维度，并填写本次数量、重量和物流。物流扫码只填写运单号。超时用原确认回放，不会另开一次发货。"
    />
    <section class="module-work-grid inventory-work-grid" aria-label="库存追溯主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>客户库存池</h2>
          </div>
        </div>
        <div class="module-tools production-filters">
          <label>
            <span>查找</span>
            <input v-model="keyword" placeholder="客户、批次或物料" aria-label="库存追溯关键字" />
          </label>
          <button class="secondary-button" type="button" :aria-expanded="advancedOpen ? 'true' : 'false'" @click="advancedOpen = !advancedOpen">
            {{ advancedOpen ? '收起筛选' : '更多筛选' }}
          </button>
          <label v-if="advancedOpen">
            <span>状态</span>
            <input v-model="statusKeyword" placeholder="如 成品" aria-label="库存状态筛选" />
          </label>
        </div>

        <p v-if="loading" class="empty-hint">正在加载库存</p>
        <p v-if="accessDenied" class="operation-feedback danger">无权限</p>
        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="module-table-wrap refined-table-wrap desk-table-wrap">
          <table class="module-table inventory-dispatch-table">
            <thead>
              <tr>
                <th>客户所有权</th>
                <th>批次/来源</th>
                <th>物料</th>
                <th>库位</th>
                <th>状态</th>
                <th>数量重量</th>
                <th>冻结</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in visibleBalances"
                :key="balanceKey(item)"
                :class="{ 'is-selected': activeBalance && balanceKey(activeBalance) === balanceKey(item) }"
                :aria-selected="activeBalance && balanceKey(activeBalance) === balanceKey(item)"
                @click="selectBalance(item)"
              >
                <td><strong>{{ item.owner }}</strong></td>
                <td>{{ item.batch }}</td>
                <td>{{ item.material }}</td>
                <td>{{ item.location }}</td>
                <td><StatusBadge :label="item.status" :tone="inventoryBalanceTone(item.status)" /></td>
                <td>{{ item.qty }}{{ item.quantityUnit ? ` ${item.quantityUnit}` : '' }} / {{ item.weight }}</td>
                <td>{{ item.frozen }}</td>
                <td><button class="table-action" type="button" @click.stop="selectBalance(item)">看台账</button></td>
              </tr>
              <tr v-if="!loading && visibleBalances.length === 0">
                <td colspan="8">暂无库存单据</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="desk-cards">
          <p v-if="!loading && visibleBalances.length === 0" class="empty-hint">暂无库存单据</p>
          <button
            v-for="item in visibleBalances"
            :key="balanceKey(item)"
            class="desk-card"
            type="button"
            @click="selectBalance(item)"
          >
            <span class="desk-card-top">
              <strong class="code">{{ item.batch }}</strong>
              <StatusBadge :label="item.status" :tone="inventoryBalanceTone(item.status)" />
            </span>
            <span>{{ item.owner }} · {{ item.material }}</span>
            <span>{{ item.qty }}{{ item.quantityUnit ? ` ${item.quantityUnit}` : '' }} / {{ item.weight }} · 冻结 {{ item.frozen }}</span>
          </button>
        </div>
      </div>

      <aside v-if="activeBalance" class="module-side-panel">
        <section class="active-object-card tone-blue">
          <div class="panel-title-row">
            <button class="secondary-button detail-back" type="button" @click="backToList">返回列表</button>
            <StatusBadge :label="activeBalance.status" :tone="inventoryBalanceTone(activeBalance.status)" />
          </div>
          <strong>{{ activeBalance.batch }}</strong>
          <dl class="detail-grid compact-detail-grid">
            <dt>客户</dt>
            <dd>{{ activeBalance.owner }}</dd>
            <dt>物料</dt>
            <dd>{{ activeBalance.material }}</dd>
            <dt>库位</dt>
            <dd>{{ activeBalance.location }}</dd>
            <dt>数量重量</dt>
            <dd>{{ activeBalance.qty }}{{ activeBalance.quantityUnit ? ` ${activeBalance.quantityUnit}` : '' }} / {{ activeBalance.weight }}</dd>
            <dt>冻结</dt>
            <dd>{{ activeBalance.frozen }}</dd>
            <dt>同来源台账</dt>
            <dd>{{ latestLedger?.bizCode ?? '暂无' }}</dd>
          </dl>
          <button v-if="openShipping && canShip && activeBalance.operationContext?.stockStatus === 'FINISHED' && activeBalance.operationContext.ownerType === 'CUSTOMER'" class="primary-button" type="button" @click="shipSelected">从这笔库存发货</button>
          <p v-else-if="activeBalance.operationContext?.stockStatus !== 'FINISHED'" class="operation-feedback">本笔尚非可发成品，请先核对来源单和质检放行状态。</p>
          <button v-if="canOpenAftercare" class="secondary-button" type="button" :disabled="loading || Boolean(loadError)" @click="aftercareSelected">退货与售后复核</button>
          <p class="operation-feedback inventory-coach">先核对来源和完整库存维度，再按需打开单内动作；不可发库存保留来源及阻断状态。</p>
        </section>
      </aside>
    </section>

    <section v-if="detailOpen" class="module-detail-grid inventory-detail-grid" aria-label="库存台账和追溯链">
      <section class="evidence-slot-panel">
        <div class="panel-title-row">
          <StatusBadge label="附件/视频/台账" tone="blue" />
        </div>
        <h2 class="compact-panel-title">追溯证据</h2>
        <div class="evidence-health compact-evidence-health">
          <article v-for="item in workspace.evidence" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.link }}</span>
            </div>
            <StatusBadge :label="item.status" :tone="item.tone" />
          </article>
        </div>
      </section>
      <section class="module-rule-panel">
        <div class="panel-title-row">
          <StatusBadge label="流转摘要" tone="blue" />
        </div>
        <div class="timeline compact-timeline">
          <article v-for="event in workspace.timeline" :key="`${event.time}-${event.bizCode}`">
            <time>{{ event.time }}</time>
            <div>
              <strong>{{ event.node }}</strong>
              <span>{{ event.bizCode }} / {{ event.operator }}</span>
              <small>{{ event.evidence }}</small>
            </div>
          </article>
        </div>
      </section>

    </section>
    </div>
    <StockLedgerPanel v-if="ledgerCreated" v-show="stockTab === 'ledger'" />
    <section v-for="draft in localAftercare" :key="draft.key" v-show="localAftercareKey === draft.key" :inert="localAftercareKey !== draft.key || undefined">
      <button class="secondary-button" type="button" @click="localAftercareKey = ''">收起退售后</button>
      <InventoryAftercarePanel :row="draft.row" :active="localAftercareKey === draft.key" @saved="loadInventoryWorkspace" @new-request="row => localAftercareDraft(row, true)" />
    </section>
  </div>
</template>
