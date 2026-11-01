<script setup lang="ts">
import { computed, ref, watch } from 'vue';

/** 本次交付的一条收费草稿。空单价保持空白，不补 0。 */
export interface DeliveryChargeDraftLine {
  name: string;
  targetCode?: string;
  quantityUnit?: string;
  method: 'QUANTITY' | 'BASE_MINUS_QUANTITY' | 'AGREED_AMOUNT' | 'ADJUSTMENT';
  quantity: string;
  unitPrice: string;
  baseQuantity: string;
  exemptQuantity: string;
  directAmount: string;
  direction: '' | 'SURCHARGE' | 'DEDUCTION';
  basis: string;
}

export interface DeliveryChargeSaved {
  deliveryId: string;
  version: number;
  lines: DeliveryChargeDraftLine[];
}

const props = defineProps<{
  deliveryId: string;
  customerName: string;
  shippedQuantity: string;
  quantityUnit: string;
  saved?: DeliveryChargeSaved | null;
  actionLabel?: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  confirm: [payload: DeliveryChargeSaved];
  save: [payload: { deliveryId: string; version: number; lines: DeliveryChargeDraftLine[] }];
}>();

const version = ref(1);
const lines = ref<DeliveryChargeDraftLine[]>([blankLine()]);

watch(() => props.deliveryId, () => {
  version.value = 1;
  lines.value = [blankLine()];
}, { immediate: true });

watch(() => props.saved, (saved) => {
  if (!saved || saved.deliveryId !== props.deliveryId) return;
  version.value = saved.version;
  lines.value = saved.lines.map(line => ({ ...line }));
}, { immediate: true });

const expressions = computed(() => lines.value.map(expressionOf));

function blankLine(): DeliveryChargeDraftLine {
  return {
    name: '', method: 'QUANTITY', quantity: '', unitPrice: '', baseQuantity: '',
    exemptQuantity: '', directAmount: '', direction: '', basis: '',
  };
}

function expressionOf(line: DeliveryChargeDraftLine) {
  if (line.method === 'QUANTITY') {
    if (!line.quantity.trim() || !line.unitPrice.trim()) return '';
    return `${line.quantity}${line.quantityUnit || props.quantityUnit} × ${line.unitPrice}元／${line.quantityUnit || props.quantityUnit}`;
  }
  if (line.method === 'BASE_MINUS_QUANTITY') {
    if (!line.baseQuantity.trim() || !line.exemptQuantity.trim() || !line.unitPrice.trim()) return '';
    return `（${line.baseQuantity} − ${line.exemptQuantity}）${line.quantityUnit || props.quantityUnit} × ${line.unitPrice}元`;
  }
  if (line.method === 'AGREED_AMOUNT') return line.directAmount.trim() ? `议定 ${line.directAmount}元` : '';
  if (!line.directAmount.trim() && !(line.quantity.trim() && line.unitPrice.trim())) return '';
  return line.directAmount.trim() ? `调整 ${line.directAmount}元` : `${line.quantity} × ${line.unitPrice}`;
}

function save(confirmed = false) {
  const payload = { deliveryId: props.deliveryId, version: version.value, lines: lines.value.map(line => ({ ...line })) };
  if (confirmed) emit('confirm', payload); else emit('save', payload);
}
</script>

<template>
  <section aria-label="本次交付计费">
    <h3>本次交付计费</h3>
    <p>{{ customerName }} · {{ deliveryId }} · 实发 {{ shippedQuantity }}{{ quantityUnit }} · 版本 {{ version }}</p>
    <p v-for="(line, index) in lines" :key="index">
      <label>加工来源<input v-model="line.targetCode" :aria-label="`第${index + 1}个来源`" /></label>
      <label>单位<input v-model="line.quantityUnit" :aria-label="`第${index + 1}个单位`" /></label>
      <label>项目<input v-model="line.name" :aria-label="`第${index + 1}个项目`" /></label>
      <label>方式
        <select v-model="line.method" :aria-label="`第${index + 1}个计费方式`">
          <option value="QUANTITY">数量单价</option>
          <option value="BASE_MINUS_QUANTITY">基数扣数量</option>
          <option value="AGREED_AMOUNT">议定金额</option>
          <option value="ADJUSTMENT">补收或扣减</option>
        </select>
      </label>
      <label>数量<input v-model="line.quantity" :aria-label="`第${index + 1}个数量`" /></label>
      <label>单价<input v-model="line.unitPrice" :aria-label="`第${index + 1}个单价`" /></label>
      <label v-if="line.method === 'BASE_MINUS_QUANTITY'">基数<input v-model="line.baseQuantity" :aria-label="`第${index + 1}个基数`" /></label>
      <label v-if="line.method === 'ADJUSTMENT'">方向<select v-model="line.direction"><option value="">请选择</option><option value="SURCHARGE">补收</option><option value="DEDUCTION">扣减</option></select></label>
      <label v-if="line.method === 'BASE_MINUS_QUANTITY'">免计<input v-model="line.exemptQuantity" :aria-label="`第${index + 1}个免计`" /></label>
      <label v-if="line.method === 'AGREED_AMOUNT' || line.method === 'ADJUSTMENT'">金额<input v-model="line.directAmount" :aria-label="`第${index + 1}个金额`" /></label>
      <label v-if="line.method !== 'QUANTITY'">依据<input v-model="line.basis" :aria-label="`第${index + 1}个依据`" /></label>
      <span>{{ expressions[index] }}</span>
    </p>
    <button type="button" :disabled="disabled" @click="lines.push(blankLine())">增加收费项目</button>
    <button v-if="actionLabel" type="button" :disabled="disabled" @click="save()">暂存草稿</button>
    <button type="button" :disabled="disabled" @click="save(Boolean(actionLabel))">{{ actionLabel || '保存本次账单草稿' }}</button>
  </section>
</template>
