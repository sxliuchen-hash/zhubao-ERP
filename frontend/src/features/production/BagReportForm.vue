<script setup lang="ts">
import { ref, watch } from 'vue';
import { fetchBagAvailability, submitWholeBagReport } from '../../services/productionQualityApi';
import { clearBagDraft, loadBagDraft, saveBagDraft } from './bagReportDraft';

const props = defineProps<{ bagId: string }>();
const quantity = ref('');
const photoName = ref('');
const requestId = ref('');
const loss = ref('');
const message = ref('');
const pending = ref(false);
const available = ref('');
const block = ref('');
let currentBag = '';

watch(() => props.bagId, (bagId, previous) => {
  if (previous && requestId.value) {
    saveBagDraft({ bagId: previous, requestId: requestId.value, quantity: quantity.value, photoName: photoName.value, loss: loss.value, pendingResult: pending.value });
  }
  currentBag = bagId;
  const draft = loadBagDraft(bagId);
  quantity.value = draft.quantity;
  photoName.value = draft.photoName;
  requestId.value = draft.requestId;
  loss.value = draft.loss;
  pending.value = draft.pendingResult;
  message.value = draft.pendingResult ? '结果待核对，请用原请求重试' : '';
  void readAvailability(bagId);
}, { immediate: true });

async function readAvailability(bagId: string) {
  try {
    const view = await fetchBagAvailability(bagId);
    if (currentBag !== bagId) return;
    available.value = String(view.availableQuantity ?? '');
    block.value = view.blockingReasons?.[0] ?? '';
    if (!quantity.value && available.value) quantity.value = available.value;
  } catch (caught) {
    if (currentBag !== bagId) return;
    block.value = caught instanceof Error ? caught.message : '无法读取可处理量';
  }
}

function onPhoto(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (file) photoName.value = file.name;
  remember();
}

function remember() {
  saveBagDraft({ bagId: props.bagId, requestId: requestId.value, quantity: quantity.value, photoName: photoName.value, loss: loss.value, pendingResult: pending.value });
}

async function submit() {
  remember();
  if (!photoName.value) {
    message.value = '请先保留本次照片';
    return;
  }
  pending.value = true;
  remember();
  try {
    await submitWholeBagReport({
      requestId: requestId.value,
      bagId: props.bagId,
      bagVersion: 1,
      quantity: { confirmedQuantity: Number(quantity.value) },
      attachmentCode: photoName.value,
      progressPercent: null,
    });
    pending.value = false;
    message.value = '已提交';
    clearBagDraft(props.bagId);
  } catch {
    pending.value = true;
    message.value = '结果待核对，请用原请求重试';
    remember();
  }
}
</script>

<template>
  <form class="bag-report" aria-label="整袋报工" @submit.prevent="submit">
    <p v-if="block" role="alert">{{ block }}</p>
    <p v-if="available">可处理 {{ available }}</p>
    <label>本次数量<input v-model="quantity" aria-label="整袋数量" inputmode="decimal" @change="remember" /></label>
    <label>本次照片<input aria-label="整袋照片" type="file" accept="image/*" @change="onPhoto" /></label>
    <p v-if="photoName">未上传照片 {{ photoName }}</p>
    <details>
      <summary>异常去向</summary>
      <label>损耗<input v-model="loss" aria-label="损耗数量" @change="remember" /></label>
    </details>
    <p v-if="message" role="status">{{ message }}</p>
    <button type="submit">{{ pending ? '原请求重试' : '一次提交' }}</button>
  </form>
</template>
