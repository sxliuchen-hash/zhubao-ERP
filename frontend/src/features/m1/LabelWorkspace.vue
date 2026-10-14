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
import { buildLabelMarkup, labelFaceCss, labelPreviewScale, openLabelPrint } from './labelPrint';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';
import { apiGet, apiPost } from '../../services/apiClient';

// 标签打印工作台。标签只印固定信息，进度靠扫码查看。
// 前期只做黑白打印：预览和出纸都是白底黑字，按标签纸毫米尺寸单独打印。

const preview = ref<LabelPayload | null>(null);
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

const labelMarkup = computed(() => preview.value ? buildLabelMarkup(preview.value) : '');
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
  if (response.requiresSelection) {
    preview.value = null;
    loadedSticker.value = null;
    stickerHint.value = '请选择母单或子单';
    return;
  }
  const target = canonicalTarget(response);
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
  if (preview.value) openLabelPrint(preview.value);
}

const printJobId = ref('');
const printJobStatus = ref('');
const printFailure = ref('');
const reprintJobReason = ref('');
const printAttempt = ref<RequestAttempt | null>(null);
const reprintAttempt = ref<RequestAttempt | null>(null);
const printSubmitting = ref(false);

function printStatusLabel(status: string) {
  if (status === 'SPOOLED') return '已送入系统队列';
  if (status === 'PENDING') return '待领取';
  if (status === 'LEASED') return '已领取';
  if (status === 'FAILED') return '发送前失败';
  if (status === 'UNCERTAIN') return '不确定是否出纸';
  if (status === 'CANCELED') return '已取消';
  return status;
}

function printFingerprint() {
  const current = preview.value;
  // 同版刷新预览可能生成新实例，重试仍复用原请求；业务版本变化必须另建打印任务。
  const version = current?.qrContent.split('|')[3] ?? '';
  return [current?.primaryCode ?? '', current?.templateCode || 'FLOW', String(current?.printerSetting.copies || 1), version].join('|');
}

async function submitPrintJob() {
  if (!preview.value || printSubmitting.value) return;
  errorMessage.value = '';
  printAttempt.value = nextRequestAttempt(printAttempt.value, printFingerprint(), `print-${preview.value.primaryCode}`);
  printSubmitting.value = true;
  try {
    const job = await apiPost<{ requestId: string; businessCode: string; templateCode: string; copies: number }, { id: string; status: string; failureReason: string }>(
      '/api/label/print-jobs',
      {
        requestId: printAttempt.value.requestId,
        businessCode: preview.value.primaryCode,
        templateCode: preview.value.templateCode || 'FLOW',
        copies: preview.value.printerSetting.copies || 1,
      },
    );
    printJobId.value = job.id;
    printJobStatus.value = printStatusLabel(job.status);
    printFailure.value = job.failureReason || '';
    feedback.value = `已提交打印任务 ${job.id}。已送入系统队列不等于已经出纸。`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '打印任务提交失败';
  } finally {
    printSubmitting.value = false;
  }
}

async function refreshPrintJob() {
  if (!printJobId.value) return;
  const job = await apiGet<{ status: string; failureReason: string }>(`/api/label/print-jobs/${printJobId.value}`);
  printJobStatus.value = printStatusLabel(job.status);
  printFailure.value = job.failureReason || '';
}

async function reprintPrintJob() {
  if (printSubmitting.value) return;
  if (!printJobId.value || !reprintJobReason.value.trim()) {
    errorMessage.value = '人工补打必须填写原因';
    return;
  }
  const sourceJobId = printJobId.value;
  const reason = reprintJobReason.value.trim();
  reprintAttempt.value = nextRequestAttempt(reprintAttempt.value, `${sourceJobId}|${reason}`, 'reprint');
  printSubmitting.value = true;
  errorMessage.value = '';
  try {
    const job = await apiPost<{ requestId: string; reason: string }, { id: string; status: string; failureReason: string }>(
      `/api/label/print-jobs/${sourceJobId}/retries`,
      { requestId: reprintAttempt.value.requestId, reason },
    );
    printJobId.value = job.id;
    printJobStatus.value = printStatusLabel(job.status);
    printFailure.value = job.failureReason || '';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '人工补打失败';
  } finally {
    printSubmitting.value = false;
  }
}

// ---- 模板管理 ----
const showTemplateEditor = ref(false);
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

async function submitTemplate() {
  templateFeedback.value = '';
  templateError.value = '';
  if (!templateForm.value.templateCode.trim() || !templateForm.value.templateName.trim()) {
    templateError.value = '请填写模板编码和名称';
    return;
  }
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
    });
    templateFeedback.value = `已保存模板：${saved.templateName}`;
    showTemplateEditor.value = false;
    await loadTemplates();
  } catch (error) {
    templateError.value = error instanceof Error ? error.message : '模板保存失败，请检查后端 API 或权限';
  }
}

onMounted(loadTemplates);
</script>

<template>
  <div class="label-desk page-stack" :class="{ 'has-preview': preview }">
    <section class="label-intro">
      <h2>标签打印</h2>
      <p class="label-note">
        40×30 mm 贴纸保留单号、二维码、验证码、客户、加工要求和期限；详细信息扫码查看。打印时选择佳博 GP-9134T，并核对纸张尺寸和缩放为 100%。
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
            <input v-model="form.primaryCode" type="text" placeholder="如 CC-20260709-001-JL" />
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
            <button type="button" class="primary-button" :disabled="printSubmitting" @click="submitPrintJob">提交到打印工位</button>
          </div>
        </div>

        <div class="label-print-area">
          <component :is="'style'">{{ labelFaceCss }}</component>
          <div class="label-mat">
            <div class="label-sheet" :style="sheetStyle">
              <div class="label-scale" :style="labelScaleStyle" v-html="labelMarkup"></div>
            </div>
          </div>
          <p class="qr-content-line">二维码内容：{{ preview.qrContent }}。浏览器预览打印不等于厂内出纸。</p>
          <p v-if="printJobId">任务 {{ printJobId }} · {{ printJobStatus }}<template v-if="printFailure"> · {{ printFailure }}</template></p>
          <button v-if="printJobId" type="button" class="secondary-button" @click="refreshPrintJob">刷新任务状态</button>
          <label v-if="printJobId">人工补打原因<input v-model="reprintJobReason" aria-label="人工补打原因" /></label>
          <button v-if="printJobId" type="button" class="secondary-button" :disabled="printSubmitting" @click="reprintPrintJob">人工补打</button>
        </div>
      </section>
      <section v-else class="label-preview"><p class="hint">请先按单号打开贴纸或生成标签，成功后再预览和打印。</p></section>
    </div>

    <!-- 模板与打印机设置管理 -->
    <section class="template-manager">
      <div class="manager-head">
        <h3>标签模板与打印机设置</h3>
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
            <span>模板字段（来源用于母单自动带值）</span>
            <button type="button" class="link-button" @click="addTemplateField">+ 添加字段</button>
          </div>
          <div v-for="(field, index) in templateForm.fields" :key="index" class="field-row">
            <input v-model="field.label" type="text" placeholder="字段名，如 客户" />
            <select v-model="field.source">
              <option value="">人工填写</option>
              <option v-for="option in motherFieldSourceOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="link-button danger" @click="removeTemplateField(index)">删除</button>
          </div>
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
</style>
