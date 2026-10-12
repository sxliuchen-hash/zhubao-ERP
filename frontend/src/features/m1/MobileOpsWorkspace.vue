<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ProgressNoteForm from '../scan/ProgressNoteForm.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { appendProgressRecord, progressTargetFromScan } from '../scan/progressTarget';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import type { ScanContextResponse, ScanProgressRecord, ScanRelatedTarget } from '../../services/scanContextApi';
import { uploadCapture, type DocumentCapture } from '../../services/documentCaptureApi';
import { submitMobileIntake, type MobileIntakeResult } from '../../services/mobileIntakeApi';
import {
  lookupProcessStation,
  submitProcessStation,
  type ProcessStationCard,
  type ProcessStationView,
} from '../../services/processStationApi';

const scanText = ref('');
const scanHint = ref('');
const formError = ref('');
const customerName = ref('');
const processingRequirement = ref('');
const dueDate = ref('');
const goodsName = ref('');
const quantity = ref('1');
const weight = ref('');
const expressCompany = ref('顺丰');
const submitting = ref(false);
const created = ref<MobileIntakeResult | null>(null);
const videoFile = ref<File | null>(null);
const uploading = ref(false);
const uploadError = ref('');
const captures = ref<DocumentCapture[]>([]);
const intakeScan = reactive(useScanContext());

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

const newExpressCode = computed(() => {
  const target = intakeScan.identity;
  if (!target || target.objectType !== 'EXPRESS_NO' || target.motherOrderCode) return '';
  const action = intakeScan.context?.actions.find((item) => item.code === 'INTAKE');
  if (action && !action.allowed) return '';
  return target.code ?? '';
});

const intakeMotherCode = computed(() => {
  if (created.value?.motherOrderCode) return created.value.motherOrderCode;
  const target = intakeScan.identity;
  if (!target) return '';
  if (target.objectType === 'MOTHER_ORDER') return target.code ?? '';
  if (target.objectType === 'EXPRESS_NO') return target.motherOrderCode ?? '';
  return '';
});

function previousText(station: ProcessStationCard) {
  if (!station.previousProcessName) return '无上一道';
  return station.previousSubmitted ? '已交' : '未交';
}

