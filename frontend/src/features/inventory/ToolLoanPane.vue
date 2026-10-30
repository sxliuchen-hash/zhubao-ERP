<script setup lang="ts">
import { ref } from 'vue';
import { borrowTool, returnTool } from '../../services/toolLoanApi';

export interface ToolHolder {
  holderId: string;
  holderName: string;
  outstanding: string;
  unit: string;
}

const props = defineProps<{
  itemId: string;
  warehouseCode: string;
  stockQuantity: string;
  unit: string;
  holders: ToolHolder[];
  loanId: string;
  loanVersion: number;
}>();

const holderId = ref('');
const holderName = ref('');
const quantity = ref('');
const returnQuantity = ref('');
const message = ref('');

async function submitBorrow() {
  if (!holderId.value.trim()) {
    message.value = '请选择持有人编号';
    return;
  }
  await borrowTool({
    requestId: `borrow-${holderId.value}`,
    itemId: props.itemId,
    warehouseCode: props.warehouseCode,
    holderId: holderId.value,
    holderName: holderName.value,
    quantity: quantity.value,
    pieceCode: '',
  });
  message.value = '已借出，所有权仍是公司';
}

async function submitReturn() {
  await returnTool(props.loanId, {
    requestId: `return-${props.loanId}`,
    version: props.loanVersion,
    quantity: returnQuantity.value,
  });
  message.value = '已按原借出归还';
}
</script>

<template>
  <section aria-label="工具借还">
    <p>库内 {{ warehouseCode }} {{ stockQuantity }}{{ unit }}</p>
    <p v-for="holder in holders" :key="holder.holderId">
      未还 {{ holder.holderId }} {{ holder.holderName }} {{ holder.outstanding }}{{ holder.unit }}
    </p>
    <label>持有人编号 <input v-model="holderId" aria-label="持有人编号" /></label>
    <label>姓名 <input v-model="holderName" aria-label="持有人姓名" /></label>
    <label>借出数量 <input v-model="quantity" aria-label="借出数量" /></label>
    <button type="button" @click="submitBorrow">借出</button>
    <label>归还数量 <input v-model="returnQuantity" aria-label="归还数量" /></label>
    <button type="button" @click="submitReturn">归还</button>
    <p v-if="message">{{ message }}</p>
  </section>
</template>
