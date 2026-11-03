<script setup lang="ts">
import { ref, watch } from 'vue';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
import { deliverSampling, settleSamplingFee } from '../../services/samplingApi';

const props = defineProps<{ linkedCode?: string }>();
const samplingCode = ref('');
watch(() => props.linkedCode, (value) => { if (value) samplingCode.value = value; }, { immediate: true });
const versionKind = ref('MODEL');
const versionNo = ref('1');
const quantity = ref('');
const unit = ref('颗');
const credentialCode = ref('');
const deliveryId = ref('');
const amount = ref('');
const reason = ref('');
const message = ref('');
const operation = useBusinessCommand('sampling-delivery', samplingCode, { versionKind, versionNo, quantity, unit, credentialCode, deliveryId, amount, reason }, message);


async function deliver() {
  const counted = Number(quantity.value);
  if (!Number.isFinite(counted) || counted <= 0) {
    message.value = '交付数量必须大于零';
    return;
  }
  const saved = await operation.submit('deliver', {
    versionKind: versionKind.value,
    versionNo: Number(versionNo.value),
    materialUseId: '',
    quantity: counted,
    unit: unit.value.trim(),
    credentialCode: credentialCode.value.trim(),
  }, (code, body) => deliverSampling(code, body));
  if (!saved) return;
  deliveryId.value = saved.deliveryId;
  message.value = saved.orderClosed
    ? '不应把打样单标成完成'
    : saved.remainingQuantity == null ? '交付实物份额待核，样费未确认' : `已交付，剩余 ${saved.remainingQuantity}，样费未确认`;
}

async function charge() {
  if (amount.value.trim() === '0') {
    message.value = '未知金额不能写成 0';
    return;
  }
  const saved = await operation.submit(`fee:${deliveryId.value.trim()}`, {
    amount: amount.value.trim() ? Number(amount.value) : null,
    waive: false,
    reason: '',
    motherOrderCode: '',
    settleWages: false,
  }, (code, body) => settleSamplingFee(code, deliveryId.value.trim(), body));
  if (!saved) return;
  message.value = `已确认样费 ${saved.amount}`;
}

async function waive() {
  if (!reason.value.trim()) {
    message.value = '请填写免收原因';
    return;
  }
  const saved = await operation.submit(`fee:${deliveryId.value.trim()}`, {
    amount: null,
    waive: true,
    reason: reason.value.trim(),
    motherOrderCode: '',
    settleWages: false,
  }, (code, body) => settleSamplingFee(code, deliveryId.value.trim(), body));
  if (!saved) return;
  message.value = saved.amount == null ? `已免收：${saved.reason}` : '免收不应有金额';
}
</script>

<template>
  <section class="job-pane" aria-label="打样交付和样费">
    <header>
      <h2>分次交付和样费</h2>
      <p>部分交付不结束打样单。免收必须写原因，不记零元，也不抵大货。</p>
    </header>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <fieldset class="job-fieldset" :disabled="operation.pending.value">
      <div class="info-fields">
        <label><span>打样号</span><input v-model="samplingCode" aria-label="交付打样号" /></label>
        <label><span>版本种类</span><input v-model="versionKind" aria-label="交付版本种类" /></label>
        <label><span>版本号</span><input v-model="versionNo" aria-label="交付版本号" /></label>
        <label><span>数量</span><input v-model="quantity" aria-label="交付数量" /></label>
        <label><span>单位</span><input v-model="unit" aria-label="交付单位" /></label>
        <label><span>交付凭据</span><input v-model="credentialCode" aria-label="交付凭据" /></label>
      </div>
      <div class="job-toolbar"><button type="button" class="primary-button" @click="deliver">交付</button></div>
      <div class="job-block">
        <h3>样费</h3>
        <div class="info-fields">
          <label><span>交付编号</span><input v-model="deliveryId" aria-label="交付编号" /></label>
          <label><span>样费</span><input v-model="amount" aria-label="样费" /></label>
          <label><span>免收原因</span><input v-model="reason" aria-label="免收原因" /></label>
        </div>
        <div class="job-toolbar">
          <button type="button" class="primary-button" @click="charge">确认样费</button>
          <button type="button" class="secondary-button" @click="waive">明确免收</button>
        </div>
      </div>
    </fieldset>
    <p v-if="operation.pending.value" role="status">正在提交，请稍候</p>
    <div class="job-actions">
      <button v-if="operation.hasUnconfirmed.value" type="button" class="secondary-button" @click="operation.restorePending">恢复上次填写</button>
      <button type="button" class="secondary-button" @click="operation.startNext">开始下一笔</button>
    </div>
  </section>
</template>