function clearIntakeTarget() {
  const had = intakeScan.context !== null || intakeScan.loading || created.value !== null;
  intakeScan.invalidate();
  created.value = null;
  captures.value = [];
  formError.value = '';
  if (had) scanHint.value = '单号已变化，请重新识别';
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

watch(scanText, () => clearIntakeTarget(), { flush: 'sync' });
watch(stationScanText, () => clearStationTarget(), { flush: 'sync' });
watch(jobMode, () => {
  clearIntakeTarget();
  clearStationTarget();
});

function applyIntake(response: ScanContextResponse | null) {
  if (!response) {
    if (intakeScan.error) formError.value = intakeScan.error;
    return;
  }
  if (response.requiresSelection) {
    scanHint.value = '请选择要打开的单据';
    return;
  }
  const target = canonicalTarget(response);
  if (!target) {
    scanHint.value = '没有定位到单据';
    return;
  }
  if (target.objectType === 'EXPRESS_NO' && !target.motherOrderCode) {
    scanHint.value = '这是新的快递，请填写客户、加工要求和期限';
    return;
  }
  if (target.objectType === 'MOTHER_ORDER' || target.motherOrderCode) {
    scanHint.value = target.objectType === 'EXPRESS_NO'
      ? `快递单号已对应母单 ${target.motherOrderCode}`
      : `已识别母单 ${target.code}，可以补传拆包视频`;
    return;
  }
  scanHint.value = '请扫描快递面单，或系统母单标签';
}

async function recognizeIntake(draft: ScanDraft) {
  formError.value = '';
  scanHint.value = '';
  const content = draft.content.trim();
  if (!content) {
    intakeScan.invalidate();
    scanHint.value = '请先扫快递单号或手输';
    return;
  }
  const response = await intakeScan.lookup({
    content,
    objectType: draft.objectType,
    intent: 'INTAKE',
  });
  applyIntake(response);
}

async function chooseIntakeTarget(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
  formError.value = '';
  scanHint.value = '';
  const response = await intakeScan.lookup({
    content: related.code,
    objectType: related.objectType,
    intent: 'INTAKE',
  });
  applyIntake(response);
}

async function createMotherOrder() {
  formError.value = '';
  const courier = newExpressCode.value;
  const seen = intakeScan.revision;
  if (!courier || intakeScan.loading) {
    formError.value = '请先识别快递单号';
    return;
  }
  if (!customerName.value.trim() || !processingRequirement.value.trim() || !dueDate.value.trim()) {
    formError.value = '客户、加工要求和完成期限不能为空';
    return;
  }
  if (!goodsName.value.trim() || Number(quantity.value) <= 0 || Number(weight.value) <= 0) {
    formError.value = '请填写货品名称、数量和重量';
    return;
  }
  submitting.value = true;
  try {
    const result = await submitMobileIntake({
      courierNo: courier,
      customerName: customerName.value.trim(),
      processingRequirement: processingRequirement.value.trim(),
      dueDate: dueDate.value,
      goodsName: goodsName.value.trim(),
      quantity: Number(quantity.value),
      weight: weight.value,
      expressCompany: expressCompany.value.trim(),
    });
    if (intakeScan.revision !== seen) return;
    created.value = result;
  } catch (error) {
    if (intakeScan.revision !== seen) return;
    formError.value = error instanceof Error ? error.message : '生成母单失败';
  } finally {
    if (intakeScan.revision === seen) submitting.value = false;
  }
}

function onVideoPicked(event: Event) {
  const input = event.target as HTMLInputElement;
  videoFile.value = input.files?.[0] ?? null;
  uploadError.value = '';
}

async function uploadVideo() {
  const motherCode = intakeMotherCode.value;
  const seen = intakeScan.revision;
  uploadError.value = '';
  if (!motherCode || !videoFile.value || intakeScan.loading) {
    uploadError.value = '请先生成母单，再选择拆包视频';
    return;
  }
  uploading.value = true;
  try {
    const uploaded = await uploadCapture({
      objectType: 'mother',
      businessCode: motherCode,
      scene: 'UNPACK',
      file: videoFile.value,
    });
    if (intakeScan.revision !== seen) return;
    captures.value = [
      ...captures.value,
      {
        code: uploaded.code,
        businessCode: motherCode,
        scene: '拆包',
        fileName: uploaded.fileName,
        mediaType: uploaded.mediaType,
        checksum: uploaded.checksum,
        uploadedAt: uploaded.uploadedAt,
        owner: '',
        previewUrl: uploaded.previewUrl,
      },
    ];
    videoFile.value = null;
  } catch (error) {
    if (intakeScan.revision !== seen) return;
    uploadError.value = error instanceof Error ? error.message : '拆包视频上传失败';
  } finally {
    if (intakeScan.revision === seen) uploading.value = false;
  }
}

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

    <section v-if="jobMode === 'intake'" class="section-block">
      <div class="section-heading">
        <div>
          <h2>手机收货</h2>
          <p>扫快递面单上的单号。新单生成母单并写下加工要求和期限，然后录拆包视频。</p>
        </div>
      </div>
      <ScanInput
        v-model="scanText"
        field-text="快递单号或二维码内容"
        input-aria-label="快递单号"
        placeholder="手输运单号，或贴上扫到的内容"
        submit-label="识别单号"
        submit-aria-label="识别单号"
        photo-aria-label="拍面单"
        :busy="intakeScan.loading"
        @draft="clearIntakeTarget"
        @recognize="recognizeIntake"
      />
      <p v-if="scanHint" class="operation-feedback">{{ scanHint }}</p>
      <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
      <OrderContextCard :context="intakeScan.context" @select="chooseIntakeTarget" />
    </section>

    <section v-if="jobMode === 'intake' && newExpressCode && !created" class="section-block">
      <h2>新建母单</h2>
      <p>快递单号 {{ newExpressCode }}</p>
      <div class="intake-form">
        <label class="field">
          <span>客户</span>
          <input v-model="customerName" type="text" aria-label="客户" />
        </label>
        <label class="field">
          <span>加工要求</span>
          <textarea v-model="processingRequirement" aria-label="加工要求" />
        </label>
        <label class="field">
          <span>完成期限</span>
          <input v-model="dueDate" type="date" aria-label="完成期限" />
        </label>
        <label class="field">
          <span>快递公司</span>
          <input v-model="expressCompany" type="text" aria-label="快递公司" />
        </label>
        <label class="field">
          <span>货品名称</span>
          <input v-model="goodsName" type="text" aria-label="货品名称" />
        </label>
        <label class="field">
          <span>数量</span>
          <input v-model="quantity" type="number" min="1" step="1" aria-label="数量" />
        </label>
        <label class="field">
          <span>重量（克）</span>
          <input v-model="weight" type="number" min="0" step="0.001" aria-label="重量" />
        </label>
        <button class="primary-button" type="button" :disabled="submitting || intakeScan.loading" @click="createMotherOrder">
          {{ submitting ? '正在生成' : '生成母单' }}
        </button>
      </div>
    </section>

    <section v-if="jobMode === 'intake' && intakeMotherCode" class="section-block">
      <h2>拆包视频</h2>
      <p v-if="created">
        母单 {{ created.motherOrderCode }}
        <template v-if="created.qrContent"> / {{ created.qrContent }}</template>
      </p>
      <div class="intake-form">
        <label class="field">
          <span>选择或录一段短视频</span>
          <input type="file" accept="video/mp4,video/webm" capture="environment" aria-label="拆包视频" @change="onVideoPicked" />
        </label>
        <button class="primary-button" type="button" :disabled="uploading || !videoFile || intakeScan.loading" @click="uploadVideo">
          {{ uploading ? '正在上传' : '上传拆包视频' }}
        </button>
        <p v-if="uploadError" class="operation-feedback danger">{{ uploadError }}</p>
        <ul v-if="captures.length" class="draft-list">
          <li v-for="item in captures" :key="item.code">{{ item.fileName }}</li>
        </ul>
      </div>
    </section>
  </div>
</template>
