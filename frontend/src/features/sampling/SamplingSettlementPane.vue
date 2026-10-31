<script setup lang="ts">
import { ref } from 'vue';
import { deliverSampling, settleSamplingFee } from '../../services/samplingApi';

const samplingCode = ref('');
const versionKind = ref('MODEL');
const versionNo = ref('1');
const quantity = ref('');
const unit = ref('颗');
const credentialCode = ref('');
const deliveryId = ref('');
const amount = ref('');
const reason = ref('');
const message = ref('');

function newRequestId() {
  return `sampling-delivery-${Date.now()}`;
}

async function deliver() {
  const counted = Number(quantity.value);
  if (!Number.isFinite(counted) || counted <= 0) {
    message.value = '交付数量必须大于零';
    return;
  }
  const saved = await deliverSampling(samplingCode.value.trim(), {
    requestId: newRequestId(),
    versionKind: versionKind.value,
    versionNo: Number(versionNo.value),
    materialUseId: '',
    quantity: counted,
    unit: unit.value.trim(),
    credentialCode: credentialCode.value.trim(),
  });
  deliveryId.value = saved.deliveryId;
  message.value = saved.orderClosed
    ? '不应把打样单标成完成'
    : `已交付，剩余 ${saved.remainingQuantity}，样费未确认`;
}

async function charge() {
  if (amount.value.trim() === '0') {
    message.value = '未知金额不能写成 0';
    return;
  }
  const saved = await settleSamplingFee(samplingCode.value.trim(), deliveryId.value.trim(), {
    requestId: newRequestId(),
    amount: amount.value.trim() ? Number(amount.value) : null,
    waive: false,
    reason: '',
    motherOrderCode: '',
    settleWages: false,
  });
  message.value = `已确认样费 ${saved.amount}`;
}

async function waive() {
  if (!reason.value.trim()) {
    message.value = '请填写免收原因';
    return;
  }
  const saved = await settleSamplingFee(samplingCode.value.trim(), deliveryId.value.trim(), {
    requestId: newRequestId(),
    amount: null,
    waive: true,
    reason: reason.value.trim(),
    motherOrderCode: '',
    settleWages: false,
  });
  message.value = saved.amount == null ? `已免收：${saved.reason}` : '免收不应有金额';
}
</script>

<template>
  <section class="page-stack" aria-label="打样交付和样费">
    <header>
      <h2>分次交付和样费</h2>
      <p>部分交付不结束打样单。免收必须写原因，不记零元，也不抵大货。</p>
    </header>
    <p role="status">{{ message }}</p>
    <label>打样号 <input v-model="samplingCode" aria-label="交付打样号" /></label>
    <label>版本种类 <input v-model="versionKind" aria-label="交付版本种类" /></label>
    <label>版本号 <input v-model="versionNo" aria-label="交付版本号" /></label>
    <label>数量 <input v-model="quantity" aria-label="交付数量" /></label>
    <label>单位 <input v-model="unit" aria-label="交付单位" /></label>
    <label>交付凭据 <input v-model="credentialCode" aria-label="交付凭据" /></label>
    <button type="button" @click="deliver">交付</button>
    <label>交付编号 <input v-model="deliveryId" aria-label="交付编号" /></label>
    <label>样费 <input v-model="amount" aria-label="样费" /></label>
    <button type="button" @click="charge">确认样费</button>
    <label>免收原因 <input v-model="reason" aria-label="免收原因" /></label>
    <button type="button" @click="waive">明确免收</button>
  </section>
</template>
