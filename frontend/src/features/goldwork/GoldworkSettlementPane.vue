<script setup lang="ts">
import { ref, watch } from 'vue';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
import { deliverGoldwork } from '../../services/goldworkApi';

const props = defineProps<{ linkedCode?: string }>();
const goldworkCode = ref('');
watch(() => props.linkedCode, (value) => { if (value) goldworkCode.value = value; }, { immediate: true });
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
  <section class="job-pane" aria-label="金工交付">
    <header>
      <h2>交付</h2>
      <p>只交付已放行份额。不能按克数乘金价，费用未确认不是 0 元应收，也不写普通发货。</p>
    </header>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <fieldset class="job-fieldset" :disabled="operation.pending.value">
      <div class="info-fields">
        <label><span>金工号</span><input v-model="goldworkCode" aria-label="交付金工号" /></label>
        <label><span>放行份额</span><input v-model="qualityId" aria-label="放行份额" /></label>
        <label><span>净重</span><input v-model="netWeight" aria-label="交付净重" /></label>
        <label><span>单位</span><input v-model="weightUnit" aria-label="交付单位" /></label>
      </div>
      <div class="job-toolbar"><button type="button" class="primary-button" @click="deliver">交付</button></div>
    </fieldset>
    <p v-if="operation.pending.value" role="status">正在提交，请稍候</p>
    <div class="job-actions">
      <button v-if="operation.hasUnconfirmed.value" type="button" class="secondary-button" @click="operation.restorePending">恢复上次填写</button>
      <button type="button" class="secondary-button" @click="operation.startNext">开始下一笔</button>
    </div>
  </section>
</template>
