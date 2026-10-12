<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import OperationInputs from './OperationInputs.vue';
import { createAmountForm } from './operationAmounts';
import { operationDraft } from './operationDraft';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import ProgressNoteForm from '../scan/ProgressNoteForm.vue';
import ShippingPanel from '../shipping/ShippingPanel.vue';
import { progressTargetOf } from '../scan/progressTarget';
import type { InventoryBalance, Tone } from '../../data/m1PrototypeData';
import {
  fetchInventoryWorkspace,
  type InventoryWorkspaceModel,
} from '../../services/businessWorkspaceApi';
import {
  reviewAfterSalesReturn,
  returnCustomerShipment,
  shipFinishedStock,
  type AfterSalesReviewResult,
  type CustomerReturnResult,
  type OutboundShipmentResult,
} from '../../services/businessOperationApi';

const emptyInventory = (): InventoryWorkspaceModel => ({
  balances: [],
  ledgerEntries: [],
  timeline: [],
  evidence: [],
});

const workspace = ref<InventoryWorkspaceModel>(emptyInventory());
const loadError = ref('');
const { operationValues, resetAmounts, assertAmounts, amountHint, amountDefaultNote } = createAmountForm();
const shippingFinishedStock = ref(false);
const outboundShipmentResult = ref<OutboundShipmentResult | null>(null);
const outboundShipmentError = ref('');
const returningCustomerStock = ref(false);
const customerReturnResult = ref<CustomerReturnResult | null>(null);
const customerReturnError = ref('');
const reviewingAfterSalesReturn = ref(false);
const afterSalesReviewResult = ref<AfterSalesReviewResult | null>(null);
const afterSalesReviewError = ref('');
const selectedBalanceKey = ref('');
const { keyword, filtered: filteredBalances } = useDeskFilter(
  () => workspace.value.balances,
  (item) => `${item.owner} ${item.batch} ${item.material} ${item.location} ${item.status}`,
);

const activeBalance = computed(
  () =>
    workspace.value.balances.find((item) => balanceKey(item) === selectedBalanceKey.value) ??
    workspace.value.balances.find((item) => item.status.includes('成品')) ??
    workspace.value.balances.find((item) => item.status.includes('在制') || item.frozen !== '0') ??
    workspace.value.balances[0],
);
const shippingProgressTarget = computed(() => progressTargetOf(activeBalance.value?.operationContext));

const latestLedger = computed(
  () =>
    workspace.value.ledgerEntries.find((entry) => entry.source === activeBalance.value?.batch) ??
    workspace.value.ledgerEntries[0],
);

watch(activeBalance, (balance) => { resetAmounts(balance?.operationContext); });

function balanceKey(item: InventoryBalance) {
  const context = item.operationContext;
  return JSON.stringify([item.owner, item.batch, item.material, item.location, item.status,
    context?.customerId, context?.ownerCustomerId, context?.motherOrderCode, context?.workOrderCode,
    context?.materialCode, context?.warehouseCode, context?.ownerType, context?.stockStatus]);
}

function inventoryBalanceTone(status: string): Tone {
  if (status.includes('返工')) return 'violet';
  if (status.includes('在制')) return 'amber';
  if (status.includes('成品')) return 'green';
  return 'blue';
}

onMounted(() => {
  void loadInventoryWorkspace();
});

async function loadInventoryWorkspace() {
  loadError.value = '';
  try {
    const loaded = await fetchInventoryWorkspace();
    workspace.value = loaded;
    if (loaded.balances.length === 0 && loaded.ledgerEntries.length === 0) {
      loadError.value = '接口没有返回库存单据';
    }
  } catch (error) {
    workspace.value = emptyInventory();
    loadError.value = error instanceof Error ? error.message : '库存单据加载失败';
  } finally {
    if (!workspace.value.balances.some((item) => balanceKey(item) === selectedBalanceKey.value)) {
      selectedBalanceKey.value = activeBalance.value ? balanceKey(activeBalance.value) : '';
    }
  }
}

