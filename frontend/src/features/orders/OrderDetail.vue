<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { ScanContextResponse } from '../../services/scanContextApi';
import { canonicalTarget } from '../scan/useScanContext';
import { progressStageLabel } from '../scan/progressTarget';
import OrderEvidence from './OrderEvidence.vue';

const props = withDefaults(defineProps<{
  context: ScanContextResponse;
  showCustomer?: boolean;
  showWeight?: boolean;
  showStation?: boolean;
}>(), { showCustomer: true, showWeight: true, showStation: true });
const tab = ref<'overview' | 'flow' | 'evidence'>('overview');
const identity = computed(() => canonicalTarget(props.context));
watch(() => props.context, () => { tab.value = 'overview'; });
function amount(value: number | null, unit: string | null) {
  return value === null ? '未填写' : `${value}${unit ? ` ${unit}` : ''}`;
}
</script>

<template>
  <div v-if="identity" class="order-detail-content">
    <nav class="order-detail-tabs" aria-label="单据详情分区">
      <button v-for="item in ([['overview', '概况'], ['flow', '流转'], ['evidence', '附件']] as const)" :key="item[0]"
        type="button" :aria-pressed="tab === item[0]" @click="tab = item[0]">{{ item[1] }}</button>
    </nav>
    <div v-if="tab === 'overview'" class="order-overview">
      <dl v-if="identity.objectType !== 'EXPRESS_NO'" class="mobile-order-facts">
        <div><dt>货品</dt><dd>{{ context.order.goodsName || '未填写' }}</dd></div>
        <div><dt>数量</dt><dd>{{ amount(context.order.quantity, context.order.quantityUnit) }}</dd></div>
        <div><dt>交期</dt><dd>{{ context.order.dueDate || '未填写' }}</dd></div>
        <div v-if="showCustomer && context.order.customerName"><dt>客户</dt><dd>{{ context.order.customerName }}</dd></div>
        <div v-if="showWeight"><dt>重量</dt><dd>{{ amount(context.order.weight, context.order.weightUnit) }}</dd></div>
      </dl>
      <slot name="summary" />
      <details v-if="identity.objectType !== 'EXPRESS_NO'" class="info-disclosure">
        <summary>关联单号</summary>
        <dl class="info-facts"><div><dt>母单</dt><dd>{{ identity.motherOrderCode || '未提供' }}</dd></div><div v-if="identity.workOrderCode"><dt>子单／执行目标</dt><dd>{{ identity.workOrderCode }}</dd></div></dl>
      </details>
      <p v-if="identity.objectType === 'EXPRESS_NO'">这张运单尚未定位到订单，可进入收货建单。</p>
      <section v-else class="mobile-order-requirements" aria-label="订单要求">
        <h4>当前要求</h4>
        <p v-if="identity.objectType !== 'MOTHER_ORDER'">子单／本轮次要求：{{ context.requirements.workOrderRequirement || '未填写' }}</p>
        <p v-if="identity.objectType === 'MOTHER_ORDER' || !context.requirements.workOrderRequirement">母单要求：{{ context.requirements.motherRequirement || '未填写' }}</p>
        <details v-else class="info-disclosure"><summary>查看母单原要求</summary><p>{{ context.requirements.motherRequirement || '未填写' }}</p></details>
        <details v-if="showStation && context.requirements.stationRequirements.length" class="info-disclosure">
          <summary>岗位要求<small>{{ context.requirements.stationRequirements.length }} 项</small></summary>
        <ul>
          <li v-for="(item, index) in context.requirements.stationRequirements" :key="index">
            岗位 {{ item.process || '当前工序' }}：{{ item.text || '未填写' }}
          </li>
        </ul>
        </details>
      </section>
    </div>
    <section v-else-if="tab === 'flow'" class="order-flow" aria-label="本单流转事实">
      <p>查询目标：{{ identity.code }}。下列记录保留原单号；尚无跨母子单聚合时间线。</p>
      <h4>正式交工</h4>
      <p v-if="!context.progress.handoffs.length">暂无正式交工记录</p>
      <p v-for="(item, index) in context.progress.handoffs" :key="index">
        {{ item.process || '未填工序' }} · {{ item.ledgerCode || '未提供单号' }} · {{ item.operator || '未提供操作人' }} · {{ item.createdAt || '未提供时间' }}
      </p>
      <h4>质检事实</h4>
      <p v-if="!context.progress.quality">暂无质检结论</p>
      <template v-else>
        <p>{{ context.progress.quality.orderCode }} · {{ context.progress.quality.qualityCaseCode }} · {{ context.progress.quality.outcome || '待核对' }}</p>
        <p>{{ context.progress.quality.frozen ? '已冻结' : '未冻结' }} · 返工单 {{ context.progress.quality.reworkOrderCode || '无' }}</p>
      </template>
      <h4>过程说明</h4>
      <p v-if="!context.progress.progressRecords.length">暂无过程说明</p>
      <p v-for="(item, index) in context.progress.progressRecords" :key="index">
        {{ item.targetCode || '未提供原单' }} · {{ progressStageLabel(item.stage) }} {{ item.process }} · {{ item.note || '未填写' }} · {{ item.operatorName }} {{ item.recordedAt }}
      </p>
      <h4>发货事实</h4>
      <p v-if="!context.progress.shipments.length">暂无发货记录</p>
      <p v-for="(item, index) in context.progress.shipments" :key="index">
        {{ item.outboundCode || '未提供发货单' }} · {{ item.workOrderCode || item.motherOrderCode }} · {{ item.logisticsNo || '未填物流' }} · {{ item.status || '待核对' }}
      </p>
    </section>
    <OrderEvidence v-else :key="`${identity.objectType}:${identity.code}`" :target-type="identity.objectType" :target-code="identity.code" />
  </div>
</template>

<style scoped>
.order-detail-content, .order-overview { display: grid; gap: 14px; min-width: 0; }
.order-detail-tabs { display: flex; gap: 8px; border-bottom: 1px solid var(--line); padding-bottom: 8px; }
.order-detail-tabs button { min-height: 44px; padding: 8px 14px; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.order-detail-tabs button[aria-pressed="true"] { background: var(--action-soft); color: var(--action); font-weight: 700; }
.mobile-order-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 16px; margin: 0; }
.mobile-order-facts dt { color: var(--muted); font-size: 12px; }
.mobile-order-facts dd { margin: 3px 0 0; overflow-wrap: anywhere; }
.mobile-order-requirements { border-top: 1px solid var(--line); padding-top: 12px; }
.mobile-order-requirements h4, .order-flow h4 { margin: 0 0 8px; }
.mobile-order-requirements p, .order-flow p { margin: 0 0 10px; white-space: pre-wrap; overflow-wrap: anywhere; }
.order-flow h4 { margin-top: 16px; }
</style>
