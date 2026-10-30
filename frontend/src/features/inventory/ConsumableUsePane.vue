<script setup lang="ts">
import { ref, watch } from 'vue';
import { consumeConsumable, issueConsumable } from '../../services/consumableStockApi';

const props = defineProps<{
  itemId: string;
  receiptId: string;
  issueId: string;
  issueVersion: number;
  timing: 'UNDECIDED' | 'ON_ISSUE' | 'ON_ACTUAL_USE';
}>();

const quantity = ref('');
const amount = ref('');
const message = ref('');

watch(() => props.itemId, () => {
  quantity.value = '';
  amount.value = '';
  message.value = '';
});

async function submit() {
  if (props.timing === 'UNDECIDED') {
    message.value = '耗材计耗方式尚未选定';
    return;
  }
  if (!quantity.value.trim()) {
    message.value = '请填写数量';
    return;
  }
  if (props.timing === 'ON_ISSUE') {
    await issueConsumable({
      requestId: `cons-issue-${props.itemId}`,
      itemId: props.itemId,
      receiptId: props.receiptId,
      workshopCode: 'WS-01',
      quantity: quantity.value,
    });
    message.value = '领用即耗已提交，不生成客户收费';
    return;
  }
  await consumeConsumable(props.issueId, {
    requestId: `cons-use-${props.issueId}`,
    version: props.issueVersion,
    quantity: quantity.value,
  });
  message.value = amount.value.trim() ? '实际耗用已提交' : '金额未齐';
}
</script>

<template>
  <section aria-label="耗材耗用">
    <p v-if="timing === 'UNDECIDED'">耗材计耗方式尚未选定</p>
    <p v-else-if="timing === 'ON_ISSUE'">领用即耗，不重复计费</p>
    <p v-else>领用只转到车间，实际耗用才计费用</p>
    <label>数量 <input v-model="quantity" aria-label="耗用数量" /></label>
    <label>金额 <input v-model="amount" aria-label="耗用金额" placeholder="未齐" /></label>
    <button type="button" @click="submit">提交</button>
    <p v-if="message">{{ message }}</p>
  </section>
</template>
