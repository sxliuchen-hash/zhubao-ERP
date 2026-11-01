<script setup lang="ts">
import { ref } from 'vue';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
import { deliverGoldwork } from '../../services/goldworkApi';

const goldworkCode = ref('');
const qualityId = ref('');
const netWeight = ref('');
const weightUnit = ref('克');
const message = ref('');
const operation = useBusinessCommand('goldwork-delivery', goldworkCode, { qualityId, netWeight, weightUnit }, message);


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
  const saved = await operation.submit('deliver', {
    qualityId: qualityId.value.trim(),
    pieceCount: null,
    netWeight: net,
    weightUnit: weightUnit.value.trim(),
    goldPrice: null,
    amount: null,
    shipAsProcessing: false,
    countCustomerGoldAsSales: false,
  }, (code, body) => deliverGoldwork(code, body));
  if (!saved) return;
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
    <fieldset class="page-stack" :disabled="operation.pending.value" style="margin:0;padding:0;border:0;min-width:0">
    <label>金工号 <input v-model="goldworkCode" aria-label="交付金工号" /></label>
    <label>放行份额 <input v-model="qualityId" aria-label="放行份额" /></label>
    <label>净重 <input v-model="netWeight" aria-label="交付净重" /></label>
    <label>单位 <input v-model="weightUnit" aria-label="交付单位" /></label>
    <button type="button" @click="deliver">交付</button>
    </fieldset>
    <p v-if="operation.pending.value">正在提交，请稍候</p>
    <button v-if="operation.hasUnconfirmed.value" type="button" @click="operation.restorePending">恢复上次填写</button>
    <button type="button" @click="operation.startNext">开始下一笔</button>
  </section>
</template>
