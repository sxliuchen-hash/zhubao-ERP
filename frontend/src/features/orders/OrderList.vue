<script setup lang="ts">
import { computed } from 'vue';
import type { Tone } from '../../data/m1PrototypeData';
import type { OrderWorkspaceRow } from '../../services/orderWorkspaceTypes';
import StatusBadge from '../../components/StatusBadge.vue';
import OrderKindMark from './OrderKindMark.vue';
import { orderProductionStatus, orderQuantityText, processStyle } from './orderWorkspaceLabels';

const props = defineProps<{ rows: OrderWorkspaceRow[]; selectedKey: string; sort: string }>();
const emit = defineEmits<{ select: [row: OrderWorkspaceRow]; sort: [sort: SortValue] }>();

type SortValue = 'CREATED_DESC' | 'CREATED_ASC' | 'DUE_ASC' | 'DUE_DESC' | 'GOODS_ASC' | 'GOODS_DESC' | 'CUSTOMER_ASC' | 'CUSTOMER_DESC' | 'CREATOR_ASC' | 'CREATOR_DESC' | 'PROCESS_ASC' | 'PROCESS_DESC' | 'STATUS_ASC' | 'STATUS_DESC';
const columns: Array<{ key: string; label: string; first: 'ASC' | 'DESC' }> = [
  { key: 'GOODS', label: '货品', first: 'ASC' },
  { key: 'CUSTOMER', label: '客户', first: 'ASC' },
  { key: 'CREATOR', label: '建单人', first: 'ASC' },
  { key: 'CREATED', label: '创建时间', first: 'DESC' },
  { key: 'PROCESS', label: '工序', first: 'ASC' },
  { key: 'DUE', label: '交期', first: 'ASC' },
  { key: 'STATUS', label: '状态', first: 'ASC' },
];
const active = computed(() => {
  const match = /^([A-Z]+)_(ASC|DESC)$/.exec(props.sort || 'CREATED_DESC');
  return { key: match?.[1] ?? 'CREATED', direction: match?.[2] ?? 'DESC' };
});
function nextSort(column: { key: string; first: 'ASC' | 'DESC' }): SortValue {
  if (active.value.key !== column.key) return `${column.key}_${column.first}` as SortValue;
  const direction = active.value.direction === 'ASC' ? 'DESC' : 'ASC';
  return `${column.key}_${direction}` as SortValue;
}
function createdText(value: string | null | undefined) {
  if (!value?.trim()) return '时间未记录';
  const text = value.trim().replace('T', ' ');
  return text.length > 16 ? text.slice(0, 16) : text;
}
function statusTone(facet: string): Tone {
  switch (facet) {
    case 'IN_PROCESS': return 'amber';
    case 'WAITING_QUALITY': return 'violet';
    case 'REWORK': return 'red';
    case 'READY_TO_SHIP': return 'green';
    case 'SHIPPED': return 'blue';
    default: return 'gray';
  }
}
</script>

<template>
  <div class="order-table-wrap">
    <table class="order-table">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column.key" :aria-sort="active.key === column.key ? (active.direction === 'ASC' ? 'ascending' : 'descending') : 'none'">
            <button type="button" class="order-sort" :aria-label="`按${column.label}排序`" @click="emit('sort', nextSort(column))">
              {{ column.label }}<span v-if="active.key === column.key" aria-hidden="true">{{ active.direction === 'ASC' ? '↑' : '↓' }}</span>
            </button>
          </th>
          <th>数量</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.rowKey" :class="['order-row', processStyle(row.currentProcess), { selected: selectedKey === row.rowKey }]">
          <td>
            <button class="table-action order-goods" type="button" :aria-label="`查看${row.code}`" @click="emit('select', row)">{{ row.goodsName || '货品未填写' }}</button>
            <small class="order-code-line"><OrderKindMark :object-type="row.objectType" />{{ row.code }}</small>
          </td>
          <td>
            <strong>{{ row.customerName || '客户未提供' }}</strong>
            <small>{{ row.motherOrderCode }}</small>
          </td>
          <td>{{ row.createdBy || '历史未记录' }}</td>
          <td>{{ createdText(row.createdAt) }}</td>
          <td>
            <span class="process-chip" :class="processStyle(row.currentProcess)">{{ row.currentProcess || '未分工序' }}<small v-if="row.processPercent !== null">{{ row.processPercent }}%</small></span>
            <small v-if="row.processFormallyDelivered !== null">{{ row.processFormallyDelivered ? '已交工' : '未交工' }}</small>
          </td>
          <td>{{ row.dueDate || '未填' }}</td>
          <td><StatusBadge :label="orderProductionStatus(row)" :tone="statusTone(row.facet)" /><small v-if="row.facetNote">{{ row.facetNote }}</small></td>
          <td>{{ orderQuantityText(row.quantity, row.quantityUnit) }}<small>{{ row.weight === null ? '重量未填' : `${row.weight} ${row.weightUnit || ''}`.trim() }}</small></td>
        </tr>
      </tbody>
    </table>
  </div>
  <div class="order-mobile-sort" aria-label="订单表头排序">
    <button v-for="column in columns" :key="column.key" type="button" class="order-sort" :aria-label="`按${column.label}排序`" :aria-pressed="active.key === column.key" @click="emit('sort', nextSort(column))">
      {{ column.label }}<span v-if="active.key === column.key" aria-hidden="true">{{ active.direction === 'ASC' ? '↑' : '↓' }}</span>
    </button>
  </div>
  <div class="order-cards">
    <button v-for="row in rows" :key="row.rowKey" class="order-card" :class="processStyle(row.currentProcess)" type="button" :aria-label="`查看${row.code}`" @click="emit('select', row)">
      <span class="order-card-title"><strong>{{ row.goodsName || '货品未填写' }}</strong><StatusBadge :label="orderProductionStatus(row)" :tone="statusTone(row.facet)" /></span>
      <span class="order-code-line"><OrderKindMark :object-type="row.objectType" />{{ row.code }} · {{ row.customerName || '客户未提供' }}</span>
      <span>建单人：{{ row.createdBy || '历史未记录' }} · <span class="process-chip" :class="processStyle(row.currentProcess)">{{ row.currentProcess || '未分工序' }}</span> · {{ createdText(row.createdAt) }}</span>
      <span>{{ orderQuantityText(row.quantity, row.quantityUnit) }} · {{ row.dueDate || '交期未填' }}</span>
    </button>
  </div>
