<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
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

defineProps<{
  title: string;
  intro: string;
}>();

const scanText = ref('');
const hint = ref('');
const formError = ref('');
const qualityCode = ref('');
const checkBusy = ref(false);
const checkSubmitting = ref(false);
const reworkOpening = ref(false);
const reportingRework = ref(false);
const recheckSubmitting = ref(false);
const releasing = ref(false);
const checkView = ref<QualityCheckView | null>(null);
const checkResult = ref<QualityCheckResult | null>(null);
const requirementRework = ref<QualityReworkResult | null>(null);
const reworkReportLedger = ref('');
const releaseLedger = ref('');
const clauseDrafts = ref<Record<number, { verdict: string; reason: string }>>({});
const recheckDrafts = ref<Record<number, { verdict: string; reason: string }>>({});
const requestIds = ref<Record<string, string>>({});
const qualityScan = reactive(useScanContext());

const progressTarget = computed(() => progressTargetFromScan(qualityScan.context));
const shownInspector = computed(() => checkResult.value?.inspector || checkView.value?.inspector || '');
const requirementSubmitted = computed(() => Boolean(checkView.value?.qualityCaseCode));
const canOpenRequirementRework = computed(() => (
  checkView.value?.outcome === '不合格' && !checkView.value.reworkOrderCode && !requirementRework.value
));
const canReportRework = computed(() => (
  Boolean(checkView.value?.reworkOrderCode)
  && !checkView.value?.reworkReported
  && !reworkReportLedger.value
));
const canRecheck = computed(() => Boolean(checkView.value?.canRecheck) && !recheckSubmitting.value);
const canRelease = computed(() => Boolean(checkView.value?.canRelease) && !releasing.value && !releaseLedger.value);
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
  && Boolean(checkView.value?.ready)
  && !requirementSubmitted.value
  && requirementHint.value === ''
  && !checkSubmitting.value
  && !qualityScan.loading
));

const canSubmitRecheck = computed(() => (
  Boolean(qualityCode.value)
  && canRecheck.value
  && recheckHint.value === ''
  && !qualityScan.loading
));

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
  formError.value = '';
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
  return Object.keys(drafts)
    .map((key) => `${key}:${drafts[Number(key)]?.verdict ?? ''}:${drafts[Number(key)]?.reason.trim() ?? ''}`)
    .join('|');
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
  if (!target || (target.objectType !== 'WORK_ORDER' && target.objectType !== 'MOTHER_ORDER')) {
    hint.value = '请扫描子单贴纸，或不拆母单的贴纸';
    return;
  }
  checkBusy.value = true;
  try {
    const loaded = await lookupQualityCheck(target.code);
    if (qualityScan.revision !== seen) return;
    qualityCode.value = target.code;
    resetDrafts(loaded);
    checkView.value = loaded;
  } catch (error) {
    if (qualityScan.revision !== seen) return;
    qualityCode.value = '';
    formError.value = error instanceof Error ? error.message : '贴纸识别失败';
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
  scanText.value = target.code;
  void recognize({
    content: target.code,
    ...(target.objectType ? { objectType: target.objectType } : {}),
  });
});

async function chooseTarget(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
  await recognize({ content: related.code, objectType: related.objectType });
}

function stillHere(code: string, seen: number) {
  return qualityScan.revision === seen && qualityCode.value === code;
}

async function refreshView(code: string, seen: number) {
  try {
    const loaded = await lookupQualityCheck(code);
    if (!stillHere(code, seen)) return;
    if (loaded.qualityCaseCode) {
      checkView.value = loaded;
      resetDrafts(loaded);
    }
  } catch {
    if (!stillHere(code, seen)) return;
    hint.value = '业务已保存，页面刷新失败。请重新打开本单，不会再次提交。';
  }
}

async function retrySame(message: string, again: () => Promise<void>) {
  if (message !== '后端接口不可用') return false;
  try {
    await again();
    return true;
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '提交失败';
    hint.value = '请用原确认重试，不会另开一次质检';
    return true;
  }
}

