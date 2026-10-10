<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { hasLiveCamera, openCameraStream, readBarcodeFromFile, readLiveBarcode } from '../scan/cameraBarcode';
import { parseScanCode } from '../scan/scanCode';
import { uploadCapture, type DocumentCapture } from '../../services/documentCaptureApi';
import {
  lookupMobileIntake,
  submitMobileIntake,
  type MobileIntakeResult,
} from '../../services/mobileIntakeApi';
import {
  lookupProcessStation,
  submitProcessStation,
  type ProcessStationCard,
  type ProcessStationView,
} from '../../services/processStationApi';

const scanText = ref('');
const scanHint = ref('');
const scanning = ref(false);
const recognizing = ref(false);
const courierNo = ref('');
const existing = ref<MobileIntakeResult | null>(null);
const customerName = ref('');
const processingRequirement = ref('');
const dueDate = ref('');
const goodsName = ref('');
const quantity = ref('1');
const weight = ref('');
const expressCompany = ref('顺丰');
const submitting = ref(false);
const formError = ref('');
const created = ref<MobileIntakeResult | null>(null);
const videoFile = ref<File | null>(null);
const uploading = ref(false);
const uploadError = ref('');
const captures = ref<DocumentCapture[]>([]);
const cameraRef = ref<HTMLVideoElement | null>(null);
const stillInputRef = ref<HTMLInputElement | null>(null);
let scanGeneration = 0;

onBeforeUnmount(() => {
  scanGeneration += 1;
  stopCamera();
});
const jobMode = ref<'intake' | 'station'>('intake');
const stationScanText = ref('');
const stationHint = ref('');
const stationError = ref('');
const stationView = ref<ProcessStationView | null>(null);
const stationBusy = ref(false);
const submittingProcess = ref('');

const activeOrder = () => created.value ?? existing.value;

function previousText(station: ProcessStationCard) {
  if (!station.previousProcessName) {
    return '无上一道';
  }
  return station.previousSubmitted ? '已交' : '未交';
}

async function recognize() {
  formError.value = '';
  scanHint.value = '';
  existing.value = null;
  created.value = null;
  captures.value = [];
  const raw = scanText.value.trim();
  if (!raw) {
    scanHint.value = '请先扫快递单号或手输';
    return;
  }
  let parsed;
  try {
    parsed = parseScanCode(raw);
  } catch (error) {
    scanHint.value = error instanceof Error ? error.message : '未识别的二维码内容';
    return;
  }
  if (parsed.objectType === 'MOTHER_ORDER') {
    courierNo.value = '';
    created.value = {
      alreadyExists: true,
      motherOrderCode: parsed.code,
      courierNo: '',
      customerName: '',
      processingRequirement: '',
      dueDate: '',
      goodsName: '',
      quantity: 0,
      weight: 0,
      ledgerCode: '',
      qrContent: raw,
    };
    scanHint.value = `已识别母单 ${parsed.code}，可以补传拆包视频`;
    return;
  }
  if (parsed.objectType !== 'EXPRESS_NO') {
    scanHint.value = '请扫描快递面单，或系统母单标签';
    return;
  }
  courierNo.value = parsed.code;
  recognizing.value = true;
  try {
    const lookup = await lookupMobileIntake(parsed.code);
    if (lookup.found) {
      existing.value = {
        alreadyExists: true,
        motherOrderCode: lookup.motherOrderCode,
        courierNo: lookup.courierNo,
        customerName: lookup.customerName,
        processingRequirement: lookup.processingRequirement,
        dueDate: lookup.dueDate,
        goodsName: lookup.goodsName,
        quantity: 0,
        weight: 0,
        ledgerCode: '',
        qrContent: `JG|mother|${lookup.motherOrderCode}`,
      };
      scanHint.value = `快递单号已对应母单 ${lookup.motherOrderCode}`;
    } else {
      scanHint.value = '这是新的快递，请填写客户、加工要求和期限';
    }
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '快递单号查询失败';
  } finally {
    recognizing.value = false;
  }
}

function stopCamera() {
  const video = cameraRef.value;
  const stream = video?.srcObject;
  if (stream instanceof MediaStream) {
    stream.getTracks().forEach((track) => track.stop());
  }
  if (video) {
    video.srcObject = null;
  }
}

function setScanHint(message: string) {
  if (jobMode.value === 'station') {
    stationHint.value = message;
    return;
  }
  scanHint.value = message;
}

function openStillCapture() {
  stillInputRef.value?.click();
}

