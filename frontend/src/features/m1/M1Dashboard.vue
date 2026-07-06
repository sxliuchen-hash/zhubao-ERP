<script setup lang="ts">
import MetricCard from '../../components/MetricCard.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
import {
  getHardwareManualFallbackCount,
  inboundOrders,
  inventoryBalances,
  metrics,
  productionHandoffs,
  productionStationLoads,
  qualityCases,
  workOrders,
  workQueues,
} from '../../data/m1PrototypeData';

function severityTone(severity: string): Tone {
  if (severity === 'high') return 'red';
  if (severity === 'medium') return 'amber';
  return 'gray';
}

function severityLabel(severity: string) {
  if (severity === 'high') return '高';
  if (severity === 'medium') return '中';
  return '普通';
}

function workOrderTone(status: string): Tone {
  if (status.includes('称重') || status.includes('阻断')) return 'red';
  if (status.includes('待') || status.includes('返工')) return 'amber';
  return 'green';
}

function inboundTone(status: string, video: string): Tone {
  if (video === '失败' || status.includes('异常')) return 'red';
  if (status.includes('待') || video === '截取中') return 'amber';
  return 'green';
}

function qualityTone(status: string): Tone {
  if (status.includes('待')) return 'amber';
  if (status.includes('完成')) return 'green';
  return 'blue';
}

function inventoryTone(status: string): Tone {
  if (status.includes('冻结') || status.includes('返工')) return 'red';
  if (status.includes('在制')) return 'violet';
  if (status.includes('待')) return 'amber';
  return 'green';
}

const activeInbound = inboundOrders[0];
const activeWorkOrder = workOrders.find((order) => order.status.includes('称重')) ?? workOrders[0];
const activeQuality = qualityCases[0];
const activeInventory = inventoryBalances[1] ?? inventoryBalances[0];

const shiftSignals = [
  { label: '入库阻断', value: '2', hint: '物料确认 / 视频失败', tone: 'amber' },
  { label: '生产瓶颈', value: '5', hint: '镶石工位积压', tone: 'red' },
  { label: '质检积压', value: '7', hint: '最早 09:40 提交', tone: 'amber' },
  { label: '出库风险', value: '0', hint: '追溯链路可核验', tone: 'green' },
] as const;

const consoleRows = [
  ...inboundOrders.map((order) => ({
    code: order.code,
    type: '来料母单',
    customer: order.customer,
    material: order.material,
    node: order.status,
    owner: '库管员 何琳',
    blocker: order.video === '失败' ? '视频截取失败' : order.video,
    next: order.video === '失败' ? '人工上传' : '收货处理',
    tone: inboundTone(order.status, order.video),
  })),
  ...workOrders.map((order) => ({
    code: order.code,
    type: '生产工单',
    customer: order.customer,
    material: order.material,
    node: order.process,
    owner: order.assignee,
    blocker: order.status,
    next: order.status.includes('称重') ? '补称重' : '报工',
    tone: workOrderTone(order.status),
  })),
  ...qualityCases.map((item) => {
    const order = workOrders.find((workOrder) => workOrder.code === item.workOrder) ?? activeWorkOrder;
    return {
      code: item.code,
      type: '质检记录',
      customer: order.customer,
      material: order.material,
      node: item.process,
      owner: item.inspector,
      blocker: item.status,
      next: item.action,
      tone: qualityTone(item.status),
    };
  }),
  ...inventoryBalances.map((item) => ({
    code: item.batch,
    type: '库存批次',
    customer: item.owner,
    material: item.material,
    node: item.location,
    owner: '库存服务',
    blocker: item.status,
    next: item.frozen === '0' ? '查看台账' : '处理冻结',
    tone: inventoryTone(item.status),
  })),
];

const controlSteps = [
  { index: '01', title: '扫码收货', detail: '绑定快递单、客户、物料档案', state: '2 单待处理', tone: 'amber' },
  { index: '02', title: '拆包称重', detail: '记录拆包前后重量与视频时间段', state: '1 单视频失败', tone: 'red' },
  { index: '03', title: '子工单', detail: '按工序模板拆分并打印标签', state: '11 张已生成', tone: 'blue' },
  { index: '04', title: '工序报工', detail: '扫码确认投入、产出、计件快照', state: '18 张在制', tone: 'violet' },
  { index: '05', title: '质检放行', detail: '缺陷、返工、损耗审批闭环', state: '7 批待检', tone: 'amber' },
] as const;

