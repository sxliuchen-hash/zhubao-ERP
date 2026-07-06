<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
import {
  inboundCheckpoints as fallbackInboundCheckpoints,
  inboundOrders as fallbackInboundOrders,
  inboundRisks as fallbackInboundRisks,
} from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  type InboundWorkspaceModel,
} from '../../services/businessWorkspaceApi';
import {
  confirmInbound,
  type InboundConfirmationResult,
} from '../../services/businessOperationApi';

const workspace = ref<InboundWorkspaceModel>({
  checkpoints: fallbackInboundCheckpoints,
  orders: fallbackInboundOrders,
  risks: fallbackInboundRisks,
});
const confirmingInbound = ref(false);
const confirmationResult = ref<InboundConfirmationResult | null>(null);
const confirmationError = ref('');

const activeOrder = computed(
  () =>
    workspace.value.orders.find((order) => order.status.includes('异常') || order.video === '失败') ??
    workspace.value.orders[0],
);

const activeRisk = computed(
  () => workspace.value.risks.find((risk) => risk.code === activeOrder.value?.code) ?? workspace.value.risks[0],
);

const inboundStats = computed(() => [
  {
    label: '待收货',
    value: workspace.value.orders.filter((order) => order.status.includes('待')).length,
    unit: '单',
    detail: '快递/母单待绑定',
    tone: 'amber' as Tone,
  },
  {
    label: '异常母单',
    value: workspace.value.orders.filter((order) => order.status.includes('异常') || order.video === '失败').length,
    unit: '单',
    detail: '优先处理视频和物料阻断',
    tone: 'red' as Tone,
  },
  {
    label: '已入库',
    value: workspace.value.orders.filter((order) => order.status.includes('已入库')).length,
    unit: '单',
    detail: '写入客户所有库存',
    tone: 'green' as Tone,
  },
  {
    label: '证据槽',
    value: workspace.value.checkpoints.length,
    unit: '项',
    detail: '称重/视频/标签/快递',
    tone: 'blue' as Tone,
  },
]);

const inboundFlow = [
  { code: 'SCAN', name: '扫码收货', status: '快递单绑定母单', tone: 'green' },
  { code: 'PACK', name: '拆包称重', status: '前后重量留证', tone: 'amber' },
  { code: 'VID', name: '视频截取', status: '失败可人工上传', tone: 'red' },
  { code: 'LABEL', name: '标签打印', status: '母单/批次可追溯', tone: 'blue' },
  { code: 'LEDGER', name: '确认入库', status: '写客户所有库存', tone: 'green' },
] as const;

onMounted(async () => {
  try {
    workspace.value = await fetchInboundWorkspace();
  } catch {
    // Keep deterministic local samples available when the backend is not running.
  }
});

function videoTone(video: string): Tone {
  if (video === '失败') return 'red';
  if (video === '截取中') return 'amber';
  if (video === '已关联') return 'green';
  return 'gray';
}

function statusTone(status: string): Tone {
  if (status.includes('异常')) return 'red';
  if (status.includes('待')) return 'amber';
  return 'green';
}

async function confirmActiveInbound() {
  if (!activeOrder.value) return;

  confirmingInbound.value = true;
  confirmationResult.value = null;
  confirmationError.value = '';

  try {
    confirmationResult.value = await confirmInbound(activeOrder.value);
  } catch (error) {
    confirmationError.value = error instanceof Error ? error.message : '确认入库失败';
  } finally {
    confirmingInbound.value = false;
  }
}
</script>

