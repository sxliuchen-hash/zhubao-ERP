<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  lossApprovals as fallbackLossApprovals,
  qualityCases as fallbackQualityCases,
  qualityMetrics as fallbackQualityMetrics,
  type Tone,
} from '../../data/m1PrototypeData';
import {
  fetchQualityWorkspace,
  type QualityWorkspaceModel,
} from '../../services/businessWorkspaceApi';
import {
  approveLoss,
  dispatchRework,
  freezeQualityStock,
  releaseQualityStock,
  reportRework,
  type LossApprovalResult,
  type QualityReleaseResult,
  type ReworkDispatchResult,
  type ReworkReportResult,
  type QualityFreezeResult,
} from '../../services/businessOperationApi';

const workspace = ref<QualityWorkspaceModel>({
  metrics: fallbackQualityMetrics,
  cases: fallbackQualityCases,
  lossApprovals: fallbackLossApprovals,
});
const freezingQualityStock = ref(false);
const qualityFreezeResult = ref<QualityFreezeResult | null>(null);
const qualityFreezeError = ref('');
const dispatchingRework = ref(false);
const reworkDispatchResult = ref<ReworkDispatchResult | null>(null);
const reworkDispatchError = ref('');
const reportingRework = ref(false);
const reworkReportResult = ref<ReworkReportResult | null>(null);
const reworkReportError = ref('');
const releasingQualityStock = ref(false);
const qualityReleaseResult = ref<QualityReleaseResult | null>(null);
const qualityReleaseError = ref('');
const approvingLoss = ref(false);
const lossApprovalResult = ref<LossApprovalResult | null>(null);
const lossApprovalError = ref('');
const selectedQualityCaseCode = ref('');

const activeCase = computed(
  () =>
    workspace.value.cases.find((item) => item.code === selectedQualityCaseCode.value) ??
    workspace.value.cases.find((item) => item.status.includes('待') || item.action.includes('返工')) ??
    workspace.value.cases[0],
);

const activeApproval = computed(
  () =>
    workspace.value.lossApprovals.find((item) => item.workOrder === activeCase.value?.workOrder) ??
    workspace.value.lossApprovals.find((item) => item.action.includes('待')) ??
    workspace.value.lossApprovals[0],
);

const qualityStats = computed(() => [
  {
    label: '待处理质检',
    value: workspace.value.cases.filter((item) => !item.status.includes('完成')).length,
    unit: '批',
    detail: '返工/复检/称重待补',
    tone: 'amber' as Tone,
  },
  {
    label: '返工在链',
    value: workspace.value.cases.filter((item) => item.action.includes('返工') || item.process.includes('返工')).length,
    unit: '单',
    detail: '仍走同一工单状态机',
    tone: 'violet' as Tone,
  },
  {
    label: '损耗阻断',
    value: workspace.value.lossApprovals.filter((item) => item.tone === 'red').length,
    unit: '条',
    detail: '需审批和工资试算',
    tone: 'red' as Tone,
  },
  {
    label: '证据缺口',
    value: workspace.value.metrics.filter((item) => item.tone === 'red' || item.tone === 'amber').length,
    unit: '项',
    detail: '照片/称重/复检结论',
    tone: 'blue' as Tone,
  },
]);

const qualityFlow = [
  { code: 'INSP', name: '质检登记', status: '缺陷和数量留痕', tone: 'blue' },
  { code: 'FREEZE', name: '冻结库存', status: '异常半成品不可流转', tone: 'red' },
  { code: 'RWK', name: '返工派发', status: '生成返工工单', tone: 'violet' },
  { code: 'RPT', name: '返工报工', status: '返工 WIP 回半成品', tone: 'green' },
  { code: 'PASS', name: '复检放行', status: '合格后入成品/半成品', tone: 'green' },
] as const;

