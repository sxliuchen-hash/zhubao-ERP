<script setup lang="ts">
import { ref, watch } from 'vue';

/** 整单员工核算的一份额草稿。空工价保持空白，不补 0。 */
export interface EmployeeShareDraft {
  payeeAccount: string;
  processName: string;
  quantity: string;
  unitPrice: string;
  proxy: boolean;
}

export interface EmployeeSettlementSaved {
  targetCode: string;
  version: number;
  shares: EmployeeShareDraft[];
}

const props = defineProps<{
  targetCode: string;
  employeeQuantity: string;
  outsourcingQuantity: string;
  lossQuantity: string | null;
  saved?: EmployeeSettlementSaved | null;
}>();

const emit = defineEmits<{
  save: [payload: { targetCode: string; version: number; shares: EmployeeShareDraft[] }];
}>();

const version = ref(1);
const shares = ref<EmployeeShareDraft[]>([blankShare()]);

watch(() => props.targetCode, () => {
  version.value = 1;
  shares.value = [blankShare()];
}, { immediate: true });

watch(() => props.saved, (saved) => {
  if (!saved || saved.targetCode !== props.targetCode) return;
  version.value = saved.version;
  shares.value = saved.shares.map(share => ({ ...share }));
}, { immediate: true });

function blankShare(): EmployeeShareDraft {
  return { payeeAccount: '', processName: '', quantity: '', unitPrice: '', proxy: false };
}

function save() {
  emit('save', { targetCode: props.targetCode, version: version.value, shares: shares.value.map(share => ({ ...share })) });
}
</script>

<template>
  <section aria-label="整单员工核算">
    <h3>整单员工核算</h3>
    <p>{{ targetCode }} · 员工实作 {{ employeeQuantity }} · 外协 {{ outsourcingQuantity }} 不计入 · 版本 {{ version }}</p>
    <p>{{ lossQuantity == null ? '损耗尚未核定，不扣工资' : `损耗 ${lossQuantity} 不自动扣工资` }}</p>
    <p v-for="(share, index) in shares" :key="index">
      <label>计酬账号<input v-model="share.payeeAccount" :aria-label="`第${index + 1}个计酬账号`" /></label>
      <label>工序<input v-model="share.processName" :aria-label="`第${index + 1}个工序`" /></label>
      <label>数量<input v-model="share.quantity" :aria-label="`第${index + 1}个实作数量`" /></label>
      <label>工价<input v-model="share.unitPrice" :aria-label="`第${index + 1}个工价`" /></label>
    </p>
    <button type="button" @click="save">保存员工核算草稿</button>
  </section>
</template>
