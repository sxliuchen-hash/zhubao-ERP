<script setup lang="ts">
defineProps<{
  title: string;
}>();
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">ZB</span>
        <div>
          <strong>来料加工 ERP</strong>
          <span>生产追溯控制中台</span>
        </div>
      </div>
      <div class="sidebar-status">
        <span>当前基线</span>
        <strong>M2.1 / 评审工作台</strong>
        <i>API 优先，样例兜底，硬件失败可回退</i>
      </div>
      <nav class="nav">
        <slot name="nav" />
      </nav>
      <div class="sidebar-matrix" aria-label="系统运行概览">
        <span><b />API</span>
        <span><b />PWA</span>
        <span><b />NVR</span>
        <span><b />Ledger</span>
      </div>
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
          <span><i class="live-dot" />API 正常</span>
          <span>证据完整率 92%</span>
          <span>高风险 3</span>
        </div>
      </div>
      <header class="workspace-header">
        <div>
          <span class="eyebrow">M2 业务评审台</span>
          <h1>{{ title }}</h1>
          <p>客户来料、母单、工单、质检返工、库存台账、证据链在同一业务台面评审。</p>
        </div>
        <div class="header-actions">
          <button class="secondary-button" type="button">全局搜索</button>
          <button class="secondary-button" type="button">导出评审包</button>
          <button class="primary-button" type="button">新建母单</button>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>
