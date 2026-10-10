<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import OperationInputs from './OperationInputs.vue';
import { operationDraft } from './operationDraft';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
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
const operationValues = ref<Record<string, string>>({});
const reportingProduction = ref(false);
const productionReportResult = ref<ProductionReportResult | null>(null);
const productionReportError = ref('');
const selectedWorkOrderCode = ref('');
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

async function loadProductionWorkspace() {
  loadError.value = '';
  try {
    const loaded = await fetchProductionWorkspace();
    workspace.value = loaded;
    if (loaded.workOrders.length === 0) {
      loadError.value = '接口没有返回工单';
    }
  } catch (error) {
    workspace.value = emptyProduction();
    loadError.value = error instanceof Error ? error.message : '工单加载失败';
  } finally {
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

watch(activeWorkOrder, () => { operationValues.value = {}; });

function workOrderTone(status: string): Tone {
  if (status.includes('称重')) return 'red';
  if (status.includes('待')) return 'amber';
  if (status.includes('返工')) return 'violet';
  return 'green';
}

async function reportActiveProduction() {
  if (!activeWorkOrder.value) return;

  reportingProduction.value = true;
  productionReportResult.value = null;
  productionReportError.value = '';

  try {
    productionReportResult.value = await reportProduction(activeWorkOrder.value, activeHandoff.value?.code, operationDraft(operationValues.value));
    await loadProductionWorkspace();
  } catch (error) {
    productionReportError.value = error instanceof Error ? error.message : '提交报工失败';
  } finally {
    reportingProduction.value = false;
  }
}

function selectWorkOrder(code: string) {
  selectedWorkOrderCode.value = code;
  productionReportResult.value = null;
  productionReportError.value = '';
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
  <div class="production-desk page-stack">
    <p v-if="workspace.dataSource === 'DEMO'" class="operation-feedback warning">当前显示评审样例，仅用于演示。</p>
    <section class="module-work-grid production-work-grid" aria-label="生产派工主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>今日工单池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>扫码</span>
              <input v-model="keyword" placeholder="工单号" aria-label="工单扫码" />
            </label>
          </div>
        </div>

        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="module-table-wrap refined-table-wrap">
          <table class="module-table production-dispatch-table">
            <thead>
              <tr>
                <th>工单</th>
                <th>客户/物料</th>
                <th>工序/项目</th>
                <th>机器/品类</th>
                <th>负责人</th>
                <th>产出进度</th>
                <th>状态/审核</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="order in filteredWorkOrders"
                :key="order.code"
                :class="{ 'is-selected': activeWorkOrder?.code === order.code }"
                :aria-selected="activeWorkOrder?.code === order.code"
                @click="selectWorkOrder(order.code)"
              >
                <td><strong>{{ order.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.customer }}</b>
                    <small>{{ order.material }}</small>
                  </span>
                </td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.process }}</b>
                    <small>{{ order.workItemName }} · {{ order.processCategory }}</small>
                  </span>
                </td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.machineNo }}</b>
                    <small>{{ order.productCategory }}</small>
                  </span>
                </td>
                <td>{{ order.assignee }}</td>
                <td>
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
              <tr v-if="filteredWorkOrders.length === 0">
                <td colspan="8">暂无工单</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeWorkOrder" class="module-side-panel">
        <section class="active-object-card tone-red">
          <div class="panel-title-row">
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
            <dd>{{ activeWorkOrder.startedAt }} -> {{ activeWorkOrder.finishedAt }}</dd>
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
          <div class="detail-actions three-actions">
            <button class="primary-button" type="button" :disabled="reportingProduction" @click="reportActiveProduction">
              {{ reportingProduction ? '正在入账' : '提交报工' }}
            </button>
          </div>
          <div v-if="productionReportResult" class="operation-feedback success">
            <strong>生产报工已写入台账</strong>
            <span>{{ productionReportResult.ledgerCode }} / {{ productionReportResult.workOrderCode }}</span>
            <small>{{ productionReportResult.fromStockStatus }} -> {{ productionReportResult.toStockStatus }}</small>
          </div>
          <div v-else-if="productionReportError" class="operation-feedback danger">
            <strong>提交报工失败</strong>
            <span>{{ productionReportError }}</span>
            <small>请确认该工单已有在制库存，并完成必要称重交接。</small>
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
        </section>
      </aside>

    <section class="module-detail-grid" aria-label="称重交接、工序模板和报工规则">
      <div class="section-block module-rule-panel">
        <div class="section-heading">
          <div>
            <h2>称重交接记录</h2>
            <p>损耗、工资试算和质检归因都必须能追溯到交接证据。</p>
          </div>
        </div>
        <div class="handoff-table refined-handoff-table">
          <div class="handoff-row handoff-head">
            <span>交接单</span>
            <span>从</span>
            <span>到</span>
            <span>投入</span>
            <span>产出</span>
            <span>损耗</span>
            <span>状态</span>
          </div>
          <div v-for="handoff in workspace.handoffs" :key="handoff.code" class="handoff-row">
            <strong>{{ handoff.code }}</strong>
            <span>{{ handoff.from }}</span>
            <span>{{ handoff.to }}</span>
            <span>{{ handoff.inputWeight }}</span>
            <span>{{ handoff.outputWeight }}</span>
            <span>{{ handoff.lossRate }}</span>
            <StatusBadge :label="handoff.status" :tone="handoff.tone" />
          </div>
        </div>
      </div>

      <div class="section-block module-rule-panel machine-output-summary-panel">
        <div class="section-heading">
          <div>
            <h2>设备产量汇总</h2>
            <p>雕刻登记表入账后的设备产量记录，按最近批次进入生产统计口径。</p>
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
