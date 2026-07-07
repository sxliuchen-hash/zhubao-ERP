<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
import {
  productionHandoffs as fallbackProductionHandoffs,
  productionStationLoads as fallbackProductionStationLoads,
  workOrders as fallbackWorkOrders,
} from '../../data/m1PrototypeData';
import {
  fetchProductionWorkspace,
  type ProductionWorkspaceModel,
} from '../../services/businessWorkspaceApi';
import {
  reportProduction,
  type ProductionReportResult,
} from '../../services/businessOperationApi';

const workspace = ref<ProductionWorkspaceModel>({
  stationLoads: fallbackProductionStationLoads,
  workOrders: fallbackWorkOrders,
  handoffs: fallbackProductionHandoffs,
});
const reportingProduction = ref(false);
const productionReportResult = ref<ProductionReportResult | null>(null);
const productionReportError = ref('');
const selectedWorkOrderCode = ref('');

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

const productionStats = computed(() => [
  {
    label: '在制工单',
    value: workspace.value.workOrders.length,
    unit: '张',
    detail: '按客户/物料/工序维度调度',
    tone: 'blue' as Tone,
  },
  {
    label: '称重阻断',
    value: workspace.value.handoffs.filter((handoff) => handoff.status.includes('阻断')).length,
    unit: '条',
    detail: '进入下工序前必须处理',
    tone: 'red' as Tone,
  },
  {
    label: '满载工位',
    value: workspace.value.stationLoads.filter((station) => station.tone === 'red').length,
    unit: '个',
    detail: '暂缓继续派单',
    tone: 'amber' as Tone,
  },
  {
    label: '返工在制',
    value: workspace.value.workOrders.filter((order) => order.status.includes('返工')).length,
    unit: '张',
    detail: '默认不进正式工资',
    tone: 'violet' as Tone,
  },
]);

const dispatchLanes = [
  { code: 'PLAN', name: '排产', status: '按工位负载派单', tone: 'blue' },
  { code: 'SCAN', name: '扫码进站', status: '工单/人员/工序绑定', tone: 'green' },
  { code: 'WIP', name: '工序报工', status: 'WIP 转半成品', tone: 'green' },
  { code: 'WEIGH', name: '称重交接', status: '阻断下工序', tone: 'red' },
  { code: 'QC', name: '送检', status: '进入质检队列', tone: 'amber' },
] as const;

const productionActionTrace = [
  { step: '01', title: '扫码进站', detail: '绑定工单、人员、工序和工位，进入可报工状态。' },
  { step: '02', title: '工序报工', detail: '提交完成数量、产出重量和计件工资试算快照。' },
  { step: '03', title: '称重交接', detail: '关键工序补齐投入/产出重量，异常时阻断下工序。' },
  { step: '04', title: '送检入账', detail: '报工结果写库存台账，待检批次进入质检队列。' },
];

onMounted(async () => {
  try {
    workspace.value = await fetchProductionWorkspace();
  } catch {
    // Keep deterministic local samples available when the backend is not running.
  } finally {
    selectedWorkOrderCode.value = activeWorkOrder.value?.code ?? '';
  }
});

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
    productionReportResult.value = await reportProduction(activeWorkOrder.value, activeHandoff.value?.code);
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
</script>

