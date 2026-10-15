<script setup lang="ts">
import { computed, inject, nextTick, onMounted, reactive, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { fetchCustomers, fetchMaterials, saveCustomer, saveMaterial,
  type CustomerProfile, type MaterialProfile } from '../../services/masterDataApi';
import StatusBadge from '../../components/StatusBadge.vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import FieldSteps from '../field/FieldSteps.vue';
import { keptInputFailure } from '../field/keptInput';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import { useTraceTarget } from '../scan/useTraceTarget';
import type { TraceTarget } from '../scan/traceTarget';
import type { ScanContextResponse, ScanRelatedTarget } from '../../services/scanContextApi';
import {
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
const openLabel = inject<((code: string) => void) | null>('zhubao-open-label', null);
const emit = defineEmits<{ saved: [result: MobileIntakeResult] }>();
const traceTarget = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
const visibleTabs = inject<Ref<string[] | null>>('zhubao-visible-tabs', ref(null));
const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canManageData = computed(() => ['ADMIN', 'GENERAL_MANAGER', 'WAREHOUSE_KEEPER', 'WORKSHOP_LEADER'].includes(authSession.value?.role ?? ''));
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
const visibleCustomers = computed(() => customers.value.filter((item) => item.enabled &&
  `${item.customerCode} ${item.name} ${item.aliases}`.toLowerCase().includes(customerSearch.value.trim().toLowerCase())));
const visibleMaterials = computed(() => materials.value.filter((item) => item.enabled &&
  `${item.materialCode} ${item.name} ${item.aliases}`.toLowerCase().includes(materialSearch.value.trim().toLowerCase())));
const processingRequirement = ref('');
const dueDate = ref('');
const goodsName = ref('');
const quantity = ref('1');
const quantityUnit = ref('件');
const weight = ref('');
const extraLines = ref<Array<{ materialCode: string; goodsName: string; quantity: string; quantityUnit: string; weight: string }>>([]);
const expressCompany = ref('');
const sourceType = ref<'COURIER' | 'CUSTOMER_DELIVERY' | 'INTERNAL_OTHER'>('COURIER');
const manualDraft = ref(false);
const manualCourier = ref('');
const submitting = ref(false);
const entryPanel = ref<HTMLElement | null>(null);
const draftPanel = ref<HTMLElement | null>(null);
const savedPanel = ref<HTMLElement | null>(null);
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
const draftAvailable = computed(() => Boolean(newExpressCode.value) || manualDraft.value || sourceType.value !== 'COURIER');

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
  if (draftAvailable.value) return 1;
  return 0;
});

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
  if (traceTarget.value?.intent === 'INTAKE') traceTarget.value = null;
  scanText.value = '';
  clearIntakeTarget();
  scanHint.value = '';
  customerName.value = '';
  customerId.value = null;
  materialCode.value = '';
  processingRequirement.value = '';
  dueDate.value = '';
  goodsName.value = '';
  quantity.value = '1';
  quantityUnit.value = '件';
  weight.value = '';
  extraLines.value = [];
  expressCompany.value = '';
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
    dueDate.value,
    goodsName.value.trim(),
    quantity.value,
    quantityUnit.value,
    weight.value,
    JSON.stringify(extraLines.value),
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

async function createMotherOrder() {
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
      dueDate: dueDate.value,
      goodsName: goodsName.value.trim(),
      quantity: totalQuantity,
      weight: String(totalWeight),
      expressCompany: expressCompany.value.trim(),
      lines,
      requestId: currentRequestId,
    });
    if (intakeScan.revision !== seen) return;
    created.value = result;
    emit('saved', result);
    showPanel(savedPanel);
  } catch (error) {
    if (intakeScan.revision !== seen) return;
    if (intakeScan.revision === seen) formError.value = keptInputFailure(error, '生成母单失败');
  } finally {
    submitting.value = false;
  }
}

