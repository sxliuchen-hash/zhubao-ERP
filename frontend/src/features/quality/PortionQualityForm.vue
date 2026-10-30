<script setup lang="ts">
import { ref, watch } from 'vue';
import { fetchShippablePortion, submitPortionInspection, submitPortionRelease, submitPortionRework } from '../../services/productionQualityApi';

const props = defineProps<{ bagId: string }>();
const quantity = ref('');
const reasons = ref<string[]>([]);
const clauses = ref('');
const pass = ref('');
const rework = ref('');
const message = ref('');
const reworkBagId = ref('');
const requestId = ref('');

watch(() => props.bagId, bagId => {
  requestId.value = `qc-${bagId}`;
  reworkBagId.value = '';
  message.value = '';
  void load(bagId);
}, { immediate: true });

async function load(bagId: string) {
  try {
    const view = await fetchShippablePortion(bagId);
    if (props.bagId !== bagId) return;
    quantity.value = String(view?.quantity ?? '');
    reasons.value = view?.blockingReasons ?? [];
  } catch (caught) {
    if (props.bagId !== bagId) return;
    reasons.value = [caught instanceof Error ? caught.message : '无法读取可发条件'];
  }
}

async function inspect() {
  const result = await submitPortionInspection({
    requestId: requestId.value,
    bagId: props.bagId,
    bagVersion: 1,
    requirementVersion: 'REQ',
    pass: Number(pass.value || 0),
    rework: Number(rework.value || 0),
    scrap: 0,
    pending: 0,
    failedClauses: clauses.value.split('、').map(item => item.trim()).filter(Boolean),
  });
  message.value = result?.inspectionId ? '已记录本次份额' : '已提交';
}

async function release() {
  await submitPortionRelease({ requestId: `${requestId.value}-rel`, bagId: props.bagId, bagVersion: 1, quantity: Number(pass.value || 0) });
  message.value = '已按合格数量放行';
}

async function openRework() {
  const task = await submitPortionRework({ requestId: `${requestId.value}-rw`, bagId: props.bagId, bagVersion: 1, round: 1 });
  reworkBagId.value = task.reworkBagId;
}
</script>

<template>
  <section aria-label="按袋质检">
    <p>本袋可发 {{ quantity || '待核' }}</p>
    <ul v-if="reasons.length">
      <li v-for="reason in reasons" :key="reason">{{ reason }}</li>
    </ul>
    <label>条款<input v-model="clauses" aria-label="检查条款" placeholder="用顿号分开" /></label>
    <label>合格数量<input v-model="pass" aria-label="合格数量" inputmode="decimal" /></label>
    <label>返工数量<input v-model="rework" aria-label="返工数量" inputmode="decimal" /></label>
    <p>返工去向 {{ reworkBagId || '尚未开出' }}</p>
    <button type="button" @click="inspect">提交本次质检</button>
    <button type="button" @click="release">放行合格部分</button>
    <button type="button" @click="openRework">开出返工份额</button>
    <p v-if="message" role="status">{{ message }}</p>
  </section>
</template>
