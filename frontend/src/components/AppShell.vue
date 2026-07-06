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
          <strong>来料加工进销存与生产管理系统</strong>
          <span>库存台账 · 生产工单 · 质检返工 · 证据链</span>
        </div>
      </div>

      <div class="sidebar-status ops-status-card">
        <span>当前开发基线</span>
        <strong>M3.36 / 现场指挥台产品化</strong>
        <i>首页已按真实车间调度台重构；附件存储、权限守卫、库存台账、硬件兜底和样例验收仍按模块逐步推进。</i>
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
          <p>白班来料 6 单、在制 18 张、质检 7 批、证据完整率 92%，高风险阻断 3 项，优先处理称重、视频和损耗审批。</p>
          <div class="ops-scope-ribbon" aria-label="当前实现边界">
            <span>库存台账 JDBC</span>
            <span>业务动作写台账</span>
            <span>验收 fixture</span>
            <span>附件本地存储</span>
            <span>硬件 POC</span>
            <span>工资试算基线</span>
          </div>
        </div>
        <div class="ops-command-metrics" aria-label="生产作业状态">
          <article>
            <span>待办</span>
            <strong>31</strong>
            <small>收货/报工/质检</small>
          </article>
          <article>
            <span>阻断</span>
            <strong>3</strong>
            <small>称重/视频/审批</small>
          </article>
          <article>
            <span>台账</span>
            <strong>55</strong>
            <small>自动化测试通过</small>
          </article>
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
