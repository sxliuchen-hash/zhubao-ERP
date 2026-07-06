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

const implementationLanes = [
  {
    code: 'DOC',
    title: '文档与设计',
    value: '10+2 份',
    detail: '需求、流程、领域模型、接口、硬件、配置和进度文档已成体系。',
    state: '可评审',
    tone: 'green',
  },
  {
    code: 'API',
    title: '真实业务动作',
    value: '6 条',
    detail: '来料确认入库、生产报工、质检冻结、返工派发、返工报工、复检放行已能写入库存台账。',
    state: '部分可验',
    tone: 'amber',
  },
  {
    code: 'UI',
    title: '作业页面',
    value: '10 个模块',
    detail: '来料、生产、质检、库存、配置、文档附件、样例、硬件和 PWA 已有评审入口。',
    state: '需深化',
    tone: 'amber',
  },
  {
    code: 'HW',
    title: '软硬件联动',
    value: '4 类 POC',
    detail: '视频、标签、扫码、称重按兼容方案记录兜底路径。',
    state: 'POC',
    tone: 'blue',
  },
] as const;

const deliveryGaps = [
  { title: '工资口径 / 损耗审批', detail: '复检放行已写台账，但工资试算、超标损耗审批和审批流仍未闭环。', tone: 'red' },
  { title: '出库 / 退货 / 完工', detail: '尚未形成成品冻结、发货和客户退料闭环。', tone: 'amber' },
  { title: '权限 / 审批 / 审计', detail: '已有字段和页面占位，未接入真实登录、角色和审批流。', tone: 'amber' },
  { title: '对象存储 / 版本锁定', detail: '附件上传、checksum 和预览 URL 已接，MinIO/S3、签名 URL 和审批锁定仍未完成。', tone: 'blue' },
] as const;

const chainStages = [
  { index: '01', title: '客户来料', state: '可操作', detail: '确认入库写 RAW_WAITING 台账', tone: 'green' },
  { index: '02', title: '拆包称重', state: '样例', detail: '称重和视频证据仍是评审数据', tone: 'amber' },
  { index: '03', title: '子工单', state: '样例', detail: '工序模板和标签待接真实业务表', tone: 'amber' },
  { index: '04', title: '生产报工', state: '可操作', detail: 'WIP 转 SEMI_FINISHED 台账', tone: 'green' },
  { index: '05', title: '质检返工', state: '可操作', detail: '冻结、返工、返工报工、复检放行已接台账', tone: 'green' },
  { index: '06', title: '出库追溯', state: '待开发', detail: '成品冻结、发货和退货未完成', tone: 'red' },
] as const;

const readinessRows = [
  { module: '来料母单', api: '写账 API 已接', ui: '主表 + 详情 + 入库动作', data: '样例 + 内存兜底', status: '可演示验收' },
  { module: '生产工单', api: '报工 API 已接', ui: '派工 + 报工 + 交接', data: '样例 + JDBC 快照', status: '可演示验收' },
  { module: '质检返工', api: '冻结/返工派发/返工报工/复检放行 API 已接', ui: '质检队列 + 库存动作核验', data: '样例 + 真实台账', status: '待审批与工资口径' },
  { module: '库存追溯', api: '台账读写已具备', ui: '余额 + 台账 + 证据', data: '真实台账 + 样例', status: '继续聚合' },
  { module: '配置/构型', api: '只读基线 API', ui: '参数 + 权限 + 版本', data: 'seed 数据', status: '待审批写入' },
  { module: '文档附件', api: '文档包/附件/上传/预览 API 已接', ui: '文档包 + 上传测试 + 审计', data: '样例 + 内存上传', status: '待对象存储' },
  { module: '硬件联动', api: 'POC 记录 API', ui: '设备矩阵 + 兜底任务', data: 'POC 样例', status: '待现场复测' },
] as const;
</script>

<template>
  <div class="page-stack cockpit-page ops-dashboard">
    <section class="ops-command-center">
      <div class="ops-command-copy">
        <span class="eyebrow">Delivery Review</span>
        <h2>当前不是完整交付系统，是可运行评审原型</h2>
        <p>已完成工程基线、文档包、样例工作台、库存台账、来料入库、生产报工、质检冻结、返工派发、返工报工、文档附件上传和预览 URL 基线；复检放行、出库、审批权限、对象存储和硬件闭环还需要继续开发。</p>
        <div class="command-actions">
          <button class="primary-button" type="button">查看真实闭环</button>
          <button class="secondary-button" type="button">查看待开发缺口</button>
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

    <section class="review-ledger-board" aria-label="当前实现进度">
      <div class="review-ledger-main">
        <div class="review-ledger-head">
          <div>
            <span class="eyebrow">Implementation Ledger</span>
            <h2>我目前实际做到了什么</h2>
            <p>把“看起来像页面”和“已经能验收的能力”拆开，评审时按这张表判断是否可以进入下一阶段。</p>
          </div>
          <StatusBadge label="M3.12 基线" tone="blue" />
        </div>
        <div class="review-lane-grid">
          <article v-for="lane in implementationLanes" :key="lane.code" :class="`review-lane tone-${lane.tone}`">
            <span>{{ lane.code }}</span>
            <div>
              <strong>{{ lane.title }}</strong>
              <b>{{ lane.value }}</b>
              <small>{{ lane.detail }}</small>
            </div>
            <StatusBadge :label="lane.state" :tone="lane.tone" />
          </article>
        </div>
      </div>

      <aside class="delivery-gap-panel">
        <div class="review-ledger-head compact">
          <div>
            <span class="eyebrow">Open Gaps</span>
            <h2>还不能说完成的部分</h2>
          </div>
        </div>
        <div class="gap-stack">
          <article v-for="gap in deliveryGaps" :key="gap.title" :class="`tone-${gap.tone}`">
            <strong>{{ gap.title }}</strong>
            <span>{{ gap.detail }}</span>
          </article>
        </div>
      </aside>
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
