<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import OperationInputs from './OperationInputs.vue';
import { createAmountForm } from './operationAmounts';
import { operationDraft } from './operationDraft';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import ProductionPanel from '../production/ProductionPanel.vue';
import type { Tone } from '../../data/m1PrototypeData';
import { ApiRequestError } from '../../services/apiClient';
import {
  fetchProductionWorkspace,
  type ProductionWorkspaceModel,
} from '../../services/businessWorkspaceApi';
import {
  reportProduction,
  type ProductionReportResult,
} from '../../services/businessOperationApi';
import {
  bindMachineOutput,
  fetchMachineOutputMachineMatchSuggestions,
  fetchMachineOutputSummary,
  type MachineOutputMachineMatchSuggestion,
  type MachineOutputSummary,
} from '../../services/machineOutputApi';

const emptyProduction = (): ProductionWorkspaceModel => ({
  stationLoads: [],
  workOrders: [],
  handoffs: [],
});

const workspace = ref<ProductionWorkspaceModel>(emptyProduction());
const loadError = ref('');
const accessDenied = ref(false);
const loading = ref(false);
const { operationValues, resetAmounts, assertAmounts, amountHint, amountDefaultNote } = createAmountForm();
const reportingProduction = ref(false);
const reportDenied = ref(false);
const productionReportResult = ref<ProductionReportResult | null>(null);
const productionReportError = ref('');
const selectedWorkOrderCode = ref('');
const customerKeyword = ref('');
const statusKeyword = ref('');
const advancedOpen = ref(false);
const detailOpen = ref(false);
const { keyword, filtered: filteredWorkOrders } = useDeskFilter(
  () => workspace.value.workOrders,
  (order) => `${order.code} ${order.customer} ${order.material} ${order.process} ${order.assignee} ${order.status}`,
);
const machineOutputSummary = ref<MachineOutputSummary>({
  totalRecords: 0,
  totalQuantity: 0,
  quantityUnit: '件',
  byMachine: [],
  byWorkDate: [],
  unmatchedMachineRecords: 0,
  signatureMissingRecords: 0,
});
const machineOutputSummaryLoading = ref(false);
const machineOutputSummaryError = ref('');
const machineMatchSuggestions = ref<MachineOutputMachineMatchSuggestion[]>([]);
const machineMatchSuggestionError = ref('');

const activeWorkOrder = computed(
  () =>
    workspace.value.workOrders.find((order) => order.code === selectedWorkOrderCode.value) ??
    workspace.value.workOrders.find((order) => order.status.includes('称重')) ??
    workspace.value.workOrders.find((order) => order.status.includes('待')) ??
    workspace.value.workOrders[0],
);
const activeHandoff = computed(
  () =>
    workspace.value.handoffs.find((handoff) => handoff.status.includes('阻断') || handoff.status.includes('待')) ??
    workspace.value.handoffs[0],
);
const visibleWorkOrders = computed(() => {
  const customer = customerKeyword.value.trim().toLowerCase();
  const status = statusKeyword.value.trim();
  return filteredWorkOrders.value.filter((order) => {
    const customerMatched = !customer || order.customer.toLowerCase().includes(customer);
    const statusMatched = !status || order.status.includes(status);
    return customerMatched && statusMatched;
  });
});

const machineOutputTopMachines = computed(() => machineOutputSummary.value.byMachine.slice(0, 4));
const machineOutputWorkDates = computed(() => machineOutputSummary.value.byWorkDate.slice(0, 3));
const machineMatchSuggestionTop = computed(() => machineMatchSuggestions.value.slice(0, 3));
const machineOutputTotalText = computed(
  () => `${machineOutputSummary.value.totalQuantity}${machineOutputSummary.value.quantityUnit}`,
);

onMounted(() => {
  void loadProductionWorkspace();
  void loadMachineOutputSummary();
  void loadMachineMatchSuggestions();
});

function denied(error: unknown): boolean {
  return error instanceof ApiRequestError && (error.status === 403 || error.code === 40301);
}

function describeFailure(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) return error.message;
  return fallback;
}

async function loadProductionWorkspace() {
  loadError.value = '';
  accessDenied.value = false;
  loading.value = true;
  try {
    const loaded = await fetchProductionWorkspace();
    workspace.value = loaded;
    if (loaded.workOrders.length === 0) {
      loadError.value = '接口没有返回工单';
    }
  } catch (error) {
    workspace.value = emptyProduction();
    accessDenied.value = denied(error);
    loadError.value = describeFailure(error, '工单加载失败');
  } finally {
    loading.value = false;
    if (!workspace.value.workOrders.some((order) => order.code === selectedWorkOrderCode.value)) {
      selectedWorkOrderCode.value = activeWorkOrder.value?.code ?? '';
    }
  }
}

