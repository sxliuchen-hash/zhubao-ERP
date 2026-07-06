<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import { qualityCases } from '../../data/m1PrototypeData';
</script>

<template>
  <div class="page-stack">
    <section class="toolbar-band">
      <div>
        <h2>质检返工与损耗处理</h2>
        <p>返工只保留一套工单状态机，不再创建独立返工状态机，避免后期逻辑冲突。</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" type="button">批量派检</button>
        <button class="primary-button" type="button">登记质检</button>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>待处理质检</h2>
          <p>每条质检记录必须能关联工单、工序、报工、缺陷照片和处理动作。</p>
        </div>
      </div>
      <div class="quality-grid">
        <article v-for="item in qualityCases" :key="item.code" class="quality-item">
          <div class="quality-title">
            <strong>{{ item.code }}</strong>
            <StatusBadge :label="item.status" :tone="item.status.includes('完成') ? 'green' : item.status.includes('称重') ? 'red' : 'amber'" />
          </div>
          <dl>
            <dt>工单</dt>
            <dd>{{ item.workOrder }}</dd>
            <dt>工序</dt>
            <dd>{{ item.process }}</dd>
            <dt>质检员</dt>
            <dd>{{ item.inspector }}</dd>
            <dt>缺陷</dt>
            <dd>{{ item.defect }}</dd>
            <dt>处理</dt>
            <dd>{{ item.action }}</dd>
          </dl>
          <div class="button-row">
            <button class="secondary-button" type="button">查看证据</button>
            <button class="primary-button" type="button">处理</button>
          </div>
        </article>
      </div>
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>损耗审批</h2>
            <p>超标损耗必须能解释投入、产出、报废、返工和责任归因。</p>
          </div>
        </div>
        <div class="approval-list">
          <div>
            <strong>LOSS-20260706-009</strong>
            <span>ZD-20260706-001 / 抛光 / 损耗 3.8% / 标准 2.0%</span>
            <StatusBadge label="待组长审批" tone="red" />
          </div>
          <div>
            <strong>LOSS-20260706-010</strong>
            <span>ZD-20260706-006 / 焊接 / 重量差 1.20g</span>
            <StatusBadge label="待补称重" tone="amber" />
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>返工路径</h2>
            <p>返工工单是特殊工单类型，仍走同一套报工、质检和库存台账。</p>
          </div>
        </div>
        <div class="rework-flow">
          <span>质检不合格</span>
          <span>选择返工工序</span>
          <span>生成返工工单</span>
          <span>返工报工</span>
          <span>复检放行</span>
        </div>
      </div>
    </section>
  </div>
</template>
