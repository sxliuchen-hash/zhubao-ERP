<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  lossApprovals as fallbackLossApprovals,
  qualityCases as fallbackQualityCases,
  qualityMetrics as fallbackQualityMetrics,
} from '../../data/m1PrototypeData';
import {
  fetchQualityWorkspace,
  type QualityWorkspaceModel,
} from '../../services/businessWorkspaceApi';

const workspace = ref<QualityWorkspaceModel>({
  metrics: fallbackQualityMetrics,
  cases: fallbackQualityCases,
  lossApprovals: fallbackLossApprovals,
});

const activeCase = computed(
  () =>
    workspace.value.cases.find((item) => item.status.includes('待') || item.action.includes('返工')) ??
    workspace.value.cases[0],
);

const activeApproval = computed(
  () =>
    workspace.value.lossApprovals.find((item) => item.action.includes('待')) ??
    workspace.value.lossApprovals[0],
);

onMounted(async () => {
  try {
    workspace.value = await fetchQualityWorkspace();
  } catch {
    // Keep deterministic local samples available when the backend is not running.
  }
});
</script>

<template>
  <div class="page-stack">
    <section class="operations-hero">
      <div class="hero-copy">
        <span class="eyebrow">Quality Control</span>
        <h2>质检返工处理台</h2>
        <p>用缺陷、照片、称重、损耗和返工工单串成一条处理路径，避免返工状态机分叉。</p>
      </div>
      <div class="hero-kpis">
        <article v-for="metric in workspace.metrics.slice(0, 3)" :key="metric.label">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
        </article>
      </div>
    </section>

    <section class="control-filter-bar" aria-label="质检筛选">
      <div>
        <span>质检队列</span>
        <strong>待检 / 返工复检 / 损耗审批</strong>
      </div>
      <div>
        <span>证据要求</span>
        <strong>缺陷照片、称重记录、处理动作</strong>
      </div>
      <div>
        <span>返工策略</span>
        <strong>返工仍走同一套工单状态机</strong>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" type="button">只看阻断</button>
        <button class="secondary-button" type="button">批量复检</button>
      </div>
    </section>

    <section class="module-ops-board" aria-label="质检作业主控台">
      <div class="module-master">
        <div class="module-toolbar">
          <div>
            <span class="eyebrow">QC Desk</span>
            <h2>质检作业主控台</h2>
            <p>质检、返工、缺陷证据和损耗审批在同一张作业表内处理，避免返工状态分叉。</p>
          </div>
          <div class="module-tools">
            <label>
              <span>质检扫码</span>
              <input value="QC-20260706-021" aria-label="质检扫码" />
            </label>
            <button class="primary-button" type="button">登记质检</button>
            <button class="secondary-button" type="button">上传照片</button>
          </div>
        </div>
        <div class="module-table-wrap">
          <table class="module-table">
            <thead>
              <tr>
                <th>质检单</th>
                <th>工单/工序</th>
                <th>质检员</th>
                <th>缺陷</th>
                <th>处理</th>
                <th>状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in workspace.cases" :key="item.code">
                <td><strong>{{ item.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ item.workOrder }}</b>
                    <small>{{ item.process }}</small>
                  </span>
                </td>
                <td>{{ item.inspector }}</td>
                <td>{{ item.defect }}</td>
                <td>{{ item.action }}</td>
                <td><StatusBadge :label="item.status" :tone="item.status.includes('完成') ? 'green' : item.status.includes('称重') ? 'red' : 'amber'" /></td>
                <td><button class="table-action" type="button">处理</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeCase" class="module-detail">
        <div class="detail-title">
          <span>当前质检</span>
          <strong>{{ activeCase.code }}</strong>
          <StatusBadge :label="activeCase.status" :tone="activeCase.status.includes('称重') ? 'red' : 'amber'" />
        </div>
        <dl class="detail-grid">
          <dt>工单</dt>
          <dd>{{ activeCase.workOrder }}</dd>
          <dt>工序</dt>
          <dd>{{ activeCase.process }}</dd>
          <dt>缺陷</dt>
          <dd>{{ activeCase.defect }}</dd>
          <dt>处理动作</dt>
          <dd>{{ activeCase.action }}</dd>
          <dt>损耗审批</dt>
          <dd>{{ activeApproval?.code ?? '无' }} / {{ activeApproval?.action ?? '正常' }}</dd>
          <dt>证据</dt>
          <dd>缺陷照片、称重记录、处理结论</dd>
        </dl>
        <div class="detail-actions">
          <button class="primary-button" type="button">生成返工工单</button>
          <button class="secondary-button" type="button">登记缺陷照片</button>
          <button class="secondary-button" type="button">提交损耗审批</button>
        </div>
      </aside>
    </section>

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

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>待处理质检</h2>
            <p>每条质检记录必须能关联工单、工序、报工、缺陷照片和处理动作。</p>
          </div>
        </div>
        <div class="quality-grid dense">
          <article v-for="item in workspace.cases" :key="item.code" class="quality-item">
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
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>质检指标</h2>
            <p>把返工、损耗和证据缺口放在同一个看板里。</p>
          </div>
        </div>
        <div class="evidence-health">
          <article v-for="metric in workspace.metrics" :key="metric.label">
            <div>
              <strong>{{ metric.label }}</strong>
              <span>{{ metric.detail }}</span>
            </div>
            <StatusBadge :label="metric.value" :tone="metric.tone" />
          </article>
        </div>
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
          <div v-for="approval in workspace.lossApprovals" :key="approval.code">
            <strong>{{ approval.code }}</strong>
            <span>{{ approval.workOrder }} / {{ approval.process }} / 标准 {{ approval.standard }} / 实际 {{ approval.actual }} / {{ approval.evidence }}</span>
            <StatusBadge :label="approval.action" :tone="approval.tone" />
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
