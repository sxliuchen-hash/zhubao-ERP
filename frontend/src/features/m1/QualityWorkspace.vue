<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import OperationInputs from './OperationInputs.vue';
import { createAmountForm } from './operationAmounts';
import { operationDraft } from './operationDraft';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import type { LossApproval, Tone } from '../../data/m1PrototypeData';
import { parseScanCode } from '../scan/scanCode';
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
import {
  lookupQualityCheck,
  openQualityRework,
  submitQualityCheck,
  type QualityCheckResult,
  type QualityCheckView,
  type QualityReworkResult,
} from '../../services/qualityCheckApi';

const emptyQuality = (): QualityWorkspaceModel => ({
  metrics: [],
  cases: [],
  lossApprovals: [],
});

const workspace = ref<QualityWorkspaceModel>(emptyQuality());
const loadError = ref('');
const { operationValues, resetAmounts, assertAmounts, amountHint, amountDefaultNote } = createAmountForm();
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
const checkScanText = ref('');
let recognitionVersion = 0;
const checkHint = ref('');
const checkError = ref('');
const checkBusy = ref(false);
const checkSubmitting = ref(false);
const checkView = ref<QualityCheckView | null>(null);
const checkResult = ref<QualityCheckResult | null>(null);
const clauseDrafts = ref<Record<number, { verdict: string; reason: string }>>({});
const reworkOpening = ref(false);
const requirementRework = ref<QualityReworkResult | null>(null);
const requirementReworkError = ref('');
const { keyword, filtered: filteredCases } = useDeskFilter(
  () => workspace.value.cases,
  (item) => `${item.code} ${item.workOrder} ${item.process} ${item.inspector} ${item.defect} ${item.status}`,
);

const activeCase = computed(
  () =>
    workspace.value.cases.find((item) => item.code === selectedQualityCaseCode.value) ??
    workspace.value.cases.find((item) => item.status.includes('待') || item.action.includes('返工')) ??
    workspace.value.cases[0],
);

const activeApproval = computed(
  () =>
    workspace.value.lossApprovals.find((item) => item.workOrder === activeCase.value?.workOrder),
);

const shownInspector = computed(() => checkResult.value?.inspector || checkView.value?.inspector || '');
const requirementSubmitted = computed(() => Boolean(checkView.value?.qualityCaseCode));
const canOpenRequirementRework = computed(() => (
  checkView.value?.outcome === '不合格' && !checkView.value.reworkOrderCode && !requirementRework.value
));
const requirementHint = computed(() => {
  const view = checkView.value;
  if (!view || view.qualityCaseCode) {
    return '';
  }
  if (!view.ready) {
    return view.blockReason;
  }
  const missingVerdict = view.lines.some((line) => !clauseDrafts.value[line.lineNo]?.verdict);
  if (missingVerdict) {
    return '每一条加工要求都要有结果才能提交';
  }
  const missingReason = view.lines.some((line) => {
    const draft = clauseDrafts.value[line.lineNo];
    return (draft?.verdict === '不合格' || draft?.verdict === '让步') && !draft.reason.trim();
  });
  if (missingReason) {
    return '不合格和让步必须写原因';
  }
  return '';
});
const canSubmitRequirement = computed(() => (
  Boolean(checkView.value?.ready) && !requirementSubmitted.value && requirementHint.value === '' && !checkSubmitting.value
));

const recordedDefect = computed(() => {
  const value = activeCase.value?.defect?.trim() ?? '';
  return value && value !== '未判定' ? value : '';
});
const effectiveDefect = computed(() => operationValues.value.defect?.trim() || recordedDefect.value);
const effectiveReason = computed(() => operationValues.value.reason?.trim() || recordedDefect.value);

watch(activeCase, (item) => { resetAmounts(item?.operationContext); });
watch(checkScanText, () => {
  const hadRecognition = checkView.value !== null || checkBusy.value || checkResult.value !== null || requirementRework.value !== null;
  recognitionVersion += 1;
  checkBusy.value = false;
  checkView.value = null;
  clauseDrafts.value = {};
  checkResult.value = null;
  requirementRework.value = null;
  checkError.value = '';
  requirementReworkError.value = '';
  checkHint.value = hadRecognition ? '贴纸已变化，请重新识别' : '';
}, { flush: 'sync' });

function qualityCaseTone(status: string, action = ''): Tone {
  if (status.includes('完成')) return 'green';
  if (status.includes('称重')) return 'red';
  if (action.includes('返工')) return 'violet';
  return 'amber';
}

onMounted(() => {
  void loadQualityWorkspace();
});

