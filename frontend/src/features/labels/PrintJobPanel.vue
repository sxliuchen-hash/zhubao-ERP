<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { LabelPayload } from '../../services/labelApi';
import type { PrintBatchCommand, PrintBatchView, PrintQueueView, PrintTargetJobView } from '../../services/printRoutingTypes';
import { ApiRequestError, apiGet, apiPost } from '../../services/apiClient';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';

const props = defineProps<{ payload: LabelPayload; disabled?: boolean }>();
const emit = defineEmits<{ locked: [value: boolean] }>();
const queues = ref<PrintQueueView[]>([]);
const selectedTargets = ref<string[]>([]);
const copiesPerTarget = ref(1);
const submitting = ref(false);
const busyJob = ref('');
const error = ref('');
const readError = ref('');
const feedback = ref('');
type Receipt = { fingerprint: string; batch: PrintBatchView; preview: LabelPayload };
const receipts = ref<Receipt[]>([]);
type PendingPrint = { body: PrintBatchCommand; fingerprint: string; preview: LabelPayload; sheets: number };
const pending = ref<PendingPrint | null>(null);
const printAttempt = ref<RequestAttempt | null>(null);
const paperChecks = ref<Record<string, string>>({});
const rerouteTargets = ref<Record<string, string>>({});
const rerouteReasons = ref<Record<string, string>>({});
const rerouteAttempts = new Map<string, RequestAttempt>();
type Reroute = { jobId: string; body: { requestId: string; target: { stationId: string; queueId: string }; reason: string }; original: Receipt };
const unknownReroutes = ref<Record<string, Reroute>>({});
const unknownPaperChecks = ref<Record<string, string>>({});
const intent = ref(0);
let disposed = false;
let readGeneration = 0;
onBeforeUnmount(() => { disposed = true; ++readGeneration; });
const locked = computed(() => submitting.value || Boolean(pending.value) || Boolean(busyJob.value) || Object.keys(unknownReroutes.value).length > 0 || Object.keys(unknownPaperChecks.value).length > 0);
watch(locked, value => emit('locked', value), { immediate: true });
const eligibleQueues = computed(() => queuesFor(props.payload));
const chosenTargets = computed(() => eligibleQueues.value.filter(queue => selectedTargets.value.includes(`${queue.stationId}|${queue.queueId}`)));
const estimatedSheets = computed(() => chosenTargets.value.length * Number(copiesPerTarget.value || 0));
function queuesFor(payload: LabelPayload) {
  return queues.value.filter(queue => queue.enabled && (queue.labelType === 'ALL' || queue.labelType === payload.type) && queue.widthMm === payload.printerSetting.widthMm && queue.heightMm === payload.printerSetting.heightMm);
}
function fingerprint() {
  const label = props.payload;
  return JSON.stringify([label.type, label.primaryCode, label.templateCode || 'FLOW', label.qrContent, label.printerSetting, label.layout,
    label.fields, label.storageSources, label.storageEdition, label.brandTitle, label.footerLeft, label.footerRight, Number(copiesPerTarget.value), chosenTargets.value.map(queue => [queue.stationId, queue.queueId]).sort(), intent.value]);
}
const submitted = computed(() => receipts.value.some(receipt => receipt.fingerprint === fingerprint()));
async function loadQueues() {
  readError.value = '';
  try { const loaded = await apiGet<PrintQueueView[]>('/api/label/print-queues'); if (!disposed) queues.value = loaded; }
  catch (caught) { if (!disposed) readError.value = caught instanceof Error ? caught.message : '打印队列读取失败'; }
}
onMounted(loadQueues);
async function submitPrintJob(retry = false) {
  if (props.disabled || (retry && !pending.value)) return;
  if (submitting.value || busyJob.value || Object.keys(unknownReroutes.value).length || Object.keys(unknownPaperChecks.value).length || (pending.value && !retry)) return;
  if (!retry && submitted.value) { feedback.value = '该请求已提交，请核对已有批次状态。'; return; }
  if (!retry && (!chosenTargets.value.length || !Number.isInteger(Number(copiesPerTarget.value)) || Number(copiesPerTarget.value) < 1 || Number(copiesPerTarget.value) > 20)) { error.value = '请选择打印目标并填写每目标 1 到 20 份'; return; }
  let command = retry ? pending.value : null;
  if (!command) {
    const key = fingerprint();
    printAttempt.value = nextRequestAttempt(printAttempt.value, key, 'print');
    command = { fingerprint: key, preview: JSON.parse(JSON.stringify(props.payload)) as LabelPayload, sheets: estimatedSheets.value, body: {
      requestId: printAttempt.value.requestId, businessCode: props.payload.primaryCode, templateCode: props.payload.templateCode || 'FLOW',
      copiesPerTarget: Number(copiesPerTarget.value), targets: chosenTargets.value.map(queue => ({ stationId: queue.stationId, queueId: queue.queueId })),
    } };
  }
  ++readGeneration;
  submitting.value = true; error.value = '';
  try {
    const batch = await apiPost<PrintBatchCommand, PrintBatchView>('/api/label/print-batches', command.body);
    if (disposed) return;
    if (!batch?.id || !Array.isArray(batch.jobs)) throw new Error('打印回执不完整，请保留原请求核对');
    pending.value = null;
    receipts.value = [...receipts.value.filter(receipt => receipt.batch.id !== batch.id), { fingerprint: command.fingerprint, batch, preview: command.preview }];
    feedback.value = `已提交打印批次 ${batch.id}，共 ${batch.jobs.length} 个目标、预计 ${command.sheets} 张。送入系统队列不等于已经出纸。`;
  } catch (caught) {
    if (disposed) return;
    const known = caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408;
    // 原结果未知时，即使后续版本冲突，也不能生成新编号绕过原任务。
    pending.value = known && !retry ? null : command;
    error.value = caught instanceof Error ? caught.message : '打印任务提交失败';
  } finally { submitting.value = false; }
}
function nextPrint() { if (!locked.value && !props.disabled) { intent.value += 1; feedback.value = '已开启另一批打印草稿，请重新核对目标和份数后提交。'; } }
function statusLabel(status: string) { return ({ SPOOLED: '已送入系统队列', PENDING: '待工位上线或领取', LEASED: '已领取', FAILED: '失败', UNCERTAIN: '不确定是否出纸', CANCELED: '已取消' } as Record<string, string>)[status] || status; }
function plainFailure(reason: string) {
  if (!reason) return '打印没有送出';
  if (reason.includes('保存标识')) return '保存要求无法打印';
  if (reason.includes('空间不足')) return '40×30 纸放不下保存说明';
  if (reason.includes('没有名为') || reason.includes('未映射')) return '找不到打印机';
  return reason;
}
async function cancelFailed(job: PrintTargetJobView) {
  if (locked.value || props.disabled || job.status !== 'FAILED') return;
  ++readGeneration;
  busyJob.value = job.id;
  error.value = '';
  try {
    await apiPost(`/api/label/print-target-jobs/${encodeURIComponent(job.id)}/cancellations`, {});
    if (!disposed) { job.status = 'CANCELED'; feedback.value = '已取消'; await refreshPrintJob(); }
  } catch (caught) {
    if (!disposed) error.value = caught instanceof Error ? caught.message : '取消失败';
  } finally { busyJob.value = ''; }
}
function snapshot(receipt: Receipt): Record<string, unknown> | null {
  try { const parsed: unknown = JSON.parse(receipt.batch.contentSnapshot || ''); return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : null; }
  catch { return null; }
}
function snapshotLabel(receipt: Receipt) { const data = snapshot(receipt); return data ? `${String(data.businessCode || data.primaryCode || receipt.batch.businessCode || '')} · ${String(data.version || '')} · ${String(data.instance || '')}` : '未返回任务快照，请核对任务编号'; }
function queuesForReceipt(receipt: Receipt) {
  const data = snapshot(receipt);
  const qr = String(data?.qr || '');
  const type = qr.startsWith('JG|mother|') ? 'MOTHER' : qr.startsWith('JG|work_order|') ? 'WORK_ORDER' : receipt.preview.type;
  return queues.value.filter(queue => queue.enabled && (queue.labelType === 'ALL' || queue.labelType === type)
    && queue.widthMm === (data?.widthMm ?? receipt.preview.printerSetting.widthMm) && queue.heightMm === (data?.heightMm ?? receipt.preview.printerSetting.heightMm));
}
async function refreshPrintJob() {
  const token = ++readGeneration;
  readError.value = '';
  const entries = [...receipts.value];
  const loaded = await Promise.allSettled(entries.map(receipt => apiGet<PrintBatchView>(`/api/label/print-batches/${encodeURIComponent(receipt.batch.id)}`)));
  if (disposed || token !== readGeneration) return;
  loaded.forEach((result, index) => {
    if (result.status === 'fulfilled' && result.value?.id === entries[index].batch.id) {
      const current = receipts.value.find(receipt => receipt.batch.id === result.value.id);
      if (current) current.batch = result.value;
      for (const job of result.value.jobs) {
        if (unknownPaperChecks.value[job.id] === job.paperCheckResult) delete unknownPaperChecks.value[job.id];
      }
    } else readError.value += `${entries[index].batch.id} 刷新失败；原回执保留。`;
  });
}
async function verifyTargetPaper(job: PrintTargetJobView) {
  if (locked.value || props.disabled) return;
  const result = paperChecks.value[job.id]?.trim();
  if (!result) { error.value = '请填写该目标的出纸核实结果'; return; }
  ++readGeneration;
  busyJob.value = job.id; error.value = '';
  try { await apiPost(`/api/label/print-target-jobs/${encodeURIComponent(job.id)}/paper-checks`, { result }); if (!disposed) { job.paperCheckResult = result; feedback.value = `已记录 ${job.id} 核纸结果`; await refreshPrintJob(); } }
  catch (caught) {
    if (!disposed) {
      const known = caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408;
      if (!known) unknownPaperChecks.value[job.id] = result;
      error.value = caught instanceof Error ? caught.message : '出纸核实失败';
    }
  }
  finally { busyJob.value = ''; }
}
async function rerouteTarget(job: PrintTargetJobView, receipt: Receipt, retry = false) {
  if (props.disabled || (retry && !unknownReroutes.value[job.id])) return;
  if (submitting.value || busyJob.value || pending.value || Object.keys(unknownPaperChecks.value).length || (!retry && Object.keys(unknownReroutes.value).length)) return;
  let command = retry ? unknownReroutes.value[job.id] : null;
  if (!command) {
    const queue = queuesForReceipt(receipt).find(item => `${item.stationId}|${item.queueId}` === rerouteTargets.value[job.id]);
    const reason = rerouteReasons.value[job.id]?.trim();
    if (!queue || !reason) { error.value = '改派须选择新目标并填写原因'; return; }
    const key = JSON.stringify([job.id, queue.stationId, queue.queueId, reason]);
    const attempt = nextRequestAttempt(rerouteAttempts.get(job.id) ?? null, key, 'reroute'); rerouteAttempts.set(job.id, attempt);
    command = { jobId: job.id, original: receipt, body: { requestId: attempt.requestId, target: { stationId: queue.stationId, queueId: queue.queueId }, reason } };
  }
  ++readGeneration;
  busyJob.value = job.id; error.value = '';
  try {
    const replacement = await apiPost<typeof command.body, PrintBatchView>(`/api/label/print-target-jobs/${encodeURIComponent(command.jobId)}/reroute`, command.body);
    if (disposed) return;
    if (!replacement?.id || !Array.isArray(replacement.jobs)) throw new Error('改派回执不完整，请保留原请求核对');
    delete unknownReroutes.value[job.id];
    receipts.value = [...receipts.value.filter(item => item.batch.id !== replacement.id), { fingerprint: `reroute:${replacement.id}`, batch: replacement, preview: command.original.preview }];
    job.status = 'CANCELED';
    feedback.value = `原目标已取消，改派批次 ${replacement.id} 已创建。请核对新目标状态。`;
    await refreshPrintJob();
  } catch (caught) {
    if (disposed) return;
    const known = caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408;
    if (!known || retry) unknownReroutes.value[job.id] = command;
    error.value = caught instanceof Error ? caught.message : '改派失败';
  } finally { busyJob.value = ''; }
}
</script>

