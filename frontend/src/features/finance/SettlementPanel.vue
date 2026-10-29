<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiRequestError, apiGet, apiPost } from '../../services/apiClient';

const props = defineProps<{ targetType: string; targetCode: string }>();
const emit = defineEmits<{ changed: []; locked: [value: boolean] }>();

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
type Attempt = { path: string; body: Record<string, unknown> };
const pending = ref<Attempt | null>(null);
const locked = computed(() => busy.value || Boolean(pending.value));
watch(locked, value => emit('locked', value), { immediate: true });
let readSequence = 0;
const customerAmount = ref('');
const employees = ref<EmployeeDraft[]>([]);

const blocked = computed(() => !context.value?.eligible || context.value?.feeTarget === false);
const confirmed = computed(() => context.value?.current?.status === 'CONFIRMED');

watch(() => [props.targetType, props.targetCode], load, { immediate: true });

async function load() {
  if (pending.value) return;
  const sequence = ++readSequence;
  const targetType = props.targetType; const targetCode = props.targetCode.trim();
  error.value = '';
  if (!props.targetCode.trim()) { context.value = null; return; }
  try {
    const loaded = await apiGet<SettlementContext>('/api/finance/order-settlements/context?targetType=' + encodeURIComponent(targetType) + '&targetCode=' + encodeURIComponent(targetCode));
    if (sequence !== readSequence || props.targetType !== targetType || props.targetCode.trim() !== targetCode) return;
    context.value = loaded;
    employees.value = (context.value.labor ?? []).map(fact => ({
      sourceRef: fact.sourceRef, processName: fact.processName, mode: fact.weightOnly ? 'GRAM' : 'FIXED',
      quantity: fact.measuredWeight != null ? String(fact.measuredWeight) : fact.quantity != null ? String(fact.quantity) : '',
      price: '', account: '', remark: fact.weightOnly ? '' : '',
    }));
  } catch (caught) {
    if (sequence !== readSequence) return;
    context.value = null;
    error.value = caught instanceof Error ? caught.message : '结单资格读取失败';
  }
}

function requestId() {
  const raw = (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`).replace(/-/g, '');
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

async function submit(path: string, retry = false) {
  if (busy.value || (!retry && (pending.value || !context.value || blocked.value))) return;
  const current = context.value;
  const attempt = retry ? pending.value : current ? { path, body: {
    requestId: requestId(), targetType: current.targetType, targetCode: current.targetCode,
    expectedVersion: current.current?.status === 'DRAFT' ? current.current.rowVersion : 0,
    deliveryRevision: current.deliveryRevision, customerWaived: false, waiveReason: '',
    noInternalLabor: employees.value.length === 0, noLaborReason: employees.value.length === 0 ? '无内部计件' : '',
    items: employees.value.length === 0 ? items().slice(0, 1) : items(),
  } } : null;
  if (!attempt) return;
  pending.value = attempt;
  busy.value = true; error.value = ''; message.value = '';
  try {
    const saved = await apiPost<Record<string, unknown>, { code: string; status: string }>('/api/finance/order-settlements/' + attempt.path, attempt.body);
    pending.value = null;
    message.value = (attempt.path === 'confirmations' ? '结单已确认 ' : attempt.path === 'adjustments' ? '结单已调整 ' : '草稿已保存 ') + saved.code;
    emit('changed');
    await load();
  } catch (caught) {
    if (caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408) pending.value = null;
    error.value = caught instanceof Error ? caught.message : '结单提交失败';
  } finally { busy.value = false; }
}
</script>

<template>
  <section class="settlement-panel" aria-label="交货后结单">
    <h3>交货后结单</h3>
    <p v-if="!targetCode">请先选择母单或子单。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="pending" role="status">结单结果待核对，原金额和请求已保留。
      <button class="secondary-button" type="button" :disabled="busy" @click="submit(pending.path, true)">原结单请求重试</button>
    </p>
    <template v-if="context">
      <p>{{ context.customerName }} · {{ context.targetCode }} · {{ context.deliveryState }}</p>
      <p v-if="!context.feeTarget">拆单后请按子单分别结单，母单只汇总。</p>
      <ul v-if="context.blocks.length"><li v-for="block in context.blocks" :key="block">{{ block }}</li></ul>
      <p v-if="blocked">交齐前不能填写客户加工费或员工工费。</p>
      <p v-else-if="confirmed">本单已确认。客户净额 {{ context.current?.customerNet }}<template v-if="context.current?.employeeNet != null">，员工净额 {{ context.current?.employeeNet }}</template>。调整只追加差额。</p>
      <fieldset v-if="!blocked" :disabled="locked">
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
      </fieldset>
    </template>
  </section>
</template>

<style scoped>
.settlement-panel { display: grid; gap: 10px; }
.settlement-labor { display: grid; gap: 8px; padding: 8px 0; border-top: 1px solid var(--line); }
</style>
