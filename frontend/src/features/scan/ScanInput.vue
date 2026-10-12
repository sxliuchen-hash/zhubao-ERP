<script setup lang="ts">
import { computed, ref } from 'vue';
import { hasLiveCamera } from './cameraBarcode';
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
}>(), {
  placeholder: '',
  submitAriaLabel: '',
  cameraAriaLabel: '打开摄像头',
  photoAriaLabel: '拍照识别',
  busy: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  draft: [];
  recognize: [draft: ScanDraft];
}>();

const scanner = useScanner();
const scanning = scanner.scanning;
const scannerHint = scanner.hint;
const videoEl = scanner.videoRef;
const photoRef = ref<HTMLInputElement | null>(null);
const objectType = ref<ScanObjectType | ''>('');
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
  acceptSubmit = false;
  emit('recognize', draftOf(props.modelValue));
  queueMicrotask(() => {
    acceptSubmit = true;
  });
}

function openPhoto() {
  photoRef.value?.click();
}

function publish(raw: string) {
  emit('update:modelValue', raw);
  emit('recognize', draftOf(raw));
}

async function onCamera() {
  if (!hasLiveCamera()) {
    photoRef.value?.click();
    return;
  }
  const raw = await scanner.scanCamera();
  if (raw) publish(raw);
}

async function onPhoto(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  const raw = await scanner.scanPhoto(file);
  if (raw) publish(raw);
}
</script>

<template>
  <form class="scan-entry intake-form" @submit.prevent="submitManual">
    <label class="field">
      <span>对象类型</span>
      <select aria-label="对象类型" :value="objectType" @change="onType">
        <option value="">由系统判断</option>
        <option v-for="option in scanObjectTypeOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
    </label>
    <label class="field">
      <span>{{ fieldText }}</span>
      <input
        :value="modelValue"
        type="text"
        autocomplete="off"
        :placeholder="placeholder"
        :aria-label="inputAriaLabel"
        @input="onInput"
      />
    </label>
    <div class="button-row">
      <button
        class="primary-button"
        type="submit"
        :disabled="busy || scanning"
        :aria-label="submitAria"
        @click="submitManual"
      >
        {{ busy ? '正在识别' : submitLabel }}
      </button>
      <button
        class="secondary-button"
        type="button"
        :disabled="scanning"
        :aria-label="cameraAriaLabel"
        @click="onCamera"
      >
        {{ scanning ? '正在扫码' : '打开摄像头' }}
      </button>
      <button
        class="secondary-button"
        type="button"
        :disabled="scanning"
        :aria-label="photoAriaLabel"
        @click="openPhoto"
      >
        {{ photoAriaLabel }}
      </button>
    </div>
    <input
      ref="photoRef"
      class="still-capture"
      type="file"
      accept="image/*"
      capture="environment"
      aria-label="条码照片"
      @change="onPhoto"
    />
    <video v-show="scanning" ref="videoEl" class="intake-camera" muted playsinline autoplay />
    <p v-if="scannerHint" class="operation-feedback">{{ scannerHint }}</p>
  </form>
</template>