async function shipActiveFinishedStock() {
  if (!activeBalance.value) return;

  shippingFinishedStock.value = true;
  outboundShipmentResult.value = null;
  outboundShipmentError.value = '';
  customerReturnResult.value = null;
  customerReturnError.value = '';
  afterSalesReviewResult.value = null;
  afterSalesReviewError.value = '';

  try {
    assertAmounts();
    outboundShipmentResult.value = await shipFinishedStock(activeBalance.value, operationDraft(operationValues.value));
    await loadInventoryWorkspace();
  } catch (error) {
    outboundShipmentError.value = error instanceof Error ? error.message : '确认出库失败';
  } finally {
    shippingFinishedStock.value = false;
  }
}

async function returnActiveCustomerShipment() {
  if (!activeBalance.value) return;

  returningCustomerStock.value = true;
  customerReturnResult.value = null;
  customerReturnError.value = '';
  afterSalesReviewResult.value = null;
  afterSalesReviewError.value = '';

  try {
    assertAmounts();
    customerReturnResult.value = await returnCustomerShipment(activeBalance.value, operationDraft(operationValues.value));
    await loadInventoryWorkspace();
  } catch (error) {
    customerReturnError.value = error instanceof Error ? error.message : '客户退货入库失败';
  } finally {
    returningCustomerStock.value = false;
  }
}

async function reviewActiveAfterSalesReturn() {
  if (!activeBalance.value) return;

  reviewingAfterSalesReturn.value = true;
  afterSalesReviewResult.value = null;
  afterSalesReviewError.value = '';

  try {
    assertAmounts();
    afterSalesReviewResult.value = await reviewAfterSalesReturn(activeBalance.value, operationDraft(operationValues.value));
    await loadInventoryWorkspace();
  } catch (error) {
    afterSalesReviewError.value = error instanceof Error ? error.message : '售后复核分流失败';
  } finally {
    reviewingAfterSalesReturn.value = false;
  }
}

defineExpose({ reloadInventory: loadInventoryWorkspace });

function selectBalance(item: InventoryBalance) {
  selectedBalanceKey.value = balanceKey(item);
  outboundShipmentResult.value = null;
  outboundShipmentError.value = '';
  customerReturnResult.value = null;
  customerReturnError.value = '';
  afterSalesReviewResult.value = null;
  afterSalesReviewError.value = '';
}
</script>

