<script setup lang="ts">
const capabilities = [
  'Spring Boot API',
  'Vue 3 PWA',
  '扫码解析',
  '转移式库存台账',
  '视频截取任务',
  'RBAC 基线',
  '测试套件',
];
</script>

<template>
  <section class="panel">
    <div class="panel-heading">
      <h2>M0 能力边界</h2>
      <span class="status-pill">ready</span>
    </div>
    <div class="capability-grid">
      <article v-for="capability in capabilities" :key="capability" class="capability-item">
        {{ capability }}
      </article>
    </div>
  </section>
</template>