<template>
  <section class="print-job-panel page-stack" aria-label="单据打印任务">
    <p v-if="feedback" class="operation-feedback" role="status">{{ feedback }}</p><p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p><p v-if="readError" class="operation-feedback warning" role="alert">{{ readError }}</p>
    <fieldset class="print-targets" aria-label="打印目标" :disabled="locked || disabled">
      <legend>选择工位和打印队列</legend><p v-if="!eligibleQueues.length">暂无匹配当前标签类型和纸张尺寸的队列；请由管理员配置工位及队列。</p>
      <label v-for="queue in eligibleQueues" :key="`${queue.stationId}|${queue.queueId}`"><input v-model="selectedTargets" type="checkbox" :value="`${queue.stationId}|${queue.queueId}`" />{{ queue.stationId }} · {{ queue.name }}（{{ queue.queueId }}）</label>
      <label>每目标份数<input v-model.number="copiesPerTarget" type="number" min="1" max="20" aria-label="每目标份数" /></label><p>目标 {{ chosenTargets.length }} 个 × 每目标 {{ copiesPerTarget }} 份 = 预计 {{ estimatedSheets }} 张；每个目标都会独立打印。</p>
      <button class="primary-button" type="button" :disabled="locked || disabled || submitted || !chosenTargets.length" @click="submitPrintJob()">{{ submitting ? '提交中…' : '提交到打印工位' }}</button>
    </fieldset>
    <p v-if="pending" class="operation-feedback warning">原打印请求结果待核对：{{ pending.body.businessCode }} · {{ pending.body.requestId }}。目标、份数和原编号已保留。<button class="secondary-button" type="button" :disabled="submitting || Boolean(busyJob) || disabled" @click="submitPrintJob(true)">原打印请求重试</button></p>
    <button v-if="submitted" class="secondary-button" type="button" :disabled="locked || disabled" @click="nextPrint">另建一批打印草稿</button>
    <button class="secondary-button" type="button" :disabled="locked" @click="loadQueues">重读打印队列</button>
    <button v-if="receipts.length" class="secondary-button" type="button" @click="refreshPrintJob">刷新目标状态</button>
    <article v-for="receipt in receipts" :key="receipt.batch.id" class="print-targets">
      <h3>批次 {{ receipt.batch.id }}</h3><p>任务冻结快照：{{ snapshotLabel(receipt) }}。送入系统队列不等于已经出纸。</p>
      <details v-if="snapshot(receipt)" class="frozen-snapshot"><summary>查看任务原始快照</summary><p>二维码：{{ snapshot(receipt)?.qr }}</p><p>纸张：{{ snapshot(receipt)?.widthMm }}×{{ snapshot(receipt)?.heightMm }}mm · {{ snapshot(receipt)?.dpi }} DPI</p><pre>{{ JSON.stringify(snapshot(receipt)?.fields, null, 2) }}</pre></details>
      <p v-if="snapshot(receipt)?.qr && snapshot(receipt)?.qr !== receipt.preview.qrContent" class="operation-feedback warning">任务快照与当时预览的版本或实例不同，请以冻结快照核对。</p>
      <div v-for="job in receipt.batch.jobs" :key="job.id" class="print-target-job">
        <template v-if="job.status === 'FAILED'">
          <p>失败：{{ plainFailure(job.failureReason) }}</p>
          <button class="secondary-button" type="button" :disabled="locked || disabled" @click="cancelFailed(job)">取消</button>
        </template>
        <template v-else>
        <strong>{{ job.targetStationId }} / {{ job.targetQueueId }}</strong> · {{ job.id }} · {{ statusLabel(job.status) }}
        <p v-if="job.failureReason">{{ job.failureReason }}</p><p v-if="job.paperCheckResult">出纸核实：{{ job.paperCheckResult }}</p><p v-if="job.predecessorId">来源任务 {{ job.predecessorId }} · {{ job.retryReason }}</p>
        <p v-if="unknownPaperChecks[job.id]" class="operation-feedback warning">{{ job.id }} 核纸记录结果待核对：{{ unknownPaperChecks[job.id] }}。请刷新目标状态核对原记录，尚未开放重复记录或改派。</p>
        <fieldset v-if="['SPOOLED', 'UNCERTAIN'].includes(job.status)" :disabled="locked || disabled"><label>核纸结果<select v-model="paperChecks[job.id]" :aria-label="`${job.id}核纸结果`"><option value="">请选择</option><option>确认未出纸</option><option>已出纸</option><option>仍不确定</option></select></label><button class="secondary-button" type="button" @click="verifyTargetPaper(job)">记录核纸</button></fieldset>
        <fieldset v-if="job.status === 'PENDING' || (['SPOOLED', 'UNCERTAIN'].includes(job.status) && job.paperCheckResult === '确认未出纸')" :disabled="locked || disabled">
          <label>改派目标<select v-model="rerouteTargets[job.id]" :aria-label="`${job.id}改派目标`"><option value="">请选择新工位和队列</option><option v-for="queue in queuesForReceipt(receipt).filter(item => item.stationId !== job.targetStationId || item.queueId !== job.targetQueueId)" :key="`${queue.stationId}|${queue.queueId}`" :value="`${queue.stationId}|${queue.queueId}`">{{ queue.stationId }} / {{ queue.queueId }} · {{ queue.name }}</option></select></label><label>改派原因<input v-model="rerouteReasons[job.id]" :aria-label="`${job.id}改派原因`" /></label><button class="secondary-button" type="button" @click="rerouteTarget(job, receipt)">取消原目标并创建关联任务</button>
        </fieldset>
        <p v-if="unknownReroutes[job.id]">原改派结果待核对，保留原目标与编号。<button class="secondary-button" type="button" :disabled="Boolean(busyJob) || disabled" @click="rerouteTarget(job, receipt, true)">原改派请求重试</button></p>
        </template>
      </div>
    </article>
  </section>
</template>

<style scoped>
.print-job-panel { min-width: 0; overflow-wrap: anywhere; }
.print-targets { display: grid; gap: 10px; margin: 0; padding: 12px; border: 1px solid var(--line); border-radius: 8px; min-width: 0; }
.print-targets label { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.print-targets input[type='number'] { width: 72px; }
.print-target-job { display: grid; gap: 8px; border-top: 1px solid var(--line); padding-top: 10px; min-width: 0; }
.print-target-job fieldset { min-width: 0; border: 0; padding: 0; display: grid; gap: 8px; }
input, select, button { min-height: 40px; max-width: 100%; box-sizing: border-box; }
input[type='checkbox'] { min-height: 20px; }
h3, p { margin: 0; }
.frozen-snapshot pre { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
