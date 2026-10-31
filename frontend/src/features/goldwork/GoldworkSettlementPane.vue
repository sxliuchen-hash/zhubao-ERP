<script setup lang="ts">
import { ref } from 'vue';
import { deliverGoldwork } from '../../services/goldworkApi';

const goldworkCode = ref('');
const qualityId = ref('');
const netWeight = ref('');
const weightUnit = ref('克');
const message = ref('');

function newRequestId() {
  return `goldwork-delivery-${Date.now()}`;
}

async function deliver() {
  const trimmed = netWeight.value.trim();
  if (!trimmed) {
    message.value = '请填写本次交付份额';
    return;
  }
  const net = Number(trimmed);
  if (!Number.isFinite(net) || net === 0) {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await deliverGoldwork(goldworkCode.value.trim(), {
    requestId: newRequestId(),
    qualityId: qualityId.value.trim(),
    pieceCount: null,
    netWeight: net,
    weightUnit: weightUnit.value.trim(),
    goldPrice: null,
    amount: null,
    shipAsProcessing: false,
    countCustomerGoldAsSales: false,
  });
  message.value = saved.feeState === 'UNCONFIRMED'
    ? `已交付 ${saved.deliveryId}，费用未确认`
    : `已交付 ${saved.deliveryId}`;
}
</script>

<template>
  <section class="page-stack" aria-label="金工交付">
    <header>
      <h2>交付</h2>
      <p>只交付已放行份额。不能按克数乘金价，费用未确认不是 0 元应收，也不写普通发货。</p>
    </header>
    <p role="status">{{ message }}</p>
    <label>金工号 <input v-model="goldworkCode" aria-label="交付金工号" /></label>
    <label>放行份额 <input v-model="qualityId" aria-label="放行份额" /></label>
    <label>净重 <input v-model="netWeight" aria-label="交付净重" /></label>
    <label>单位 <input v-model="weightUnit" aria-label="交付单位" /></label>
    <button type="button" @click="deliver">交付</button>
  </section>
</template>
