<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import type { InboundOrder, Tone } from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  type InboundWorkspaceModel,
} from '../../services/businessWorkspaceApi';
import {
  confirmInbound,
  type InboundConfirmationResult,
} from '../../services/businessOperationApi';
import { fetchCaptures, type DocumentCapture } from '../../services/documentCaptureApi';
import { fetchMotherOrder, type MobileIntakeResult } from '../../services/mobileIntakeApi';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import MotherSplitPanel from './MotherSplitPanel.vue';

const emptyInbound = (): InboundWorkspaceModel => ({
  checkpoints: [],
  orders: [],
  risks: [],
});

const workspace = ref<InboundWorkspaceModel>(emptyInbound());
const loadError = ref('');
const confirmingInbound = ref(false);
const confirmationResult = ref<InboundConfirmationResult | null>(null);
const confirmationError = ref('');
const selectedOrderCode = ref('');
const processingRequirement = ref('');
const dueDate = ref('');
const captures = ref<DocumentCapture[]>([]);
const captureError = ref('');
const motherDetail = ref<MobileIntakeResult | null>(null);
const structuredCode = ref('');
const structuredError = ref('');
const customerKeyword = ref('');
const statusKeyword = ref('');
const advancedOpen = ref(false);
const detailOpen = ref(false);
const loading = ref(false);
const { keyword, filtered: filteredOrders } = useDeskFilter(
  () => workspace.value.orders,
  (order) => `${order.code} ${order.customer} ${order.material} ${order.courierNo} ${order.goodsName} ${order.status}`,
);

const activeOrder = computed(
  () =>
    workspace.value.orders.find((order) => order.code === selectedOrderCode.value) ??
    workspace.value.orders.find((order) => order.status.includes('异常') || order.video === '失败') ??
    workspace.value.orders[0],
);

const activeRisk = computed(
  () => workspace.value.risks.find((risk) => risk.code === activeOrder.value?.code) ?? workspace.value.risks[0],
);

const evidenceOperationCode = computed(() => {
  if (!activeOrder.value || !motherDetail.value) return '';
  if (motherDetail.value.motherOrderCode !== activeOrder.value.code) return '';
  return motherDetail.value.ledgerCode ?? '';
});

const visibleOrders = computed(() => {
  const customer = customerKeyword.value.trim().toLowerCase();
  const status = statusKeyword.value.trim();
  return filteredOrders.value.filter((order) => {
    const customerMatched = !customer || order.customer.toLowerCase().includes(customer);
    const statusMatched = !status || order.status.includes(status);
    return customerMatched && statusMatched;
  });
});

onMounted(() => {
  void loadInboundWorkspace();
});

async function loadInboundWorkspace() {
  loadError.value = '';
  loading.value = true;
  try {
    const loaded = await fetchInboundWorkspace();
    workspace.value = loaded;
    if (loaded.orders.length === 0) {
      loadError.value = '接口没有返回来料单据';
    }
  } catch (error) {
    workspace.value = emptyInbound();
    loadError.value = error instanceof Error ? error.message : '来料单据加载失败';
  } finally {
    loading.value = false;
    if (!workspace.value.orders.some((order) => order.code === selectedOrderCode.value)) {
      selectedOrderCode.value = activeOrder.value?.code ?? '';
    }
    await loadOrderExtras(selectedOrderCode.value);
  }
}

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

async function loadOrderExtras(code: string) {
  captures.value = [];
  motherDetail.value = null;
  if (!code) {
    return;
  }
  try {
    motherDetail.value = await fetchMotherOrder(code);
    processingRequirement.value = motherDetail.value.processingRequirement;
    dueDate.value = motherDetail.value.dueDate;
  } catch {
    motherDetail.value = null;
  }
  try {
    captures.value = await fetchCaptures(code);
    captureError.value = '';
  } catch (error) {
    captures.value = [];
    captureError.value = error instanceof Error ? error.message : '视频暂时无法打开';
  }
}

function requirementText(order: InboundOrder) {
  if (motherDetail.value?.motherOrderCode !== order.code) {
    return '打开后核对';
  }
  const requirement = motherDetail.value.processingRequirement.trim() || '未记录';
  const due = motherDetail.value.dueDate.trim() || '未记录';
  return `${requirement} / ${due}`;
}