const qualityPriorityRows = computed(() => [
  ...workspace.value.lossApprovals.map((item) => ({
    code: item.code,
    title: item.action,
    detail: `${item.workOrder} / ${item.process} / ${item.evidence}`,
    owner: item.tone === 'red' ? '车间组长' : '操作工',
    tone: item.tone,
  })),
  ...workspace.value.cases
    .filter((item) => !item.status.includes('完成'))
    .map((item) => ({
      code: item.code,
      title: item.status,
      detail: `${item.workOrder} / ${item.process} / ${item.defect}`,
      owner: item.inspector,
      tone: item.status.includes('称重') ? ('red' as Tone) : ('amber' as Tone),
    })),
]);

function qualityCaseTone(status: string, action = ''): Tone {
  if (status.includes('完成')) return 'green';
  if (status.includes('称重')) return 'red';
  if (action.includes('返工')) return 'violet';
  return 'amber';
}

const qualityActionRows: Array<{
  step: string;
  action: string;
  stockFlow: string;
  api: string;
  evidence: string;
  state: string;
  tone: Tone;
}> = [
  {
    step: '01',
    action: '冻结异常库存',
    stockFlow: 'SEMI_FINISHED -> FROZEN',
    api: 'POST /quality-freezes',
    evidence: '缺陷照片、质检结论、称重记录',
    state: '已接真实台账',
    tone: 'green',
  },
  {
    step: '02',
    action: '生成返工工单',
    stockFlow: 'FROZEN -> REWORK',
    api: 'POST /rework-dispatches',
    evidence: '返工原因、派发人、冻结库存',
    state: '已接真实台账',
    tone: 'green',
  },
  {
    step: '03',
    action: '提交返工报工',
    stockFlow: 'REWORK -> SEMI_FINISHED',
    api: 'POST /rework-reports',
    evidence: '返工产出、操作人、返工照片',
    state: '已接真实台账',
    tone: 'green',
  },
  {
    step: '04',
    action: '复检放行',
    stockFlow: 'SEMI_FINISHED -> FINISHED',
    api: 'POST /quality-releases',
    evidence: '复检结果、放行人、抽检记录',
    state: '已接真实台账',
    tone: 'green',
  },
  {
    step: '05',
    action: '损耗归因',
    stockFlow: '超标损耗 -> 工资试算快照',
    api: 'POST /loss-approvals',
    evidence: '投入产出重量、责任工序、审批链、工资试算',
    state: '已接试算基线',
    tone: 'green',
  },
];

const qualityBoundaryRows = [
  { label: '真实能力', value: '冻结、返工派发、返工报工、复检放行会调用后端并写库存台账；损耗审批会生成工资试算快照', tone: 'green' },
  { label: '样例数据', value: '质检队列、损耗审批和证据列表仍来自评审快照或本地样例', tone: 'amber' },
  { label: '明显缺口', value: '损耗审批仍是轻量命令，尚未接入正式审批流、正式工资发放和锁账', tone: 'red' },
] as const;

const qualityActionTrace = [
  { step: '01', title: '冻结异常库存', detail: '不合格半成品先冻结，避免继续流转。' },
  { step: '02', title: '返工派发', detail: '返工仍是同一工单状态机下的特殊工单。' },
  { step: '03', title: '返工报工', detail: '返工产出回半成品库存，并关联缺陷证据。' },
  { step: '04', title: '复检放行', detail: '复检通过后转成品或半成品，失败继续返工。' },
  { step: '05', title: '损耗审批', detail: '超标损耗生成审批任务和工资试算快照。' },
];

const showLegacyReviewSections = false;

onMounted(async () => {
  try {
    workspace.value = await fetchQualityWorkspace();
  } catch {
    // Keep deterministic local samples available when the backend is not running.
  } finally {
    selectedQualityCaseCode.value = activeCase.value?.code ?? '';
  }
});

async function freezeActiveQualityStock() {
  if (!activeCase.value) return;

  freezingQualityStock.value = true;
  qualityFreezeResult.value = null;
  qualityFreezeError.value = '';

  try {
    qualityFreezeResult.value = await freezeQualityStock(activeCase.value);
  } catch (error) {
    qualityFreezeError.value = error instanceof Error ? error.message : '冻结异常库存失败';
  } finally {
    freezingQualityStock.value = false;
  }
}

