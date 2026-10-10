<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
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
import { parseScanCode } from '../scan/scanCode';
import { buildLabelMarkup, labelFaceCss, labelPreviewScale, openLabelPrint } from './labelPrint';

// 标签打印工作台。标签只印固定信息，进度靠扫码查看。
// 前期只做黑白打印：预览和出纸都是白底黑字，按标签纸毫米尺寸单独打印。

const preview = ref<LabelPayload | null>(null);
const templatesError = ref('');
const feedback = ref('');
const errorMessage = ref('');
const submitting = ref(false);
const stickerQuery = ref('');
const stickerHint = ref('');
const reprintReason = ref('');
const loadedSticker = ref<{ type: LabelType; code: string } | null>(null);

// 模板列表（连后端后加载）
const templates = ref<LabelTemplate[]>([]);
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
  width: `${(preview.value?.printerSetting.widthMm ?? 60) * labelPreviewScale}mm`,
  height: `${(preview.value?.printerSetting.heightMm ?? 40) * labelPreviewScale}mm`,
}));
const labelScaleStyle = computed(() => ({
  transform: `scale(${labelPreviewScale})`,
  transformOrigin: 'top left',
  width: `${(preview.value?.printerSetting.widthMm ?? 60)}mm`,
  height: `${(preview.value?.printerSetting.heightMm ?? 40)}mm`,
}));

// 当前类型可选模板
const templatesForType = computed(() =>
  templates.value.filter((template) => template.labelType === form.value.type && template.enabled),
);
const motherTemplates = computed(() =>
  templates.value.filter((template) => template.labelType === 'MOTHER' && template.enabled),
);

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

async function openFieldSticker() {
  preview.value = null;
  loadedSticker.value = null;
  feedback.value = '';
  errorMessage.value = '';
  stickerHint.value = '';
  const raw = stickerQuery.value.trim();
  if (!raw) {
    errorMessage.value = '请填写母单号、子单号或贴纸二维码';
    return;
  }
  let kind: 'MOTHER' | 'WORK_ORDER' | '' = '';
  let code = raw;
  if (raw.startsWith('JG|')) {
    try {
      const parsed = parseScanCode(raw);
      if (parsed.objectType === 'MOTHER_ORDER') kind = 'MOTHER';
      else if (parsed.objectType === 'WORK_ORDER') kind = 'WORK_ORDER';
      else {
        errorMessage.value = '这不是母单或子单贴纸';
        return;
      }
      code = parsed.code;
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '未识别的二维码内容';
      return;
    }
  }
  submitting.value = true;
  try {
    const payload = await loadStickerPayload(kind, code);
    showSticker(payload, payload.type === 'WORK_ORDER' ? 'WORK_ORDER' : 'MOTHER');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '贴纸生成失败';
  } finally {
    submitting.value = false;
  }
}

async function loadStickerPayload(kind: 'MOTHER' | 'WORK_ORDER' | '', code: string): Promise<LabelPayload> {
  if (kind === 'WORK_ORDER') return fetchWorkOrderLabel(code);
  if (kind === 'MOTHER') return fetchMotherLabel(code);
  try {
    return await fetchMotherLabel(code);
  } catch (error) {
    const message = error instanceof Error ? error.message : '';
    if (!message.includes('未找到')) throw error;
    return fetchWorkOrderLabel(code);
  }
}

function showSticker(payload: LabelPayload, kind: 'MOTHER' | 'WORK_ORDER') {
  preview.value = payload;
  loadedSticker.value = { type: payload.type, code: payload.primaryCode };
  const hasCode = payload.fields.some((field) => field.name === '验证码' && field.value.trim() !== '');
  stickerHint.value = hasCode
    ? (kind === 'WORK_ORDER' ? `已打开子单 ${payload.primaryCode}` : `已打开母单 ${payload.primaryCode}`)
    : '这张单还没有验证码。不拆的母单要先在来料页选择不拆。';
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
  widthMm: 60,
  heightMm: 40,
  dpi: 203,
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
    widthMm: 60,
    heightMm: 40,
    dpi: 203,
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
  <div class="label-desk page-stack">
    <section class="label-intro">
      <h2>标签打印</h2>
      <p class="label-note">
        现场贴纸印验证码、客户、加工要求、期限和单号。物料、数量、重量可以一起印。进度用手机扫二维码查看。
        前期使用<strong>黑白打印</strong>，按标签纸尺寸单独出纸，不使用彩色。
      </p>
    </section>

    <div class="label-layout">
      <section class="label-forms">
        <div class="form-card">
          <h3>现场贴纸</h3>
          <p class="hint">按母单号或子单号打开，字段从单据带出。也可以粘贴贴纸上的二维码内容。</p>
          <label class="field">
            <span>单号或二维码</span>
            <input v-model="stickerQuery" type="text" aria-label="现场贴纸单号" placeholder="母单号、子单号或 JG|mother|单号" />
          </label>
          <button type="button" class="primary-button" aria-label="打开现场贴纸" :disabled="submitting" @click="openFieldSticker">
            打开贴纸
          </button>
          <p v-if="stickerHint" class="hint">{{ stickerHint }}</p>
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
            <select v-model="form.type">
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

      <section v-if="preview" class="label-preview">
        <div class="preview-toolbar">
          <span>标签预览</span>
          <div class="toolbar-actions">
            <span class="printer-hint">
              {{ preview.printerSetting.widthMm }}×{{ preview.printerSetting.heightMm }}mm · 黑白 ·
              {{ preview.printerSetting.copies }} 份
              <template v-if="preview.printerSetting.printerName">· {{ preview.printerSetting.printerName }}</template>
            </span>
            <button type="button" class="primary-button" @click="printLabel">打印</button>
          </div>
        </div>

        <div class="label-print-area">
          <component :is="'style'">{{ labelFaceCss }}</component>
          <div class="label-mat">
            <div class="label-sheet" :style="sheetStyle">
              <div class="label-scale" :style="labelScaleStyle" v-html="labelMarkup"></div>
            </div>
          </div>
          <p class="qr-content-line">二维码内容：{{ preview.qrContent }}。打印时请关闭页眉页脚，并选择黑白标签机或激光机。</p>
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
      <table v-else class="template-table">
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
          <tr v-for="template in templates" :key="template.id">
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
  color: #46505c;
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
  background: #fff;
  border: 1px solid #e2e7ec;
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
  color: #46505c;
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
  border: 1px solid #ccd3da;
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
  color: #46505c;
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
  border: 1px solid #ccd3da;
  border-radius: 6px;
  font-size: 13px;
}
.hint {
  color: #6b7684;
  font-size: 12px;
  margin: 0 0 10px;
}
.feedback {
  font-size: 13px;
  padding: 8px 10px;
  border-radius: 6px;
  margin-top: 8px;
}
.feedback.ok {
  background: #e4f5ea;
  color: #1f8b4c;
}
.feedback.error {
  background: #fdeceb;
  color: #d0453b;
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
  color: #6b7684;
  font-weight: 400;
}
.label-mat {
  background: #e6e8eb;
  border: 1px solid #d5dbe2;
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
  color: #6b7684;
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
  border-bottom: 1px solid #eef1f5;
  padding: 8px 10px;
  text-align: left;
}
.template-table th {
  color: #6b7684;
  font-weight: 600;
}
.template-editor {
  margin-top: 16px;
  border-top: 1px solid #e2e7ec;
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

  .preview-toolbar,
  .manager-head,
  .editor-actions,
  .toolbar-actions {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .template-manager { overflow-x: auto; }
}
</style>
