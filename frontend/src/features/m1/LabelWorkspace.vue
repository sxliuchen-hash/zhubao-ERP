<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref, watch, type Ref } from 'vue';
import {
  fetchLabelTemplates,
  fetchMotherLabel,
  fetchWorkOrderLabel,
  generateLabel,
  labelTypeOptions,
  motherFieldSourceOptions,
  reprintLabel,
  saveLabelTemplate,
  type LabelField,
  type LabelLayout,
  type LabelPayload,
  type LabelTemplate,
  type LabelTemplateField,
  type LabelType,
} from '../../services/labelApi';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import type { TraceTarget } from '../scan/traceTarget';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import type { ScanRelatedTarget } from '../../services/scanContextApi';
import { applyCustomerMark, customerInitials } from './customerInitials';
import { buildLabelMarkup, labelFaceCss, labelPreviewScale, openLabelPrint } from './labelPrint';
import { fetchCustomers } from '../../services/masterDataApi';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';
import { apiGet, apiPost } from '../../services/apiClient';
import type { AuthSession } from '../../services/authApi';

// 标签打印工作台。标签只印固定信息，进度靠扫码查看。
// 前期只做黑白打印：预览和出纸都是白底黑字，按标签纸毫米尺寸单独打印。

const preview = ref<LabelPayload | null>(null);
const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canManageRoutes = computed(() => authSession.value?.role === 'ADMIN');
const templatesError = ref('');
const feedback = ref('');
const errorMessage = ref('');
const submitting = ref(false);
const stickerQuery = ref('');
const stickerHint = ref('');
const labelScan = reactive(useScanContext());
const reprintReason = ref('');
const loadedSticker = ref<{ type: LabelType; code: string } | null>(null);

// 模板列表（连后端后加载）
const templates = ref<LabelTemplate[]>([]);
const templateKeyword = ref('');
const templatesLoaded = ref(false);

// 通用生成表单
const form = ref<{
  type: LabelType;
  primaryCode: string;
  templateCode: string;
  fields: LabelField[];
  footerLeft: string;
  footerRight: string;
}>({
  type: 'MOTHER',
  primaryCode: '',
  templateCode: '',
  fields: [
    { name: '客户', value: '' },
    { name: '物料', value: '' },
    { name: '数量', value: '' },
  ],
  footerLeft: '',
  footerRight: '',
});

// 按母单号便捷生成
const motherOrderCode = ref('');
const motherTemplateCode = ref('');
const customerMarkId = ref('');
const customerMarks = ref<Array<{ customerId: number; name: string; initials: string }>>([]);
const selectedMark = computed(() => customerMarks.value.find((item) => String(item.customerId) === customerMarkId.value) ?? null);
const duplicateMark = computed(() => {
  const current = selectedMark.value;
  if (!current) return false;
  return customerMarks.value.some((item) => item.customerId !== current.customerId && item.initials === current.initials);
});
const printedPreview = computed(() => preview.value
  ? applyCustomerMark(preview.value, selectedMark.value?.initials ?? '')
  : null);

const labelMarkup = computed(() => printedPreview.value ? buildLabelMarkup(printedPreview.value) : '');
const sheetStyle = computed(() => ({
  width: `calc(${preview.value?.printerSetting.widthMm ?? 40}mm * var(--label-scale, ${labelPreviewScale}))`,
  height: `calc(${preview.value?.printerSetting.heightMm ?? 30}mm * var(--label-scale, ${labelPreviewScale}))`,
}));
const labelScaleStyle = computed(() => ({
  transform: `scale(var(--label-scale, ${labelPreviewScale}))`,
  transformOrigin: 'top left',
  width: `${(preview.value?.printerSetting.widthMm ?? 40)}mm`,
  height: `${(preview.value?.printerSetting.heightMm ?? 30)}mm`,
}));

// 当前类型可选模板
const templatesForType = computed(() =>
  templates.value.filter((template) => template.labelType === form.value.type && template.enabled),
);
const motherTemplates = computed(() =>
  templates.value.filter((template) => template.labelType === 'MOTHER' && template.enabled),
);
const visibleTemplates = computed(() => {
  const text = templateKeyword.value.trim().toLowerCase();
  if (!text) return templates.value;
  return templates.value.filter((template) => `${template.templateCode} ${template.templateName} ${template.labelType}`.toLowerCase().includes(text));
});

watch(() => form.value.templateCode, (code) => {
  const template = templates.value.find((item) => item.templateCode === code && item.labelType === form.value.type);
  if (!template) return;
  const previous = new Map(form.value.fields.map((field) => [field.name, field.value]));
  form.value.fields = [...template.fields]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((field) => ({ name: field.label, value: previous.get(field.label) || '' }));
});
watch(() => form.value.type, () => { form.value.templateCode = ''; });

function addField() {
  form.value.fields.push({ name: '', value: '' });
}

function removeField(index: number) {
  form.value.fields.splice(index, 1);
}

async function loadTemplates() {
  templatesError.value = '';
  try {
    templates.value = await fetchLabelTemplates();
    templatesLoaded.value = true;
  } catch (error) {
    templatesError.value = error instanceof Error ? error.message : '标签模板读取失败';
    templatesLoaded.value = false;
  }
}

async function submitGenerate() {
  preview.value = null;
  loadedSticker.value = null;
  feedback.value = '';
  errorMessage.value = '';
  if (!form.value.primaryCode.trim()) {
    errorMessage.value = '请填写标签主编码';
    return;
  }
  submitting.value = true;
  try {
    const payload = await generateLabel({
      type: form.value.type,
      primaryCode: form.value.primaryCode.trim(),
      fields: form.value.fields
        .filter((field) => field.name.trim() !== '')
        .map((field) => ({ name: field.name.trim(), value: field.value.trim() })),
      footerLeft: form.value.footerLeft.trim() || undefined,
      footerRight: form.value.footerRight.trim() || undefined,
      templateCode: form.value.templateCode || undefined,
    });
    preview.value = payload;
    feedback.value = `已生成 ${payload.typeLabel}标签：${payload.primaryCode}`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '标签生成失败，请检查后端 API 或权限';
  } finally {
    submitting.value = false;
  }
}

function clearStickerTarget() {
  const had = labelScan.context !== null || preview.value !== null || loadedSticker.value !== null;
  labelScan.invalidate();
  preview.value = null;
  loadedSticker.value = null;
  submitting.value = false;
  if (had) stickerHint.value = '单号已变化，请重新查询';
}

watch(stickerQuery, () => clearStickerTarget(), { flush: 'sync' });