async function loadQualityWorkspace() {
  loadError.value = '';
  try {
    const loaded = await fetchQualityWorkspace();
    workspace.value = loaded;
    if (loaded.cases.length === 0) {
      loadError.value = '接口没有返回质检单';
    }
  } catch (error) {
    workspace.value = emptyQuality();
    loadError.value = error instanceof Error ? error.message : '质检单加载失败';
  } finally {
    if (!workspace.value.cases.some((item) => item.code === selectedQualityCaseCode.value)) {
      selectedQualityCaseCode.value = activeCase.value?.code ?? '';
    }
  }
}

async function freezeActiveQualityStock() {
  if (!activeCase.value) return;

  freezingQualityStock.value = true;
  qualityFreezeResult.value = null;
  qualityFreezeError.value = '';

  try {
    if (!effectiveDefect.value) throw new Error('请填写缺陷说明');
    assertAmounts();
    qualityFreezeResult.value = await freezeQualityStock(activeCase.value, operationDraft(operationValues.value));
    await loadQualityWorkspace();
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
    const draft = operationDraft(operationValues.value);
    if (!effectiveReason.value) throw new Error('请填写返工原因');
    if (!String(draft.reworkOrderCode ?? '').trim()) throw new Error('请填写返工单号');
    assertAmounts();
    reworkDispatchResult.value = await dispatchRework(activeCase.value, draft);
    await loadQualityWorkspace();
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
    assertAmounts();
    reworkReportResult.value = await reportRework(activeCase.value, operationDraft(operationValues.value));
    await loadQualityWorkspace();
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
    assertAmounts();
    qualityReleaseResult.value = await releaseQualityStock(activeCase.value, operationDraft(operationValues.value));
    await loadQualityWorkspace();
  } catch (error) {
    qualityReleaseError.value = error instanceof Error ? error.message : '复检放行失败';
  } finally {
    releasingQualityStock.value = false;
  }
}

async function approveActiveLoss() {
  if (!activeCase.value) return;

  approvingLoss.value = true;
  lossApprovalResult.value = null;
  lossApprovalError.value = '';

  try {
    const draft = operationDraft(operationValues.value);
    if (!operationValues.value.approvalCode?.trim()) throw new Error('请填写损耗申请单号');
    const application: LossApproval = {
      code: operationValues.value.approvalCode.trim(), workOrder: activeCase.value.workOrder,
      process: activeCase.value.process, operationContext: activeCase.value.operationContext,
      standard: '', actual: '', evidence: '', action: '待申请', tone: 'amber',
    };
    lossApprovalResult.value = await approveLoss(application, draft);
    await loadQualityWorkspace();
  } catch (error) {
    lossApprovalError.value = error instanceof Error ? error.message : '提交损耗申请失败';
  } finally {
    approvingLoss.value = false;
  }
}

function resetRequirementDrafts(view: QualityCheckView) {
  const next: Record<number, { verdict: string; reason: string }> = {};
  for (const line of view.lines) {
    next[line.lineNo] = { verdict: line.verdict, reason: line.reason };
  }
  clauseDrafts.value = next;
}

async function recognizeRequirement() {
  checkHint.value = '';
  checkError.value = '';
  checkResult.value = null;
  requirementRework.value = null;
  requirementReworkError.value = '';
  const raw = checkScanText.value.trim();
  if (!raw) {
    checkHint.value = '请先扫子单贴纸，或不拆母单的贴纸';
    return;
  }
  if (raw.startsWith('JG|')) {
    try {
      const parsed = parseScanCode(raw);
      if (parsed.objectType !== 'WORK_ORDER' && parsed.objectType !== 'MOTHER_ORDER') {
        checkHint.value = '请扫描子单贴纸，或不拆母单的贴纸';
        return;
      }
    } catch (error) {
      checkHint.value = error instanceof Error ? error.message : '未识别的二维码内容';
      return;
    }
  }
  const version = ++recognitionVersion;
  checkView.value = null;
  clauseDrafts.value = {};
  checkBusy.value = true;
  try {
    const loaded = await lookupQualityCheck(raw);
    if (version !== recognitionVersion || raw !== checkScanText.value.trim()) return;
    resetRequirementDrafts(loaded);
    checkView.value = loaded;
  } catch (error) {
    if (version !== recognitionVersion) return;
    checkError.value = error instanceof Error ? error.message : '贴纸识别失败';
  } finally {
    if (version === recognitionVersion) checkBusy.value = false;
  }
}

