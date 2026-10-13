<script setup lang="ts">
import { ref } from 'vue';
import { apiGet } from '../../services/apiClient';

interface StockView {
  dimension?: {
    ownerType?: string;
    customerId?: number;
    motherOrderCode?: string;
    materialCode?: string;
    warehouseCode?: string;
    stockStatus?: string;
    batchCode?: string;
  };
  quantity?: number;
  weight?: number;
}

const open = ref(false);
const error = ref('');
const customer = ref<StockView[]>([]);
const factory = ref<StockView[]>([]);
const company = ref<StockView[]>([]);

async function load() {
  open.value = true;
  error.value = '';
  try {
    const [customerRows, factoryRows, companyRows] = await Promise.all([
      apiGet<StockView[]>('/api/inventory/prep-stock/customer'),
      apiGet<StockView[]>('/api/inventory/prep-stock/factory'),
      apiGet<StockView[]>('/api/inventory/prep-stock/company').catch(() => []),
    ]);
    customer.value = customerRows;
    factory.value = factoryRows;
    company.value = companyRows;
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '库存口径加载失败';
  }
}

function label(row: StockView) {
  const dimension = row.dimension;
  return [
    dimension?.batchCode || dimension?.motherOrderCode || '未编号',
    dimension?.materialCode || '',
    dimension?.stockStatus || '',
    `${row.quantity ?? 0}`,
  ].filter(Boolean).join(' · ');
}
</script>

<template>
  <section class="section-block" aria-label="备料和自有物料">
    <h2>备料、厂内实物和公司自有物料</h2>
    <p>客户备料不绑定加工母单。厂内实物按台账结余展示。公司自有生产物料与财务耗材分开，耗材仍在财务页核算。</p>
    <button class="secondary-button" type="button" @click="load">查看三类库存</button>
    <p v-if="error" class="operation-feedback danger">{{ error }}</p>
    <template v-if="open">
      <h3>客户备料</h3>
      <p v-if="!customer.length">暂无客户备料</p>
      <article v-for="(row, index) in customer" :key="'c' + index">{{ label(row) }}</article>
      <h3>厂内实物</h3>
      <p v-if="!factory.length">暂无厂内结余</p>
      <article v-for="(row, index) in factory" :key="'f' + index">{{ label(row) }}</article>
      <h3>公司自有生产物料</h3>
      <p v-if="!company.length">暂无公司自有生产物料，或当前账号不可查看</p>
      <article v-for="(row, index) in company" :key="'o' + index">{{ label(row) }}</article>
    </template>
  </section>
</template>
