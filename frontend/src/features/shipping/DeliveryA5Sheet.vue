<script setup lang="ts">
import { computed, ref } from 'vue';
import { buildA5Pages, renderA5Markup, type A5BagLine } from './deliveryA5';

const props = defineProps<{
  deliveryId: string;
  customerName: string;
  logistics: string;
  lines: A5BagLine[];
  feeAmount: string | null;
  feeText?: string;
  publicHref?: string;
  officePrinters: string[];
}>();

const emit = defineEmits<{ print: [printer: string] }>();
const printer = ref(props.officePrinters[0] ?? '');
const pages = computed(() => buildA5Pages(props));
const markup = computed(() => pages.value.map(page => renderA5Markup({ ...page, feeText: props.feeText ?? page.feeText })).join(''));

function printOffice() {
  if (!printer.value) return;
  emit('print', printer.value);
}
</script>

<template>
  <section aria-label="A5发货清单">
    <label>办公打印机
      <select v-model="printer" aria-label="办公打印机">
        <option v-for="name in officePrinters" :key="name" :value="name">{{ name }}</option>
      </select>
    </label>
    <button type="button" @click="printOffice">用办公打印机打印A5</button>
    <div v-html="markup"></div>
  </section>
</template>