const incomingLabel = inject<Ref<TraceTarget | null>>('zhubao-label-target', ref(null));
watch(incomingLabel, (target) => {
  if (!target?.code) return;
  stickerQuery.value = target.code;
  void openFieldSticker({ content: target.code, ...(target.objectType ? { objectType: target.objectType } : {}) });
}, { immediate: true });

async function openFieldSticker(draft: ScanDraft) {
  feedback.value = '';
  errorMessage.value = '';
  stickerHint.value = '';
  const raw = draft.content.trim();
  if (!raw) {
    labelScan.invalidate();
    preview.value = null;
    loadedSticker.value = null;
    errorMessage.value = '请填写母单号、子单号或贴纸二维码';
    return;
  }
  const response = await labelScan.lookup({
    content: raw,
    objectType: draft.objectType,
    intent: 'LOOKUP',
  });
  if (!response) {
    preview.value = null;
    loadedSticker.value = null;
    if (labelScan.error) errorMessage.value = labelScan.error;
    return;
  }
  const seen = labelScan.revision;
  const knownMother = response.target.objectType === 'MOTHER_ORDER' && response.target.code
    ? { objectType: 'MOTHER_ORDER' as const, code: response.target.code }
    : null;
  if (response.requiresSelection && !knownMother) {
    preview.value = null;
    loadedSticker.value = null;
    stickerHint.value = '请选择母单或子单';
    return;
  }
  const target = canonicalTarget(response) ?? knownMother;
  if (!target) {
    errorMessage.value = '没有定位到单据';
    return;
  }
  if (target.objectType !== 'MOTHER_ORDER' && target.objectType !== 'WORK_ORDER') {
    errorMessage.value = '这不是母单或子单贴纸';
    return;
  }
  const kind = target.objectType === 'WORK_ORDER' ? 'WORK_ORDER' : 'MOTHER';
  submitting.value = true;
  try {
    const payload = kind === 'WORK_ORDER'
      ? await fetchWorkOrderLabel(target.code)
      : await fetchMotherLabel(target.code);
    if (labelScan.revision !== seen) return;
    showSticker(payload, kind);
    if (response.requiresSelection && kind === 'MOTHER') {
      stickerHint.value = `已打开母单 ${payload.primaryCode}。这张母单已拆成子单，母单标签仍可打印；下方可改选子单贴纸。`;
    }
  } catch (error) {
    if (labelScan.revision !== seen) return;
    errorMessage.value = error instanceof Error ? error.message : '贴纸生成失败';
  } finally {
    if (labelScan.revision === seen) submitting.value = false;
  }
}

async function chooseStickerTarget(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
  await openFieldSticker({ content: related.code, objectType: related.objectType });
}

function showSticker(payload: LabelPayload, kind: 'MOTHER' | 'WORK_ORDER') {
  preview.value = payload;
  loadedSticker.value = { type: payload.type, code: payload.primaryCode };
  const hasCode = payload.fields.some((field) => field.name === '验证码' && field.value.trim() !== '');
  stickerHint.value = hasCode
    ? (kind === 'WORK_ORDER' ? `已打开子单 ${payload.primaryCode}` : `已打开母单 ${payload.primaryCode}`)
    : '收货母单标签可直接打印；用于生产流转前，请在来料页选择不拆或拆成子单，生成验证码。';
  feedback.value = `已从单据生成贴纸：${payload.primaryCode}`;
}

async function reprintSticker() {
  feedback.value = '';
  errorMessage.value = '';
  if (!loadedSticker.value) {
    errorMessage.value = '请先按单号打开现场贴纸';
    return;
  }
  if (!reprintReason.value.trim()) {
    errorMessage.value = '补打必须填写原因';
    return;
  }
  submitting.value = true;
  try {
    const payload = await reprintLabel({
      type: loadedSticker.value.type,
      primaryCode: loadedSticker.value.code,
      reason: reprintReason.value.trim(),
    });
    preview.value = payload;
    feedback.value = `已记录补打：${payload.primaryCode}`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '补打失败';
  } finally {
    submitting.value = false;
  }
}

async function loadMotherLabel() {
  preview.value = null;
  loadedSticker.value = null;
  feedback.value = '';
  errorMessage.value = '';
  if (!motherOrderCode.value.trim()) {
    errorMessage.value = '请填写母单号';
    return;
  }
  submitting.value = true;
  try {
    const payload = await fetchMotherLabel(motherOrderCode.value.trim(), motherTemplateCode.value || undefined);
    preview.value = payload;
    feedback.value = `已从结构化母单生成标签：${payload.primaryCode}`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '母单标签生成失败，请检查母单号或后端 API';
  } finally {
    submitting.value = false;
  }
}

function printLabel() {
  if (printedPreview.value) openLabelPrint(printedPreview.value);
}

async function loadCustomerMarks() {
  try {
    const rows = await fetchCustomers();
    if (!Array.isArray(rows)) {
      customerMarks.value = [];
      return;
    }
    customerMarks.value = rows
      .filter((row) => row.enabled !== false && typeof row.name === 'string' && row.name.trim() !== '')
      .map((row) => ({
        customerId: row.customerId,
        name: row.name.trim(),
        initials: customerInitials(row.name),
      }))
      .filter((row) => row.initials !== '')
      .sort((left, right) => left.initials.localeCompare(right.initials) || left.name.localeCompare(right.name, 'zh-CN'));
  } catch {
    customerMarks.value = [];
  }
}

interface PrintQueueView { stationId: string; queueId: string; name: string; labelType: string;
  widthMm: number; heightMm: number; enabled: boolean; defaultQueue: boolean }
interface PrintTargetJobView { id: string; targetStationId: string; targetQueueId: string; status: string;
  failureReason: string; paperCheckResult: string; copies: number; predecessorId: string; retryReason: string }
interface PrintBatchView { id: string; jobs: PrintTargetJobView[] }
const printQueues = ref<PrintQueueView[]>([]);
const allPrintQueues = ref<PrintQueueView[]>([]);
const printStations = ref<Array<{ stationId: string; name: string; enabled: boolean; remark: string }>>([]);
const stationForm = ref({ stationId: '', name: '', enabled: true, remark: '' });
const queueForm = ref({ stationId: '', queueId: '', name: '', labelType: 'ALL', widthMm: 40, heightMm: 30,
  enabled: true, defaultQueue: false });
