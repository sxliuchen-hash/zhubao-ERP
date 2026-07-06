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
          <strong>来料加工生产追溯系统</strong>
          <span>进销存 · 生产 · 质检 · 证据链</span>
        </div>
      </div>

      <div class="sidebar-status ops-status-card">
        <span>当前开发基线</span>
        <strong>M3.15 / 台账闭环扩展中</strong>
        <i>已接：来料入库、生产报工、质检冻结、返工、复检放行、成品出库、附件上传。待接：退货、审批、对象存储、真实权限。</i>
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
          <span class="eyebrow">Operations Console</span>
          <h1>{{ title }}</h1>
          <p>围绕客户来料、工序报工、质检返工、库存台账和硬件证据组织现场作业。</p>
          <div class="ops-scope-ribbon" aria-label="当前实现边界">
            <span>库存台账 JDBC</span>
            <span>业务动作写台账</span>
            <span>附件元数据</span>
            <span>硬件 POC</span>
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
