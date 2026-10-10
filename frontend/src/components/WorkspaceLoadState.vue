<script setup lang="ts">
import { inject } from 'vue';

defineProps<{ error?: Error }>();
const retry = inject<() => void>('zhubao-retry-workspace', () => {});
</script>

<template>
  <section class="section-block" :role="error ? 'alert' : 'status'" :aria-busy="!error">
    <template v-if="error">
      <p>页面加载失败，请检查网络后重试。</p>
      <button class="primary-button" type="button" @click="retry">重新加载页面</button>
    </template>
    <p v-else>正在加载页面，请稍候。</p>
  </section>
</template>
