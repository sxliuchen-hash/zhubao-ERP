<script setup lang="ts">
import { computed, inject, nextTick, onMounted, reactive, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { canManageMasterData } from '../../services/masterDataAccess';
import { fetchCustomers, fetchMaterials, fetchShortcuts, STORAGE_CONDITIONS, saveCustomer, saveMaterial,
  type CustomerProfile, type MaterialProfile, type RequirementShortcut } from '../../services/masterDataApi';
import FilterSelect from '../../components/FilterSelect.vue';
import RequirementComposer from './RequirementComposer.vue';
import PrepStockPanel from '../m1/PrepStockPanel.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import EvidencePreviewLink from '../evidence/EvidencePreviewLink.vue';
import { fetchEvidenceBoard, type EvidenceBoard } from '../../services/evidenceApi';
import { keptInputFailure } from '../field/keptInput';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import { useTraceTarget } from '../scan/useTraceTarget';
import type { TraceTarget } from '../scan/traceTarget';
import type { ScanContextResponse, ScanRelatedTarget } from '../../services/scanContextApi';
import {
  submitMobileIntake, fetchMotherOrder,
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
const openLabel = inject<((code: string) => void) | null>('zhubao-open-label', null);
const emit = defineEmits<{ saved: [result: MobileIntakeResult] }>();
const traceTarget = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
const visibleTabs = inject<Ref<string[] | null>>('zhubao-visible-tabs', ref(null));
const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canManageData = computed(() => canManageMasterData(authSession.value));
const canOpenLabel = computed(() => !visibleTabs.value || visibleTabs.value.includes('label'));
const canOpenInbound = computed(() => !visibleTabs.value || visibleTabs.value.includes('inbound'));

const scanText = ref('');
const scanHint = ref('');
const formError = ref('');
const customerName = ref('');
const customerId = ref<number | null>(null);
const customers = ref<CustomerProfile[]>([]);
const materials = ref<MaterialProfile[]>([]);
const materialCode = ref('');
const customerSearch = ref('');
const materialSearch = ref('');
const newCustomerName = ref('');
const newMaterialName = ref('');
const catalogError = ref('');
const catalogHint = ref('');
const catalogSaving = ref(false);
const catalogDialog = ref<'customer' | 'material' | ''>('');
const catalogMaterialTarget = ref<(typeof extraLines.value)[number] | null>(null);
const catalogDraftKey = ref('');
function openCatalog(kind: 'customer' | 'material', query: string, line: (typeof extraLines.value)[number] | null = null) {
  if (!canManageData.value || submitting.value) return;
  catalogMaterialTarget.value = line;
  catalogDraftKey.value = evidenceDraftKey.value;
  newCustomerName.value = kind === 'customer' ? query : '';
  newMaterialName.value = kind === 'material' ? query : '';
  catalogError.value = '';
  catalogDialog.value = kind;
}
function closeCatalog() { if (!catalogSaving.value) catalogDialog.value = ''; }
const processingRequirement = ref('');
const intakePurpose = ref('processing');
const prepCreated = ref(false);
const prepOpen = ref(false);
watch(intakePurpose, purpose => { if (purpose === 'prep') prepCreated.value = true; prepOpen.value = purpose === 'prep'; }, { flush: 'sync' });
function togglePrepDraft() { intakePurpose.value = prepOpen.value ? 'processing' : 'prep'; }
const requirementTemplateCode = ref('');
const requirementTemplateVersion = ref(0);
const requirementComposerKey = ref(0);
const shortcuts = ref<RequirementShortcut[]>([]);
const dueDate = ref('');
const goodsName = ref('');
const quantity = ref('1');
const quantityUnit = ref('件');
const weight = ref('');
const extraLines = ref<Array<{ materialCode: string; goodsName: string; quantity: string; quantityUnit: string; weight: string }>>([]);
const customerOptions = computed(() => customers.value
  .filter((item) => item.enabled || item.customerId === customerId.value)
  .map((item) => ({
    value: String(item.customerId),
    label: `${item.customerCode} · ${item.name}`,
    searchText: `${item.customerCode} ${item.name} ${item.aliases}`,
  })));
const materialOptions = computed(() => {
  const selected = new Set([materialCode.value, ...extraLines.value.map((line) => line.materialCode)].filter(Boolean));
  return materials.value
    .filter((item) => item.enabled || selected.has(item.materialCode))
    .map((item) => ({
      value: item.materialCode,
      label: `${item.name} · ${item.materialCode}${item.storageConditions?.length ? ' · ' + item.storageConditions.map(code => STORAGE_CONDITIONS.find(c => c.code === code)?.label || code).join('＋') : ' · 未配置保存条件'}`,
      searchText: `${item.materialCode} ${item.name} ${item.aliases}`,
    }));
});
const sourceType = ref<'COURIER' | 'CUSTOMER_DELIVERY' | 'INTERNAL_OTHER'>('COURIER');
const manualDraft = ref(false);
const manualCourier = ref('');
const submitting = ref(false);
const formStage = ref<1 | 2>(1);
const savedQuantityUnit = ref('');
const entryPanel = ref<HTMLElement | null>(null);
const draftPanel = ref<HTMLElement | null>(null);
const savedPanel = ref<HTMLElement | null>(null);
const created = ref<MobileIntakeResult | null>(null);
const evidenceDraftKey = ref(crypto.randomUUID());
const existingLedgerCode = ref('');
const evidenceIdentityError = ref('');
const requestId = ref('');
const requestFingerprint = ref('');
const intakeScan = reactive(useScanContext());
const relatedWaybill = ref('');
const relatedMotherCodes = ref<string[]>([]);
const relatedEvidence = ref<Array<{ motherCode: string; board: EvidenceBoard | null; error: string }>>([]);
const relatedEvidenceLoading = ref(false);
let relatedEvidenceRequest = 0;

const newExpressCode = computed(() => {
  const target = intakeScan.identity;
  if (!target || target.objectType !== 'EXPRESS_NO' || target.motherOrderCode) return '';
  const action = intakeScan.context?.actions.find((item) => item.code === 'INTAKE');
  if (action && !action.allowed) return '';
  return target.code ?? '';
});
const draftAvailable = computed(() => Boolean(newExpressCode.value) || manualDraft.value || sourceType.value !== 'COURIER');

const intakeMotherCode = computed(() => {
  if (created.value?.motherOrderCode) return created.value.motherOrderCode;
  const target = intakeScan.identity;
  if (!target) return '';
  if (target.objectType === 'MOTHER_ORDER') return target.code ?? '';
  if (target.objectType === 'EXPRESS_NO') return target.motherOrderCode ?? '';
  return '';
});

watch(intakeMotherCode, async code => {
  existingLedgerCode.value = '';
  evidenceIdentityError.value = '';
  if (!code || created.value) return;
  const draft = evidenceDraftKey.value;
  try {
    const mother = await fetchMotherOrder(code);
    if (draft === evidenceDraftKey.value && code === intakeMotherCode.value) existingLedgerCode.value = mother.ledgerCode;
  } catch (error) {
    if (draft === evidenceDraftKey.value) evidenceIdentityError.value = error instanceof Error ? error.message : '无法读取真实收货编号';
  }
});

const savedMother = computed(() => Boolean(created.value) || Boolean(intakeMotherCode.value && !newExpressCode.value));

const shownCustomer = computed(() => created.value?.customerName || intakeScan.context?.order.customerName || '');
const shownRequirement = computed(() => created.value?.processingRequirement || intakeScan.context?.order.requirement || '');
const shownDue = computed(() => created.value?.dueDate || intakeScan.context?.order.dueDate || '');

function showPanel(panel: Ref<HTMLElement | null>) {
  void nextTick(() => {
    if (globalThis.matchMedia?.('(max-width: 900px)').matches) {
      panel.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
    }
  });
}

function openLabelPage() {
  if (openLabel) openLabel(intakeMotherCode.value);
  else openTab?.('label');
}

function openMotherDetails() {
  traceTarget.value = {
    objectType: 'MOTHER_ORDER', code: intakeMotherCode.value, intent: 'INTAKE',
    token: (traceTarget.value?.token ?? 0) + 1,
  };
  openTab?.('inbound');
}

function startNextIntake() {
  evidenceDraftKey.value = crypto.randomUUID();
  formStage.value = 1;
  savedQuantityUnit.value = '';
  if (traceTarget.value?.intent === 'INTAKE') traceTarget.value = null;
  scanText.value = '';
  clearIntakeTarget();
  scanHint.value = '';
  customerName.value = '';
  customerId.value = null;
  materialCode.value = '';
  processingRequirement.value = '';
  requirementTemplateCode.value = '';
  requirementTemplateVersion.value = 0;
  requirementComposerKey.value += 1;
  dueDate.value = '';
  goodsName.value = '';
  quantity.value = '1';
  quantityUnit.value = '件';
  weight.value = '';
  extraLines.value = [];
  customerSearch.value = '';
  materialSearch.value = '';
  sourceType.value = 'COURIER';
  manualDraft.value = false;
  manualCourier.value = '';
  showPanel(entryPanel);
}

function shownText(value: string) {
  return value.trim() || '未记录';
}

function clearIntakeTarget() {
  const had = intakeScan.context !== null || intakeScan.loading || created.value !== null;
  if (had) evidenceDraftKey.value = crypto.randomUUID();
  existingLedgerCode.value = '';
  evidenceIdentityError.value = '';
  intakeScan.invalidate();
  created.value = null;
  formStage.value = 1;
  savedQuantityUnit.value = '';
  requestId.value = '';
  requestFingerprint.value = '';
  formError.value = '';
  relatedWaybill.value = '';
  relatedMotherCodes.value = [];
  relatedEvidence.value = [];
  relatedEvidenceLoading.value = false;
  relatedEvidenceRequest += 1;
  if (had) scanHint.value = '单号已变化，请重新识别';
}

watch(scanText, () => clearIntakeTarget(), { flush: 'sync' });

function applyIntake(response: ScanContextResponse | null) {
  if (!response) {
    if (intakeScan.error) formError.value = intakeScan.error;
    return;
  }
  if (response.requiresSelection) {
    const waybill = response.relatedTargets.find(item => item.objectType === 'EXPRESS_NO' && !item.motherOrderCode)?.code;
    if (waybill) {
      relatedEvidenceRequest += 1;
      relatedEvidenceLoading.value = false;
      relatedWaybill.value = waybill;
      relatedMotherCodes.value = [...new Set(response.relatedTargets
        .filter(item => item.objectType === 'MOTHER_ORDER' && item.code)
        .map(item => item.code!))];
      relatedEvidence.value = [];
    }
    scanHint.value = '请选择具体母单，或选择运单继续新建';
    return;
  }
  const target = canonicalTarget(response);
  if (!target) {
    scanHint.value = '没有定位到单据';
    return;
  }
  if (target.objectType === 'EXPRESS_NO' && !target.motherOrderCode) {
    sourceType.value = 'COURIER';
    scanHint.value = '已选运单，可新建一张母单';
    showPanel(draftPanel);
    return;
  }
  if (target.objectType === 'MOTHER_ORDER' || target.motherOrderCode) {
    scanHint.value = target.objectType === 'EXPRESS_NO'
      ? `快递单号已对应母单 ${target.motherOrderCode}`
      : `已识别母单 ${target.code}，可以补传拆包证据`;
    showPanel(savedPanel);
    return;
  }
  scanHint.value = '请扫描快递面单，或系统母单标签';
}

async function loadRelatedEvidence() {
  const waybill = relatedWaybill.value;
  const mothers = [...relatedMotherCodes.value];
  if (!waybill || !mothers.length || relatedEvidenceLoading.value) return;
  const revision = intakeScan.revision;
  const request = ++relatedEvidenceRequest;
  relatedEvidenceLoading.value = true;
  try {
    const settled = await Promise.allSettled(mothers.map(motherCode =>
      fetchEvidenceBoard({ targetType: 'MOTHER_ORDER', targetCode: motherCode })));
    if (request !== relatedEvidenceRequest || revision !== intakeScan.revision || waybill !== relatedWaybill.value) return;
    relatedEvidence.value = settled.map((result, index) => ({
      motherCode: mothers[index],
      board: result.status === 'fulfilled' ? result.value : null,
      error: result.status === 'rejected' ? '证据读取失败或无查看权限' : '',
    }));
  } finally { if (request === relatedEvidenceRequest) relatedEvidenceLoading.value = false; }
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
    sourceType.value,
    newExpressCode.value || manualCourier.value.trim(),
    customerName.value.trim(),
    customerId.value,
    materialCode.value,
    processingRequirement.value.trim(),
    requirementTemplateCode.value,
    requirementTemplateVersion.value,
    dueDate.value,
    goodsName.value.trim(),
    quantity.value,
    quantityUnit.value,
    weight.value,
    JSON.stringify(extraLines.value),
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

function nextFormStage() {
  formError.value = '';
  if (!customerId.value || !customerName.value.trim()) {
    formError.value = '请先选择客户';
    return;
  }
  const lines = [{ materialCode: materialCode.value, goodsName: goodsName.value, quantity: quantity.value, quantityUnit: quantityUnit.value, weight: weight.value }, ...extraLines.value];
  if (lines.some(line => !line.materialCode || !line.goodsName.trim() || !line.quantityUnit.trim()
    || !Number.isFinite(Number(line.quantity)) || Number(line.quantity) <= 0
    || !Number.isFinite(Number(line.weight)) || Number(line.weight) <= 0)) {
    formError.value = '请核对每条来料的材质、货品、数量、单位和重量';
    return;
  }
  if (new Set(lines.map(line => line.quantityUnit.trim())).size > 1) { formError.value = '同一加工母单的数量单位必须一致，请分开建单'; return; }
  formStage.value = 2;
  showPanel(draftPanel);
}

async function createMotherOrder() {
  if (intakePurpose.value !== 'processing') return;
  if (submitting.value) return;
  formError.value = '';
  const courier = sourceType.value === 'COURIER' ? (newExpressCode.value || manualCourier.value.trim()) : '';
  const seen = intakeScan.revision;
  if (intakeScan.loading) {
    formError.value = '请等待扫码识别完成';
    return;
  }
  if (!customerId.value || !customerName.value.trim() || !processingRequirement.value.trim() || !dueDate.value.trim()) {
    formError.value = '请选择客户并填写加工要求和完成期限';
    return;
  }
  if (processingRequirement.value.trim().length > 500) {
    formError.value = '加工要求超过 500 字，请缩短后再保存';
    return;
  }
  if (!materialCode.value || !goodsName.value.trim() || !quantityUnit.value.trim() || !Number.isFinite(Number(quantity.value)) || Number(quantity.value) <= 0 || !Number.isFinite(Number(weight.value)) || Number(weight.value) <= 0) {
    formError.value = '请选择材质并填写货品名称、数量和重量';
    return;
  }
  if (extraLines.value.some((line) => !line.materialCode || !line.goodsName.trim()
    || !Number.isFinite(Number(line.quantity)) || Number(line.quantity) <= 0
    || !Number.isFinite(Number(line.weight)) || Number(line.weight) <= 0
    || !line.quantityUnit.trim())) {
    formError.value = '每条材质明细都要选择材质并填写货品、数量、单位和重量';
    return;
  }
  const lines = [
    { materialCode: materialCode.value, goodsName: goodsName.value.trim(), quantity: Number(quantity.value),
      quantityUnit: quantityUnit.value.trim(), weight: weight.value },
    ...extraLines.value.map((line) => ({ materialCode: line.materialCode, goodsName: line.goodsName.trim(),
      quantity: Number(line.quantity), quantityUnit: line.quantityUnit.trim(), weight: line.weight })),
  ];
  const totalQuantity = lines.reduce((sum, line) => sum + Math.round(line.quantity * 1000), 0) / 1000;
  const totalWeight = lines.reduce((sum, line) => sum + Math.round(Number(line.weight) * 1000), 0) / 1000;
  const currentRequestId = ensureRequestId();
  submitting.value = true;
  try {
    const result = await submitMobileIntake({
      courierNo: courier,
      sourceType: sourceType.value,
      customerName: customerName.value.trim(),
      customerId: customerId.value,
      materialCode: materialCode.value,
      processingRequirement: processingRequirement.value.trim(),
      requirementTemplateCode: requirementTemplateCode.value,
      requirementTemplateVersion: requirementTemplateVersion.value,
      dueDate: dueDate.value,
      goodsName: goodsName.value.trim(),
      quantity: totalQuantity,
      weight: String(totalWeight),
      expressCompany: '',
      lines,
      requestId: currentRequestId,
    });
    if (intakeScan.revision !== seen) return;
    savedQuantityUnit.value = lines[0]?.quantityUnit ?? '';
    created.value = result;
    emit('saved', result);
    showPanel(savedPanel);
  } catch (error) {
    if (intakeScan.revision !== seen) return;
    if (intakeScan.revision === seen) formError.value = keptInputFailure(error, '确认建单并入库失败');
  } finally {
    submitting.value = false;
  }
}

async function loadCatalog() {
  try {
    const [customerRows, materialRows, shortcutRows] = await Promise.all([
      fetchCustomers(), fetchMaterials(), fetchShortcuts(),
    ]);
    customers.value = customerRows;
    materials.value = materialRows;
    shortcuts.value = shortcutRows;
    catalogError.value = '';
  } catch (error) {
    catalogError.value = error instanceof Error ? error.message : '客户与材质目录加载失败';
  }
}

function selectCustomer(value: string) {
  const id = Number(value);
  customerId.value = value && Number.isInteger(id) ? id : null;
  customerName.value = customers.value.find((item) => item.customerId === customerId.value)?.name ?? '';
}

function pendingCatalogName(dedicated: string, search: string) {
  return dedicated.trim() || search.trim();
}

async function addCustomer() {
  if (!canManageData.value || catalogSaving.value) return;
  const name = pendingCatalogName(newCustomerName.value, customerSearch.value);
  if (!name) {
    catalogHint.value = '';
    catalogError.value = '请填写要新增的客户名称';
    return;
  }
  catalogSaving.value = true;
  try {
    const saved = await saveCustomer({ customerId: 0, customerCode: '', name,
      aliases: '', contact: '', phone: '', remark: '', enabled: true });
    customers.value = [...customers.value.filter((item) => item.customerId !== saved.customerId), saved];
    if (catalogDraftKey.value !== evidenceDraftKey.value) return;
    customerId.value = saved.customerId;
    customerName.value = saved.name;
    catalogDialog.value = '';
    newCustomerName.value = '';
    customerSearch.value = '';
    catalogError.value = '';
    catalogHint.value = `已新增客户档案「${saved.name}」，并选入本单`;
  } catch (error) {
    catalogHint.value = '';
    catalogError.value = error instanceof Error ? error.message : '新增客户失败';
  } finally {
    catalogSaving.value = false;
  }
}

async function addMaterial() {
  if (!canManageData.value || catalogSaving.value) return;
  const name = pendingCatalogName(newMaterialName.value, materialSearch.value);
  if (!name) {
    catalogHint.value = '';
    catalogError.value = '请填写要新增的材质名称';
    return;
  }
  catalogSaving.value = true;
  try {
    const saved = await saveMaterial({ materialCode: '', name, aliases: '', remark: '', enabled: true });
    materials.value = [...materials.value.filter((item) => item.materialCode !== saved.materialCode), saved];
    if (catalogDraftKey.value !== evidenceDraftKey.value) return;
    const target = catalogMaterialTarget.value;
    if (target && !extraLines.value.includes(target)) { catalogError.value = '原明细已移除，请重新选择材质'; return; }
    if (target) target.materialCode = saved.materialCode;
    else materialCode.value = saved.materialCode;
    catalogHint.value = `已新增材质「${saved.name}」，并选入发起新增的明细`;
    catalogDialog.value = '';
    newMaterialName.value = '';
    materialSearch.value = '';
    catalogError.value = '';
  } catch (error) {
    catalogHint.value = '';
    catalogError.value = error instanceof Error ? error.message : '新增材质失败';
  } finally {
    catalogSaving.value = false;
  }
}

onMounted(loadCatalog);
</script>

<template>
  <div v-if="catalogDialog" class="intake-dialog-backdrop" @keydown.esc="closeCatalog">
    <section role="dialog" aria-modal="true" :aria-label="catalogDialog === 'customer' ? '新增客户档案' : '新增材质'" class="intake-catalog-dialog">
      <h2>{{ catalogDialog === 'customer' ? '新增客户档案' : '新增材质' }}</h2>
      <label v-if="catalogDialog === 'customer'" class="field"><span>客户名称</span><input v-model="newCustomerName" aria-label="新增客户名称" :disabled="catalogSaving" /></label>
      <label v-else class="field"><span>材质名称</span><input v-model="newMaterialName" aria-label="新增材质名称" :disabled="catalogSaving" /></label>
      <p v-if="catalogError" role="alert">{{ catalogError }}</p>
      <button type="button" class="primary-button" :disabled="catalogSaving" @click="catalogDialog === 'customer' ? addCustomer() : addMaterial()">保存并选回</button>
      <button type="button" class="secondary-button" :disabled="catalogSaving" @click="closeCatalog">取消</button>
    </section>
  </div>
  <section ref="entryPanel" class="section-block intake-create">
    <div class="section-heading">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ intro }}</p>
      </div>
    </div>
    <label class="field">
      <span>收货来源</span>
      <select v-model="sourceType" aria-label="收货来源" @change="clearIntakeTarget">
        <option value="COURIER">快递寄来</option>
        <option value="CUSTOMER_DELIVERY">客户送达</option>
        <option value="INTERNAL_OTHER">厂内接收／其他</option>
      </select>
    </label>
    <button v-if="sourceType === 'COURIER' && !draftAvailable" class="secondary-button" type="button" @click="manualDraft = true">无运单直接建单</button>
    <ScanInput
      v-model="scanText"
      field-text="快递单号或二维码内容"
      input-aria-label="快递单号"
      placeholder="手输运单号，或贴上扫到的内容"
      submit-label="识别单号"
      submit-aria-label="识别单号"
      photo-aria-label="拍面单"
      :bind-mobile-scan="field"
      :busy="intakeScan.loading || submitting"
      @draft="clearIntakeTarget"
      @recognize="recognizeIntake"
    />
    <p v-if="scanHint" class="operation-feedback">{{ scanHint }}</p>
    <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
    <p v-if="catalogError && !draftAvailable" class="operation-feedback danger">{{ catalogError }}</p>
    <OrderContextCard v-if="!newExpressCode && !savedMother" :context="intakeScan.context" @select="chooseIntakeTarget" />
    <section v-if="relatedWaybill && relatedMotherCodes.length" aria-label="运单关联证据">
      <h3>运单 {{ relatedWaybill }} 关联证据</h3>
      <p>关联 {{ relatedMotherCodes.length }} 张可见母单；证据按原母单分组，不改变原始归属。</p>
      <button type="button" class="secondary-button" :disabled="relatedEvidenceLoading" @click="loadRelatedEvidence">
        {{ relatedEvidenceLoading ? '正在读取' : '查看关联证据' }}
      </button>
      <article v-for="group in relatedEvidence" :key="group.motherCode">
        <h4>母单 {{ group.motherCode }}</h4>
        <p v-if="group.error">{{ group.error }}</p>
        <p v-else-if="!group.board?.links.length && !group.board?.historical.length">暂无证据</p>
        <ul v-else>
          <li v-for="item in group.board?.links || []" :key="item.linkCode">
            {{ item.fileName }} · {{ item.sceneLabel || item.scene }}
            <EvidencePreviewLink v-if="item.previewUrl" :attachment-code="item.attachmentCode" :media-type-hint="item.mediaType" />
          </li>
          <li v-for="item in group.board?.historical || []" :key="item.attachmentCode">
            {{ item.fileName }} · {{ item.label }}
            <EvidencePreviewLink v-if="item.previewUrl" :attachment-code="item.attachmentCode" :media-type-hint="item.mediaType" />
          </li>
        </ul>
      </article>
    </section>
  </section>

  <section v-if="draftAvailable && !created" ref="draftPanel" class="section-block intake-draft intake-create" aria-label="收货草稿">
    <div class="field-card-head">
      <h2>{{ intakePurpose === 'prep' ? '寄存备料' : '新建母单' }}</h2>
      <StatusBadge v-if="field" label="草稿" tone="gray" />
    </div>
    <p v-if="newExpressCode">运单号 {{ newExpressCode }}</p>
    <p v-if="intakePurpose === 'prep'">先寄存不建立加工母单，不产生加工费；下方单独登记备料。</p>
    <p v-else-if="field">草稿尚未建单，填好资料后点击“确认建单并入库”。</p>
    <p>照片或视频可稍后补传，不影响建单。</p>
    <label class="field"><span>收货用途</span><select v-model="intakePurpose" aria-label="收货用途"><option value="processing">现在加工，建立母单</option><option value="prep">先寄存备料</option></select></label>
    <div v-show="intakePurpose === 'processing'" class="intake-form" :inert="intakePurpose !== 'processing' || undefined">
      <nav class="intake-stage-nav" aria-label="收货填写步骤">
        <button type="button" class="secondary-button" :aria-current="formStage === 1 ? 'step' : undefined" :disabled="submitting" @click="formStage = 1">1 客户来料</button>
        <button type="button" class="secondary-button" :aria-current="formStage === 2 ? 'step' : undefined" :disabled="submitting" @click="nextFormStage">2 要求证据</button>
      </nav>
      <p v-if="catalogHint" class="operation-feedback">{{ catalogHint }}</p>
      <p v-if="catalogError" class="operation-feedback danger">{{ catalogError }}</p>
      <fieldset class="intake-stage intake-goods" :class="{ 'intake-stage-hidden': formStage !== 1 }" :disabled="submitting">
      <legend>客户与来料明细</legend>
      <label v-if="sourceType === 'COURIER' && !newExpressCode" class="field">
        <span>运单号（可不填）</span>
        <input v-model="manualCourier" type="text" aria-label="运单号（可不填）" />
      </label>
      <label class="field">
        <span>客户</span>
        <FilterSelect
          preserve-selection-on-query
          :model-value="customerId == null ? '' : String(customerId)"
          :options="customerOptions"
          field-label="客户"
          placeholder="输入名称、编号或别称筛选"
          empty-text="没有匹配的客户"
          :create-label="canManageData ? '新增客户档案' : ''"
          @create="openCatalog('customer', $event)"
          @update:model-value="selectCustomer"
          @update:query="customerSearch = $event"
        />
      </label>
      <label class="field">
        <span>材质</span>
        <FilterSelect
          preserve-selection-on-query
          v-model="materialCode"
          :options="materialOptions"
          field-label="材质"
          placeholder="输入名称、编码或别称筛选"
          empty-text="没有匹配的材质"
          :create-label="canManageData ? '新增材质' : ''"
          @create="openCatalog('material', $event)"
          @update:query="materialSearch = $event"
        />
      </label>
      <details v-if="materials.find(m => m.materialCode === materialCode)?.storageInstruction"><summary>查看保存说明</summary>{{ materials.find(m => m.materialCode === materialCode)?.storageInstruction }}</details>
      <label class="field">
        <span>货品名称</span>
        <input v-model="goodsName" type="text" aria-label="货品名称" />
      </label>
      <label class="field">
        <span>数量</span>
        <input v-model="quantity" type="number" min="0" step="0.001" aria-label="数量" />
      </label>
      <label class="field"><span>单位</span><input v-model="quantityUnit" aria-label="单位" /></label>
      <label class="field">
        <span>重量（克）</span>
        <input v-model="weight" type="number" min="0" step="0.001" aria-label="重量" />
      </label>
      <div v-for="(line, index) in extraLines" :key="index" class="intake-line">
        <strong>明细 {{ index + 2 }}</strong>
        <label class="field">
          <span>材质</span>
          <FilterSelect
          preserve-selection-on-query
            v-model="line.materialCode"
            :options="materialOptions"
            :field-label="`明细${index + 2}材质`"
            placeholder="输入名称、编码或别称筛选"
            empty-text="没有匹配的材质"
            :create-label="canManageData ? '新增材质' : ''"
            @create="openCatalog('material', $event, line)"
          />
        </label>
        <details v-if="materials.find(m => m.materialCode === line.materialCode)?.storageInstruction"><summary>查看本明细保存说明</summary>{{ materials.find(m => m.materialCode === line.materialCode)?.storageInstruction }}</details>
        <label class="field"><span>货品名称</span><input v-model="line.goodsName" :aria-label="`明细${index + 2}货品名称`" /></label>
        <label class="field"><span>数量</span><input v-model="line.quantity" type="number" min="0" step="0.001" :aria-label="`明细${index + 2}数量`" /></label>
        <label class="field"><span>单位</span><input v-model="line.quantityUnit" :aria-label="`明细${index + 2}单位`" /></label>
        <label class="field"><span>重量（克）</span><input v-model="line.weight" type="number" min="0" step="0.001" :aria-label="`明细${index + 2}重量`" /></label>
        <button type="button" class="secondary-button" @click="extraLines.splice(index, 1)">移除明细</button>
      </div>
      <button type="button" class="secondary-button" @click="extraLines.push({ materialCode: '', goodsName: '', quantity: '1', quantityUnit: '件', weight: '' })">增加材质明细</button>
      <button v-if="!field" type="button" class="primary-button intake-stage-next" @click="nextFormStage">下一步：要求与证据</button>
      </fieldset>
      <fieldset class="intake-stage intake-requirement" :class="{ 'intake-stage-hidden': formStage !== 2 }" :disabled="submitting">
      <legend>加工要求与交期</legend>
      <RequirementComposer
        :key="requirementComposerKey"
        v-model="processingRequirement"
        v-model:template-code="requirementTemplateCode"
        v-model:template-version="requirementTemplateVersion"
        :shortcuts="shortcuts"

      />
      <label class="field">
        <span>完成期限</span>
        <input v-model="dueDate" type="date" aria-label="完成期限" />
      </label>
      <div aria-label="建单核对摘要"><strong>{{ customerName || '未选客户' }}</strong><p v-for="(line, index) in [{ materialCode, goodsName, quantity, quantityUnit, weight }, ...extraLines]" :key="index">明细 {{ index + 1 }}：{{ materials.find(m => m.materialCode === line.materialCode)?.name || '未选材质' }} · {{ line.goodsName || '未填货品' }} · {{ line.quantity }} {{ line.quantityUnit }} ／ {{ line.weight || '未称量' }} 克</p></div>
      <p>核对最终要求后确认建单并入库；上传失败可单独重试，不重复建单。</p>
      <button class="secondary-button intake-stage-back" type="button" @click="formStage = 1">返回客户来料</button>
      <button v-if="!field" class="primary-button intake-submit" type="button" :disabled="submitting || intakeScan.loading" @click="createMotherOrder">
        {{ submitting ? '正在建单并入库' : '确认建单并入库' }}
      </button>
      </fieldset>
    </div>
  </section>

  <section v-if="prepCreated" class="section-block intake-prep-draft" aria-label="寄存备料草稿">
    <button class="secondary-button" type="button" :aria-expanded="prepOpen" @click="togglePrepDraft">{{ prepOpen ? '收起寄存草稿' : '返回寄存草稿' }}</button>
    <PrepStockPanel v-show="prepOpen" scope="prep" :initial-source="newExpressCode ? `运单 ${newExpressCode}` : ''" :inert="!prepOpen || undefined" />
  </section>

  <article v-if="savedMother" ref="savedPanel" class="field-card intake-saved intake-create" aria-label="已保存母单">
    <div class="field-card-head">
      <span>收货已保存，下一步打印标签</span>
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
      <template v-if="created">
        <dt>数量／重量</dt>
        <dd>{{ created.quantity }} {{ savedQuantityUnit || '（单位未提供）' }} / {{ created.weight }} 克</dd>
      </template>
    </dl>
    <div class="intake-next-actions">
      <button v-if="canOpenLabel" class="primary-button" type="button" aria-label="打开标签" @click="openLabelPage">预览并打印母单标签</button>
      <button v-if="canOpenInbound" class="secondary-button" type="button" @click="openMotherDetails">拆单与流转贴纸</button>
      <button class="secondary-button" type="button" @click="startNextIntake">继续收下一件</button>
    </div>
    <p>单号会自动带入标签页。<template v-if="created?.ledgerCode">收货已入库，无需再次确认入库；</template>生产前可在来料详情选择不拆或拆单。</p>
    <details v-if="intakeScan.context && !newExpressCode">
      <summary>查看母单详情与追溯</summary>
      <OrderContextCard :context="intakeScan.context" @select="chooseIntakeTarget" />
    </details>
  </article>

  <section v-show="(draftAvailable || intakeMotherCode) && intakePurpose === 'processing'" :inert="intakePurpose !== 'processing' || undefined" class="section-block intake-create" :class="{ 'intake-stage-hidden': !savedMother && formStage !== 2 }">
    <h2 v-if="field && !savedMother">草稿文件</h2>
    <h2 v-else-if="field && savedMother">已保存后的拆包证据</h2>
    <h2 v-else>拆包证据</h2>
    <p v-if="created">
      母单 {{ created.motherOrderCode }}
      <template v-if="created.qrContent"> / {{ created.qrContent }}</template>
    </p>
    <p v-if="created">业务已保存。照片或视频失败可以单独重试，不会再次建单或入库。</p>
    <p v-else-if="intakeMotherCode">读取原收货操作编号后自动关联补拍文件。</p>
    <p v-if="evidenceIdentityError" class="operation-feedback danger">{{ evidenceIdentityError }}</p>
    <p v-else>文件先留在当前表单。建单成功后上传并关联。</p>
    <EvidenceCapture
      target-type="MOTHER_ORDER"
      :target-code="intakeMotherCode"
      stage="INTAKE"
      scene="UNPACK"
      operation-type="inbound_confirm"
      :draft-key="evidenceDraftKey"
      :auto-upload="true"
      :operation-code="created?.ledgerCode || existingLedgerCode"
    />
  </section>

  <div v-if="intakePurpose === 'processing' && field && draftAvailable && !created" class="field-dock intake-create">
    <button v-if="formStage === 1" class="primary-button" type="button" :disabled="submitting || intakeScan.loading" @click="nextFormStage">下一步：要求与证据</button>
    <button v-else class="primary-button intake-submit" type="button" :disabled="submitting || intakeScan.loading" @click="createMotherOrder">
      {{ submitting ? '正在建单并入库' : '确认建单并入库' }}
    </button>
  </div>
</template>

<style scoped>
.intake-dialog-backdrop { position: fixed; inset: 0; z-index: 100; display: grid; place-items: center; padding: 16px; background: #0006; }
.intake-catalog-dialog { width: min(460px, 100%); max-height: 90vh; overflow: auto; padding: 24px; background: var(--content); border-radius: 12px; }
.intake-draft, .intake-saved { min-width: 0; scroll-margin-top: 20px; overflow-wrap: anywhere; }
.catalog-create { display: grid; gap: 8px; }
.catalog-create span { color: var(--muted); font-size: 12px; }
.requirement-shortcuts { display: grid; gap: 8px; }
.shortcut-list { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.shortcut-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--content);
}
.shortcut-phrase,
.shortcut-remove {
  min-height: 28px;
  border: 0;
  background: transparent;
  cursor: pointer;
}
.shortcut-phrase { padding: 2px 8px 2px 12px; }
.shortcut-remove {
  width: 28px;
  padding: 0 8px 0 0;
  color: var(--muted);
  font-size: 16px;
  line-height: 1;
}
.shortcut-list .empty-hint { flex: 1 1 100%; margin: 0; }
.intake-next-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.intake-next-actions button { min-height: 44px; }
.intake-stage-nav { display: none; }
.intake-stage { display: grid; gap: 12px; min-width: 0; margin: 0; padding: 16px; border: 1px solid var(--line); border-radius: 12px; }
.intake-stage legend { padding: 0 6px; font-weight: 700; color: var(--action); }
.intake-stage-next, .intake-stage-back { display: none; }
@media (min-width: 901px) {
  .intake-form { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: start; }
  .intake-form > .operation-feedback { grid-column: 1 / -1; }
  .intake-goods { grid-column: 1; }
  .intake-requirement { grid-column: 2; }
}

@media (max-width: 900px) {
  .intake-goods, .intake-line { grid-template-columns: 1fr 1fr; }
  .intake-line { display: grid; gap: 8px; }
  .intake-goods > :not(.field),
  .intake-line > :not(.field),
  .intake-goods > .field,
  .intake-line > .field { grid-column: 1 / -1; }
  .intake-goods > .field:has(input[type="number"]),
  .intake-goods > .field:has(input[aria-label="单位"]),
  .intake-line > .field:has(input[type="number"]),
  .intake-line > .field:has(input[aria-label$="单位"]) { grid-column: auto; }
  .intake-stage-hidden { display: none; }
  .intake-stage-nav { display: flex; gap: 10px; flex-wrap: wrap; }
  .intake-stage-nav button[aria-current="step"] { background: var(--action-soft); color: var(--action); }
  .intake-stage-next, .intake-stage-back { display: inline-flex; }
  .intake-stage { padding: 12px; }
  .intake-stage :deep(input), .intake-stage :deep(select), .intake-stage :deep(textarea) { font-size: 16px; min-height: 44px; }
  .intake-create button.secondary-button,
  .intake-create button.primary-button,
  .intake-create :deep(button.secondary-button),
  .intake-create :deep(button.primary-button),
  .intake-create :deep(button.table-action),
  .intake-create :deep(.scan-fallback-actions button),
  .intake-create :deep(.scan-guide-switch button),
  .intake-create .intake-next-actions button {
    width: auto;
    max-width: 100%;
    justify-self: start;
    min-height: 44px;
    padding: 4px 12px;
    font-size: 14px;
  }

  .intake-create :deep(.scan-entry .scan-fallback-actions),
  .intake-create :deep(.scan-entry .scan-guide-switch),
  .intake-create :deep(.evidence-capture .button-row) {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .intake-create :deep(button.scan-camera-button) {
    width: 100%;
    justify-self: stretch;
    min-height: 56px;
    padding: 12px 16px;
    font-size: 18px;
  }

  .intake-create button.intake-submit {
    min-height: 44px;
    padding: 6px 16px;
    font-size: 15px;
  }

  .intake-create.field-dock button.intake-submit {
    width: 100%;
    justify-self: stretch;
  }
}
</style>
