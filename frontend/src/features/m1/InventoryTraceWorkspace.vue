<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  inventoryBalances as fallbackInventoryBalances,
  inventoryLedgerEntries as fallbackInventoryLedgerEntries,
  traceEvents as fallbackTraceEvents,
  traceEvidence as fallbackTraceEvidence,
  type Tone,
} from '../../data/m1PrototypeData';
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

const workspace = ref<InventoryWorkspaceModel>({
  balances: fallbackInventoryBalances,
  ledgerEntries: fallbackInventoryLedgerEntries,
  timeline: fallbackTraceEvents,
  evidence: fallbackTraceEvidence,
});
const shippingFinishedStock = ref(false);
const outboundShipmentResult = ref<OutboundShipmentResult | null>(null);
const outboundShipmentError = ref('');
const returningCustomerStock = ref(false);
const customerReturnResult = ref<CustomerReturnResult | null>(null);
const customerReturnError = ref('');
const reviewingAfterSalesReturn = ref(false);
const afterSalesReviewResult = ref<AfterSalesReviewResult | null>(null);
const afterSalesReviewError = ref('');

const activeBalance = computed(
  () =>
    workspace.value.balances.find((item) => item.status.includes('成品')) ??
    workspace.value.balances.find((item) => item.status.includes('在制') || item.frozen !== '0') ??
    workspace.value.balances[0],
);

const latestLedger = computed(() => workspace.value.ledgerEntries[0]);

const inventoryStats = computed(() => [
  {
    label: '客户批次',
    value: workspace.value.balances.length,
    unit: '批',
    detail: '按客户所有权隔离',
    tone: 'blue' as const,
  },
  {
    label: '台账事件',
    value: workspace.value.ledgerEntries.length,
    unit: '条',
    detail: '只追加不改余额',
    tone: 'green' as const,
  },
  {
    label: '冻结库存',
    value: workspace.value.balances.filter((item) => item.frozen !== '0').length,
    unit: '批',
    detail: '质检/退货/异常锁定',
    tone: 'amber' as const,
  },
  {
    label: '证据缺口',
    value: workspace.value.evidence.filter((item) => item.tone === 'red' || item.tone === 'amber').length,
    unit: '项',
    detail: '附件或硬件证据待补',
    tone: 'red' as const,
  },
]);

const inventoryFlow = [
  { code: 'OWN', name: '客户所有权', status: '来料和自有库存隔离', tone: 'blue' },
  { code: 'LOT', name: '批次库存', status: '母单/工单/出库串联', tone: 'green' },
  { code: 'LEDGER', name: '库存台账', status: '每次变化可解释', tone: 'green' },
  { code: 'FREEZE', name: '冻结库存', status: '质检异常不可流转', tone: 'amber' },
  { code: 'RETURN', name: '退货复核', status: '回 RETURN-CHECK', tone: 'red' },
] as const;

const inventoryAlerts = computed(() => [
  ...workspace.value.evidence.map((item) => ({
    code: item.label,
    title: item.status,
    detail: item.link,
    owner: item.status.includes('待') ? '责任人待补' : '系统归档',
    tone: item.tone,
  })),
  ...workspace.value.balances
    .filter((item) => item.frozen !== '0' || item.status.includes('返工'))
    .map((item) => ({
      code: item.batch,
      title: item.status,
      detail: `${item.owner} / ${item.material} / 冻结 ${item.frozen}`,
      owner: item.location,
      tone: item.status.includes('返工') ? ('violet' as const) : ('amber' as const),
    })),
]);

function inventoryBalanceTone(status: string): Tone {
  if (status.includes('返工')) return 'violet';
  if (status.includes('在制')) return 'amber';
  if (status.includes('成品')) return 'green';
  return 'blue';
}