<template>
  <div class="inventory-trace-desk page-stack">
    <p v-if="workspace.dataSource === 'DEMO'" class="operation-feedback warning">当前显示评审样例，仅用于演示。</p>
    <ShippingPanel
      title="确认发货"
      intro="扫成品标签。选择完整库存维度，并填写本次数量、重量和物流。物流扫码只填写运单号。超时用原确认回放，不会另开一次发货。"
    />
    <section class="module-work-grid inventory-work-grid" aria-label="库存追溯主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>客户库存池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>查找</span>
              <input v-model="keyword" placeholder="客户、批次或物料" aria-label="库存追溯关键字" />
            </label>
          </div>
        </div>

        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="module-table-wrap refined-table-wrap">
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
                v-for="item in filteredBalances"
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
                <td>{{ item.qty }} 件 / {{ item.weight }}</td>
                <td>{{ item.frozen }}</td>
                <td><button class="table-action" type="button" @click.stop="selectBalance(item)">看台账</button></td>
              </tr>
              <tr v-if="filteredBalances.length === 0">
                <td colspan="8">暂无库存单据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeBalance" class="module-side-panel">
        <section class="active-object-card tone-blue">
          <div class="panel-title-row">
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
            <dd>{{ activeBalance.qty }} 件 / {{ activeBalance.weight }}</dd>
            <dt>冻结</dt>
            <dd>{{ activeBalance.frozen }}</dd>
            <dt>最近台账</dt>
            <dd>{{ latestLedger?.bizCode ?? '暂无' }}</dd>
          </dl>
          <OperationInputs v-model="operationValues" :fields="[['shipmentCode', '出库单号'], ['logisticsNo', '发货运单号'], ['originalShipmentCode', '原出库单号'], ['returnCode', '退货单号'], ['returnLogisticsNo', '退货运单号'], ['reason', '退货原因'], ['reviewCode', '售后复核单号'], ['disposition', '处理结论'], ['finding', '复核说明'], ['quantity', '本次数量', 'number'], ['weight', '本次重量（克）', 'number'], ['evidenceCode', '证据编号']]" />
          <p v-if="amountDefaultNote" class="operation-feedback">{{ amountDefaultNote }}</p>
          <p v-if="amountHint" class="operation-feedback warning">{{ amountHint }}</p>
          <ProgressNoteForm
            v-if="shippingProgressTarget"
            stage="SHIPPING"
            :target-type="shippingProgressTarget.targetType"
            :target-code="shippingProgressTarget.targetCode"
            :mother-order-code="shippingProgressTarget.motherOrderCode"
          />
          <div class="detail-actions">
            <button class="primary-button" type="button" :disabled="shippingFinishedStock" @click="shipActiveFinishedStock">
              {{ shippingFinishedStock ? '正在出库' : '确认出库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="returningCustomerStock" @click="returnActiveCustomerShipment">
              {{ returningCustomerStock ? '正在退货' : '退货入库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="reviewingAfterSalesReturn" @click="reviewActiveAfterSalesReturn">
              {{ reviewingAfterSalesReturn ? '正在复核' : '售后复核' }}
            </button>
          </div>
          <div v-if="outboundShipmentResult" class="operation-feedback success">
            <strong>成品出库已写入台账</strong>
            <span>{{ outboundShipmentResult.ledgerCode }} / {{ outboundShipmentResult.shipmentCode }}</span>
            <small>{{ outboundShipmentResult.fromStockStatus }} -> {{ outboundShipmentResult.toStockStatus }} / {{ outboundShipmentResult.logisticsNo }}</small>
          </div>
          <div v-else-if="outboundShipmentError" class="operation-feedback danger">
            <strong>确认出库失败</strong>
            <span>{{ outboundShipmentError }}</span>
          </div>
          <div v-if="customerReturnResult" class="operation-feedback success">
            <strong>客户退货已写入台账</strong>
            <span>{{ customerReturnResult.ledgerCode }} / {{ customerReturnResult.returnCode }}</span>
            <small>{{ customerReturnResult.fromStockStatus }} -> {{ customerReturnResult.toStockStatus }} / {{ customerReturnResult.returnLogisticsNo }}</small>
          </div>
          <div v-else-if="customerReturnError" class="operation-feedback danger">
            <strong>客户退货入库失败</strong>
            <span>{{ customerReturnError }}</span>
          </div>
          <div v-if="afterSalesReviewResult" class="operation-feedback success">
            <strong>售后复核已写入台账</strong>
            <span>{{ afterSalesReviewResult.ledgerCode }} / {{ afterSalesReviewResult.reviewCode }}</span>
            <small>{{ afterSalesReviewResult.fromStockStatus }} -> {{ afterSalesReviewResult.toStockStatus }} / {{ afterSalesReviewResult.disposition }}</small>
          </div>
          <div v-else-if="afterSalesReviewError" class="operation-feedback danger">
            <strong>售后复核分流失败</strong>
            <span>{{ afterSalesReviewError }}</span>
            <small>请确认退货库存已进入 RETURN-CHECK，并已补齐复核照片或视频证据。</small>
          </div>
        </section>
      </aside>

    <section class="module-detail-grid inventory-detail-grid" aria-label="库存台账和追溯链">
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
          <StatusBadge label="只追加" tone="green" />
        </div>
        <h2 class="compact-panel-title">库存台账明细</h2>
        <div class="ledger-table compact-ledger-table">
          <div class="ledger-row ledger-head">
            <span>时间</span>
            <span>台账号</span>
            <span>动作</span>
            <span>变化后</span>
            <span>来源</span>
          </div>
          <div v-for="entry in workspace.ledgerEntries" :key="entry.bizCode" class="ledger-row">
            <time>{{ entry.time }}</time>
            <strong>{{ entry.bizCode }}</strong>
            <span>{{ entry.movement }}</span>
            <span>{{ entry.after }}</span>
            <span>{{ entry.source }}</span>
          </div>
        </div>
      </section>

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <StatusBadge label="全链路" tone="blue" />
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
    </section>
  </div>
</template>