async function applyScannedText(raw: string) {
  if (jobMode.value === 'station') {
    stationScanText.value = raw;
    await recognizeStation();
    return;
  }
  scanText.value = raw;
  await recognize();
}

async function scanWithCamera() {
  scanHint.value = '';
  if (jobMode.value === 'station') {
    stationHint.value = '';
  }
  if (!hasLiveCamera()) {
    openStillCapture();
    return;
  }
  const generation = scanGeneration + 1;
  scanGeneration = generation;
  scanning.value = true;
  let stream: MediaStream | null = null;
  try {
    stream = await openCameraStream();
    if (generation !== scanGeneration) {
      return;
    }
    const video = cameraRef.value;
    if (!video) {
      setScanHint('摄像头预览没有准备好，请改用拍面单');
      return;
    }
    video.srcObject = stream;
    await video.play();
    const rawValue = await readLiveBarcode(video, 20000, () => generation !== scanGeneration);
    if (generation !== scanGeneration) {
      return;
    }
    if (rawValue) {
      await applyScannedText(rawValue);
      return;
    }
    setScanHint(jobMode.value === 'station' ? '没有读到贴纸，请改拍贴纸或手输' : '没有读到快递单号，请改拍面单或手输');
  } catch {
    if (generation !== scanGeneration) {
      return;
    }
    setScanHint(jobMode.value === 'station' ? '无法打开摄像头，请改拍贴纸或手输' : '无法打开摄像头，请改拍面单或手输');
  } finally {
    stream?.getTracks().forEach((track) => track.stop());
    if (cameraRef.value) {
      cameraRef.value.srcObject = null;
    }
    scanning.value = false;
  }
}

async function onStillPicked(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  scanning.value = true;
  setScanHint('');
  try {
    const rawValue = await readBarcodeFromFile(file);
    if (!rawValue) {
      setScanHint(jobMode.value === 'station' ? '照片里没有读到贴纸，请重拍或手输' : '照片里没有读到快递单号，请重拍或手输');
      return;
    }
    await applyScannedText(rawValue);
  } catch {
    setScanHint('没能识别这张照片，请重拍或手输');
  } finally {
    scanning.value = false;
  }
}

async function createMotherOrder() {
  formError.value = '';
  if (!courierNo.value) {
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
    created.value = await submitMobileIntake({
      courierNo: courierNo.value,
      customerName: customerName.value.trim(),
      processingRequirement: processingRequirement.value.trim(),
      dueDate: dueDate.value,
      goodsName: goodsName.value.trim(),
      quantity: Number(quantity.value),
      weight: weight.value,
      expressCompany: expressCompany.value.trim(),
    });
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '生成母单失败';
  } finally {
    submitting.value = false;
  }
}

function onVideoPicked(event: Event) {
  const input = event.target as HTMLInputElement;
  videoFile.value = input.files?.[0] ?? null;
  uploadError.value = '';
}