const newStationToken = ref('');
const routeFeedback = ref('');
const selectedTargets = ref<string[]>([]);
const copiesPerTarget = ref(1);
const printBatch = ref<PrintBatchView | null>(null);
const paperChecks = ref<Record<string, string>>({});
const rerouteTargets = ref<Record<string, string>>({});
const rerouteReasons = ref<Record<string, string>>({});
const rerouteAttempts = ref<Record<string, RequestAttempt>>({});
const reroutedBatches = ref<PrintBatchView[]>([]);
const printAttempt = ref<RequestAttempt | null>(null);
const printSubmitting = ref(false);
const eligibleQueues = computed(() => printQueues.value.filter(queue => preview.value
  && (queue.labelType === 'ALL' || queue.labelType === preview.value.type)
  && queue.widthMm === preview.value.printerSetting.widthMm
  && queue.heightMm === preview.value.printerSetting.heightMm));
const chosenTargets = computed(() => eligibleQueues.value.filter(queue => selectedTargets.value.includes(`${queue.stationId}|${queue.queueId}`)));
const estimatedSheets = computed(() => chosenTargets.value.length * Number(copiesPerTarget.value || 0));

async function loadPrintQueues() {
  try {
    printQueues.value = await apiGet<PrintQueueView[]>('/api/label/print-queues');
    if (canManageRoutes.value) {
      printStations.value = await apiGet<typeof printStations.value>('/api/label/print-stations');
      allPrintQueues.value = await apiGet<PrintQueueView[]>('/api/label/print-queues/all');
    }
  }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '打印队列加载失败'; }
}
async function savePrintStation() {
  try {
    const result = await apiPost<typeof stationForm.value, { station: typeof stationForm.value; token: string }>(
      '/api/label/print-stations', stationForm.value);
    newStationToken.value = result.token;
    routeFeedback.value = `工位 ${result.station.stationId} 已保存${result.token ? '，请立即保存下方新凭据' : ''}`;
    await loadPrintQueues();
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '工位保存失败'; }
}
async function savePrintQueue() {
  try {
    await apiPost('/api/label/print-queues', queueForm.value);
    routeFeedback.value = `队列 ${queueForm.value.queueId} 已保存`;
    await loadPrintQueues();
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '队列保存失败'; }
}
async function rotateStationToken(id: string) {
  try {
    const result = await apiPost<{}, { token: string }>(`/api/label/print-stations/${encodeURIComponent(id)}/rotate-token`, {});
    newStationToken.value = result.token;
    routeFeedback.value = `工位 ${id} 凭据已轮换，请更新厂内程序配置`;
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '凭据轮换失败'; }
}

function printStatusLabel(status: string) {
  if (status === 'SPOOLED') return '已送入系统队列';
  if (status === 'PENDING') return '待工位上线或领取';
  if (status === 'LEASED') return '已领取';
  if (status === 'FAILED') return '发送前失败';
  if (status === 'UNCERTAIN') return '不确定是否出纸';
  if (status === 'CANCELED') return '已取消';
  return status;
}

function printFingerprint() {
  const current = printedPreview.value;
  // 同版同模板内容重试复用请求；换客户暗记后另建打印任务。
  const version = current?.qrContent.split('|')[3] ?? '';
  return JSON.stringify([
    current?.primaryCode ?? '', current?.templateCode || 'FLOW', version,
    current?.printerSetting, current?.layout, current?.fields,
    current?.brandTitle, current?.footerLeft, current?.footerRight,
    selectedMark.value?.initials ?? '',
    Number(copiesPerTarget.value), chosenTargets.value.map(queue => [queue.stationId, queue.queueId]).sort(),
  ]);
}

async function submitPrintJob() {
  if (!preview.value || printSubmitting.value) return;
  if (!chosenTargets.value.length || !Number.isInteger(Number(copiesPerTarget.value))
      || Number(copiesPerTarget.value) < 1 || Number(copiesPerTarget.value) > 20) {
    errorMessage.value = '请选择打印目标并填写每目标 1 到 20 份';
    return;
  }
  errorMessage.value = '';
  printAttempt.value = nextRequestAttempt(printAttempt.value, printFingerprint(), `print-${preview.value.primaryCode}`);
  printSubmitting.value = true;
  try {
    const batch = await apiPost<{ requestId: string; businessCode: string; templateCode: string;
      copiesPerTarget: number; targets: Array<{ stationId: string; queueId: string }>; customerMark: string }, PrintBatchView>(
      '/api/label/print-batches',
      {
        requestId: printAttempt.value.requestId,
        businessCode: preview.value.primaryCode,
        templateCode: preview.value.templateCode || 'FLOW',
        copiesPerTarget: Number(copiesPerTarget.value),
        targets: chosenTargets.value.map(queue => ({ stationId: queue.stationId, queueId: queue.queueId })),
        customerMark: selectedMark.value?.initials ?? '',
      },
    );
    printBatch.value = batch;
    feedback.value = `已提交打印批次 ${batch.id}，共 ${batch.jobs.length} 个目标、预计 ${estimatedSheets.value} 张。送入系统队列不等于已经出纸。`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '打印任务提交失败';
  } finally {
    printSubmitting.value = false;
  }
}

async function refreshPrintJob() {
  if (!printBatch.value) return;
  printBatch.value = await apiGet<PrintBatchView>(`/api/label/print-batches/${printBatch.value.id}`);
  reroutedBatches.value = await Promise.all(reroutedBatches.value.map(batch =>
    apiGet<PrintBatchView>(`/api/label/print-batches/${batch.id}`)));
}

async function verifyTargetPaper(jobId: string) {
  const result = paperChecks.value[jobId]?.trim();
  if (!result) { errorMessage.value = '请填写该目标的出纸核实结果'; return; }
  try {
    await apiPost(`/api/label/print-target-jobs/${jobId}/paper-checks`, { result });
    await refreshPrintJob();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '出纸核实失败';
  }
}
async function rerouteTarget(job: PrintTargetJobView) {
  const selected = rerouteTargets.value[job.id] || '';
  const queue = eligibleQueues.value.find(item => `${item.stationId}|${item.queueId}` === selected);
  const reason = rerouteReasons.value[job.id]?.trim();
  if (!queue || !reason) { errorMessage.value = '改派须选择新目标并填写原因'; return; }
  const fingerprint = JSON.stringify([job.id, selected, reason]);
  const attempt = nextRequestAttempt(rerouteAttempts.value[job.id] || null, fingerprint, `reroute-${job.id}`);
  rerouteAttempts.value[job.id] = attempt;
  try {
    const replacement = await apiPost<{ requestId: string; target: { stationId: string; queueId: string }; reason: string }, PrintBatchView>(
      `/api/label/print-target-jobs/${encodeURIComponent(job.id)}/reroute`,
      { requestId: attempt.requestId, target: { stationId: queue.stationId, queueId: queue.queueId }, reason },
    );
    await refreshPrintJob();
    reroutedBatches.value = [...reroutedBatches.value.filter(batch => batch.id !== replacement.id), replacement];
    feedback.value = `原目标已取消，改派批次 ${replacement.id} 已创建。请核对新目标状态。`;
    errorMessage.value = '';
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '改派失败'; }
}

