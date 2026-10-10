<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
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
const motherDetail = ref<MobileIntakeResult | null>(null);
const structuredCode = ref('');
const structuredError = ref('');
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

onMounted(() => {
  void loadInboundWorkspace();
});

async function loadInboundWorkspace() {
  loadError.value = '';
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
  } catch {
    captures.value = [];
  }
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
  confirmationResult.value = null;
  confirmationError.value = '';
  processingRequirement.value = '';
  dueDate.value = '';
  void loadOrderExtras(code);
}
</script>

<template>
  <div class="inbound-desk page-stack">
    <section class="module-work-grid inbound-work-grid" aria-label="来料作业主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>今日母单池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>扫码</span>
              <input v-model="keyword" placeholder="母单或快递单号" aria-label="快递或母单扫码" />
            </label>
          </div>
        </div>

        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="module-table-wrap refined-table-wrap">
          <table class="module-table inbound-dispatch-table">
            <thead>
              <tr>
                <th>母单</th>
                <th>签收</th>
                <th>客户/货品</th>
                <th>快递</th>
                <th>寄收/签字</th>
                <th>数量重量</th>
                <th>入库状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="order in filteredOrders"
                :key="order.code"
                :class="{ 'is-selected': activeOrder?.code === order.code }"
                :aria-selected="activeOrder?.code === order.code"
                @click="selectInboundOrder(order.code)"
              >
                <td><strong>{{ order.code }}</strong></td>
                <td>{{ order.inboundDate }} / {{ order.expressCompany }}</td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.customer }}</b>
                    <small>{{ order.goodsName || order.material }}</small>
                  </span>
                </td>
                <td>{{ order.courierNo }}</td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.senderName }} -> {{ order.receiverName }}</b>
                    <small>拆箱：{{ order.unpackSigner || '待补签' }}</small>
                  </span>
                </td>
                <td>{{ order.qty }} 件 / {{ order.weight }}</td>
                <td><StatusBadge :label="order.status" :tone="statusTone(order.status)" /></td>
                <td><button class="table-action" type="button" @click.stop="selectInboundOrder(order.code)">处理</button></td>
              </tr>
              <tr v-if="filteredOrders.length === 0">
                <td colspan="8">暂无来料单据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeOrder" class="module-side-panel">
        <section class="active-object-card tone-amber">
          <div class="panel-title-row">
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
              <span v-if="captures.length === 0">待补</span>
              <span v-else>已关联 {{ captures.length }} 个文件</span>
            </div>
          </article>
          <article v-for="item in captures" :key="item.code">
            <div>
              <strong>{{ item.fileName }}</strong>
              <a :href="item.previewUrl" target="_blank" rel="noopener">打开</a>
            </div>
          </article>
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
