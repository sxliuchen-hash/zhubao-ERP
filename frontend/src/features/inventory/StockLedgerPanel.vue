<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { fetchStockLedgers, type StockLedgerDimension, type StockLedgerRecord } from '../../services/stockLedgerApi';

const props = withDefaults(defineProps<{ initialDocumentCode?: string }>(), { initialDocumentCode: '' });
const rows = ref<StockLedgerRecord[]>([]);
const filters = reactive({ query: '', document: props.initialDocumentCode, warehouse: '', owner: '', from: '', to: '' });
const loading = ref(false);
const error = ref('');
const page = ref(1);
const advancedFilterCount = computed(() => [filters.document, filters.warehouse, filters.from, filters.to].filter(value => value.trim()).length);
const size = 20;
let generation = 0;
let disposed = false;
const typeLabels: Record<string, string> = { customer_return: '客户退货', after_sales_review: '售后复核', outbound_shipment: '发货', inbound_confirm: '收货', production_report: '交工', process_station_report: '工序交付', quality_release: '质检放行' };
function label(type: string) { return typeLabels[type] || type; }
function dateKey(time: string) {
  const date = new Date(time);
  return Number.isNaN(date.valueOf()) ? '' : `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
function dimensionText(dimension: StockLedgerDimension | null) {
  if (!dimension) return '无库存维度';
  return `${dimension.ownerType === 'CUSTOMER' ? '客户料' : dimension.ownerType === 'COMPANY' ? '公司料' : dimension.ownerType} · 客户 ${dimension.customerId ?? '未记录'} · 货主 ${dimension.ownerCustomerId ?? '未记录'} · 母单 ${dimension.motherOrderCode || '无'} · 工单 ${dimension.workOrderCode || '无'} · 批次 ${dimension.batchCode || '无'} · ${dimension.materialCode} · ${dimension.warehouseCode} · ${dimension.stockStatus}`;
}
const filtered = computed(() => rows.value.filter(row => {
  const dimensions = [row.from, row.to].filter((value): value is StockLedgerDimension => Boolean(value));
  const text = `${row.code} ${row.documentCode} ${label(row.documentType)} ${row.remark} ${dimensions.map(dimensionText).join(' ')}`.toLowerCase();
  const date = dateKey(row.bizTime);
  return (!filters.query.trim() || text.includes(filters.query.trim().toLowerCase()))
    && (!filters.document.trim() || row.documentCode === filters.document.trim())
    && (!filters.warehouse.trim() || dimensions.some(dimension => dimension.warehouseCode === filters.warehouse.trim()))
    && (!filters.owner || dimensions.some(dimension => dimension.ownerType === filters.owner))
    && (!filters.from || date >= filters.from) && (!filters.to || date <= filters.to);
}));
const visible = computed(() => filtered.value.slice((page.value - 1) * size, page.value * size));
watch(filters, () => { page.value = 1; });
async function load() {
  const token = ++generation;
  loading.value = true; error.value = ''; rows.value = []; page.value = 1;
  try { const loaded = await fetchStockLedgers(); if (!disposed && token === generation) rows.value = loaded; }
  catch (caught) { if (!disposed && token === generation) error.value = caught instanceof Error ? caught.message : '出入库读取失败'; }
  finally { if (!disposed && token === generation) loading.value = false; }
}
onMounted(load);
onBeforeUnmount(() => { disposed = true; generation += 1; });
</script>

<template>
  <section class="stock-ledger-panel page-stack" aria-label="全部出入库检索">
    <header><h2>出入库记录</h2><button class="secondary-button" type="button" :disabled="loading" @click="load">刷新台账</button></header>
    <p>逐条核对来源和去向；公司料与客户料、不同单位均不合计。流水未提供数量单位时保留未知。</p>
    <div class="ledger-filters">
      <label>查找<input v-model="filters.query" aria-label="台账查找" placeholder="台账号、单号、物料、客户或备注" /></label>
      <label>货权<select v-model="filters.owner" aria-label="台账货权"><option value="">全部可见</option><option value="COMPANY">公司料</option><option value="CUSTOMER">客户料</option></select></label>
      <details class="info-disclosure ledger-advanced"><summary>更多筛选<small v-if="advancedFilterCount">已填 {{ advancedFilterCount }} 项</small></summary><div class="info-fields">
      <label>精确业务单号<input v-model="filters.document" aria-label="台账业务单号" /></label>
      <label>精确仓库<input v-model="filters.warehouse" aria-label="台账仓库" /></label>
      <label>开始日期<input v-model="filters.from" type="date" aria-label="台账开始日期" /></label>
      <label>结束日期<input v-model="filters.to" type="date" aria-label="台账结束日期" /></label>
      </div></details>
    </div>
    <p v-if="loading" role="status">正在读取当前账号可见台账…</p>
    <p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p>
    <p v-else-if="!loading">当前账号可见 {{ rows.length }} 条，筛选后 {{ filtered.length }} 条。数量单位未提供；重量单位为克。</p>
    <article v-for="row in visible" :key="row.code" class="ledger-entry">
      <header><strong>{{ label(row.documentType) }} · {{ row.documentCode }}</strong><span>{{ row.code }} · {{ row.bizTime }}</span></header>
      <p>数量 {{ row.quantity }}（单位未提供） / 重量 {{ row.weight }} 克</p>
      <p>{{ row.from?.warehouseCode || '外部' }} → {{ row.to?.warehouseCode || '外部' }} · {{ row.to?.materialCode || row.from?.materialCode || '物料未提供' }}</p>
      <details class="info-disclosure"><summary>来源与去向明细</summary>
      <div class="ledger-dimensions"><p>来源：{{ dimensionText(row.from) }}</p><p>去向：{{ dimensionText(row.to) }}</p></div>
      <p v-if="row.sourceLedgerCode">关联原流水 {{ row.sourceLedgerCode }}</p><p v-if="row.remark">{{ row.remark }}</p>
      </details>
    </article>
    <p v-if="!loading && !error && !filtered.length">没有符合条件的可见流水</p>
    <nav v-if="filtered.length" class="ledger-pages" aria-label="台账分页"><button class="secondary-button" type="button" :disabled="page <= 1" @click="page -= 1">上一页</button><span>第 {{ page }} / {{ Math.ceil(filtered.length / size) }} 页</span><button class="secondary-button" type="button" :disabled="page * size >= filtered.length" @click="page += 1">下一页</button></nav>
  </section>
</template>

<style scoped>
.stock-ledger-panel { min-width: 0; }
header, .ledger-pages { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; justify-content: space-between; }
h2, p { margin: 0; }
.ledger-filters { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 220px)); gap: 10px 12px; justify-content: start; align-items: end; }
.ledger-advanced { grid-column: 1 / -1; }
label { display: grid; gap: 4px; }
input, select { width: 100%; min-width: 0; box-sizing: border-box; min-height: 40px; }
.ledger-entry { border: 1px solid var(--line); border-radius: 10px; padding: 12px; display: grid; gap: 8px; overflow-wrap: anywhere; }
.ledger-entry header span { font-size: 12px; color: var(--muted); }
.ledger-dimensions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
button { min-height: 44px; }
@media (max-width: 900px) { .ledger-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } .ledger-dimensions { grid-template-columns: 1fr; } }
</style>