</template>

<style scoped>
.order-table-wrap { overflow-x: auto; max-width: 100%; }
.order-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.order-table th, .order-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--line); vertical-align: middle; }
.order-table th { color: var(--muted); white-space: nowrap; background: var(--soft); }
.order-table small { display: block; color: var(--muted); margin-top: 3px; overflow-wrap: anywhere; }
.order-sort { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; background: transparent; color: inherit; font: inherit; font-weight: 700; cursor: pointer; }
.order-sort:hover, .order-sort[aria-pressed="true"] { color: var(--action); }
.order-goods { font-weight: 700; }
.order-code-line { display: flex; align-items: center; gap: 6px; }
.order-row.engrave { box-shadow: inset 3px 0 0 #e09a3e; }
.order-row.set { box-shadow: inset 3px 0 0 #7a5aaa; }
.order-row.polish { box-shadow: inset 3px 0 0 #1f8a86; }
.order-row.intake { box-shadow: inset 3px 0 0 #315fd1; }
.order-row.other { box-shadow: inset 3px 0 0 #c5ced8; }
.selected { background: var(--action-soft); }
.process-chip { display: inline-flex; align-items: center; gap: 4px; min-height: 22px; padding: 0 8px; border-radius: 999px; border: 1px solid transparent; font-size: 12px; font-weight: 700; white-space: nowrap; }
.process-chip small { margin: 0; color: inherit; font-weight: 600; }
.process-chip.engrave { color: #8a4b12; background: #fff1e0; border-color: #f0c48a; }
.process-chip.set { color: #5b3d8a; background: #f3eefb; border-color: #d4c4ee; }
.process-chip.polish { color: #0f6e73; background: #e7f6f6; border-color: #b7e0df; }
.process-chip.intake { color: #244bb0; background: #e7eefb; border-color: #c5d4f3; }
.process-chip.other { color: #526074; background: #f4f6f8; border-color: #d8dee6; }
.order-mobile-sort, .order-cards { display: none; }
@media (max-width: 900px) {
  .order-table-wrap { display: none; }
  .order-mobile-sort { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; }
  .order-mobile-sort .order-sort { flex: 0 0 auto; min-height: 32px; padding: 0 10px; border: 1px solid var(--line); border-radius: 999px; background: var(--content); }
  .order-cards { display: grid; gap: 10px; }
  .order-card { display: grid; gap: 6px; width: 100%; min-width: 0; text-align: left; padding: 12px 12px 12px 14px; background: var(--content); border: 1px solid var(--line); border-radius: 12px; color: var(--ink); overflow-wrap: anywhere; }
  .order-card.engrave { border-left: 4px solid #e09a3e; }
  .order-card.set { border-left: 4px solid #7a5aaa; }
  .order-card.polish { border-left: 4px solid #1f8a86; }
  .order-card.intake { border-left: 4px solid #315fd1; }
  .order-card.other { border-left: 4px solid #c5ced8; }
  .order-card-title { display: flex; gap: 12px; justify-content: space-between; align-items: center; }
}
</style>
