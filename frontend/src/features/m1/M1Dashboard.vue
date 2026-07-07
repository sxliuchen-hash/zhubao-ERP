<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
import {
  getHardwareManualFallbackCount,
  getOpenExceptionCount,
  inboundOrders,
  inventoryBalances,
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
  return 'blue';
}

function severityLabel(severity: string) {
  if (severity === 'high') return 'P0';
  if (severity === 'medium') return 'P1';
  return 'P2';
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
const activeHandoff = productionHandoffs.find((handoff) => handoff.tone === 'red') ?? productionHandoffs[0];
const activeInbound = inboundOrders[0];
const activeInventory = inventoryBalances[0];

const commandStats = [
  { label: '来料待处理', value: 6, unit: '单', detail: '2 单需确认物料/客户', tone: 'amber' },
  { label: '在制工单', value: 18, unit: '张', detail: '1 张称重交接阻断', tone: 'red' },
  { label: '质检队列', value: 7, unit: '批', detail: '返工、复检、损耗同链路', tone: 'amber' },
  { label: '证据完整率', value: 92, unit: '%', detail: '视频截取失败 1 项', tone: 'green' },
  { label: '自动化测试', value: 55, unit: '项', detail: '后端回归基线', tone: 'blue' },
] as const;

const processLanes = [
  { code: 'IN', name: '收货', value: '6 单', status: '扫码绑定母单', tone: 'amber' },
  { code: 'WT', name: '称重', value: '3 单', status: '拆包前后留痕', tone: 'red' },
  { code: 'MO', name: '派工', value: '18 张', status: '工序/WIP 台账', tone: 'red' },
  { code: 'QC', name: '质检', value: '7 批', status: '返工复检排队', tone: 'amber' },
  { code: 'ST', name: '入库', value: '4 批', status: '客户所有权隔离', tone: 'green' },
  { code: 'EV', name: '证据', value: '92%', status: '视频失败可兜底', tone: 'blue' },
] as const;

const dispatchRows = [
  ...workQueues.map((item) => ({
    priority: severityLabel(item.severity),
    code: item.id,
    type: '异常',
    object: item.title,
    material: item.subtitle,
    node: item.due,
    owner: item.owner,
    evidence: '审批 / 称重 / 视频',
    action: item.severity === 'high' ? '立即处理' : '分派责任人',
    tone: severityTone(item.severity),
  })),
  ...inboundOrders.map((order) => ({
    priority: order.video === '失败' ? 'P0' : 'P1',
    code: order.code,
    type: '母单',
    object: order.customer,
    material: order.material,
    node: order.status,
    owner: '库管员 何琳',
    evidence: order.video === '失败' ? '视频缺口' : `视频 ${order.video}`,
    action: order.video === '失败' ? '人工上传' : '收货处理',
    tone: inboundTone(order.status, order.video),
  })),
  ...workOrders.map((order) => ({
    priority: order.status.includes('称重') ? 'P0' : 'P2',
    code: order.code,
    type: '工单',
    object: order.customer,
    material: order.material,
    node: order.process,
    owner: order.assignee,
    evidence: order.status.includes('称重') ? '称重照片缺口' : '报工记录',
    action: order.status.includes('称重') ? '补称重' : '扫码报工',
    tone: workOrderTone(order.status),
  })),
  ...qualityCases.map((item) => ({
    priority: item.status.includes('待') ? 'P1' : 'P2',
    code: item.code,
    type: '质检',
    object: item.process,
    material: item.defect,
    node: item.status,
    owner: item.inspector,
    evidence: item.action,
    action: item.action,
    tone: qualityTone(item.status),
  })),
].slice(0, 14);

const focusCards = [
  {
    label: '焦点工单',
    code: activeWorkOrder.code,
    tone: workOrderTone(activeWorkOrder.status),
    status: activeWorkOrder.status,
    lines: [
      ['客户', activeWorkOrder.customer],
      ['物料', activeWorkOrder.material],
      ['工序', activeWorkOrder.process],
      ['负责人', activeWorkOrder.assignee],
      ['进度', `${activeWorkOrder.finishedQty}/${activeWorkOrder.plannedQty} 件`],
    ],
    action: '扫码报工',
  },
  {
    label: '称重交接',
    code: activeHandoff.code,
    tone: activeHandoff.tone,
    status: activeHandoff.status,
    lines: [
      ['流转', `${activeHandoff.from} -> ${activeHandoff.to}`],
      ['投入', activeHandoff.inputWeight],
      ['产出', activeHandoff.outputWeight],
      ['损耗', activeHandoff.lossRate],
    ],
    action: '补充证据',
  },
  {
    label: '质检门禁',
    code: activeQuality.code,
    tone: qualityTone(activeQuality.status),
    status: activeQuality.status,
    lines: [
      ['工序', activeQuality.process],
      ['缺陷', activeQuality.defect],
      ['质检员', activeQuality.inspector],
      ['动作', activeQuality.action],
    ],
    action: '生成返工',
  },
] as const;

const evidenceCards = [
  ...traceEvidence,
  { label: '轻量审批任务', status: 'JDBC 可切换', link: 'approval_task / approval_audit_event', tone: 'blue' as Tone },
];

const sourceRows = [
  {
    source: 'ERPNext Work Order / Job Card',
    applied: '工单、工序、报工、WIP 与成品库存移动',
    rule: '工序动作必须生成库存或证据事件，避免只有页面状态。',
  },
  {
    source: 'Odoo Shop Floor / Quality Checks',
    applied: '工作中心、现场优先级、质量检查点',
    rule: '现场页面优先呈现下一动作、责任人和阻断原因。',
  },
  {
    source: 'InvenTree Stock / Build Orders',
    applied: '批次、库位、条码、构建单和追溯',
    rule: '客户所有权、批次、库位、状态不能混写。',
  },
] as const;

const moduleRows = [
  { module: '来料母单', api: 'inbound-confirmations', state: '可写 RAW_WAITING 台账', next: '补强拆包视频失败兜底 UI', tone: 'green' },
  { module: '生产工单', api: 'production-reports', state: 'WIP 可转半成品', next: '称重交接强校验', tone: 'green' },
  { module: '质检返工', api: 'quality-freezes / rework-*', state: '冻结、返工、复检可回归', next: '缺陷照片批量上传', tone: 'amber' },
  { module: '损耗审批', api: 'loss-approvals / approvals/tasks', state: '工资试算 + 轻量审批', next: '正式锁账边界', tone: 'amber' },
  { module: '库存追溯', api: 'ledger / shipment / return', state: '台账、出库、退货可回归', next: '售后复核分流', tone: 'amber' },
  { module: '文档附件', api: 'documents / uploads', state: '本地存储适配可用', next: 'NAS/S3 签名 URL', tone: 'green' },
  { module: '硬件联动', api: 'hardware / poc-records', state: 'POC 记录可管理', next: '现场复测与型号冻结', tone: 'red' },
] as const;

const operatorChecklist = [
  { label: '补称重证据', value: activeHandoff.code, detail: '补录投入/产出照片并关联视频时间段', tone: 'red' },
  { label: '处理视频失败', value: 'INT-VID-001', detail: '后台重试失败后走人工上传兜底', tone: 'amber' },
  { label: '锁定审批口径', value: 'LOSS-20260706-009', detail: '超标损耗只做试算，不做正式工资发放', tone: 'blue' },
] as const;
</script>

<template>
  <div class="command-center-v3 page-stack">
    <section class="mission-hero-v2 control-room-strip" aria-label="现场经营态势">
      <div class="mission-copy control-room-copy">
        <div class="console-title-line">
          <span class="eyebrow">M3.53 OPERATIONS DESK</span>
          <StatusBadge label="业务操作台整改" tone="green" />
        </div>
        <h2>来料加工现场作业台</h2>
        <p>首屏聚合调度员要立即处理的对象：当前阻断、今日单据池、焦点工单、证据缺口、库存台账落点和下一动作。</p>
        <div class="mission-chip-row" aria-label="一期业务边界">
          <span>客户库存隔离</span>
          <span>台账可解释</span>
          <span>WIP 可追溯</span>
          <span>返工单一状态机</span>
          <span>视频失败可兜底</span>
        </div>
      </div>

      <div class="mission-kpi-grid" aria-label="白班关键指标">
        <article v-for="item in commandStats" :key="item.label" :class="`tone-${item.tone}`">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
          <em>{{ item.detail }}</em>
        </article>
      </div>
    </section>

    <section class="production-desk-grid command-grid-v2" aria-label="业务链路、单据调度和焦点处理">
      <aside class="process-rail-card flow-rail-v2">
        <div class="panel-title-row">
          <div>
            <span class="eyebrow">PROCESS</span>
            <h2>一期闭环</h2>
          </div>
          <StatusBadge label="真实链路" tone="blue" />
        </div>
        <div class="flow-step-list">
          <article v-for="lane in processLanes" :key="lane.code" :class="`tone-${lane.tone}`">
            <b>{{ lane.code }}</b>
            <div>
              <strong>{{ lane.name }}</strong>
              <span>{{ lane.value }} / {{ lane.status }}</span>
            </div>
          </article>
        </div>
        <div class="flow-rule-card">
          <strong>台账原则</strong>
          <span>数量、重量、状态变化必须落到台账、证据或审批事件。</span>
        </div>
      </aside>

      <main class="dispatch-console-v3 dispatch-console-v2">
        <div class="floor-toolbar">
          <div>
            <span class="eyebrow">LIVE DISPATCH</span>
            <h2>今日单据池</h2>
          </div>
          <div class="floor-filter-row" aria-label="单据筛选">
            <button class="filter-pill active" type="button">全部 14</button>
            <button class="filter-pill" type="button">P0 阻断 3</button>
            <button class="filter-pill" type="button">质检 7</button>
            <label>
              <span>追溯</span>
              <input value="MD-20260706-001" aria-label="追溯关键字" />
            </label>
          </div>
        </div>

        <div class="erp-table-wrap command-table-wrap">
          <table class="erp-table floor-dispatch-table command-table-v2">
            <thead>
              <tr>
                <th>优先级</th>
                <th>单号</th>
                <th>类型</th>
                <th>业务对象</th>
                <th>节点</th>
                <th>责任人</th>
                <th>证据/风险</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in dispatchRows" :key="`${row.type}-${row.code}`">
                <td><StatusBadge :label="row.priority" :tone="row.tone" /></td>
                <td><strong>{{ row.code }}</strong></td>
                <td>{{ row.type }}</td>
                <td>
                  <span class="cell-stack">
                    <b>{{ row.object }}</b>
                    <small>{{ row.material }}</small>
                  </span>
                </td>
                <td>{{ row.node }}</td>
                <td>{{ row.owner }}</td>
                <td>{{ row.evidence }}</td>
                <td><button class="table-action" type="button">{{ row.action }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </main>

      <aside class="active-record-panel">
        <div class="panel-title-row">
          <div>
            <span class="eyebrow">ACTIVE RECORD</span>
            <h2>当前处理单</h2>
          </div>
          <StatusBadge :label="activeWorkOrder.status" :tone="workOrderTone(activeWorkOrder.status)" />
        </div>
        <div class="active-record-code">
          <strong>{{ activeWorkOrder.code }}</strong>
          <span>{{ activeWorkOrder.customer }} / {{ activeWorkOrder.material }}</span>
        </div>
        <dl class="detail-grid active-record-grid">
          <dt>母单</dt>
          <dd>{{ activeInbound.code }}</dd>
          <dt>工序</dt>
          <dd>{{ activeWorkOrder.process }}</dd>
          <dt>负责人</dt>
          <dd>{{ activeWorkOrder.assignee }}</dd>
          <dt>进度</dt>
          <dd>{{ activeWorkOrder.finishedQty }}/{{ activeWorkOrder.plannedQty }} 件</dd>
          <dt>库存批次</dt>
          <dd>{{ activeInventory.batch }}</dd>
          <dt>证据</dt>
          <dd>{{ activeHandoff.status }}</dd>
        </dl>
        <div class="operator-checklist" aria-label="操作员待办">
          <article v-for="item in operatorChecklist" :key="item.label" :class="`tone-${item.tone}`">
            <strong>{{ item.label }}</strong>
            <span>{{ item.value }}</span>
            <small>{{ item.detail }}</small>
          </article>
        </div>
        <div class="active-action-grid">
          <button class="primary-button" type="button">补称重</button>
          <button class="secondary-button" type="button">生成返工</button>
          <button class="secondary-button" type="button">查看台账</button>
        </div>
      </aside>
    </section>

    <section class="focus-stack-v2 focus-board-v3" aria-label="焦点任务">
      <section
        v-for="card in focusCards"
        :key="card.code"
        class="focus-card-v2"
        :class="`tone-${card.tone}`"
      >
        <div class="panel-title-row">
          <span class="eyebrow">{{ card.label }}</span>
          <StatusBadge :label="card.status" :tone="card.tone" />
        </div>
        <strong>{{ card.code }}</strong>
        <dl class="detail-grid">
          <template v-for="line in card.lines" :key="`${card.code}-${line[0]}`">
            <dt>{{ line[0] }}</dt>
            <dd>{{ line[1] }}</dd>
          </template>
        </dl>
        <button class="table-action" type="button">{{ card.action }}</button>
      </section>
    </section>

    <section class="ops-insight-grid-v2" aria-label="证据链、开源依据和模块缺口">
      <div class="insight-panel evidence-panel-v2">
        <div class="section-heading">
          <div>
            <span class="eyebrow">EVIDENCE</span>
            <h2>证据链健康度</h2>
          </div>
          <StatusBadge label="92%" tone="green" />
        </div>
        <div class="evidence-matrix">
          <article v-for="item in evidenceCards" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.link }}</span>
            </div>
            <StatusBadge :label="item.status" :tone="item.tone" />
          </article>
        </div>
      </div>

      <div class="insight-panel hardware-panel-v2">
        <div class="section-heading">
          <div>
            <span class="eyebrow">HARDWARE</span>
            <h2>软硬件结合管理</h2>
          </div>
          <StatusBadge :label="`${getHardwareManualFallbackCount()} 项兜底`" tone="amber" />
        </div>
        <div class="hardware-poc-grid">
          <article class="tone-red">
            <strong>NVR 视频</strong>
            <span>截取失败可人工上传，保留时间段、失败日志和补录人。</span>
          </article>
          <article class="tone-green">
            <strong>扫码枪</strong>
            <span>USB HID 优先，失败允许手输并二次确认。</span>
          </article>
          <article class="tone-amber">
            <strong>电子秤</strong>
            <span>一期人工读数加照片，二期再接 RS232/USB/TCP。</span>
          </article>
        </div>
      </div>

      <div class="insight-panel source-panel-v2">
        <div class="section-heading">
          <div>
            <span class="eyebrow">OPEN SOURCE BASIS</span>
            <h2>样例与开源依据</h2>
          </div>
          <StatusBadge label="避免造轮子" tone="blue" />
        </div>
        <div class="source-row-list">
          <article v-for="item in sourceRows" :key="item.source">
            <strong>{{ item.source }}</strong>
            <span>{{ item.applied }}</span>
            <small>{{ item.rule }}</small>
          </article>
        </div>
      </div>
    </section>

    <section class="lower-grid-v2" aria-label="工位运行与模块完成度">
      <div class="workstation-panel station-panel-v2">
        <div class="section-heading">
          <div>
            <span class="eyebrow">WORK CENTERS</span>
            <h2>工位运行状态</h2>
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
      </div>

      <div class="module-contract-panel module-panel-v2">
        <div class="section-heading">
          <div>
            <span class="eyebrow">DELIVERY LEDGER</span>
            <h2>模块完成度与真实 API 契约</h2>
          </div>
          <StatusBadge label="M3.53 核心联动" tone="green" />
        </div>
        <div class="readiness-table-wrap">
          <table class="readiness-table command-readiness-table">
            <thead>
              <tr>
                <th>模块</th>
                <th>真实接口</th>
                <th>已接状态</th>
                <th>下一缺口</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in moduleRows" :key="row.module">
                <td><strong>{{ row.module }}</strong></td>
                <td>{{ row.api }}</td>
                <td><StatusBadge :label="row.state" :tone="row.tone" /></td>
                <td>{{ row.next }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>
