<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import { inventoryBalances, traceEvents } from '../../data/m1PrototypeData';
</script>

<template>
  <div class="page-stack">
    <section class="toolbar-band">
      <div>
        <h2>库存台账与全链路追溯</h2>
        <p>客户所有权、批次、仓库、状态和冻结量必须共同构成库存查询维度。</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" type="button">导出台账</button>
        <button class="primary-button" type="button">追溯查询</button>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>实时库存余额</h2>
          <p>余额只来自库存台账汇总，页面不提供直接编辑入口。</p>
        </div>
      </div>
      <div class="data-table inventory-table">
        <div class="table-row table-head">
          <span>客户所有权</span>
          <span>批次/来源</span>
          <span>物料</span>
          <span>位置</span>
          <span>状态</span>
          <span>数量/重量</span>
          <span>冻结</span>
        </div>
        <div v-for="item in inventoryBalances" :key="`${item.owner}-${item.batch}-${item.status}`" class="table-row">
          <strong>{{ item.owner }}</strong>
          <span>{{ item.batch }}</span>
          <span>{{ item.material }}</span>
          <span>{{ item.location }}</span>
          <StatusBadge :label="item.status" :tone="item.status.includes('返工') ? 'violet' : item.status.includes('在制') ? 'amber' : item.status.includes('成品') ? 'green' : 'blue'" />
          <span>{{ item.qty }} 件 / {{ item.weight }}</span>
          <span>{{ item.frozen }}</span>
        </div>
      </div>
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>追溯时间线</h2>
            <p>样例对象：MD-20260706-001 / ZD-20260706-001。</p>
          </div>
        </div>
        <div class="timeline">
          <article v-for="event in traceEvents" :key="`${event.time}-${event.bizCode}`">
            <time>{{ event.time }}</time>
            <div>
              <strong>{{ event.node }}</strong>
              <span>{{ event.bizCode }} / {{ event.operator }}</span>
              <small>{{ event.evidence }}</small>
            </div>
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>台账写入原则</h2>
            <p>所有业务单据只能调用库存服务写台账。</p>
          </div>
        </div>
        <div class="rule-grid compact">
          <article>
            <strong>来料入库</strong>
            <span>外部来料转客户所有待加工库存。</span>
          </article>
          <article>
            <strong>工单开工</strong>
            <span>待加工转在制，不允许负库存。</span>
          </article>
          <article>
            <strong>质检报废</strong>
            <span>在制转报废，记录责任归因。</span>
          </article>
          <article>
            <strong>出库发货</strong>
            <span>成品先冻结，确认后转已发货。</span>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>
