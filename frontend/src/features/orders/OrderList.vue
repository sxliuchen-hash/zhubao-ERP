<script setup lang="ts">
import type { OrderWorkspaceRow } from '../../services/orderWorkspaceTypes';
import { orderFacetLabel, orderQuantityText } from './orderWorkspaceLabels';
defineProps<{ rows: OrderWorkspaceRow[]; selectedKey: string }>();
const emit = defineEmits<{ select: [row: OrderWorkspaceRow] }>();
</script>

<template>
  <div class="order-table-wrap">
    <table class="order-table">
      <thead><tr><th>货品／目标</th><th>客户／母单</th><th>数量／重量</th><th>当前工序</th><th>交期</th><th>状态</th></tr></thead>
      <tbody>
        <tr v-for="row in rows" :key="row.rowKey" :class="{ selected: selectedKey === row.rowKey }">
          <td><button class="table-action" type="button" :aria-label="`查看${row.code}`" @click="emit('select', row)">{{ row.goodsName || '货品未填写' }}</button><small>{{ row.code }}</small><small v-if="row.executionRoundCode">执行轮次：{{ row.executionRoundCode }}</small></td>
          <td>{{ row.customerName || '客户未提供' }}<small>{{ row.motherOrderCode }}</small><small v-if="row.executionRoundCode && row.workOrderCode">原子单：{{ row.workOrderCode }}</small></td>
          <td>{{ orderQuantityText(row.quantity, row.quantityUnit) }}<small>{{ row.weight === null ? '重量未填写' : `${row.weight} ${row.weightUnit || ''}` }}</small></td>
          <td>{{ row.currentProcess || '未提供工序' }}<small>{{ row.processPercent === null ? '未记录工序进度' : `工序进度 ${row.processPercent}%` }}</small><small v-if="row.processFormallyDelivered !== null">{{ row.processFormallyDelivered ? '本工序已正式交工' : '本工序未正式交工' }}</small></td>
          <td>{{ row.dueDate || '交期未填写' }}</td>
          <td>{{ row.businessStatus || '待核对' }}<small>{{ orderFacetLabel(row.facet) }}</small><small v-if="row.facetNote">{{ row.facetNote }}</small></td>
        </tr>
      </tbody>
    </table>
  </div>
  <div class="order-cards">
    <button v-for="row in rows" :key="row.rowKey" class="order-card" type="button" :aria-label="`查看${row.code}`" @click="emit('select', row)">
      <span class="order-card-title"><strong>{{ row.goodsName || '货品未填写' }}</strong><span>{{ orderFacetLabel(row.facet) }}</span></span>
      <span>{{ row.code }}<template v-if="row.executionRoundCode"> · 执行轮次</template></span>
      <span class="order-card-quiet">母单 {{ row.motherOrderCode }}<template v-if="row.executionRoundCode && row.workOrderCode"> · 原子单 {{ row.workOrderCode }}</template></span>
      <span v-if="row.customerName">{{ row.customerName }}</span>
      <span>{{ orderQuantityText(row.quantity, row.quantityUnit) }} · {{ row.dueDate || '交期未填写' }}</span>
      <span>{{ row.currentProcess || '未提供工序' }} · {{ row.processPercent === null ? '未记录工序进度' : `${row.processPercent}%` }} · {{ row.businessStatus }}</span>
      <span v-if="row.processFormallyDelivered !== null" class="order-card-quiet">{{ row.processFormallyDelivered ? '本工序已正式交工' : '本工序未正式交工' }}</span>
      <span v-if="row.facetNote" class="order-card-quiet">{{ row.facetNote }}</span>
    </button>
  </div>
</template>

<style scoped>
.order-table-wrap { overflow-x: auto; max-width: 100%; }
.order-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.order-table th, .order-table td { text-align: left; padding: 12px; border-bottom: 1px solid var(--line); vertical-align: top; }
.order-table th { color: var(--muted); white-space: nowrap; }
.order-table small { display: block; color: var(--muted); margin-top: 5px; overflow-wrap: anywhere; }
.selected { background: var(--action-soft); }
.order-cards { display: none; }
@media (max-width: 900px) {
  .order-table-wrap { display: none; }
  .order-cards { display: grid; gap: 10px; }
  .order-card { display: grid; gap: 4px; width: 100%; min-width: 0; text-align: left; padding: 12px; background: var(--content); border: 1px solid var(--line); border-radius: 12px; color: var(--ink); overflow-wrap: anywhere; }
  .order-card-quiet { display: none; }
  .order-card-title { display: flex; gap: 12px; justify-content: space-between; color: var(--action); }
}
</style>