<template>
  <div class="production-desk page-stack">
    <section class="module-command-surface production-command-surface" aria-label="生产派工报工作业台">
      <div class="module-command-main">
        <div class="console-title-line">
          <span class="eyebrow">WORK ORDER DESK</span>
          <StatusBadge label="工序报工写台账" tone="green" />
        </div>
        <div class="module-command-title">
          <h2>生产派工报工作业台</h2>
          <div class="module-date-card">
            <strong>总装一线</strong>
            <span>白班 / 在制工单</span>
          </div>
        </div>

        <div class="module-stat-grid">
          <article v-for="item in productionStats" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>

        <div class="module-flow-strip" aria-label="生产工序链路">
          <article v-for="lane in dispatchLanes" :key="lane.code" :class="`tone-${lane.tone}`">
            <b>{{ lane.code }}</b>
            <div>
              <strong>{{ lane.name }}</strong>
              <span>{{ lane.status }}</span>
            </div>
          </article>
        </div>
      </div>

      <aside class="module-priority-panel">
        <div>
          <span class="eyebrow">BOTTLENECK</span>
          <strong>工位负载</strong>
        </div>
        <article v-for="station in workspace.stationLoads" :key="station.station" :class="`tone-${station.tone}`">
          <StatusBadge :label="`${station.queue} / ${station.capacity}`" :tone="station.tone" />
          <div>
            <strong>{{ station.station }}</strong>
            <span>{{ station.bottleneck }}</span>
          </div>
        </article>
      </aside>
    </section>

    <section class="module-work-grid production-work-grid" aria-label="生产派工主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <span class="eyebrow">WORK ORDER POOL</span>
            <h2>今日工单池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>工单扫码</span>
              <input value="ZD-20260706-006" aria-label="工单扫码" />
            </label>
            <button class="primary-button" type="button">扫码报工</button>
            <button class="secondary-button" type="button">补录交接</button>
          </div>
        </div>

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
                v-for="order in workspace.workOrders"
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
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeWorkOrder" class="module-side-panel">
        <section class="active-object-card tone-red">
          <div class="panel-title-row">
            <span class="eyebrow">ACTIVE WORK ORDER</span>
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
          <div class="detail-actions three-actions">
            <button class="primary-button" type="button" :disabled="reportingProduction" @click="reportActiveProduction">
              {{ reportingProduction ? '正在入账' : '提交报工' }}
            </button>
            <button class="secondary-button" type="button">称重交接</button>
            <button class="secondary-button" type="button">打印标签</button>
          </div>
          <div class="operation-action-trace" aria-label="当前工单动作链">
            <article v-for="item in productionActionTrace" :key="item.step">
              <span>{{ item.step }}</span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.detail }}</small>
            </article>
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
              <span class="eyebrow">WEIGH HANDOFF</span>
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
    </section>

    <section class="module-detail-grid" aria-label="称重交接、工序模板和报工规则">
      <div class="section-block module-rule-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">HANDOFF LEDGER</span>
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

      <div class="section-block module-rule-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">ROUTING</span>
            <h2>工序模板</h2>
            <p>当前样例：戒托加工标准流程。</p>
          </div>
        </div>
        <ol class="process-list compact-process-list">
          <li>
            <span>01</span>
            <div>
              <strong>雕刻</strong>
              <small>设备工序，记录机器编号、品类、操机签字</small>
            </div>
          </li>
          <li>
            <span>02</span>
            <div>
              <strong>粗抛</strong>
              <small>计件 0.80 元/件，记录工作项目和开始时间</small>
            </div>
          </li>
          <li>
            <span>03</span>
            <div>
              <strong>精抛</strong>
              <small>计件 1.10 元/件，需称重交接和交单审核</small>
            </div>
          </li>
          <li>
            <span>04</span>
            <div>
              <strong>镶石</strong>
              <small>计件 2.40 元/件，需质检</small>
            </div>
          </li>
          <li>
            <span>05</span>
            <div>
              <strong>终检入库</strong>
              <small>质检通过后转成品库存</small>
            </div>
          </li>
        </ol>
      </div>

      <div class="section-block module-rule-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">REPORT RULES</span>
            <h2>报工校验规则</h2>
            <p>前端只做提示和录入约束，最终校验以后端领域服务为准。</p>
          </div>
        </div>
        <div class="rule-stack">
          <article>
            <strong>扫码绑定</strong>
            <span>只能对本人或本班组授权工单报工。</span>
          </article>
          <article>
            <strong>称重阻断</strong>
            <span>关键工序缺少投入/产出重量时不能提交。</span>
          </article>
          <article>
            <strong>工资快照</strong>
            <span>工序单价按报工时快照写入，后续调价不影响历史。</span>
          </article>
          <article>
            <strong>返工规则</strong>
            <span>返工工单默认不计工资，特殊情况走审批。</span>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>
