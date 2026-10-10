<script setup lang="ts">
import { ref } from 'vue';
import { parseScanCode, type ParsedScanCode } from './scanCode';

const content = ref('JG|work_order|CC-20260706-001-JL-ZD001');
const result = ref<ParsedScanCode | null>(null);
const error = ref('');

function resolve() {
  try {
    result.value = parseScanCode(content.value, 'pwa_report');
    error.value = '';
  } catch (err) {
    result.value = null;
    error.value = err instanceof Error ? err.message : '解析失败';
  }
}
</script>

<template>
  <section class="panel">
    <div class="panel-heading">
      <h2>扫码解析验证</h2>
      <span class="status-pill">PWA</span>
    </div>
    <label class="field">
      <span>二维码内容</span>
      <input v-model="content" type="text" />
    </label>
    <button class="primary-button" type="button" @click="resolve">解析</button>
    <p v-if="error" class="error-text">{{ error }}</p>
    <pre v-if="result" class="result-box">{{ result }}</pre>
  </section>
</template>
