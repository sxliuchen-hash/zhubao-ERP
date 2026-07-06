<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
import {
  getHardwareManualFallbackCount,
  getOpenExceptionCount,
  inboundOrders,
  inventoryBalances,
  metrics,
  productionHandoffs,
  productionStationLoads,
  qualityCases,
  traceEvidence,
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

const activeWorkOrder = workOrders.find((order) => order.status.includes('称重')) ?? workOrders[0];
const activeQuality = qualityCases[0];
const activeInventory = inventoryBalances[1] ?? inventoryBalances[0];
const handoffBlocker = productionHandoffs.find((handoff) => handoff.tone === 'red') ?? productionHandoffs[0];

const shiftKpis = [
  { label: '今日来料', value: '6', unit: '单', detail: '2 单待确认客户/物料', tone: 'amber' },
  { label: '在制工单', value: '18', unit: '张', detail: '5 张超过 4 小时', tone: 'red' },
  { label: '质检队列', value: '7', unit: '批', detail: '2 批返工复检', tone: 'amber' },
  { label: '证据完整率', value: '92', unit: '%', detail: '1 个视频任务失败', tone: 'green' },
] as const;

const operationLanes = [
  {
    code: 'IN',
    title: '来料收货',
    value: `${inboundOrders.length} 单`,
    status: '优先确认客户所有权、快递单和拆包视频',
    action: '扫码收货',
    tone: 'amber',
  },
  {
    code: 'MO',
    title: '生产报工',
    value: `${workOrders.length} 张`,
    status: '称重待补会阻断进入下一工序',
    action: '扫码报工',
    tone: 'red',
  },
  {
    code: 'QC',
    title: '质检返工',
    value: `${qualityCases.length} 批`,
    status: '返工、复检和损耗必须共享一套状态机',
    action: '处理质检',
    tone: 'amber',
  },
  {
    code: 'ST',
    title: '库存追溯',
    value: `${inventoryBalances.length} 批`,
    status: '按客户、批次、工单、状态解释每次数量变化',
    action: '查台账',
    tone: 'green',
  },
] as const;

const dispatchRows = [
  ...inboundOrders.map((order) => ({
    code: order.code,
    object: `来料母单 / ${order.customer}`,
    material: order.material,
    node: order.status,
    owner: '库管员 何琳',
    status: order.video === '失败' ? '视频截取失败' : order.video,
    action: order.video === '失败' ? '人工上传' : '收货处理',
    tone: inboundTone(order.status, order.video),
  })),
  ...workOrders.map((order) => ({
    code: order.code,
    object: `生产工单 / ${order.customer}`,
    material: order.material,
    node: order.process,
    owner: order.assignee,
    status: order.status,
    action: order.status.includes('称重') ? '补称重' : '报工',
    tone: workOrderTone(order.status),
  })),
  ...qualityCases.map((item) => {
    const order = workOrders.find((workOrder) => workOrder.code === item.workOrder) ?? activeWorkOrder;
    return {
      code: item.code,
      object: `质检记录 / ${order.customer}`,
      material: order.material,
      node: item.process,
      owner: item.inspector,
      status: item.status,
      action: item.action,
      tone: qualityTone(item.status),
    };
  }),
  ...inventoryBalances.map((item) => ({
    code: item.batch,
    object: `库存批次 / ${item.owner}`,
    material: item.material,
    node: item.location,
    owner: '库存服务',
    status: item.status,
    action: item.frozen === '0' ? '查台账' : '处理冻结',
    tone: inventoryTone(item.status),
  })),
].slice(0, 12);

const commandRows = [
  { scene: '来料入库', api: 'POST /api/business/operations/inbound-confirmations', state: '写 RAW_WAITING 台账', tone: 'green' },
  { scene: '生产报工', api: 'POST /api/business/operations/production-reports', state: 'WIP 转半成品', tone: 'green' },
  { scene: '质检冻结', api: 'POST /api/business/operations/quality-freezes', state: '冻结库存', tone: 'green' },
  { scene: '返工派发', api: 'POST /api/business/operations/rework-dispatches', state: '冻结转返工在制', tone: 'green' },
  { scene: '返工报工', api: 'POST /api/business/operations/rework-reports', state: '返工回半成品', tone: 'green' },
  { scene: '复检放行', api: 'POST /api/business/operations/quality-releases', state: '半成品转成品', tone: 'green' },
  { scene: '成品出库', api: 'POST /api/business/operations/outbound-shipments', state: 'FINISHED 转 SHIPPED', tone: 'green' },
  { scene: '客户退货', api: 'POST /api/business/operations/customer-returns', state: '退货复核库存', tone: 'amber' },
  { scene: '损耗审批', api: 'POST /api/business/operations/loss-approvals', state: '工资试算 + 轻量审批任务', tone: 'amber' },
] as const;

const readinessRows = [
  { module: '来料母单', api: '写账 API 已接', ui: '收货、拆包、证据入口已成型', data: '样例 + 内存兜底', status: '可演示验收' },
  { module: '生产工单', api: '报工 API 已接', ui: '派工、报工、交接面板已成型', data: '样例 + JDBC 快照', status: '可演示验收' },
  { module: '质检返工', api: '冻结/返工/复检/损耗 API 已接', ui: '队列、冻结、返工和工资试算入口已成型', data: '样例 + 真实台账', status: '待正式审批流' },
  { module: '库存追溯', api: '台账读写、出库、退货 API 已接', ui: '余额、台账、出库、退货和证据已成型', data: '真实台账 + 样例', status: '待售后复核分流' },
  { module: '文档附件', api: '上传、预览、审计 API 已接', ui: '文档包和上传测试台已成型', data: '样例 + 内存上传', status: '待对象存储' },
  { module: '硬件联动', api: 'POC 记录 API 已接', ui: '设备矩阵和兜底任务已成型', data: 'POC 样例', status: '待现场复测' },
] as const;

const openSourceReferences = [
  { name: 'ERPNext/Frappe', use: '参考工单、工序、Job Card 和库存转移结构', fit: '适合 POC 对照制造闭环' },
  { name: 'Odoo Manufacturing', use: '参考制造订单、质量检查和工作中心组织方式', fit: '适合作业台信息架构对照' },
  { name: 'InvenTree', use: '参考批次、库存位置、零件和追溯逻辑', fit: '适合库存/条码模型对照' },
] as const;
</script>

<template>
  <div class="m3-ops-board page-stack">
    <section class="shift-command-center" aria-label="白班生产作业台">
      <div class="shift-command-main">
        <div class="console-title-line">
          <span class="eyebrow">M3.23 Shopfloor Console</span>
          <StatusBadge label="从评审页转向真实作业台" tone="blue" />
        </div>
        <h2>白班生产作业台</h2>
        <p>
          首页不再只解释项目进展，而是把今天必须处理的单据、阻断、证据和真实 API 能力放到同一张作业桌上。
          当前仍是评审/POC 阶段，但页面结构开始按车间实际操作推进。
        </p>
        <div class="shift-kpi-grid" aria-label="白班关键指标">
          <article v-for="item in shiftKpis" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>
      </div>

      <aside class="priority-rail">
        <div>
          <span class="eyebrow">Priority</span>
          <strong>今天先处理这些阻断</strong>
        </div>
        <article v-for="item in workQueues.slice(0, 3)" :key="item.id" :class="`tone-${severityTone(item.severity)}`">
          <StatusBadge :label="severityLabel(item.severity)" :tone="severityTone(item.severity)" />
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.subtitle }}</span>
          </div>
        </article>
      </aside>
    </section>

    <section class="operation-lane-grid" aria-label="一期作业流">
      <article v-for="lane in operationLanes" :key="lane.code" :class="`tone-${lane.tone}`">
        <b>{{ lane.code }}</b>
        <div>
          <span>{{ lane.title }}</span>
          <strong>{{ lane.value }}</strong>
          <small>{{ lane.status }}</small>
        </div>
        <button class="table-action" type="button">{{ lane.action }}</button>
      </article>
    </section>

    <section class="shopfloor-grid" aria-label="业务调度与焦点工单">
      <div class="dispatch-console">
        <div class="console-toolbar m3-toolbar">
          <div>
            <span class="eyebrow">Live Dispatch</span>
            <h2>业务调度队列</h2>
            <p>用单号、对象、当前节点、责任人和下一动作组织现场工作，而不是只放模块入口。</p>
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

        <div class="erp-table-wrap m3-table-wrap">
          <table class="erp-table m3-dispatch-table">
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
              <tr v-for="row in dispatchRows" :key="row.code">
                <td><strong>{{ row.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ row.object }}</b>
                    <small>{{ row.material }}</small>
                  </span>
                </td>
                <td>{{ row.node }}</td>
                <td>{{ row.owner }}</td>
                <td><StatusBadge :label="row.status" :tone="row.tone" /></td>
                <td><button class="table-action" type="button">{{ row.action }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside class="focus-inspector">
        <section class="inspector-card tone-red">
          <div class="inspector-head">
            <span>当前焦点工单</span>
            <StatusBadge :label="activeWorkOrder.status" :tone="workOrderTone(activeWorkOrder.status)" />
          </div>
          <strong>{{ activeWorkOrder.code }}</strong>
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
          <div class="detail-actions">
            <button class="primary-button" type="button">扫码报工</button>
            <button class="secondary-button" type="button">称重交接</button>
          </div>
        </section>

        <section class="inspector-card tone-amber">
          <div class="inspector-head">
            <span>称重交接阻断</span>
            <StatusBadge :label="handoffBlocker.status" :tone="handoffBlocker.tone" />
          </div>
          <strong>{{ handoffBlocker.code }}</strong>
          <dl class="detail-grid">
            <dt>流转</dt>
            <dd>{{ handoffBlocker.from }} -> {{ handoffBlocker.to }}</dd>
            <dt>投入</dt>
            <dd>{{ handoffBlocker.inputWeight }}</dd>
            <dt>产出</dt>
            <dd>{{ handoffBlocker.outputWeight }}</dd>
            <dt>损耗</dt>
            <dd>{{ handoffBlocker.lossRate }}</dd>
          </dl>
        </section>

        <section class="inspector-card tone-blue">
          <div class="inspector-head">
            <span>样例与开源参考</span>
            <StatusBadge label="避免造轮子" tone="blue" />
          </div>
          <article v-for="item in openSourceReferences" :key="item.name" class="reference-row">
            <strong>{{ item.name }}</strong>
            <span>{{ item.use }}</span>
            <small>{{ item.fit }}</small>
          </article>
        </section>
      </aside>
    </section>

    <section class="proof-grid" aria-label="证据链与现场阻断">
      <div class="proof-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Evidence</span>
            <h2>证据链健康度</h2>
            <p>每个关键动作必须落到附件、视频、称重、台账或审批记录。</p>
          </div>
        </div>
        <div class="evidence-matrix">
          <article v-for="item in traceEvidence" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.link }}</span>
            </div>
            <StatusBadge :label="item.status" :tone="item.tone" />
          </article>
        </div>
      </div>

      <div class="proof-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Quality Gate</span>
            <h2>质检与损耗口径</h2>
            <p>返工和损耗必须追溯到工序投入、产出称重和缺陷证据。</p>
          </div>
        </div>
        <div class="quality-snapshot">
          <article class="tone-amber">
            <span>当前质检</span>
            <strong>{{ activeQuality.code }}</strong>
            <small>{{ activeQuality.defect }}</small>
          </article>
          <article class="tone-red">
            <span>损耗审批</span>
            <strong>LOSS-20260706-009</strong>
            <small>试算快照，不进入正式工资发放</small>
          </article>
          <article class="tone-green">
            <span>库存维度</span>
            <strong>{{ activeInventory.batch }}</strong>
            <small>{{ activeInventory.owner }} / {{ activeInventory.status }}</small>
          </article>
        </div>
      </div>

      <div class="proof-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Hardware POC</span>
            <h2>软硬件结合管理</h2>
            <p>NVR、打印、扫码和电子秤先按兼容方案推进，失败必须有人工兜底。</p>
          </div>
        </div>
        <div class="hardware-summary">
          <strong>{{ getHardwareManualFallbackCount() }} 项需要兜底</strong>
          <span>视频截取失败允许人工上传，电子秤一期人工读数加照片留证。</span>
          <StatusBadge label="POC 未冻结" tone="amber" />
        </div>
      </div>
    </section>

    <section class="workstation-panel" aria-label="工位运行状态">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Work Centers</span>
          <h2>工位运行状态</h2>
          <p>把瓶颈、队列和质检积压直接暴露给车间组长。</p>
        </div>
        <StatusBadge :label="`${getOpenExceptionCount()} 个开放异常`" tone="amber" />
      </div>
      <div class="station-card-grid">
        <article v-for="station in productionStationLoads" :key="station.station" :class="`tone-${station.tone}`">
          <div>
            <strong>{{ station.station }}</strong>
            <span>{{ station.bottleneck }}</span>
          </div>
          <b>{{ station.queue }}</b>
          <small>负载 {{ station.capacity }}</small>
        </article>
      </div>
    </section>

    <section class="module-contract-panel" aria-label="模块完成度与接口契约">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Delivery Ledger</span>
          <h2>模块完成度与真实 API 契约</h2>
          <p>清楚标记哪些已经能跑业务命令，哪些仍是样例、POC 或待开发缺口。</p>
        </div>
        <StatusBadge label="M3.23 前端作业台整改" tone="blue" />
      </div>
      <div class="module-contract-grid">
        <div class="api-command-list">
          <article v-for="item in commandRows" :key="item.scene" :class="`tone-${item.tone}`">
            <div>
              <strong>{{ item.scene }}</strong>
              <span>{{ item.api }}</span>
            </div>
            <StatusBadge :label="item.state" :tone="item.tone" />
          </article>
        </div>
        <div class="readiness-table-wrap">
          <table class="readiness-table">
            <thead>
              <tr>
                <th>模块</th>
                <th>接口</th>
                <th>页面</th>
                <th>数据</th>
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
      </div>
    </section>

    <section class="metric-grid ops-metric-grid" aria-label="今日业务指标">
      <article v-for="metric in metrics" :key="metric.label" :class="`metric-card tone-${metric.tone}`">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.delta }}</small>
      </article>
    </section>

    <section class="boundary-ledger" aria-label="当前边界说明">
      <article class="tone-green">
        <strong>真实后端：库存台账与业务动作</strong>
        <span>来料、报工、质检、返工、复检、出库、退货和损耗试算已经形成一条可测试链路。</span>
      </article>
      <article class="tone-amber">
        <strong>前端状态：作业台结构成型，仍非完整交付系统</strong>
        <span>首页已按现场作业逻辑重构，但真实权限、审批 UI、对象存储和售后复核仍需继续开发。</span>
      </article>
      <article class="tone-red">
        <strong>工资边界：只做试算，不做正式工资发放</strong>
        <span>损耗审批返回工资试算快照和轻量审批任务，不代表正式工资锁账或财务凭证。</span>
      </article>
    </section>
  </div>
</template>
