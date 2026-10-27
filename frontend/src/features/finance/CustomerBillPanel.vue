<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { ApiRequestError } from '../../services/apiClient';
import { fetchStatement, recordReceipt, type CustomerReceipt, type CustomerStatement } from '../../services/financeApi';
import { formatAmount } from '../../utils/formatAmount';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';
import { validBillContext, type BillContext, type BillMode } from './billContext';

const props = withDefaults(defineProps<{ context: BillContext; initialMode?: BillMode; entryRevision?: number }>(), { initialMode: 'statement', entryRevision: 0 });
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canRead = computed(() => !session.value?.effectivePermissions || session.value.effectivePermissions.includes('FINANCE_CUSTOMER_READ'));
const canReceive = computed(() => !session.value?.effectivePermissions || session.value.effectivePermissions.includes('FINANCE_RECEIPT'));
const mode = ref<BillMode>(props.initialMode);
const statement = ref<CustomerStatement | null>(null);
const reading = ref(false);
const readError = ref('');
const error = ref('');
const message = ref('');
const receiptDate = ref(new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }));
const receiptAmount = ref('');
const allocationRows = ref<Array<{ code: string; amount: string }>>([{ code: props.context.receivableCode ?? '', amount: '' }]);
const receipt = ref<CustomerReceipt | null>(null);
type ReceiptBody = Parameters<typeof recordReceipt>[0];
const pending = ref<ReceiptBody | null>(null);
const submitting = ref(false);
const attempt = ref<RequestAttempt | null>(null);
const locked = computed(() => submitting.value || Boolean(pending.value));
const receipts = ref<Array<{ body: ReceiptBody; receipt: CustomerReceipt }>>([]);
let disposed = false;
let generation = 0;
onBeforeUnmount(() => { disposed = true; ++generation; });
watch([() => props.entryRevision, () => props.context.receivableCode, () => props.initialMode], () => {
  mode.value = props.initialMode;
  if (locked.value || receipt.value) return;
  if (props.context.receivableCode) addAllocation(props.context.receivableCode);
});
function addAllocation(code: string) {
  if (locked.value || receipt.value || !canReceive.value || allocationRows.value.some(row => row.code === code)) return;
  const empty = allocationRows.value.find(row => !row.code.trim() && !row.amount.trim());
  if (empty) empty.code = code; else allocationRows.value.push({ code, amount: '' });
}
watch(canRead, allowed => { if (!allowed) { ++generation; statement.value = null; reading.value = false; } });
async function loadStatement() {
  if (!canRead.value || !validBillContext(props.context)) return;
  const token = ++generation; const context = { ...props.context };
  reading.value = true; readError.value = '';
  try {
    const loaded = await fetchStatement(String(context.customerId), context.yearMonth);
    if (disposed || token !== generation) return;
    if (!loaded || loaded.customerId !== context.customerId || loaded.lines.some(line => line.customerId !== context.customerId)) throw new Error('账单响应与本客户不一致');
    statement.value = loaded;
  } catch (caught) {
    if (!disposed && token === generation) readError.value = (receipt.value ? '收款已登记，账单刷新失败：' : '账单读取失败：') + (caught instanceof Error ? caught.message : '读取失败') + (receipt.value ? '。请只重试查询，不要再次登记。' : '');
  } finally { if (!disposed && token === generation) reading.value = false; }
}
onMounted(loadStatement);
function amount(value: string, label: string) {
  if (!/^\d+(\.\d+)?$/.test(value.trim()) || !Number.isFinite(Number(value)) || Number(value) <= 0) throw new Error(label + '必须为正数');
  return Number(value);
}
function command(): ReceiptBody {
  if (!validBillContext(props.context)) throw new Error('请核对稳定客户编号和账单月份');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(receiptDate.value)) throw new Error('请填写收款日期');
  const allocations = allocationRows.value.filter(row => row.code.trim() || row.amount.trim()).map(row => {
    const code = row.code.trim(); if (!code) throw new Error('请填写分摊对应的应收行');
    return { receivableCode: code, amount: amount(row.amount, '分摊金额') };
  });
  if (new Set(allocations.map(row => row.receivableCode)).size !== allocations.length) throw new Error('同一应收行请合并填写一次');
  const body = { customerId: props.context.customerId, customerName: statement.value?.customerName || '客户' + props.context.customerId, receivedOn: receiptDate.value,
    amount: amount(receiptAmount.value, '收款金额'), allocations };
  attempt.value = nextRequestAttempt(attempt.value, JSON.stringify(body), 'receipt');
  return { ...body, idempotencyKey: attempt.value.requestId };
}
async function saveReceipt(retry = false) {
  if (!canReceive.value || submitting.value || receipt.value || (pending.value && !retry) || (retry && !pending.value)) return;
  error.value = '';
  let body = retry ? pending.value : null;
  try { if (!body) body = command(); } catch (caught) { error.value = caught instanceof Error ? caught.message : '请核对收款内容'; return; }
  if (!body) return;
  const original = body;
  ++generation; reading.value = false; submitting.value = true;
  try {
    const saved = await recordReceipt(original);
    if (disposed) return;
    if (!saved?.code) throw new Error('收款回执不完整，请保留原请求核对');
    pending.value = null; receipt.value = saved; receipts.value.push({ body: original, receipt: saved });
    message.value = '收款已登记，单号 ' + saved.code;
  } catch (caught) {
    if (disposed) return;
    const known = caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408;
    pending.value = known && !retry ? null : original;
    error.value = caught instanceof Error ? caught.message : '收款登记失败';
  } finally { submitting.value = false; }
  if (!disposed && receipt.value) await loadStatement();
}
function nextReceipt() {
  if (locked.value || !canReceive.value) return;
  receipt.value = null; receiptAmount.value = ''; allocationRows.value = [{ code: '', amount: '' }]; attempt.value = null;
  error.value = ''; message.value = '已开启另一笔收款草稿，请重新核对实际收款及分摊。';
}
</script>

