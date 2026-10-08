<script setup lang="ts">
defineProps<{
  title: string;
}>();

const traceQuery = defineModel<string>('traceQuery', { default: '' });

const businessDate = formatToday();

function formatToday() {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}
</script>

<template>
  <div class="app-shell ops-shell review-shell">
    <aside class="sidebar ops-sidebar review-sidebar">
      <div class="brand ops-brand">
        <span class="brand-mark">ZB</span>
        <div>
          <strong>来料加工</strong>
          <span>收货 · 生产 · 质检 · 库存</span>
        </div>
      </div>

      <nav class="nav ops-nav" aria-label="主导航">
        <slot name="nav" />
      </nav>
    </aside>

    <main class="workspace ops-workspace review-workspace">
      <div class="top-strip ops-topbar review-topbar">
        <div class="context-stack">
          <span>{{ businessDate }}</span>
        </div>
        <label class="global-search lean-search">
          <span>追溯</span>
          <input v-model="traceQuery" placeholder="母单、工单或快递单号" aria-label="追溯查询" />
        </label>
      </div>

      <header class="workspace-header ops-page-header review-page-header lean-header">
        <h1>{{ title }}</h1>
      </header>

      <slot />
    </main>
  </div>
</template>
