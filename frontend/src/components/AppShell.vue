<script setup lang="ts">
defineProps<{
  title: string;
}>();
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">M0</span>
        <div>
          <strong>来料加工 ERP</strong>
          <span>工程验证台</span>
        </div>
      </div>
      <nav class="nav">
        <slot name="nav" />
      </nav>
    </aside>
    <main class="workspace">
      <header class="workspace-header">
        <h1>{{ title }}</h1>
        <p>前后端分离、PWA、库存台账、扫码和视频任务的 M0 验证基线</p>
      </header>
      <slot />
    </main>
  </div>
</template>