async function loadCatalog() {
  try {
    const [customerRows, materialRows] = await Promise.all([fetchCustomers(), fetchMaterials()]);
    customers.value = customerRows;
    materials.value = materialRows;
    catalogError.value = '';
  } catch (error) {
    catalogError.value = error instanceof Error ? error.message : '客户与材质目录加载失败';
  }
}

function chooseCustomer() {
  customerName.value = customers.value.find((item) => item.customerId === Number(customerId.value))?.name ?? '';
}

async function addCustomer() {
  if (!newCustomerName.value.trim()) return;
  try {
    const saved = await saveCustomer({ customerId: 0, customerCode: '', name: newCustomerName.value.trim(),
      aliases: '', contact: '', phone: '', remark: '', enabled: true });
    customers.value = [...customers.value, saved];
    customerId.value = saved.customerId;
    customerName.value = saved.name;
    newCustomerName.value = '';
    catalogError.value = '';
  } catch (error) { catalogError.value = error instanceof Error ? error.message : '新增客户失败'; }
}

async function addMaterial() {
  if (!newMaterialName.value.trim()) return;
  try {
    const saved = await saveMaterial({ materialCode: '', name: newMaterialName.value.trim(),
      aliases: '', remark: '', enabled: true });
    materials.value = [...materials.value, saved];
    materialCode.value = saved.materialCode;
    newMaterialName.value = '';
    catalogError.value = '';
  } catch (error) { catalogError.value = error instanceof Error ? error.message : '新增材质失败'; }
}

onMounted(loadCatalog);
</script>