const evidenceMatrix = [
  { label: '快递单照片', status: '已上传', link: activeInbound.courierNo, tone: 'green' },
  { label: '拆包视频', status: activeInbound.video, link: 'NVR 08:42-09:05', tone: activeInbound.video === '已关联' ? 'green' : 'amber' },
  { label: '称重交接', status: productionHandoffs[2]?.status ?? '待确认', link: productionHandoffs[2]?.code ?? 'HO-待生成', tone: 'red' },
  { label: '缺陷照片', status: '5 张', link: activeQuality.code, tone: 'blue' },
  { label: '库存台账', status: '只追加', link: activeInventory.batch, tone: 'green' },
] as const;

const approvalDock = [
  { title: '超标损耗审批', code: 'LOSS-20260706-009', owner: '车间组长', sla: '18 分钟', tone: 'red' },
  { title: '配置阈值变更', code: 'CFG-CR-20260706-001', owner: '生产负责人', sla: '今日 16:40', tone: 'amber' },
  { title: '视频人工上传', code: 'VID-20260706-004', owner: '库管员', sla: '34 分钟', tone: 'amber' },
] as const;

const boundaryCards = [
  { label: '真实后端', value: '库存台账 JDBC', detail: 'M3.1 已具备 MySQL profile 与仓储测试', tone: 'green' },
  { label: '评审样例', value: '业务工作台数据', detail: '仍以固定样例和内存 API 兜底为主', tone: 'amber' },
  { label: '硬件策略', value: `${getHardwareManualFallbackCount()} 项需兜底`, detail: 'NVR、标签、扫码、电子秤按兼容方案推进', tone: 'blue' },
] as const;
</script>

