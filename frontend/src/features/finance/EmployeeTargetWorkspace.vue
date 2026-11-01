<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import EmployeeSettlementPane, { type EmployeeSettlementSaved } from './EmployeeSettlementPane.vue';
import { fetchEmployeeTarget, settleEmployeeTarget, type EmployeeContext } from '../../services/deliveryFinanceApi';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
const props = defineProps<{ targetCode: string }>(); const target = computed(() => props.targetCode);
const message = ref(''); const draftJson = ref(''); const context = ref<EmployeeContext | null>(null); const completed = ref(false);
const command = useBusinessCommand('employee-target', target, { draftJson }, message); const { pending, hasUnconfirmed } = command;
const saved = computed<EmployeeSettlementSaved | null>(() => draftJson.value ? JSON.parse(draftJson.value) as EmployeeSettlementSaved : null);
let sequence = 0;
watch(target, async code => {
  const read = ++sequence; context.value = null; completed.value = false;
  try {
    const view = await fetchEmployeeTarget(code); if (read !== sequence) return; context.value = view;
    if (!draftJson.value) draftJson.value = JSON.stringify({ targetCode: code, version: 1, shares: view.closure.work.map(work => ({ payeeAccount: work.payeeAccount, processName: work.stepId, quantity: String(work.quantity), unitPrice: '', proxy: false })) });
  } catch (cause) { if (read === sequence) message.value = cause instanceof Error ? cause.message : '实作依据读取失败'; }
}, { immediate: true });
async function submit(payload: EmployeeSettlementSaved) {
  draftJson.value = JSON.stringify(payload);
  if (payload.shares.some(share => !share.unitPrice.trim())) { message.value = '请填写每一工序的工价，空白不能按0入账'; return; }
  const result = await command.submit('settle', { shares: payload.shares }, (code, body) => settleEmployeeTarget(code, body));
  if (result) { completed.value = true; message.value = `整单员工工费已核算，共${result.length}条`; }
}
</script>
<template>
  <section aria-label="员工真实工序核算">
    <p v-if="message" role="status">{{ message }}</p>
    <template v-if="context">
      <p v-if="context.closure.closure !== 'CLOSED_NORMAL'">本单尚未具备普通计酬条件；返工、全损须先核定计酬规则。</p>
      <p v-if="!context.closure.workEvidenceComplete">实作人员或照片依据未齐，请核对原报工。</p>
      <p v-for="work in context.closure.work" :key="`${work.payeeAccount}:${work.stepId}`">{{ work.payeeAccount }} · {{ work.processName }} · {{ work.quantity }}{{ context.closure.quantityUnit }}</p>
      <p v-if="context.payroll.length">该目标已有工资记录，不能重复核算。</p>
      <EmployeeSettlementPane :target-code="targetCode" :employee-quantity="context.closure.employeeQuantity == null ? '待核' : String(context.closure.employeeQuantity)" :outsourcing-quantity="String(context.closure.outsourcingQuantity)" :loss-quantity="context.closure.lossQuantity == null ? null : String(context.closure.lossQuantity)" :saved="saved" :disabled="pending || completed || (context.payroll.length > 0 && !hasUnconfirmed) || context.closure.closure !== 'CLOSED_NORMAL' || !context.closure.workEvidenceComplete" action-label="确认整单工费并入账" @save="draftJson = JSON.stringify($event); message = '草稿已在页面会话中保留，尚未入账'" @confirm="submit" />
      <button v-if="hasUnconfirmed" type="button" @click="command.restorePending">恢复上次填写后重试</button>
    </template>
  </section>
</template>