// ---- 模板管理 ----
const showTemplateEditor = ref(false);
function emptyLayout(): LabelLayout {
  return { mode: 'AUTO', qrWidthMm: null, codeFontPt: null, fieldFontPt: null,
    emphasisFontPt: null, titleFontPt: null, footerFontPt: null };
}

const templateForm = ref<{
  id?: number;
  templateCode: string;
  templateName: string;
  labelType: LabelType;
  brandTitle: string;
  fields: LabelTemplateField[];
  footerLeftSource: string;
  footerRightSource: string;
  isDefault: boolean;
  enabled: boolean;
  widthMm: number;
  heightMm: number;
  dpi: number;
  printerName: string;
  copies: number;
  remark: string;
  layout: LabelLayout;
}>({
  templateCode: '',
  templateName: '',
  labelType: 'MOTHER',
  brandTitle: '珠宝来料加工系统',
  fields: [{ label: '客户', source: 'customer', sortOrder: 1 }],
  footerLeftSource: '',
  footerRightSource: '',
  isDefault: false,
  enabled: true,
  widthMm: 40,
  heightMm: 30,
  dpi: 300,
  printerName: '',
  copies: 1,
  remark: '',
  layout: emptyLayout(),
});
const templateFeedback = ref('');
const templateError = ref('');

function newTemplate() {
  templateForm.value = {
    templateCode: '',
    templateName: '',
    labelType: 'MOTHER',
    brandTitle: '珠宝来料加工系统',
    fields: [{ label: '客户', source: 'customer', sortOrder: 1 }],
    footerLeftSource: '',
    footerRightSource: '',
    isDefault: false,
    enabled: true,
    widthMm: 40,
    heightMm: 30,
    dpi: 300,
    printerName: '',
    copies: 1,
    remark: '',
    layout: emptyLayout(),
  };
  showTemplateEditor.value = true;
  templateFeedback.value = '';
  templateError.value = '';
}

function editTemplate(template: LabelTemplate) {
  templateForm.value = {
    id: template.id,
    templateCode: template.templateCode,
    templateName: template.templateName,
    labelType: template.labelType,
    brandTitle: template.brandTitle,
    fields: template.fields.map((field) => ({ ...field })),
    footerLeftSource: template.footerLeftSource,
    footerRightSource: template.footerRightSource,
    isDefault: template.isDefault,
    enabled: template.enabled,
    widthMm: template.printerSetting.widthMm,
    heightMm: template.printerSetting.heightMm,
    dpi: template.printerSetting.dpi,
    printerName: template.printerSetting.printerName,
    copies: template.printerSetting.copies,
    remark: template.remark,
    layout: { ...emptyLayout(), ...template.layout },
  };
  showTemplateEditor.value = true;
  templateFeedback.value = '';
  templateError.value = '';
}

function addTemplateField() {
  templateForm.value.fields.push({ label: '', source: '', sortOrder: templateForm.value.fields.length + 1 });
}

function removeTemplateField(index: number) {
  templateForm.value.fields.splice(index, 1);
}

function moveTemplateField(index: number, direction: -1 | 1) {
  const next = index + direction;
  if (next < 0 || next >= templateForm.value.fields.length) return;
  [templateForm.value.fields[index], templateForm.value.fields[next]] =
    [templateForm.value.fields[next], templateForm.value.fields[index]];
}

const draftFieldExamples: Record<string, string> = {
  verificationCode: 'K7M2', customer: '示例客户', processingRequirement: '按要求加工',
  motherOrderCode: 'MD-20261014-0001',
  dueDate: '2026-10-20', code: 'MD-20261014-0001', goodsName: '戒托', materialCode: 'WL-001',
  quantity: '2 件', weight: '12 克', inboundDate: '2026-10-14', warehouseCode: 'A01',
  receiver: '收货人', express: '顺丰',
};
const templateDraft = computed<LabelPayload>(() => ({
  type: templateForm.value.labelType,
  typeLabel: labelTypeOptions.find((item) => item.value === templateForm.value.labelType)?.label ?? '标签',
  primaryCode: templateForm.value.labelType === 'WORK_ORDER' ? 'ZD-20261014-0001' : 'MD-20261014-0001',
  qrContent: templateForm.value.labelType === 'WORK_ORDER' ? 'JG|work_order|ZD-20261014-0001|0|PREVIEW' : 'JG|mother|MD-20261014-0001|0|PREVIEW',
  fields: [
    ...templateForm.value.fields.filter((field) => field.label.trim() &&
      !(templateForm.value.labelType === 'WORK_ORDER' && (field.source === 'motherOrderCode' || field.label.trim() === '母单'))).map((field) => ({
    name: field.label.trim(), value: draftFieldExamples[field.source] || '示例内容',
    })),
    ...(templateForm.value.labelType === 'WORK_ORDER' ? [{ name: '母单', value: 'MD-20261014-0001' }] : []),
  ],
  footerLeft: draftFieldExamples[templateForm.value.footerLeftSource] || '',
  footerRight: draftFieldExamples[templateForm.value.footerRightSource] || '',
  printedAt: '',
  brandTitle: templateForm.value.brandTitle,
  templateCode: templateForm.value.templateCode || 'DRAFT',
  printerSetting: {
    widthMm: templateForm.value.widthMm, heightMm: templateForm.value.heightMm,
    dpi: templateForm.value.dpi, printerName: templateForm.value.printerName,
    copies: templateForm.value.copies,
  },
  layout: templateForm.value.layout,
}));
const templateDraftMarkup = computed(() => buildLabelMarkup(templateDraft.value));
const templateDraftSheetStyle = computed(() => ({
  width: `calc(${templateForm.value.widthMm}mm * var(--label-scale, ${labelPreviewScale}))`,
  height: `calc(${templateForm.value.heightMm}mm * var(--label-scale, ${labelPreviewScale}))`,
}));
const templateDraftScaleStyle = computed(() => ({
  transform: `scale(var(--label-scale, ${labelPreviewScale}))`,
  transformOrigin: 'top left',
  width: `${templateForm.value.widthMm}mm`,
  height: `${templateForm.value.heightMm}mm`,
}));