<template>
  <div class="inbound-desk page-stack">
    <section class="module-command-surface inbound-command-surface" aria-label="来料收货拆包作业台">
      <div class="module-command-main">
        <div class="console-title-line">
          <span class="eyebrow">INBOUND DESK</span>
          <StatusBadge label="客户所有权隔离" tone="blue" />
        </div>
        <div class="module-command-title">
          <h2>来料收货拆包作业台</h2>
          <div class="module-date-card">
            <strong>白班</strong>
            <span>收货 / 拆包 / 入库</span>
          </div>
        </div>

        <div class="module-stat-grid">
          <article v-for="item in inboundStats" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>

        <div class="module-flow-strip" aria-label="来料入库链路">
          <article v-for="step in inboundFlow" :key="step.code" :class="`tone-${step.tone}`">
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
          <span class="eyebrow">EXCEPTION</span>
          <strong>优先处理</strong>
        </div>
        <article v-for="risk in workspace.risks" :key="risk.code" :class="`tone-${risk.tone}`">
          <StatusBadge :label="risk.action" :tone="risk.tone" />
          <div>
            <strong>{{ risk.title }}</strong>
            <span>{{ risk.detail }}</span>
            <small>{{ risk.code }} · {{ risk.owner }}</small>
          </div>
        </article>
      </aside>
    </section>

    <section class="module-work-grid inbound-work-grid" aria-label="来料作业主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <span class="eyebrow">MOTHER ORDER POOL</span>
            <h2>今日母单池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>扫码入口</span>
              <input value="SF1048329001" aria-label="快递或母单扫码" />
            </label>
            <button class="primary-button" type="button">扫码收货</button>
            <button class="secondary-button" type="button">批量打印</button>
          </div>
        </div>

        <div class="module-table-wrap refined-table-wrap">
          <table class="module-table inbound-dispatch-table">
            <thead>
              <tr>
                <th>母单</th>
                <th>客户/物料</th>
                <th>快递单</th>
                <th>数量重量</th>
                <th>入库状态</th>
                <th>视频证据</th>
                <th>权属</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in workspace.orders" :key="order.code">
                <td><strong>{{ order.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.customer }}</b>
                    <small>{{ order.material }}</small>
                  </span>
                </td>
                <td>{{ order.courierNo }}</td>
                <td>{{ order.qty }} 件 / {{ order.weight }}</td>
                <td><StatusBadge :label="order.status" :tone="statusTone(order.status)" /></td>
                <td><StatusBadge :label="order.video" :tone="videoTone(order.video)" /></td>
                <td>{{ order.owner }}</td>
                <td><button class="table-action" type="button">处理</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeOrder" class="module-side-panel">
        <section class="active-object-card tone-amber">
          <div class="panel-title-row">
            <span class="eyebrow">ACTIVE MOTHER ORDER</span>
            <StatusBadge :label="activeOrder.status" :tone="statusTone(activeOrder.status)" />
          </div>
          <strong>{{ activeOrder.code }}</strong>
          <dl class="detail-grid">
            <dt>客户</dt>
            <dd>{{ activeOrder.customer }}</dd>
            <dt>物料</dt>
            <dd>{{ activeOrder.material }}</dd>
            <dt>快递单</dt>
            <dd>{{ activeOrder.courierNo }}</dd>
            <dt>权属</dt>
            <dd>{{ activeOrder.owner }}</dd>
            <dt>数量重量</dt>
            <dd>{{ activeOrder.qty }} 件 / {{ activeOrder.weight }}</dd>
            <dt>风险</dt>
            <dd>{{ activeRisk?.title ?? '无阻断' }}</dd>
          </dl>
          <div class="detail-actions three-actions">
            <button class="primary-button" type="button" :disabled="confirmingInbound" @click="confirmActiveInbound">
              {{ confirmingInbound ? '正在入账' : '确认入库' }}
            </button>
            <button class="secondary-button" type="button">视频任务</button>
            <button class="secondary-button" type="button">打印标签</button>
          </div>
          <div v-if="confirmationResult" class="operation-feedback success">
            <strong>库存台账已写入</strong>
            <span>{{ confirmationResult.ledgerCode }} / {{ confirmationResult.motherOrderCode }}</span>
            <small>{{ confirmationResult.warehouseCode }} · {{ confirmationResult.stockStatus }}</small>
          </div>
          <div v-else-if="confirmationError" class="operation-feedback danger">
            <strong>确认入库失败</strong>
            <span>{{ confirmationError }}</span>
            <small>请确认后端 API 已启动，并检查数量、重量和库存维度。</small>
          </div>
        </section>

        <section class="evidence-slot-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">EVIDENCE SLOTS</span>
              <h2>入库证据槽</h2>
            </div>
          </div>
          <div class="evidence-health compact-evidence-health">
            <article v-for="item in workspace.checkpoints" :key="item.label">
              <div>
                <strong>{{ item.label }}</strong>
                <span>{{ item.status }}</span>
              </div>
              <StatusBadge :label="item.value" :tone="item.tone" />
            </article>
          </div>
        </section>
      </aside>
    </section>

    <section class="module-detail-grid" aria-label="称重、标签和规则">
      <div class="section-block module-rule-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">WEIGHING</span>
            <h2>拆包称重交接</h2>
            <p>用于解释每一次重量变化，并给损耗归因留下证据。</p>
          </div>
        </div>
        <div class="form-grid">
          <label>
            <span>母单</span>
            <input value="MD-20260706-001" readonly />
          </label>
          <label>
            <span>拆包前重量</span>
            <input value="486.80 g" readonly />
          </label>
          <label>
            <span>确认入库重量</span>
            <input value="486.25 g" readonly />
          </label>
          <label>
            <span>交接人</span>
            <input value="何琳 / 马杰" readonly />
          </label>
        </div>
        <div class="notice-list">
          <p>重量差 0.55 g，低于系统参数 `loss.default.tolerance`，允许确认入库。</p>
          <p>拆包视频状态为待开始，确认入库前需要创建视频截取任务。</p>
        </div>
      </div>

      <div class="section-block module-rule-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">LABEL</span>
            <h2>母单标签预览</h2>
            <p>标签必须支持扫码追溯到客户、批次、物料、库存台账和视频证据。</p>
          </div>
        </div>
        <div class="label-preview">
          <div class="qr-box">QR</div>
          <div>
            <strong>MD-20260706-001</strong>
            <span>恒信珠宝 / 18K 金戒托</span>
            <span>客户所有 / 待加工 / 原料库 A-01</span>
            <span>数量 320 件 / 重量 486.25 g</span>
          </div>
        </div>
        <div class="button-row">
          <button class="secondary-button" type="button">打印标签</button>
          <button class="secondary-button" type="button">补打记录</button>
        </div>
      </div>

      <div class="section-block module-rule-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">RULES</span>
            <h2>入库校验规则</h2>
            <p>确认入库必须由证据链和客户所有权共同约束。</p>
          </div>
        </div>
        <div class="rule-stack">
          <article>
            <strong>客户所有权</strong>
            <span>所有来料默认进入客户所有库存，不混入企业自有库存。</span>
          </article>
          <article>
            <strong>视频兜底</strong>
            <span>NVR 截取失败时允许人工上传，但必须保留失败日志。</span>
          </article>
          <article>
            <strong>称重留痕</strong>
            <span>拆包前后重量和交接人必须进入后续损耗归因口径。</span>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>
