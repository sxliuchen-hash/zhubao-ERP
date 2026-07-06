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

const commandStats = [
  { label: '来料待办', value: 6, unit: '单', tone: 'amber', detail: '2 单待客户/物料确认' },
  { label: '在制工单', value: 18, unit: '张', tone: 'red', detail: '1 张称重阻断' },
  { label: '质检队列', value: 7, unit: '批', tone: 'amber', detail: '返工、复检、损耗同链路' },
  { label: '库存批次', value: inventoryBalances.length, unit: '批', tone: 'green', detail: '按客户所有权隔离' },
  { label: '证据完整率', value: 92, unit: '%', tone: 'blue', detail: '视频失败 1 项' },
] as const;

const processLanes = [
  { code: 'IN', name: '收货', value: '6 单', status: '拆包/称重优先', tone: 'amber' },
  { code: 'PK', name: '拆包', value: '3 单', status: '视频证据待补', tone: 'red' },
  { code: 'MO', name: '报工', value: '18 张', status: '称重交接阻断', tone: 'red' },
  { code: 'QC', name: '质检', value: '7 批', status: '返工复检排队', tone: 'amber' },
  { code: 'ST', name: '入库', value: '4 批', status: '台账可追溯', tone: 'green' },
  { code: 'OUT', name: '出库', value: '4 单', status: '待客户确认', tone: 'blue' },
] as const;

const dispatchRows = [
  ...workQueues.map((item) => ({
    priority: severityLabel(item.severity),
    code: item.id,
    object: item.title,
    customer: item.subtitle,
    node: item.due,
    owner: item.owner,
    evidence: '审批/称重/视频',
    status: item.severity === 'high' ? '阻断' : '待处理',
    action: item.severity === 'high' ? '立即处理' : '派给责任人',
    tone: severityTone(item.severity),
  })),
  ...inboundOrders.map((order) => ({
    priority: order.video === '失败' ? '高' : '中',
    code: order.code,
    object: `来料母单 / ${order.customer}`,
    customer: order.material,
    node: order.status,
    owner: '库管员 何琳',
    evidence: order.video === '失败' ? '视频缺口' : '快递/标签',
    status: order.video === '失败' ? '视频截取失败' : order.video,
    action: order.video === '失败' ? '人工上传' : '收货处理',
    tone: inboundTone(order.status, order.video),
  })),
  ...workOrders.map((order) => ({
    priority: order.status.includes('称重') ? '高' : '普通',
    code: order.code,
    object: `生产工单 / ${order.customer}`,
    customer: order.material,
    node: order.process,
    owner: order.assignee,
    evidence: order.status.includes('称重') ? '称重照片缺口' : '报工记录',
    status: order.status,
    action: order.status.includes('称重') ? '补称重' : '报工',
    tone: workOrderTone(order.status),
  })),
  ...qualityCases.map((item) => {
    const order = workOrders.find((workOrder) => workOrder.code === item.workOrder) ?? activeWorkOrder;
    return {
      priority: item.status.includes('待') ? '中' : '普通',
      code: item.code,
      object: `质检记录 / ${order.customer}`,
      customer: order.material,
      node: item.process,
      owner: item.inspector,
      evidence: item.defect,
      status: item.status,
      action: item.action,
      tone: qualityTone(item.status),
    };
  }),
  ...inventoryBalances.map((item) => ({
    priority: item.frozen === '0' ? '普通' : '中',
    code: item.batch,
    object: `库存批次 / ${item.owner}`,
    customer: item.material,
    node: item.location,
    owner: '库存服务',
    evidence: item.weight,
    status: item.status,
    action: item.frozen === '0' ? '查台账' : '处理冻结',
    tone: inventoryTone(item.status),
  })),
].slice(0, 16);

const evidenceCards = [
  ...traceEvidence,
  { label: '轻量审批任务', status: 'JDBC 可切换', link: 'approval_task / approval_audit_event', tone: 'blue' as Tone },
];