function normalizedLayout(): LabelLayout | null {
  const layout = templateForm.value.layout;
  const result = { ...layout };
  const limits: Array<[keyof Omit<LabelLayout, 'mode'>, number, number]> = [
    ['qrWidthMm', 5, 100], ['codeFontPt', 4, 36], ['fieldFontPt', 4, 36],
    ['emphasisFontPt', 4, 36], ['titleFontPt', 4, 36], ['footerFontPt', 4, 36],
  ];
  for (const [key, min, max] of limits) {
    const value = result[key];
    if (value === null || String(value).trim() === '') {
      result[key] = null;
      continue;
    }
    if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
      templateError.value = `${key === 'qrWidthMm' ? '二维码宽度' : '字号'}须在 ${min}～${max} 之间`;
      return null;
    }
  }
  return result;
}

async function submitTemplate() {
  templateFeedback.value = '';
  templateError.value = '';
  if (!templateForm.value.templateCode.trim() || !templateForm.value.templateName.trim()) {
    templateError.value = '请填写模板编码和名称';
    return;
  }
  const layout = normalizedLayout();
  if (!layout) return;
  try {
    const saved = await saveLabelTemplate({
      id: templateForm.value.id,
      templateCode: templateForm.value.templateCode.trim(),
      templateName: templateForm.value.templateName.trim(),
      labelType: templateForm.value.labelType,
      brandTitle: templateForm.value.brandTitle.trim(),
      fields: templateForm.value.fields
        .filter((field) => field.label.trim() !== '')
        .map((field, index) => ({ label: field.label.trim(), source: field.source, sortOrder: index + 1 })),
      footerLeftSource: templateForm.value.footerLeftSource,
      footerRightSource: templateForm.value.footerRightSource,
      isDefault: templateForm.value.isDefault,
      enabled: templateForm.value.enabled,
      widthMm: templateForm.value.widthMm,
      heightMm: templateForm.value.heightMm,
      dpi: templateForm.value.dpi,
      printerName: templateForm.value.printerName.trim(),
      copies: templateForm.value.copies,
      remark: templateForm.value.remark.trim(),
      layout,
    });
    templateFeedback.value = `已保存模板：${saved.templateName}`;
    showTemplateEditor.value = false;
    await loadTemplates();
  } catch (error) {
    templateError.value = error instanceof Error ? error.message : '模板保存失败，请检查后端 API 或权限';
  }
}

onMounted(() => { void loadTemplates(); void loadPrintQueues(); void loadCustomerMarks(); });
</script>

