<script setup lang="ts">
import { ref, watch } from 'vue';
import { issueFactoryMaterial, returnFactoryMaterial } from '../../services/factoryStockApi';

export interface StockPosition {
  label: '库内' | '车间' | '未还';
  location: string;
  quantity: string;
  unit: string;
}

const props = defineProps<{
  itemId: string;
  batchCode: string;
  unit: string;
  positions: StockPosition[];
  issueId: string;
  issueVersion: number;
}>();

const quantity = ref('');
const from = ref('');
const workshop = ref('');
const returnQuantity = ref('');
const message = ref('');

watch(() => props.itemId, () => {
  quantity.value = '';
  returnQuantity.value = '';
  message.value = '';
});

function position(label: StockPosition['label']) {
  return props.positions.find(row => row.label === label);
}

async function submitIssue() {
  const source = position('库内');
  if (!source || from.value !== source.location) {
    message.value = '只能从库内领用，未还不计入可领';
    return;
  }
  if (!quantity.value.trim()) {
    message.value = '请填写领用数量';
    return;
  }
  await issueFactoryMaterial({
    requestId: `issue-${props.itemId}-${quantity.value}`,
    itemId: props.itemId,
    batchCode: props.batchCode,
    fromWarehouse: source.location,
    workshopCode: workshop.value,
    quantity: quantity.value,
    weight: null,
    unit: props.unit,
    targetCode: '',
  });
  message.value = '已提交领用';
}

async function submitReturn() {
  if (!props.issueId || !returnQuantity.value.trim()) {
    message.value = '请按原领用退回';
    return;
  }
  await returnFactoryMaterial(props.issueId, {
    requestId: `return-${props.issueId}-${returnQuantity.value}`,
    version: props.issueVersion,
    quantity: returnQuantity.value,
    weight: null,
    unit: props.unit,
  });
  message.value = '已提交退回';
}
</script>

<template>
  <section aria-label="本厂物料分位">
    <p v-for="row in positions" :key="row.label">{{ row.label }} {{ row.location }} {{ row.quantity }}{{ row.unit }}</p>
    <p>库内、车间和未还分开显示，没有可手改的总余额。</p>
    <label>领用数量 <input v-model="quantity" aria-label="领用数量" /></label>
    <label>来源位置 <input v-model="from" aria-label="来源位置" /></label>
    <label>车间 <input v-model="workshop" aria-label="车间" /></label>
    <button type="button" @click="submitIssue">领用</button>
    <label>退回数量 <input v-model="returnQuantity" aria-label="退回数量" /></label>
    <button type="button" @click="submitReturn">退回</button>
    <p v-if="message">{{ message }}</p>
  </section>
</template>
