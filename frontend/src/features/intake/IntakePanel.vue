<script setup lang="ts">
import { computed, inject, reactive, ref, watch } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import FieldSteps from '../field/FieldSteps.vue';
import { keptInputFailure } from '../field/keptInput';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import { useTraceTarget } from '../scan/useTraceTarget';
import type { ScanContextResponse, ScanRelatedTarget } from '../../services/scanContextApi';
import {
  fetchMotherOrder,
  lookupMobileIntake,
  submitMobileIntake,
  type MobileIntakeResult,
} from '../../services/mobileIntakeApi';

withDefaults(defineProps<{
  title: string;
  intro: string;
  field?: boolean;
}>(), {
  field: false,
});

const openTab = inject<((tab: string) => void) | null>('zhubao-open-tab', null);

const scanText = ref('');
const scanHint = ref('');
const formError = ref('');
const customerName = ref('');
const processingRequirement = ref('');
const dueDate = ref('');
const goodsName = ref('');
const quantity = ref('1');
const weight = ref('');
const expressCompany = ref('顺丰');
const submitting = ref(false);
const created = ref<MobileIntakeResult | null>(null);
const requestId = ref('');
const requestFingerprint = ref('');
const intakeScan = reactive(useScanContext());

const newExpressCode = computed(() => {
  const target = intakeScan.identity;
  if (!target || target.objectType !== 'EXPRESS_NO' || target.motherOrderCode) return '';
  const action = intakeScan.context?.actions.find((item) => item.code === 'INTAKE');
  if (action && !action.allowed) return '';
  return target.code ?? '';
});

const intakeMotherCode = computed(() => {
  if (created.value?.motherOrderCode) return created.value.motherOrderCode;
  const target = intakeScan.identity;
  if (!target) return '';
  if (target.objectType === 'MOTHER_ORDER') return target.code ?? '';
  if (target.objectType === 'EXPRESS_NO') return target.motherOrderCode ?? '';
  return '';
});

const savedMother = computed(() => Boolean(created.value) || Boolean(intakeMotherCode.value && !newExpressCode.value));

const intakeStep = computed(() => {
  if (savedMother.value) return 2;
  if (newExpressCode.value) return 1;
  return 0;
});

const shownCustomer = computed(() => created.value?.customerName || intakeScan.context?.order.customerName || '');
const shownRequirement = computed(() => created.value?.processingRequirement || intakeScan.context?.order.requirement || '');
const shownDue = computed(() => created.value?.dueDate || intakeScan.context?.order.dueDate || '');

function openLabelPage() {
  openTab?.('label');
}

function shownText(value: string) {
  return value.trim() || '未记录';
}

function clearIntakeTarget() {
  const had = intakeScan.context !== null || intakeScan.loading || created.value !== null;
  intakeScan.invalidate();
  created.value = null;
  requestId.value = '';
  requestFingerprint.value = '';
  formError.value = '';
  if (had) scanHint.value = '单号已变化，请重新识别';
}

watch(scanText, () => clearIntakeTarget(), { flush: 'sync' });

function applyIntake(response: ScanContextResponse | null) {
  if (!response) {
    if (intakeScan.error) formError.value = intakeScan.error;
    return;
  }
  if (response.requiresSelection) {
    scanHint.value = '请选择要打开的单据';
    return;
  }
  const target = canonicalTarget(response);
  if (!target) {
    scanHint.value = '没有定位到单据';
    return;
  }
  if (target.objectType === 'EXPRESS_NO' && !target.motherOrderCode) {
    scanHint.value = '这是新的快递，请填写客户、加工要求和期限';
    return;
  }
  if (target.objectType === 'MOTHER_ORDER' || target.motherOrderCode) {
    scanHint.value = target.objectType === 'EXPRESS_NO'
      ? `快递单号已对应母单 ${target.motherOrderCode}`
      : `已识别母单 ${target.code}，可以补传拆包证据`;
    return;
  }
  scanHint.value = '请扫描快递面单，或系统母单标签';
}