<template>
  <div class="label-desk page-stack" :class="{ 'has-preview': preview }">
    <section class="label-intro">
      <h2>标签打印</h2>
      <p class="label-note">
        模板可设置字段、顺序、纸张尺寸与字号。二维码和主单号始终保留，详细信息可扫码查看。打印时请核对打印机纸张尺寸和缩放为 100%。
      </p>
    </section>

    <div class="label-layout">
      <section class="label-forms">
        <div class="form-card">
          <h3>现场贴纸</h3>
          <p class="hint">按母单号或子单号打开，字段从单据带出。也可以粘贴贴纸上的二维码内容。</p>
          <ScanInput
            v-model="stickerQuery"
            field-text="单号或二维码"
            input-aria-label="现场贴纸单号"
            placeholder="母单号、子单号或 JG|mother|单号"
            submit-label="打开贴纸"
            submit-aria-label="打开现场贴纸"
            photo-aria-label="拍贴纸"
            :busy="submitting || labelScan.loading"
            @draft="clearStickerTarget"
            @recognize="openFieldSticker"
          />
          <p v-if="stickerHint" class="hint">{{ stickerHint }}</p>
          <OrderContextCard :context="labelScan.context" @select="chooseStickerTarget" />
          <label class="field">
            <span>补打原因</span>
            <input v-model="reprintReason" type="text" aria-label="补打原因" placeholder="如 贴纸丢失" />
          </label>
          <button type="button" class="table-action" :disabled="submitting" @click="reprintSticker">补打并记录</button>
        </div>

        <div class="form-card">
          <h3>通用标签生成</h3>
          <p class="hint">选择模板后会带出字段名称与顺序，填写对应值即可生成。</p>
          <label class="field">
            <span>标签类型</span>
            <select v-model="form.type" aria-label="标签类型">
              <option v-for="option in labelTypeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label v-if="templatesForType.length" class="field">
            <span>使用模板（可选，留空用默认模板）</span>
            <select v-model="form.templateCode">
              <option value="">默认模板</option>
              <option v-for="template in templatesForType" :key="template.id" :value="template.templateCode">
                {{ template.templateName }}{{ template.isDefault ? '（默认）' : '' }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>主编码</span>
            <input v-model="form.primaryCode" type="text" placeholder="如 MD-20261014-0001" />
          </label>

          <div class="fields-block">
            <div class="fields-head">
              <span>标签字段（只放固定信息）</span>
              <button type="button" class="link-button" @click="addField">+ 添加字段</button>
            </div>
            <div v-for="(field, index) in form.fields" :key="index" class="field-row">
              <input v-model="field.name" type="text" placeholder="名称，如 客户" />
              <input v-model="field.value" type="text" placeholder="值，如 恒信珠宝" />
              <button type="button" class="link-button danger" @click="removeField(index)">删除</button>
            </div>
          </div>

          <label class="field">
            <span>左下角脚注</span>
            <input v-model="form.footerLeft" type="text" placeholder="如 快递：顺丰 SF..." />
          </label>
          <label class="field">
            <span>右下角脚注</span>
            <input v-model="form.footerRight" type="text" placeholder="如 收货人：何琴" />
          </label>

          <button type="button" class="primary-button" :disabled="submitting" @click="submitGenerate">
            生成标签预览
          </button>
        </div>

        <div class="form-card">
          <h3>按母单号便捷生成</h3>
          <p class="hint">从已落库的结构化母单直接生成母单标签，字段自动带出。</p>
          <label class="field">
            <span>母单号</span>
            <input v-model="motherOrderCode" type="text" placeholder="如 MO20260709001" />
          </label>
          <label v-if="motherTemplates.length" class="field">
            <span>使用模板（可选）</span>
            <select v-model="motherTemplateCode">
              <option value="">默认模板</option>
              <option v-for="template in motherTemplates" :key="template.id" :value="template.templateCode">
                {{ template.templateName }}{{ template.isDefault ? '（默认）' : '' }}
              </option>
            </select>
          </label>
          <button type="button" class="primary-button" :disabled="submitting" @click="loadMotherLabel">
            按母单生成
          </button>
        </div>

        <p v-if="feedback" class="feedback ok">{{ feedback }}</p>
        <p v-if="errorMessage" class="feedback error">{{ errorMessage }}</p>
      </section>

      <section v-if="preview" class="label-preview" aria-label="标签预览">
        <div class="preview-toolbar">
          <span>标签预览</span>
          <div class="toolbar-actions">
            <span class="printer-hint">
              {{ preview.printerSetting.widthMm }}×{{ preview.printerSetting.heightMm }}mm · 黑白 ·
              {{ preview.printerSetting.copies }} 份
              <template v-if="preview.printerSetting.printerName">· {{ preview.printerSetting.printerName }}</template>
            </span>
            <button type="button" class="primary-button" @click="printLabel">浏览器预览打印</button>
            <button type="button" class="primary-button" :disabled="printSubmitting || !chosenTargets.length" @click="submitPrintJob">提交到打印工位</button>
          </div>
        </div>

        <label class="customer-mark">
          <span>页脚客户暗记</span>
          <select v-model="customerMarkId" aria-label="页脚客户暗记">
            <option value="">不印暗记</option>
            <option v-for="item in customerMarks" :key="item.customerId" :value="String(item.customerId)">
              {{ item.initials }} · {{ item.name }}
            </option>
          </select>
        </label>
        <p class="hint">选择后，贴纸页脚只印拼音首字母，并去掉客户中文名。下拉中的名称只供内部选择。</p>
        <p v-if="duplicateMark" class="hint">还有其他客户是同一组首字母，贴纸上无法区分。</p>

        <div class="label-print-area">
          <component :is="'style'">{{ labelFaceCss }}</component>
          <div class="label-mat">
            <div class="label-sheet" :style="sheetStyle">
              <div class="label-scale" :style="labelScaleStyle" v-html="labelMarkup"></div>
            </div>
          </div>
          <p class="qr-content-line">二维码内容：{{ preview.qrContent }}。浏览器预览打印不等于厂内出纸。</p>
          <div class="print-targets" aria-label="打印目标">
            <strong>选择工位和打印队列</strong>
            <p v-if="!eligibleQueues.length">暂无匹配当前标签类型和纸张尺寸的队列；请由管理员配置工位及队列。</p>
            <label v-for="queue in eligibleQueues" :key="`${queue.stationId}|${queue.queueId}`">
              <input v-model="selectedTargets" type="checkbox" :value="`${queue.stationId}|${queue.queueId}`" />
              {{ queue.stationId }} · {{ queue.name }}（{{ queue.queueId }}）
            </label>
            <label>每目标份数 <input v-model.number="copiesPerTarget" type="number" min="1" max="20" aria-label="每目标份数" /></label>
            <p>目标 {{ chosenTargets.length }} 个 × 每目标 {{ copiesPerTarget }} 份 = 预计 {{ estimatedSheets }} 张；每个目标都会独立打印。</p>
          </div>
          <div v-if="printBatch" class="print-targets">
            <button type="button" class="secondary-button" @click="refreshPrintJob">刷新目标状态</button>
            <div v-for="batch in [printBatch, ...reroutedBatches]" :key="batch.id">
              <p>批次 {{ batch.id }} · SPOOLED 仅表示送入系统队列</p>
              <div v-for="job in batch.jobs" :key="job.id">
                <strong>{{ job.targetStationId }} / {{ job.targetQueueId }}</strong> · {{ job.id }} · {{ printStatusLabel(job.status) }}
                <span v-if="job.failureReason"> · {{ job.failureReason }}</span>
                <span v-if="job.paperCheckResult"> · 出纸核实：{{ job.paperCheckResult }}</span>
                <span v-if="job.predecessorId"> · 来源任务 {{ job.predecessorId }} · {{ job.retryReason }}</span>
                <label v-if="['SPOOLED', 'FAILED', 'UNCERTAIN'].includes(job.status)">核纸结果
                  <select v-model="paperChecks[job.id]" :aria-label="`${job.id}核纸结果`">
                    <option value="">请选择</option>
                    <option value="确认未出纸">确认未出纸</option>
                    <option value="已出纸">已出纸</option>
                    <option value="仍不确定">仍不确定</option>
                  </select>
                </label>
                <button v-if="['SPOOLED', 'FAILED', 'UNCERTAIN'].includes(job.status)" type="button"
                  class="secondary-button" @click="verifyTargetPaper(job.id)">记录核纸</button>
                <div v-if="job.status === 'PENDING' || (['SPOOLED', 'FAILED', 'UNCERTAIN'].includes(job.status) && job.paperCheckResult === '确认未出纸')">
                  <label>改派目标
                    <select v-model="rerouteTargets[job.id]" :aria-label="`${job.id}改派目标`">
                      <option value="">请选择新工位和队列</option>
                      <option v-for="queue in eligibleQueues.filter(item => item.stationId !== job.targetStationId || item.queueId !== job.targetQueueId)"
                        :key="`${queue.stationId}|${queue.queueId}`" :value="`${queue.stationId}|${queue.queueId}`">
                        {{ queue.stationId }} / {{ queue.queueId }} · {{ queue.name }}
                      </option>
                    </select>
                  </label>
                  <label>改派原因 <input v-model="rerouteReasons[job.id]" :aria-label="`${job.id}改派原因`" /></label>
                  <button type="button" class="secondary-button" @click="rerouteTarget(job)">取消原目标并创建关联任务</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
      <section v-else class="label-preview"><p class="hint">请先按单号打开贴纸或生成标签，成功后再预览和打印。</p></section>
    </div>

    <section v-if="canManageRoutes" class="template-manager" aria-label="工位与队列维护">
      <h3>工位与打印队列</h3>
      <p v-if="routeFeedback">{{ routeFeedback }}</p>
      <p v-if="newStationToken">新工位凭据（仅此时显示）：<code>{{ newStationToken }}</code></p>
      <div class="field-row">
        <label>工位 ID <input v-model="stationForm.stationId" aria-label="工位 ID" /></label>
        <label>工位名称 <input v-model="stationForm.name" aria-label="工位名称" /></label>
        <label>备注 <input v-model="stationForm.remark" aria-label="工位备注" /></label>
        <label><input v-model="stationForm.enabled" type="checkbox" />启用工位</label>
        <button type="button" class="secondary-button" @click="savePrintStation">保存工位</button>
      </div>
      <p v-for="station in printStations" :key="station.stationId">
        {{ station.stationId }} · {{ station.name }} · {{ station.enabled ? '启用' : '停用' }}
        <button type="button" class="link-button" @click="stationForm = { ...station }">编辑</button>
        <button type="button" class="link-button" @click="rotateStationToken(station.stationId)">轮换凭据</button>
      </p>
      <div class="field-row">
        <label>所属工位 <select v-model="queueForm.stationId" aria-label="队列工位">
          <option value="">选择工位</option><option v-for="station in printStations" :key="station.stationId" :value="station.stationId">{{ station.name }}</option>
        </select></label>
        <label>队列 ID <input v-model="queueForm.queueId" aria-label="队列 ID" /></label>
        <label>显示名称 <input v-model="queueForm.name" aria-label="队列名称" /></label>
        <label>标签类型 <select v-model="queueForm.labelType" aria-label="队列标签类型">
          <option value="ALL">母单与子单</option><option value="MOTHER">母单</option><option value="WORK_ORDER">子单</option>
        </select></label>
        <label>宽（mm）<input v-model.number="queueForm.widthMm" type="number" aria-label="队列纸宽" /></label>
        <label>高（mm）<input v-model.number="queueForm.heightMm" type="number" aria-label="队列纸高" /></label>
        <label><input v-model="queueForm.enabled" type="checkbox" />启用队列</label>
        <label><input v-model="queueForm.defaultQueue" type="checkbox" />默认队列</label>
        <button type="button" class="secondary-button" @click="savePrintQueue">保存队列</button>
      </div>
      <p v-for="queue in allPrintQueues" :key="`${queue.stationId}|${queue.queueId}`">
        {{ queue.stationId }} / {{ queue.queueId }} · {{ queue.name }} · {{ queue.enabled ? '启用' : '停用' }}
        <button type="button" class="link-button" @click="queueForm = { ...queue }">编辑</button>
      </p>
    </section>

    <!-- 模板与打印机设置管理 -->
    <section class="template-manager">
      <div class="manager-head">
        <h3>标签模板配置</h3>
        <button type="button" class="link-button" @click="newTemplate">+ 新建模板</button>
      </div>
      <p v-if="!templatesLoaded" class="hint">{{ templatesError || '正在读取标签模板' }}</p>
      <label v-else>
        <span>查找模板</span>
        <input v-model="templateKeyword" aria-label="查找模板" placeholder="编码或名称" />
      </label>
      <div v-if="templatesLoaded" class="desk-table-wrap">
      <table class="template-table">
        <thead>
          <tr>
            <th>模板编码</th>
            <th>名称</th>
            <th>类型</th>
            <th>默认</th>
            <th>启用</th>
            <th>打印尺寸</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="template in visibleTemplates" :key="template.id">
            <td>{{ template.templateCode }}</td>
            <td>{{ template.templateName }}</td>
            <td>{{ template.labelType }}</td>
            <td>{{ template.isDefault ? '是' : '' }}</td>
            <td>{{ template.enabled ? '启用' : '停用' }}</td>
            <td>{{ template.printerSetting.widthMm }}×{{ template.printerSetting.heightMm }}mm</td>
            <td><button type="button" class="link-button" @click="editTemplate(template)">编辑</button></td>
          </tr>
        </tbody>
      </table>
      </div>
      <div v-if="templatesLoaded" class="desk-cards">
        <article v-for="template in visibleTemplates" :key="`card-${template.id}`" class="desk-card production-handoff-card">
          <span class="desk-card-top">
            <strong class="code">{{ template.templateCode }}</strong>
            <span>{{ template.printerSetting.widthMm }}×{{ template.printerSetting.heightMm }}mm</span>
          </span>
          <span>{{ template.templateName }} · {{ template.labelType }}</span>
          <button type="button" class="link-button" @click="editTemplate(template)">编辑</button>
        </article>
      </div>

      <div v-if="showTemplateEditor" class="template-editor">
        <h4>{{ templateForm.id ? '编辑模板' : '新建模板' }}</h4>
        <div class="editor-grid">
          <label class="field">
            <span>模板编码</span>
            <input v-model="templateForm.templateCode" type="text" :disabled="!!templateForm.id" placeholder="如 MOTHER-BOX" />
          </label>
          <label class="field">
            <span>模板名称</span>
            <input v-model="templateForm.templateName" type="text" placeholder="如 母单周转盒模板" />
          </label>
          <label class="field">
            <span>标签类型</span>
            <select v-model="templateForm.labelType">
              <option v-for="option in labelTypeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>品牌抬头</span>
            <input v-model="templateForm.brandTitle" type="text" />
          </label>
        </div>

        <div class="fields-block">
          <div class="fields-head">
            <span>模板字段（母单、子单可自动带值）</span>
            <button type="button" class="link-button" @click="addTemplateField">+ 添加字段</button>
          </div>
          <div v-for="(field, index) in templateForm.fields" :key="index" class="field-row">
            <input v-model="field.label" :aria-label="`第${index + 1}个字段名称`" type="text" placeholder="字段名，如 客户" />
            <select v-model="field.source" :aria-label="`第${index + 1}个字段来源`">
              <option value="">通用标签手动填写</option>
              <option v-for="option in motherFieldSourceOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
            <span class="field-actions">
              <button type="button" class="link-button" :disabled="index === 0" :aria-label="`上移第${index + 1}个字段`" @click="moveTemplateField(index, -1)">↑</button>
              <button type="button" class="link-button" :disabled="index === templateForm.fields.length - 1" :aria-label="`下移第${index + 1}个字段`" @click="moveTemplateField(index, 1)">↓</button>
              <button type="button" class="link-button danger" :aria-label="`删除第${index + 1}个字段`" @click="removeTemplateField(index)">删除</button>
            </span>
          </div>
          <p class="hint">从上到下即打印顺序；不添加字段时只印单号和二维码。母单／子单字段须选择单据来源，手动字段用于通用标签生成。</p>
        </div>

        <div class="editor-grid">
          <label class="field">
            <span>左脚注来源</span>
            <select v-model="templateForm.footerLeftSource">
              <option value="">无</option>
              <option v-for="option in motherFieldSourceOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>右脚注来源</span>
            <select v-model="templateForm.footerRightSource">
              <option value="">无</option>
              <option v-for="option in motherFieldSourceOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>

        <h5>打印机设置（黑白）</h5>
        <div class="editor-grid">
          <label class="field">
            <span>宽度(mm)</span>
            <input v-model.number="templateForm.widthMm" type="number" min="1" />
          </label>
          <label class="field">
            <span>高度(mm)</span>
            <input v-model.number="templateForm.heightMm" type="number" min="1" />
          </label>
          <label class="field">
            <span>DPI</span>
            <input v-model.number="templateForm.dpi" type="number" min="1" />
          </label>
          <label class="field">
            <span>黑白打印机名称（提示用）</span>
            <input v-model="templateForm.printerName" type="text" placeholder="如 黑白标签机" />
          </label>
          <label class="field">
            <span>份数</span>
            <input v-model.number="templateForm.copies" type="number" min="1" />
          </label>
        </div>

        <h5>标签版式与字号</h5>
        <div class="editor-grid">
          <label class="field">
            <span>版式</span>
            <select v-model="templateForm.layout.mode" aria-label="标签版式">
              <option value="AUTO">按纸张尺寸自动</option>
              <option value="COMPACT">紧凑版</option>
              <option value="STANDARD">标准版</option>
            </select>
          </label>
          <label class="field"><span>二维码宽度 (mm)</span><input v-model.number="templateForm.layout.qrWidthMm" aria-label="二维码宽度" type="number" min="5" max="100" step="0.5" placeholder="默认" /></label>
          <label class="field"><span>单号字号 (pt)</span><input v-model.number="templateForm.layout.codeFontPt" aria-label="单号字号" type="number" min="4" max="36" step="0.5" placeholder="默认" /></label>
          <label class="field"><span>字段字号 (pt)</span><input v-model.number="templateForm.layout.fieldFontPt" aria-label="字段字号" type="number" min="4" max="36" step="0.5" placeholder="默认" /></label>
          <label class="field"><span>首个字段值字号 (pt)</span><input v-model.number="templateForm.layout.emphasisFontPt" aria-label="首个字段值字号" type="number" min="4" max="36" step="0.5" placeholder="默认" /></label>
          <label class="field"><span>类型字号 (pt)</span><input v-model.number="templateForm.layout.titleFontPt" aria-label="类型字号" type="number" min="4" max="36" step="0.5" placeholder="默认" /></label>
          <label class="field"><span>脚注字号 (pt)</span><input v-model.number="templateForm.layout.footerFontPt" aria-label="脚注字号" type="number" min="4" max="36" step="0.5" placeholder="默认" /></label>
        </div>
        <p class="hint">留空沿用系统字号。二维码和主单号始终保留，便于扫码追溯。调整后请用实际标签纸试印。</p>

        <div class="template-draft" aria-label="模板实时预览">
          <strong>模板实时预览（示例数据）</strong>
          <div class="label-mat">
            <component :is="'style'">{{ labelFaceCss }}</component>
            <div class="label-sheet" :style="templateDraftSheetStyle">
              <div class="label-scale" :style="templateDraftScaleStyle" v-html="templateDraftMarkup"></div>
            </div>
          </div>
        </div>

        <div class="editor-grid">
          <label class="field checkbox">
            <input v-model="templateForm.isDefault" type="checkbox" />
            <span>设为该类型默认模板</span>
          </label>
          <label class="field checkbox">
            <input v-model="templateForm.enabled" type="checkbox" />
            <span>启用</span>
          </label>
        </div>

        <div class="editor-actions">
          <button type="button" class="primary-button" @click="submitTemplate">保存模板</button>
          <button type="button" class="link-button" @click="showTemplateEditor = false">取消</button>
        </div>
        <p v-if="templateFeedback" class="feedback ok">{{ templateFeedback }}</p>
        <p v-if="templateError" class="feedback error">{{ templateError }}</p>
      </div>
      <p v-else-if="templateFeedback" class="feedback ok">{{ templateFeedback }}</p>
    </section>
  </div>
</template>

<style scoped>
.label-desk {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.label-intro h2 {
  margin: 0 0 4px;
}
.label-note {
  color: var(--ink-muted);
  font-size: 14px;
  line-height: 1.6;
}
.label-layout {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(360px, 1fr);
  gap: 20px;
  align-items: start;
}
.form-card,
.template-manager {
  background: var(--content);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
}
.form-card h3 {
  margin: 0 0 10px;
  font-size: 15px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--ink-muted);
}
.field.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.field input[type='text'],
.field input[type='number'],
.field select {
  padding: 7px 9px;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  font-size: 14px;
}
.fields-block {
  margin-bottom: 10px;
}
.fields-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--ink-muted);
  margin-bottom: 6px;
}
.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 6px;
  margin-bottom: 6px;
}
.field-actions { display: flex; align-items: center; gap: 4px; }
.template-draft { margin-top: 14px; display: grid; gap: 8px; font-size: 13px; }
.template-draft .label-mat { width: fit-content; max-width: 100%; }
.field-row input,
.field-row select {
  padding: 6px 8px;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  font-size: 13px;
}
.hint {
  color: var(--ink-muted);
  font-size: 12px;
  margin: 0 0 10px;
}
.feedback {
  font-size: 13px;
  padding: 8px 10px;
  border-radius: 6px;
  margin-top: 8px;
}
.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}
.customer-mark {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 400;
}
.customer-mark select {
  min-width: 12em;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.printer-hint {
  font-size: 12px;
  color: var(--ink-muted);
  font-weight: 400;
}
.label-mat {
  background: var(--workspace);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px;
  overflow: auto;
}
.label-sheet {
  position: relative;
}
.label-scale {
  position: absolute;
  top: 0;
  left: 0;
}
.qr-content-line {
  font-size: 12px;
  color: var(--ink-muted);
  margin-top: 8px;
  word-break: break-all;
}
.manager-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.manager-head h3 {
  margin: 0;
  font-size: 15px;
}
.template-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.template-table th,
.template-table td {
  border-bottom: 1px solid var(--border);
  padding: 8px 10px;
  text-align: left;
}
.template-table th {
  color: var(--ink-muted);
  font-weight: 600;
}
.template-editor {
  margin-top: 16px;
  border-top: 1px solid var(--border);
  padding-top: 16px;
}
.template-editor h4 {
  margin: 0 0 12px;
}
.template-editor h5 {
  margin: 12px 0 8px;
}
.editor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.editor-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

@media (max-width: 900px) {
  .label-desk.has-preview .label-preview { order: -1; }

  .label-desk,
  .label-layout,
  .label-layout > *,
  .form-card,
  .template-manager {
    min-width: 0;
    max-width: 100%;
  }

  .label-layout,
  .field-row { grid-template-columns: minmax(0, 1fr); }

  .field input[type='text'],
  .field input[type='number'],
  .field select,
  .field-row input,
  .field-row select {
    min-height: 44px;
    font-size: 16px;
  }

  .preview-toolbar,
  .manager-head,
  .editor-actions,
  .toolbar-actions {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .template-manager { overflow-x: auto; }
}

@media (max-width: 600px) {
  .label-preview { --label-scale: 1.1; }
  .label-mat { padding: 10px; }
}
.print-targets {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.print-targets label { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.print-targets input[type='number'] { width: 72px; }
</style>
