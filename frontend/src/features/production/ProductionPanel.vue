<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ProgressNoteForm from '../scan/ProgressNoteForm.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { appendProgressRecord, progressTargetFromScan } from '../scan/progressTarget';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import type { ScanContextResponse, ScanProgressRecord, ScanRelatedTarget } from '../../services/scanContextApi';
import {
  lookupProcessStation,
  submitProcessStation,
  type ProcessStationCard,
  type ProcessStationReport,
  type ProcessStationView,
} from '../../services/processStationApi';

defineProps<{
  title: string;
  intro: string;
}>();

const scanText = ref('');
const hint = ref('');
const formError = ref('');
const stationView = ref<ProcessStationView | null>(null);
const stationCode = ref('');
const stationBusy = ref(false);
const submittingProcess = ref('');
const delivered = ref<ProcessStationReport | null>(null);
const requestIds = ref<Record<string, string>>({});
const stationScan = reactive(useScanContext());

const progressTarget = computed(() => progressTargetFromScan(stationScan.context));
const processOptions = computed(() => (stationView.value?.stations ?? []).map((item) => item.processName));
const evidenceTargetType = computed(() => (
  stationScan.identity?.objectType === 'MOTHER_ORDER' ? 'MOTHER_ORDER' : 'WORK_ORDER'
));
const evidenceOperationType = computed(() => {
  if (!delivered.value) return '';
  return delivered.value.movedToSemiFinished ? 'production_report' : 'process_station_report';
});

function rememberProgress(record: ScanProgressRecord) {
  appendProgressRecord(stationScan.context, record);
}

function previousText(station: ProcessStationCard) {
  if (!station.previousProcessName) return '无上一道';
  return station.previousSubmitted ? '已交' : '未交';
}

function clearStationTarget() {
  const had = stationScan.context !== null || stationScan.loading || stationView.value !== null || stationCode.value !== '';
  stationScan.invalidate();
  stationView.value = null;
  stationCode.value = '';
  stationBusy.value = false;
  delivered.value = null;
  requestIds.value = {};
  formError.value = '';
  if (had) hint.value = '贴纸已变化，请重新识别';
}

watch(scanText, () => clearStationTarget(), { flush: 'sync' });

function ensureRequestId(code: string, processName: string) {
  const fingerprint = `${code}|${processName.trim()}`;
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
    stationCode.value = target.code;
    stationView.value = loaded;
    if (loaded.message) hint.value = loaded.message;
  } catch (error) {
    if (stationScan.revision !== seen) return;
    stationCode.value = '';
    stationView.value = null;
    formError.value = error instanceof Error ? error.message : '贴纸识别失败';
  } finally {
    if (stationScan.revision === seen) stationBusy.value = false;
  }
}

async function recognizeStation(draft: ScanDraft) {
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

async function chooseStationTarget(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
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
  delivered.value = result;
  try {
    stationView.value = await lookupProcessStation(code);
  } catch {
    if (stationScan.revision !== seen) return;
    hint.value = '交付已保存，页面刷新失败。请重新打开本单，不会再次交付。';
    return;
  }
  if (stationScan.revision !== seen) {
    stationView.value = null;
    stationCode.value = '';
    delivered.value = null;
    return;
  }
  hint.value = `${processName}已交`;
}

async function deliver(station: ProcessStationCard) {
  const code = stationCode.value;
  const seen = stationScan.revision;
  formError.value = '';
  if (!code || station.submitted || stationScan.loading || submittingProcess.value) return;
  const currentRequestId = ensureRequestId(code, station.processName);
  submittingProcess.value = station.processName;
  try {
    const result = await submitProcessStation(code, station.processName, currentRequestId);
    await applyResult(result, code, station.processName, seen);
  } catch (error) {
    if (stationScan.revision !== seen) return;
    const message = error instanceof Error ? error.message : '报工失败';
    if (message === '后端接口不可用') {
      try {
        const replayed = await submitProcessStation(code, station.processName, currentRequestId);
        await applyResult(replayed, code, station.processName, seen);
        return;
      } catch (again) {
        if (stationScan.revision !== seen) return;
        formError.value = again instanceof Error ? again.message : '报工失败';
        hint.value = '请用原确认重试，不会另开一次交付';
        return;
      }
    }
    formError.value = message;
  } finally {
    if (submittingProcess.value === station.processName) submittingProcess.value = '';
  }
}
</script>

<template>
  <section class="section-block">
    <div class="section-heading">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ intro }}</p>
      </div>
    </div>
    <ScanInput
      v-model="scanText"
      field-text="贴纸二维码"
      input-aria-label="贴纸二维码"
      placeholder="JG|work_order|子单号 或 JG|mother|母单号"
      submit-label="识别贴纸"
      submit-aria-label="识别贴纸"
      photo-aria-label="拍贴纸"
      :busy="stationScan.loading || stationBusy"
      @draft="clearStationTarget"
      @recognize="recognizeStation"
    />
    <p v-if="hint" class="operation-feedback">{{ hint }}</p>
    <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
    <OrderContextCard :context="stationScan.context" @select="chooseStationTarget" />
    <ProgressNoteForm
      v-if="progressTarget"
      stage="PRODUCTION"
      :target-type="progressTarget.targetType"
      :target-code="progressTarget.targetCode"
      :mother-order-code="progressTarget.motherOrderCode"
      :process-options="processOptions"
      @saved="rememberProgress"
    />
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
        :disabled="submittingProcess !== '' || !stationCode"
        @click="deliver(station)"
      >
        {{ submittingProcess === station.processName ? '正在提交' : `提交${station.processName}` }}
      </button>
      <p v-else class="operation-feedback">{{ station.processName }}已交</p>
    </article>
    <section v-if="stationCode" class="section-block">
      <h2>工序证据</h2>
      <p v-if="delivered">业务已保存。证据上传或关联失败时只重试文件，不会再次交付。</p>
      <p v-else>可以先选择文件。正式交付成功后才关联到本次台账，不会把待上传文件当成已经报工。</p>
      <EvidenceCapture
        :target-type="evidenceTargetType"
        :target-code="stationCode"
        stage="PRODUCTION"
        scene="PROCESS"
        :operation-type="evidenceOperationType"
        :operation-code="delivered?.ledgerCode || ''"
        :process-name="delivered?.processName || ''"
      />
    </section>
  </section>
</template>
