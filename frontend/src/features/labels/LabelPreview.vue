<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { LabelPayload } from '../../services/labelApi';
import type { LabelPrintImage } from '../../services/printRoutingTypes';
import { labelPreviewScale } from '../m1/labelPrint';
import { renderLabelPreviewImage } from './labelPreviewImage';

const props = defineProps<{ payload: LabelPayload }>();
const emit = defineEmits<{ ready: [image: LabelPrintImage | null] }>();
const image = ref<LabelPrintImage | null>(null);
const error = ref('');
let generation = 0;
onBeforeUnmount(() => { ++generation; });
watch(() => props.payload, async payload => {
  const current = ++generation;
  image.value = null;
  error.value = '';
  emit('ready', null);
  try {
    const rendered = await renderLabelPreviewImage(payload);
    if (current !== generation) return;
    image.value = rendered;
    emit('ready', rendered);
  } catch (caught) {
    if (current === generation) error.value = caught instanceof Error ? caught.message : '标签画面生成失败';
  }
}, { immediate: true, deep: true });
const source = computed(() => image.value ? `data:image/png;base64,${image.value.pngBase64}` : '');
const size = computed(() => ({
  width: `calc(${props.payload.printerSetting.widthMm}mm * var(--label-scale, ${labelPreviewScale}))`,
  height: `calc(${props.payload.printerSetting.heightMm}mm * var(--label-scale, ${labelPreviewScale}))`,
}));
</script>

<template>
  <div v-if="source" class="label-mat"><img :src="source" :style="size" alt="标签最终打印画面" /></div>
  <p v-else-if="error" role="alert">{{ error }}</p>
  <p v-else role="status">正在生成标签画面…</p>
  <a v-if="source" :href="source" :download="`${payload.primaryCode}-标签.png`">下载预览图片</a>
</template>

<style scoped>
.label-mat { overflow: auto; min-width: 0; padding: 12px; border: 1px solid var(--line); background: var(--workspace); }
img { display: block; max-width: none; }
</style>
