<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import IntakePanel from '../intake/IntakePanel.vue';
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
  type ProcessStationView,
} from '../../services/processStationApi';

const jobMode = ref<'intake' | 'station'>('intake');
const stationScanText = ref('');
const stationHint = ref('');
const stationError = ref('');
const stationView = ref<ProcessStationView | null>(null);
const stationCode = ref('');
const stationBusy = ref(false);
const submittingProcess = ref('');
const stationScan = reactive(useScanContext());

const stationProgressTarget = computed(() => progressTargetFromScan(stationScan.context));
const stationProcessOptions = computed(() => (stationView.value?.stations ?? []).map((item) => item.processName));

function rememberStationProgress(record: ScanProgressRecord) {
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
  stationError.value = '';
  if (had) stationHint.value = '贴纸已变化，请重新识别';
}

watch(stationScanText, () => clearStationTarget(), { flush: 'sync' });
watch(jobMode, () => {
  clearStationTarget();
});

async function loadStation(response: ScanContextResponse, seen: number) {
  if (stationScan.revision !== seen) return;
  if (response.requiresSelection) {
    stationHint.value = '这张母单已经拆分，请选择子单';
    return;
  }
  const target = canonicalTarget(response);
  if (!target) {
    stationHint.value = '没有定位到可报工的单据';
    return;
  }
  if (target.objectType === 'EXPRESS_NO') {
    stationHint.value = '这是快递单号，请切到收货';
    return;
  }
  if (target.objectType !== 'WORK_ORDER' && target.objectType !== 'MOTHER_ORDER') {
    stationHint.value = '请扫描子单贴纸，或不拆母单的贴纸';
    return;
  }
  stationBusy.value = true;
  try {
    const loaded = await lookupProcessStation(target.code);
    if (stationScan.revision !== seen) return;
    stationCode.value = target.code;
    stationView.value = loaded;
    if (loaded.message) stationHint.value = loaded.message;
  } catch (error) {
    if (stationScan.revision !== seen) return;
    stationCode.value = '';
    stationView.value = null;
    stationError.value = error instanceof Error ? error.message : '贴纸识别失败';
  } finally {
    if (stationScan.revision === seen) stationBusy.value = false;
  }
}

async function recognizeStation(draft: ScanDraft) {
  stationError.value = '';
  stationHint.value = '';
  const content = draft.content.trim();
  if (!content) {
    stationScan.invalidate();
    stationHint.value = '请先扫子单贴纸，或不拆母单的贴纸';
    return;
  }
  const response = await stationScan.lookup({
    content,
    objectType: draft.objectType,
    intent: 'PRODUCTION',
  });
  if (!response) {
    if (stationScan.error) stationError.value = stationScan.error;
    return;
  }
  await loadStation(response, stationScan.revision);
}

async function chooseStationTarget(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
  stationError.value = '';
  stationHint.value = '';
  const response = await stationScan.lookup({
    content: related.code,
    objectType: related.objectType,
    intent: 'PRODUCTION',
  });
  if (!response) {
    if (stationScan.error) stationError.value = stationScan.error;
    return;
  }
  await loadStation(response, stationScan.revision);
}

async function submitStation(station: ProcessStationCard) {
  const code = stationCode.value;
  const seen = stationScan.revision;
  stationError.value = '';
  if (!code || station.submitted || stationScan.loading || submittingProcess.value) return;
  submittingProcess.value = station.processName;
  try {
    await submitProcessStation(code, station.processName);
    if (stationScan.revision !== seen || stationCode.value !== code) return;
    stationView.value = await lookupProcessStation(code);
    if (stationScan.revision !== seen) {
      stationView.value = null;
      stationCode.value = '';
      return;
    }
    stationHint.value = `${station.processName}已交`;
  } catch (error) {
    if (stationScan.revision !== seen) return;
    stationError.value = error instanceof Error ? error.message : '报工失败';
  } finally {
    if (submittingProcess.value === station.processName) submittingProcess.value = '';
  }
}
</script>

<template>
  <div class="page-stack intake-page">
    <div class="button-row">
      <button
        :class="jobMode === 'intake' ? 'primary-button' : 'secondary-button'"
        type="button"
        aria-label="收货"
        @click="jobMode = 'intake'"
      >
        收货
      </button>
      <button
        :class="jobMode === 'station' ? 'primary-button' : 'secondary-button'"
        type="button"
        aria-label="岗位报工"
        @click="jobMode = 'station'"
      >
        岗位报工
      </button>
    </div>

    <section v-if="jobMode === 'station'" class="section-block">
      <div class="section-heading">
        <div>
          <h2>岗位报工</h2>
          <p>扫子单贴纸。不拆的母单扫母单贴纸。页面只显示当前账号能做的工序。</p>
        </div>
      </div>
      <ScanInput
        v-model="stationScanText"
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
      <p v-if="stationHint" class="operation-feedback">{{ stationHint }}</p>
      <p v-if="stationError" class="operation-feedback danger">{{ stationError }}</p>
      <OrderContextCard :context="stationScan.context" @select="chooseStationTarget" />
      <ProgressNoteForm
        v-if="stationProgressTarget"
        stage="PRODUCTION"
        :target-type="stationProgressTarget.targetType"
        :target-code="stationProgressTarget.targetCode"
        :mother-order-code="stationProgressTarget.motherOrderCode"
        :process-options="stationProcessOptions"
        @saved="rememberStationProgress"
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
          @click="submitStation(station)"
        >
          {{ submittingProcess === station.processName ? '正在提交' : `提交${station.processName}` }}
        </button>
        <p v-else class="operation-feedback">{{ station.processName }}已交</p>
      </article>
    </section>

    <IntakePanel
      v-if="jobMode === 'intake'"
      title="手机收货"
      intro="扫快递面单上的单号。新单生成母单并写下加工要求和期限，然后补拆包证据。"
    />
  </div>
</template>
