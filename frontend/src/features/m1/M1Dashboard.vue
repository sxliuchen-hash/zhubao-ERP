<script setup lang="ts">
import MetricCard from '../../components/MetricCard.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { metrics, workOrders, workQueues } from '../../data/m1PrototypeData';

function severityTone(severity: string) {
  if (severity === 'high') return 'red';
  if (severity === 'medium') return 'amber';
  return 'gray';
}
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid" aria-label="今日业务指标">
      <MetricCard v-for="metric in metrics" :key="metric.label" :metric="metric" />
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>现场待办</h2>
            <p>优先处理会阻断入库、报工、质检和出库的事项。</p>
          </div>
          <button class="text-button" type="button">查看全部</button>
        </div>
        <div class="work-queue">
          <article v-for="item in workQueues" :key="item.id" class="queue-row">
            <StatusBadge :label="item.severity === 'high' ? '高' : item.severity === 'medium' ? '中' : '普通'" :tone="severityTone(item.severity)" />
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.subtitle }}</span>
            </div>
            <small>{{ item.owner }} / {{ item.due }}</small>
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>核心链路进度</h2>
            <p>以母单和子工单为主线观察现场瓶颈。</p>
          </div>
        </div>
        <div class="pipeline">
          <div class="pipeline-step active">
            <span>01</span>
            <strong>来料</strong>
            <small>6 单</small>
          </div>
          <div class="pipeline-step active">
            <span>02</span>
            <strong>拆包/称重</strong>
            <small>4 单</small>
          </div>
          <div class="pipeline-step active">
            <span>03</span>
            <strong>工序报工</strong>
            <small>18 张</small>
          </div>
          <div class="pipeline-step warning">
            <span>04</span>
            <strong>质检返工</strong>
            <small>7 批</small>
          </div>
          <div class="pipeline-step">
            <span>05</span>
            <strong>出库追溯</strong>
            <small>4 单</small>
          </div>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>在制工单看板</h2>
          <p>用于车间组长快速判断分配、报工、称重和质检状态。</p>
        </div>
        <button class="primary-button" type="button">新建拆分</button>
      </div>
      <div class="data-table">
        <div class="table-row table-head">
          <span>工单</span>
          <span>客户/物料</span>
          <span>当前工序</span>
          <span>负责人</span>
          <span>进度</span>
          <span>状态</span>
        </div>
        <div v-for="order in workOrders" :key="order.code" class="table-row">
          <strong>{{ order.code }}</strong>
          <span>{{ order.customer }} / {{ order.material }}</span>
          <span>{{ order.process }}</span>
          <span>{{ order.assignee }}</span>
          <span>
            <i class="progress-bar"><b :style="{ width: `${order.progress}%` }" /></i>
            {{ order.finishedQty }}/{{ order.plannedQty }}
          </span>
          <StatusBadge :label="order.status" :tone="order.status.includes('待') ? 'amber' : order.status.includes('返工') ? 'violet' : 'green'" />
        </div>
      </div>
    </section>
  </div>
</template>
