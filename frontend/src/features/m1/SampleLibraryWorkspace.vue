<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import { sampleScenarios } from '../../data/m1PrototypeData';

function scenarioTone(status: string) {
  if (status === 'ready') return 'green';
  if (status === 'blocked') return 'red';
  return 'amber';
}

function scenarioLabel(status: string) {
  if (status === 'ready') return '可演示';
  if (status === 'blocked') return '阻塞';
  return '待补数据';
}
</script>

<template>
  <div class="page-stack">
    <section class="command-hero">
      <div>
        <span class="eyebrow">Sample Control</span>
        <h2>样例库与验收脚本</h2>
        <p>把业务评审、测试、演示和后续验收统一到同一批样例，避免每次演示都临时造数据。</p>
      </div>
      <div class="hero-stat">
        <strong>{{ sampleScenarios.length }}</strong>
        <span>条核心样例</span>
      </div>
    </section>

    <section class="sample-grid">
      <article v-for="scenario in sampleScenarios" :key="scenario.id" class="sample-card">
        <div class="sample-card-head">
          <div>
            <small>{{ scenario.id }}</small>
            <strong>{{ scenario.name }}</strong>
          </div>
          <StatusBadge :label="scenarioLabel(scenario.status)" :tone="scenarioTone(scenario.status)" />
        </div>
        <p>{{ scenario.purpose }}</p>
        <div class="chip-row">
          <span v-for="node in scenario.chain" :key="node">{{ node }}</span>
        </div>
        <div class="evidence-box">
          <strong>验收证据</strong>
          <span>{{ scenario.evidence.join(' / ') }}</span>
        </div>
        <div class="sample-owner">
          <span>负责人</span>
          <strong>{{ scenario.owner }}</strong>
        </div>
      </article>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>样例管理规则</h2>
          <p>样例要同时服务产品评审、自动化测试、现场培训和验收签字。</p>
        </div>
      </div>
      <div class="rule-grid">
        <article>
          <strong>同一主链路</strong>
          <span>订单、母单、工单、库存、质检、视频必须能被一个样例串起来。</span>
        </article>
        <article>
          <strong>可重复执行</strong>
          <span>后续进入数据库后，样例通过 seed 脚本初始化，测试结果稳定。</span>
        </article>
        <article>
          <strong>证据完整</strong>
          <span>每个样例至少包含业务单据、操作日志、库存台账和必要附件。</span>
        </article>
        <article>
          <strong>版本可追踪</strong>
          <span>样例变更必须同步文档、测试和 Git 提交说明。</span>
        </article>
      </div>
    </section>
  </div>
</template>
