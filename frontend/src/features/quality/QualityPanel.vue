<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { ApiRequestError } from '../../services/apiClient';
import type { OrderActionTarget } from '../orders/orderActions';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import FieldSteps from '../field/FieldSteps.vue';
import { keptInputFailure } from '../field/keptInput';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ProgressNoteForm from '../scan/ProgressNoteForm.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { appendProgressRecord, progressTargetFromScan } from '../scan/progressTarget';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import { useTraceTarget } from '../scan/useTraceTarget';
import type { ScanContextResponse, ScanProgressRecord, ScanRelatedTarget } from '../../services/scanContextApi';
import {
  lookupQualityCheck,
  openQualityRework,
  releaseQualityRound,
  reportQualityRework,
  submitQualityCheck,
  submitQualityRecheck,
  type QualityCheckLine,
  type QualityCheckResult,
  type QualityCheckView,
  type QualityReworkResult,
} from '../../services/qualityCheckApi';

const props = withDefaults(defineProps<{
  title: string;
  intro: string;
  field?: boolean;
  initialTarget?: OrderActionTarget | null;
  embedded?: boolean;
}>(), {
  field: false,
  initialTarget: null,
  embedded: false,
});
const emit = defineEmits<{ changed: []; targetSelected: [target: OrderActionTarget] }>();
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
type QualityOperation = 'check' | 'rework' | 'report' | 'recheck' | 'release';
const operationPermissions: Record<QualityOperation, string> = { check: 'BUSINESS_QUALITY_FREEZE', rework: 'BUSINESS_REWORK_DISPATCH', report: 'BUSINESS_REWORK_REPORT', recheck: 'BUSINESS_QUALITY_FREEZE', release: 'BUSINESS_QUALITY_RELEASE' };
function allowed(kind: QualityOperation) { return !session.value?.effectivePermissions || session.value.effectivePermissions.includes(operationPermissions[kind]); }
type QualityCommand = { kind: QualityOperation; code: string; requestId: string; send: () => Promise<void>; success: string };
const pendingCommand = ref<QualityCommand | null>(null);
const activeOperation = ref<QualityOperation | ''>('');
const operationBusy = computed(() => Boolean(activeOperation.value));
const readError = ref('');

const scanText = ref('');
const hint = ref('');
const formError = ref('');
const qualityCode = ref('');
const checkBusy = ref(false);
const checkSubmitting = computed(() => activeOperation.value === 'check');
const reworkOpening = computed(() => activeOperation.value === 'rework');
const reportingRework = computed(() => activeOperation.value === 'report');
const recheckSubmitting = computed(() => activeOperation.value === 'recheck');
const releasing = computed(() => activeOperation.value === 'release');
const checkView = ref<QualityCheckView | null>(null);
const checkResult = ref<QualityCheckResult | null>(null);
const requirementRework = ref<QualityReworkResult | null>(null);
const reworkReportLedger = ref('');
const releaseLedger = ref('');
const clauseDrafts = ref<Record<number, { verdict: string; reason: string }>>({});
const recheckDrafts = ref<Record<number, { verdict: string; reason: string }>>({});
const requestIds = ref<Record<string, string>>({});
const focusLine = ref(0);
const qualityScan = reactive(useScanContext());

const progressTarget = computed(() => qualityCode.value ? progressTargetFromScan(qualityScan.context) : null);
const shownInspector = computed(() => checkResult.value?.inspector || checkView.value?.inspector || '');
const requirementSubmitted = computed(() => Boolean(checkView.value?.qualityCaseCode));
const canOpenRequirementRework = computed(() => (
  allowed('rework') && checkView.value?.outcome === '不合格' && !checkView.value.reworkOrderCode && !requirementRework.value
));
const canReportRework = computed(() => (
  allowed('report') && Boolean(checkView.value?.reworkOrderCode)
  && !checkView.value?.reworkReported
  && !reworkReportLedger.value
));
const canRecheck = computed(() => allowed('recheck') && Boolean(checkView.value?.canRecheck));
const canRelease = computed(() => allowed('release') && Boolean(checkView.value?.canRelease) && !releaseLedger.value);
const roundCode = computed(() => checkResult.value?.roundCode || checkView.value?.roundCode || '');
const evidenceTargetType = computed(() => (
  qualityScan.identity?.objectType === 'MOTHER_ORDER' ? 'MOTHER_ORDER' : 'WORK_ORDER'
));

