<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { hasLiveCamera } from './cameraBarcode';
import { bindMobileScanTarget, unbindMobileScanTarget } from './mobileScanBridge';
import type { ScanDraft } from './scanDraft';
import { useScanner } from './useScanner';
import { scanObjectTypeOptions, type ScanObjectType } from '../../services/scanContextApi';

const props = withDefaults(defineProps<{
  modelValue: string;
  fieldText: string;
  inputAriaLabel: string;
  placeholder?: string;
  submitLabel: string;
  submitAriaLabel?: string;
  cameraAriaLabel?: string;
  photoAriaLabel?: string;
  busy?: boolean;
  bindMobileScan?: boolean;
}>(), {
  placeholder: '',
  submitAriaLabel: '',
  cameraAriaLabel: '打开摄像头',
  photoAriaLabel: '拍照识别',
  busy: false,
  bindMobileScan: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  draft: [];
  recognize: [draft: ScanDraft];
}>();

const scanner = useScanner();
const scanning = scanner.scanning;
const cameraActive = scanner.cameraActive;
const scannerHint = scanner.hint;
const videoEl = scanner.videoRef;
const photoRef = ref<HTMLInputElement | null>(null);
const albumRef = ref<HTMLInputElement | null>(null);
const manualRef = ref<HTMLInputElement | null>(null);
const objectType = ref<ScanObjectType | ''>('');
const guideType = ref<'qr' | 'barcode'>('qr');
const submitAria = computed(() => props.submitAriaLabel || props.submitLabel);
let acceptSubmit = true;

function draftOf(content: string): ScanDraft {
  const draft: ScanDraft = { content: content.trim() };
  if (objectType.value) draft.objectType = objectType.value;
  return draft;
}

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
}

function onType(event: Event) {
  objectType.value = (event.target as HTMLSelectElement).value as ScanObjectType | '';
  emit('draft');
}

function submitManual(event?: Event) {
  event?.preventDefault();
  if (!acceptSubmit || props.busy || scanning.value) return;
  if (!props.modelValue.trim()) {
    scannerHint.value = '请先扫码，或填写单号后再识别';
    manualRef.value?.focus();
    return;
  }
  acceptSubmit = false;
  emit('recognize', draftOf(props.modelValue));
  queueMicrotask(() => {
    acceptSubmit = true;
  });
}

function openPhoto() {
  if (props.busy || scanning.value) return;
  photoRef.value?.click();
}

function stopCamera() {
  scanner.stop();
  scannerHint.value = '已停止扫码，可以重新扫码、选图或手输单号';
}

function publish(raw: string) {
  emit('update:modelValue', raw);
  emit('recognize', draftOf(raw));
}

async function onCamera() {
  if (props.busy || scanning.value) return;
  if (!hasLiveCamera()) {
    scannerHint.value = '当前浏览器无法实时扫码，请拍照识别，也可以选图或手输单号';
    photoRef.value?.click();
    return;
  }
  const reading = scanner.scanCamera();
  await nextTick();
  const panel = videoEl.value?.closest('.scan-camera-panel');
  if (panel && typeof panel.scrollIntoView === 'function') {
    panel.scrollIntoView({ block: 'center' });
  }
  const raw = await reading;
  if (raw) publish(raw);
}

onMounted(() => {
  if (props.bindMobileScan) bindMobileScanTarget(onCamera);
});

onBeforeUnmount(() => {
  unbindMobileScanTarget(onCamera);
});

async function onPhoto(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || props.busy || scanning.value) return;
  const raw = await scanner.scanPhoto(file);
  if (raw) publish(raw);
}
</script>

