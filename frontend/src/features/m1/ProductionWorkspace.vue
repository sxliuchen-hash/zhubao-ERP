<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  productionHandoffs as fallbackProductionHandoffs,
  productionStationLoads as fallbackProductionStationLoads,
  workOrders as fallbackWorkOrders,
} from '../../data/m1PrototypeData';
import {
  fetchProductionWorkspace,
  type ProductionWorkspaceModel,
} from '../../services/businessWorkspaceApi';

const workspace = ref<ProductionWorkspaceModel>({
  stationLoads: fallbackProductionStationLoads,
  workOrders: fallbackWorkOrders,
  handoffs: fallbackProductionHandoffs,
});

const activeWorkOrder = computed(
  () =>
    workspace.value.workOrders.find((order) => order.status.includes('称重') || order.status.includes('待')) ??
    workspace.value.workOrders[0],
);

const activeHandoff = computed(
  () =>
    workspace.value.handoffs.find((handoff) => handoff.status.includes('阻断') || handoff.status.includes('待')) ??
    workspace.value.handoffs[0],
);

onMounted(async () => {
  try {
    workspace.value = await fetchProductionWorkspace();
  } catch {
    // Keep deterministic local samples available when the backend is not running.
  }
});
</script>

<template>
  <div class="page-stack">
    <section class="operations-hero">
      <div class="hero-copy">
        <span class="eyebrow">Workshop Control</span>
        <h2>生产工单调度台</h2>
        <p>用工位负载、称重交接和工序状态判断是否能继续派工，避免只看到工单列表。</p>
      </div>
      <div class="hero-kpis">
        <article>
          <span>在制工单</span>
          <strong>{{ workspace.workOrders.length }}</strong>
        </article>
        <article>
          <span>交接阻断</span>
          <strong>1</strong>
        </article>
        <article>
          <span>工位满载</span>
          <strong>1</strong>
        </article>
      </div>
    </section>

    <section class="control-filter-bar" aria-label="生产筛选">
      <div>
        <span>派工范围</span>
        <strong>总装一线 / 白班 / 在制工单</strong>
      </div>
      <div>
        <span>阻断规则</span>
        <strong>称重缺失、质检积压、损耗超标</strong>
      </div>
      <div>
        <span>工资口径</span>
        <strong>只做计件试算，单价按报工快照</strong>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" type="button">瓶颈工位</button>
        <button class="secondary-button" type="button">待交接</button>
      </div>
    </section>

    <section class="module-ops-board" aria-label="生产派工主控台">
      <div class="module-master">
        <div class="module-toolbar">
          <div>
            <span class="eyebrow">Dispatch Desk</span>
            <h2>生产派工主控台</h2>
            <p>工单、工位、报工和称重交接放在同一操作区，现场组长可以直接判断下一步。</p>
          </div>
          <div class="module-tools">
            <label>
              <span>工单扫码</span>
              <input value="ZD-20260706-006" aria-label="工单扫码" />
            </label>
            <button class="primary-button" type="button">扫码报工</button>
            <button class="secondary-button" type="button">补录交接</button>
          </div>
        </div>
        <div class="module-table-wrap">
          <table class="module-table">
            <thead>
              <tr>
                <th>工单</th>
                <th>客户/物料</th>
                <th>工序</th>
                <th>负责人</th>
                <th>产出进度</th>
                <th>状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in workspace.workOrders" :key="order.code">
                <td><strong>{{ order.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ order.customer }}</b>
                    <small>{{ order.material }}</small>
                  </span>
                </td>
                <td>{{ order.process }}</td>
                <td>{{ order.assignee }}</td>
                <td>
                  <i class="progress-bar"><b :style="{ width: `${order.progress}%` }" /></i>
                  {{ order.finishedQty }}/{{ order.plannedQty }}
                </td>
                <td><StatusBadge :label="order.status" :tone="order.status.includes('称重') ? 'red' : order.status.includes('待') ? 'amber' : order.status.includes('返工') ? 'violet' : 'green'" /></td>
                <td><button class="table-action" type="button">进入工序</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeWorkOrder" class="module-detail">
        <div class="detail-title">
          <span>当前工单</span>
          <strong>{{ activeWorkOrder.code }}</strong>
          <StatusBadge :label="activeWorkOrder.status" :tone="activeWorkOrder.status.includes('称重') ? 'red' : activeWorkOrder.status.includes('待') ? 'amber' : 'blue'" />
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
          <dt>交接阻断</dt>
          <dd>{{ activeHandoff?.code ?? '无' }} / {{ activeHandoff?.status ?? '正常' }}</dd>
          <dt>工资口径</dt>
          <dd>计件试算，单价按报工快照</dd>
        </dl>
        <div class="detail-actions">
          <button class="primary-button" type="button">提交报工</button>
          <button class="secondary-button" type="button">称重交接</button>
          <button class="secondary-button" type="button">打印工单标签</button>
        </div>
      </aside>
    </section>

    <section class="toolbar-band">
      <div>
        <h2>生产工单与工序报工</h2>
        <p>母单拆分、工序模板、扫码报工和称重交接必须围绕同一张子工单流转。</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" type="button">打印工单标签</button>
        <button class="primary-button" type="button">拆分子工单</button>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>工位负载</h2>
          <p>派工前先看瓶颈工位，避免继续向已积压工序推单。</p>
        </div>
      </div>
      <div class="station-grid">
        <article v-for="station in workspace.stationLoads" :key="station.station">
          <div>
            <strong>{{ station.station }}</strong>
            <span>{{ station.bottleneck }}</span>
          </div>
          <StatusBadge :label="`${station.queue} / ${station.capacity}`" :tone="station.tone" />
        </article>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>工单执行列表</h2>
            <p>已报工工序不允许直接修改顺序；需要通过更正记录保留痕迹。</p>
          </div>
        </div>
        <div class="data-table production-table">
          <div class="table-row table-head">
            <span>工单</span>
            <span>客户</span>
            <span>物料</span>
            <span>工序</span>
            <span>负责人</span>
            <span>产出</span>
            <span>状态</span>
          </div>
          <div v-for="order in workspace.workOrders" :key="order.code" class="table-row">
            <strong>{{ order.code }}</strong>
            <span>{{ order.customer }}</span>
            <span>{{ order.material }}</span>
            <span>{{ order.process }}</span>
            <span>{{ order.assignee }}</span>
            <span>
              <i class="progress-bar"><b :style="{ width: `${order.progress}%` }" /></i>
              {{ order.finishedQty }}/{{ order.plannedQty }}
            </span>
            <StatusBadge :label="order.status" :tone="order.status.includes('称重') ? 'red' : order.status.includes('待') ? 'amber' : order.status.includes('返工') ? 'violet' : 'green'" />
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>工序模板</h2>
            <p>当前样例：戒托加工标准流程。</p>
          </div>
        </div>
        <ol class="process-list">
          <li>
            <span>01</span>
            <div>
              <strong>粗抛</strong>
              <small>计件 0.80 元/件，记录损耗</small>
            </div>
          </li>
          <li>
            <span>02</span>
            <div>
              <strong>精抛</strong>
              <small>计件 1.10 元/件，需称重交接</small>
            </div>
          </li>
          <li>
            <span>03</span>
            <div>
              <strong>镶石</strong>
              <small>计件 2.40 元/件，需质检</small>
            </div>
          </li>
          <li>
            <span>04</span>
            <div>
              <strong>终检入库</strong>
              <small>质检通过后转成品库存</small>
            </div>
          </li>
        </ol>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>称重交接记录</h2>
          <p>损耗、工资试算和质检归因都必须能追溯到交接证据。</p>
        </div>
      </div>
      <div class="handoff-table">
        <div class="handoff-row handoff-head">
          <span>交接单</span>
          <span>从</span>
          <span>到</span>
          <span>投入</span>
          <span>产出</span>
          <span>损耗</span>
          <span>状态</span>
        </div>
        <div v-for="handoff in workspace.handoffs" :key="handoff.code" class="handoff-row">
          <strong>{{ handoff.code }}</strong>
          <span>{{ handoff.from }}</span>
          <span>{{ handoff.to }}</span>
          <span>{{ handoff.inputWeight }}</span>
          <span>{{ handoff.outputWeight }}</span>
          <span>{{ handoff.lossRate }}</span>
          <StatusBadge :label="handoff.status" :tone="handoff.tone" />
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>报工校验规则</h2>
          <p>前端只做提示和录入约束，最终校验以后端领域服务为准。</p>
        </div>
      </div>
      <div class="rule-grid">
        <article>
          <strong>扫码绑定</strong>
          <span>只能对本人或本班组授权工单报工。</span>
        </article>
        <article>
          <strong>称重阻断</strong>
          <span>关键工序缺少投入/产出重量时不能提交。</span>
        </article>
        <article>
          <strong>工资快照</strong>
          <span>工序单价按报工时快照写入，后续调价不影响历史。</span>
        </article>
        <article>
          <strong>返工规则</strong>
          <span>返工工单默认不计工资，特殊情况走审批。</span>
        </article>
      </div>
    </section>
  </div>
</template>
