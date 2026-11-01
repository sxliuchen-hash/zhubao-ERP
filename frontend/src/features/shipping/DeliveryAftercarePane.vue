<script setup lang="ts">
import { computed, ref } from 'vue';

const props = defineProps<{
  deliveryId: string;
  quantityDraft?: string;
  disabled?: boolean;
  originalQuantity: string;
  returnedQuantity: string;
  hasCharge: boolean;
  hasRefund: boolean;
  correct: (intent: 'REVERSE_BEFORE_HANDOVER' | 'OVERWRITE_QUANTITY' | 'CANCEL_AND_REOPEN') => Promise<void>;
  receive: (quantity: string) => Promise<void>;
}>();

const emit = defineEmits<{ 'update:quantityDraft': [quantity: string] }>();
const message = ref('');
const entered = ref('');
const returnQuantity = computed({ get: () => props.quantityDraft ?? entered.value, set: value => { entered.value = value; emit('update:quantityDraft', value); } });
const returnedNow = ref(false);

const remaining = computed(() => String(Number(props.originalQuantity) - Number(props.returnedQuantity)));

async function ask(intent: 'REVERSE_BEFORE_HANDOVER' | 'OVERWRITE_QUANTITY' | 'CANCEL_AND_REOPEN') {
  if (props.disabled) return;
  message.value = '';
  try {
    await props.correct(intent);
  } catch (error) {
    message.value = error instanceof Error ? error.message : '不能办理';
  }
}

async function submitReturn() {
  if (props.disabled) return;
  message.value = '';
  if (!Number.isFinite(Number(returnQuantity.value)) || Number(returnQuantity.value) <= 0 || !returnQuantity.value.trim() || Number(returnQuantity.value) > Number(remaining.value)) {
    message.value = '退货数量不得超过有效原交付';
    return;
  }
  try {
    await props.receive(returnQuantity.value.trim());
    returnedNow.value = true;
    message.value = '已收回，待复核，原发货单仍在';
  } catch (error) {
    returnedNow.value = false;
    message.value = error instanceof Error ? error.message : '退货失败';
  }
}
</script>

<template>
  <section aria-label="交付更正与退货">
    <h3>交付更正与退货</h3>
    <p>原发货单 {{ deliveryId }} · 原数量 {{ originalQuantity }} · 已退 {{ returnedQuantity }}</p>
    <p v-if="hasCharge">已确认费用仍保留</p>
    <p v-if="hasRefund">已确认退款仍保留</p>
    <button type="button" :disabled="disabled" @click="ask('OVERWRITE_QUANTITY')">更正数量</button>
    <button type="button" :disabled="disabled" @click="ask('CANCEL_AND_REOPEN')">取消并重开</button>
    <button type="button" :disabled="disabled" @click="ask('REVERSE_BEFORE_HANDOVER')">按未交接取消</button>
    <label>退货数量<input v-model="returnQuantity" aria-label="退货数量" /></label>
    <button type="button" :disabled="disabled" @click="submitReturn">办理退货</button>
    <p v-if="message">{{ message }}</p>
    <p v-if="returnedNow">状态：待复核</p>
  </section>
</template>
