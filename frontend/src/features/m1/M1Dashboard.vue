<script setup lang="ts">
import MetricCard from '../../components/MetricCard.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  getHardwareManualFallbackCount,
  inboundOrders,
  inventoryBalances,
  metrics,
  productionHandoffs,
  qualityCases,
  workOrders,
  workQueues,
} from '../../data/m1PrototypeData';

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

const activeInbound = inboundOrders[0];
const activeWorkOrder = workOrders[2] ?? workOrders[0];
const activeQuality = qualityCases[0];
const activeInventory = inventoryBalances[1] ?? inventoryBalances[0];

const operationSteps = [
  { index: '01', title: '扫码收货', detail: '绑定快递单、客户、物料档案', state: '2 单待处理', tone: 'amber' },
  { index: '02', title: '拆包称重', detail: '记录拆包前后重量与视频时间段', state: '1 单视频失败', tone: 'red' },
  { index: '03', title: '生成子工单', detail: '按工序模板拆分并打印标签', state: '11 张已生成', tone: 'blue' },
  { index: '04', title: '工序报工', detail: '扫码确认投入、产出、计件快照', state: '18 张在制', tone: 'violet' },
  { index: '05', title: '质检放行', detail: '缺陷、返工、损耗审批闭环', state: '7 批待检', tone: 'amber' },
] as const;

const consoleRows = [
  {
    code: activeInbound.code,
    type: '来料母单',
    customer: activeInbound.customer,
    material: activeInbound.material,
    node: '拆包称重',
    owner: '库管员 何琳',
    blocker: activeInbound.video === '失败' ? '视频截取失败' : '待称重确认',
    next: '开始拆包视频',
    tone: activeInbound.video === '失败' ? 'red' : 'amber',
  },
  {
    code: activeWorkOrder.code,
    type: '生产工单',
    customer: activeWorkOrder.customer,
    material: activeWorkOrder.material,
    node: activeWorkOrder.process,
    owner: activeWorkOrder.assignee,
    blocker: activeWorkOrder.status,
    next: '补录称重交接',
    tone: activeWorkOrder.status.includes('称重') ? 'red' : 'blue',
  },
  {
    code: activeQuality.code,
    type: '质检记录',
    customer: '恒信珠宝',
    material: '18K 金戒托',
    node: activeQuality.process,
    owner: activeQuality.inspector,
    blocker: activeQuality.status,
    next: activeQuality.action,
    tone: 'amber',
  },
  {
    code: activeInventory.batch,
    type: '库存批次',
    customer: activeInventory.owner,
    material: activeInventory.material,
    node: activeInventory.location,
    owner: '库存服务',
    blocker: activeInventory.status,
    next: '查看台账',
    tone: activeInventory.status.includes('在制') ? 'violet' : 'green',
  },
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

    <section class="erp-console-board" aria-label="生产运营工作台">
      <div class="console-master">
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
              <span>关键词</span>
              <input value="MD-20260706-001" aria-label="追溯关键词" />
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

      <aside class="console-detail">
        <div class="detail-title">
          <span>当前焦点</span>
          <strong>{{ activeWorkOrder.code }}</strong>
          <StatusBadge :label="activeWorkOrder.status" :tone="activeWorkOrder.status.includes('称重') ? 'red' : 'blue'" />
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

    <section class="operations-dock" aria-label="核心作业入口">
      <article v-for="step in operationSteps" :key="step.index" :class="`tone-${step.tone}`">
        <span>{{ step.index }}</span>
        <div>
          <strong>{{ step.title }}</strong>
          <small>{{ step.detail }}</small>
        </div>
        <StatusBadge :label="step.state" :tone="step.tone" />
      </article>
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>证据链完整性</h2>
            <p>每个关键动作必须能落到附件、视频、称重、台账或审批记录。</p>
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
