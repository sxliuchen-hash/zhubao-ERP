<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { apiGet, apiPost } from '../../services/apiClient';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';

const props = defineProps<{ target: { orderCode: string; motherOrderCode: string } }>();
const target = computed(() => props.target);
const code = computed(() => props.target.orderCode);
const message = ref('');
const error = ref('');
const readError = ref('');
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canFinance = computed(() => session.value?.effectivePermissions
  ? session.value.effectivePermissions.includes('FINANCE_BILLING_POST')
  : ['ADMIN', 'GENERAL_MANAGER', 'FINANCE_STATISTICIAN'].includes(session.value?.role ?? ''));
let disposed = false;
let readGeneration = 0;
onBeforeUnmount(() => { disposed = true; readGeneration += 1; });
interface OrderChangeRow {
  id: string;
  status: string;
  versionNo: number;
  expectedVersion: number;
  oldRequirement: string;
  newRequirement: string;
  oldDueDate: string;
  newDueDate: string;
  oldRoute: string;
  newRoute: string;
  reworkProcess: string;
  amountUnchanged: boolean;
  customerUnitPrice: number | null;
  workerFee: number | null;
  customerDelta: number | null;
  financeNote: string;
  motherOrderCode: string;
}

const changeRequirement = ref('');
const changeDue = ref('');
const changeRoute = ref('');
const changeRework = ref('');
const changeId = ref('');
const changes = ref<OrderChangeRow[]>([]);
const selectedChange = ref<OrderChangeRow | null>(null);
const financeNote = ref('');
const customerPrice = ref('');
const workerFee = ref('');
const customerDelta = ref('');
const timeline = ref<string[]>([]);
const changeAttempt = ref<RequestAttempt | null>(null);
const applyAttempt = ref<RequestAttempt | null>(null);
const changeSubmitting = ref(false);

function changeFingerprint() {
  const mother = target.value?.motherOrderCode || code.value.trim();
  const work = target.value?.orderCode && target.value.orderCode !== target.value.motherOrderCode ? target.value.orderCode : '';
  return [mother, work, changeRequirement.value.trim(), changeDue.value.trim(), changeRoute.value.trim(), changeRework.value.trim()].join('|');
}

function motherCode() {
  return target.value?.motherOrderCode || code.value.trim();
}

async function loadChanges() {
  const mother = motherCode();
  if (!mother) return;
  const token = ++readGeneration;
  readError.value = '';
  try {
    const loaded = await apiGet<OrderChangeRow[]>(`/api/business/order-changes?motherOrderCode=${encodeURIComponent(mother)}`);
    if (disposed || token !== readGeneration) return;
    changes.value = loaded;
    if (changeId.value) selectedChange.value = changes.value.find((item) => item.id === changeId.value) ?? selectedChange.value;
  } catch (caught) {
    if (!disposed && token === readGeneration) readError.value = caught instanceof Error ? caught.message : '改单列表读取失败';
  }
}

async function draftChange() {
  if (changeSubmitting.value) return;
  error.value = '';
  const mother = target.value?.motherOrderCode || code.value.trim();
  const work = target.value?.orderCode && target.value.orderCode !== target.value.motherOrderCode ? target.value.orderCode : '';
  changeAttempt.value = nextRequestAttempt(changeAttempt.value, changeFingerprint(), 'change');
  changeSubmitting.value = true;
  try {
    const created = await apiPost<{ motherOrderCode: string; workOrderCode: string; newRequirement: string; newDueDate: string; newRoute: string; reworkProcess: string; requestId: string }, { id: string }>(
      '/api/business/order-changes',
      {
        motherOrderCode: mother,
        workOrderCode: work,
        newRequirement: changeRequirement.value.trim(),
        newDueDate: changeDue.value.trim(),
        newRoute: changeRoute.value.trim(),
        reworkProcess: changeRework.value.trim(),
        requestId: changeAttempt.value.requestId,
      },
    );
    if (disposed) return;
    readGeneration += 1;
    changeId.value = created.id;
    message.value = `已保存改单 ${created.id}，送财务确认后才会生效`;
    await loadChanges();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '改单申请失败';
  } finally {
    changeSubmitting.value = false;
  }
}

async function runChange(path: string, body: unknown, success: string) {
  if (changeSubmitting.value || !changeId.value) return;
  error.value = '';
  changeSubmitting.value = true;
  try {
    const saved = await apiPost<unknown, OrderChangeRow>(path, body);
    if (disposed) return;
    readGeneration += 1;
    selectedChange.value = saved;
    changeId.value = selectedChange.value.id;
    message.value = success;
    await loadChanges();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '改单操作失败';
  } finally {
    changeSubmitting.value = false;
  }
}

async function submitChange() {
  await runChange(`/api/business/order-changes/${changeId.value}/finance-submissions`, {}, '已送财务确认');
}

async function confirmUnchanged() {
  if (!canFinance.value) return;
  await runChange(`/api/business/order-changes/${changeId.value}/finance-confirmations`, {
    amountUnchanged: true,
    note: financeNote.value.trim() || '金额不变',
  }, '财务已确认金额不变');
}

async function confirmDelta() {
  if (!canFinance.value) return;
  await runChange(`/api/business/order-changes/${changeId.value}/finance-confirmations`, {
    amountUnchanged: false,
    customerUnitPrice: Number(customerPrice.value),
    workerFee: Number(workerFee.value),
    customerDelta: Number(customerDelta.value || 0),
    workerDelta: 0,
    note: financeNote.value.trim(),
  }, '财务已确认差额');
}