async function submitRequirement() {
  if (!canSubmitRequirement.value || !checkView.value || !qualityCode.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const view = checkView.value;
  const requestId = ensureRequestId('check', `${code}|${lineFingerprint(clauseDrafts.value)}`);
  checkSubmitting.value = true;
  formError.value = '';
  const send = () => submitQualityCheck({ code, lines: linesFrom(clauseDrafts.value), requestId });
  try {
    const result = await send();
    if (!stillHere(code, seen)) return;
    checkResult.value = result;
    checkView.value = {
      ...view,
      inspector: result.inspector,
      qualityCaseCode: result.qualityCaseCode,
      outcome: result.outcome,
      frozen: result.frozen,
      reworkOrderCode: result.reworkOrderCode,
      lines: result.lines,
      roundCode: result.roundCode,
      roundNo: result.roundNo,
      roundType: result.roundType,
      ready: false,
      blockReason: '',
    };
    hint.value = `${roundLabel(result.roundType)}已提交`;
    await refreshView(code, seen);
  } catch (error) {
    if (!stillHere(code, seen)) return;
    const message = error instanceof Error ? error.message : '提交质检失败';
    const recovered = await retrySame(message, async () => {
      const result = await send();
      if (!stillHere(code, seen)) return;
      checkResult.value = result;
      hint.value = `${roundLabel(result.roundType)}已提交`;
      await refreshView(code, seen);
    });
    if (!recovered) formError.value = message;
  } finally {
    checkSubmitting.value = false;
  }
}

async function openRequirementRework() {
  if (!checkView.value || !qualityCode.value || !canOpenRequirementRework.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const view = checkView.value;
  const requestId = ensureRequestId('rework', code);
  reworkOpening.value = true;
  formError.value = '';
  const send = () => openQualityRework(code, requestId);
  try {
    const opened = await send();
    if (!stillHere(code, seen)) return;
    requirementRework.value = opened;
    checkView.value = { ...view, reworkOrderCode: opened.reworkOrderCode };
    hint.value = '已开返工';
    await refreshView(code, seen);
  } catch (error) {
    if (!stillHere(code, seen)) return;
    const message = error instanceof Error ? error.message : '开返工失败';
    const recovered = await retrySame(message, async () => {
      const opened = await send();
      if (!stillHere(code, seen)) return;
      requirementRework.value = opened;
      hint.value = '已开返工';
    });
    if (!recovered) formError.value = message;
  } finally {
    reworkOpening.value = false;
  }
}

async function reportRework() {
  if (!canReportRework.value || !qualityCode.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const view = checkView.value;
  const requestId = ensureRequestId('report', code);
  reportingRework.value = true;
  formError.value = '';
  const send = () => reportQualityRework(code, requestId);
  try {
    const reported = await send();
    if (!stillHere(code, seen)) return;
    reworkReportLedger.value = reported.ledgerCode;
    if (view) checkView.value = { ...view, reworkReported: true };
    hint.value = '返工已报工';
    await refreshView(code, seen);
  } catch (error) {
    if (!stillHere(code, seen)) return;
    const message = error instanceof Error ? error.message : '返工报工失败';
    const recovered = await retrySame(message, async () => {
      const reported = await send();
      if (!stillHere(code, seen)) return;
      reworkReportLedger.value = reported.ledgerCode;
      hint.value = '返工已报工';
    });
    if (!recovered) formError.value = message;
  } finally {
    reportingRework.value = false;
  }
}

async function submitRecheck() {
  if (!canSubmitRecheck.value || !qualityCode.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const requestId = ensureRequestId('recheck', `${code}|${lineFingerprint(recheckDrafts.value)}`);
  recheckSubmitting.value = true;
  formError.value = '';
  const send = () => submitQualityRecheck({ code, lines: linesFrom(recheckDrafts.value), requestId });
  try {
    const result = await send();
    if (!stillHere(code, seen)) return;
    checkResult.value = result;
    hint.value = '复检已提交';
    await refreshView(code, seen);
  } catch (error) {
    if (!stillHere(code, seen)) return;
    const message = error instanceof Error ? error.message : '提交复检失败';
    const recovered = await retrySame(message, async () => {
      const result = await send();
      if (!stillHere(code, seen)) return;
      checkResult.value = result;
      hint.value = '复检已提交';
    });
    if (!recovered) formError.value = message;
  } finally {
    recheckSubmitting.value = false;
  }
}

async function releaseRound() {
  if (!canRelease.value || !qualityCode.value) return;
  const code = qualityCode.value;
  const seen = qualityScan.revision;
  const requestId = ensureRequestId('release', `${code}|${roundCode.value}`);
  releasing.value = true;
  formError.value = '';
  const send = () => releaseQualityRound(code, requestId);
  try {
    const released = await send();
    if (!stillHere(code, seen)) return;
    releaseLedger.value = released.ledgerCode;
    hint.value = '已放行';
    await refreshView(code, seen);
  } catch (error) {
    if (!stillHere(code, seen)) return;
    const message = error instanceof Error ? error.message : '放行失败';
    const recovered = await retrySame(message, async () => {
      const released = await send();
      if (!stillHere(code, seen)) return;
      releaseLedger.value = released.ledgerCode;
      hint.value = '已放行';
    });
    if (!recovered) formError.value = message;
  } finally {
    releasing.value = false;
  }
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
    <ScanInput
      v-model="scanText"
      field-text="贴纸二维码"
      input-aria-label="质检贴纸"
      placeholder="JG|work_order|子单号 或 JG|mother|母单号"
      submit-label="识别贴纸"
      submit-aria-label="识别质检贴纸"
      photo-aria-label="拍贴纸"
      :busy="checkBusy || qualityScan.loading"
      @draft="clearTarget"
      @recognize="recognize"
    />
    <p v-if="hint" class="operation-feedback warning">{{ hint }}</p>
    <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
    <OrderContextCard :context="qualityScan.context" @select="chooseTarget" />
    <ProgressNoteForm
      v-if="progressTarget"
      stage="QUALITY"
      :target-type="progressTarget.targetType"
      :target-code="progressTarget.targetCode"
      :mother-order-code="progressTarget.motherOrderCode"
      @saved="rememberProgress"
    />

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
        <span>{{ checkResult.frozen ? '半成品已再次冻结' : '本轮可以用于放行' }}</span>
      </div>
      <div v-if="canRelease" class="button-row">
        <button class="primary-button" type="button" aria-label="放行" :disabled="releasing" @click="releaseRound">
          {{ releasing ? '正在放行' : '放行' }}
        </button>
      </div>
      <p v-else-if="requirementSubmitted && checkView.releaseBlock" class="operation-feedback warning">{{ checkView.releaseBlock }}</p>
      <p v-if="releaseLedger" class="operation-feedback success">已放行 {{ releaseLedger }}。原不合格记录仍保留。</p>
    </template>

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
