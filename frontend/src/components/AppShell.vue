<script setup lang="ts">
defineProps<{
  title: string;
}>();
</script>

<template>
  <div class="app-shell ops-shell">
    <aside class="sidebar ops-sidebar">
      <div class="brand ops-brand">
        <span class="brand-mark">ZB</span>
        <div>
          <strong>来料加工 ERP</strong>
          <span>进销存 · 生产 · 追溯 · 证据链</span>
        </div>
      </div>

      <div class="sidebar-status ops-status-card">
        <span>当前开发基线</span>
        <strong>M3.6 / 生产报工写台账</strong>
        <i>文档与样例工作台已成体系；来料入库、生产报工已写真实库存台账；质检返工、出库、审批、附件仍待开发</i>
      </div>

      <nav class="nav ops-nav" aria-label="主导航">
        <slot name="nav" />
      </nav>

      <div class="sidebar-matrix ops-health-grid" aria-label="系统运行概览">
        <span><b />API</span>
        <span><b />PWA</span>
        <span><b />NVR</span>
        <span><b />Ledger</span>
      </div>

      <div class="sidebar-footer ops-version-card">
        <span>技术基线</span>
        <strong>Spring Boot / Vue / PWA / MySQL</strong>
      </div>
    </aside>

    <main class="workspace ops-workspace">
      <div class="top-strip ops-topbar">
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

      <header class="workspace-header ops-page-header">
        <div>
          <span class="eyebrow">Operations Review Console</span>
          <h1>{{ title }}</h1>
          <p>把客户来料、母单拆分、工序报工、质检返工、库存台账和硬件证据放到同一张可排查、可追责、可验收的业务台面。</p>
          <div class="ops-scope-ribbon" aria-label="当前实现边界">
            <span>库存台账 JDBC</span>
            <span>业务工作台快照</span>
            <span>业务动作写台账</span>
            <span>硬件 POC 记录</span>
            <span>权限审批待接入</span>
          </div>
        </div>
        <div class="header-actions command-actions-panel" aria-label="全局操作">
          <label class="global-search">
            <span>全局追溯</span>
            <input value="MD-20260706-001" aria-label="全局追溯" />
          </label>
          <button class="secondary-button" type="button">导出评审包</button>
          <button class="primary-button" type="button">新建母单</button>
        </div>
      </header>

      <slot />
    </main>
  </div>
</template>