<template>
  <section class="customer-bill-panel page-stack" aria-label="客户账单与收款办理">
    <h3>客户 {{ context.customerId }} · {{ statement?.customerName || '按原客户编号核对' }} · {{ context.yearMonth || '全部月份' }}</h3>
    <p>收款、账单分摊和预收沿用原规则，账单总额不会自动填为收款命令。</p>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p><p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p>
    <p v-if="readError && canRead" class="operation-feedback warning" role="alert">{{ readError }}</p>
    <nav class="bill-actions" aria-label="账单办理方式"><button v-if="canRead" class="secondary-button" type="button" :aria-pressed="mode === 'statement'" @click="mode = 'statement'">账单核对</button><button v-if="canReceive" class="secondary-button" type="button" :aria-pressed="mode === 'receipt'" @click="mode = 'receipt'">登记收款</button><button v-if="canRead" class="secondary-button" type="button" :disabled="reading" @click="loadStatement">{{ reading ? '查询中…' : '重新查询账单' }}</button></nav>
    <section v-if="canRead" v-show="mode === 'statement'" aria-label="本客户账单">
      <p v-if="!statement">尚未取得本客户账单。</p>
      <template v-else><dl class="bill-totals"><dt>应收</dt><dd>{{ formatAmount(statement.receivableAmount) }}</dd><dt>已收</dt><dd>{{ formatAmount(statement.receivedAmount) }}</dd><dt>未收</dt><dd>{{ formatAmount(statement.outstandingAmount) }}</dd><dt>预收</dt><dd>{{ formatAmount(statement.prepaymentAmount) }}</dd></dl><p v-if="!statement.lines.length">暂无账单行</p>
        <div class="bill-table"><table><thead><tr><th>原应收行</th><th>单据／工序</th><th>数量与原单位</th><th>金额</th><th v-if="canReceive">办理</th></tr></thead><tbody><tr v-for="line in statement.lines" :key="line.code"><td>{{ line.code }}</td><td>{{ line.workOrderCode || line.motherOrderCode }} · {{ line.processName }}</td><td>{{ line.quantity }} {{ line.unit }}</td><td>{{ formatAmount(line.amount) }}</td><td v-if="canReceive"><button class="secondary-button" type="button" :disabled="locked || Boolean(receipt)" :aria-label="'分摊到' + line.code" @click="addAllocation(line.code); mode = 'receipt'">选入本次分摊</button></td></tr></tbody></table></div>
      </template>
    </section>
    <section v-if="canReceive" v-show="mode === 'receipt'" aria-label="原客户收款草稿">
      <p v-if="pending" class="operation-feedback warning">原收款结果待核对：客户 {{ pending.customerId }} · {{ pending.idempotencyKey }}。原金额及分摊已保留。<button class="secondary-button" type="button" :disabled="submitting" @click="saveReceipt(true)">原收款请求重试</button></p>
      <fieldset :disabled="locked || Boolean(receipt)"><legend>本次实际收款</legend><label>收款日期<input v-model="receiptDate" type="date" aria-label="收款日期" /></label><label>收款金额<input v-model="receiptAmount" inputmode="decimal" aria-label="收款金额" /></label><p>可全部预收，也可将本次收款的一部分分摊到原应收行；不限制为本月账单未收总额。</p>
        <div v-for="(row, index) in allocationRows" :key="index" class="allocation-row"><label>原应收行<input v-model="row.code" :aria-label="index === 0 ? '核销账单号' : '第' + (index + 1) + '个核销账单号'" placeholder="留空可全部预收" /></label><label>分摊金额<input v-model="row.amount" inputmode="decimal" :aria-label="index === 0 ? '核销金额' : '第' + (index + 1) + '个核销金额'" /></label><button v-if="allocationRows.length > 1" class="secondary-button" type="button" @click="allocationRows.splice(index, 1)">移除本行</button></div>
        <button class="secondary-button" type="button" @click="allocationRows.push({ code: '', amount: '' })">增加分摊行</button><button class="primary-button" type="button" @click="saveReceipt()">确认登记收款</button>
      </fieldset>
      <p v-if="receipt" role="status">收款单号 {{ receipt.code }}，成功回执已保留。</p><button v-if="receipt" class="secondary-button" type="button" :disabled="locked" @click="nextReceipt">另开一笔收款</button>
    </section>
    <details v-if="receipts.length && canReceive"><summary>本次收款回执</summary><article v-for="item in receipts" :key="item.receipt.code"><strong>{{ item.receipt.code }}</strong><p>原客户 {{ item.body.customerId }} · 收款 {{ formatAmount(item.receipt.amount) }} · {{ item.receipt.receivedOn }}</p><p v-for="line in item.receipt.allocations" :key="line.receivableCode">{{ line.receivableCode }} · {{ formatAmount(line.amount) }}</p></article></details>
  </section>
