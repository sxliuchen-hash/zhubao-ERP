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
          <span>进销存 · 生产 · 追溯</span>
        </div>
      </div>
      <div class="sidebar-status">
        <span>当前基线</span>
        <strong>M2.4 / 操作型 UI 重构</strong>
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
          <p>把客户来料、母单拆分、工序报工、质检返工、库存台账和硬件证据放到可操作的业务台面。</p>
        </div>
        <div class="header-actions command-actions-panel" aria-label="全局操作">
          <label class="global-search">
            <span>全局搜索</span>
            <input value="MD-20260706-001" aria-label="全局搜索" />
          </label>
          <button class="secondary-button" type="button">导出评审包</button>
          <button class="primary-button" type="button">新建母单</button>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>