const requirementHint = computed(() => {
  const view = checkView.value;
  if (!view || view.qualityCaseCode) return '';
  if (!view.ready) return view.blockReason;
  return missingHint(view.lines, clauseDrafts.value);
});

const recheckHint = computed(() => {
  const view = checkView.value;
  if (!view?.canRecheck) return view?.recheckBlock || '';
  return missingHint(view.lines, recheckDrafts.value);
});

const canSubmitRequirement = computed(() => (
  Boolean(qualityCode.value)
  && allowed('check')
  && Boolean(checkView.value?.ready)
  && !requirementSubmitted.value
  && requirementHint.value === ''
  && !operationBusy.value && !pendingCommand.value
  && !qualityScan.loading
));

const canSubmitRecheck = computed(() => (
  Boolean(qualityCode.value)
  && canRecheck.value
  && recheckHint.value === ''
  && !operationBusy.value && !pendingCommand.value
  && !qualityScan.loading
));

const qualityStep = computed(() => {
  if (!checkView.value) return 0;
  if (!requirementSubmitted.value) return 1;
  return 2;
});

const currentClauseReady = computed(() => {
  const line = checkView.value?.lines[focusLine.value];
  if (!line) return false;
  const draft = clauseDrafts.value[line.lineNo];
  if (!draft?.verdict) return false;
  if ((draft.verdict === '不合格' || draft.verdict === '让步') && !draft.reason.trim()) return false;
  return true;
});

function missingHint(lines: QualityCheckLine[], drafts: Record<number, { verdict: string; reason: string }>) {
  const missingVerdict = lines.some((line) => !drafts[line.lineNo]?.verdict);
  if (missingVerdict) return '每一条加工要求都要有结果才能提交';
  const missingReason = lines.some((line) => {
    const draft = drafts[line.lineNo];
    return (draft?.verdict === '不合格' || draft?.verdict === '让步') && !draft.reason.trim();
  });
  if (missingReason) return '不合格和让步必须写原因';
  return '';
}

function rememberProgress(record: ScanProgressRecord) {
  appendProgressRecord(qualityScan.context, record);
}

function roundLabel(roundType?: string) {
  if (roundType === 'RECHECK') return '复检';
  if (roundType === 'INITIAL') return '初检';
  return '质检';
}

function resetDrafts(view: QualityCheckView) {
  const next: Record<number, { verdict: string; reason: string }> = {};
  const review: Record<number, { verdict: string; reason: string }> = {};
  for (const line of view.lines) {
    next[line.lineNo] = { verdict: line.verdict, reason: line.reason };
    review[line.lineNo] = { verdict: '', reason: '' };
  }
  clauseDrafts.value = next;
  recheckDrafts.value = review;
  focusLine.value = 0;
}

function clearTarget() {
  const had = qualityScan.context !== null || qualityScan.loading || checkView.value !== null || qualityCode.value !== '';
  qualityScan.invalidate();
  checkView.value = null;
  checkResult.value = null;
  requirementRework.value = null;
  reworkReportLedger.value = '';
  releaseLedger.value = '';
  clauseDrafts.value = {};
  recheckDrafts.value = {};
  requestIds.value = {};
  qualityCode.value = '';
  checkBusy.value = false;
  focusLine.value = 0;
  formError.value = '';
  readError.value = '';
  if (had) hint.value = '贴纸已变化，请重新识别';
}

watch(scanText, () => clearTarget(), { flush: 'sync' });

