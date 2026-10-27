<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiRequestError, apiGet, apiPost } from '../../services/apiClient';

const props = defineProps<{ targetType: string; targetCode: string }>();
const emit = defineEmits<{ changed: [] }>();

interface LaborFact { sourceRef: string; processName: string; quantity: number | null; measuredWeight: number | null; weightOnly: boolean }
interface SettlementContext {
  eligible: boolean; feeTarget: boolean; deliveryState: string; deliveryRevision: string;
  targetType: string; targetCode: string; customerName: string; blocks: string[];
  labor: LaborFact[];
  current: { code: string; status: string; rowVersion: number; customerNet: number; employeeNet: number | null } | null;
}
interface EmployeeDraft { sourceRef: string; processName: string; mode: 'PIECE' | 'GRAM' | 'FIXED' | 'WAIVED'; quantity: string; price: string; account: string; remark: string }

const context = ref<SettlementContext | null>(null);
const error = ref('');
const message = ref('');
const busy = ref(false);
const customerAmount = ref('');
const employees = ref<EmployeeDraft[]>([]);

const blocked = computed(() => !context.value?.eligible || context.value?.feeTarget === false);
const confirmed = computed(() => context.value?.current?.status === 'CONFIRMED');

watch(() => [props.targetType, props.targetCode], load, { immediate: true });

async function load() {
  error.value = '';
  if (!props.targetCode.trim()) { context.value = null; return; }
  try {
    context.value = await apiGet<SettlementContext>('/api/finance/order-settlements/context?targetType=' + encodeURIComponent(props.targetType) + '&targetCode=' + encodeURIComponent(props.targetCode.trim()));
    employees.value = (context.value.labor ?? []).map(fact => ({
      sourceRef: fact.sourceRef, processName: fact.processName, mode: fact.weightOnly ? 'GRAM' : 'FIXED',
      quantity: fact.measuredWeight != null ? String(fact.measuredWeight) : fact.quantity != null ? String(fact.quantity) : '',
      price: '', account: '', remark: fact.weightOnly ? '' : '',
    }));
  } catch (caught) {
    context.value = null;
    error.value = caught instanceof Error ? caught.message : '结单资格读取失败';
  }
}

function requestId() {
  const raw = crypto.randomUUID().replace(/-/g, '');
  return ('settle-' + raw).slice(0, 32);
}

function items(): Array<Record<string, unknown>> {
  const rows: Array<Record<string, unknown>> = [{
    lineNo: 1, kind: 'CUSTOMER', title: '客户加工费', sourceRef: '', chargeMode: 'FIXED', quantity: null, quantityUnit: '件',
    chargeUnit: '', unitPrice: null, grossAmount: customerAmount.value, deduction: 0, netAmount: null, payeeAccount: '', payeeName: '', remark: '', lossSourceCode: '',
  }];
  employees.value.forEach((row, index) => {
    rows.push({
      lineNo: index + 2, kind: 'EMPLOYEE', title: row.processName, sourceRef: row.sourceRef, chargeMode: row.mode,
      quantity: row.mode === 'FIXED' || row.mode === 'WAIVED' ? null : row.quantity,
      quantityUnit: row.mode === 'GRAM' ? '克' : '件', chargeUnit: row.mode, unitPrice: row.mode === 'FIXED' || row.mode === 'WAIVED' ? null : row.price,
      grossAmount: row.mode === 'FIXED' ? row.price : null, deduction: 0, netAmount: null, payeeAccount: row.account, payeeName: '',
      remark: row.remark, lossSourceCode: '',
    });
  });
  return rows;
}

async function submit(path: string) {
  if (!context.value || busy.value || blocked.value) return;
  busy.value = true; error.value = ''; message.value = '';
  try {
    const saved = await apiPost<Record<string, unknown>, { code: string; status: string }>('/api/finance/order-settlements/' + path, {
      requestId: requestId(), targetType: context.value.targetType, targetCode: context.value.targetCode,
      expectedVersion: context.value.current?.status === 'DRAFT' ? context.value.current.rowVersion : 0,
      deliveryRevision: context.value.deliveryRevision, customerWaived: false, waiveReason: '', noInternalLabor: employees.value.length === 0, noLaborReason: employees.value.length === 0 ? '无内部计件' : '',
      items: employees.value.length === 0 ? items().slice(0, 1) : items(),
    });
    message.value = (path === 'confirmations' ? '结单已确认 ' : path === 'adjustments' ? '结单已调整 ' : '草稿已保存 ') + saved.code;
    emit('changed');
    await load();
  } catch (caught) {
    error.value = caught instanceof ApiRequestError ? caught.message : caught instanceof Error ? caught.message : '结单提交失败';
  } finally { busy.value = false; }
}
</script>

<template>
  <section class="settlement-panel" aria-label="交货后结单">
    <h3>交货后结单</h3>
    <p v-if="!targetCode">请先选择母单或子单。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <template v-if="context">
      <p>{{ context.customerName }} · {{ context.targetCode }} · {{ context.deliveryState }}</p>
      <p v-if="!context.feeTarget">拆单后请按子单分别结单，母单只汇总。</p>
      <ul v-if="context.blocks.length"><li v-for="block in context.blocks" :key="block">{{ block }}</li></ul>
      <p v-if="blocked">交齐前不能填写客户加工费或员工工费。</p>
      <p v-else-if="confirmed">本单已确认。客户净额 {{ context.current?.customerNet }}<template v-if="context.current?.employeeNet != null">，员工净额 {{ context.current?.employeeNet }}</template>。调整只追加差额。</p>
      <template v-if="!blocked">
        <label>客户加工费<input v-model="customerAmount" aria-label="客户加工费" inputmode="decimal" /></label>
        <div v-for="row in employees" :key="row.sourceRef" class="settlement-labor">
          <p>{{ row.processName }} · {{ row.sourceRef }}</p>
          <label>计酬账号<input v-model="row.account" aria-label="计酬账号" /></label>
          <label>方式
            <select v-model="row.mode" aria-label="计酬方式">
              <option value="FIXED">固定金额</option>
              <option value="PIECE">按件</option>
              <option value="GRAM">按克</option>
              <option value="WAIVED">免计</option>
            </select>
          </label>
          <label v-if="row.mode === 'PIECE' || row.mode === 'GRAM'">数量<input v-model="row.quantity" aria-label="计酬数量" inputmode="decimal" /></label>
          <label v-if="row.mode !== 'WAIVED'">{{ row.mode === 'FIXED' ? '金额' : '单价' }}<input v-model="row.price" aria-label="计酬金额或单价" inputmode="decimal" /></label>
          <label v-if="row.mode === 'GRAM' || row.mode === 'WAIVED'">依据<input v-model="row.remark" aria-label="计酬依据" /></label>
        </div>
        <button v-if="!confirmed" class="secondary-button" type="button" :disabled="busy" @click="submit('drafts')">保存草稿</button>
        <button v-if="!confirmed" class="primary-button" type="button" :disabled="busy" @click="submit('confirmations')">确认结单</button>
        <button v-else class="primary-button" type="button" :disabled="busy" @click="submit('adjustments')">调整差额</button>
      </template>
    </template>
  </section>
</template>

<style scoped>
.settlement-panel { display: grid; gap: 10px; }
.settlement-labor { display: grid; gap: 8px; padding: 8px 0; border-top: 1px solid var(--line); }
</style>