onMounted(async () => {
  try {
    workspace.value = await fetchInventoryWorkspace();
  } catch {
    // Keep deterministic local samples available when the backend is not running.
  }
});

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
    outboundShipmentResult.value = await shipFinishedStock(activeBalance.value);
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
    customerReturnResult.value = await returnCustomerShipment(activeBalance.value);
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
    afterSalesReviewResult.value = await reviewAfterSalesReturn(activeBalance.value);
  } catch (error) {
    afterSalesReviewError.value = error instanceof Error ? error.message : '售后复核分流失败';
  } finally {
    reviewingAfterSalesReturn.value = false;
  }
}
</script>

<template>
  <div class="inventory-trace-desk page-stack">
    <section class="module-command-surface inventory-command-surface" aria-label="库存追溯作业台">
      <div class="module-command-main">
        <div class="console-title-line">
          <span class="eyebrow">TRACE DESK</span>
          <StatusBadge label="客户所有权隔离" tone="blue" />
        </div>
        <div class="module-command-title">
          <h2>库存台账追溯作业台</h2>
          <div class="module-date-card">
            <strong>库存中心</strong>
            <span>余额 / 台账 / 证据</span>
          </div>
        </div>

        <div class="module-stat-grid">
          <article v-for="item in inventoryStats" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>

        <div class="module-flow-strip" aria-label="库存追溯链路">
          <article v-for="step in inventoryFlow" :key="step.code" :class="`tone-${step.tone}`">
            <b>{{ step.code }}</b>
            <div>
              <strong>{{ step.name }}</strong>
              <span>{{ step.status }}</span>
            </div>
          </article>
        </div>
      </div>

      <aside class="module-priority-panel">
        <div>
          <span class="eyebrow">TRACE BLOCKERS</span>
          <strong>追溯阻断队列</strong>
        </div>
        <article v-for="row in inventoryAlerts.slice(0, 4)" :key="row.code" :class="`tone-${row.tone}`">
          <StatusBadge :label="row.title" :tone="row.tone" />
          <div>
            <strong>{{ row.code }}</strong>
            <span>{{ row.detail }}</span>
            <small>{{ row.owner }}</small>
          </div>
        </article>
      </aside>
    </section>

    <section class="module-work-grid inventory-work-grid" aria-label="库存追溯主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <span class="eyebrow">STOCK LEDGER POOL</span>
            <h2>客户库存池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>追溯关键字</span>
              <input value="ZD-20260706-001" aria-label="库存追溯关键字" />
            </label>
            <button class="primary-button" type="button">追溯查询</button>
            <button class="secondary-button" type="button" :disabled="shippingFinishedStock" @click="shipActiveFinishedStock">
              {{ shippingFinishedStock ? '正在出库' : '确认出库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="returningCustomerStock" @click="returnActiveCustomerShipment">
              {{ returningCustomerStock ? '正在退货' : '退货入库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="reviewingAfterSalesReturn" @click="reviewActiveAfterSalesReturn">
              {{ reviewingAfterSalesReturn ? '正在复核' : '售后复核' }}
            </button>
          </div>
        </div>

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
              <tr v-for="item in workspace.balances" :key="`${item.owner}-${item.batch}-${item.status}`">
                <td><strong>{{ item.owner }}</strong></td>
                <td>{{ item.batch }}</td>
                <td>{{ item.material }}</td>
                <td>{{ item.location }}</td>
                <td><StatusBadge :label="item.status" :tone="inventoryBalanceTone(item.status)" /></td>
                <td>{{ item.qty }} 件 / {{ item.weight }}</td>
                <td>{{ item.frozen }}</td>
                <td><button class="table-action" type="button">看台账</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeBalance" class="module-side-panel">
        <section class="active-object-card tone-blue">
          <div class="panel-title-row">
            <span class="eyebrow">ACTIVE STOCK</span>
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
          <div class="detail-actions">
            <button class="primary-button" type="button">打开追溯链</button>
            <button class="secondary-button" type="button" :disabled="shippingFinishedStock" @click="shipActiveFinishedStock">
              {{ shippingFinishedStock ? '正在出库' : '确认出库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="returningCustomerStock" @click="returnActiveCustomerShipment">
              {{ returningCustomerStock ? '正在退货' : '退货入库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="reviewingAfterSalesReturn" @click="reviewActiveAfterSalesReturn">
              {{ reviewingAfterSalesReturn ? '正在复核' : '售后复核' }}
            </button>
            <button class="secondary-button" type="button">导出台账</button>
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

        <section class="evidence-slot-panel">
          <div class="panel-title-row">
            <span class="eyebrow">TRACE EVIDENCE</span>
            <StatusBadge label="附件/视频/台账" tone="blue" />
          </div>
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
      </aside>
    </section>

    <section class="module-detail-grid inventory-detail-grid" aria-label="库存台账和追溯链">
      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">LEDGER</span>
          <StatusBadge label="只追加" tone="green" />
        </div>
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
          <span class="eyebrow">TIMELINE</span>
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

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">RULES</span>
          <StatusBadge label="库存原则" tone="amber" />
        </div>
        <div class="rule-stack">
          <article>
            <strong>客户所有权</strong>
            <span>客户来料库存与企业自有库存必须严格隔离。</span>
          </article>
          <article>
            <strong>台账优先</strong>
            <span>余额来自台账汇总，业务页面不直接修改余额。</span>
          </article>
          <article>
            <strong>退货复核</strong>
            <span>客户退货先回 RETURN-CHECK，再通过售后复核决定放行、返工、冻结或报废。</span>
          </article>
        </div>
      </section>
    </section>

    <section class="operations-hero">
      <div class="hero-copy">
        <span class="eyebrow">Trace Control</span>
        <h2>库存与追溯证据台</h2>
        <p>库存余额只看当前结果，台账明细解释每一次变化，证据清单证明业务动作真实发生。</p>
      </div>
      <div class="hero-kpis">
        <article>
          <span>客户库存批次</span>
          <strong>{{ workspace.balances.length }}</strong>
        </article>
        <article>
          <span>台账事件</span>
          <strong>{{ workspace.ledgerEntries.length }}</strong>
        </article>
        <article>
          <span>证据缺口</span>
          <strong>3</strong>
        </article>
      </div>
    </section>

    <section class="control-filter-bar" aria-label="库存追溯筛选">
      <div>
        <span>查询维度</span>
        <strong>客户 / 批次 / 物料 / 库位 / 状态</strong>
      </div>
      <div>
        <span>追溯对象</span>
        <strong>MD-20260706-001 到 ZD-20260706-001</strong>
      </div>
      <div>
        <span>台账原则</span>
        <strong>只追加，不直接改余额</strong>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" type="button">冻结库存</button>
        <button class="secondary-button" type="button" :disabled="shippingFinishedStock" @click="shipActiveFinishedStock">
          {{ shippingFinishedStock ? '正在出库' : '确认出库' }}
        </button>
        <button class="secondary-button" type="button" :disabled="returningCustomerStock" @click="returnActiveCustomerShipment">
          {{ returningCustomerStock ? '正在退货' : '退货入库' }}
        </button>
        <button class="secondary-button" type="button">证据缺口</button>
      </div>
    </section>

    <section class="module-ops-board" aria-label="库存追溯主控台">
      <div class="module-master">
        <div class="module-toolbar">
          <div>
            <span class="eyebrow">Inventory Desk</span>
            <h2>库存追溯主控台</h2>
            <p>从余额、台账、证据和冻结状态同时查询，确保每次库存变化都有业务来源。</p>
          </div>
          <div class="module-tools">
            <label>
              <span>追溯关键字</span>
              <input value="ZD-20260706-001" aria-label="库存追溯关键字" />
            </label>
            <button class="primary-button" type="button">追溯查询</button>
            <button class="secondary-button" type="button" :disabled="shippingFinishedStock" @click="shipActiveFinishedStock">
              {{ shippingFinishedStock ? '正在出库' : '确认出库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="returningCustomerStock" @click="returnActiveCustomerShipment">
              {{ returningCustomerStock ? '正在退货' : '退货入库' }}
            </button>
          </div>
        </div>
        <div class="module-table-wrap">
          <table class="module-table">
            <thead>
              <tr>
                <th>客户所有权</th>
                <th>批次/来源</th>
                <th>物料</th>
                <th>位置</th>
                <th>状态</th>
                <th>数量重量</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in workspace.balances" :key="`${item.owner}-${item.batch}-${item.status}`">
                <td><strong>{{ item.owner }}</strong></td>
                <td>{{ item.batch }}</td>
                <td>{{ item.material }}</td>
                <td>{{ item.location }}</td>
                <td><StatusBadge :label="item.status" :tone="item.status.includes('返工') ? 'violet' : item.status.includes('在制') ? 'amber' : item.status.includes('成品') ? 'green' : 'blue'" /></td>
                <td>{{ item.qty }} 件 / {{ item.weight }}</td>
                <td><button class="table-action" type="button">看台账</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeBalance" class="module-detail">
        <div class="detail-title">
          <span>当前库存</span>
          <strong>{{ activeBalance.batch }}</strong>
          <StatusBadge :label="activeBalance.status" :tone="activeBalance.status.includes('在制') ? 'amber' : activeBalance.status.includes('成品') ? 'green' : 'blue'" />
        </div>
        <dl class="detail-grid">
          <dt>客户</dt>
          <dd>{{ activeBalance.owner }}</dd>
          <dt>物料</dt>
          <dd>{{ activeBalance.material }}</dd>
          <dt>位置</dt>
          <dd>{{ activeBalance.location }}</dd>
          <dt>数量重量</dt>
          <dd>{{ activeBalance.qty }} 件 / {{ activeBalance.weight }}</dd>
          <dt>冻结</dt>
          <dd>{{ activeBalance.frozen }}</dd>
          <dt>最近台账</dt>
          <dd>{{ latestLedger?.bizCode ?? '暂无' }} / {{ latestLedger?.movement ?? '暂无' }}</dd>
        </dl>
        <div class="detail-actions">
          <button class="primary-button" type="button">打开追溯链</button>
          <button class="secondary-button" type="button" :disabled="shippingFinishedStock" @click="shipActiveFinishedStock">
            {{ shippingFinishedStock ? '正在出库' : '确认出库' }}
          </button>
          <button class="secondary-button" type="button" :disabled="returningCustomerStock" @click="returnActiveCustomerShipment">
            {{ returningCustomerStock ? '正在退货' : '退货入库' }}
          </button>
          <button class="secondary-button" type="button">导出台账</button>
          <button class="secondary-button" type="button">查看证据缺口</button>
        </div>
        <div v-if="outboundShipmentResult" class="operation-feedback success">
          <strong>成品出库已写入台账</strong>
          <span>{{ outboundShipmentResult.ledgerCode }} / {{ outboundShipmentResult.shipmentCode }}</span>
          <small>{{ outboundShipmentResult.fromStockStatus }} -> {{ outboundShipmentResult.toStockStatus }} / {{ outboundShipmentResult.logisticsNo }}</small>
        </div>
        <div v-else-if="outboundShipmentError" class="operation-feedback danger">
          <strong>确认出库失败</strong>
          <span>{{ outboundShipmentError }}</span>
          <small>请确认该客户成品库存已存在，并已完成出库证据归档。</small>
        </div>
        <div v-if="customerReturnResult" class="operation-feedback success">
          <strong>客户退货已写入台账</strong>
          <span>{{ customerReturnResult.ledgerCode }} / {{ customerReturnResult.returnCode }}</span>
          <small>{{ customerReturnResult.fromStockStatus }} -> {{ customerReturnResult.toStockStatus }} / {{ customerReturnResult.returnLogisticsNo }}</small>
        </div>
        <div v-else-if="customerReturnError" class="operation-feedback danger">
          <strong>客户退货入库失败</strong>
          <span>{{ customerReturnError }}</span>
          <small>请确认原出库单已形成已发货库存；退回后进入 RETURN-CHECK，等待复核或返工判断。</small>
        </div>
      </aside>
    </section>

    <section class="toolbar-band">
      <div>
        <h2>库存台账与全链路追溯</h2>
        <p>客户所有权、批次、仓库、状态和冻结量必须共同构成库存查询维度。</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" type="button">导出台账</button>
        <button class="primary-button" type="button">追溯查询</button>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>实时库存余额</h2>
            <p>余额只来自库存台账汇总，页面不提供直接编辑入口。</p>
          </div>
        </div>
        <div class="data-table inventory-table">
          <div class="table-row table-head">
            <span>客户所有权</span>
            <span>批次/来源</span>
            <span>物料</span>
            <span>位置</span>
            <span>状态</span>
            <span>数量/重量</span>
            <span>冻结</span>
          </div>
          <div v-for="item in workspace.balances" :key="`${item.owner}-${item.batch}-${item.status}`" class="table-row">
            <strong>{{ item.owner }}</strong>
            <span>{{ item.batch }}</span>
            <span>{{ item.material }}</span>
            <span>{{ item.location }}</span>
            <StatusBadge :label="item.status" :tone="item.status.includes('返工') ? 'violet' : item.status.includes('在制') ? 'amber' : item.status.includes('成品') ? 'green' : 'blue'" />
            <span>{{ item.qty }} 件 / {{ item.weight }}</span>
            <span>{{ item.frozen }}</span>
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>追溯证据</h2>
            <p>证据缺口必须能定位到具体任务或附件。</p>
          </div>
        </div>
        <div class="evidence-health">
          <article v-for="item in workspace.evidence" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.link }}</span>
            </div>
            <StatusBadge :label="item.status" :tone="item.tone" />
          </article>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>库存台账明细</h2>
          <p>这张表用来解释余额从哪里来，后续必须成为库存问题排查的第一入口。</p>
        </div>
      </div>
      <div class="ledger-table">
        <div class="ledger-row ledger-head">
          <span>时间</span>
          <span>台账号</span>
          <span>动作</span>
          <span>变化前</span>
          <span>变化后</span>
          <span>来源单据</span>
          <span>操作人</span>
        </div>
        <div v-for="entry in workspace.ledgerEntries" :key="entry.bizCode" class="ledger-row">
          <time>{{ entry.time }}</time>
          <strong>{{ entry.bizCode }}</strong>
          <span>{{ entry.movement }}</span>
          <span>{{ entry.before }}</span>
          <span>{{ entry.after }}</span>
          <span>{{ entry.source }}</span>
          <span>{{ entry.operator }}</span>
        </div>
      </div>
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>追溯时间线</h2>
            <p>样例对象：MD-20260706-001 / ZD-20260706-001。</p>
          </div>
        </div>
        <div class="timeline">
          <article v-for="event in workspace.timeline" :key="`${event.time}-${event.bizCode}`">
            <time>{{ event.time }}</time>
            <div>
              <strong>{{ event.node }}</strong>
              <span>{{ event.bizCode }} / {{ event.operator }}</span>
              <small>{{ event.evidence }}</small>
            </div>
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>台账写入原则</h2>
            <p>所有业务单据只能调用库存服务写台账。</p>
          </div>
        </div>
        <div class="rule-grid compact">
          <article>
            <strong>来料入库</strong>
            <span>外部来料转客户所有待加工库存。</span>
          </article>
          <article>
            <strong>工单开工</strong>
            <span>待加工转在制，不允许负库存。</span>
          </article>
          <article>
            <strong>质检报废</strong>
            <span>在制转报废，记录责任归因。</span>
          </article>
          <article>
            <strong>出库发货</strong>
            <span>成品确认后通过出库单转为已发货库存状态。</span>
          </article>
          <article>
            <strong>客户退货</strong>
            <span>从已发货库存反向写入退货复核库，保留原出库单和退回物流证据。</span>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>
