<script setup lang="ts">
import { ref } from 'vue';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';
import { apiGet, apiPost } from '../../services/apiClient';

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
const customerId = ref('1001');
const materialCode = ref('');
const warehouseCode = ref('');
const batchCode = ref('');
const motherOrderCode = ref('');
const quantity = ref('1');
const weight = ref('1');
const message = ref('');
const submitting = ref(false);
const attempts = ref<Record<string, RequestAttempt>>({});

function fingerprint(prefix: string) {
  return [
    prefix,
    customerId.value.trim(),
    materialCode.value.trim(),
    warehouseCode.value.trim(),
    batchCode.value.trim(),
    motherOrderCode.value.trim(),
    quantity.value.trim(),
    weight.value.trim(),
  ].join('|');
}

function requestIdFor(prefix: string) {
  const next = nextRequestAttempt(attempts.value[prefix] ?? null, fingerprint(prefix), prefix);
  if (next !== attempts.value[prefix]) {
    attempts.value = { ...attempts.value, [prefix]: next };
  }
  return next.requestId;
}

function stockBody(prefix: string, includeMother: boolean) {
  return {
    ...(prefix.startsWith('comp-') ? {} : { customerId: Number(customerId.value) }),
    materialCode: materialCode.value.trim(),
    warehouseCode: warehouseCode.value.trim(),
    batchCode: batchCode.value.trim(),
    ...(includeMother ? { motherOrderCode: motherOrderCode.value.trim() } : {}),
    quantity: Number(quantity.value),
    weight: Number(weight.value),
    requestId: requestIdFor(prefix),
  };
}

async function submitPrep(path: string, prefix: string, includeMother: boolean, success: string) {
  if (submitting.value) return;
  error.value = '';
  message.value = '';
  submitting.value = true;
  try {
    await apiPost<Record<string, unknown>, string>(path, stockBody(prefix, includeMother));
    message.value = success;
    await load();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '库存操作失败';
  } finally {
    submitting.value = false;
  }
}

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
    <form class="section-block" @submit.prevent>
      <label>客户<input v-model="customerId" aria-label="备料客户" /></label>
      <label>物料<input v-model="materialCode" aria-label="备料物料" /></label>
      <label>仓库<input v-model="warehouseCode" aria-label="备料仓库" /></label>
      <label>批次<input v-model="batchCode" aria-label="备料批次" /></label>
      <label>加工母单<input v-model="motherOrderCode" aria-label="领用母单" /></label>
      <label>数量<input v-model="quantity" aria-label="备料数量" /></label>
      <label>重量<input v-model="weight" aria-label="备料重量" /></label>
      <button class="primary-button" type="button" :disabled="submitting" @click="submitPrep('/api/inventory/prep-stock/customer-receipts', 'prep-recv', false, '已登记客户备料')">客户备料收货</button>
      <button class="secondary-button" type="button" :disabled="submitting" @click="submitPrep('/api/inventory/prep-stock/issues', 'prep-issue', true, '已领用到加工单')">分批领用</button>
      <button class="secondary-button" type="button" :disabled="submitting" @click="submitPrep('/api/inventory/prep-stock/returns', 'prep-return', true, '已退回备料批次')">退回备料</button>
      <button class="secondary-button" type="button" :disabled="submitting" @click="submitPrep('/api/inventory/prep-stock/company-receipts', 'comp-recv', false, '已登记公司料入库')">公司料入库</button>
      <button class="secondary-button" type="button" :disabled="submitting" @click="submitPrep('/api/inventory/prep-stock/company-issues', 'comp-issue', false, '已领用公司料')">公司料领用</button>
      <button class="secondary-button" type="button" :disabled="submitting" @click="submitPrep('/api/inventory/prep-stock/company-returns', 'comp-return', false, '已退回公司料')">公司料退回</button>
    </form>
    <p v-if="message" class="operation-feedback">{{ message }}</p>
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