const sourceRows = [
  {
    source: 'ERPNext Work Order / Job Card',
    applied: '工单、工序、报工、库存转移',
    rule: '工序动作必须生成库存或证据事件',
  },
  {
    source: 'Odoo Shop Floor / Quality Checks',
    applied: '工作中心、质检点、现场优先级',
    rule: '现场页面优先呈现下一动作和阻断原因',
  },
  {
    source: 'InvenTree Stock / Build Orders',
    applied: '批次、库位、追溯和条码',
    rule: '客户所有权、批次、库位、状态不可混写',
  },
] as const;

const moduleRows = [
  { module: '来料母单', api: 'inbound-confirmations', state: '写入 RAW_WAITING 台账', next: '拆包视频失败兜底', tone: 'green' },
  { module: '生产工单', api: 'production-reports', state: 'WIP 转半成品', next: '称重交接强校验', tone: 'green' },
  { module: '质检返工', api: 'quality-freezes / rework-*', state: '冻结、返工、复检可跑通', next: '审批 UI 与登录权限', tone: 'amber' },
  { module: '损耗审批', api: 'loss-approvals / approvals/tasks', state: '工资试算 + 轻量审批', next: '正式锁账边界', tone: 'amber' },
  { module: '库存追溯', api: 'ledger / shipment / return', state: '台账、出库、退货可回归', next: '售后复核分流', tone: 'amber' },
  { module: '文档附件', api: 'documents / uploads', state: '上传、预览、审计可用', next: 'MinIO/S3 或 NAS', tone: 'amber' },
  { module: '硬件联动', api: 'hardware / poc-records', state: 'POC 记录可管理', next: '现场复测与型号冻结', tone: 'red' },
] as const;
</script>