async function recognizeIntake(draft: ScanDraft) {
  formError.value = '';
  scanHint.value = '';
  const content = draft.content.trim();
  if (!content) {
    intakeScan.invalidate();
    scanHint.value = '请先扫快递单号或手输';
    return;
  }
  const response = await intakeScan.lookup({
    content,
    objectType: draft.objectType,
    intent: 'INTAKE',
  });
  applyIntake(response);
}

useTraceTarget(['INTAKE'], (target) => {
  scanText.value = target.code;
  void recognizeIntake({
    content: target.code,
    ...(target.objectType ? { objectType: target.objectType } : {}),
  });
});

async function chooseIntakeTarget(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
  formError.value = '';
  scanHint.value = '';
  const response = await intakeScan.lookup({
    content: related.code,
    objectType: related.objectType,
    intent: 'INTAKE',
  });
  applyIntake(response);
}

function confirmationFingerprint() {
  return [
    newExpressCode.value,
    customerName.value.trim(),
    processingRequirement.value.trim(),
    dueDate.value,
    goodsName.value.trim(),
    quantity.value,
    weight.value,
    expressCompany.value.trim(),
  ].join('|');
}

function ensureRequestId() {
  const fingerprint = confirmationFingerprint();
  if (!requestId.value || requestFingerprint.value !== fingerprint) {
    const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    requestId.value = `intake-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
    requestFingerprint.value = fingerprint;
  }
  return requestId.value;
}

async function openExistingWaybill(courier: string) {
  const existing = await lookupMobileIntake(courier);
  if (!existing.found || !existing.motherOrderCode) return false;
  created.value = await fetchMotherOrder(existing.motherOrderCode);
  formError.value = '已按运单打开原单，没有再次建单';
  return true;
}

async function createMotherOrder() {
  formError.value = '';
  const courier = newExpressCode.value;
  const seen = intakeScan.revision;
  if (!courier || intakeScan.loading) {
    formError.value = '请先识别快递单号';
    return;
  }
  if (!customerName.value.trim() || !processingRequirement.value.trim() || !dueDate.value.trim()) {
    formError.value = '客户、加工要求和完成期限不能为空';
    return;
  }
  if (!goodsName.value.trim() || Number(quantity.value) <= 0 || Number(weight.value) <= 0) {
    formError.value = '请填写货品名称、数量和重量';
    return;
  }
  const currentRequestId = ensureRequestId();
  submitting.value = true;
  try {
    const result = await submitMobileIntake({
      courierNo: courier,
      customerName: customerName.value.trim(),
      processingRequirement: processingRequirement.value.trim(),
      dueDate: dueDate.value,
      goodsName: goodsName.value.trim(),
      quantity: Number(quantity.value),
      weight: weight.value,
      expressCompany: expressCompany.value.trim(),
      requestId: currentRequestId,
    });
    if (intakeScan.revision !== seen) return;
    created.value = result;
  } catch (error) {
    if (intakeScan.revision !== seen) return;
    try {
      if (await openExistingWaybill(courier)) return;
    } catch {
      // 运单核对失败时保留原来的提交错误，方便原请求重试。
    }
    formError.value = keptInputFailure(error, '生成母单失败');
  } finally {
    if (intakeScan.revision === seen) submitting.value = false;
  }
}
</script>

<template>
  <section class="section-block">
    <div class="section-heading">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ intro }}</p>
      </div>
    </div>
    <FieldSteps v-if="field" :steps="['扫码识别', '填写要求', '保存证据']" :current="intakeStep" />
    <ScanInput
      v-model="scanText"
      field-text="快递单号或二维码内容"
      input-aria-label="快递单号"
      placeholder="手输运单号，或贴上扫到的内容"
      submit-label="识别单号"
      submit-aria-label="识别单号"
      photo-aria-label="拍面单"
      :busy="intakeScan.loading"
      @draft="clearIntakeTarget"
      @recognize="recognizeIntake"
    />
    <p v-if="scanHint" class="operation-feedback">{{ scanHint }}</p>
    <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
    <OrderContextCard :context="intakeScan.context" @select="chooseIntakeTarget" />
  </section>

  <section v-if="newExpressCode && !created" class="section-block" aria-label="收货草稿">
    <div class="field-card-head">
      <h2>新建母单</h2>
      <StatusBadge v-if="field" label="草稿" tone="gray" />
    </div>
    <p>快递单号 {{ newExpressCode }}</p>
    <p v-if="field">草稿尚未建单。网络中断、上传失败或登录过期都会保留已填内容。</p>
    <p>可以先选择照片或视频。建单成功后再上传，失败只补文件，不会再次建单或入库。</p>
    <div class="intake-form">
      <label class="field">
        <span>客户</span>
        <input v-model="customerName" type="text" aria-label="客户" />
      </label>
      <label class="field">
        <span>加工要求</span>
        <textarea v-model="processingRequirement" aria-label="加工要求" />
      </label>
      <label class="field">
        <span>完成期限</span>
        <input v-model="dueDate" type="date" aria-label="完成期限" />
      </label>
      <label class="field">
        <span>快递公司</span>
        <input v-model="expressCompany" type="text" aria-label="快递公司" />
      </label>
      <label class="field">
        <span>货品名称</span>
        <input v-model="goodsName" type="text" aria-label="货品名称" />
      </label>
      <label class="field">
        <span>数量</span>
        <input v-model="quantity" type="number" min="1" step="1" aria-label="数量" />
      </label>
      <label class="field">
        <span>重量（克）</span>
        <input v-model="weight" type="number" min="0" step="0.001" aria-label="重量" />
      </label>
      <button v-if="!field" class="primary-button" type="button" :disabled="submitting || intakeScan.loading" @click="createMotherOrder">
        {{ submitting ? '正在生成' : '生成母单' }}
      </button>
    </div>
  </section>

  <article v-if="field && savedMother" class="field-card" aria-label="已保存母单">
    <div class="field-card-head">
      <span>已保存</span>
      <StatusBadge label="已保存" tone="green" />
    </div>
    <strong>{{ intakeMotherCode }}</strong>
    <dl class="detail-grid">
      <dt>客户</dt>
      <dd>{{ shownText(shownCustomer) }}</dd>
      <dt>要求</dt>
      <dd>{{ shownText(shownRequirement) }}</dd>
      <dt>期限</dt>
      <dd>{{ shownText(shownDue) }}</dd>
    </dl>
    <button class="secondary-button" type="button" aria-label="打开标签" @click="openLabelPage">打开标签</button>
  </article>

  <section v-if="newExpressCode || intakeMotherCode" class="section-block">
    <h2 v-if="field && !created">草稿文件</h2>
    <h2 v-else-if="field && created">已保存后的拆包证据</h2>
    <h2 v-else>拆包证据</h2>
    <p v-if="created">
      母单 {{ created.motherOrderCode }}
      <template v-if="created.qrContent"> / {{ created.qrContent }}</template>
    </p>
    <p v-if="created">业务已保存。照片或视频失败可以单独重试，不会再次建单或入库。</p>
    <p v-else>文件先留在当前表单。建单成功后上传并关联。</p>
    <EvidenceCapture
      target-type="MOTHER_ORDER"
      :target-code="intakeMotherCode"
      stage="INTAKE"
      scene="UNPACK"
      operation-type="inbound_confirm"
      :operation-code="created?.ledgerCode || ''"
    />
  </section>

  <div v-if="field && newExpressCode && !created" class="field-dock">
    <button class="primary-button" type="button" :disabled="submitting || intakeScan.loading" @click="createMotherOrder">
      {{ submitting ? '正在生成' : '生成母单' }}
    </button>
  </div>
</template>