function ensureRequestId(kind: string, fingerprint: string) {
  const key = `${kind}|${fingerprint}`;
  const current = requestIds.value[key];
  if (current) return current;
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const created = `quality-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
  requestIds.value = { ...requestIds.value, [key]: created };
  return created;
}

function lineFingerprint(drafts: Record<number, { verdict: string; reason: string }>) {
  return JSON.stringify(Object.keys(drafts).map(key => [Number(key), drafts[Number(key)]?.verdict ?? '', drafts[Number(key)]?.reason.trim() ?? '']));
}

function linesFrom(drafts: Record<number, { verdict: string; reason: string }>) {
  return (checkView.value?.lines ?? []).map((line) => ({
    lineNo: line.lineNo,
    verdict: drafts[line.lineNo]?.verdict ?? '',
    reason: drafts[line.lineNo]?.reason.trim() ?? '',
  }));
}

async function loadCheck(response: ScanContextResponse, seen: number) {
  if (qualityScan.revision !== seen) return;
  if (response.requiresSelection) {
    hint.value = '请选择要质检的子单';
    return;
  }
  const target = canonicalTarget(response);
  if (props.initialTarget && target && (target.code !== props.initialTarget.code || (props.initialTarget.objectType && target.objectType !== props.initialTarget.objectType))) {
    hint.value = '质检目标与当前单据或执行轮次不一致，未打开原单办理。';
    return;
  }
  if (!target || (target.objectType !== 'WORK_ORDER' && target.objectType !== 'MOTHER_ORDER')) {
    hint.value = '请扫描子单贴纸，或不拆母单的贴纸';
    return;
  }
  checkBusy.value = true;
  try {
    const loaded = await lookupQualityCheck(target.code);
    if (qualityScan.revision !== seen) return;
    if (loaded.orderCode !== target.code) { hint.value = '质检返回的单据与规范目标不一致，请返回列表核对。'; return; }
    qualityCode.value = target.code;
    resetDrafts(loaded);
    checkView.value = loaded;
  } catch (error) {
    if (qualityScan.revision !== seen) return;
    qualityCode.value = '';
    formError.value = keptInputFailure(error, '贴纸识别失败');
  } finally {
    if (qualityScan.revision === seen) checkBusy.value = false;
  }
}

async function recognize(draft: ScanDraft) {
  formError.value = '';
  hint.value = '';
  checkResult.value = null;
  requirementRework.value = null;
  reworkReportLedger.value = '';
  releaseLedger.value = '';
  const content = draft.content.trim();
  if (!content) {
    qualityScan.invalidate();
    hint.value = '请先扫子单贴纸，或不拆母单的贴纸';
    return;
  }
  const response = await qualityScan.lookup({
    content,
    objectType: draft.objectType,
    intent: 'QUALITY',
  });
  if (!response) {
    if (qualityScan.error) formError.value = qualityScan.error;
    return;
  }
  await loadCheck(response, qualityScan.revision);
}

useTraceTarget(['QUALITY'], (target) => {
  if (props.initialTarget) return;
  scanText.value = target.code;
  void recognize({
    content: target.code,
    ...(target.objectType ? { objectType: target.objectType } : {}),
  });
});
onMounted(() => {
  if (!props.initialTarget) return;
  scanText.value = props.initialTarget.code;
  void recognize({ content: props.initialTarget.code, ...(props.initialTarget.objectType ? { objectType: props.initialTarget.objectType } : {}) });
});

async function chooseTarget(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
  if (props.initialTarget) { emit('targetSelected', { objectType: related.objectType, code: related.code }); return; }
  await recognize({ content: related.code, objectType: related.objectType });
}

function stillHere(code: string, seen: number) {
  return qualityScan.revision === seen && qualityCode.value === code;
}

async function refreshView(code: string, seen: number, keepDrafts = false) {
  readError.value = '';
  try {
    const loaded = await lookupQualityCheck(code);
    if (!stillHere(code, seen)) return;
    if (loaded.orderCode !== code) { readError.value = '刷新返回的单据不一致，请核对；原回执保留。'; return; }
    if (loaded.qualityCaseCode || !checkView.value?.qualityCaseCode) {
      const sameLines = JSON.stringify(loaded.lines.map(line => [line.lineNo, line.clause])) === JSON.stringify(checkView.value?.lines.map(line => [line.lineNo, line.clause]));
      const preserve = keepDrafts && sameLines && loaded.roundCode === checkView.value?.roundCode && loaded.qualityCaseCode === checkView.value?.qualityCaseCode;
      checkView.value = loaded;
      if (!preserve) resetDrafts(loaded);
    }
  } catch {
    if (!stillHere(code, seen)) return;
    readError.value = checkView.value?.qualityCaseCode || checkResult.value || requirementRework.value || reworkReportLedger.value || releaseLedger.value
      ? '业务已保存，页面刷新失败。原回执保留，不会再次提交。' : '质检状态刷新失败，已填判定保留。';
  }
}
async function refreshState() {
  if (operationBusy.value || pendingCommand.value || !qualityCode.value || checkBusy.value) return;
  checkBusy.value = true;
  try { await refreshView(qualityCode.value, qualityScan.revision, true); }
  finally { checkBusy.value = false; }
}

async function execute(command: QualityCommand) {
  if (operationBusy.value || checkBusy.value || (pendingCommand.value && pendingCommand.value.requestId !== command.requestId)) return;
  if (!allowed(command.kind) || command.code !== qualityCode.value) { formError.value = '当前权限或单据已变化，请保留原请求并核对。'; return; }
  const seen = qualityScan.revision;
  activeOperation.value = command.kind;
  formError.value = '';
  try {
    try { await command.send(); }
    catch (error) {
      // 网络中断时只用已捕获的原请求回放一次，判定内容不会重新读取表单。
      if (!(error instanceof Error) || error.message !== '后端接口不可用') throw error;
      await command.send();
    }
    if (!stillHere(command.code, seen)) return;
    pendingCommand.value = null;
    hint.value = command.success;
    emit('changed');
    await refreshView(command.code, seen);
  } catch (error) {
    if (!stillHere(command.code, seen)) return;
    const known = error instanceof ApiRequestError && error.status >= 400 && error.status < 500 && error.status !== 408;
    pendingCommand.value = known ? null : command;
    formError.value = keptInputFailure(error, '质检操作失败');
  } finally { activeOperation.value = ''; }
}

function applyCheck(result: QualityCheckResult, view: QualityCheckView) {
  if (result.orderCode !== view.orderCode) throw new Error('质检回执单据不一致，请保留原请求核对');
  checkResult.value = result;
  checkView.value = { ...view, inspector: result.inspector, qualityCaseCode: result.qualityCaseCode,
    outcome: result.outcome, frozen: result.frozen, reworkOrderCode: result.reworkOrderCode,
    lines: result.lines, roundCode: result.roundCode, roundNo: result.roundNo, roundType: result.roundType,
    ready: false, blockReason: '', canRecheck: false, canRelease: false };
  resetDrafts(checkView.value);
}

async function submitRequirement() {
  if (!canSubmitRequirement.value || !checkView.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const view = checkView.value;
  const requestId = ensureRequestId('check', JSON.stringify([code, roundCode.value, lineFingerprint(clauseDrafts.value)]));
  const body = { code, lines: linesFrom(clauseDrafts.value), requestId };
  await execute({ kind: 'check', code, requestId, success: '初检已提交', send: async () => {
    const result = await submitQualityCheck(body);
    if (stillHere(code, seen)) applyCheck(result, view);
  } });
}

async function openRequirementRework() {
  if (!canOpenRequirementRework.value || !checkView.value || pendingCommand.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const view = checkView.value;
  const requestId = ensureRequestId('rework', JSON.stringify([code, roundCode.value, view.qualityCaseCode]));
  await execute({ kind: 'rework', code, requestId, success: '已开返工', send: async () => {
    const opened = await openQualityRework(code, requestId);
    if (!stillHere(code, seen)) return;
    requirementRework.value = opened;
    checkView.value = { ...view, reworkOrderCode: opened.reworkOrderCode };
  } });
}

async function reportRework() {
  if (!canReportRework.value || !checkView.value || pendingCommand.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const view = checkView.value;
  const requestId = ensureRequestId('report', JSON.stringify([code, view.reworkOrderCode, roundCode.value]));
  await execute({ kind: 'report', code, requestId, success: '返工已报工', send: async () => {
    const reported = await reportQualityRework(code, requestId);
    if (!stillHere(code, seen)) return;
    reworkReportLedger.value = reported.ledgerCode;
    checkView.value = { ...view, reworkReported: true };
  } });
}

async function submitRecheck() {
  if (!canSubmitRecheck.value || !checkView.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const view = checkView.value;
  const requestId = ensureRequestId('recheck', JSON.stringify([code, roundCode.value, lineFingerprint(recheckDrafts.value)]));
  const body = { code, lines: linesFrom(recheckDrafts.value), requestId };
  await execute({ kind: 'recheck', code, requestId, success: '复检已提交', send: async () => {
    const result = await submitQualityRecheck(body);
    if (stillHere(code, seen)) applyCheck(result, view);
  } });
}

async function releaseRound() {
  if (!canRelease.value || pendingCommand.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const requestId = ensureRequestId('release', JSON.stringify([code, roundCode.value]));
  await execute({ kind: 'release', code, requestId, success: '已放行', send: async () => {
    const released = await releaseQualityRound(code, requestId);
    if (!stillHere(code, seen)) return;
    releaseLedger.value = released.ledgerCode;
    if (checkView.value) checkView.value = { ...checkView.value, canRelease: false };
  } });
}

function showClause(index: number) {
  return !props.field || requirementSubmitted.value || index === focusLine.value;
}
</script>

<template>
  <section class="quality-check-panel section-block" aria-label="对照加工要求">
    <div class="section-heading">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ intro }}</p>
      </div>
    </div>
    <FieldSteps v-if="field && !embedded" :steps="['扫码识别', '逐条判定', '提交结果']" :current="qualityStep" />
    <fieldset v-if="!initialTarget" class="quality-action-fields" :disabled="operationBusy || Boolean(pendingCommand)">
    <ScanInput
      v-model="scanText"
      field-text="贴纸二维码"
      input-aria-label="质检贴纸"
      placeholder="JG|work_order|子单号 或 JG|mother|母单号"
      submit-label="识别贴纸"
      submit-aria-label="识别质检贴纸"
      photo-aria-label="拍贴纸"
      :bind-mobile-scan="field"
      :busy="checkBusy || qualityScan.loading || operationBusy || Boolean(pendingCommand)"
      @draft="clearTarget"
      @recognize="recognize"
    />
    </fieldset>
    <p v-if="hint" class="operation-feedback warning">{{ hint }}</p>
    <p v-if="checkBusy || qualityScan.loading" role="status">正在读取本单质检状态…</p>
    <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
    <button v-if="initialTarget && !checkView" class="secondary-button" type="button" :disabled="checkBusy || qualityScan.loading || operationBusy || Boolean(pendingCommand)" @click="recognize({ content: initialTarget.code, ...(initialTarget.objectType ? { objectType: initialTarget.objectType } : {}) })">重新读取本单质检</button>
    <button v-if="initialTarget && qualityCode" class="secondary-button" type="button" :disabled="operationBusy || checkBusy || Boolean(pendingCommand)" @click="refreshState">刷新质检状态</button>
    <p v-if="readError" class="operation-feedback danger" role="alert">{{ readError }}</p>
    <p v-if="pendingCommand" class="operation-feedback warning" role="alert">原{{ { check: '质检', rework: '开返工', report: '返工报工', recheck: '复检', release: '放行' }[pendingCommand.kind] }}请求结果待核对，已保留单号、判定和编号。<button class="secondary-button" type="button" :disabled="operationBusy" @click="execute(pendingCommand)">原请求重试</button></p>
    <OrderContextCard v-if="!embedded || qualityScan.context?.requiresSelection || qualityScan.identity?.objectType === 'REWORK'" :context="qualityScan.context" @select="chooseTarget" />
    <details v-if="progressTarget" class="quality-process-note">
      <summary>记录质检过程</summary>
      <ProgressNoteForm stage="QUALITY" :target-type="progressTarget.targetType" :target-code="progressTarget.targetCode" :mother-order-code="progressTarget.motherOrderCode" @saved="rememberProgress" />
    </details>

    <fieldset v-if="checkView" class="quality-action-fields" :disabled="operationBusy || checkBusy || Boolean(pendingCommand)">
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
      <p v-if="field" class="quality-inspector-note">质检员取当前登录账号，页面不能另填姓名。</p>

      <template v-for="(line, index) in checkView.lines" :key="line.lineNo">
      <article v-if="showClause(index)" class="quality-clause">
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
      </template>

      <p v-if="requirementHint" class="operation-feedback warning">{{ requirementHint }}</p>
      <div v-if="field && !requirementSubmitted" class="field-dock">
        <button
          v-if="focusLine > 0"
          class="secondary-button"
          type="button"
          aria-label="上一条"
          @click="focusLine -= 1"
        >
          上一条
        </button>
        <button
          v-if="focusLine < checkView.lines.length - 1"
          class="primary-button"
          type="button"
          aria-label="下一条"
          :disabled="!currentClauseReady"
          @click="focusLine += 1"
        >
          下一条
        </button>
        <button
          v-else
          class="primary-button"
          type="button"
          aria-label="提交质检"
          :disabled="!canSubmitRequirement"
          @click="submitRequirement"
        >
          {{ checkSubmitting ? '正在提交' : '提交质检' }}
        </button>
      </div>
      <div v-if="!field && !requirementSubmitted" class="button-row">
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
      <div v-if="checkResult && checkResult.roundType !== 'RECHECK'" class="operation-feedback success">
        <strong>{{ roundLabel(checkResult.roundType) }}已提交 · {{ checkResult.outcome }}</strong>
        <span>{{ checkResult.inspector }}</span>
        <span>质检单 {{ checkResult.qualityCaseCode }}</span>
        <span v-if="checkResult.roundCode">轮次 {{ checkResult.roundCode }}</span>
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
      <div v-if="canReportRework" class="button-row">
        <button class="primary-button" type="button" aria-label="提交返工报工" :disabled="reportingRework" @click="reportRework">
          {{ reportingRework ? '正在报工' : '提交返工报工' }}
        </button>
      </div>
      <p v-if="reworkReportLedger" class="operation-feedback success">返工报工已保存 {{ reworkReportLedger }}</p>

      <template v-if="canRecheck">
        <h3>复检</h3>
        <p>原不合格记录保留。这一轮单独判定，不会改写初检。</p>
        <article v-for="line in checkView.lines" :key="`recheck-${line.lineNo}`">
          <strong>{{ line.lineNo }}. {{ line.clause }}</strong>
          <div class="quality-verdict-row">
            <button
              v-for="verdict in ['合格', '不合格', '让步']"
              :key="verdict"
              type="button"
              class="secondary-button"
              :aria-label="`复检${verdict} ${line.lineNo}`"
              @click="recheckDrafts[line.lineNo].verdict = verdict"
            >
              {{ verdict }}
            </button>
          </div>
          <label class="field">
            <span>复检原因</span>
            <textarea v-model="recheckDrafts[line.lineNo].reason" :aria-label="`复检原因 ${line.lineNo}`" />
          </label>
        </article>
        <p v-if="recheckHint" class="operation-feedback warning">{{ recheckHint }}</p>
        <button class="primary-button" type="button" aria-label="提交复检" :disabled="!canSubmitRecheck" @click="submitRecheck">
          {{ recheckSubmitting ? '正在提交复检' : '提交复检' }}
        </button>
      </template>
      <p v-else-if="requirementSubmitted && checkView.recheckBlock" class="operation-feedback warning">{{ checkView.recheckBlock }}</p>
      <div v-if="checkResult?.roundType === 'RECHECK'" class="operation-feedback success">
        <strong>复检已提交 · {{ checkResult.outcome }}</strong>
        <span>轮次 {{ checkResult.roundCode }}</span>
        <span>{{ checkResult.frozen ? '半成品已再次冻结' : '本轮未冻结，是否可放行仍按当前服务端条件核对' }}</span>
      </div>
      <div v-if="canRelease" class="button-row">
        <button class="primary-button" type="button" aria-label="放行" :disabled="releasing" @click="releaseRound">
          {{ releasing ? '正在放行' : '放行' }}
        </button>
      </div>
      <p v-else-if="requirementSubmitted && checkView.releaseBlock" class="operation-feedback warning">{{ checkView.releaseBlock }}</p>
      <p v-if="releaseLedger" class="operation-feedback success">已放行 {{ releaseLedger }}。原不合格记录仍保留。</p>
      <details v-if="checkView.rounds?.length" class="quality-round-history">
        <summary>检验轮次与历史（{{ checkView.rounds.length }}）</summary>
        <article v-for="round in checkView.rounds" :key="round.roundCode" class="quality-clause">
          <h3>{{ roundLabel(round.roundType) }} {{ round.roundNo }} · {{ round.roundCode }}</h3>
          <p>{{ round.createdAt }} · {{ round.inspector }} · {{ round.outcome }}<template v-if="round.frozen"> · 已冻结</template></p>
          <p v-if="round.reworkOrderCode">返工 {{ round.reworkOrderCode }} · 报工台账 {{ round.reworkReportLedger || '未报工' }}</p>
          <p v-for="line in round.lines" :key="line.lineNo">{{ line.lineNo }}. {{ line.clause }} · {{ line.verdict }} {{ line.reason }}</p>
        </article>
      </details>
    </fieldset>

    <section v-if="qualityCode" class="section-block">
      <h2>质检证据</h2>
      <p v-if="roundCode">业务已保存。证据上传或关联失败时只重试文件，不会再次质检。</p>
      <p v-else>可以先选择文件。正式质检成功后才关联到本轮，不会把待上传文件当成已经判定。</p>
      <EvidenceCapture
        :target-type="evidenceTargetType"
        :target-code="qualityCode"
        stage="QUALITY"
        scene="CHECK"
        operation-type="quality_round"
        :operation-code="roundCode"
      />
    </section>
  </section>
</template>

<style scoped>
.quality-action-fields { border: 0; min-width: 0; padding: 0; margin: 0; }
.quality-action-fields[disabled] .quality-verdict-row { opacity: .65; }
.quality-round-history { margin-block: 12px; }
</style>
