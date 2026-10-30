<script setup lang="ts">
import { ref } from 'vue';
import { confirmStocktake } from '../../services/stocktakeApi';

const props = defineProps<{
  stocktakeId: string;
  version: number;
  bookQuantity: string;
  outstandingQuantity: string;
  unit: string;
  canConfirm: boolean;
}>();

const counted = ref('');
const confirmed = ref(false);
const message = ref('');

async function submit() {
  if (!props.canConfirm) {
    message.value = '没有差异确认权限';
    return;
  }
  if (confirmed.value) {
    message.value = '盘点已经确认';
    return;
  }
  if (!counted.value.trim()) {
    message.value = '请填写实盘数量';
    return;
  }
  await confirmStocktake(props.stocktakeId, { requestId: `count-${props.stocktakeId}`, version: props.version });
  confirmed.value = true;
  message.value = '已确认差额，原账面保留';
}
</script>

<template>
  <section aria-label="盘点复核">
    <p>账面 {{ bookQuantity }}{{ unit }}</p>
    <p>未还 {{ outstandingQuantity }}{{ unit }}，不计入本次实盘</p>
    <label>实盘数量 <input v-model="counted" aria-label="实盘数量" /></label>
    <button type="button" @click="submit">确认差额</button>
    <p v-if="message">{{ message }}</p>
  </section>
</template>
