<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import type { OrderActionTarget } from '../orders/orderActions';
import { ApiRequestError } from '../../services/apiClient';
import StatusBadge from '../../components/StatusBadge.vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import FieldSteps from '../field/FieldSteps.vue';
import { keptInputFailure, requirementLines } from '../field/keptInput';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ProgressNoteForm from '../scan/ProgressNoteForm.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { appendProgressRecord, progressTargetFromScan } from '../scan/progressTarget';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import { useTraceTarget } from '../scan/useTraceTarget';
import type { ScanContextResponse, ScanProgressRecord, ScanRelatedTarget } from '../../services/scanContextApi';
import {
  lookupProcessStation,
  submitProcessStation,
  type ProcessStationCard,
  type ProcessStationReport,
  type ProcessStationView,
} from '../../services/processStationApi';

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
const emit = defineEmits<{ saved: [result: ProcessStationReport]; targetSelected: [target: OrderActionTarget]; locked: [value: boolean] }>();

const scanText = ref('');
const hint = ref('');
const formError = ref('');
const stationView = ref<ProcessStationView | null>(null);
const stationCode = ref('');
const stationBusy = ref(false);
const submittingProcess = ref('');
const pendingDelivery = ref<{ code: string; processName: string; requestId: string; batch?: { quantity: string; finalWeight?: string } } | null>(null);
const locked = computed(() => Boolean(submittingProcess.value) || Boolean(pendingDelivery.value));
watch(locked, value => emit('locked', value), { immediate: true });
const delivered = ref<ProcessStationReport | null>(null);
const requestIds = ref<Record<string, string>>({});
const checkedRequirement = ref<Record<string, boolean>>({});
const reviewConfirmed = ref<Record<string, boolean>>({});
const activeProcess = ref('');
const batchQuantity = ref('');
const finalWeight = ref('');
const evidenceDrafts = ref<Array<{ key: number; result: ProcessStationReport | null }>>([{ key: 0, result: null }]);
const evidenceDraftKey = ref(0);
const currentEvidenceDraftKey = ref(0);
function evidenceType(result: ProcessStationReport | null) { return result?.batchCode ? 'process_batch_report' : result?.movedToSemiFinished ? 'production_report' : result ? 'process_station_report' : ''; }
function nextBatch() {
  if (locked.value) return;
  hint.value = ''; formError.value = '';
  delivered.value = null; checkedRequirement.value = {}; reviewConfirmed.value = {}; requestIds.value = {};
  batchQuantity.value = activeStation.value?.availableQuantity === 0 ? '0' : ''; finalWeight.value = '';
  const key = evidenceDrafts.value.length; evidenceDrafts.value.push({ key, result: null }); evidenceDraftKey.value = key; currentEvidenceDraftKey.value = key;
}
watch(activeProcess, (current, previous) => { if (!locked.value) { if (previous && current !== previous) nextBatch(); batchQuantity.value = ''; finalWeight.value = ''; } });
const stationScan = reactive(useScanContext());

