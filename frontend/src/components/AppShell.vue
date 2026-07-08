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
          <strong>来料加工 ERP/MES</strong>
          <span>库存 · 工单 · 质检 · 追溯</span>
        </div>
      </div>

      <div class="sidebar-status ops-status-card">
        <span>当前基线</span>
        <strong>M3.92 / 自助改密续期边界</strong>
        <i>仍处于 M3 增量开发：本轮在账号仓储、PBKDF2 密码哈希、失败锁定、账号启停、管理员解锁、生产密码策略和密码过期检测基础上补登录态自助改密续期，同时继续保留低成本硬件和人工兜底字段。</i>
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
          <span class="eyebrow">M3.92 ERP/MES OPERATIONS DESK</span>
          <h1>{{ title }}</h1>
          <p>白班来料、在制工单、质检返工、库存台账、审批、附件证据和硬件兜底统一进入现场作业台。</p>
          <div class="ops-scope-ribbon" aria-label="当前实现边界">
            <span>库存台账 JDBC</span>
            <span>业务动作写台账</span>
            <span>验收 fixture</span>
            <span>附件元数据仓储</span>
            <span>硬件 POC</span>
            <span>运维中心</span>
            <span>登录会话</span>
            <span>账号密码哈希</span>
            <span>失败登录锁定</span>
            <span>账号启停解锁</span>
            <span>密码策略校验</span>
            <span>密码过期边界</span>
            <span>自助改密续期</span>
            <span>登记表模板</span>
            <span>设备产量汇总</span>
            <span>业务单据快照</span>
            <span>复核补录</span>
            <span>登录审计</span>
            <span>授权导航过滤</span>
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
            <strong>57</strong>
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
