<script setup lang="ts">
import { onMounted, ref } from 'vue';
import ScanInput from '../scan/ScanInput.vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';
import { apiGet, apiPost } from '../../services/apiClient';
import {
  fetchProcessProgress,
  fetchProcessProgressHistory,
  openProcessProgress,
  submitProcessProgress,
  type ProcessProgressRow,
  type ProcessProgressTarget,
} from '../../services/processProgressApi';

const percents = [0, 30, 60, 90, 100];
const query = ref('');
const processFilter = ref('');
const dueBefore = ref('');
const page = ref(1);
const rows = ref<ProcessProgressRow[]>([]);
const history = ref<{ newPercent: number; operator: string; createdAt: string; reason: string }[]>([]);
const code = ref('');
const target = ref<ProcessProgressTarget | null>(null);
const percent = ref(0);
const note = ref('');
const reason = ref('');
const message = ref('');
const error = ref('');
const requestIds = ref<Record<string, string>>({});

onMounted(() => {
  void reload();
});

async function reload() {
  error.value = '';
  try {
    rows.value = await fetchProcessProgress(query.value, processFilter.value, dueBefore.value, page.value, 20);
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '进度加载失败';
  }
}

async function locate(draft?: ScanDraft) {
  error.value = '';
  message.value = '';
  target.value = null;
  const content = (draft?.content ?? code.value).trim();
  if (!content) {
    error.value = '请先扫码或输入单号';
    return;
  }
  try {
    const opened = await openProcessProgress(content);
    target.value = opened;
    if (opened && !opened.selectChild) {
      percent.value = opened.percent;
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '无法定位单据';
  }
}

function chooseChild(childCode: string) {
  code.value = childCode;
  void locate({ content: childCode });
}

async function submit() {
  error.value = '';
  message.value = '';
  if (!target.value || target.value.selectChild || target.value.formallyDelivered) return;
  if (typeof target.value.version !== 'number') {
    error.value = '没有读到当前进度版本，请重新定位';
    return;
  }
  const key = `${target.value.orderCode}|${target.value.currentProcess}|${percent.value}|${note.value}|${reason.value}`;
  const requestId = ensureRequestId(key);
  try {
    const saved = await submitProcessProgress({
      code: target.value.orderCode,
      percent: percent.value,
      expectedVersion: target.value.version,
      note: note.value,
      reason: reason.value,
      requestId,
    });
    delete requestIds.value[key];
    message.value = saved.percent === 100
      ? '已记下 100%。这只是工序进度，仍需在岗位报工里正式交付。'
      : `已记下 ${saved.percent}%`;
    await reload();
    await locate();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '进度提交失败';
  }
}

function ensureRequestId(key: string) {
  const current = requestIds.value[key];
  if (current) return current;
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}`;
  const created = `progress-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
  requestIds.value = { ...requestIds.value, [key]: created };
  return created;
}

async function loadHistory() {
  if (!target.value || target.value.selectChild) return;
  history.value = await fetchProcessProgressHistory(target.value.orderCode, target.value.currentProcess);
}

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
  changes.value = await apiGet<OrderChangeRow[]>(`/api/business/order-changes?motherOrderCode=${encodeURIComponent(mother)}`);
  if (changeId.value) {
    selectedChange.value = changes.value.find((item) => item.id === changeId.value) ?? selectedChange.value;
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
    selectedChange.value = await apiPost<unknown, OrderChangeRow>(path, body);
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
  await runChange(`/api/business/order-changes/${changeId.value}/finance-confirmations`, {
    amountUnchanged: true,
    note: financeNote.value.trim() || '金额不变',
  }, '财务已确认金额不变');
}

async function confirmDelta() {
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
  selectedChange.value = row;
  changeId.value = row.id;
}

async function loadTimeline() {
  const mother = target.value?.motherOrderCode || code.value;
  timeline.value = await apiGet<string[]>(`/api/business/order-changes/timeline?motherOrderCode=${encodeURIComponent(mother)}`);
}
</script>

<template>
  <section class="section-block" aria-label="工序进度报工">
    <h2>工序进度</h2>
    <p>只记录当前工序的 0%、30%、60%、90%、100%。100% 不会自动交付、质检或写库存。</p>
    <label>查找<input v-model="query" aria-label="进度查找" placeholder="单号、客户、工序或进度" @change="page = 1; reload()" /></label>
    <label>工序<input v-model="processFilter" aria-label="工序筛选" placeholder="雕刻、镶嵌或抛光" @change="page = 1; reload()" /></label>
    <label>期限到<input v-model="dueBefore" aria-label="期限筛选" placeholder="2026-12-31" @change="page = 1; reload()" /></label>
    <ScanInput v-model="code" field-text="进度单号" input-aria-label="进度单号" submit-label="定位" @recognize="locate" />
    <button class="secondary-button" type="button" @click="page -= 1; reload()" :disabled="page <= 1">上一页</button>
    <button class="secondary-button" type="button" @click="page += 1; reload()">下一页</button>
    <p v-if="error" class="operation-feedback danger">{{ error }}</p>
    <p v-if="message" class="operation-feedback">{{ message }}</p>
    <div v-if="target?.selectChild">
      <p>这张母单已拆分，请选择具体子单。</p>
      <button v-for="child in target.children" :key="child.code" class="secondary-button" type="button" @click="chooseChild(child.code)">
        {{ child.code }}
      </button>
    </div>
    <form v-else-if="target" @submit.prevent="submit">
      <p>{{ target.customerName }} · {{ target.currentProcess }} · {{ target.processingRequirement || '未记录要求' }}</p>
      <p v-if="target.formallyDelivered">这道工序已经正式交付，不能再用进度代替。</p>
      <template v-else>
        <label>进度
          <select v-model.number="percent" aria-label="工序进度">
            <option v-for="item in percents" :key="item" :value="item">{{ item }}%</option>
          </select>
        </label>
        <label>说明<input v-model="note" aria-label="进度说明" /></label>
        <label>回退原因<input v-model="reason" aria-label="进度回退原因" placeholder="只有往回调时必填" /></label>
        <button class="primary-button" type="submit">提交进度</button>
        <button class="secondary-button" type="button" @click="loadHistory">查看进度历史</button>
        <p v-for="(item, index) in history" :key="index">{{ item.createdAt }} {{ item.operator }} {{ item.newPercent }}% {{ item.reason }}</p>
      </template>
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
        <label>财务说明<input v-model="financeNote" aria-label="改单财务说明" /></label>
        <label>客户单价<input v-model="customerPrice" aria-label="改单客户单价" /></label>
        <label>工人工费<input v-model="workerFee" aria-label="改单工人工费" /></label>
        <label>客户差额<input v-model="customerDelta" aria-label="改单客户差额" /></label>
        <button class="secondary-button" type="button" :disabled="changeSubmitting" @click="confirmUnchanged">确认金额不变</button>
        <button class="secondary-button" type="button" :disabled="changeSubmitting" @click="confirmDelta">确认差额</button>
        <button class="secondary-button" type="button" :disabled="changeSubmitting" @click="rejectChange">拒绝改单</button>
        <button class="primary-button" type="button" :disabled="changeSubmitting" @click="applyChange">生效改单</button>
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
    </form>
    <div class="desk-cards">
      <article v-for="row in rows" :key="row.orderCode + row.currentProcess" class="user-card">
        <strong>{{ row.orderCode }}</strong>
        <p>母单 {{ row.motherOrderCode }} · {{ row.customerName }}</p>
        <p>{{ row.currentProcess }} {{ row.percent }}% · {{ row.businessStatus }}</p>
      </article>
    </div>
  </section>
</template>
