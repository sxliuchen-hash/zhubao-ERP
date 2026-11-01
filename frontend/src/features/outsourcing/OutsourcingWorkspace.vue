<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  fetchOutsourcingPosition, fetchOutsourcingReturns, confirmOutsourcingCompletion, type OutsourcingReturnCompletion,
  submitOutsourcingDispatch,
  submitOutsourcingReturn,
  type OutsourcingPosition,
} from '../../services/outsourcingApi';

const props = defineProps<{ orderCode: string; active: boolean }>();
const emit = defineEmits<{ changed: [] }>();

const completionRows = ref<OutsourcingReturnCompletion[]>([]);
const completionPending = ref(false);
async function loadCompletions() {
  if (!position.value || !matchesOrder.value) return;
  try { completionRows.value = await fetchOutsourcingReturns(position.value.taskId); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '读取收回明细失败'; }
}
async function complete(returnId: string) {
  if (completionPending.value) return; completionPending.value = true;
  try { await confirmOutsourcingCompletion({ requestId: `completion-${returnId}`, returnId }); await loadCompletions(); emit('changed'); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '完工确认失败，请重试原操作'; }
  finally { completionPending.value = false; }
}
const taskId = ref('');
const position = ref<OutsourcingPosition | null>(null);
const error = ref('');
const bagId = ref('');
const quantity = ref('');
const dispatchId = ref('');
const received = ref('');
const unprocessed = ref('');
const loss = ref('');
const weight = ref('');
const dispatchRequestId = ref('');
const returnRequestId = ref('');

const inHouseBags = computed(() => position.value?.sources.filter(line => line.inHouseStock) ?? []);
const openLines = computed(() => position.value?.dispatches.filter(line => line.stillOut > 0) ?? []);
const matchesOrder = computed(() => !position.value || position.value.targetCode === props.orderCode);
const sourceKeys = computed(() => [...new Set((position.value?.sources ?? []).map(line => line.sourceKey).filter(Boolean))]);

function nextRequestId(): string {
  return `os-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

async function load() {
  error.value = '';
  position.value = null;
  if (!taskId.value.trim()) {
    error.value = '请填写外协任务号';
    return;
  }
  try {
    position.value = await fetchOutsourcingPosition(taskId.value.trim());
    if (position.value.targetCode !== props.orderCode) {
      error.value = '这张外协任务不属于当前单据';
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取外协任务失败';
  }
}

async function dispatchBag() {
  if (!position.value || !matchesOrder.value || !bagId.value) return;
  const amount = Number(quantity.value);
  if (!Number.isFinite(amount) || amount <= 0) {
    error.value = '发外数量必须为正';
    return;
  }
  if (!dispatchRequestId.value) dispatchRequestId.value = nextRequestId();
  error.value = '';
  try {
    const bag = inHouseBags.value.find(line => line.bagId === bagId.value);
    await submitOutsourcingDispatch({
      requestId: dispatchRequestId.value,
      taskId: position.value.taskId,
      bagId: bagId.value,
      bagVersion: bag?.version ?? 1,
      quantity: amount,
    });
    dispatchRequestId.value = '';
    quantity.value = '';
    emit('changed');
    await load();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '发外失败';
  }
}

async function receive() {
  const line = openLines.value.find(item => item.dispatchId === dispatchId.value);
  if (!position.value || !matchesOrder.value || !line) return;
  if (!returnRequestId.value) returnRequestId.value = nextRequestId();
  error.value = '';
  try {
    await submitOutsourcingReturn({
      requestId: returnRequestId.value,
      dispatchId: line.dispatchId,
      dispatchVersion: line.version,
      received: Number(received.value || 0),
      unprocessed: Number(unprocessed.value || 0),
      loss: Number(loss.value || 0),
      weight: weight.value.trim() === '' ? null : Number(weight.value),
    });
    returnRequestId.value = '';
    received.value = '';
    unprocessed.value = '';
    loss.value = '';
    weight.value = '';
    emit('changed');
    await load();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '收回失败';
  }
}
</script>

<template>
  <section v-if="active" aria-label="外协发外与收回">
    <p>当前单据 {{ orderCode }}</p>
    <label>外协任务号 <input v-model="taskId" aria-label="外协任务号" /></label>
    <button type="button" @click="load">读取任务</button>
    <p v-if="error" role="alert">{{ error }}</p>
    <template v-if="position && matchesOrder">
      <p>外协方 {{ position.partyName }}</p>
      <p>权属 {{ position.ownerType }} {{ position.ownerRef }}</p>
      <p>来源 {{ sourceKeys.join('、') || '未记录' }}</p>
      <p>在外 {{ position.outQuantity }}</p>
      <p>待核 {{ position.pendingQuantity }}</p>
      <p v-if="position.outsourcingCountsAsInHouseStock">在外不能当成厂内现货</p>
      <button type="button" @click="loadCompletions">读取收回完工明细</button>
      <p v-for="row in completionRows" :key="row.returned.returnId">{{ row.returned.returnId }} · 实收 {{ row.returned.receivedQty }} · 未加工退回 {{ row.returned.unprocessedQty }}
        <span v-if="row.completed">已确认完工</span><button v-else-if="row.returned.receivedQty > 0" type="button" :disabled="completionPending" @click="complete(row.returned.returnId)">确认实收部分完工</button>
      </p>
      <label>本厂袋
        <select v-model="bagId" aria-label="本厂袋">
          <option value="">选择仍在本厂的袋</option>
          <option v-for="line in inHouseBags" :key="line.bagId" :value="line.bagId">{{ line.bagId }} · {{ line.quantity }}</option>
        </select>
      </label>
      <label>发外数量 <input v-model="quantity" aria-label="发外数量" inputmode="decimal" /></label>
      <button type="button" :disabled="!bagId" @click="dispatchBag">发外</button>
      <label>原发外行
        <select v-model="dispatchId" aria-label="原发外行">
          <option value="">选择仍在外的发外行</option>
          <option v-for="line in openLines" :key="line.dispatchId" :value="line.dispatchId">{{ line.dispatchId }} · 仍在外 {{ line.stillOut }}</option>
        </select>
      </label>
      <label>实收 <input v-model="received" aria-label="实收数量" inputmode="decimal" /></label>
      <label>未加工退回 <input v-model="unprocessed" aria-label="未加工退回" inputmode="decimal" /></label>
      <label>已确认损耗 <input v-model="loss" aria-label="已确认损耗" inputmode="decimal" /></label>
      <label>实重 <input v-model="weight" aria-label="实重" inputmode="decimal" placeholder="不填表示未知重量" /></label>
      <button type="button" :disabled="!dispatchId" @click="receive">按原行收回</button>
    </template>
  </section>
</template>