const progressTarget = computed(() => stationCode.value ? progressTargetFromScan(stationScan.context) : null);
const processOptions = computed(() => (stationView.value?.stations ?? []).map((item) => item.processName));
const evidenceTargetType = computed(() => (
  stationScan.identity?.objectType === 'MOTHER_ORDER' ? 'MOTHER_ORDER' : 'WORK_ORDER'
));
const evidenceOperationType = computed(() => {
  if (!delivered.value) return '';
  return delivered.value.movedToSemiFinished ? 'production_report' : 'process_station_report';
});
const activeStation = computed(() => (
  stationView.value?.stations.find((item) => item.processName === activeProcess.value)
));
const activeLines = computed(() => requirementLines(activeStation.value?.requirement ?? ''));
const checkedCount = computed(() => activeLines.value.filter((_, index) => (
  checkedRequirement.value[checkKey(activeStation.value?.processName ?? '', index)]
)).length);
const quantityReady = computed(() => stationScan.context?.order.quantity != null && stationScan.context?.order.weight != null);
const batchMode = computed(() => activeStation.value?.totalQuantity != null);
function previousReady(station: ProcessStationCard) {
  return station.availableQuantity != null ? station.availableQuantity > 0 || station.deliveredQuantity === station.totalQuantity : !station.previousProcessName || station.previousSubmitted;
}
function batchInputReady() {
  if (!activeStation.value || !batchMode.value) return true;
  const q = batchQuantity.value.trim(); const w = finalWeight.value.trim();
  if (!/^\d+(?:\.\d{1,3})?$/.test(q) || Number(q) > (activeStation.value.availableQuantity ?? 0)) return false;
  const all = Number(q) + (activeStation.value.deliveredQuantity ?? 0) === activeStation.value.totalQuantity;
  if (w && (!activeStation.value.finalProcess || !all || !/^\d+(?:\.\d{1,3})?$/.test(w) || Number(w) <= 0)) return false;
  return Number(q) > 0 || (all && Boolean(activeStation.value.finalProcess) && Number(w) > 0);
}
const canConfirmReview = computed(() => {
  if (!activeStation.value || activeStation.value.submitted || !previousReady(activeStation.value) || locked.value || stationBusy.value || stationScan.loading || (!batchMode.value && !quantityReady.value) || Boolean(delivered.value) || !batchInputReady()) return false;
  return activeLines.value.length === 0 || checkedCount.value === activeLines.value.length;
});

watch(stationView, (view) => {
  const stations = view?.stations ?? [];
  if (!stations.some((item) => item.processName === activeProcess.value)) {
    activeProcess.value = stations.find(item => !item.submitted && previousReady(item))?.processName ?? stations.find(item => !item.submitted)?.processName ?? stations[0]?.processName ?? '';
  }
});

function checkKey(processName: string, index: number) {
  return `${processName}:${index}`;
}

function toggleCheck(processName: string, index: number, checked: boolean) {
  checkedRequirement.value = { ...checkedRequirement.value, [checkKey(processName, index)]: checked };
}

async function confirmAndDeliver() {
  const station = activeStation.value;
  if (!station || !canConfirmReview.value) return;
  // 点击明确的交工按钮后才提交；逐条勾选本身不写业务。
  reviewConfirmed.value = { ...reviewConfirmed.value, [station.processName]: true };
  await deliver(station);
}

function amountText(value: number | null | undefined, unit: string | null | undefined) {
  if (value === null || value === undefined) return '未记录';
  return unit?.trim() ? `${value} ${unit}` : String(value);
}

function rememberProgress(record: ScanProgressRecord) {
  appendProgressRecord(stationScan.context, record);
}

function previousText(station: ProcessStationCard) {
  if (!station.previousProcessName) return '无上一道';
  return station.previousSubmitted ? '已交' : '未交';
}

function clearStationTarget() {
  if (locked.value) return;
  const had = stationScan.context !== null || stationScan.loading || stationView.value !== null || stationCode.value !== '';
  stationScan.invalidate();
  stationView.value = null;
  stationCode.value = '';
  stationBusy.value = false;
  delivered.value = null;
  requestIds.value = {};
  checkedRequirement.value = {};
  reviewConfirmed.value = {};
  activeProcess.value = '';
  formError.value = '';
  batchQuantity.value = ''; finalWeight.value = ''; evidenceDrafts.value = [{ key: 0, result: null }]; evidenceDraftKey.value = 0; currentEvidenceDraftKey.value = 0;
  if (had) hint.value = '贴纸已变化，请重新识别';
}

watch(scanText, () => clearStationTarget(), { flush: 'sync' });

