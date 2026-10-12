<script setup lang="ts">
import { computed } from 'vue';
import { renderQrSvg } from '../services/qrcode';

// 通用二维码展示组件：给定文本内容，渲染为内联 SVG 二维码（离线可用）。
const props = defineProps<{ content: string; size?: number }>();

const svg = computed(() => renderQrSvg(props.content));
const boxSize = computed(() => `${props.size ?? 120}px`);
</script>

<template>
  <div class="qr-image" :style="{ width: boxSize, height: boxSize }" v-html="svg"></div>
</template>

<style scoped>
.qr-image {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--content);
  border: 1px solid var(--border);
}
.qr-image :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
