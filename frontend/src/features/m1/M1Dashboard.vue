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

const commandSummaryTiles = [
  { label: '今日来料', value: '6', unit: '单', detail: '2 单需确认客户/物料', tone: 'amber' },
  { label: '在制工单', value: '18', unit: '张', detail: '5 张停留超过 4 小时', tone: 'red' },
  { label: '质检队列', value: '7', unit: '批', detail: '2 批返工复检', tone: 'amber' },
  { label: '可出库', value: '4', unit: '单', detail: '追溯链路可核验', tone: 'green' },
] as const;

const projectRealityRows = [
  {
    area: '文档与业务设计',
    done: '已形成 00-17 多文档体系，覆盖开源调研、PRD、流程、领域模型、架构、硬件、接口、验收和样例数据来源。',
    evidence: 'docs/00-17 + 优化版需求文档',
    gap: '还需要用现场真实单据继续压测流程口径。',
    tone: 'green',
  },
  {
    area: '后端业务闭环',
    done: '已接来料入库、生产报工、质检冻结、返工派发、返工报工、复检放行、出库、客户退货、损耗审批试算和轻量审批任务。',
    evidence: 'Spring Boot API + 库存台账服务',
    gap: '真实权限、复杂流程编排、售后复核和正式工资锁账还没有完成。',
    tone: 'amber',
  },
  {
    area: '验收样例',
    done: '新增 JSON fixture 驱动的真实链路测试，验证同一客户/母单/工单/物料维度下的台账流转。',
    evidence: 'real-flow-scenario-20260706.json',
    gap: '目前只有单份脱敏样例，还不是批量真实数据回归。',
    tone: 'amber',
  },
  {
    area: '前端页面',
    done: '已有来料、生产、质检、库存、配置、文档、样例、硬件、PWA 等评审入口，并能调用部分真实业务 API。',
    evidence: 'Vue 3 + PWA + Vitest',
    gap: '之前界面偏 demo，内容表达不够系统，本轮开始按 ERP/MES 工作台重构。',
    tone: 'red',
  },
  {
    area: '硬件与证据',
    done: '已设计 NVR/视频截取、扫码、标签、电子秤兜底和附件归档路径。',
    evidence: '硬件 POC 页面 + 文档附件 API',
    gap: 'MinIO/S3、真实 NVR、打印机和电子秤现场联调仍未完成。',
    tone: 'amber',
  },
] as const;

const deliveryGaps = [
  { title: '真实权限与复杂审批流', detail: '轻量审批任务 API 已接，但真实登录、角色授权、流程编排、审批 UI 和审计持久化还没有完成。', tone: 'red' },
  { title: '正式工资发放', detail: '损耗审批只生成工资试算快照，不进入正式工资单、锁账或发放。', tone: 'red' },
  { title: '对象存储与文件签名', detail: '附件元数据、checksum 和预览 URL 已有基线，MinIO/S3 实际落盘未接。', tone: 'amber' },
  { title: '售后复核闭环', detail: '客户退货已能写台账，但退货后的售后质检、返工、报废和客户确认仍需补齐。', tone: 'amber' },
] as const;

const commandRows = [
  { scene: '来料入库', api: 'POST /api/business/operations/inbound-confirmations', state: '写 RAW_WAITING 台账', tone: 'green' },
  { scene: '生产报工', api: 'POST /api/business/operations/production-reports', state: 'WIP 转半成品', tone: 'green' },
  { scene: '质检冻结', api: 'POST /api/business/operations/quality-freezes', state: '冻结库存', tone: 'green' },
  { scene: '返工派发', api: 'POST /api/business/operations/rework-dispatches', state: '单状态机返工', tone: 'green' },
  { scene: '返工报工', api: 'POST /api/business/operations/rework-reports', state: '返工产出回半成品', tone: 'green' },
  { scene: '复检放行', api: 'POST /api/business/operations/quality-releases', state: '半成品转成品', tone: 'green' },
  { scene: '成品出库', api: 'POST /api/business/operations/outbound-shipments', state: 'FINISHED 转 SHIPPED', tone: 'green' },
  { scene: '客户退货', api: 'POST /api/business/operations/customer-returns', state: '退货入 RETURN-CHECK', tone: 'amber' },
  { scene: '损耗审批', api: 'POST /api/business/operations/loss-approvals', state: '工资试算 + 轻量审批', tone: 'amber' },
] as const;

