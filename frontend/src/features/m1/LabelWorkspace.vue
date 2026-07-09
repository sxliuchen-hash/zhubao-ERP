<script setup lang="ts">
import { computed, ref } from 'vue';
import QrImage from '../../components/QrImage.vue';
import {
  fetchMotherLabel,
  generateLabel,
  labelTypeOptions,
  type LabelField,
  type LabelPayload,
  type LabelType,
} from '../../services/labelApi';

// 标签打印工作台：把样张里的"贴周转盒/周转袋标签"变成系统真功能。
// 设计原则与样张一致——标签只承载固定不变的信息，加工进度靠扫码实时查看。

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
};

const preview = ref<LabelPayload>(sampleLabel);
const feedback = ref('');
const errorMessage = ref('');
const submitting = ref(false);

// 通用生成表单
const form = ref<{
  type: LabelType;
  primaryCode: string;
  fields: LabelField[];
  footerLeft: string;
  footerRight: string;
}>({
  type: 'MOTHER',
  primaryCode: '',
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

const brandName = 'XX 珠宝来料加工系统';

const hasFields = computed(() => preview.value.fields.length > 0);

function addField() {
  form.value.fields.push({ name: '', value: '' });
}

function removeField(index: number) {
  form.value.fields.splice(index, 1);
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
    const payload = await fetchMotherLabel(motherOrderCode.value.trim());
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
</script>

<template>
  <div class="label-desk page-stack">
    <section class="label-intro">
      <h2>标签打印</h2>
      <p class="label-note">
        把样张里的"贴周转盒 / 周转袋标签"变成系统真功能。标签<strong>只印固定不变的信息</strong>（编码、客户、物料、数量、重量、日期），
        加工进度这类会变的信息不印在标签上，工人<strong>手机扫二维码</strong>就能看最新工序，省纸省事。
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
          <button type="button" class="primary-button" @click="printLabel">打印</button>
        </div>

        <div class="label-print-area">
          <div class="label-card">
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
.form-card {
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
.field input,
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
.field-row input {
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
.label-card {
  width: 360px;
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

@media print {
  .label-forms,
  .preview-toolbar,
  .label-intro,
  .qr-content-line {
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