async function dispatchActiveRework() {
  if (!activeCase.value) return;

  dispatchingRework.value = true;
  reworkDispatchResult.value = null;
  reworkDispatchError.value = '';

  try {
    reworkDispatchResult.value = await dispatchRework(activeCase.value);
  } catch (error) {
    reworkDispatchError.value = error instanceof Error ? error.message : '生成返工工单失败';
  } finally {
    dispatchingRework.value = false;
  }
}

async function reportActiveRework() {
  if (!activeCase.value) return;

  reportingRework.value = true;
  reworkReportResult.value = null;
  reworkReportError.value = '';

  try {
    reworkReportResult.value = await reportRework(activeCase.value);
  } catch (error) {
    reworkReportError.value = error instanceof Error ? error.message : '提交返工报工失败';
  } finally {
    reportingRework.value = false;
  }
}

async function releaseActiveQualityStock() {
  if (!activeCase.value) return;

  releasingQualityStock.value = true;
  qualityReleaseResult.value = null;
  qualityReleaseError.value = '';

  try {
    qualityReleaseResult.value = await releaseQualityStock(activeCase.value);
  } catch (error) {
    qualityReleaseError.value = error instanceof Error ? error.message : '复检放行失败';
  } finally {
    releasingQualityStock.value = false;
  }
}

async function approveActiveLoss() {
  if (!activeApproval.value) return;

  approvingLoss.value = true;
  lossApprovalResult.value = null;
  lossApprovalError.value = '';

  try {
    lossApprovalResult.value = await approveLoss(activeApproval.value);
  } catch (error) {
    lossApprovalError.value = error instanceof Error ? error.message : '提交损耗审批失败';
  } finally {
    approvingLoss.value = false;
  }
}

function selectQualityCase(code: string) {
  selectedQualityCaseCode.value = code;
  qualityFreezeResult.value = null;
  qualityFreezeError.value = '';
  reworkDispatchResult.value = null;
  reworkDispatchError.value = '';
  reworkReportResult.value = null;
  reworkReportError.value = '';
  qualityReleaseResult.value = null;
  qualityReleaseError.value = '';
  lossApprovalResult.value = null;
  lossApprovalError.value = '';
}
</script>