async function uploadVideo() {
  const order = activeOrder();
  uploadError.value = '';
  if (!order?.motherOrderCode || !videoFile.value) {
    uploadError.value = '请先生成母单，再选择拆包视频';
    return;
  }
  uploading.value = true;
  try {
    const uploaded = await uploadCapture({
      objectType: 'mother',
      businessCode: order.motherOrderCode,
      scene: 'UNPACK',
      file: videoFile.value,
    });
    captures.value = [
      ...captures.value,
      {
        code: uploaded.code,
        businessCode: order.motherOrderCode,
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
    uploadError.value = error instanceof Error ? error.message : '拆包视频上传失败';
  } finally {
    uploading.value = false;
  }
}

async function recognizeStation() {
  stationError.value = '';
  stationHint.value = '';
  stationView.value = null;
  const raw = stationScanText.value.trim();
  if (!raw) {
    stationHint.value = '请先扫子单贴纸，或不拆母单的贴纸';
    return;
  }
  let parsed;
  try {
    parsed = parseScanCode(raw);
  } catch (error) {
    stationHint.value = error instanceof Error ? error.message : '未识别的二维码内容';
    return;
  }
  if (parsed.objectType === 'EXPRESS_NO') {
    stationHint.value = '这是快递单号，请切到收货';
    return;
  }
  if (parsed.objectType !== 'WORK_ORDER' && parsed.objectType !== 'MOTHER_ORDER') {
    stationHint.value = '请扫描子单贴纸，或不拆母单的贴纸';
    return;
  }
  stationBusy.value = true;
  try {
    stationView.value = await lookupProcessStation(raw);
    if (stationView.value.message) {
      stationHint.value = stationView.value.message;
    }
  } catch (error) {
    stationError.value = error instanceof Error ? error.message : '贴纸识别失败';
  } finally {
    stationBusy.value = false;
  }
}

async function submitStation(station: ProcessStationCard) {
  stationError.value = '';
  if (!stationScanText.value.trim() || station.submitted) {
    return;
  }
  submittingProcess.value = station.processName;
  try {
    await submitProcessStation(stationScanText.value.trim(), station.processName);
    stationView.value = await lookupProcessStation(stationScanText.value.trim());
    stationHint.value = `${station.processName}已交`;
  } catch (error) {
    stationError.value = error instanceof Error ? error.message : '报工失败';
  } finally {
    submittingProcess.value = '';
  }
}
</script>

<template>
  <div class="page-stack intake-page">
    <input
      ref="stillInputRef"
      class="still-capture"
      type="file"
      accept="image/*"
      capture="environment"
      aria-label="条码照片"
      @change="onStillPicked"
    />
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
      <div class="intake-form">
        <label class="field">
          <span>贴纸二维码</span>
          <input v-model="stationScanText" type="text" placeholder="JG|work_order|子单号 或 JG|mother|母单号" aria-label="贴纸二维码" />
        </label>
        <div class="button-row">
          <button class="primary-button" type="button" :disabled="stationBusy" aria-label="识别贴纸" @click="recognizeStation">
            {{ stationBusy ? '正在识别' : '识别贴纸' }}
          </button>
          <button class="secondary-button" type="button" :disabled="scanning" aria-label="打开摄像头" @click="scanWithCamera">
            {{ scanning ? '正在扫码' : '打开摄像头' }}
          </button>
          <button class="secondary-button" type="button" :disabled="scanning" aria-label="拍贴纸" @click="openStillCapture">
            拍贴纸
          </button>
        </div>
        <video v-show="scanning && jobMode === 'station'" ref="cameraRef" class="intake-camera" muted playsinline autoplay />
        <p v-if="stationHint" class="operation-feedback">{{ stationHint }}</p>
        <p v-if="stationError" class="operation-feedback danger">{{ stationError }}</p>
      </div>
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
          :disabled="submittingProcess !== ''"
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
      <div class="intake-form">
        <label class="field">
          <span>快递单号或二维码内容</span>
          <input v-model="scanText" type="text" placeholder="手输运单号，或贴上扫到的内容" aria-label="快递单号" />
        </label>
        <div class="button-row">
          <button class="primary-button" type="button" :disabled="recognizing" @click="recognize">
            {{ recognizing ? '正在识别' : '识别单号' }}
          </button>
          <button class="secondary-button" type="button" :disabled="scanning" aria-label="打开摄像头" @click="scanWithCamera">
            {{ scanning ? '正在扫码' : '打开摄像头' }}
          </button>
          <button class="secondary-button" type="button" :disabled="scanning" aria-label="拍面单" @click="openStillCapture">
            拍面单
          </button>
        </div>
        <video v-show="scanning && jobMode === 'intake'" ref="cameraRef" class="intake-camera" muted playsinline autoplay />
        <p v-if="scanHint" class="operation-feedback">{{ scanHint }}</p>
        <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
      </div>
    </section>

    <section v-if="jobMode === 'intake' && existing && !created" class="section-block">
      <h2>已有母单 {{ existing.motherOrderCode }}</h2>
      <dl class="detail-grid">
        <dt>客户</dt>
        <dd>{{ existing.customerName || '未记录' }}</dd>
        <dt>加工要求</dt>
        <dd>{{ existing.processingRequirement || '未记录' }}</dd>
        <dt>期限</dt>
        <dd>{{ existing.dueDate || '未记录' }}</dd>
        <dt>货品</dt>
        <dd>{{ existing.goodsName || '未记录' }}</dd>
      </dl>
    </section>

    <section v-else-if="jobMode === 'intake' && courierNo && !created" class="section-block">
      <h2>新建母单</h2>
      <p>快递单号 {{ courierNo }}</p>
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
        <button class="primary-button" type="button" :disabled="submitting" @click="createMotherOrder">
          {{ submitting ? '正在生成' : '生成母单' }}
        </button>
      </div>
    </section>

    <section v-if="jobMode === 'intake' && activeOrder()" class="section-block">
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
        <button class="primary-button" type="button" :disabled="uploading || !videoFile" @click="uploadVideo">
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