const dispatchRows = [
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

const dispatchPreviewRows = dispatchRows.slice(0, 10);

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

const chainStages = [
  { index: '01', title: '客户来料', state: '可操作', detail: '确认入库写 RAW_WAITING 台账', tone: 'green' },
  { index: '02', title: '拆包称重', state: '样例', detail: '称重和视频证据仍是评审数据', tone: 'amber' },
  { index: '03', title: '子工单', state: '样例', detail: '工序模板和标签待接真实业务表', tone: 'amber' },
  { index: '04', title: '生产报工', state: '可操作', detail: 'WIP 转 SEMI_FINISHED 台账', tone: 'green' },
  { index: '05', title: '质检返工', state: '可操作', detail: '冻结、返工、返工报工、复检放行已接台账', tone: 'green' },
  { index: '06', title: '出库退货', state: '部分可操作', detail: 'FINISHED 转 SHIPPED，SHIPPED 退回 RETURN-CHECK 已接', tone: 'green' },
] as const;

const readinessRows = [
  { module: '来料母单', api: '写账 API 已接', ui: '主表 + 详情 + 入库动作', data: '样例 + 内存兜底', status: '可演示验收' },
  { module: '生产工单', api: '报工 API 已接', ui: '派工 + 报工 + 交接', data: '样例 + JDBC 快照', status: '可演示验收' },
  { module: '质检返工', api: '冻结/返工/复检/损耗 API 已接', ui: '质检队列 + 库存动作校验 + 工资试算', data: '样例 + 真实台账', status: '待正式审批流' },
  { module: '库存追溯', api: '台账读写、出库、退货 API 已接', ui: '余额 + 台账 + 出库 + 退货 + 证据', data: '真实台账 + 样例', status: '待售后复核分流' },
  { module: '配置/构型', api: '只读基线 API', ui: '参数 + 权限 + 版本', data: 'seed 数据', status: '待审批写入' },
  { module: '文档附件', api: '文档包/附件/上传/预览 API 已接', ui: '文档包 + 上传测试 + 审计', data: '样例 + 内存上传', status: '待对象存储' },
  { module: '硬件联动', api: 'POC 记录 API', ui: '设备矩阵 + 兜底任务', data: 'POC 样例', status: '待现场复测' },
] as const;
</script>

<template>
  <div class="page-stack ops-dashboard review-dashboard">
    <section class="mission-control-v2" aria-label="来料加工生产运营台">
      <div class="mission-primary-panel">
        <div class="console-title-line">
          <span class="eyebrow">M3.22 Review Console</span>
          <StatusBadge label="评审原型，距离交付还有差距" tone="amber" />
        </div>
        <h2>来料加工生产运营台</h2>
        <p>
          你看到的前端目前还不是完整交付系统。实际完成的工作更多在需求文档、业务模型、后端台账、接口和验收测试上；
          本轮开始把这些能力显式放回首页，让页面能说明项目真实状态，而不是只有几个简单入口。
        </p>
        <div class="summary-tile-grid" aria-label="今日运行摘要">
          <article v-for="tile in commandSummaryTiles" :key="tile.label" :class="`tone-${tile.tone}`">
            <span>{{ tile.label }}</span>
            <strong>{{ tile.value }}<small>{{ tile.unit }}</small></strong>
            <em>{{ tile.detail }}</em>
          </article>
        </div>
      </div>

      <aside class="delivery-verdict-panel">
        <span class="eyebrow">Project Verdict</span>
        <strong>可以继续做 POC，不能说已经可开发交付</strong>
        <p>前端需要从评审页升级为真实作业台；后端还要补正式审批、权限、对象存储、售后复核和真实数据聚合。</p>
        <div class="verdict-meter">
          <span>业务闭环</span><b style="--value: 64%" />
          <span>前端可用性</span><b style="--value: 38%" />
          <span>交付完整度</span><b style="--value: 42%" />
        </div>
      </aside>
    </section>

    <section class="project-reality-board" aria-label="项目真实进展">
      <div class="review-ledger-head">
        <div>
          <span class="eyebrow">Implementation Ledger</span>
          <h2>我目前实际做到了什么</h2>
          <p>把“已经验证的能力”和“仍然只是样例或规划的部分”拆开，避免把评审页面包装成完整系统。</p>
        </div>
        <StatusBadge label="已提交验收 fixture" tone="blue" />
      </div>
      <div class="project-reality-grid">
        <article v-for="row in projectRealityRows" :key="row.area" :class="`tone-${row.tone}`">
          <div>
            <strong>{{ row.area }}</strong>
            <p>{{ row.done }}</p>
          </div>
          <dl>
            <dt>证据</dt>
            <dd>{{ row.evidence }}</dd>
            <dt>缺口</dt>
            <dd>{{ row.gap }}</dd>
          </dl>
        </article>
      </div>
    </section>

    <section class="operator-workbench-grid" aria-label="业务调度台">
      <div class="dispatch-table-panel">
        <div class="console-toolbar refined-toolbar">
          <div>
            <span class="eyebrow">Live Dispatch</span>
            <h2>业务调度台</h2>
            <p>按单据、工序、责任人和阻断原因组织，业务方一眼能看到今天该处理什么。</p>
          </div>
          <div class="console-tools">
            <label>
              <span>对象</span>
              <select>
                <option>全部单据</option>
                <option>只看来料母单</option>
                <option>只看生产工单</option>
                <option>只看质检阻断</option>
              </select>
            </label>
            <label>
              <span>追溯</span>
              <input value="MD-20260706-001" aria-label="追溯关键字" />
            </label>
            <button class="primary-button" type="button">查询</button>
          </div>
        </div>

        <div class="erp-table-wrap refined-table-wrap">
          <table class="erp-table dispatch-table">
            <thead>
              <tr>
                <th>业务单号</th>
                <th>对象/客户</th>
                <th>当前节点</th>
                <th>责任人</th>
                <th>状态</th>
                <th>下一动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in dispatchPreviewRows" :key="row.code">
                <td><strong>{{ row.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ row.type }} / {{ row.customer }}</b>
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

      <aside class="right-command-column">
        <section class="focus-order-panel">
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
            <dt>工序</dt>
            <dd>{{ activeWorkOrder.process }}</dd>
            <dt>责任人</dt>
            <dd>{{ activeWorkOrder.assignee }}</dd>
            <dt>产出</dt>
            <dd>{{ activeWorkOrder.finishedQty }}/{{ activeWorkOrder.plannedQty }} 件</dd>
          </dl>
          <div class="detail-actions compact-actions">
            <button class="primary-button" type="button">扫码报工</button>
            <button class="secondary-button" type="button">称重交接</button>
          </div>
        </section>

        <section class="command-api-panel">
          <div class="panel-kicker">
            <span>已接业务命令</span>
            <strong>真实命令 / 样例数据分层</strong>
          </div>
          <article v-for="item in commandRows" :key="item.scene" :class="`tone-${item.tone}`">
            <div>
              <strong>{{ item.scene }}</strong>
              <span>{{ item.api }}</span>
            </div>
            <StatusBadge :label="item.state" :tone="item.tone" />
          </article>
        </section>
      </aside>
    </section>

    <section class="compact-process-strip" aria-label="一期核心作业入口">
      <article v-for="step in controlSteps" :key="step.index" :class="`tone-${step.tone}`">
        <b>{{ step.index }}</b>
        <div>
          <strong>{{ step.title }}</strong>
          <span>{{ step.detail }}</span>
        </div>
        <StatusBadge :label="step.state" :tone="step.tone" />
      </article>
    </section>

    <section class="evidence-risk-grid" aria-label="证据与阻断">
      <div class="section-block refined-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Blocking Queue</span>
            <h2>现场阻断队列</h2>
            <p>先处理会影响入库、报工、质检和出库的事项。</p>
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

      <div class="section-block refined-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Evidence Health</span>
            <h2>证据链健康度</h2>
            <p>每个关键动作必须落到附件、视频、称重、台账或审批记录。</p>
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

      <div class="section-block refined-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Open Gaps</span>
            <h2>还不具备交付条件</h2>
            <p>这些不是美化页面能解决的，需要继续按计划补后端和现场 POC。</p>
          </div>
        </div>
        <div class="gap-stack">
          <article v-for="gap in deliveryGaps" :key="gap.title" :class="`tone-${gap.tone}`">
            <strong>{{ gap.title }}</strong>
            <span>{{ gap.detail }}</span>
          </article>
        </div>
      </div>
    </section>

    <section class="ops-stage-map-panel" aria-label="一期业务闭环状态">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Business Chain</span>
          <h2>一期业务闭环状态图</h2>
          <p>真实开发必须沿着这条链路推进，不再继续只做孤立页面。</p>
        </div>
      </div>
      <div class="ops-stage-map">
        <article v-for="stage in chainStages" :key="stage.index" :class="`stage-node tone-${stage.tone}`">
          <span>{{ stage.index }}</span>
          <div>
            <strong>{{ stage.title }}</strong>
            <small>{{ stage.detail }}</small>
          </div>
          <StatusBadge :label="stage.state" :tone="stage.tone" />
        </article>
      </div>
    </section>

    <section class="readiness-board" aria-label="模块完成度台账">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Module Readiness</span>
          <h2>模块完成度台账</h2>
          <p>用 API、页面、数据来源和状态四个维度说明，不把样例页面包装成已完成系统。</p>
        </div>
      </div>
      <div class="readiness-table-wrap">
        <table class="readiness-table">
          <thead>
            <tr>
              <th>模块</th>
              <th>接口状态</th>
              <th>页面状态</th>
              <th>数据来源</th>
              <th>结论</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in readinessRows" :key="row.module">
              <td><strong>{{ row.module }}</strong></td>
              <td>{{ row.api }}</td>
              <td>{{ row.ui }}</td>
              <td>{{ row.data }}</td>
              <td>{{ row.status }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="metric-grid ops-metric-grid" aria-label="今日业务指标">
      <MetricCard v-for="metric in metrics" :key="metric.label" :metric="metric" />
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
            <span>责任人</span>
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
          <p>这些边界会继续保留在页面里，避免把演示页面误当成交付系统。</p>
        </div>
      </div>
      <div class="owner-grid ops-boundary-grid">
        <article class="tone-green">
          <div>
            <strong>真实后端：库存台账 JDBC</strong>
            <span>MySQL profile、JDBC 仓储测试和同一客户/母单/工单/物料维度验收测试已补齐。</span>
          </div>
          <StatusBadge label="已推进" tone="green" />
        </article>
        <article class="tone-amber">
          <div>
            <strong>评审样例：业务工作台数据</strong>
            <span>页面仍以固定样例和内存 API 兜底为主，需要迁移到真实业务聚合查询。</span>
          </div>
          <StatusBadge label="待迁移" tone="amber" />
        </article>
        <article class="tone-blue">
          <div>
            <strong>硬件策略：{{ getHardwareManualFallbackCount() }} 项需要兜底</strong>
            <span>NVR、标签、扫码、电子秤按兼容方案推进，一期不能被强硬件集成卡死。</span>
          </div>
          <StatusBadge label="POC 中" tone="blue" />
        </article>
        <article class="tone-red">
          <div>
            <strong>超标损耗审批：试算快照，不是正式工资</strong>
            <span>POST /loss-approvals 可以计算损耗扣减、生成工资试算和轻量审批任务，但不代表正式工资发放。</span>
          </div>
          <StatusBadge label="未交付" tone="red" />
        </article>
      </div>
    </section>
  </div>
</template>
