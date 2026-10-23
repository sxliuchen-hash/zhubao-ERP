<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import type { OpenOrderChangeAction, OrderActionTarget } from '../orders/orderActions';
import OrderChangePanel from '../orders/OrderChangePanel.vue';
import { ApiRequestError } from '../../services/apiClient';
import {
  fetchProcessProgress, fetchProcessProgressHistory, openProcessProgress, submitProcessProgress,
  type ProcessProgressHead, type ProcessProgressRow, type ProcessProgressTarget,
} from '../../services/processProgressApi';

const props = withDefaults(defineProps<{
  initialCode?: string;
  initialTarget?: OrderActionTarget | null;
  bindMobileScan?: boolean;
  embedded?: boolean;
  formallyDeliveredProcess?: string;
}>(), { bindMobileScan: false, embedded: false, initialTarget: null });
const emit = defineEmits<{ saved: [result: ProcessProgressHead]; targetSelected: [target: OrderActionTarget] }>();
const percents = [0, 30, 60, 90, 100];
const query = ref('');
const processFilter = ref('');
const dueBefore = ref('');
const page = ref(1);
const rows = ref<ProcessProgressRow[]>([]);
const history = ref<Awaited<ReturnType<typeof fetchProcessProgressHistory>>>([]);
const code = ref('');
const target = ref<ProcessProgressTarget | null>(null);
const percent = ref(0);
const note = ref('');
const reason = ref('');
const message = ref('');
const error = ref('');
const readError = ref('');
const receipt = ref<ProcessProgressHead | null>(null);
const submitting = ref(false);
const locating = ref(false);
const changeOpen = ref(false);
const changeCreated = ref(false);
const unknownRequest = ref<Parameters<typeof submitProcessProgress>[0] | null>(null);
const expectedCode = ref(props.initialTarget?.code ?? '');
const openOrderChange = inject<OpenOrderChangeAction | null>('zhubao-open-order-change', null);
const isDelivered = computed(() => Boolean(target.value?.formallyDelivered || (props.formallyDeliveredProcess && props.formallyDeliveredProcess === target.value?.currentProcess)));
const requestIds = new Map<string, string>();
let generation = 0;
let listGeneration = 0;
let historyGeneration = 0;
let disposed = false;

function invalidate() {
  generation += 1;
  historyGeneration += 1;
  target.value = null;
  history.value = [];
  receipt.value = null;
  message.value = '';
  error.value = '';
  readError.value = '';
  changeOpen.value = false;
  changeCreated.value = false;
  locating.value = false;
}
watch(code, invalidate, { flush: 'sync' });

onMounted(() => {
  if (!props.embedded) void reload();
  const initial = props.initialTarget?.code.trim() || props.initialCode?.trim();
  if (initial) { code.value = initial; void locate({ content: initial }); }
});
onBeforeUnmount(() => { disposed = true; generation += 1; listGeneration += 1; historyGeneration += 1; });

async function reload() {
  const token = ++listGeneration;
  readError.value = '';
  try {
    const loaded = await fetchProcessProgress(query.value, processFilter.value, dueBefore.value, page.value, 20);
    if (!disposed && token === listGeneration) rows.value = loaded;
  } catch (caught) {
    if (!disposed && token === listGeneration) readError.value = caught instanceof Error ? caught.message : '进度列表加载失败';
  }
}
async function locate(draft?: ScanDraft) {
  const content = (draft?.content ?? code.value).trim();
  invalidate();
  const token = generation;
  if (!content) { error.value = '请先扫码或输入单号'; return; }
  locating.value = true;
  try {
    const opened = await openProcessProgress(content);
    if (disposed || token !== generation) return;
    if (expectedCode.value && !opened.selectChild && opened.orderCode !== expectedCode.value) {
      error.value = '进度目标与当前单据或轮次不一致，请核对后重新打开。';
      return;
    }
    target.value = opened;
    percent.value = opened.percent;
    note.value = '';
    reason.value = '';
  } catch (caught) {
    if (!disposed && token === generation) error.value = caught instanceof Error ? caught.message : '无法定位单据';
  } finally {
    if (!disposed && token === generation) locating.value = false;
  }
}
function chooseChild(childCode: string) {
  if (!target.value?.children.some(child => child.code === childCode)) return;
  if (props.initialTarget) { emit('targetSelected', { objectType: 'WORK_ORDER', code: childCode }); return; }
  code.value = childCode;
  void locate({ content: childCode });
}

