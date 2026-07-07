<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import {
  acceptanceDataSets,
  openSourceSampleMappings,
  sampleScenarios,
  type Tone,
} from '../../data/m1PrototypeData';

function scenarioTone(status: string): Tone {
  if (status === 'ready') return 'green';
  if (status === 'blocked') return 'red';
  return 'amber';
}

function scenarioLabel(status: string) {
  if (status === 'ready') return '可演示';
  if (status === 'blocked') return '阻塞';
  return '待补数据';
}

function mappingLabel(status: string) {
  if (status === 'applied') return '已映射';
  if (status === 'watching') return '观察中';
  return '预留';
}

function mappingTone(status: string): Tone {
  if (status === 'applied') return 'green';
  if (status === 'watching') return 'blue';
  return 'gray';
}

function dataSetLabel(status: string) {
  if (status === 'active') return '测试中';
  if (status === 'blocked') return '阻塞';
  return '待现场数据';
}

function dataSetTone(status: string): Tone {
  if (status === 'active') return 'green';
  if (status === 'blocked') return 'red';
  return 'amber';
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
      <StatusBadge label="M3.50 样例映射" tone="green" />
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

    <section class="section-block">
      <div class="section-heading">
        <div>
          <span class="eyebrow">OPEN SOURCE MAPPING</span>
          <h2>开源样例映射</h2>
          <p>把 ERPNext、Odoo、InvenTree 的成熟业务对象映射到本项目样例，避免临时造轮子。</p>
        </div>
        <StatusBadge :label="`${openSourceSampleMappings.length} 条映射`" tone="blue" />
      </div>
      <div class="sample-source-grid">
        <article v-for="mapping in openSourceSampleMappings" :key="mapping.source">
          <div class="sample-card-head">
            <div>
              <small>{{ mapping.mappedScenarioId }}</small>
              <strong>{{ mapping.source }}</strong>
            </div>
            <StatusBadge :label="mappingLabel(mapping.status)" :tone="mappingTone(mapping.status)" />
          </div>
          <p>{{ mapping.reusablePattern }}</p>
          <dl class="detail-grid">
            <dt>映射模块</dt>
            <dd>{{ mapping.mappedModule }}</dd>
            <dt>验收规则</dt>
            <dd>{{ mapping.acceptanceRule }}</dd>
            <dt>参考链接</dt>
            <dd>{{ mapping.referenceUrl }}</dd>
          </dl>
        </article>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <span class="eyebrow">REAL DATA TEST</span>
          <h2>真实验收数据集</h2>
          <p>每个样例必须能指向 fixture、自动化测试、证据编码和当前缺口，后续现场数据按这个矩阵替换。</p>
        </div>
        <StatusBadge :label="`${acceptanceDataSets.length} 组数据`" tone="green" />
      </div>
      <div class="acceptance-data-grid">
        <article v-for="dataset in acceptanceDataSets" :key="dataset.code">
          <div class="sample-card-head">
            <div>
              <small>{{ dataset.scenarioId }}</small>
              <strong>{{ dataset.code }}</strong>
            </div>
            <StatusBadge :label="dataSetLabel(dataset.status)" :tone="dataSetTone(dataset.status)" />
          </div>
          <dl class="detail-grid">
            <dt>fixture</dt>
            <dd>{{ dataset.fixture }}</dd>
            <dt>自动化</dt>
            <dd>{{ dataset.automatedTest }}</dd>
            <dt>覆盖链路</dt>
            <dd>{{ dataset.covers.join(' / ') }}</dd>
            <dt>证据</dt>
            <dd>{{ dataset.evidence.join(' / ') }}</dd>
            <dt>缺口</dt>
            <dd>{{ dataset.gaps.join(' / ') }}</dd>
          </dl>
        </article>
      </div>
    </section>
  </div>
</template>
