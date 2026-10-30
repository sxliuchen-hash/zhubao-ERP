<script setup lang="ts">
import { computed } from 'vue';
import { parseDeliveryScan, presentPublicDelivery } from './publicDelivery';

const props = defineProps<{
  location: string;
  disabled: boolean;
  bagCodes: string[];
  photoIds: string[];
  deliveryId: string;
  loggedIn: boolean;
  roleAllowsFinance: boolean;
  device: string;
  network: string;
}>();

const parsed = computed(() => parseDeliveryScan(props.location));
const view = computed(() => presentPublicDelivery(props));
</script>

<template>
  <section aria-label="公开交付">
    <p v-if="parsed.kind === 'factory'">厂内工单码 {{ parsed.raw }}</p>
    <template v-else-if="parsed.kind === 'public'">
      <p>状态：{{ view.status }}</p>
      <ul>
        <li v-for="bag in view.bagCodes" :key="bag">{{ bag }}</li>
      </ul>
      <a v-if="view.internalHref" :href="view.internalHref">查看内部加工</a>
      <a v-if="view.financeHref" :href="view.financeHref">查看允许的账单</a>
    </template>
    <p v-else>无法识别</p>
  </section>
</template>