<template>
  <form class="scan-entry intake-form" :class="{ 'is-scanning': cameraActive }" @submit.prevent="submitManual">
    <div class="scan-start">
      <button
        class="primary-button scan-camera-button"
        type="button"
        :disabled="busy || scanning"
        :aria-label="cameraAriaLabel"
        @click="onCamera"
      >
        {{ cameraActive ? '正在扫码…' : '相机扫码' }}
      </button>
      <p class="scan-help">对准快递面单或单据标签，识别后自动继续</p>
    </div>
    <div v-show="cameraActive" class="scan-camera-panel" role="region" aria-label="扫码取景">
      <div class="scan-guide-switch" role="group" aria-label="取景框类型">
        <button type="button" :aria-pressed="guideType === 'qr'" @click="guideType = 'qr'">二维码框</button>
        <button type="button" :aria-pressed="guideType === 'barcode'" @click="guideType = 'barcode'">条形码框</button>
      </div>
      <div class="scan-viewfinder">
        <video ref="videoEl" class="intake-camera" muted playsinline autoplay />
        <div class="scan-target" :class="`scan-target--${guideType}`" aria-hidden="true" />
      </div>
      <p class="scan-help">{{ guideType === 'qr' ? '将完整二维码放入方框内' : '将完整条形码放入宽框内' }}，靠近一些，避开反光</p>
      <button class="secondary-button" type="button" aria-label="停止扫码" @click="stopCamera">停止扫码</button>
    </div>
    <div class="scan-fallback-actions">
      <button
        class="secondary-button"
        type="button"
        :disabled="busy || scanning"
        :aria-label="photoAriaLabel"
        @click="openPhoto"
      >
        {{ photoAriaLabel }}
      </button>
      <button
        class="secondary-button"
        type="button"
        :disabled="busy || scanning"
        aria-label="从相册选图"
        @click="albumRef?.click()"
      >从相册选图</button>
    </div>
    <p v-if="scannerHint" class="operation-feedback scan-feedback" role="status" aria-live="polite">{{ scannerHint }}</p>
    <label class="field">
      <span>{{ fieldText }}（也可手输）</span>
      <input
        ref="manualRef"
        :value="modelValue"
        type="text"
        autocomplete="off"
        autocapitalize="off"
        :spellcheck="false"
        enterkeyhint="go"
        :disabled="scanning"
        :placeholder="placeholder"
        :aria-label="inputAriaLabel"
        @input="onInput"
      />
    </label>
    <div class="button-row">
      <button
        class="secondary-button"
        type="submit"
        :disabled="busy || scanning"
        :aria-label="submitAria"
        @click="submitManual"
      >
        {{ busy ? '正在识别' : submitLabel }}
      </button>
    </div>
    <details class="scan-options">
      <summary>对象类型：{{ scanObjectTypeOptions.find(option => option.value === objectType)?.label || '自动判断' }}（可选）</summary>
      <label class="field">
        <span>同号无法区分时再选择对象类型</span>
        <select aria-label="对象类型" :value="objectType" :disabled="scanning" @change="onType">
          <option value="">由系统判断</option>
          <option v-for="option in scanObjectTypeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </details>
    <input
      ref="photoRef"
      class="still-capture"
      type="file"
      accept="image/*"
      capture="environment"
      aria-label="条码照片"
      @change="onPhoto"
    />
    <input
      ref="albumRef"
      class="still-capture"
      type="file"
      accept="image/*"
      aria-label="相册条码照片"
      @change="onPhoto"
    />
  </form>
</template>

<style scoped>
.scan-entry {
  min-width: 0;
}

.scan-entry > *,
.scan-entry .field {
  min-width: 0;
}

.scan-entry input:not([type="file"]),
.scan-entry select {
  box-sizing: border-box;
  max-width: 100%;
  width: 100%;
}

.scan-camera-button {
  width: 100%;
  min-height: 56px;
  font-size: 18px;
}

.scan-help {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--muted, #64748b);
  overflow-wrap: anywhere;
}

.scan-fallback-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.scan-fallback-actions button {
  min-width: 0;
  min-height: 44px;
  white-space: normal;
}

.scan-camera-panel {
  display: grid;
  gap: 10px;
}

.scan-guide-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.scan-guide-switch button {
  min-height: 44px;
  border: 1px solid var(--border, #cbd5e1);
  border-radius: 8px;
  background: var(--surface, #fff);
  color: inherit;
  font: inherit;
}

.scan-guide-switch button[aria-pressed="true"] {
  border-color: #0f766e;
  background: #ccfbf1;
  color: #115e59;
  font-weight: 700;
}

.scan-viewfinder {
  position: relative;
  overflow: hidden;
  border-radius: 12px;
  background: #0f172a;
  height: clamp(240px, 78vw, 380px);
}

.scan-viewfinder .intake-camera {
  display: block;
  width: 100%;
  height: 100%;
  max-height: none;
  object-fit: cover;
}

.scan-target {
  position: absolute;
  top: 50%;
  left: 50%;
  box-sizing: border-box;
  border: 3px solid #facc15;
  border-radius: 10px;
  box-shadow: 0 0 0 999px rgb(15 23 42 / 34%), 0 0 0 1px #0f172a inset;
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.scan-target--qr {
  width: min(62%, 240px);
  aspect-ratio: 1;
}

.scan-target--barcode {
  width: calc(100% - 32px);
  max-width: 460px;
  aspect-ratio: 4 / 1;
  max-height: 100px;
}

.scan-feedback {
  margin: 0;
  overflow-wrap: anywhere;
}

.scan-options summary {
  min-height: 36px;
  cursor: pointer;
  color: var(--muted, #64748b);
  font-size: 13px;
}

@media (max-width: 900px) {
  .scan-start,
  .scan-entry:not(.is-scanning) > .scan-fallback-actions {
    display: none;
  }
}
</style>
