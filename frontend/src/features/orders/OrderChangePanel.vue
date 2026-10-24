<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { ApiRequestError, apiGet, apiPost } from '../../services/apiClient';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';

const props = defineProps<{ target: { orderCode: string; motherOrderCode: string } }>();
const emit = defineEmits<{ changed: [] }>();
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canFinance = computed(() => session.value?.effectivePermissions
  ? session.value.effectivePermissions.includes('FINANCE_BILLING_POST')
  : ['ADMIN', 'GENERAL_MANAGER', 'FINANCE_STATISTICIAN'].includes(session.value?.role ?? ''));
interface OrderChangeRow {
  id: string; motherOrderCode: string; workOrderCode: string; status: string; versionNo: number; expectedVersion: number;
  oldRequirement: string; newRequirement: string; oldDueDate: string; newDueDate: string; oldRoute: string; newRoute: string;
  reworkProcess: string; amountUnchanged: boolean; customerUnitPrice: number | null; workerFee: number | null;
  customerDelta: number | null; workerDelta: number | null; financeNote: string; receivableCode: string; processName: string; workerName: string;
}
type Kind = 'draft' | 'submit' | 'confirm' | 'reject' | 'apply';
interface Command { kind: Kind; path: string; body: Record<string, unknown>; id: string; before: string; success: string; idempotent: boolean }
const message = ref('');
const error = ref('');
const readError = ref('');
const timelineError = ref('');
const changes = ref<OrderChangeRow[]>([]);
const selectedChange = ref<OrderChangeRow | null>(null);
const evidenceTargets = ref<OrderChangeRow[]>([]);
const changeRequirement = ref('');
const changeDue = ref('');
const changeRoute = ref('');
const changeRework = ref('');
const composeOpen = ref(false);
const draftBase = ref<number | null>(null);
const draftSaved = ref<OrderChangeRow | null>(null);
const changeAttempt = ref<RequestAttempt | null>(null);
const applyAttempt = ref<RequestAttempt | null>(null);
const pending = ref<Command | null>(null);
const submitting = ref(false);
const reading = ref(false);
const checking = ref(false);
const locked = computed(() => submitting.value || Boolean(pending.value));
const receipts = ref<Array<{ id: string; text: string }>>([]);
const financeNote = ref('');
const customerPrice = ref('');
const workerFee = ref('');
const customerDelta = ref('');
const workerDelta = ref('');
const receivableCode = ref('');
const processName = ref('');
const workerName = ref('');
const timeline = ref<string[]>([]);
const confirmedRows = new Map<string, OrderChangeRow>();
const financeDrafts = new Map<string, string[]>();
const financeFields = [financeNote, customerPrice, workerFee, customerDelta, workerDelta, receivableCode, processName, workerName];
let disposed = false;
let readGeneration = 0;
let timelineGeneration = 0;
onBeforeUnmount(() => { disposed = true; ++readGeneration; ++timelineGeneration; });
function workCode() { return props.target.orderCode === props.target.motherOrderCode ? '' : props.target.orderCode; }
function validRow(row: OrderChangeRow | null | undefined, id?: string) {
  return Boolean(row?.id && row.motherOrderCode === props.target.motherOrderCode && (!id || row.id === id));
}
function statusLabel(status: string) { return ({ DRAFT: '草稿', PENDING_FINANCE: '待财务确认', FINANCE_CONFIRMED: '财务已确认', REJECTED: '已拒绝', APPLIED: '已生效' } as Record<string, string>)[status] || status; }
function rememberEvidence(row: OrderChangeRow) { if (!evidenceTargets.value.some(item => item.id === row.id)) evidenceTargets.value.push(row); }
function assignSelection(row: OrderChangeRow) {
  if (selectedChange.value?.id !== row.id) {
    if (selectedChange.value) financeDrafts.set(selectedChange.value.id, financeFields.map(field => field.value));
    const saved = financeDrafts.get(row.id);
    financeFields.forEach((field, index) => { field.value = saved?.[index] ?? ''; });
  }
  selectedChange.value = row; rememberEvidence(row);
}
function preserveReceipt(row: OrderChangeRow) {
  const receipt = confirmedRows.get(row.id);
  const rank: Record<string, number> = { DRAFT: 0, PENDING_FINANCE: 1, FINANCE_CONFIRMED: 2, REJECTED: 3, APPLIED: 3 };
  return receipt && (rank[receipt.status] ?? -1) > (rank[row.status] ?? -1) ? receipt : row;
}
function select(row: OrderChangeRow) {
  if (locked.value || !validRow(row)) return;
  composeOpen.value = false;
  assignSelection(row);
}
function latestBase(rows: OrderChangeRow[]) {
  const target = workCode() || props.target.motherOrderCode;
  return rows.filter(row => row.status === 'APPLIED' && (row.motherOrderCode === target || row.workOrderCode === target))
    .reduce((max, row) => Math.max(max, row.versionNo), 0);
}
async function loadChanges() {
  if (submitting.value) return;
  const token = ++readGeneration;
  reading.value = true; readError.value = '';
  try {
    const loaded = await apiGet<OrderChangeRow[]>('/api/business/order-changes?motherOrderCode=' + encodeURIComponent(props.target.motherOrderCode));
    if (disposed || token !== readGeneration) return;
    if (!Array.isArray(loaded) || loaded.some(row => !validRow(row))) throw new Error('改单列表与当前母单不一致');
    changes.value = loaded.map(preserveReceipt);
    for (const receipt of confirmedRows.values()) if (!changes.value.some(row => row.id === receipt.id)) changes.value.push(receipt);
    if (draftBase.value === null && !pending.value) draftBase.value = latestBase(changes.value);
    if (selectedChange.value && !pending.value) selectedChange.value = changes.value.find(row => row.id === selectedChange.value?.id) ?? selectedChange.value;
  } catch (caught) { if (!disposed && token === readGeneration) readError.value = caught instanceof Error ? caught.message : '改单列表读取失败'; }
  finally { if (!disposed && token === readGeneration) reading.value = false; }
}
onMounted(loadChanges);
function knownRejection(caught: unknown) { return caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408; }
async function execute(command: Command, retry = false) {
  if (submitting.value || (!retry && pending.value)) return;
  if (['confirm', 'reject'].includes(command.kind) && !canFinance.value) return;
  ++readGeneration; reading.value = false; submitting.value = true; error.value = '';
  try {
    const saved = await apiPost<Record<string, unknown>, OrderChangeRow>(command.path, command.body);
    if (disposed) return;
    if (!validRow(saved, command.id || undefined) || (command.kind === 'draft' && saved.workOrderCode !== workCode())) throw new Error('改单回执与原目标不一致，请保留原请求核对');
    pending.value = null; assignSelection(saved);
    confirmedRows.set(saved.id, saved);
    if (command.kind === 'draft') draftSaved.value = saved;
    message.value = command.success + ' ' + saved.id + ' · ' + statusLabel(saved.status);
    receipts.value.push({ id: saved.id, text: message.value });
    emit('changed');
  } catch (caught) {
    if (disposed) return;
    // 已有未知结果时，后续冲突或拒绝也不能改号绕过原请求。
    if (!knownRejection(caught) || retry) pending.value = command;
    error.value = caught instanceof Error ? caught.message : '改单操作失败';
  } finally { submitting.value = false; }
  if (!disposed && !pending.value) await loadChanges();
}
function draftBody() {
  return { motherOrderCode: props.target.motherOrderCode, workOrderCode: workCode(), newRequirement: changeRequirement.value.trim(),
    newDueDate: changeDue.value.trim(), newRoute: changeRoute.value.trim(), reworkProcess: changeRework.value.trim(), expectedVersion: draftBase.value };
}
async function draftChange() {
  if (locked.value || reading.value || draftSaved.value || draftBase.value === null) return;
  const body = draftBody();
  changeAttempt.value = nextRequestAttempt(changeAttempt.value, JSON.stringify(body), 'change');
  await execute({ kind: 'draft', id: '', before: '', path: '/api/business/order-changes', body: { ...body, requestId: changeAttempt.value.requestId }, success: '已保存改单', idempotent: true });
}
async function newDraft() {
  if (locked.value || reading.value) return;
  composeOpen.value = true;
  draftSaved.value = null; draftBase.value = null; changeAttempt.value = null;
  changeRequirement.value = ''; changeDue.value = ''; changeRoute.value = ''; changeRework.value = '';
  await loadChanges();
}
async function reloadBase() { if (!locked.value && !draftSaved.value && !reading.value) { draftBase.value = null; changeAttempt.value = null; await loadChanges(); } }
async function stage(kind: 'submit' | 'confirm' | 'reject', body: Record<string, unknown> = {}) {
  const current = selectedChange.value;
  if (!current || locked.value || (kind === 'submit' ? current.status !== 'DRAFT' : current.status !== 'PENDING_FINANCE')) return;
  if (kind !== 'submit' && !canFinance.value) return;
  const suffix = { submit: 'finance-submissions', confirm: 'finance-confirmations', reject: 'finance-rejections' }[kind];
  const success = { submit: '已送财务确认', confirm: '财务确认完成', reject: '财务已拒绝' }[kind];
  await execute({ kind, id: current.id, before: current.status, path: '/api/business/order-changes/' + encodeURIComponent(current.id) + '/' + suffix, body, success, idempotent: false });
}
async function confirmUnchanged() { await stage('confirm', { amountUnchanged: true, note: financeNote.value.trim() || '金额不变' }); }
function decimal(value: string) { return /^-?\d+(\.\d+)?$/.test(value.trim()) && Number.isFinite(Number(value)); }
async function confirmDelta() {
  if (!canFinance.value || locked.value) return;
  const customer = customerDelta.value.trim() || '0'; const worker = workerDelta.value.trim() || '0';
  if (![customerPrice.value, workerFee.value, customer, worker].every(decimal)) { error.value = '请填写有效客户单价、工费和差额'; return; }
  if (Number(customer) < 0 && !receivableCode.value.trim()) { error.value = '减额必须指定原应收行'; return; }
  if ((Number(customer) > 0 || Number(worker) !== 0) && !processName.value.trim()) { error.value = '增额或工费差额必须指定工序'; return; }
  if (Number(worker) !== 0 && !workerName.value.trim()) { error.value = '工费差额必须指定工人'; return; }
  await stage('confirm', { amountUnchanged: false, customerUnitPrice: customerPrice.value.trim(), workerFee: workerFee.value.trim(),
    customerDelta: customer, workerDelta: worker, receivableCode: receivableCode.value.trim(), processName: processName.value.trim(),
    workerName: workerName.value.trim(), note: financeNote.value.trim() });
}
async function applyChange() {
  const current = selectedChange.value;
  if (!current || locked.value || current.status !== 'FINANCE_CONFIRMED') return;
  applyAttempt.value = nextRequestAttempt(applyAttempt.value, JSON.stringify([current.id, current.expectedVersion]), 'change-apply');
  await execute({ kind: 'apply', id: current.id, before: current.status, path: '/api/business/order-changes/' + encodeURIComponent(current.id) + '/applications',
    body: { requestId: applyAttempt.value.requestId, expectedVersion: current.expectedVersion }, success: '改单已生效', idempotent: true });
}
async function retryOriginal() { if (pending.value?.idempotent && !submitting.value) await execute(pending.value, true); }
async function checkUnknown() {
  const command = pending.value;
  if (!command || command.idempotent || submitting.value || checking.value) return;
  checking.value = true; readError.value = '';
  try {
    const row = await apiGet<OrderChangeRow>('/api/business/order-changes/' + encodeURIComponent(command.id));
    if (disposed || pending.value !== command) return;
    if (!validRow(row, command.id)) throw new Error('核对响应与原改单不一致');
    assignSelection(row);
    const progressed = command.kind === 'submit' ? ['PENDING_FINANCE', 'FINANCE_CONFIRMED', 'REJECTED', 'APPLIED'] : ['FINANCE_CONFIRMED', 'REJECTED', 'APPLIED'];
    if (progressed.includes(row.status)) {
      pending.value = null;
      confirmedRows.set(row.id, row);
      message.value = '只读核对：' + row.id + ' 当前为' + statusLabel(row.status) + '，未再次提交原操作。';
      receipts.value.push({ id: row.id, text: message.value }); emit('changed');
    } else readError.value = '原改单仍为' + statusLabel(row.status) + '，结果尚不能确认，请稍后再次只读核对';
  } catch (caught) { if (!disposed) readError.value = caught instanceof Error ? caught.message : '原改单核对失败'; }
  finally { checking.value = false; }
}
async function loadTimeline() {
  const token = ++timelineGeneration; timelineError.value = '';
  try {
    const loaded = await apiGet<string[]>('/api/business/order-changes/timeline?motherOrderCode=' + encodeURIComponent(props.target.motherOrderCode));
    if (!disposed && token === timelineGeneration) timeline.value = loaded;
  } catch (caught) { if (!disposed && token === timelineGeneration) timelineError.value = caught instanceof Error ? caught.message : '时间线读取失败'; }
}
</script>

