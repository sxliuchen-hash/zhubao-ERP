<script setup lang="ts">
import MetricCard from '../../components/MetricCard.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { getHardwareManualFallbackCount, metrics, workOrders, workQueues } from '../../data/m1PrototypeData';

function severityTone(severity: string) {
  if (severity === 'high') return 'red';
  if (severity === 'medium') return 'amber';
  return 'gray';
}

function severityLabel(severity: string) {
  if (severity === 'high') return '高';
  if (severity === 'medium') return '中';
  return '普通';
}

const flowRows = [
  { node: '来料接收', total: '6 单', abnormal: '2 单待确认', owner: '库管组', next: '扫码收货 / 拆包称重', tone: 'amber' },
  { node: '母单拆分', total: '11 张子工单', abnormal: '1 张未打印', owner: '计划员', next: '生成标签 / 分配工序', tone: 'blue' },
  { node: '工序报工', total: '18 张在制', abnormal: '5 张停留超时', owner: '车间组长', next: '补称重 / 派工', tone: 'red' },
  { node: '质检返工', total: '7 批待检', abnormal: '2 批返工复检', owner: '质检组', next: '登记缺陷 / 生成返工单', tone: 'amber' },
  { node: '成品出库', total: '4 单待出库', abnormal: '0 单阻断', owner: '仓储组', next: '复核追溯证据', tone: 'green' },
] as const;

const evidenceRows = [
  { item: '拆包视频', value: '92%', status: '1 个截取失败', owner: '库管员', tone: 'amber' },
  { item: '称重交接', value: '86%', status: '2 个待补照片', owner: '车间组长', tone: 'amber' },
  { item: '标签扫描', value: '100%', status: '今日无漏扫', owner: '计划员', tone: 'green' },
  { item: '硬件兜底', value: `${getHardwareManualFallbackCount()} 项`, status: '已纳入任务队列', owner: '实施顾问', tone: 'gray' },
] as const;

const shiftSignals = [
  { label: '入库阻断', value: '2', hint: '物料确认 / 视频失败', tone: 'amber' },
  { label: '生产瓶颈', value: '5', hint: '镶石工位积压', tone: 'red' },
  { label: '质检积压', value: '7', hint: '最早 09:40 提交', tone: 'amber' },
  { label: '出库风险', value: '0', hint: '追溯链路可核验', tone: 'green' },
] as const;

const ownerLoads = [
  { owner: '库管组', task: '拆包视频失败、母单待确认', count: '3 项', tone: 'amber' },
  { owner: '车间组长', task: '超标损耗、称重待补', count: '4 项', tone: 'red' },
  { owner: '质检组', task: '待检积压、返工复检', count: '7 批', tone: 'amber' },
  { owner: '实施顾问', task: '硬件兜底和样例脚本', count: '4 项', tone: 'blue' },
] as const;
</script>

<template>
  <div class="page-stack cockpit-page">
    <section class="command-center">
      <div class="command-main">
        <span class="eyebrow">Production Control</span>
        <h2>今日生产追溯指挥台</h2>
        <p>把客户来料所有权、称重证据、工序报工、质检返工和库存台账放到一张可排查、可追责、可验收的业务台面。</p>
        <div class="command-actions">
          <button class="primary-button" type="button">处理最高风险</button>
          <button class="secondary-button" type="button">打开追溯链路</button>
          <span><i class="live-dot" />车间实时评审视图</span>
        </div>
      </div>
      <div class="command-kpis">
        <article v-for="signal in shiftSignals" :key="signal.label" :class="`tone-${signal.tone}`">
          <span>{{ signal.label }}</span>
          <strong>{{ signal.value }}</strong>
          <small>{{ signal.hint }}</small>
        </article>
      </div>
    </section>

    <section class="control-filter-bar" aria-label="业务筛选">
      <div>
        <span>评审范围</span>
        <strong>今日白班 / 总装一线 / 客户来料</strong>
      </div>
      <div>
        <span>追溯对象</span>
        <strong>MD-20260706-001 / ZD-20260706-001</strong>
      </div>
      <div>
        <span>证据策略</span>
        <strong>NVR 优先，失败人工上传</strong>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" type="button">切换班次</button>
        <button class="secondary-button" type="button">导出日报</button>
      </div>
    </section>

    <section class="metric-grid" aria-label="今日业务指标">
      <MetricCard v-for="metric in metrics" :key="metric.label" :metric="metric" />
    </section>

    <section class="dashboard-grid">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>现场待办</h2>
            <p>按阻断程度排序，先处理会影响入库、报工、质检和出库的事项。</p>
          </div>
          <button class="text-button" type="button">查看全部</button>
        </div>
        <div class="work-queue">
          <article v-for="item in workQueues" :key="item.id" class="queue-row">
            <StatusBadge :label="severityLabel(item.severity)" :tone="severityTone(item.severity)" />
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
        <div class="flow-table">
          <div class="flow-row flow-head">
            <span>节点</span>
            <span>业务量</span>
            <span>异常</span>
            <span>责任</span>
            <span>下一动作</span>
          </div>
          <div v-for="row in flowRows" :key="row.node" class="flow-row">
            <strong>{{ row.node }}</strong>
            <span>{{ row.total }}</span>
            <StatusBadge :label="row.abnormal" :tone="row.tone" />
            <span>{{ row.owner }}</span>
            <span>{{ row.next }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
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
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>证据链健康</h2>
            <p>每个阻断项都要能落到责任人和补救动作。</p>
          </div>
        </div>
        <div class="evidence-health">
          <article v-for="row in evidenceRows" :key="row.item">
            <div>
              <strong>{{ row.item }}</strong>
              <span>{{ row.status }} / {{ row.owner }}</span>
            </div>
            <StatusBadge :label="row.value" :tone="row.tone" />
          </article>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>责任分布与下一步</h2>
          <p>评审时优先确认“谁处理、处理什么、处理后影响哪条链路”。</p>
        </div>
      </div>
      <div class="owner-grid">
        <article v-for="item in ownerLoads" :key="item.owner" :class="`tone-${item.tone}`">
          <div>
            <strong>{{ item.owner }}</strong>
            <span>{{ item.task }}</span>
          </div>
          <StatusBadge :label="item.count" :tone="item.tone" />
        </article>
      </div>
    </section>
  </div>
</template>
