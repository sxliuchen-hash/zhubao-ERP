<script setup lang="ts">
import { computed, inject, onMounted, ref, watch, type Ref } from 'vue';
import {
  fetchLabelTemplates,
  fetchMotherLabel,
  generateLabel,
  labelTypeOptions,
  motherFieldSourceOptions,
  saveLabelTemplate,
  type LabelField,
  type LabelLayout,
  type LabelPayload,
  type LabelTemplate,
  type LabelTemplateField,
  type LabelType,
} from '../../services/labelApi';
import OrderLabelPanel from '../labels/OrderLabelPanel.vue';
import type { PrintQueueView } from '../../services/printRoutingTypes';
import { customerInitials } from './customerInitials';
import { buildLabelMarkup, labelFaceCss, labelPreviewScale } from './labelPrint';
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

async function loadMotherLabel() {
  preview.value = null;
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

const allPrintQueues = ref<PrintQueueView[]>([]);
const printStations = ref<Array<{ stationId: string; name: string; enabled: boolean; remark: string }>>([]);
const stationForm = ref({ stationId: 'liuchen', name: 'liuchen', enabled: true, remark: '' });
const queueForm = ref({ stationId: 'liuchen', queueId: 'liuchen', name: 'liuchen', labelType: 'ALL', widthMm: 40, heightMm: 30,
  enabled: true, defaultQueue: false });
const newStationToken = ref('');
const routeFeedback = ref('');
async function loadPrintQueues() {
  try {
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

function applySourceLabel(index: number) {
  const field = templateForm.value.fields[index];
  if (!field) return;
  const label = field.label.trim();
  if (field.source === 'customerInitials' && (label === '' || label === '客户')) field.label = '暗记';
  if (field.source === 'motherOrderCode' && (label === '' || label === '客户')) field.label = '母单';
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
  verificationCode: 'K7M2', customer: '示例客户', customerInitials: customerInitials('示例客户'), processingRequirement: '按要求加工',
  motherOrderCode: 'MD-20261014-0001',
  dueDate: '2026-10-20', code: 'MD-20261014-0001', goodsName: '戒托', materialCode: 'WL-001',
  quantity: '2 件', weight: '12 克', inboundDate: '2026-10-14', warehouseCode: 'A01',
  receiver: '收货人', express: '顺丰',
};
function workOrderDraftFields(): LabelField[] {
  const placed = templateForm.value.fields
    .filter((field) => field.label.trim() !== '')
    .map((field) => ({ name: field.label.trim(), value: draftFieldExamples[field.source] || '示例内容' }));
  const chosen = templateForm.value.fields.some((field) => field.source === 'motherOrderCode')
    || templateForm.value.footerLeftSource === 'motherOrderCode'
    || templateForm.value.footerRightSource === 'motherOrderCode';
  if (templateForm.value.labelType === 'WORK_ORDER' && !chosen) {
    placed.splice(Math.min(1, placed.length), 0, { name: '母单', value: 'MD-20261014-0001' });
  }
  return placed;
}

const templateDraft = computed<LabelPayload>(() => ({
  type: templateForm.value.labelType,
  typeLabel: labelTypeOptions.find((item) => item.value === templateForm.value.labelType)?.label ?? '标签',
  primaryCode: templateForm.value.labelType === 'WORK_ORDER' ? 'ZD-20261014-0001' : 'MD-20261014-0001',
  qrContent: templateForm.value.labelType === 'WORK_ORDER' ? 'JG|work_order|ZD-20261014-0001|0|PREVIEW' : 'JG|mother|MD-20261014-0001|0|PREVIEW',
  fields: workOrderDraftFields(),
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

onMounted(() => { void loadTemplates(); void loadPrintQueues(); });
</script>

<template>
  <div class="label-desk page-stack" :class="{ 'has-preview': preview }">
    <section class="label-intro">
      <h2>标签打印</h2>
      <p class="label-note">
        模板可设置字段、顺序、纸张尺寸与字号。二维码和主单号始终保留，详细信息可扫码查看。打印时请核对打印机纸张尺寸和缩放为 100%。
      </p>
    </section>

    <OrderLabelPanel :external-payload="preview" />
    <details class="advanced-label-tools">
      <summary>高级标签工具</summary>
      <section class="label-forms">
        <div class="form-card generic-label-tools">
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

    </details>

    <details v-if="canManageRoutes" class="info-disclosure label-route-settings">
      <summary>工位与打印队列设置</summary>
    <section class="template-manager" aria-label="工位与队列维护">
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

    </details>
    <details class="info-disclosure label-template-settings">
      <summary>标签模板设置</summary>
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
            <select v-model="field.source" :aria-label="`第${index + 1}个字段来源`" @change="applySourceLabel(index)">
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
          <p class="hint">从上到下即打印顺序。子单的所属母单号放在哪个字段或脚注，就印在哪个位置；模板没放这项时，仍默认排在第二行。</p>
        </div>

        <div class="editor-grid">
          <label class="field">
            <span>左脚注来源</span>
            <select v-model="templateForm.footerLeftSource" aria-label="左脚注来源">
              <option value="">无</option>
              <option v-for="option in motherFieldSourceOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>右脚注来源</span>
            <select v-model="templateForm.footerRightSource" aria-label="右脚注来源">
              <option value="">无</option>
              <option v-for="option in motherFieldSourceOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>
        <p class="hint">字段或脚注选择“客户首字母暗记”后，只印该单客户名称的拼音首字母。若模板里仍保留“客户”来源，中文名会继续印出。</p>

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
    </details>
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

  .label-layout { grid-template-columns: minmax(0, 1fr); }
  .field-row, .editor-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .field-row > .field-actions,
  .field-row > .link-button,
  .editor-grid > label:has(textarea) { grid-column: 1 / -1; }

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