async function loadMachineOutputSummary() {
  machineOutputSummaryLoading.value = true;
  machineOutputSummaryError.value = '';
  try {
    machineOutputSummary.value = await fetchMachineOutputSummary(100);
  } catch (error) {
    machineOutputSummaryError.value = error instanceof Error ? error.message : '设备产量汇总读取失败';
  } finally {
    machineOutputSummaryLoading.value = false;
  }
}

const machineBindMessage = ref('');

// 将某机器编号的未绑定记录绑定到候选机器档案 ID（仅 READY_TO_BIND 且候选唯一时可用）
async function bindMachineMatch(item: MachineOutputMachineMatchSuggestion) {
  machineBindMessage.value = '';
  const machineId = item.candidateMachineIds[0];
  if (machineId == null) {
    machineBindMessage.value = `${item.machineNoSnapshot} 无候选机器档案，请先建档`;
    return;
  }
  try {
    const result = await bindMachineOutput({ machineNoSnapshot: item.machineNoSnapshot, machineId });
    machineBindMessage.value = `已将 ${result.machineNoSnapshot} 的 ${result.boundRecords} 条记录绑定到机器档案 ${result.machineId}`;
    await loadMachineMatchSuggestions();
  } catch (error) {
    machineBindMessage.value = error instanceof Error ? error.message : '机器档案绑定失败';
  }
}

async function loadMachineMatchSuggestions() {
  machineMatchSuggestionError.value = '';
  try {
    machineMatchSuggestions.value = await fetchMachineOutputMachineMatchSuggestions(100);
  } catch (error) {
    machineMatchSuggestions.value = [];
    machineMatchSuggestionError.value = error instanceof Error ? error.message : '机器档案匹配建议读取失败';
  }
}

watch(activeWorkOrder, (order) => { resetAmounts(order?.operationContext); });

function workOrderTone(status: string): Tone {
  if (status.includes('称重')) return 'red';
  if (status.includes('待')) return 'amber';
  if (status.includes('返工')) return 'violet';
  return 'green';
}

// 桌面工单池旧报工不带请求编号，不是回放路径。库存和交接单仍各自提交。现场工序交付走上方共用面板。
async function reportActiveProduction() {
  if (!activeWorkOrder.value) return;

  reportingProduction.value = true;
  productionReportResult.value = null;
  productionReportError.value = '';
  reportDenied.value = false;

  try {
    assertAmounts();
    productionReportResult.value = await reportProduction(activeWorkOrder.value, activeHandoff.value?.code, operationDraft(operationValues.value));
    await loadProductionWorkspace();
  } catch (error) {
    reportDenied.value = denied(error);
    productionReportError.value = describeFailure(error, '提交报工失败');
  } finally {
    reportingProduction.value = false;
  }
}

function selectWorkOrder(code: string) {
  selectedWorkOrderCode.value = code;
  detailOpen.value = true;
  productionReportResult.value = null;
  productionReportError.value = '';
  reportDenied.value = false;
}

function backToList() {
  detailOpen.value = false;
}

function outputQuantityText(quantity: number | string) {
  return `${quantity}${machineOutputSummary.value.quantityUnit}`;
}

function machineMatchReadinessLabel(readiness: MachineOutputMachineMatchSuggestion['readiness']) {
  if (readiness === 'READY_TO_BIND') return '可绑定';
  if (readiness === 'CONFLICT') return '候选冲突';
  return '待建档';
}

function machineMatchTone(readiness: MachineOutputMachineMatchSuggestion['readiness']): Tone {
  if (readiness === 'READY_TO_BIND') return 'green';
  if (readiness === 'CONFLICT') return 'red';
  return 'amber';
}
</script>