async function confirmActiveInbound() {
  if (!activeOrder.value) return;
  if (!processingRequirement.value.trim() || !dueDate.value.trim()) {
    confirmationError.value = '加工要求和完成期限不能为空';
    confirmationResult.value = null;
    return;
  }

  confirmingInbound.value = true;
  confirmationResult.value = null;
  confirmationError.value = '';

  try {
    confirmationResult.value = await confirmInbound(activeOrder.value, {
      processingRequirement: processingRequirement.value,
      dueDate: dueDate.value,
      customerName: activeOrder.value.customer,
    });
    await loadInboundWorkspace();
  } catch (error) {
    confirmationError.value = error instanceof Error ? error.message : '确认入库失败';
  } finally {
    confirmingInbound.value = false;
  }
}

async function openStructuredMother() {
  structuredError.value = '';
  const code = structuredCode.value.trim();
  if (!code) {
    structuredError.value = '请填写母单号';
    return;
  }
  try {
    motherDetail.value = await fetchMotherOrder(code);
    processingRequirement.value = motherDetail.value.processingRequirement;
    dueDate.value = motherDetail.value.dueDate;
  } catch (error) {
    motherDetail.value = null;
    structuredError.value = error instanceof Error ? error.message : '母单不存在';
  }
}

function selectInboundOrder(code: string) {
  selectedOrderCode.value = code;
  detailOpen.value = true;
  confirmationResult.value = null;
  confirmationError.value = '';
  processingRequirement.value = '';
  dueDate.value = '';
  void loadOrderExtras(code);
}

function backToList() {
  detailOpen.value = false;
}
</script>