</template>

<style scoped>
.customer-bill-panel { min-width: 0; overflow-wrap: anywhere; }
.bill-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.bill-totals { display: grid; grid-template-columns: repeat(4, auto minmax(0, 1fr)); gap: 8px; }
.bill-totals dd { margin: 0; }
fieldset { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 220px)); justify-content: start; gap: 12px; min-width: 0; border: 1px solid var(--line); border-radius: 10px; padding: 12px; }
fieldset > :is(legend, p) { grid-column: 1 / -1; }
fieldset > button { width: auto; justify-self: start; }
label { display: grid; gap: 4px; min-width: 0; }
input { width: 100%; min-width: 0; box-sizing: border-box; min-height: 40px; }
.allocation-row { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) auto; gap: 8px; }
.bill-table { max-width: 100%; overflow-x: auto; } table { width: 100%; border-collapse: collapse; } th, td { text-align: left; padding: 8px; border-bottom: 1px solid var(--line); }
@media (max-width: 900px) { fieldset { grid-template-columns: repeat(2, minmax(0, 1fr)); } .bill-totals { grid-template-columns: auto minmax(0, 1fr); } .allocation-row { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); } .allocation-row > button { grid-column: 1 / -1; } input, button { min-height: 44px; } input { font-size: 16px; } }
</style>
