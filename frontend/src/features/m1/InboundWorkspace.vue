<script setup lang="ts">
import { computed, inject, nextTick, onMounted, ref, type Ref } from 'vue';
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
import EvidencePreviewLink from '../evidence/EvidencePreviewLink.vue';
import IntakePanel from '../intake/IntakePanel.vue';
import MotherSplitPanel from './MotherSplitPanel.vue';
import { useTraceTarget } from '../scan/useTraceTarget';

const emptyInbound = (): InboundWorkspaceModel => ({
  checkpoints: [],
  orders: [],
  risks: [],
});

const workspace = ref<InboundWorkspaceModel>(emptyInbound());
const loadError = ref('');
const confirmingInbound = ref(false);
const inboundRequestId = ref('');
const inboundFingerprint = ref('');
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
const detailPanel = ref<HTMLElement | null>(null);
let scrollRequested = false;
let extrasRevision = 0;
const openLabel = inject<((code: string) => void) | null>('zhubao-open-label', null);
const visibleTabs = inject<Ref<string[] | null>>('zhubao-visible-tabs', ref(null));
const canPrintLabel = computed(() => Boolean(openLabel) && (!visibleTabs.value || visibleTabs.value.includes('label')));
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

useTraceTarget(['INTAKE'], (target) => {
  if (target.objectType !== 'MOTHER_ORDER') return;
  selectInboundOrder(target.code);
});

async function loadInboundWorkspace() {
  loadError.value = '';
  loading.value = true;
  try {
    const loaded = await fetchInboundWorkspace();
    workspace.value = loaded;
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

async function handleIntakeSaved(result: MobileIntakeResult) {
  selectedOrderCode.value = result.motherOrderCode;
  detailOpen.value = true;
  await loadInboundWorkspace();
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
  const revision = ++extrasRevision;
  captures.value = [];
  motherDetail.value = null;
  captureError.value = '';
  if (!code) {
    return;
  }
  try {
    const detail = await fetchMotherOrder(code);
    if (revision !== extrasRevision) return;
    motherDetail.value = detail;
    processingRequirement.value = motherDetail.value.processingRequirement;
    dueDate.value = motherDetail.value.dueDate;
  } catch {
    if (revision !== extrasRevision) return;
    motherDetail.value = null;
  }
  await refreshCaptures(code, revision);
  await nextTick();
  if (scrollRequested && detailPanel.value && activeOrder.value?.code === selectedOrderCode.value) {
    scrollRequested = false;
    if (globalThis.matchMedia?.('(max-width: 900px)').matches) {
      detailPanel.value.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
    }
  }
}

async function refreshCaptures(code: string, revision = extrasRevision) {
  if (!code) return;
  try {
    const loaded = await fetchCaptures(code);
    if (revision !== extrasRevision || activeOrder.value?.code !== code) return;
    captures.value = loaded;
    captureError.value = '';
  } catch (error) {
    if (revision !== extrasRevision || activeOrder.value?.code !== code) return;
    captureError.value = error instanceof Error ? error.message : '现场文件暂时无法打开';
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
  const fingerprint = `${activeOrder.value.code}|${processingRequirement.value.trim()}|${dueDate.value.trim()}|${activeOrder.value.courierNo}|${activeOrder.value.qty}|${activeOrder.value.weight}`;
  if (!inboundRequestId.value || inboundFingerprint.value !== fingerprint) {
    const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}`;
    inboundRequestId.value = `inbound-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
    inboundFingerprint.value = fingerprint;
  }

  try {
    confirmationResult.value = await confirmInbound(activeOrder.value, {
      processingRequirement: processingRequirement.value,
      dueDate: dueDate.value,
      customerName: activeOrder.value.customer,
      requestId: inboundRequestId.value,
    });
    inboundRequestId.value = '';
    inboundFingerprint.value = '';
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
  scrollRequested = true;
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
    <IntakePanel
      title="新建来料"
      intro="扫运单查看关联母单，也可用同一运单新建另一张母单。提交超时请用原请求号核对或重试。"
      @saved="handleIntakeSaved"
    />
    <section class="module-work-grid inbound-work-grid" aria-label="来料作业主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>今日母单池</h2>
          </div>
          <button class="secondary-button" type="button" :disabled="loading" @click="loadInboundWorkspace">
            {{ loading ? '正在刷新' : '刷新母单列表' }}
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
                <td colspan="6">暂无来料单据，请在上方扫码或输入快递单号，填写收货资料后生成第一张母单。</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="inbound-cards">
          <p v-if="!loading && visibleOrders.length === 0" class="empty-hint">暂无来料单据，请在上方扫码或输入快递单号，填写收货资料后生成第一张母单。</p>
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

      <aside v-if="activeOrder" ref="detailPanel" class="module-side-panel" aria-label="母单详情">
        <section class="active-object-card tone-amber">
          <div class="panel-title-row">
            <button class="secondary-button detail-back" type="button" @click="backToList">返回列表</button>
            <StatusBadge :label="activeOrder.status" :tone="statusTone(activeOrder.status)" />
          </div>
          <strong>{{ activeOrder.code }}</strong>
          <button v-if="canPrintLabel" class="secondary-button" type="button" @click="openLabel?.(activeOrder.code)">预览母单标签</button>
          <MotherSplitPanel v-if="motherDetail" :mother-order-code="motherDetail.motherOrderCode" />
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
          <p v-if="motherDetail?.ledgerCode" class="operation-feedback success">已收货入库。下一步选择不拆或拆成子单，预览并打印流转贴纸。</p>
          <label v-if="!motherDetail?.ledgerCode" class="field">
            <span>加工要求</span>
            <textarea v-model="processingRequirement" aria-label="加工要求" />
          </label>
          <label v-if="!motherDetail?.ledgerCode" class="field">
            <span>完成期限</span>
            <input v-model="dueDate" type="date" aria-label="完成期限" />
          </label>
          <div v-if="!motherDetail?.ledgerCode" class="detail-actions three-actions">
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
            <input v-model="structuredCode" aria-label="打开母单号" placeholder="如 MD-20261014-0001" />
          </label>
          <button class="table-action" type="button" @click="openStructuredMother">打开母单</button>
          <p v-if="structuredError" class="operation-feedback danger">{{ structuredError }}</p>
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
              <strong>现场采集</strong>
              <span v-if="captureError">{{ captureError }}</span>
              <span v-else-if="captures.length === 0">待补</span>
              <span v-else>已采集 {{ captures.length }} 个文件，关联状态见下方</span>
            </div>
          </article>
          <article v-for="item in captures" :key="item.code">
            <div>
              <strong>{{ item.fileName }}</strong>
              <EvidencePreviewLink v-if="item.previewUrl" :attachment-code="item.code" :media-type-hint="item.mediaType" />
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
            @uploaded="refreshCaptures"
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