<template>
  <div class="inbound-desk page-stack" :class="{ 'is-detail': detailOpen }">
    <p v-if="workspace.dataSource === 'DEMO'" class="operation-feedback warning">当前显示评审样例，仅用于演示。</p>
    <section class="module-work-grid inbound-work-grid" aria-label="来料作业主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>今日母单池</h2>
          </div>
          <button class="primary-button" type="button" :disabled="!activeOrder || confirmingInbound" @click="confirmActiveInbound">
            {{ confirmingInbound ? '正在入账' : '确认入库' }}
          </button>
        </div>
        <div class="module-tools inbound-filters">
          <label>
            <span>扫码</span>
            <input v-model="keyword" placeholder="母单或快递单号" aria-label="快递或母单扫码" />
          </label>
          <label>
            <span>客户</span>
            <input v-model="customerKeyword" placeholder="客户名称" aria-label="客户筛选" />
          </label>
          <button class="secondary-button" type="button" :aria-expanded="advancedOpen ? 'true' : 'false'" @click="advancedOpen = !advancedOpen">
            {{ advancedOpen ? '收起筛选' : '更多筛选' }}
          </button>
          <label v-if="advancedOpen">
            <span>状态</span>
            <input v-model="statusKeyword" placeholder="如 待拆包" aria-label="状态筛选" />
          </label>
        </div>

        <p v-if="loading" class="empty-hint">正在加载来料</p>
        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="module-table-wrap refined-table-wrap inbound-table-wrap">
          <table class="module-table inbound-dispatch-table">
            <thead>
              <tr>
                <th>母单</th>
                <th>客户</th>
                <th>加工要求／期限</th>
                <th class="amount">数量重量</th>
                <th>状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="order in visibleOrders"
                :key="order.code"
                :class="{ 'is-selected': activeOrder?.code === order.code }"
                :aria-selected="activeOrder?.code === order.code"
                @click="selectInboundOrder(order.code)"
              >
                <td class="code"><strong>{{ order.code }}</strong></td>
                <td>{{ order.customer }}</td>
                <td>{{ requirementText(order) }}</td>
                <td class="amount">{{ order.qty }} 件 / {{ order.weight }}</td>
                <td><StatusBadge :label="order.status" :tone="statusTone(order.status)" /></td>
                <td><button class="table-action" type="button" @click.stop="selectInboundOrder(order.code)">处理</button></td>
              </tr>
              <tr v-if="!loading && visibleOrders.length === 0">
                <td colspan="6">暂无来料单据</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="inbound-cards">
          <p v-if="!loading && visibleOrders.length === 0" class="empty-hint">暂无来料单据</p>
          <button
            v-for="order in visibleOrders"
            :key="order.code"
            class="inbound-card"
            type="button"
            @click="selectInboundOrder(order.code)"
          >
            <span class="inbound-card-top">
              <strong class="code">{{ order.code }}</strong>
              <StatusBadge :label="order.status" :tone="statusTone(order.status)" />
            </span>
            <span>{{ order.customer }}</span>
            <span>{{ requirementText(order) }} · {{ order.qty }} 件 / {{ order.weight }}</span>
          </button>
        </div>
      </div>

      <aside v-if="activeOrder" class="module-side-panel">
        <section class="active-object-card tone-amber">
          <div class="panel-title-row">
            <button class="secondary-button detail-back" type="button" @click="backToList">返回列表</button>
            <StatusBadge :label="activeOrder.status" :tone="statusTone(activeOrder.status)" />
          </div>
          <strong>{{ activeOrder.code }}</strong>
          <dl class="detail-grid">
            <dt>客户</dt>
            <dd>{{ activeOrder.customer }}</dd>
            <dt>物料</dt>
            <dd>{{ activeOrder.material }}</dd>
            <dt>货品名称</dt>
            <dd>{{ activeOrder.goodsName }}</dd>
            <dt>快递名称</dt>
            <dd>{{ activeOrder.expressCompany }}</dd>
            <dt>快递单</dt>
            <dd>{{ activeOrder.courierNo }}</dd>
            <dt>寄货人</dt>
            <dd>{{ activeOrder.senderName }}</dd>
            <dt>收货人</dt>
            <dd>{{ activeOrder.receiverName }}</dd>
            <dt>拆箱签字</dt>
            <dd>{{ activeOrder.unpackSigner }}</dd>
            <dt>权属</dt>
            <dd>{{ activeOrder.owner }}</dd>
            <dt>数量重量</dt>
            <dd>{{ activeOrder.qty }} 件 / {{ activeOrder.weight }}</dd>
            <dt>备注</dt>
            <dd>{{ activeOrder.remark }}</dd>
            <dt>风险</dt>
            <dd>{{ activeRisk?.title ?? '无阻断' }}</dd>
            <template v-if="motherDetail">
              <dt>加工要求</dt>
              <dd>{{ motherDetail.processingRequirement }}</dd>
              <dt>期限</dt>
              <dd>{{ motherDetail.dueDate }}</dd>
            </template>
          </dl>
          <label class="field">
            <span>加工要求</span>
            <textarea v-model="processingRequirement" aria-label="加工要求" />
          </label>
          <label class="field">
            <span>完成期限</span>
            <input v-model="dueDate" type="date" aria-label="完成期限" />
          </label>
          <div class="detail-actions three-actions">
            <button class="primary-button" type="button" :disabled="confirmingInbound" @click="confirmActiveInbound">
              {{ confirmingInbound ? '正在入账' : '确认入库' }}
            </button>
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
          <label class="field">
            <span>打开母单号</span>
            <input v-model="structuredCode" aria-label="打开母单号" placeholder="如 MD-20261008-001" />
          </label>
          <button class="table-action" type="button" @click="openStructuredMother">打开母单</button>
          <p v-if="structuredError" class="operation-feedback danger">{{ structuredError }}</p>
          <MotherSplitPanel v-if="motherDetail" :mother-order-code="motherDetail.motherOrderCode" />
        </section>
      </aside>

      <section class="evidence-slot-panel">
        <div class="section-heading">
          <div>
            <h2>入库证据槽</h2>
          </div>
        </div>
        <div class="evidence-health compact-evidence-health">
          <article>
            <div>
              <strong>拆包视频</strong>
              <span v-if="captureError">{{ captureError }}</span>
              <span v-else-if="captures.length === 0">待补</span>
              <span v-else>已关联 {{ captures.length }} 个文件</span>
            </div>
          </article>
          <article v-for="item in captures" :key="item.code">
            <div>
              <strong>{{ item.fileName }}</strong>
              <a :href="item.previewUrl" target="_blank" rel="noopener">打开</a>
            </div>
          </article>
          <EvidenceCapture
            v-if="activeOrder"
            target-type="MOTHER_ORDER"
            :target-code="activeOrder.code"
            stage="INTAKE"
            scene="UNPACK"
            operation-type="inbound_confirm"
            :operation-code="evidenceOperationCode"
          />
          <article v-for="item in workspace.checkpoints" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.status }}</span>
            </div>
            <StatusBadge :label="item.value" :tone="item.tone" />
          </article>
        </div>
      </section>
    </section>

  </div>
</template>
