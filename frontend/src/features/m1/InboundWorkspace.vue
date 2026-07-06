<script setup lang="ts">
import { onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  inboundCheckpoints as fallbackInboundCheckpoints,
  inboundOrders as fallbackInboundOrders,
  inboundRisks as fallbackInboundRisks,
} from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  type InboundWorkspaceModel,
} from '../../services/businessWorkspaceApi';

const workspace = ref<InboundWorkspaceModel>({
  checkpoints: fallbackInboundCheckpoints,
  orders: fallbackInboundOrders,
  risks: fallbackInboundRisks,
});

onMounted(async () => {
  try {
    workspace.value = await fetchInboundWorkspace();
  } catch {
    // Keep deterministic local samples available when the backend is not running.
  }
});

function videoTone(video: string) {
  if (video === '失败') return 'red';
  if (video === '截取中') return 'amber';
  if (video === '已关联') return 'green';
  return 'gray';
}

function statusTone(status: string) {
  if (status.includes('异常')) return 'red';
  if (status.includes('待')) return 'amber';
  return 'green';
}
</script>

<template>
  <div class="page-stack">
    <section class="operations-hero">
      <div class="hero-copy">
        <span class="eyebrow">Inbound Control</span>
        <h2>来料接收控制台</h2>
        <p>把快递收货、拆包称重、客户所有库存、视频证据和标签打印压在同一条入库链路里。</p>
      </div>
      <div class="hero-kpis">
        <article v-for="item in workspace.checkpoints.slice(0, 3)" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>
    </section>

    <section class="control-filter-bar" aria-label="来料筛选">
      <div>
        <span>业务对象</span>
        <strong>客户来料 / 母单 / 拆包称重</strong>
      </div>
      <div>
        <span>优先级</span>
        <strong>视频失败与物料待确认优先</strong>
      </div>
      <div>
        <span>库存归属</span>
        <strong>客户所有，不进入企业自有库存</strong>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" type="button">只看异常</button>
        <button class="secondary-button" type="button">打印队列</button>
      </div>
    </section>

    <section class="toolbar-band">
      <div>
        <h2>来料入库与母单</h2>
        <p>围绕快递扫码、拆包称重、客户所有库存入账和母单标签建立第一道追溯入口。</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" type="button">扫码收货</button>
        <button class="primary-button" type="button">新建母单</button>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>今日来料队列</h2>
            <p>确认入库后只通过业务单据影响客户所有库存，不允许直接改余额。</p>
          </div>
        </div>
        <div class="data-table inbound-table">
          <div class="table-row table-head">
            <span>母单</span>
            <span>客户</span>
            <span>快递单号</span>
            <span>物料</span>
            <span>数量/重量</span>
            <span>状态</span>
            <span>视频</span>
          </div>
          <div v-for="order in workspace.orders" :key="order.code" class="table-row">
            <strong>{{ order.code }}</strong>
            <span>{{ order.customer }}</span>
            <span>{{ order.courierNo }}</span>
            <span>{{ order.material }}</span>
            <span>{{ order.qty }} 件 / {{ order.weight }}</span>
            <StatusBadge :label="order.status" :tone="statusTone(order.status)" />
            <StatusBadge :label="order.video" :tone="videoTone(order.video)" />
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>入库检查点</h2>
            <p>缺少任一关键证据时，系统应阻止确认入库或进入异常处理。</p>
          </div>
        </div>
        <div class="evidence-health">
          <article v-for="item in workspace.checkpoints" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.status }}</span>
            </div>
            <StatusBadge :label="item.value" :tone="item.tone" />
          </article>
        </div>
      </div>
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>拆包称重交接</h2>
            <p>用于解释每一次重量变化，并给损耗归因留下证据。</p>
          </div>
        </div>
        <div class="form-grid">
          <label>
            <span>母单</span>
            <input value="MD-20260706-001" readonly />
          </label>
          <label>
            <span>拆包前重量</span>
            <input value="486.80 g" readonly />
          </label>
          <label>
            <span>确认入库重量</span>
            <input value="486.25 g" readonly />
          </label>
          <label>
            <span>交接人</span>
            <input value="何琳 / 马杰" readonly />
          </label>
        </div>
        <div class="notice-list">
          <p>重量差 0.55 g，低于系统参数 `loss.default.tolerance`，允许确认入库。</p>
          <p>拆包视频状态为待开始，确认入库前需要创建视频截取任务。</p>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>母单标签预览</h2>
            <p>标签必须支持扫码追溯到客户、批次、物料、库存台账和视频证据。</p>
          </div>
        </div>
        <div class="label-preview">
          <div class="qr-box">QR</div>
          <div>
            <strong>MD-20260706-001</strong>
            <span>恒信珠宝 / 18K 金戒托</span>
            <span>客户所有 / 待加工 / 原料库 A-01</span>
            <span>数量 320 件 / 重量 486.25 g</span>
          </div>
        </div>
        <div class="button-row">
          <button class="secondary-button" type="button">打印标签</button>
          <button class="secondary-button" type="button">补打记录</button>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>入库异常处置</h2>
          <p>异常必须有责任人、处置动作和证据兜底，不能停留在口头沟通。</p>
        </div>
      </div>
      <div class="ops-card-grid">
        <article v-for="risk in workspace.risks" :key="risk.code" class="ops-card">
          <div class="ops-card-head">
            <div>
              <small>{{ risk.code }}</small>
              <strong>{{ risk.title }}</strong>
            </div>
            <StatusBadge :label="risk.action" :tone="risk.tone" />
          </div>
          <p>{{ risk.detail }}</p>
          <span>责任人：{{ risk.owner }}</span>
        </article>
      </div>
    </section>
  </div>
</template>
