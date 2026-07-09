<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import QrImage from '../../components/QrImage.vue';
import {
  fetchLabelTemplates,
  fetchMotherLabel,
  generateLabel,
  labelTypeOptions,
  motherFieldSourceOptions,
  saveLabelTemplate,
  type LabelField,
  type LabelPayload,
  type LabelTemplate,
  type LabelTemplateField,
  type LabelType,
} from '../../services/labelApi';

// 标签打印工作台：把样张里的"贴周转盒/周转袋标签"变成系统真功能。
// 支持用户自定义标签模板（字段、抬头、脚注、打印机设置）。
// 设计原则与样张一致——标签只承载固定不变的信息，加工进度靠扫码实时查看。

const defaultPrinter = { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 };

// 本地评审样例（未连后端 API 时展示，保证离线也能看到效果）
const sampleLabel: LabelPayload = {
  type: 'MOTHER',
  typeLabel: '母单',
  primaryCode: 'CC-20260709-001-JL',
  qrContent: 'JG|mother|CC-20260709-001-JL',
  fields: [
    { name: '客户', value: '恒信珠宝' },
    { name: '物料', value: '18K 金戒托' },
    { name: '数量', value: '320 件' },
    { name: '重量', value: '35.20 克' },
    { name: '收货日', value: '2026-07-09' },
  ],
  footerLeft: '快递：顺丰 SF-IN-...001',
  footerRight: '收货人：何琴',
  printedAt: '（本地样例）',
  brandTitle: '珠宝来料加工系统',
  templateCode: '',
  printerSetting: { ...defaultPrinter },
};

const preview = ref<LabelPayload>(sampleLabel);
const feedback = ref('');
const errorMessage = ref('');
const submitting = ref(false);

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

const brandName = computed(() => preview.value.brandTitle || '珠宝来料加工系统');

const hasFields = computed(() => preview.value.fields.length > 0);

// 当前类型可选模板
const templatesForType = computed(() =>
  templates.value.filter((template) => template.labelType === form.value.type && template.enabled),
);
const motherTemplates = computed(() =>
  templates.value.filter((template) => template.labelType === 'MOTHER' && template.enabled),
);

// 打印区域尺寸（按打印机设置的毫米数）
const labelStyle = computed(() => ({
  width: `${preview.value.printerSetting.widthMm}mm`,
  minHeight: `${preview.value.printerSetting.heightMm}mm`,
}));

function addField() {
  form.value.fields.push({ name: '', value: '' });
}

function removeField(index: number) {
  form.value.fields.splice(index, 1);
}

async function loadTemplates() {
  try {
    templates.value = await fetchLabelTemplates();
    templatesLoaded.value = true;
  } catch {
    // 未连后端时静默，使用本地样例
    templatesLoaded.value = false;
  }
}

async function submitGenerate() {
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
  if (typeof window !== 'undefined') {
    window.print();
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
        把样张里的"贴周转盒 / 周转袋标签"变成系统真功能。标签<strong>只印固定不变的信息</strong>（编码、客户、物料、数量、重量、日期），
        加工进度这类会变的信息不印在标签上，工人<strong>手机扫二维码</strong>就能看最新工序，省纸省事。
        支持<strong>自定义标签模板与打印机设置</strong>。
      </p>
    </section>

    <div class="label-layout">
      <section class="label-forms">
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

      <section class="label-preview">
        <div class="preview-toolbar">
          <span>标签预览</span>
          <div class="toolbar-actions">
            <span class="printer-hint">
              {{ preview.printerSetting.widthMm }}×{{ preview.printerSetting.heightMm }}mm ·
              {{ preview.printerSetting.dpi }}dpi ·
              {{ preview.printerSetting.copies }} 份
              <template v-if="preview.printerSetting.printerName">· {{ preview.printerSetting.printerName }}</template>
            </span>
            <button type="button" class="primary-button" @click="printLabel">打印</button>
          </div>
        </div>

        <div class="label-print-area">
          <div class="label-card" :style="labelStyle">
            <div class="label-head">
              <span class="brand">{{ brandName }}</span>
              <span class="type">{{ preview.typeLabel }}</span>
            </div>
            <div class="label-body">
              <div class="qr-box">
                <QrImage :content="preview.qrContent" :size="120" />
                <div class="qr-hint">扫码看实时进度</div>
              </div>
              <div class="info">
                <div class="primary-code">{{ preview.primaryCode }}</div>
                <div class="primary-code-hint">编码（全链路唯一，追溯入口）</div>
                <dl v-if="hasFields" class="kv">
                  <template v-for="field in preview.fields" :key="field.name">
                    <dt>{{ field.name }}</dt>
                    <dd>{{ field.value }}</dd>
                  </template>
                </dl>
              </div>
            </div>
            <div class="label-foot">
              <span>{{ preview.footerLeft }}</span>
              <span>{{ preview.footerRight }}</span>
            </div>
          </div>
          <p class="qr-content-line">二维码内容：{{ preview.qrContent }}</p>
        </div>
      </section>
    </div>

    <!-- 模板与打印机设置管理 -->
    <section class="template-manager">
      <div class="manager-head">
        <h3>标签模板与打印机设置</h3>
        <button type="button" class="link-button" @click="newTemplate">+ 新建模板</button>
      </div>
      <p v-if="!templatesLoaded" class="hint">未连接后端 API，模板管理需连接后端；当前展示本地样例标签。</p>
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

        <h5>打印机设置</h5>
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
            <span>打印机名称（提示用）</span>
            <input v-model="templateForm.printerName" type="text" placeholder="如 ZDesigner ZD888" />
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
.link-button {
  background: none;
  border: none;
  color: #2f6fed;
  cursor: pointer;
  font-size: 13px;
}
.link-button.danger {
  color: #d0453b;
}
.primary-button {
  background: #1f2733;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 9px 16px;
  cursor: pointer;
  font-size: 14px;
}
.primary-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
.label-card {
  background: #fff;
  border: 2px solid #1f2733;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
}
.label-head {
  background: #1f2733;
  color: #fff;
  padding: 8px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.label-head .brand {
  font-size: 12px;
  opacity: 0.85;
}
.label-head .type {
  font-size: 12px;
  background: #f4b740;
  color: #1f2733;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: bold;
}
.label-body {
  display: flex;
  padding: 12px;
  gap: 12px;
}
.qr-box {
  width: 120px;
  flex-shrink: 0;
  text-align: center;
}
.qr-box .qr-hint {
  font-size: 11px;
  color: #6b7684;
  margin-top: 4px;
}
.info {
  flex: 1;
}
.primary-code {
  font-size: 20px;
  font-weight: bold;
  letter-spacing: 0.5px;
  color: #1f2733;
  word-break: break-all;
}
.primary-code-hint {
  font-size: 11px;
  color: #6b7684;
  margin-bottom: 8px;
}
.kv {
  display: grid;
  grid-template-columns: 64px 1fr;
  row-gap: 5px;
  font-size: 13px;
  margin: 0;
}
.kv dt {
  color: #6b7684;
}
.kv dd {
  color: #1f2733;
  font-weight: 600;
  margin: 0;
}
.label-foot {
  border-top: 1px dashed #c7ced6;
  padding: 8px 12px;
  font-size: 12px;
  color: #46505c;
  display: flex;
  justify-content: space-between;
  gap: 8px;
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

@media print {
  .label-forms,
  .preview-toolbar,
  .label-intro,
  .qr-content-line,
  .template-manager {
    display: none;
  }
  .label-layout {
    grid-template-columns: 1fr;
  }
  .label-card {
    box-shadow: none;
    page-break-inside: avoid;
  }
}
</style>