<template>
  <div class="quality-desk page-stack">
    <section class="module-command-surface quality-command-surface" aria-label="质检返工作业台">
      <div class="module-command-main">
        <div class="console-title-line">
          <span class="eyebrow">QUALITY DESK</span>
          <StatusBadge label="返工单状态机收敛" tone="violet" />
        </div>
        <div class="module-command-title">
          <h2>质检返工损耗作业台</h2>
          <div class="module-date-card">
            <strong>终检</strong>
            <span>质检 / 返工 / 放行</span>
          </div>
        </div>

        <div class="module-stat-grid">
          <article v-for="item in qualityStats" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>

        <div class="module-flow-strip" aria-label="质检返工处理链路">
          <article v-for="step in qualityFlow" :key="step.code" :class="`tone-${step.tone}`">
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
          <span class="eyebrow">QC BLOCKERS</span>
          <strong>质检阻断队列</strong>
        </div>
        <article v-for="row in qualityPriorityRows.slice(0, 4)" :key="row.code" :class="`tone-${row.tone}`">
          <StatusBadge :label="row.title" :tone="row.tone" />
          <div>
            <strong>{{ row.code }}</strong>
            <span>{{ row.detail }}</span>
            <small>{{ row.owner }}</small>
          </div>
        </article>
      </aside>
    </section>

    <section class="module-work-grid quality-work-grid" aria-label="质检返工主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <span class="eyebrow">QUALITY CASE POOL</span>
            <h2>今日质检池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>质检扫码</span>
              <input value="QC-20260706-021" aria-label="质检扫码" />
            </label>
            <button class="primary-button" type="button">登记质检</button>
            <button class="secondary-button" type="button">上传照片</button>
          </div>
        </div>

        <div class="module-table-wrap refined-table-wrap">
          <table class="module-table quality-dispatch-table">
            <thead>
              <tr>
                <th>质检单</th>
                <th>工单/工序</th>
                <th>质检员</th>
                <th>缺陷</th>
                <th>处理</th>
                <th>状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in workspace.cases"
                :key="item.code"
                :class="{ 'is-selected': activeCase?.code === item.code }"
                :aria-selected="activeCase?.code === item.code"
                @click="selectQualityCase(item.code)"
              >
                <td><strong>{{ item.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ item.workOrder }}</b>
                    <small>{{ item.process }}</small>
                  </span>
                </td>
                <td>{{ item.inspector }}</td>
                <td>{{ item.defect }}</td>
                <td>{{ item.action }}</td>
                <td><StatusBadge :label="item.status" :tone="qualityCaseTone(item.status, item.action)" /></td>
                <td><button class="table-action" type="button" @click.stop="selectQualityCase(item.code)">处理</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeCase" class="module-side-panel">
        <section class="active-object-card tone-violet">
          <div class="panel-title-row">
            <span class="eyebrow">ACTIVE QUALITY CASE</span>
            <StatusBadge :label="activeCase.status" :tone="qualityCaseTone(activeCase.status, activeCase.action)" />
          </div>
          <strong>{{ activeCase.code }}</strong>
          <dl class="detail-grid compact-detail-grid">
            <dt>工单</dt>
            <dd>{{ activeCase.workOrder }}</dd>
            <dt>工序</dt>
            <dd>{{ activeCase.process }}</dd>
            <dt>缺陷</dt>
            <dd>{{ activeCase.defect }}</dd>
            <dt>损耗审批</dt>
            <dd>{{ activeApproval?.code ?? '无' }}</dd>
          </dl>
          <div class="detail-actions">
            <button class="primary-button" type="button" :disabled="freezingQualityStock" @click="freezeActiveQualityStock">
              {{ freezingQualityStock ? '正在冻结' : '冻结异常库存' }}
            </button>
            <button class="secondary-button" type="button" :disabled="dispatchingRework" @click="dispatchActiveRework">
              {{ dispatchingRework ? '正在派发' : '生成返工工单' }}
            </button>
            <button class="secondary-button" type="button" :disabled="reportingRework" @click="reportActiveRework">
              {{ reportingRework ? '正在报工' : '提交返工报工' }}
            </button>
            <button class="secondary-button" type="button" :disabled="releasingQualityStock" @click="releaseActiveQualityStock">
              {{ releasingQualityStock ? '正在放行' : '复检放行入库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="approvingLoss" @click="approveActiveLoss">
              {{ approvingLoss ? '正在试算' : '提交损耗审批' }}
            </button>
          </div>
          <div class="operation-action-trace" aria-label="当前质检动作链">
            <article v-for="item in qualityActionTrace" :key="item.step">
              <span>{{ item.step }}</span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.detail }}</small>
            </article>
          </div>
          <div v-if="qualityFreezeResult" class="operation-feedback success">
            <strong>质检冻结已写入台账</strong>
            <span>{{ qualityFreezeResult.ledgerCode }} / {{ qualityFreezeResult.qualityCaseCode }}</span>
          </div>
          <div v-else-if="qualityFreezeError" class="operation-feedback danger">
            <strong>冻结异常库存失败</strong>
            <span>{{ qualityFreezeError }}</span>
          </div>
          <div v-if="reworkDispatchResult" class="operation-feedback success">
            <strong>返工派发已写入台账</strong>
            <span>{{ reworkDispatchResult.ledgerCode }} / {{ reworkDispatchResult.reworkOrderCode }}</span>
          </div>
          <div v-else-if="reworkDispatchError" class="operation-feedback danger">
            <strong>生成返工工单失败</strong>
            <span>{{ reworkDispatchError }}</span>
          </div>
          <div v-if="reworkReportResult" class="operation-feedback success">
            <strong>返工报工已写入台账</strong>
            <span>{{ reworkReportResult.ledgerCode }} / {{ reworkReportResult.reworkOrderCode }}</span>
          </div>
          <div v-else-if="reworkReportError" class="operation-feedback danger">
            <strong>提交返工报工失败</strong>
            <span>{{ reworkReportError }}</span>
          </div>
          <div v-if="qualityReleaseResult" class="operation-feedback success">
            <strong>复检放行已写入台账</strong>
            <span>{{ qualityReleaseResult.ledgerCode }} / {{ qualityReleaseResult.qualityCaseCode }}</span>
          </div>
          <div v-else-if="qualityReleaseError" class="operation-feedback danger">
            <strong>复检放行失败</strong>
            <span>{{ qualityReleaseError }}</span>
          </div>
          <div v-if="lossApprovalResult" class="operation-feedback success">
            <strong>损耗审批已生成工资试算</strong>
            <span>{{ lossApprovalResult.approvalCode }} / {{ lossApprovalResult.approvalStatus }}</span>
            <small>试算 {{ lossApprovalResult.trialWage }}，不进入正式工资</small>
          </div>
          <div v-else-if="lossApprovalError" class="operation-feedback danger">
            <strong>提交损耗审批失败</strong>
            <span>{{ lossApprovalError }}</span>
          </div>
        </section>

        <section class="evidence-slot-panel">
          <div class="panel-title-row">
            <span class="eyebrow">EVIDENCE</span>
            <StatusBadge label="照片/称重/结论" tone="blue" />
          </div>
          <div class="rule-stack">
            <article>
              <strong>质量检查项</strong>
              <span>缺陷类型、数量、照片和测量值必须和质检单绑定。</span>
            </article>
            <article>
              <strong>返工口径</strong>
              <span>返工是特殊工单，不创建第二套状态机。</span>
            </article>
            <article>
              <strong>损耗口径</strong>
              <span>按工序投入重量计算，超标进入轻量审批和工资试算快照。</span>
            </article>
          </div>
        </section>
      </aside>
    </section>

    <section class="module-detail-grid quality-detail-grid" aria-label="质检规则和接口契约">
      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">API CONTRACT</span>
          <StatusBadge label="5 个动作" tone="green" />
        </div>
        <div class="rule-stack">
          <article v-for="row in qualityActionRows" :key="row.step">
            <strong>{{ row.step }} · {{ row.action }}</strong>
            <span><code>{{ row.stockFlow }}</code> / <code>{{ row.api }}</code> / {{ row.evidence }}</span>
          </article>
        </div>
      </section>

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">METRICS</span>
          <StatusBadge label="质检指标" tone="blue" />
        </div>
        <div class="evidence-health compact-evidence-health">
          <article v-for="metric in workspace.metrics" :key="metric.label">
            <div>
              <strong>{{ metric.label }}</strong>
              <span>{{ metric.detail }}</span>
            </div>
            <StatusBadge :label="metric.value" :tone="metric.tone" />
          </article>
        </div>
      </section>

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">BOUNDARY</span>
          <StatusBadge label="仍有缺口" tone="amber" />
        </div>
        <div class="rule-stack">
          <article v-for="row in qualityBoundaryRows" :key="row.label" :class="`tone-${row.tone}`">
            <strong>{{ row.label }}</strong>
            <span>{{ row.value }}</span>
          </article>
        </div>
      </section>
    </section>

    <template v-if="showLegacyReviewSections">
    <section class="operations-hero">
      <div class="hero-copy">
        <span class="eyebrow">Quality Control</span>
        <h2>质检返工处理台</h2>
        <p>用缺陷、照片、称重、损耗和返工工单串成一条处理路径，避免返工状态机分叉。</p>
      </div>
      <div class="hero-kpis">
        <article v-for="metric in workspace.metrics.slice(0, 3)" :key="metric.label">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
        </article>
      </div>
    </section>

    <section class="control-filter-bar" aria-label="质检筛选">
      <div>
        <span>质检队列</span>
        <strong>待检 / 返工复检 / 损耗审批</strong>
      </div>
      <div>
        <span>证据要求</span>
        <strong>缺陷照片、称重记录、处理动作</strong>
      </div>
      <div>
        <span>返工策略</span>
        <strong>返工仍走同一套工单状态机</strong>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" type="button">只看阻断</button>
        <button class="secondary-button" type="button">批量复检</button>
      </div>
    </section>

    <section class="quality-runtime-board" aria-label="质检返工真实动作核验">
      <div class="quality-runtime-main">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Operation Contract</span>
            <h2>质检返工真实动作核验</h2>
            <p>把页面按钮、库存流转、后端 API 和证据要求对齐，避免只做展示页却没有业务闭环。</p>
          </div>
          <StatusBadge label="M3.14 已接 4 个写账动作" tone="blue" />
        </div>
        <div class="quality-action-table-wrap">
          <table class="quality-action-table">
            <thead>
              <tr>
                <th>步骤</th>
                <th>业务动作</th>
                <th>库存流转</th>
                <th>后端接口</th>
                <th>证据要求</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in qualityActionRows" :key="row.step">
                <td><strong>{{ row.step }}</strong></td>
                <td>{{ row.action }}</td>
                <td><code>{{ row.stockFlow }}</code></td>
                <td><code>{{ row.api }}</code></td>
                <td>{{ row.evidence }}</td>
                <td><StatusBadge :label="row.state" :tone="row.tone" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <aside class="quality-boundary-panel">
        <div class="section-heading compact">
          <div>
            <span class="eyebrow">Delivery Boundary</span>
            <h2>当前边界</h2>
          </div>
        </div>
        <div class="quality-boundary-list">
          <article v-for="row in qualityBoundaryRows" :key="row.label" :class="`tone-${row.tone}`">
            <StatusBadge :label="row.label" :tone="row.tone" />
            <span>{{ row.value }}</span>
          </article>
        </div>
      </aside>
    </section>

    <section class="module-ops-board" aria-label="质检作业主控台">
      <div class="module-master">
        <div class="module-toolbar">
          <div>
            <span class="eyebrow">QC Desk</span>
            <h2>质检作业主控台</h2>
            <p>质检、返工、缺陷证据和损耗审批在同一张作业表内处理，避免返工状态分叉。</p>
          </div>
          <div class="module-tools">
            <label>
              <span>质检扫码</span>
              <input value="QC-20260706-021" aria-label="质检扫码" />
            </label>
            <button class="primary-button" type="button">登记质检</button>
            <button class="secondary-button" type="button">上传照片</button>
          </div>
        </div>
        <div class="module-table-wrap">
          <table class="module-table">
            <thead>
              <tr>
                <th>质检单</th>
                <th>工单/工序</th>
                <th>质检员</th>
                <th>缺陷</th>
                <th>处理</th>
                <th>状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in workspace.cases" :key="item.code">
                <td><strong>{{ item.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ item.workOrder }}</b>
                    <small>{{ item.process }}</small>
                  </span>
                </td>
                <td>{{ item.inspector }}</td>
                <td>{{ item.defect }}</td>
                <td>{{ item.action }}</td>
                <td><StatusBadge :label="item.status" :tone="item.status.includes('完成') ? 'green' : item.status.includes('称重') ? 'red' : 'amber'" /></td>
                <td><button class="table-action" type="button">处理</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeCase" class="module-detail">
        <div class="detail-title">
          <span>当前质检</span>
          <strong>{{ activeCase.code }}</strong>
          <StatusBadge :label="activeCase.status" :tone="activeCase.status.includes('称重') ? 'red' : 'amber'" />
        </div>
        <dl class="detail-grid">
          <dt>工单</dt>
          <dd>{{ activeCase.workOrder }}</dd>
          <dt>工序</dt>
          <dd>{{ activeCase.process }}</dd>
          <dt>缺陷</dt>
          <dd>{{ activeCase.defect }}</dd>
          <dt>处理动作</dt>
          <dd>{{ activeCase.action }}</dd>
          <dt>损耗审批</dt>
          <dd>{{ activeApproval?.code ?? '无' }} / {{ activeApproval?.action ?? '正常' }}</dd>
          <dt>证据</dt>
          <dd>缺陷照片、称重记录、处理结论</dd>
        </dl>
        <div class="detail-actions">
          <button class="primary-button" type="button" :disabled="freezingQualityStock" @click="freezeActiveQualityStock">
            {{ freezingQualityStock ? '正在冻结' : '冻结异常库存' }}
          </button>
          <button class="secondary-button" type="button" :disabled="dispatchingRework" @click="dispatchActiveRework">
            {{ dispatchingRework ? '正在派发' : '生成返工工单' }}
          </button>
          <button class="secondary-button" type="button" :disabled="reportingRework" @click="reportActiveRework">
            {{ reportingRework ? '正在报工' : '提交返工报工' }}
          </button>
          <button class="secondary-button" type="button" :disabled="releasingQualityStock" @click="releaseActiveQualityStock">
            {{ releasingQualityStock ? '正在放行' : '复检放行入库' }}
          </button>
          <button class="secondary-button" type="button" :disabled="approvingLoss" @click="approveActiveLoss">
            {{ approvingLoss ? '正在试算' : '提交损耗审批' }}
          </button>
        </div>
        <div v-if="qualityFreezeResult" class="operation-feedback success">
          <strong>质检冻结已写入台账</strong>
          <span>{{ qualityFreezeResult.ledgerCode }} / {{ qualityFreezeResult.qualityCaseCode }}</span>
          <small>{{ qualityFreezeResult.fromStockStatus }} -> {{ qualityFreezeResult.toStockStatus }}</small>
        </div>
        <div v-else-if="qualityFreezeError" class="operation-feedback danger">
          <strong>冻结异常库存失败</strong>
          <span>{{ qualityFreezeError }}</span>
          <small>请确认该工单已有半成品库存，并已完成必要质检证据归档。</small>
        </div>
        <div v-if="reworkDispatchResult" class="operation-feedback success">
          <strong>返工派发已写入台账</strong>
          <span>{{ reworkDispatchResult.ledgerCode }} / {{ reworkDispatchResult.reworkOrderCode }}</span>
          <small>{{ reworkDispatchResult.fromStockStatus }} -> {{ reworkDispatchResult.toStockStatus }}</small>
        </div>
        <div v-else-if="reworkDispatchError" class="operation-feedback danger">
          <strong>生成返工工单失败</strong>
          <span>{{ reworkDispatchError }}</span>
          <small>请确认该工单已有冻结库存，并保留质检缺陷证据。</small>
        </div>
        <div v-if="reworkReportResult" class="operation-feedback success">
          <strong>返工报工已写入台账</strong>
          <span>{{ reworkReportResult.ledgerCode }} / {{ reworkReportResult.reworkOrderCode }}</span>
          <small>{{ reworkReportResult.fromStockStatus }} -> {{ reworkReportResult.toStockStatus }}</small>
        </div>
        <div v-else-if="reworkReportError" class="operation-feedback danger">
          <strong>提交返工报工失败</strong>
          <span>{{ reworkReportError }}</span>
          <small>请确认该返工单已有返工在制库存，并完成返工报工证据。</small>
        </div>
        <div v-if="qualityReleaseResult" class="operation-feedback success">
          <strong>复检放行已写入台账</strong>
          <span>{{ qualityReleaseResult.ledgerCode }} / {{ qualityReleaseResult.qualityCaseCode }}</span>
          <small>{{ qualityReleaseResult.fromStockStatus }} -> {{ qualityReleaseResult.toStockStatus }}</small>
        </div>
        <div v-else-if="qualityReleaseError" class="operation-feedback danger">
          <strong>复检放行失败</strong>
          <span>{{ qualityReleaseError }}</span>
          <small>请确认该工单已有半成品库存，并完成复检结论和放行证据。</small>
        </div>
        <div v-if="lossApprovalResult" class="operation-feedback success">
          <strong>损耗审批已生成工资试算</strong>
          <span>{{ lossApprovalResult.approvalCode }} / {{ lossApprovalResult.approvalStatus }}</span>
          <small>
            损耗 {{ lossApprovalResult.lossWeight }}g，超标 {{ lossApprovalResult.excessLossWeight }}g；
            计件 {{ lossApprovalResult.pieceWage }}，理论扣减 {{ lossApprovalResult.lossDeduction }}，
            试算 {{ lossApprovalResult.trialWage }}，不进入正式工资
          </small>
        </div>
        <div v-else-if="lossApprovalError" class="operation-feedback danger">
          <strong>提交损耗审批失败</strong>
          <span>{{ lossApprovalError }}</span>
          <small>请确认投入/产出称重证据、标准损耗率和工序单价快照已经存在。</small>
        </div>
      </aside>
    </section>

    <section class="toolbar-band">
      <div>
        <h2>质检返工与损耗处理</h2>
        <p>返工只保留一套工单状态机，不再创建独立返工状态机，避免后期逻辑冲突。</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" type="button">批量派检</button>
        <button class="primary-button" type="button">登记质检</button>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>待处理质检</h2>
            <p>每条质检记录必须能关联工单、工序、报工、缺陷照片和处理动作。</p>
          </div>
        </div>
        <div class="quality-grid dense">
          <article v-for="item in workspace.cases" :key="item.code" class="quality-item">
            <div class="quality-title">
              <strong>{{ item.code }}</strong>
              <StatusBadge :label="item.status" :tone="item.status.includes('完成') ? 'green' : item.status.includes('称重') ? 'red' : 'amber'" />
            </div>
            <dl>
              <dt>工单</dt>
              <dd>{{ item.workOrder }}</dd>
              <dt>工序</dt>
              <dd>{{ item.process }}</dd>
              <dt>质检员</dt>
              <dd>{{ item.inspector }}</dd>
              <dt>缺陷</dt>
              <dd>{{ item.defect }}</dd>
              <dt>处理</dt>
              <dd>{{ item.action }}</dd>
            </dl>
            <div class="button-row">
              <button class="secondary-button" type="button">查看证据</button>
              <button class="primary-button" type="button">处理</button>
            </div>
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>质检指标</h2>
            <p>把返工、损耗和证据缺口放在同一个看板里。</p>
          </div>
        </div>
        <div class="evidence-health">
          <article v-for="metric in workspace.metrics" :key="metric.label">
            <div>
              <strong>{{ metric.label }}</strong>
              <span>{{ metric.detail }}</span>
            </div>
            <StatusBadge :label="metric.value" :tone="metric.tone" />
          </article>
        </div>
      </div>
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>损耗审批</h2>
            <p>超标损耗必须能解释投入、产出、报废、返工和责任归因。</p>
          </div>
        </div>
        <div class="approval-list">
          <div v-for="approval in workspace.lossApprovals" :key="approval.code">
            <strong>{{ approval.code }}</strong>
            <span>{{ approval.workOrder }} / {{ approval.process }} / 标准 {{ approval.standard }} / 实际 {{ approval.actual }} / {{ approval.evidence }}</span>
            <StatusBadge :label="approval.action" :tone="approval.tone" />
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>返工路径</h2>
            <p>返工工单是特殊工单类型，仍走同一套报工、质检和库存台账。</p>
          </div>
        </div>
        <div class="rework-flow">
          <span>质检不合格</span>
          <span>选择返工工序</span>
          <span>生成返工工单</span>
          <span>返工报工</span>
          <span>复检放行</span>
        </div>
      </div>
    </section>
    </template>
  </div>
</template>