async function submit() {
  if (submitting.value || locating.value || receipt.value || !target.value || target.value.selectChild || isDelivered.value) return;
  error.value = '';
  const current = target.value;
  if (typeof current.version !== 'number') { error.value = '没有读到当前进度版本，请重新定位'; return; }
  if (!percents.includes(percent.value)) { error.value = '请选择五档工序进度'; return; }
  if (!unknownRequest.value && percent.value < current.percent && !reason.value.trim()) { error.value = '进度回退必须填写原因'; return; }
  const key = JSON.stringify([current.orderCode, current.currentProcess, current.version, percent.value, note.value, reason.value]);
  let requestId = requestIds.get(key);
  if (!requestId) {
    requestId = `progress-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
    requestIds.set(key, requestId);
  }
  const payload = unknownRequest.value ?? { code: current.orderCode, percent: percent.value, expectedVersion: current.version, note: note.value, reason: reason.value, requestId };
  if (payload.code !== current.orderCode) { error.value = '原进度请求尚待核对，请回到原单后再重试'; return; }
  const token = generation;
  submitting.value = true;
  try {
    const saved = await submitProcessProgress(payload);
    if (disposed || token !== generation) return;
    unknownRequest.value = null;
    receipt.value = saved;
    message.value = saved.percent === 100 ? '已记下 100%。这只是工序进度，仍需正式交工。' : `已记下 ${saved.percent}%`;
    target.value = { ...current, percent: saved.percent, version: saved.version };
    emit('saved', saved);
    if (!props.embedded) await reload();
  } catch (caught) {
    if (!disposed && token === generation) {
      const knownFailure = caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408;
      unknownRequest.value = knownFailure ? null : payload;
      error.value = caught instanceof Error ? caught.message : '进度提交失败';
    }
  } finally {
    submitting.value = false;
  }
}
function editAgain() { receipt.value = null; message.value = ''; }
function showChange() {
  if (openOrderChange && target.value && !target.value.selectChild) {
    openOrderChange({ objectType: props.initialTarget?.objectType ?? null, code: target.value.orderCode, motherOrderCode: target.value.motherOrderCode });
    return;
  }
  changeCreated.value = true;
  changeOpen.value = !changeOpen.value;
}
async function loadHistory() {
  const current = target.value;
  if (!current || current.selectChild) return;
  const token = ++historyGeneration;
  const selection = generation;
  try {
    const loaded = await fetchProcessProgressHistory(current.orderCode, current.currentProcess);
    if (!disposed && token === historyGeneration && selection === generation) history.value = loaded;
  } catch (caught) {
    if (!disposed && token === historyGeneration && selection === generation) readError.value = caught instanceof Error ? caught.message : '进度历史读取失败';
  }
}
</script>

<template>
  <section class="section-block process-progress-panel" aria-label="工序进度报工">
    <h2>工序进度</h2>
    <p>只记录当前工序的 0%、30%、60%、90%、100%。100% 不会自动交付、质检或写库存。</p>
    <div v-if="!embedded" class="progress-filters">
      <label>查找<input v-model="query" aria-label="进度查找" placeholder="单号、客户、工序或进度" @change="page = 1; reload()" /></label>
      <label>工序<input v-model="processFilter" aria-label="工序筛选" placeholder="雕刻、镶嵌或抛光" @change="page = 1; reload()" /></label>
      <label>期限到<input v-model="dueBefore" aria-label="期限筛选" type="date" @change="page = 1; reload()" /></label>
      <button class="secondary-button" type="button" :disabled="page <= 1" @click="page -= 1; reload()">上一页</button>
      <button class="secondary-button" type="button" @click="page += 1; reload()">下一页</button>
    </div>
    <ScanInput v-if="!initialTarget" v-model="code" field-text="进度单号" input-aria-label="进度单号" submit-label="定位" :busy="locating || submitting || Boolean(unknownRequest)" :bind-mobile-scan="bindMobileScan" @draft="invalidate" @recognize="locate" />
    <p v-if="locating" role="status">正在定位工序…</p>
    <button v-if="initialTarget" class="secondary-button" type="button" :disabled="locating || submitting || Boolean(unknownRequest)" @click="locate({ content: initialTarget.code })">刷新本单工序</button>
    <p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p>
    <p v-if="readError" class="operation-feedback danger" role="alert">只读刷新失败：{{ readError }}。已有成功回执保留。</p>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <div v-if="target?.selectChild">
      <p>这张母单已拆分，请选择具体子单。</p>
      <button v-for="child in target.children" :key="child.code" class="secondary-button" type="button" @click="chooseChild(child.code)">{{ child.code }}</button>
    </div>
    <form v-else-if="target" aria-label="保存工序进度" @submit.prevent="submit">
      <p>{{ target.orderCode }} · {{ target.customerName }} · {{ target.currentProcess }} · {{ target.processingRequirement || '未记录要求' }}</p>
      <p v-if="isDelivered">这道工序已经正式交付，不能再用进度代替。可刷新本单工序核对后续工序。</p>
      <fieldset v-else :disabled="submitting || Boolean(receipt) || Boolean(unknownRequest)">
        <label>进度<select v-model.number="percent" aria-label="工序进度"><option v-for="item in percents" :key="item" :value="item">{{ item }}%</option></select></label>
        <label>说明<input v-model="note" aria-label="进度说明" /></label>
        <label>回退原因<input v-model="reason" aria-label="进度回退原因" placeholder="只有往回调时必填" /></label>
        <button class="primary-button" type="submit" :disabled="submitting || Boolean(receipt) || Boolean(unknownRequest)">{{ submitting ? '提交中…' : '提交进度' }}</button>
      </fieldset>
      <p v-if="unknownRequest">原请求结果待核对：{{ unknownRequest.code }} · {{ unknownRequest.percent }}% · 版本 {{ unknownRequest.expectedVersion }}。保留原内容与编号。<button class="secondary-button" type="button" :disabled="submitting" @click="submit">原请求重试</button></p>
      <p v-if="receipt">回执：{{ receipt.orderCode }} · {{ receipt.processName }} · 进度版本 {{ receipt.version }} <button type="button" class="secondary-button" @click="editAgain">记录下一次进度</button></p>
      <button class="secondary-button" type="button" @click="loadHistory">查看进度历史</button>
      <p v-for="(item, index) in history" :key="index">{{ item.createdAt }} {{ item.operator }} {{ item.newPercent }}% {{ item.reason }}</p>
    </form>
    <template v-if="target && !target.selectChild && !embedded">
      <button class="secondary-button" type="button" :aria-expanded="changeOpen" @click="showChange">{{ changeOpen ? '收起改单办理' : '打开改单办理' }}</button>
      <OrderChangePanel v-if="changeCreated" v-show="changeOpen" :key="target.orderCode" :target="target" />
    </template>
    <div v-if="!embedded" class="desk-cards"><article v-for="row in rows" :key="row.orderCode + row.currentProcess" class="user-card"><strong>{{ row.orderCode }}</strong><p>母单 {{ row.motherOrderCode }} · {{ row.customerName }}</p><p>{{ row.currentProcess }} {{ row.percent }}% · {{ row.businessStatus }}</p></article></div>
  </section>
</template>

<style scoped>
.process-progress-panel { display: grid; gap: 12px; }
.process-progress-panel fieldset { display: grid; gap: 12px; margin: 0; padding: 0; border: 0; min-width: 0; }
.process-progress-panel label { display: grid; gap: 6px; }
.process-progress-panel input, .process-progress-panel select { box-sizing: border-box; width: 100%; min-width: 0; }
.progress-filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
@media (max-width: 900px) { .process-progress-panel input, .process-progress-panel select { font-size: 16px; min-height: 44px; } .process-progress-panel button { min-height: 44px; } }
</style>