async function rejectChange() {
  if (!canFinance.value) return;
  await runChange(`/api/business/order-changes/${changeId.value}/finance-rejections`, {
    amountUnchanged: false,
    note: financeNote.value.trim(),
  }, '财务已拒绝');
}

async function applyChange() {
  if (!selectedChange.value) {
    error.value = '请先打开要生效的改单';
    return;
  }
  const fingerprint = `${selectedChange.value.id}|${selectedChange.value.expectedVersion}`;
  applyAttempt.value = nextRequestAttempt(applyAttempt.value, fingerprint, 'change-apply');
  await runChange(`/api/business/order-changes/${selectedChange.value.id}/applications`, {
    requestId: applyAttempt.value.requestId,
    expectedVersion: selectedChange.value.expectedVersion,
  }, '改单已生效');
}

function openChange(row: OrderChangeRow) {
  if (selectedChange.value?.id !== row.id) {
    financeNote.value = '';
    customerPrice.value = '';
    workerFee.value = '';
    customerDelta.value = '';
  }
  selectedChange.value = row;
  changeId.value = row.id;
}

async function loadTimeline() {
  const mother = target.value?.motherOrderCode || code.value;
  try {
    const loaded = await apiGet<string[]>(`/api/business/order-changes/timeline?motherOrderCode=${encodeURIComponent(mother)}`);
    if (!disposed) timeline.value = loaded;
  } catch (caught) {
    if (!disposed) readError.value = caught instanceof Error ? caught.message : '时间线读取失败';
  }
}
</script>

<template>
  <section class="order-change-panel section-block" aria-label="单据改单办理">
    <p>母单 {{ target.motherOrderCode }} · 当前目标 {{ target.orderCode }}</p>
    <p>申请、财务确认和生效分别办理；进度版本不作为改单基准版本。</p>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p>
    <p v-if="readError" class="operation-feedback danger" role="alert">只读刷新失败：{{ readError }}。已有回执保留。</p>
      <h3>改单</h3>
      <label>新要求<input v-model="changeRequirement" aria-label="改单新要求" /></label>
      <label>新期限<input v-model="changeDue" aria-label="改单新期限" /></label>
      <label>新路线<input v-model="changeRoute" aria-label="改单新路线" placeholder="留空则沿用当前有效路线，如 雕刻、镶嵌" /></label>
      <label>重做工序<input v-model="changeRework" aria-label="改单重做工序" placeholder="已完成的工序才生成新轮次" /></label>
      <button class="secondary-button" type="button" :disabled="changeSubmitting" @click="draftChange">保存改单申请</button>
      <button class="secondary-button" type="button" :disabled="!changeId || changeSubmitting" @click="submitChange">送财务确认</button>
      <button class="secondary-button" type="button" @click="loadChanges">刷新改单</button>
      <article v-for="row in changes" :key="row.id">
        <button class="secondary-button" type="button" @click="openChange(row)">{{ row.id }} · {{ row.status }} · 版本 {{ row.versionNo }}</button>
      </article>
      <section v-if="selectedChange" aria-label="改单详情">
        <p>基准版本 {{ selectedChange.expectedVersion }} · 当前状态 {{ selectedChange.status }}</p>
        <p>要求：{{ selectedChange.oldRequirement || '空' }} → {{ selectedChange.newRequirement || '空' }}</p>
        <p>期限：{{ selectedChange.oldDueDate || '空' }} → {{ selectedChange.newDueDate || '空' }}</p>
        <p>路线：{{ selectedChange.oldRoute || '空' }} → {{ selectedChange.newRoute || '空' }}</p>
        <p v-if="selectedChange.reworkProcess">重做工序：{{ selectedChange.reworkProcess }}</p>
        <template v-if="canFinance">
        <label>财务说明<input v-model="financeNote" aria-label="改单财务说明" /></label>
        <label>客户单价<input v-model="customerPrice" aria-label="改单客户单价" /></label>
        <label>工人工费<input v-model="workerFee" aria-label="改单工人工费" /></label>
        <label>客户差额<input v-model="customerDelta" aria-label="改单客户差额" /></label>
        <button class="secondary-button" type="button" :disabled="changeSubmitting" @click="confirmUnchanged">确认金额不变</button>
        <button class="secondary-button" type="button" :disabled="changeSubmitting" @click="confirmDelta">确认差额</button>
        <button class="secondary-button" type="button" :disabled="changeSubmitting" @click="rejectChange">拒绝改单</button>
        </template>
        <button class="primary-button" type="button" :disabled="changeSubmitting || !['FINANCE_CONFIRMED', 'APPLIED'].includes(selectedChange.status)" @click="applyChange">生效改单</button>
        <h4>改单证据</h4>
        <EvidenceCapture
          target-type="MOTHER_ORDER"
          :target-code="selectedChange.motherOrderCode || motherCode()"
          stage="ORDER_CHANGE"
          scene="改单"
          operation-type="ORDER_CHANGE"
          :operation-code="selectedChange.id"
        />
      </section>
      <button class="secondary-button" type="button" @click="loadTimeline">查看时间线</button>
      <p v-for="(line, index) in timeline" :key="'t' + index">{{ line }}</p>
  </section>
</template>

<style scoped>
.order-change-panel { display: grid; gap: 12px; }
.order-change-panel label { display: grid; gap: 6px; min-width: 0; }
.order-change-panel input { box-sizing: border-box; width: 100%; min-width: 0; }
@media (max-width: 900px) { .order-change-panel input { font-size: 16px; min-height: 44px; } .order-change-panel button { min-height: 44px; } }
</style>