<template>
  <section ref="entryPanel" class="section-block">
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
    <FieldSteps v-if="field" :steps="['扫码识别', '填写要求', '保存证据']" :current="intakeStep" />
    <ScanInput
      v-model="scanText"
      field-text="快递单号或二维码内容"
      input-aria-label="快递单号"
      placeholder="手输运单号，或贴上扫到的内容"
      submit-label="识别单号"
      submit-aria-label="识别单号"
      photo-aria-label="拍面单"
      :busy="intakeScan.loading || submitting"
      @draft="clearIntakeTarget"
      @recognize="recognizeIntake"
    />
    <p v-if="scanHint" class="operation-feedback">{{ scanHint }}</p>
    <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
    <p v-if="catalogError" class="operation-feedback danger">{{ catalogError }}</p>
    <OrderContextCard v-if="!newExpressCode && !savedMother" :context="intakeScan.context" @select="chooseIntakeTarget" />
  </section>

  <section v-if="draftAvailable && !created" ref="draftPanel" class="section-block intake-draft" aria-label="收货草稿">
    <div class="field-card-head">
      <h2>新建母单</h2>
      <StatusBadge v-if="field" label="草稿" tone="gray" />
    </div>
    <p v-if="newExpressCode">运单号 {{ newExpressCode }}</p>
    <p v-if="field">草稿尚未建单，填好资料后点击“生成母单”。</p>
    <p>照片或视频可稍后补传，不影响建单。</p>
    <div class="intake-form">
      <label v-if="sourceType === 'COURIER' && !newExpressCode" class="field">
        <span>运单号（可不填）</span>
        <input v-model="manualCourier" type="text" aria-label="运单号（可不填）" />
      </label>
      <label class="field">
        <span>搜索客户</span>
        <input v-model="customerSearch" type="search" aria-label="搜索客户" />
      </label>
      <label class="field">
        <span>客户</span>
        <select v-model.number="customerId" aria-label="客户" @change="chooseCustomer">
          <option :value="null">请选择客户</option>
          <option v-for="item in visibleCustomers" :key="item.customerId" :value="item.customerId">{{ item.customerCode }} · {{ item.name }}</option>
        </select>
      </label>
      <div v-if="canManageData" class="field">
        <span>新增客户档案</span>
        <input v-model="newCustomerName" aria-label="新增客户名称" />
        <button type="button" class="secondary-button" @click="addCustomer">新增客户</button>
      </div>
      <label class="field">
        <span>加工要求</span>
        <textarea v-model="processingRequirement" aria-label="加工要求" />
      </label>
      <label class="field">
        <span>完成期限</span>
        <input v-model="dueDate" type="date" aria-label="完成期限" />
      </label>
      <label v-if="sourceType === 'COURIER'" class="field">
        <span>快递公司</span>
        <input v-model="expressCompany" type="text" aria-label="快递公司" />
      </label>
      <label class="field">
        <span>搜索材质</span>
        <input v-model="materialSearch" type="search" aria-label="搜索材质" />
      </label>
      <label class="field">
        <span>材质</span>
        <select v-model="materialCode" aria-label="材质">
          <option value="">请选择材质</option>
          <option v-for="item in visibleMaterials" :key="item.materialCode" :value="item.materialCode">{{ item.name }} · {{ item.materialCode }}</option>
        </select>
      </label>
      <div v-if="canManageData" class="field">
        <span>新增自定义材质</span>
        <input v-model="newMaterialName" aria-label="新增材质名称" />
        <button type="button" class="secondary-button" @click="addMaterial">新增材质</button>
      </div>
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
        <label class="field"><span>材质</span><select v-model="line.materialCode" :aria-label="`明细${index + 2}材质`">
          <option value="">请选择材质</option>
          <option v-for="item in visibleMaterials" :key="item.materialCode" :value="item.materialCode">{{ item.name }} · {{ item.materialCode }}</option>
        </select></label>
        <label class="field"><span>货品名称</span><input v-model="line.goodsName" :aria-label="`明细${index + 2}货品名称`" /></label>
        <label class="field"><span>数量</span><input v-model="line.quantity" type="number" min="0" step="0.001" :aria-label="`明细${index + 2}数量`" /></label>
        <label class="field"><span>单位</span><input v-model="line.quantityUnit" :aria-label="`明细${index + 2}单位`" /></label>
        <label class="field"><span>重量（克）</span><input v-model="line.weight" type="number" min="0" step="0.001" :aria-label="`明细${index + 2}重量`" /></label>
        <button type="button" class="secondary-button" @click="extraLines.splice(index, 1)">移除明细</button>
      </div>
      <button type="button" class="secondary-button" @click="extraLines.push({ materialCode: '', goodsName: '', quantity: '1', quantityUnit: '件', weight: '' })">增加材质明细</button>
      <button v-if="!field" class="primary-button" type="button" :disabled="submitting || intakeScan.loading" @click="createMotherOrder">
        {{ submitting ? '正在生成' : '生成母单' }}
      </button>
    </div>
  </section>

  <article v-if="savedMother" ref="savedPanel" class="field-card intake-saved" aria-label="已保存母单">
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
        <dd>{{ created.quantity }} 件 / {{ created.weight }} 克</dd>
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

  <section v-if="draftAvailable || intakeMotherCode" class="section-block">
    <h2 v-if="field && !savedMother">草稿文件</h2>
    <h2 v-else-if="field && savedMother">已保存后的拆包证据</h2>
    <h2 v-else>拆包证据</h2>
    <p v-if="created">
      母单 {{ created.motherOrderCode }}
      <template v-if="created.qrContent"> / {{ created.qrContent }}</template>
    </p>
    <p v-if="created">业务已保存。照片或视频失败可以单独重试，不会再次建单或入库。</p>
    <p v-else-if="intakeMotherCode">请先完成入库确认，取得操作编号后再上传并关联。</p>
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

  <div v-if="field && draftAvailable && !created" class="field-dock">
    <button class="primary-button" type="button" :disabled="submitting || intakeScan.loading" @click="createMotherOrder">
      {{ submitting ? '正在生成' : '生成母单' }}
    </button>
  </div>
</template>

<style scoped>
.intake-draft, .intake-saved { min-width: 0; scroll-margin-top: 20px; overflow-wrap: anywhere; }
.intake-next-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.intake-next-actions button { min-height: 44px; }
</style>