<template>
  <div class="page-stack cockpit-page ops-dashboard">
    <section class="ops-command-center">
      <div class="ops-command-copy">
        <span class="eyebrow">Production Control</span>
        <h2>今日生产追溯指挥台</h2>
        <p>优先暴露会影响入库、报工、质检和出库的阻断项，不再把系统做成只有指标和按钮的静态页面。</p>
        <div class="command-actions">
          <button class="primary-button" type="button">处理最高风险</button>
          <button class="secondary-button" type="button">打开追溯链路</button>
          <span><i class="live-dot" />车间实时评审视图</span>
        </div>
      </div>
      <div class="ops-command-kpis">
        <article v-for="signal in shiftSignals" :key="signal.label" :class="`tone-${signal.tone}`">
          <span>{{ signal.label }}</span>
          <strong>{{ signal.value }}</strong>
          <small>{{ signal.hint }}</small>
        </article>
      </div>
    </section>

    <section class="ops-layout-grid">
      <div class="ops-main-panel">
        <div class="console-toolbar">
          <div>
            <span class="eyebrow">Live Operations</span>
            <h2>生产运营主控台</h2>
            <p>按单据、工序、责任人和阻断原因组织操作入口，避免只看摘要指标。</p>
          </div>
          <div class="console-tools">
            <label>
              <span>业务对象</span>
              <select>
                <option>全部单据</option>
                <option>只看来料母单</option>
                <option>只看生产工单</option>
                <option>只看质检阻断</option>
              </select>
            </label>
            <label>
              <span>关键字</span>
              <input value="MD-20260706-001" aria-label="追溯关键字" />
            </label>
            <button class="primary-button" type="button">追溯查询</button>
          </div>
        </div>

        <div class="erp-table-wrap">
          <table class="erp-table">
            <thead>
              <tr>
                <th>业务单号</th>
                <th>类型</th>
                <th>客户/物料</th>
                <th>当前节点</th>
                <th>责任人</th>
                <th>阻断/状态</th>
                <th>下一动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in consoleRows" :key="row.code">
                <td><strong>{{ row.code }}</strong></td>
                <td>{{ row.type }}</td>
                <td>
                  <span class="cell-stack">
                    <b>{{ row.customer }}</b>
                    <small>{{ row.material }}</small>
                  </span>
                </td>
                <td>{{ row.node }}</td>
                <td>{{ row.owner }}</td>
                <td><StatusBadge :label="row.blocker" :tone="row.tone" /></td>
                <td><button class="table-action" type="button">{{ row.next }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside class="ops-focus-panel">
        <div class="detail-title">
          <span>当前焦点工单</span>
          <strong>{{ activeWorkOrder.code }}</strong>
          <StatusBadge :label="activeWorkOrder.status" :tone="workOrderTone(activeWorkOrder.status)" />
        </div>
        <dl class="detail-grid">
          <dt>客户</dt>
          <dd>{{ activeWorkOrder.customer }}</dd>
          <dt>物料</dt>
          <dd>{{ activeWorkOrder.material }}</dd>
          <dt>当前工序</dt>
          <dd>{{ activeWorkOrder.process }}</dd>
          <dt>负责人</dt>
          <dd>{{ activeWorkOrder.assignee }}</dd>
          <dt>产出进度</dt>
          <dd>{{ activeWorkOrder.finishedQty }}/{{ activeWorkOrder.plannedQty }} 件</dd>
          <dt>库存归属</dt>
          <dd>客户所有，在制库存</dd>
        </dl>
        <div class="detail-actions">
          <button class="primary-button" type="button">扫码报工</button>
          <button class="secondary-button" type="button">称重交接</button>
          <button class="secondary-button" type="button">查看证据</button>
        </div>
      </aside>
    </section>

    <section class="operations-dock ops-process-dock" aria-label="核心作业入口">
      <article v-for="step in controlSteps" :key="step.index" :class="`tone-${step.tone}`">
        <span>{{ step.index }}</span>
        <div>
          <strong>{{ step.title }}</strong>
          <small>{{ step.detail }}</small>
        </div>
        <StatusBadge :label="step.state" :tone="step.tone" />
      </article>
    </section>

    <section class="metric-grid ops-metric-grid" aria-label="今日业务指标">
      <MetricCard v-for="metric in metrics" :key="metric.label" :metric="metric" />
    </section>

    <section class="ops-three-column">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>现场待办</h2>
            <p>按阻断程度排序，先处理会影响入库、报工、质检和出库的事项。</p>
          </div>
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
            <h2>证据链健康</h2>
            <p>每个关键动作都必须落到附件、视频、称重、台账或审批记录。</p>
          </div>
        </div>
        <div class="evidence-matrix">
          <article v-for="item in evidenceMatrix" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.link }}</span>
            </div>
            <StatusBadge :label="item.status" :tone="item.tone" />
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>审批与兜底队列</h2>
            <p>硬件失败、损耗超标、配置变更必须有责任人和时限。</p>
          </div>
        </div>
        <div class="approval-dock">
          <article v-for="item in approvalDock" :key="item.code">
            <StatusBadge :label="item.sla" :tone="item.tone" />
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.code }} / {{ item.owner }}</span>
            </div>
          </article>
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
            <StatusBadge :label="order.status" :tone="workOrderTone(order.status)" />
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>工位负载</h2>
            <p>把瓶颈直接暴露给排产和质检，而不是留在口头沟通里。</p>
          </div>
        </div>
        <div class="station-stack">
          <article v-for="station in productionStationLoads" :key="station.station" :class="`tone-${station.tone}`">
            <div>
              <strong>{{ station.station }}</strong>
              <span>{{ station.bottleneck }}</span>
            </div>
            <StatusBadge :label="`${station.queue} / ${station.capacity}`" :tone="station.tone" />
          </article>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>当前边界说明</h2>
          <p>这里明确哪些已经进入真实实现，哪些仍是样例评审数据，避免把演示页面误当成交付系统。</p>
        </div>
      </div>
      <div class="owner-grid ops-boundary-grid">
        <article v-for="item in boundaryCards" :key="item.label" :class="`tone-${item.tone}`">
          <div>
            <strong>{{ item.label }}：{{ item.value }}</strong>
            <span>{{ item.detail }}</span>
          </div>
          <StatusBadge :label="item.tone === 'green' ? '已推进' : item.tone === 'amber' ? '待迁移' : '规划中'" :tone="item.tone" />
        </article>
      </div>
    </section>
  </div>
</template>