function ensureRequestId(code: string, processName: string) {
  const fingerprint = `${code}|${processName.trim()}|${batchMode.value ? batchQuantity.value.trim() : ""}|${finalWeight.value.trim()}`;
  const current = requestIds.value[fingerprint];
  if (current) return current;
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const created = `station-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
  requestIds.value = { ...requestIds.value, [fingerprint]: created };
  return created;
}

async function loadStation(response: ScanContextResponse, seen: number) {
  if (stationScan.revision !== seen) return;
  if (response.requiresSelection) {
    hint.value = '这张母单已经拆分，请选择子单';
    return;
  }
  const target = canonicalTarget(response);
  if (!target) {
    hint.value = '没有定位到可报工的单据';
    return;
  }
  if (props.initialTarget && (target.code !== props.initialTarget.code
    || (props.initialTarget.objectType && target.objectType !== props.initialTarget.objectType))) {
    hint.value = '报工目标与当前单据或执行轮次不一致，请返回列表核对。';
    return;
  }
  if (target.objectType === 'EXPRESS_NO') {
    hint.value = '这是快递单号，请切到收货';
    return;
  }
  if (target.objectType !== 'WORK_ORDER' && target.objectType !== 'MOTHER_ORDER') {
    hint.value = '请扫描子单贴纸，或不拆母单的贴纸';
    return;
  }
  stationBusy.value = true;
  try {
    const loaded = await lookupProcessStation(target.code);
    if (stationScan.revision !== seen) return;
    if (loaded.orderCode !== target.code) {
      hint.value = '岗位单据与规范目标不一致，未打开正式交工。';
      return;
    }
    stationCode.value = target.code;
    stationView.value = loaded;
    if (loaded.message) hint.value = loaded.message;
  } catch (error) {
    if (stationScan.revision !== seen) return;
    stationCode.value = '';
    stationView.value = null;
    formError.value = keptInputFailure(error, '贴纸识别失败');
  } finally {
    if (stationScan.revision === seen) stationBusy.value = false;
  }
}

async function recognizeStation(draft: ScanDraft) {
  if (locked.value) return;
  formError.value = '';
  hint.value = '';
  const content = draft.content.trim();
  if (!content) {
    stationScan.invalidate();
    hint.value = '请先扫子单贴纸，或不拆母单的贴纸';
    return;
  }
  const response = await stationScan.lookup({
    content,
    objectType: draft.objectType,
    intent: 'PRODUCTION',
  });
  if (!response) {
    if (stationScan.error) formError.value = stationScan.error;
    return;
  }
  await loadStation(response, stationScan.revision);
}

useTraceTarget(['PRODUCTION'], (target) => {
  if (props.initialTarget) return;
  scanText.value = target.code;
  void recognizeStation({
    content: target.code,
    ...(target.objectType ? { objectType: target.objectType } : {}),
  });
});
onMounted(() => {
  if (!props.initialTarget) return;
  scanText.value = props.initialTarget.code;
  void recognizeStation({ content: props.initialTarget.code, ...(props.initialTarget.objectType ? { objectType: props.initialTarget.objectType } : {}) });
});

async function chooseStationTarget(related: ScanRelatedTarget) {
  if (locked.value) return;
  if (!related.code || !related.objectType) return;
  if (props.initialTarget) { emit('targetSelected', { objectType: related.objectType, code: related.code }); return; }
  formError.value = '';
  hint.value = '';
  const response = await stationScan.lookup({
    content: related.code,
    objectType: related.objectType,
    intent: 'PRODUCTION',
  });
  if (!response) {
    if (stationScan.error) formError.value = stationScan.error;
    return;
  }
  await loadStation(response, stationScan.revision);
}

async function applyResult(result: ProcessStationReport, code: string, processName: string, seen: number) {
  if (stationScan.revision !== seen || stationCode.value !== code) return;
  pendingDelivery.value = null;
  delivered.value = result;
  const draft = evidenceDrafts.value.find(item => item.key === currentEvidenceDraftKey.value); if (draft) draft.result = result;
  if (stationView.value) stationView.value = { ...stationView.value, stations: stationView.value.stations.map(station => station.processName === result.processName ? { ...station, submitted: result.processCompleted !== false, deliveredQuantity: result.deliveredQuantity ?? station.deliveredQuantity, availableQuantity: Math.max(0, (station.availableQuantity ?? 0) - (result.batchQuantity ?? 0)) } : station) };
  emit('saved', result);
  try {
    const loaded = await lookupProcessStation(code);
    if (stationScan.revision !== seen || stationCode.value !== code) return;
    if (loaded.orderCode !== code) { hint.value = '交付已保存，刷新返回的单据不一致，请重新打开本单核对。'; return; }
    stationView.value = loaded;
  } catch {
    if (stationScan.revision !== seen) return;
    hint.value = '交付已保存，页面刷新失败。请重新打开本单，不会再次交付。';
    return;
  }
  hint.value = result.processCompleted === false ? "本批数量已保存，可继续登记后续批次" : `${processName}已全部交齐`;
}

async function deliver(station: ProcessStationCard) {
  const code = stationCode.value;
  const seen = stationScan.revision;
  formError.value = '';
  if (props.field && !reviewConfirmed.value[station.processName]) return;
  if (!previousReady(station)) { formError.value = `请先完成上一道「${station.previousProcessName}」交工`; return; }
  if (pendingDelivery.value && (pendingDelivery.value.code !== code || pendingDelivery.value.processName !== station.processName)) return;
  if (!code || station.submitted || stationScan.loading || submittingProcess.value
    || (delivered.value?.orderCode === code && delivered.value.processName === station.processName)) return;
  const currentRequestId = pendingDelivery.value?.requestId ?? ensureRequestId(code, station.processName);
  submittingProcess.value = station.processName;
  try {
    const batch = pendingDelivery.value?.batch ?? (batchMode.value ? { quantity: batchQuantity.value.trim(), ...(finalWeight.value.trim() ? { finalWeight: finalWeight.value.trim() } : {}) } : undefined);
    const result = batch ? await submitProcessStation(code, station.processName, currentRequestId, batch) : await submitProcessStation(code, station.processName, currentRequestId);
    await applyResult(result, code, station.processName, seen);
  } catch (error) {
    if (stationScan.revision !== seen) return;
    const knownFailure = error instanceof ApiRequestError && error.status >= 400 && error.status < 500 && error.status !== 408;
    pendingDelivery.value = knownFailure ? null : { code, processName: station.processName, requestId: currentRequestId, batch: batchMode.value ? { quantity: batchQuantity.value.trim(), ...(finalWeight.value.trim() ? { finalWeight: finalWeight.value.trim() } : {}) } : undefined };
    formError.value = keptInputFailure(error, '交工失败');
  } finally {
    if (submittingProcess.value === station.processName) submittingProcess.value = '';
  }
}
function retryDelivery() {
  const station = stationView.value?.stations.find(item => item.processName === pendingDelivery.value?.processName);
  if (station) void deliver(station);
  else formError.value = '原交工工序尚未读取完整，请保留本单并核对原结果';
}
</script>

<template>
  <section class="section-block" :class="{ 'production-field': field }">
    <div class="section-heading">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ intro }}</p>
      </div>
    </div>
    <FieldSteps v-if="field && !embedded" :steps="['扫码识别', '核对并交工']" :current="stationView ? 1 : 0" />
    <ScanInput
      v-if="!initialTarget"
      v-model="scanText"
      field-text="贴纸二维码"
      input-aria-label="贴纸二维码"
      placeholder="JG|work_order|子单号 或 JG|mother|母单号"
      submit-label="识别贴纸"
      submit-aria-label="识别贴纸"
      photo-aria-label="拍贴纸"
      :bind-mobile-scan="field"
      :busy="stationScan.loading || stationBusy || locked"
      @draft="clearStationTarget"
      @recognize="recognizeStation"
    />
    <p v-if="hint" class="operation-feedback">{{ hint }}</p>
    <p v-if="formError" class="operation-feedback danger" role="alert">{{ formError }}</p>
    <p v-if="pendingDelivery" class="operation-feedback warning" role="alert">交工结果待核对：{{ pendingDelivery.code }} · {{ pendingDelivery.processName }}。原确认编号已保留。<button type="button" class="secondary-button" :disabled="Boolean(submittingProcess)" @click="retryDelivery">原交工请求重试</button></p>
    <button v-if="initialTarget && !stationView" class="secondary-button" type="button" :disabled="stationBusy || stationScan.loading || locked" @click="recognizeStation({ content: initialTarget.code, ...(initialTarget.objectType ? { objectType: initialTarget.objectType } : {}) })">重新读取本单工序</button>
    <p v-if="delivered" class="operation-feedback" role="status">交工已保存：{{ delivered.orderCode }} · {{ delivered.processName }}<template v-if="delivered.batchCode"> · 本批 {{ delivered.batchQuantity }}，累计 {{ delivered.deliveredQuantity }}，剩余 {{ delivered.remainingQuantity }}</template><template v-if="delivered.ledgerCode"> · 台账 {{ delivered.ledgerCode }}</template></p>
    <p v-if="delivered" class="operation-feedback" role="status">{{ delivered.processCompleted === false ? (delivered.remainingQuantity === 0 ? '数量已交齐，待末道总重量登记后进入质检。' : '本批已交出的数量可以继续下一道，当前工序尚未全部交齐。') : delivered.movedToSemiFinished ? '本单工序已完成，已转半成品，下一步由质检处理。' : '本工序已交工，下一岗位可继续；库存仍在制。' }}</p>
    <div v-if="delivered?.processCompleted === false" class="button-row"><button type="button" class="primary-button" @click="nextBatch">{{ delivered.remainingQuantity === 0 ? '登记末道总重量' : '登记下一批' }}</button></div>
    <button v-if="initialTarget && stationView" class="secondary-button" type="button" :disabled="locked || stationBusy" @click="recognizeStation({ content: initialTarget.code, ...(initialTarget.objectType ? { objectType: initialTarget.objectType } : {}) })">刷新可交数量</button>
    <OrderContextCard v-if="!embedded || stationScan.context?.requiresSelection" :context="stationScan.context" @select="chooseStationTarget" />
    <article v-if="field && stationView" class="field-card" aria-label="当前单据">
      <div class="field-card-head">
        <span>{{ stationScan.identity?.objectType === 'MOTHER_ORDER' ? '当前母单' : '当前子单' }}</span>
        <StatusBadge :label="activeStation?.submitted ? '已交工' : activeStation && !previousReady(activeStation) ? '待上一道交工' : '待交工'" :tone="activeStation?.submitted ? 'green' : 'amber'" />
      </div>
      <strong>{{ activeStation?.processName || '暂无可办理工序' }}</strong><span class="info-context">{{ stationView.orderCode }}</span>
      <p v-if="stationScan.context?.target.motherOrderCode && stationScan.identity?.objectType !== 'MOTHER_ORDER'">
        母单 {{ stationScan.context.target.motherOrderCode }}
        <template v-if="stationView.customerName"> · {{ stationView.customerName }}</template>
      </p>
      <details class="field-note">
      <summary>查看物料、期限与工序信息</summary>
      <dl class="detail-grid">
        <dt>客户</dt>
        <dd>{{ stationView.customerName || '未记录' }}</dd>
        <dt>物料</dt>
        <dd>{{ stationScan.context?.order.goodsName?.trim() || '未记录' }}</dd>
        <dt>期限</dt>
        <dd>{{ stationView.dueDate || '未记录' }}</dd>
        <dt>本账号工序</dt>
        <dd>{{ processOptions.join('、') || '未绑定' }}</dd>
        <template v-if="activeStation">
          <dt>{{ activeStation.processName }}要求</dt>
          <dd>{{ activeStation.requirement || '未记录' }}</dd>
          <dt>上一道是否已交</dt>
          <dd>{{ previousText(activeStation) }}</dd>
        </template>
      </dl>
      </details>
      <p v-if="!embedded" class="field-coach">只显示当前账号能做的工序。</p>
    </article>
    <div v-if="field && (stationView?.stations.length ?? 0) > 1" class="field-process-list">
      <p v-if="!activeStation">请选择本次办理的工序。</p>
      <button
        v-for="station in stationView?.stations ?? []"
        :key="station.processName"
        type="button"
        :class="station.processName === activeProcess ? 'primary-button' : 'secondary-button'"
        :aria-pressed="station.processName === activeProcess"
        :aria-label="`选择工序 ${station.processName}`"
        :disabled="locked || (!station.submitted && !previousReady(station))"
        @click="activeProcess = station.processName"
      >
        {{ station.processName }}<small>{{ station.submitted ? '已交工' : previousReady(station) ? '可办理' : '待上一道交工' }}</small>
      </button>
    </div>
    <p v-if="field && activeStation && !activeStation.submitted && !previousReady(activeStation)" class="operation-feedback warning" role="alert">上一道「{{ activeStation.previousProcessName }}」还未交工，当前不能提交「{{ activeStation.processName }}」。</p>
    <article
      v-if="field && activeStation && !activeStation.submitted && !delivered && previousReady(activeStation)"
      class="field-card"
      aria-label="本岗位加工要求"
    >
      <div class="field-card-head">
        <h2>本岗位加工要求</h2>
        <span>逐条核对</span>
      </div>
      <p class="info-context">逐条核对后，点击下方“确认交工”才会提交。</p>
      <p v-if="!activeLines.length">本岗位没有单独列出的要求，请核对本次整单数量与重量。</p>
      <label v-for="(line, index) in activeLines" :key="checkKey(activeStation.processName, index)" class="field-check">
        <input
          type="checkbox"
          :disabled="locked"
          :checked="Boolean(checkedRequirement[checkKey(activeStation.processName, index)])"
          :aria-label="`核对 ${index + 1} ${line}`"
          @change="toggleCheck(activeStation.processName, index, ($event.target as HTMLInputElement).checked)"
        />
        <span>{{ line }}</span>
      </label>
    </article>
    <article
      v-if="field && activeStation && !activeStation.submitted && !delivered"
      class="field-card"
      aria-label="报工确认"
    >
      <h2>{{ batchMode ? "本次分批交工" : "本次整单交工" }}</h2>
      <p>{{ activeStation.processName }} · {{ stationView?.orderCode }}</p>
      <template v-if="batchMode">
        <dl class="detail-grid"><dt>整单数量</dt><dd>{{ activeStation.totalQuantity }} {{ stationScan.context?.order.quantityUnit }}</dd><dt>本工序已交</dt><dd>{{ activeStation.deliveredQuantity }}</dd><dt>本次可交</dt><dd>{{ activeStation.availableQuantity }}</dd></dl>
        <label class="batch-field">本次交工数量<input v-model="batchQuantity" aria-label="本次交工数量" inputmode="decimal" :disabled="locked" placeholder="填写本批实际交出的数量" /></label>
        <label v-if="activeStation.finalProcess" class="batch-field">末道整单实称重量（交齐时填，可稍后补记）<input v-model="finalWeight" aria-label="末道总重量" inputmode="decimal" :disabled="locked" /></label>
        <p class="info-context">每批只记数量，不按比例估算重量。末道数量交齐并登记总重量后进入质检；账面差额沿用损耗处理。</p>
      </template>
      <p v-else class="info-context">按这张单已有数量和重量一次交完本工序。无需先保存 100% 进度。</p>
      <dl v-if="!batchMode" class="detail-grid">
        <dt>单据数量</dt>
        <dd>{{ amountText(stationScan.context?.order.quantity, stationScan.context?.order.quantityUnit) }}</dd>
        <dt>单据重量</dt>
        <dd>{{ amountText(stationScan.context?.order.weight, stationScan.context?.order.weightUnit) }}</dd>
      </dl>
      <p v-if="!batchMode && !quantityReady" class="operation-feedback danger" role="alert">数量或重量尚未读取完整，请重新打开本单核对。</p>
    </article>
    <details v-if="activeStation?.batches?.length" class="section-block"><summary>查看本工序交工批次（{{ activeStation.batches.length }}）</summary><p v-for="batch in activeStation.batches" :key="batch.batchCode">{{ batch.createdAt }} · {{ batch.operator }} · 本批 {{ batch.quantity }}<template v-if="batch.finalWeight != null"> · 末道总重量 {{ batch.finalWeight }}</template></p></details>
    <details v-if="field && progressTarget" class="field-note">
      <summary>过程说明</summary>
      <ProgressNoteForm
        stage="PRODUCTION"
        :target-type="progressTarget.targetType"
        :target-code="progressTarget.targetCode"
        :mother-order-code="progressTarget.motherOrderCode"
        :process-options="processOptions"
        @saved="rememberProgress"
      />
    </details>
    <ProgressNoteForm
      v-else-if="progressTarget"
      stage="PRODUCTION"
      :target-type="progressTarget.targetType"
      :target-code="progressTarget.targetCode"
      :mother-order-code="progressTarget.motherOrderCode"
      :process-options="processOptions"
      @saved="rememberProgress"
    />
    <template v-if="!field">
    <article v-for="station in stationView?.stations ?? []" :key="station.processName" class="section-block">
      <h2>{{ station.processName }}</h2>
      <dl class="detail-grid">
        <dt>单号</dt>
        <dd>{{ stationView?.orderCode }}</dd>
        <dt>客户</dt>
        <dd>{{ stationView?.customerName || '未记录' }}</dd>
        <dt>{{ station.processName }}要求</dt>
        <dd>{{ station.requirement || '未记录' }}</dd>
        <dt>期限</dt>
        <dd>{{ station.dueDate || '未记录' }}</dd>
        <dt>上一道是否已交</dt>
        <dd>{{ previousText(station) }}</dd>
      </dl>
      <button
        v-if="!station.submitted"
        class="primary-button"
        type="button"
        :disabled="locked || !stationCode || !previousReady(station)"
        @click="deliver(station)"
      >
        {{ submittingProcess === station.processName ? '正在提交' : `提交${station.processName}` }}
      </button>
      <p v-else class="operation-feedback">{{ station.processName }}已交</p>
    </article>
    </template>
    <details v-if="stationCode" class="section-block" :open="!field">
      <summary>照片与视频（可选）</summary>
      <p v-if="delivered">业务已保存。证据上传或关联失败时只重试文件，不会再次交付。</p>
      <p v-else>可以先选择文件。本次交工成功后才关联到对应批次或台账，待上传文件不代表已经报工。</p>
      <nav v-if="evidenceDrafts.length > 1" aria-label="批次证据草稿"><button v-for="draft in evidenceDrafts" :key="draft.key" class="secondary-button" type="button" :aria-pressed="evidenceDraftKey === draft.key" @click="evidenceDraftKey = draft.key">{{ draft.result ? '已存批次 ' + (draft.key + 1) : '当前批次' }}</button></nav>
      <EvidenceCapture v-for="draft in evidenceDrafts" :key="draft.key" v-show="evidenceDraftKey === draft.key"
        :target-type="evidenceTargetType"
        :target-code="stationCode"
        stage="PRODUCTION"
        scene="PROCESS"
        :operation-type="evidenceType(draft.result)"
        :operation-code="draft.result?.batchCode || draft.result?.ledgerCode || ''"
        :process-name="draft.result?.processName || ''"
      />
    </details>
    <div v-if="field && activeStation && !activeStation.submitted && !delivered" class="field-dock">
      <span v-if="previousReady(activeStation) && activeLines.length && checkedCount < activeLines.length" class="info-context">已核对 {{ checkedCount }} / {{ activeLines.length }} 项要求</span>
      <button class="primary-button" type="button" :aria-label="`确认${activeStation.processName}交工`" :disabled="!canConfirmReview" @click="confirmAndDeliver">{{ submittingProcess === activeStation.processName ? '正在提交…' : batchMode && batchQuantity === '0' ? '登记总重量并完成交工' : `确认${activeStation.processName}${batchMode ? '本批' : ''}交工` }}</button>
    </div>
  </section>
</template>

<style scoped>
.batch-field { display: grid; gap: 6px; margin-top: 12px; }
.batch-field input { min-height: 44px; width: 100%; box-sizing: border-box; }
.production-field > button { width: auto; justify-self: end; }
.field-process-list button { display: grid; gap: 4px; }
.field-process-list small { font-size: 11px; font-weight: 400; }
.field-dock { display: flex; gap: 10px; align-items: center; justify-content: space-between; }
.field-dock .primary-button { flex: 1; min-height: 48px; }
@media (min-width: 901px) {
  .production-field { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; align-items: start; }
  .production-field > * { grid-column: 1 / -1; }
  .production-field > .field-card[aria-label="本岗位加工要求"],
  .production-field > .field-card[aria-label="报工确认"] { grid-column: auto; }
}
</style>
