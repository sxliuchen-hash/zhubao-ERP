<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import { workOrders } from '../../data/m1PrototypeData';
</script>

<template>
  <div class="page-stack">
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
          <div v-for="order in workOrders" :key="order.code" class="table-row">
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