<template>
  <div class="production-desk page-stack" :class="{ 'is-detail': detailOpen }">
    <p v-if="workspace.dataSource === 'DEMO'" class="operation-feedback warning">当前显示评审样例，仅用于演示。</p>
    <ProductionPanel
      title="工序交付"
      intro="扫子单或不拆母单贴纸。过程说明和正式交付分开。超时用原确认回放，不会另开一次交付。"
    />
    <section class="module-work-grid production-work-grid" aria-label="生产派工主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>今日工单池</h2>
          </div>
        </div>
        <div class="module-tools production-filters">
          <label>
            <span>扫码</span>
            <input v-model="keyword" placeholder="工单号" aria-label="工单扫码" />
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
            <input v-model="statusKeyword" placeholder="如 待称重" aria-label="状态筛选" />
          </label>
        </div>

        <p v-if="loading" class="empty-hint">正在加载工单</p>
        <p v-if="accessDenied" class="operation-feedback danger">无权限</p>
        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="module-table-wrap refined-table-wrap production-table-wrap">
          <table class="module-table production-dispatch-table">
            <thead>
              <tr>
                <th>工单</th>
                <th>客户／物料</th>
                <th>工序</th>
                <th class="amount">进度</th>
                <th>状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="order in visibleWorkOrders"
                :key="order.code"
                :class="{ 'is-selected': activeWorkOrder?.code === order.code }"
                :aria-selected="activeWorkOrder?.code === order.code"
                @click="selectWorkOrder(order.code)"
              >
                <td class="code"><strong>{{ order.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.customer }}</b>
                    <small>{{ order.material }}</small>
                  </span>
                </td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.process }}</b>
                    <small>{{ order.assignee || '未指派' }} · {{ order.machineNo || '未记机器' }}</small>
                  </span>
                </td>
                <td class="amount">
                  <i class="progress-bar"><b :style="{ width: `${order.progress}%` }" /></i>
                  {{ order.finishedQty }}/{{ order.plannedQty }}
                </td>
                <td>
                  <span class="cell-stack">
                    <StatusBadge :label="order.status" :tone="workOrderTone(order.status)" />
                    <small>{{ order.handoffAuditStatus }}</small>
                  </span>
                </td>
                <td><button class="table-action" type="button" @click.stop="selectWorkOrder(order.code)">进入工序</button></td>
              </tr>
              <tr v-if="!loading && visibleWorkOrders.length === 0">
                <td colspan="6">暂无工单</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="production-cards">
          <p v-if="!loading && visibleWorkOrders.length === 0" class="empty-hint">暂无工单</p>
          <button
            v-for="order in visibleWorkOrders"
            :key="order.code"
            class="production-card"
            type="button"
            :aria-pressed="activeWorkOrder?.code === order.code"
            @click="selectWorkOrder(order.code)"
          >
            <span class="production-card-top">
              <strong class="code">{{ order.code }}</strong>
              <StatusBadge :label="order.status" :tone="workOrderTone(order.status)" />
            </span>
            <span>{{ order.customer }} · {{ order.material }}</span>
            <span>{{ order.process }} · {{ order.assignee || '未指派' }} · {{ order.finishedQty }}/{{ order.plannedQty }}</span>
          </button>
        </div>
      </div>

      <aside v-if="activeWorkOrder" class="module-side-panel">
        <section class="active-object-card tone-red">
          <div class="panel-title-row">
            <button class="secondary-button detail-back" type="button" @click="backToList">返回列表</button>
            <StatusBadge :label="activeWorkOrder.status" :tone="workOrderTone(activeWorkOrder.status)" />
          </div>
          <strong>{{ activeWorkOrder.code }}</strong>
          <dl class="detail-grid">
            <dt>客户</dt>
            <dd>{{ activeWorkOrder.customer }}</dd>
            <dt>物料</dt>
            <dd>{{ activeWorkOrder.material }}</dd>
            <dt>当前工序</dt>
            <dd>{{ activeWorkOrder.process }}</dd>
            <dt>工作项目</dt>
            <dd>{{ activeWorkOrder.workItemName }}</dd>
            <dt>机器/品类</dt>
            <dd>{{ activeWorkOrder.machineNo }} / {{ activeWorkOrder.productCategory }}</dd>
            <dt>时间段</dt>
            <dd>{{ activeWorkOrder.startedAt }} 至 {{ activeWorkOrder.finishedAt }}</dd>
            <dt>完成情况</dt>
            <dd>{{ activeWorkOrder.completionStatus }}</dd>
            <dt>负责人</dt>
            <dd>{{ activeWorkOrder.assignee }}</dd>
            <dt>交单审核</dt>
            <dd>{{ activeWorkOrder.handoffAuditStatus }}</dd>
            <dt>交接阻断</dt>
            <dd>{{ activeHandoff?.code ?? '无' }} / {{ activeHandoff?.status ?? '正常' }}</dd>
            <dt>工资口径</dt>
            <dd>计件试算，单价按报工快照</dd>
          </dl>
          <div class="mini-progress" aria-label="工单进度">
            <span><b :style="{ width: `${activeWorkOrder.progress}%` }" /></span>
            <em>{{ activeWorkOrder.progress }}%</em>
          </div>
          <OperationInputs v-model="operationValues" :fields="[['quantity', '本次完成数量', 'number'], ['weight', '本次产出重量（克）', 'number'], ['workDate', '报工日期', 'date'], ['evidenceCode', '证据编号']]" />
          <p v-if="amountDefaultNote" class="operation-feedback">{{ amountDefaultNote }}</p>
          <p v-if="amountHint" class="operation-feedback warning">{{ amountHint }}</p>
          <p class="production-legacy-note">工单池旧报工不带请求编号，不是回放路径。现场交付请用上方工序交付。</p>
          <div class="detail-actions three-actions">
            <button class="primary-button" type="button" :disabled="reportingProduction" :aria-busy="reportingProduction ? 'true' : 'false'" @click="reportActiveProduction">
              {{ reportingProduction ? '正在入账' : '提交报工' }}
            </button>
          </div>
          <div v-if="productionReportResult" class="operation-feedback success">
            <strong>生产报工已写入台账</strong>
            <span>{{ productionReportResult.ledgerCode }} / {{ productionReportResult.workOrderCode }}</span>
            <small>{{ productionReportResult.fromStockStatus }} -> {{ productionReportResult.toStockStatus }}</small>
          </div>
          <div v-else-if="productionReportError" class="operation-feedback danger">
            <strong>{{ reportDenied ? '无权限' : '提交报工失败' }}</strong>
            <span>{{ productionReportError }}</span>
            <small v-if="!reportDenied">请确认该工单已有在制库存，并完成必要称重交接。</small>
          </div>
        </section>

        <section class="handoff-focus-panel">
          <div class="section-heading">
            <div>
              <h2>称重交接阻断</h2>
            </div>
          </div>
          <dl class="detail-grid" v-if="activeHandoff">
            <dt>交接单</dt>
            <dd>{{ activeHandoff.code }}</dd>
            <dt>从</dt>
            <dd>{{ activeHandoff.from }}</dd>
            <dt>到</dt>
            <dd>{{ activeHandoff.to }}</dd>
            <dt>投入</dt>
            <dd>{{ activeHandoff.inputWeight }}</dd>
            <dt>产出</dt>
            <dd>{{ activeHandoff.outputWeight }}</dd>
            <dt>损耗</dt>
            <dd>{{ activeHandoff.lossRate }}</dd>
          </dl>
          <p v-else class="empty-hint">暂无称重交接</p>
        </section>
      </aside>

      <section class="module-detail-grid" aria-label="称重交接和设备产量">
        <div class="section-block module-rule-panel">
          <div class="section-heading">
            <div>
              <h2>称重交接记录</h2>
              <details class="production-note">
                <summary>交接说明</summary>
                <p>损耗、工资试算和质检归因都必须能追溯到交接证据。</p>
              </details>
            </div>
          </div>
          <div class="module-table-wrap production-handoff-table">
            <table class="module-table production-handoff-grid">
              <thead>
                <tr>
                  <th>交接单</th>
                  <th>从</th>
                  <th>到</th>
                  <th class="amount">投入</th>
                  <th class="amount">产出</th>
                  <th class="amount">损耗</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="handoff in workspace.handoffs" :key="handoff.code">
                  <td class="code"><strong>{{ handoff.code }}</strong></td>
                  <td>{{ handoff.from }}</td>
                  <td>{{ handoff.to }}</td>
                  <td class="amount">{{ handoff.inputWeight }}</td>
                  <td class="amount">{{ handoff.outputWeight }}</td>
                  <td class="amount">{{ handoff.lossRate }}</td>
                  <td><StatusBadge :label="handoff.status" :tone="handoff.tone" /></td>
                </tr>
                <tr v-if="workspace.handoffs.length === 0">
                  <td colspan="7">暂无称重交接</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="production-handoff-cards">
            <p v-if="workspace.handoffs.length === 0" class="empty-hint">暂无称重交接</p>
            <article v-for="handoff in workspace.handoffs" :key="handoff.code" class="production-handoff-card">
              <span class="production-handoff-top">
                <strong class="code">{{ handoff.code }}</strong>
                <StatusBadge :label="handoff.status" :tone="handoff.tone" />
              </span>
              <span>{{ handoff.from }} 至 {{ handoff.to }}</span>
              <span>投入 {{ handoff.inputWeight }} · 产出 {{ handoff.outputWeight }} · 损耗 {{ handoff.lossRate }}</span>
            </article>
          </div>
        </div>

        <div class="section-block module-rule-panel machine-output-summary-panel">
          <div class="section-heading">
            <div>
              <h2>设备产量汇总</h2>
              <details class="production-note">
                <summary>统计说明</summary>
                <p>雕刻登记表入账后的设备产量记录，按最近批次进入生产统计口径。</p>
              </details>
            </div>
            <StatusBadge :label="machineOutputSummaryLoading ? '读取中' : `${machineOutputSummary.totalRecords} 条`" tone="green" />
          </div>

          <div class="machine-output-summary-grid">
            <article>
              <span>最近产量</span>
              <strong>{{ machineOutputSummaryLoading ? '读取中' : machineOutputTotalText }}</strong>
            </article>
            <article>
              <span>机器档案待匹配</span>
              <strong>{{ machineOutputSummary.unmatchedMachineRecords }}</strong>
            </article>
            <article>
              <span>签名附件待补</span>
              <strong>{{ machineOutputSummary.signatureMissingRecords }}</strong>
            </article>
          </div>

          <div v-if="machineOutputSummaryError" class="operation-feedback danger">
            <strong>设备产量汇总读取失败</strong>
            <span>{{ machineOutputSummaryError }}</span>
          </div>

          <div v-else class="machine-output-split">
            <div>
              <strong>按机器</strong>
              <div class="rule-stack machine-output-list" v-if="machineOutputTopMachines.length">
                <article v-for="item in machineOutputTopMachines" :key="item.machineNoSnapshot">
                  <span>{{ item.machineNoSnapshot }}</span>
                  <b>{{ outputQuantityText(item.quantity) }}</b>
                  <small>{{ item.records }} 条记录</small>
                </article>
              </div>
              <small v-else>暂无设备产量记录</small>
            </div>
            <div>
              <strong>按日期</strong>
              <div class="rule-stack machine-output-list" v-if="machineOutputWorkDates.length">
                <article v-for="item in machineOutputWorkDates" :key="item.workDate">
                  <span>{{ item.workDate }}</span>
                  <b>{{ outputQuantityText(item.quantity) }}</b>
                  <small>{{ item.records }} 条记录</small>
                </article>
              </div>
              <small v-else>暂无日期汇总</small>
            </div>
          </div>

          <div class="machine-match-suggestion-panel">
            <div class="machine-match-suggestion-title">
              <strong>机器档案匹配建议</strong>
              <span>按现场机器编号归集未绑定记录，先给出只读建议，不自动改账。</span>
            </div>
            <div v-if="machineMatchSuggestionError" class="operation-feedback danger">
              <strong>机器档案匹配建议读取失败</strong>
              <span>{{ machineMatchSuggestionError }}</span>
            </div>
            <div v-else-if="machineMatchSuggestionTop.length" class="rule-stack machine-match-suggestion-list">
              <article v-for="item in machineMatchSuggestionTop" :key="item.machineNoSnapshot">
                <div>
                  <strong>{{ item.machineNoSnapshot }}</strong>
                  <span>{{ item.productCategory || '未记录品类' }} / {{ item.latestWorkDate || '未记录日期' }}</span>
                </div>
                <StatusBadge :label="machineMatchReadinessLabel(item.readiness)" :tone="machineMatchTone(item.readiness)" />
                <small>
                  未匹配 {{ item.unmatchedRecords }} 条 / 已绑定 {{ item.matchedRecords }} 条 /
                  候选 {{ item.candidateMachineIds.length ? item.candidateMachineIds.join(', ') : '待建档' }}
                </small>
                <small>{{ item.suggestedAction }}</small>
                <button
                  v-if="item.readiness === 'READY_TO_BIND'"
                  class="table-action"
                  type="button"
                  @click="bindMachineMatch(item)"
                >
                  绑定到机器档案 {{ item.candidateMachineIds[0] }}
                </button>
              </article>
            </div>
            <small v-else>暂无待匹配机器档案。</small>
            <p v-if="machineBindMessage" class="operation-feedback">{{ machineBindMessage }}</p>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>
