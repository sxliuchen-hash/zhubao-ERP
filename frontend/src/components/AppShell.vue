<script setup lang="ts">
defineProps<{
  title: string;
}>();
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">ERP</span>
        <div>
          <strong>来料加工 ERP</strong>
          <span>生产追溯中台</span>
        </div>
      </div>
      <div class="sidebar-status">
        <span>今日评审版本</span>
        <strong>M1.2 / 业务原型</strong>
        <i>接口基线已接入样例与硬件管理</i>
      </div>
      <nav class="nav">
        <slot name="nav" />
      </nav>
      <div class="sidebar-footer">
        <span>实施基线</span>
        <strong>Spring Boot / Vue / PWA</strong>
      </div>
    </aside>
    <main class="workspace">
      <div class="top-strip">
        <div class="context-stack">
          <span>车间：总装一线</span>
          <span>班次：白班</span>
          <span>业务日期：2026-07-06</span>
        </div>
        <div class="sync-stack">
          <span>API 正常</span>
          <span>证据完整率 92%</span>
          <span>高风险 3</span>
        </div>
      </div>
      <header class="workspace-header">
        <div>
          <span class="eyebrow">M1 业务原型</span>
          <h1>{{ title }}</h1>
          <p>客户来料、母单、工单、质检返工、库存台账、证据链在同一业务台面评审。</p>
        </div>
        <div class="header-actions">
          <button class="secondary-button" type="button">导出评审包</button>
          <button class="primary-button" type="button">新建母单</button>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>
