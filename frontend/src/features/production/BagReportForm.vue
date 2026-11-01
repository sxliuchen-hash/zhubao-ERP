<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { fetchBagAvailability, submitWholeBagReport, submitQuantityDisposition } from '../../services/productionQualityApi';
import ReportPhotoCapture from '../evidence/ReportPhotoCapture.vue';
import { useBusinessCommand } from '../workspace/useBusinessCommand';

const props = defineProps<{ bagId: string }>();
const emit = defineEmits<{ saved: []; locked: [value: boolean] }>();
const quantity = ref('');
const attachmentCode = ref('');
const loss = ref('0');
const message = ref('');
const available = ref('');
const block = ref('');
const bagVersion = ref('');
const expectedStepId = ref('');
const stepName = ref('');
const targetCode = ref('');
const completed = ref(false);
const bagKey = computed(() => props.bagId);
const command = useBusinessCommand('bag-report', bagKey, { quantity, attachmentCode, loss, bagVersion, expectedStepId, targetCode, stepName, available }, message);
const { pending, hasUnconfirmed } = command;
watch(() => pending.value || hasUnconfirmed.value, value => emit('locked', value), { immediate: true });
let sequence = 0;
watch(bagKey, () => { completed.value = false; if (!hasUnconfirmed.value) available.value = ''; block.value = ''; void readAvailability(); }, { immediate: true });

async function readAvailability() {
  const bagId = props.bagId; const read = ++sequence;
  try {
    const view = await fetchBagAvailability(bagId);
    if (read !== sequence) return;
    if (!hasUnconfirmed.value) available.value = String(view.availableQuantity ?? '');
    block.value = view.blockingReasons?.[0] ?? '';
    if (!hasUnconfirmed.value && !pending.value && !completed.value) {
      bagVersion.value = String(view.bagVersion);
      expectedStepId.value = view.stepId;
      stepName.value = view.stepName;
      targetCode.value = view.targetCode;
      if (!quantity.value) quantity.value = available.value;
    }
  } catch (error) { if (read === sequence) block.value = error instanceof Error ? error.message : '无法读取可处理量'; }
}
async function next() {
  command.startNext();
  if (hasUnconfirmed.value || pending.value) return;
  quantity.value = ''; loss.value = '0'; attachmentCode.value = ''; completed.value = false;
  await readAvailability();
}
async function submit() {
  if (!attachmentCode.value) { message.value = '请先上传本次报工照片'; return; }
  const worked = Number(quantity.value); const lost = Number(loss.value);
  if (!quantity.value.trim() || !Number.isFinite(worked) || worked <= 0 || !Number.isFinite(lost) || lost < 0 || lost > worked || worked > Number(available.value)) {
    message.value = '请核对实际作业数量与损耗，不能超过可处理量'; return;
  }
  if (!expectedStepId.value || !bagVersion.value) { message.value = '请先读取当前工序'; return; }
  const common = { bagId: props.bagId, bagVersion: Number(bagVersion.value), attachmentCode: attachmentCode.value };
  const result = worked === Number(available.value) && lost === 0
    ? await command.submit('whole', { ...common, expectedStepId: expectedStepId.value, quantity: { confirmedQuantity: worked }, progressPercent: null }, (_code, body) => submitWholeBagReport(body))
    : await command.submit('partial', { ...common, handoff: Number((worked - lost).toFixed(3)), loss: lost, scrap: 0, returned: 0, pending: 0,
        exitWeight: { state: 'UNKNOWN' as const, value: null }, stockFrom: null, workQuantity: worked }, (_code, body) => submitQuantityDisposition(body));
  if (result) { completed.value = true; message.value = '本次报工已提交；继续办理请开始下一笔'; emit('saved'); }
}
</script>
<template>
  <form class="bag-report" aria-label="整袋报工" @submit.prevent="submit">
    <p v-if="block" role="alert">{{ block }}</p>
    <p v-if="stepName">当前工序 {{ stepName }}</p>
    <p v-if="available">可处理 {{ available }}</p>
    <label>本次实际作业数量（含损耗）<input v-model="quantity" aria-label="整袋数量" inputmode="decimal" /></label>
    <label>其中损耗<input v-model="loss" aria-label="损耗数量" inputmode="decimal" /></label>
    <p>本次交出 {{ Math.max(0, Number(quantity || 0) - Number(loss || 0)) }}；未处理部分留待下一次报工</p>
    <ReportPhotoCapture v-if="targetCode" v-model="attachmentCode" :business-type="targetCode.startsWith('ZD-') ? 'WORK_ORDER' : 'MOTHER_ORDER'" :business-code="targetCode" :draft-key="bagId" />
    <p v-if="message" role="status">{{ message }}</p>
    <button type="submit" :disabled="pending || completed">{{ pending ? '正在提交' : hasUnconfirmed ? '原请求重试' : '一次提交' }}</button>
    <button v-if="hasUnconfirmed" type="button" @click="command.restorePending">恢复上次填写</button>
    <button type="button" :disabled="pending || hasUnconfirmed" @click="next">开始下一笔</button>
  </form>
</template>