<template>
  <section class="order-change-panel page-stack" aria-label="单据改单办理">
    <p>母单 {{ target.motherOrderCode }} · 当前目标 {{ target.orderCode }}</p>
    <p>申请、财务确认和生效分别办理；进度版本不作为改单基准版本。</p>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p>
    <p v-if="readError" class="operation-feedback warning" role="alert">只读刷新失败：{{ readError }}。已有回执保留。</p>
    <section v-if="pending" class="operation-feedback warning" aria-label="原改单请求待核对">
      <p>{{ pending.id || pending.body.workOrderCode || pending.body.motherOrderCode }} · 原操作结果待核对，原内容{{ pending.idempotent ? '和请求编号' : '' }}已保留。</p>
      <p v-if="pending.idempotent">原编号 {{ pending.body.requestId }} · 基准 {{ pending.body.expectedVersion }}</p>
      <button v-if="pending.idempotent" class="secondary-button" type="button" :disabled="submitting" @click="retryOriginal">原改单请求重试</button>
      <button v-else class="secondary-button" type="button" :disabled="checking" @click="checkUnknown">只读核对原改单</button>
    </section>
    <details class="change-compose info-disclosure" :open="composeOpen" @toggle="composeOpen = ($event.target as HTMLDetailsElement).open">
      <summary>新改单申请</summary>
      <p>基准业务版本 {{ draftBase === null ? '尚未读取' : draftBase }}。填写期间固定，刷新列表不会自动换版。</p>
      <fieldset :disabled="locked || reading || Boolean(draftSaved) || draftBase === null">
        <label>新要求<input v-model="changeRequirement" aria-label="改单新要求" /></label>
        <label>新期限<input v-model="changeDue" aria-label="改单新期限" placeholder="如 2026-10-30，留空沿用" /></label>
        <label>新路线<input v-model="changeRoute" aria-label="改单新路线" placeholder="留空沿用，如 雕刻、镶嵌" /></label>
        <label>重做工序<input v-model="changeRework" aria-label="改单重做工序" placeholder="已完成工序生成新轮次" /></label>
        <button class="secondary-button" type="button" @click="draftChange">保存改单申请</button>
      </fieldset>
      <button v-if="!draftSaved" class="secondary-button" type="button" :disabled="locked || reading" @click="reloadBase">重新读取申请基准</button>
      <button v-else class="secondary-button" type="button" :disabled="locked || reading" @click="newDraft">另开一份改单申请</button>
    </details>
    <div class="change-record-grid">
      <section class="change-history" aria-label="改单记录">
        <h3>改单记录</h3><button class="secondary-button" type="button" :disabled="submitting || reading" @click="loadChanges">{{ reading ? '读取中…' : '刷新改单' }}</button>
        <p v-if="!changes.length">暂无已读取的改单记录。</p>
        <button v-for="row in changes" :key="row.id" class="secondary-button" type="button" :disabled="locked" :aria-pressed="selectedChange?.id === row.id" @click="select(row)">{{ row.id }} · {{ statusLabel(row.status) }} · 版本 {{ row.versionNo }}</button>
      </section>
      <section v-if="selectedChange" class="change-detail" aria-label="改单详情">
        <h3>{{ selectedChange.id }} · {{ statusLabel(selectedChange.status) }}</h3>
        <p>目标 {{ selectedChange.workOrderCode || selectedChange.motherOrderCode }} · 基准版本 {{ selectedChange.expectedVersion }} · 申请版本 {{ selectedChange.versionNo }}</p>
        <dl class="change-diff"><dt>要求</dt><dd>{{ selectedChange.oldRequirement || '空' }} → {{ selectedChange.newRequirement || '空' }}</dd><dt>期限</dt><dd>{{ selectedChange.oldDueDate || '空' }} → {{ selectedChange.newDueDate || '空' }}</dd><dt>路线</dt><dd>{{ selectedChange.oldRoute || '空' }} → {{ selectedChange.newRoute || '空' }}</dd></dl>
        <p v-if="selectedChange.reworkProcess">重做工序：{{ selectedChange.reworkProcess }}；不覆盖原交付。</p>
        <button class="secondary-button" type="button" :disabled="locked || selectedChange.status !== 'DRAFT'" @click="stage('submit')">送财务确认</button>
        <template v-if="canFinance">
          <details v-if="['FINANCE_CONFIRMED', 'APPLIED'].includes(selectedChange.status)"><summary>财务确认内容</summary><p>{{ selectedChange.amountUnchanged ? '金额不变' : '差额确认' }} · 客户单价 {{ selectedChange.customerUnitPrice }} · 工费 {{ selectedChange.workerFee }}</p><p>客户差额 {{ selectedChange.customerDelta }} · 工费差额 {{ selectedChange.workerDelta }} · {{ selectedChange.financeNote }}</p></details>
          <fieldset v-if="selectedChange.status === 'PENDING_FINANCE'" :disabled="locked" aria-label="财务确认改单">
            <label>财务说明<input v-model="financeNote" aria-label="改单财务说明" /></label>
            <label>客户单价<input v-model="customerPrice" aria-label="改单客户单价" inputmode="decimal" /></label>
            <label>工人工费<input v-model="workerFee" aria-label="改单工人工费" inputmode="decimal" /></label>
            <label>客户差额<input v-model="customerDelta" aria-label="改单客户差额" inputmode="decimal" /></label>
            <label>工费差额<input v-model="workerDelta" aria-label="改单工费差额" inputmode="decimal" /></label>
            <label>原应收行（减额必填）<input v-model="receivableCode" aria-label="改单原应收行" /></label>
            <label>工序（增额或工费差额必填）<input v-model="processName" aria-label="改单差额工序" /></label>
            <label>工人（工费差额必填）<input v-model="workerName" aria-label="改单差额工人" /></label>
            <div class="change-actions"><button class="secondary-button" type="button" @click="confirmUnchanged">确认金额不变</button><button class="secondary-button" type="button" @click="confirmDelta">确认差额</button><button class="secondary-button" type="button" @click="stage('reject', { amountUnchanged: false, note: financeNote.trim() })">拒绝改单</button></div>
          </fieldset>
        </template>
        <button class="primary-button" type="button" :disabled="locked || selectedChange.status !== 'FINANCE_CONFIRMED'" @click="applyChange">生效改单</button>
      </section>
    </div>
    <section v-for="row in evidenceTargets" :key="row.id" v-show="selectedChange?.id === row.id" :inert="selectedChange?.id !== row.id || undefined">
      <h4>改单 {{ row.id }} 的原单证据</h4>
      <EvidenceCapture target-type="MOTHER_ORDER" :target-code="row.motherOrderCode" stage="ORDER_CHANGE" scene="改单" operation-type="ORDER_CHANGE" :operation-code="row.id" />
    </section>
    <details v-if="receipts.length"><summary>本次操作回执</summary><p v-for="(receipt, index) in receipts" :key="index">{{ receipt.text }}</p></details>
    <details><summary @click="loadTimeline">查看时间线</summary><p v-if="timelineError" class="operation-feedback warning">{{ timelineError }}</p><p v-for="(line, index) in timeline" :key="index">{{ line }}</p></details>
  </section>
