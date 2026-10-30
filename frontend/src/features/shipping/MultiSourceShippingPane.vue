<script setup lang="ts">
import { computed, ref, watch } from 'vue';

export interface ShipBagChoice {
  bagId: string;
  version: number;
  quantity: string;
  unit: string;
  shippable: boolean;
  reason: string;
}

const props = defineProps<{
  orderCode: string;
  bags: ShipBagChoice[];
  confirm: (requestId: string, bagIds: string[]) => Promise<{ deliveryId: string }>;
}>();

const selected = ref<string[]>([]);
const checked = ref(false);
const requestId = ref('');
const deliveryId = ref('');
const message = ref('');

watch(() => props.orderCode, () => {
  selected.value = [];
  checked.value = false;
  requestId.value = newRequest();
  deliveryId.value = '';
  message.value = '';
}, { immediate: true });

const chosen = computed(() => props.bags.filter(bag => selected.value.includes(bag.bagId)));
const blocked = computed(() => chosen.value.some(bag => !bag.shippable));

function newRequest() {
  return `ship-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function toggle(bagId: string) {
  selected.value = selected.value.includes(bagId)
    ? selected.value.filter(id => id !== bagId)
    : [...selected.value, bagId];
  checked.value = false;
  deliveryId.value = '';
}

async function submit() {
  if (!checked.value || blocked.value || chosen.value.length === 0) return;
  message.value = '';
  try {
    const result = await props.confirm(requestId.value, chosen.value.map(bag => bag.bagId));
    deliveryId.value = result.deliveryId;
    message.value = '';
  } catch (error) {
    deliveryId.value = '';
    message.value = error instanceof Error ? error.message : '确认失败';
  }
}
</script>

<template>
  <section aria-label="多来源发货">
    <h3>选择可发袋</h3>
    <p v-for="bag in bags" :key="bag.bagId">
      <label>
        <input type="checkbox" :aria-label="`选择${bag.bagId}`" :checked="selected.includes(bag.bagId)" @change="toggle(bag.bagId)" />
        {{ bag.bagId }} {{ bag.quantity }}{{ bag.unit }}
      </label>
      <span v-if="!bag.shippable">{{ bag.reason }}</span>
    </p>
    <label><input v-model="checked" type="checkbox" aria-label="已核对所选袋" /> 已核对数量、版本和客户</label>
    <button type="button" :disabled="!checked || blocked || chosen.length === 0" @click="submit">确认发货</button>
    <button type="button" @click="message = '打印不会出库'">预览发货贴纸</button>
    <p v-if="deliveryId">发货单号 {{ deliveryId }}</p>
    <p v-if="message">{{ message }}</p>
    <button v-if="message && !deliveryId" type="button" @click="submit">原请求重试</button>
  </section>
</template>