async function submitRequirement() {
  if (!canSubmitRequirement.value || !checkView.value) {
    return;
  }
  checkSubmitting.value = true;
  const view = checkView.value;
  const version = recognitionVersion;
  const lines = view.lines.map((line) => ({
    lineNo: line.lineNo,
    verdict: clauseDrafts.value[line.lineNo]?.verdict ?? '',
    reason: clauseDrafts.value[line.lineNo]?.reason.trim() ?? '',
  }));
  checkError.value = '';
  try {
    const result = await submitQualityCheck({ code: view.orderCode, lines });
    if (version !== recognitionVersion) return;
    checkResult.value = result;
    checkView.value = {
      ...view,
      inspector: result.inspector,
      qualityCaseCode: result.qualityCaseCode,
      outcome: result.outcome,
      frozen: result.frozen,
      reworkOrderCode: result.reworkOrderCode,
      lines: result.lines,
      ready: false,
      blockReason: '',
    };
    checkHint.value = '';
  } catch (error) {
    if (version !== recognitionVersion) return;
    checkError.value = error instanceof Error ? error.message : '提交质检失败';
  } finally {
    checkSubmitting.value = false;
  }
}

async function openRequirementRework() {
  if (!checkView.value || !canOpenRequirementRework.value) {
    return;
  }
  reworkOpening.value = true;
  const view = checkView.value;
  const version = recognitionVersion;
  requirementReworkError.value = '';
  try {
    const opened = await openQualityRework(view.orderCode);
    if (version !== recognitionVersion) return;
    requirementRework.value = opened;
    checkView.value = {
      ...view,
      reworkOrderCode: opened.reworkOrderCode,
    };
  } catch (error) {
    if (version !== recognitionVersion) return;
    requirementReworkError.value = error instanceof Error ? error.message : '开返工失败';
  } finally {
    reworkOpening.value = false;
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
    <p v-if="workspace.dataSource === 'DEMO'" class="operation-feedback warning">当前显示评审样例，仅用于演示。</p>
    <section class="quality-check-panel" aria-label="对照加工要求">
      <div class="section-heading">
        <div>
          <h2>对照加工要求</h2>
          <p>扫子单贴纸。不拆的母单扫母单贴纸。质检员是当前登录账号。</p>
        </div>
      </div>
      <label class="field">
        <span>贴纸二维码</span>
        <input
          v-model="checkScanText"
          type="text"
          placeholder="JG|work_order|子单号 或 JG|mother|母单号"
          aria-label="质检贴纸"
        />
      </label>
      <div class="button-row">
        <button class="primary-button" type="button" :disabled="checkBusy" aria-label="识别质检贴纸" @click="recognizeRequirement">
          {{ checkBusy ? '正在识别' : '识别贴纸' }}
        </button>
      </div>
      <p v-if="checkHint" class="operation-feedback warning">{{ checkHint }}</p>
      <p v-if="checkError" class="operation-feedback danger">{{ checkError }}</p>

      <template v-if="checkView">
        <dl class="detail-grid compact-detail-grid">
          <dt>单号</dt>
          <dd>{{ checkView.orderCode }}</dd>
          <dt>客户</dt>
          <dd>{{ checkView.customerName || '未填写' }}</dd>
          <dt>期限</dt>
          <dd>{{ checkView.dueDate || '未填写' }}</dd>
          <dt>质检员</dt>
          <dd>{{ shownInspector || '请先登录' }}</dd>
        </dl>

        <article v-for="line in checkView.lines" :key="line.lineNo" class="quality-clause">
          <strong>{{ line.lineNo }}. {{ line.clause }}</strong>
          <template v-if="requirementSubmitted">
            <p>{{ line.verdict }}<span v-if="line.reason"> · {{ line.reason }}</span></p>
          </template>
          <template v-else-if="clauseDrafts[line.lineNo]">
            <div class="quality-verdict-row">
              <button
                v-for="verdict in ['合格', '不合格', '让步']"
                :key="verdict"
                type="button"
                class="secondary-button quality-verdict"
                :class="{
                  'is-selected': clauseDrafts[line.lineNo].verdict === verdict,
                  'is-pass': verdict === '合格',
                  'is-fail': verdict === '不合格',
                  'is-concession': verdict === '让步',
                }"
                :aria-label="`${verdict} ${line.lineNo}`"
                :aria-pressed="clauseDrafts[line.lineNo].verdict === verdict"
                @click="clauseDrafts[line.lineNo].verdict = verdict"
              >
                {{ verdict }}
              </button>
            </div>
            <label class="field">
              <span>{{ clauseDrafts[line.lineNo].verdict === '合格' ? '原因（合格可以不写）' : '原因' }}</span>
              <textarea
                v-model="clauseDrafts[line.lineNo].reason"
                :aria-label="`原因 ${line.lineNo}`"
                :placeholder="clauseDrafts[line.lineNo].verdict === '不合格' || clauseDrafts[line.lineNo].verdict === '让步' ? '必须写原因' : '可以不写'"
              />
            </label>
          </template>
        </article>

        <p v-if="requirementHint" class="operation-feedback warning">{{ requirementHint }}</p>
        <div v-if="!requirementSubmitted" class="button-row">
          <button
            class="primary-button"
            type="button"
            aria-label="提交质检"
            :disabled="!canSubmitRequirement"
            @click="submitRequirement"
          >
            {{ checkSubmitting ? '正在提交' : '提交质检' }}
          </button>
        </div>
        <div v-if="checkResult" class="operation-feedback success">
          <strong>质检已提交 · {{ checkResult.outcome }}</strong>
          <span>{{ checkResult.inspector }}</span>
          <span>质检单 {{ checkResult.qualityCaseCode }}</span>
          <span>{{ checkResult.frozen ? '半成品已转入冻结' : '已留下核对记录，未转入冻结' }}</span>
        </div>
        <div v-if="canOpenRequirementRework" class="button-row">
          <button class="primary-button" type="button" aria-label="开返工" :disabled="reworkOpening" @click="openRequirementRework">
            {{ reworkOpening ? '正在开返工' : '开返工' }}
          </button>
        </div>
        <div v-if="checkView.reworkOrderCode" class="operation-feedback success">
          <strong>已开返工 {{ checkView.reworkOrderCode }}</strong>
          <span>关联质检 {{ checkView.qualityCaseCode }}</span>
          <span>原单 {{ checkView.motherOrderCode }} / {{ checkView.orderCode }}</span>
          <span v-if="requirementRework">库存已从冻结转入返工</span>
        </div>
        <p v-if="requirementReworkError" class="operation-feedback danger">{{ requirementReworkError }}</p>
      </template>
    </section>

    <section class="module-work-grid quality-work-grid" aria-label="质检返工主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>今日质检池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>扫码</span>
              <input v-model="keyword" placeholder="质检单或工单号" aria-label="质检扫码" />
            </label>
          </div>
        </div>

        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
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
                v-for="item in filteredCases"
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
              <tr v-if="filteredCases.length === 0">
                <td colspan="7">暂无质检单</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeCase" class="module-side-panel">
        <section class="active-object-card tone-violet">
          <div class="panel-title-row">
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
          <OperationInputs v-model="operationValues" :fields="[['defect', '缺陷说明'], ['reason', '返工原因'], ['approvalCode', '损耗申请单号'], ['reworkOrderCode', '返工单号'], ['quantity', '本次数量', 'number'], ['weight', '本次重量（克）', 'number'], ['releaseResult', '复检结论'], ['evidenceCode', '证据编号'], ['operator', '损耗报工人'], ['process', '损耗所属工序'], ['inputWeight', '投入重量（克）', 'number'], ['outputWeight', '产出重量（克）', 'number'], ['reasonableTransferWeight', '合理转移重量（克）', 'number'], ['standardLossRatePercent', '标准损耗率（%）', 'number'], ['acceptedQuantity', '验收数量', 'number'], ['pieceRate', '计件单价', 'number'], ['lossDeductionUnitPrice', '损耗扣减单价', 'number']]" />
          <p v-if="amountDefaultNote" class="operation-feedback">{{ amountDefaultNote }}</p>
          <p v-if="activeCase.defect === '未判定' && !operationValues.defect?.trim()" class="operation-feedback warning">未判定不能作为缺陷，请填写缺陷说明。</p>
          <p v-if="!effectiveReason" class="operation-feedback warning">生成返工前请填写返工原因。</p>
          <p v-if="amountHint" class="operation-feedback warning">{{ amountHint }}</p>
          <div class="detail-actions">
            <button class="primary-button" type="button" :disabled="freezingQualityStock || !effectiveDefect" @click="freezeActiveQualityStock">
              {{ freezingQualityStock ? '正在冻结' : '冻结异常库存' }}
            </button>
            <button class="secondary-button" type="button" :disabled="dispatchingRework || !effectiveReason" @click="dispatchActiveRework">
              {{ dispatchingRework ? '正在派发' : '生成返工工单' }}
            </button>
            <button class="secondary-button" type="button" :disabled="reportingRework" @click="reportActiveRework">
              {{ reportingRework ? '正在报工' : '提交返工报工' }}
            </button>
            <button class="secondary-button" type="button" :disabled="releasingQualityStock" @click="releaseActiveQualityStock">
              {{ releasingQualityStock ? '正在放行' : '复检放行入库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="approvingLoss" @click="approveActiveLoss">
              {{ approvingLoss ? '正在试算' : '提交损耗申请' }}
            </button>
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
            <strong>损耗申请已生成工资试算</strong>
            <span>{{ lossApprovalResult.approvalCode }} / {{ lossApprovalResult.approvalStatus }}</span>
            <small>试算 {{ lossApprovalResult.trialWage }}，不进入正式工资</small>
          </div>
          <div v-else-if="lossApprovalError" class="operation-feedback danger">
            <strong>提交损耗申请失败</strong>
            <span>{{ lossApprovalError }}</span>
          </div>
        </section>

      </aside>
    </section>
  </div>
</template>