<template>
  <div class="m325-workbench page-stack">
    <section class="floor-command-surface" aria-label="生产现场总控">
      <div class="floor-command-main">
        <div class="console-title-line">
          <span class="eyebrow">SHOP FLOOR COMMAND</span>
          <StatusBadge label="M3.34 作业台优化" tone="green" />
        </div>
        <div class="floor-command-title">
          <h2>来料加工现场总控</h2>
          <div class="shift-clock">
            <strong>白班</strong>
            <span>2026-07-06</span>
          </div>
        </div>

        <div class="command-stat-grid" aria-label="白班业务指标">
          <article v-for="item in commandStats" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>

        <div class="process-lane-strip" aria-label="一期业务链路">
          <article v-for="lane in processLanes" :key="lane.code" :class="`tone-${lane.tone}`">
            <b>{{ lane.code }}</b>
            <div>
              <strong>{{ lane.name }}</strong>
              <span>{{ lane.value }} · {{ lane.status }}</span>
            </div>
          </article>
        </div>
      </div>

      <aside class="blocker-stack">
        <div class="blocker-stack-head">
          <span class="eyebrow">PRIORITY</span>
          <strong>阻断队列</strong>
        </div>
        <article v-for="item in workQueues.slice(0, 4)" :key="item.id" :class="`tone-${severityTone(item.severity)}`">
          <StatusBadge :label="severityLabel(item.severity)" :tone="severityTone(item.severity)" />
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.subtitle }}</span>
            <small>{{ item.owner }} · {{ item.due }}</small>
          </div>
        </article>
      </aside>
    </section>

    <section class="floor-control-grid" aria-label="业务单据池与焦点工单">
      <div class="dispatch-board-panel">
        <div class="floor-toolbar">
          <div>
            <span class="eyebrow">LIVE DISPATCH</span>
            <h2>今日单据池</h2>
          </div>
          <div class="floor-filter-row" aria-label="单据筛选">
            <button class="filter-pill active" type="button">全部 16</button>
            <button class="filter-pill" type="button">阻断 3</button>
            <button class="filter-pill" type="button">质检 7</button>
            <label>
              <span>追溯</span>
              <input value="MD-20260706-001" aria-label="追溯关键字" />
            </label>
          </div>
        </div>

        <div class="erp-table-wrap m325-table-wrap">
          <table class="erp-table floor-dispatch-table">
            <thead>
              <tr>
                <th>优先级</th>
                <th>业务单号</th>
                <th>业务对象</th>
                <th>节点</th>
                <th>责任人</th>
                <th>证据</th>
                <th>状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in dispatchRows" :key="row.code">
                <td><StatusBadge :label="row.priority" :tone="row.tone" /></td>
                <td><strong>{{ row.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ row.object }}</b>
                    <small>{{ row.customer }}</small>
                  </span>
                </td>
                <td>{{ row.node }}</td>
                <td>{{ row.owner }}</td>
                <td>{{ row.evidence }}</td>
                <td><StatusBadge :label="row.status" :tone="row.tone" /></td>
                <td><button class="table-action" type="button">{{ row.action }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside class="floor-side-stack">
        <section class="focus-workorder-panel tone-red">
          <div class="panel-title-row">
            <span class="eyebrow">当前焦点工单</span>
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
          <div class="mini-progress" aria-label="工单进度">
            <span><b :style="{ width: `${activeWorkOrder.progress}%` }" /></span>
            <em>{{ activeWorkOrder.progress }}%</em>
          </div>
          <div class="detail-actions">
            <button class="primary-button" type="button">扫码报工</button>
            <button class="secondary-button" type="button">称重交接</button>
          </div>
        </section>

        <section class="handoff-panel tone-amber">
          <div class="panel-title-row">
            <span class="eyebrow">WEIGH HANDOFF</span>
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

        <section class="quality-gate-panel tone-blue">
          <div class="panel-title-row">
            <span class="eyebrow">QUALITY GATE</span>
            <StatusBadge :label="activeQuality.status" :tone="qualityTone(activeQuality.status)" />
          </div>
          <strong>{{ activeQuality.code }}</strong>
          <span>{{ activeQuality.process }} · {{ activeQuality.defect }}</span>
          <small>损耗审批：LOSS-20260706-009，工资试算不进入正式发放。</small>
        </section>
      </aside>
    </section>

    <section class="floor-insight-grid" aria-label="证据、硬件和样例依据">
      <div class="insight-panel evidence-chain-panel">
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

      <div class="insight-panel hardware-poc-panel">
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
            <span>截取失败可人工上传，保留时间段和失败日志。</span>
          </article>
          <article class="tone-green">
            <strong>扫码枪</strong>
            <span>USB HID 模式优先，失败允许手输后复核。</span>
          </article>
          <article class="tone-amber">
            <strong>电子秤</strong>
            <span>一期人工读数加照片，二期再接 RS232/USB/TCP。</span>
          </article>
        </div>
      </div>

      <div class="insight-panel source-basis-panel">
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

    <section class="workstation-panel m325-station-panel" aria-label="工位运行状态">
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
    </section>

    <section class="module-contract-panel m325-module-panel" aria-label="模块完成度与接口契约">
      <div class="section-heading">
        <div>
          <span class="eyebrow">DELIVERY LEDGER</span>
          <h2>模块完成度与真实 API 契约</h2>
        </div>
        <StatusBadge label="M3.34 UI 整改" tone="green" />
      </div>
      <div class="readiness-table-wrap">
        <table class="readiness-table m325-readiness-table">
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
    </section>

    <section class="floor-boundary-ledger" aria-label="当前交付边界">
      <article class="tone-green">
        <strong>已验证链路</strong>
        <span>来料、报工、质检、返工、复检、出库、退货和损耗试算已能通过测试链路回归。</span>
      </article>
      <article class="tone-amber">
        <strong>仍需开发</strong>
        <span>敏感 POST 已有轻量角色守卫；真实登录、菜单权限、对象存储和复杂流程编排未完成。</span>
      </article>
      <article class="tone-red">
        <strong>工资边界</strong>
        <span>当前只做工资试算，不做正式工资发放、锁账或财务凭证。</span>
      </article>
    </section>
  </div>
</template>
