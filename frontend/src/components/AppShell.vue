<script setup lang="ts">
defineProps<{
  title: string;
}>();
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">M1</span>
        <div>
          <strong>来料加工 ERP</strong>
          <span>业务原型台</span>
        </div>
      </div>
      <nav class="nav">
        <slot name="nav" />
      </nav>
      <div class="sidebar-footer">
        <span>实施基线</span>
        <strong>Java / Vue / PWA</strong>
      </div>
    </aside>
    <main class="workspace">
      <header class="workspace-header">
        <div>
          <span class="eyebrow">M1 业务原型</span>
          <h1>{{ title }}</h1>
          <p>围绕客户来料、母单、工单、质检返工、库存台账和追溯证据的评审版界面。</p>
        </div>
        <div class="header-actions">
          <button class="secondary-button" type="button">导出评审</button>
          <button class="primary-button" type="button">新建业务单</button>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>