</template>

<style scoped>
.order-change-panel { min-width: 0; overflow-wrap: anywhere; }
.order-change-panel fieldset, .change-compose, .change-history, .change-detail { display: grid; gap: 10px; min-width: 0; border: 1px solid var(--line); padding: 12px; border-radius: 10px; }
.order-change-panel fieldset { grid-template-columns: 1fr 1fr; align-items: end; }
.order-change-panel fieldset > button,
.order-change-panel fieldset > .change-actions,
.order-change-panel fieldset > p,
.order-change-panel label:has(textarea) { grid-column: 1 / -1; }
.order-change-panel fieldset { margin: 8px 0; }
.order-change-panel label { display: grid; gap: 6px; min-width: 0; }
.order-change-panel input { box-sizing: border-box; width: 100%; min-width: 0; min-height: 40px; }
.change-record-grid { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(0, 2fr); gap: 14px; align-items: start; }
.change-history button { text-align: left; }
.change-diff { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 6px 12px; margin: 0; }
.change-diff dd { margin: 0; white-space: pre-wrap; }
.change-actions { display: flex; flex-wrap: wrap; gap: 8px; }
@media (max-width: 900px) { .change-record-grid { grid-template-columns: minmax(0, 1fr); } .order-change-panel input { font-size: 16px; min-height: 44px; } .order-change-panel button { min-height: 44px; } }
</style>
