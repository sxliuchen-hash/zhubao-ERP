<script setup lang="ts">
defineProps<{
  title: string;
}>();
</script>

<template>
  <div class="app-shell ops-shell review-shell">
    <aside class="sidebar ops-sidebar review-sidebar">
      <div class="brand ops-brand">
        <span class="brand-mark">ZB</span>
        <div>
          <strong>来料加工生产追溯系统</strong>
          <span>进销存 · 生产 · 质检 · 证据链</span>
        </div>
      </div>

      <div class="sidebar-status ops-status-card">
        <span>当前开发基线</span>
        <strong>M3.28 / 质检库存作业台</strong>
        <i>首页、来料、生产、质检和库存追溯已收敛为更接近 ERP/MES 的高密度作业台；库存台账、业务动作、轻量审批、附件元数据和硬件 POC 已接入，真实权限、审批 UI、对象存储、售后复核和正式工资仍在开发队列。</i>
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

    <main class="workspace ops-workspace review-workspace">
      <div class="top-strip ops-topbar review-topbar">
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

      <header class="workspace-header ops-page-header review-page-header">
        <div>
          <span class="eyebrow">Operations Console</span>
          <h1>{{ title }}</h1>
          <p>白班来料 6 单、在制 18 张、质检 7 批、证据完整率 92%，高风险阻断 3 项。</p>
          <div class="ops-scope-ribbon" aria-label="当前实现边界">
            <span>库存台账 JDBC</span>
            <span>业务动作写台账</span>
            <span>验收 fixture</span>
            <span>附件元数据</span>
            <span>硬件 POC</span>
            <span>工资试算基线</span>
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
