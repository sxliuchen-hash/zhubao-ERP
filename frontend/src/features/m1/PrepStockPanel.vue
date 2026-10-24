<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { storageText, type StorageRequirements, fetchCustomers, fetchMaterials, type CustomerProfile, type MaterialProfile } from '../../services/masterDataApi';
import { ApiRequestError, apiGet, apiPost } from '../../services/apiClient';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';
import RequirementComposer from '../intake/RequirementComposer.vue';

const props = withDefaults(defineProps<{ scope?: 'company' | 'prep'; initialSource?: string }>(), { scope: 'company', initialSource: '' });

interface UnitTotal { unit: string; quantity: string }
interface Overview {
  scope: string;
  batchCount: number;
  quantities: UnitTotal[];
  knownWeight: string;
  frozenCount: number;
  pendingCount: number;
}
interface BatchCard {
  scope: string;
  ownerLabel: string;
  customerId?: number | null;
  materialCode: string;
  batchCode: string;
  warehouseCode: string;
  status: string;
  quantityUnit: string;
  bookQuantity: string;
  bookWeight: string;
  frozenQuantity: string;
  availableQuantity: string;
  availableWeight: string;
  issuedQuantity: string;
  weightText: string;
  pending: boolean;
  sourceMissing: boolean;
  documentCode: string;
  externalRef: string;
  updatedAt: string;
  lineNo?: number;
}
interface ReceiptLine { storageRequirements?: StorageRequirements | null; materialCode: string; quantityUnit: string; quantity: string; weight: string; externalRef: string }
interface StockDocument { documentCode: string; kind: string; sourceNote: string; handoverPerson: string; recipient: string; purpose: string; targetOrderCode: string; lines: Array<ReceiptLine & { lineNo: number }> }
interface MovementRow {
  time: string;
  kindLabel: string;
  documentCode: string;
  source: string;
  target: string;
  quantity: string;
  weight: string;
  beforeQuantity: string;
  afterQuantity: string;
  operatorName: string;
}

const overview = ref<Overview | null>(null);
const batches = ref<BatchCard[]>([]);
const movements = ref<MovementRow[]>([]);
const loadError = ref('');
const denied = ref(false);
const loading = ref(false);
const loaded = ref(false);
const message = ref('');
const formError = ref('');
const detailError = ref('');
const submitting = ref(false);
const action = ref('');
const selectedKey = ref('');
const materialFilter = ref('');
const batchFilter = ref('');
const customerFilter = ref('');
const warehouseFilter = ref('');
const statusFilter = ref('');
const fromDate = ref('');
const toDate = ref('');
const advancedFilterCount = computed(() => [batchFilter.value, customerFilter.value, warehouseFilter.value, fromDate.value, toDate.value].filter(value => value.trim()).length);
const extraLines = ref<ReceiptLine[]>([]);
const confirmLines = ref<ReceiptLine[]>([]);
const sourceLineNo = ref(1);
const direction = ref('OUT');
const correctionReason = ref('');
const selectedDocument = ref<StockDocument | null>(null);
const customers = ref<CustomerProfile[]>([]);
const materials = ref<MaterialProfile[]>([]);
const catalogError = ref('');
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
let loadRevision = 0;
let detailRevision = 0;
const attempts = ref<Record<string, RequestAttempt>>({});
type StockCommand = { path: string; prefix: string; body: Record<string, unknown>; success: string; action: string; label: string };
const pendingCommand = ref<StockCommand | null>(null);
const locked = computed(() => submitting.value || Boolean(pendingCommand.value));
let disposed = false;
onBeforeUnmount(() => { disposed = true; ++loadRevision; ++detailRevision; });

const materialCode = ref('');
const quantityUnit = ref('克');
const quantity = ref('');
const weight = ref('');
const warehouseCode = ref('WH');
const customerId = ref('');
const customerName = ref('');
const sourceNote = ref(props.initialSource);
const handoverPerson = ref('');
const externalRef = ref('');
const recipient = ref('');
const purpose = ref('');
const targetOrder = ref('');
const goodsName = ref('');
const dueDate = ref('');
const requirement = ref('');
const templateCode = ref('');
const templateVersion = ref(0);
const postNow = ref(true);
const receiptLines = computed(() => [amountBody(), ...extraLines.value.map((line) => ({ ...line, weight: line.weight.trim() || null }))]);
const readyToPost = computed(() => postNow.value && receiptLines.value.every((line) => line.weight !== null));
const actionPermission = computed<Record<string, string>>(() => ({ receive: props.scope === 'company' ? 'STOCK_COMPANY_RECEIVE' : 'STOCK_PREP_RECEIVE',
  issue: props.scope === 'company' ? 'STOCK_COMPANY_ISSUE' : 'STOCK_PREP_ISSUE',
  return: props.scope === 'company' ? 'STOCK_COMPANY_RETURN' : 'STOCK_PREP_RETURN',
  order: 'STOCK_PREP_ISSUE', 'customer-return': 'STOCK_PREP_CUSTOMER_RETURN', freeze: 'STOCK_FREEZE', unfreeze: 'STOCK_FREEZE',
  correct: 'STOCK_CORRECT', confirm: props.scope === 'company' ? 'STOCK_COMPANY_RECEIVE' : 'STOCK_PREP_RECEIVE' }));
function canDo(name: string) {
  if (!session.value) return true;
  return session.value.effectivePermissions?.includes(actionPermission.value[name]) ?? session.value.role === 'ADMIN';
}

const selected = computed(() => batches.value.find((item) => cardKey(item) === selectedKey.value) ?? null);
const title = computed(() => props.scope === 'company' ? '公司自有物料' : '客户寄存备料');

onMounted(() => { void load(); void loadCatalog(); });

async function loadCatalog() {
  try {
    [customers.value, materials.value] = await Promise.all([fetchCustomers(), fetchMaterials()]);
    catalogError.value = '';
  } catch (error) { catalogError.value = error instanceof Error ? error.message : '客户与物料目录加载失败'; }
}

function cardKey(item: BatchCard) {
  return JSON.stringify([props.scope, item.customerId ?? null, item.documentCode, item.batchCode, item.lineNo, item.materialCode, item.warehouseCode, item.quantityUnit, item.pending ? 'pending' : item.status]);
}

function emptyOverview(): Overview {
  return { scope: props.scope, batchCount: 0, quantities: [], knownWeight: '', frozenCount: 0, pendingCount: 0 };
}

async function load() {
  const revision = ++loadRevision;
  loading.value = true;
  loadError.value = '';
  denied.value = false;
  try {
    const query = new URLSearchParams({ scope: props.scope });
    if (materialFilter.value.trim()) query.set('materialCode', materialFilter.value.trim());
    if (batchFilter.value.trim()) query.set('batchCode', batchFilter.value.trim());
    if (props.scope === 'prep' && customerFilter.value) query.set('customerId', customerFilter.value);
    if (warehouseFilter.value.trim()) query.set('warehouseCode', warehouseFilter.value.trim());
    if (statusFilter.value) query.set('status', statusFilter.value);
    if (fromDate.value) query.set('fromDate', fromDate.value);
    if (toDate.value) query.set('toDate', toDate.value);
    const [summary, rows] = await Promise.all([
      apiGet<Overview>(`/api/inventory/material-stock/overview?${query}`),
      apiGet<BatchCard[]>(`/api/inventory/material-stock/batches?${query}`),
    ]);
    if (revision !== loadRevision) return;
    overview.value = summary && !Array.isArray(summary) ? summary : emptyOverview();
    batches.value = Array.isArray(rows) ? rows : [];
    loaded.value = true;
    if (selected.value) await loadMovements(selected.value);
    else { selectedKey.value = ''; movements.value = []; selectedDocument.value = null; ++detailRevision; }
  } catch (error) {
    if (revision !== loadRevision) return;
    overview.value = null;
    batches.value = [];
    denied.value = error instanceof ApiRequestError && (error.status === 403 || error.code === 40301);
    loadError.value = error instanceof Error ? error.message : '库存加载失败';
  } finally {
    if (revision === loadRevision) loading.value = false;
  }
}

async function loadMovements(card: BatchCard) {
  const revision = ++detailRevision;
  movements.value = [];
  selectedDocument.value = null;
  detailError.value = '';
  const query = new URLSearchParams({ scope: props.scope });
  if (card.batchCode) query.set('batchCode', card.batchCode);
  if (card.documentCode) query.set('documentCode', card.documentCode);
  try {
    const [rows, document] = await Promise.all([
      apiGet<MovementRow[]>(`/api/inventory/material-stock/movements?${query}`),
      card.documentCode ? apiGet<StockDocument>(`/api/inventory/material-stock/documents/${encodeURIComponent(card.documentCode)}`) : Promise.resolve(null),
    ]);
    if (revision !== detailRevision) return;
    movements.value = rows;
    selectedDocument.value = document;
    if (card.pending && document?.lines) confirmLines.value = document.lines.map((line) => ({ ...line, quantity: String(line.quantity), weight: line.weight == null ? '' : String(line.weight) }));
  } catch (error) {
    if (revision !== detailRevision) return;
    movements.value = [];
    detailError.value = error instanceof Error ? error.message : '流水加载失败';
  }
}

function selectCard(card: BatchCard) {
  if (locked.value) return;
  if (selectedKey.value !== cardKey(card)) { quantity.value = ''; weight.value = ''; targetOrder.value = ''; confirmLines.value = []; }
  selectedKey.value = cardKey(card);
  action.value = '';
  formError.value = '';
  message.value = '';
  materialCode.value = card.materialCode;
  quantityUnit.value = card.quantityUnit === '单位未记录' ? '' : card.quantityUnit;
  warehouseCode.value = card.warehouseCode;
  if (card.customerId) customerId.value = String(card.customerId);
  void loadMovements(card);
}

function requestIdFor(prefix: string, fingerprint: string) {
  const next = nextRequestAttempt(attempts.value[prefix] ?? null, fingerprint, prefix);
  if (next !== attempts.value[prefix]) attempts.value = { ...attempts.value, [prefix]: next };
  return next.requestId;
}

function amountBody() {
  return {
    materialCode: materialCode.value.trim(),
    quantityUnit: quantityUnit.value.trim(),
    quantity: quantity.value.trim(),
    weight: weight.value.trim() === '' ? null : weight.value.trim(),
    externalRef: externalRef.value.trim(),
  };
}

async function submit(path: string, prefix: string, fingerprint: string, body: Record<string, unknown>, success: string) {
  if (locked.value || !canDo(action.value)) return;
  const snapshot = JSON.parse(JSON.stringify(body)) as Record<string, unknown>;
  snapshot.requestId = requestIdFor(prefix, JSON.stringify(snapshot));
  await sendCommand({ path, prefix, body: snapshot, success, action: action.value, label: selected.value ? `${selected.value.ownerLabel} / ${selected.value.batchCode || selected.value.documentCode} / ${selected.value.materialCode} / ${selected.value.warehouseCode}` : `${title.value} / ${snapshot.customerName || '公司自有'} / ${snapshot.warehouseCode || '未填仓库'}` });
}

async function sendCommand(command: StockCommand) {
  if (submitting.value || !canDo(command.action)) return;
  formError.value = '';
  message.value = '';
  submitting.value = true;
  try {
    const code = await apiPost<Record<string, unknown>, string>(command.path, command.body);
    if (disposed) return;
    pendingCommand.value = null;
    const remaining = { ...attempts.value };
    delete remaining[command.prefix];
    attempts.value = remaining;
    message.value = `${command.success} ${code}`;
    action.value = '';
    await load();
  } catch (error) {
    if (disposed) return;
    const known = error instanceof ApiRequestError && error.status >= 400 && error.status < 500 && error.status !== 408;
    pendingCommand.value = known ? null : command;
    formError.value = error instanceof Error ? error.message : '库存操作失败';
  } finally {
    submitting.value = false;
  }
}

function receive() {
  const line = amountBody();
  const fingerprint = [props.scope, customerId.value, warehouseCode.value, line.materialCode, line.quantityUnit, line.quantity, line.weight, postNow.value].join('|');
  const path = props.scope === 'company' ? '/api/inventory/material-stock/company-receipts' : '/api/inventory/material-stock/prep-receipts';
  void submit(path, `${props.scope}-recv`, fingerprint, {
    customerId: props.scope === 'prep' ? Number(customerId.value) : null,
    customerName: customers.value.find((customer) => String(customer.customerId) === customerId.value)?.name ?? customerName.value.trim(),
    warehouseCode: warehouseCode.value.trim(),
    sourceNote: sourceNote.value.trim(),
    handoverPerson: handoverPerson.value.trim(),
    postNow: readyToPost.value,
    lines: receiptLines.value,
  }, readyToPost.value ? '已入库' : '已保存待核');
}

function issue() {
  if (!selected.value?.batchCode) {
    formError.value = '请先选择已入库批次';
    return;
  }
  const fingerprint = ['issue', selected.value.batchCode, quantity.value, weight.value, recipient.value, purpose.value, targetOrder.value].join('|');
  const path = props.scope === 'company' ? '/api/inventory/material-stock/company-issues' : '/api/inventory/material-stock/prep-issues';
  void submit(path, `${props.scope}-issue`, fingerprint, {
    customerId: selected.value.customerId ?? null,
    warehouseCode: selected.value.warehouseCode,
    batchCode: selected.value.batchCode,
    materialCode: selected.value.materialCode,
    quantityUnit: selected.value.quantityUnit,
    quantity: quantity.value.trim(),
    weight: weight.value.trim(),
    recipient: recipient.value.trim(),
    purpose: purpose.value.trim(),
    targetOrderCode: targetOrder.value.trim(),
  }, props.scope === 'company' ? '已领用，只转入在制' : '已领用到加工单');
}

function sendBack() {
  if (!selected.value?.batchCode) return;
  const fingerprint = ['return', selected.value.batchCode, targetOrder.value, quantity.value, weight.value].join('|');
  const path = props.scope === 'company' ? '/api/inventory/material-stock/company-returns' : '/api/inventory/material-stock/prep-returns';
  void submit(path, `${props.scope}-return`, fingerprint, {
    customerId: selected.value.customerId ?? null,
    warehouseCode: selected.value.warehouseCode,
    batchCode: selected.value.batchCode,
    materialCode: selected.value.materialCode,
    sourceDocumentCode: targetOrder.value.trim(),
    sourceLineNo: sourceLineNo.value,
    quantity: quantity.value.trim(),
    weight: weight.value.trim(),
  }, '已按原单退回');
}

function customerReturn() {
  if (!selected.value?.batchCode) return;
  const fingerprint = ['out', selected.value.batchCode, quantity.value, weight.value, handoverPerson.value].join('|');
  void submit('/api/inventory/material-stock/customer-returns', 'prep-out', fingerprint, {
    customerId: selected.value.customerId,
    warehouseCode: selected.value.warehouseCode,
    batchCode: selected.value.batchCode,
    materialCode: selected.value.materialCode,
    quantity: quantity.value.trim(),
    weight: weight.value.trim(),
    handoverPerson: handoverPerson.value.trim(),
    sourceNote: sourceNote.value.trim(),
  }, '已退还客户');
}

function openOrder() {
  if (!selected.value?.batchCode) return;
  const fingerprint = ['order', selected.value.batchCode, quantity.value, requirement.value, dueDate.value, templateCode.value].join('|');
  void submit('/api/inventory/material-stock/prep-orders', 'prep-order', fingerprint, {
    customerId: selected.value.customerId,
    customerName: selected.value.ownerLabel,
    warehouseCode: selected.value.warehouseCode,
    batchCode: selected.value.batchCode,
    materialCode: selected.value.materialCode,
    quantityUnit: selected.value.quantityUnit,
    quantity: quantity.value.trim(),
    weight: weight.value.trim(),
    goodsName: goodsName.value.trim(),
    processingRequirement: requirement.value.trim(),
    dueDate: dueDate.value,
    templateCode: templateCode.value,
    templateVersion: templateVersion.value,
  }, '已建加工单');
}

function freeze() {
  if (!selected.value?.batchCode) return;
  const fingerprint = ['freeze', selected.value.batchCode, quantity.value, weight.value].join('|');
  void submit('/api/inventory/material-stock/freezes', `${props.scope}-freeze`, fingerprint, {
    scope: props.scope,
    customerId: selected.value.customerId ?? null,
    warehouseCode: selected.value.warehouseCode,
    batchCode: selected.value.batchCode,
    materialCode: selected.value.materialCode,
    quantity: quantity.value.trim(),
    weight: weight.value.trim(),
  }, '已冻结');
}

function confirmPending() {
  if (!selected.value?.pending) return;
  const fingerprint = ['confirm', selected.value.documentCode, quantity.value, weight.value].join('|');
  void submit('/api/inventory/material-stock/prep-confirmations', 'stock-confirm', fingerprint, {
    documentCode: selected.value.documentCode,
    lines: confirmLines.value,
  }, '已确认入账');
}

function unfreeze() {
  if (!selected.value) return;
  void submit('/api/inventory/material-stock/unfreezes', `${props.scope}-unfreeze`, '', {
    scope: props.scope, customerId: selected.value.customerId ?? null, warehouseCode: selected.value.warehouseCode,
    batchCode: selected.value.batchCode, materialCode: selected.value.materialCode, quantity: quantity.value.trim(),
    weight: weight.value.trim(), sourceDocumentCode: targetOrder.value.trim(),
  }, '已解除冻结');
}

function correct() {
  if (!selected.value) return;
  void submit('/api/inventory/material-stock/corrections', `${props.scope}-correct`, '', {
    scope: props.scope, customerId: selected.value.customerId ?? null, warehouseCode: selected.value.warehouseCode,
    batchCode: selected.value.batchCode, materialCode: selected.value.materialCode, quantityUnit: selected.value.quantityUnit,
    quantity: quantity.value.trim(), weight: weight.value.trim(), direction: direction.value, reason: correctionReason.value.trim(),
  }, '已记录冲正');
}
</script>

<template>
  <section class="stock-desk" :aria-label="title">
    <header class="stock-head">
      <h2>{{ title }}</h2>
      <button class="secondary-button" type="button" :disabled="submitting" @click="load">刷新</button>
      <button v-if="canDo('receive')" class="primary-button" type="button" :disabled="locked" @click="action = 'receive'">{{ props.scope === 'company' ? '入库' : '寄存收货' }}</button>
    </header>
    <p v-if="props.scope === 'company'" class="archive-note">领用只表示转入在制，仍在厂内，不会并入客户加工库存，也不会自动核销。</p>
    <p v-else class="archive-note">先寄存不建加工单、不产生加工费。重量留空表示待称量，整单称清后才入账。</p>
    <p v-if="catalogError" class="operation-feedback warning">{{ catalogError }}</p>
    <datalist :id="`${props.scope}-materials`"><option v-for="material in materials" :key="material.materialCode" :value="material.materialCode">{{ material.name }}</option></datalist>
    <datalist :id="`${props.scope}-customers`"><option v-for="customer in customers" :key="customer.customerId" :value="customer.customerId">{{ customer.name }}</option></datalist>
    <p v-if="loading" class="empty-hint">正在加载库存</p>
    <p v-if="denied" class="operation-feedback danger">无权限查看这部分库存</p>
    <p v-else-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
    <div v-if="overview && !denied" class="stock-overview" aria-label="库存总览">
      <span>有效批次 {{ overview.batchCount }}</span>
      <span v-for="item in overview.quantities" :key="item.unit">{{ item.quantity }} {{ item.unit }}</span>
      <span v-if="!overview.quantities.length">暂无已入账数量</span>
      <span>{{ overview.knownWeight ? `已知重量 ${overview.knownWeight} 克` : '暂无已称重量' }}</span>
      <span>冻结 {{ overview.frozenCount }}</span>
      <span>待核 {{ overview.pendingCount }}</span>
    </div>
    <fieldset class="stock-filters stock-plain-fields" :disabled="locked">
      <label><span>物料</span><input v-model="materialFilter" aria-label="筛选物料" /></label>
      <label><span>状态</span><select v-model="statusFilter" aria-label="筛选库存状态"><option value="">全部</option><option>待核</option><option>可用</option><option>在制</option><option>冻结</option></select></label>
      <button class="secondary-button" type="button" @click="load">应用筛选</button>
      <details class="info-disclosure stock-advanced-filters">
        <summary>更多筛选<small v-if="advancedFilterCount">已填 {{ advancedFilterCount }} 项</small></summary>
        <div class="stock-filters">
      <label><span>批次</span><input v-model="batchFilter" aria-label="筛选批次" /></label>
      <label v-if="props.scope === 'prep'"><span>客户</span><input v-model="customerFilter" :list="`${props.scope}-customers`" aria-label="筛选客户" /></label>
      <label><span>仓库</span><input v-model="warehouseFilter" aria-label="筛选仓库" /></label>
      <label><span>变动开始日期</span><input v-model="fromDate" type="date" aria-label="变动开始日期" /></label>
      <label><span>变动结束日期</span><input v-model="toDate" type="date" aria-label="变动结束日期" /></label>
        </div>
      </details>
    </fieldset>
    <p v-if="loaded && !loading && !loadError && !batches.length" class="empty-hint">没有符合条件的库存</p>
    <div class="stock-cards">
      <article v-for="card in batches" :key="cardKey(card)" class="stock-card" :class="{ 'is-selected': selected && cardKey(selected) === cardKey(card) }">
        <button type="button" class="stock-card-button" :disabled="locked" @click="selectCard(card)">
          <strong>{{ card.pending ? '待核' : (card.batchCode || '未编号') }}</strong>
          <span>{{ card.ownerLabel }} · {{ card.materialCode }} · {{ card.quantityUnit }}</span>
          <span v-if="card.pending">申报 {{ card.bookQuantity }} {{ card.quantityUnit }} · {{ card.weightText }}</span>
          <span v-else>可用 {{ card.availableQuantity }} {{ card.quantityUnit }} · {{ card.availableWeight }} 克</span>
          <span v-if="!card.pending && Number(card.frozenQuantity) > 0">冻结 {{ card.frozenQuantity }} {{ card.quantityUnit }}</span>
          <span>{{ card.status }} · {{ card.warehouseCode }}<template v-if="card.sourceMissing"> · 来源单据缺失</template></span>
        </button>
      </article>
    </div>
    <section v-if="selected" class="stock-selected-summary" aria-label="当前批次摘要">
      <h3>{{ selected.batchCode || selected.documentCode }} · {{ selected.materialCode }}</h3>
      <dl class="info-facts">
        <div><dt>账面</dt><dd>{{ selected.bookQuantity }} {{ selected.quantityUnit }} / {{ selected.bookWeight }} 克</dd></div>
        <div><dt>可用</dt><dd>{{ selected.availableQuantity }} {{ selected.quantityUnit }}</dd></div>
        <div><dt>已领用</dt><dd>{{ selected.issuedQuantity }} {{ selected.quantityUnit }}</dd></div>
        <div><dt>冻结</dt><dd>{{ selected.frozenQuantity }} {{ selected.quantityUnit }}</dd></div>
      </dl>
    </section>
    <fieldset v-if="selected" class="stock-actions stock-plain-fields" :disabled="locked">
      <button v-if="canDo('issue')" class="secondary-button" type="button" :disabled="submitting || !selected?.batchCode || selected.sourceMissing" @click="action = 'issue'">领用</button>
      <button v-if="canDo('return')" class="secondary-button" type="button" :disabled="submitting || !selected?.batchCode" @click="action = 'return'">{{ props.scope === 'company' ? '原单退回' : '退回备料' }}</button>
      <button v-if="props.scope === 'prep' && canDo('customer-return')" class="secondary-button" type="button" :disabled="submitting || !selected?.batchCode || selected.sourceMissing" @click="action = 'customer-return'">退还客户</button>
      <button v-if="props.scope === 'prep' && canDo('order')" class="secondary-button" type="button" :disabled="submitting || !selected?.batchCode || selected.sourceMissing" @click="action = 'order'">从备料建加工单</button>
      <details v-if="canDo('freeze') || (session?.role === 'ADMIN' && canDo('correct'))" class="info-disclosure">
        <summary>更多批次操作</summary>
        <div class="stock-actions">
      <button v-if="canDo('freeze')" class="secondary-button" type="button" :disabled="submitting || !selected?.batchCode || selected.sourceMissing" @click="action = 'freeze'">冻结</button>
      <button v-if="canDo('unfreeze')" class="secondary-button" type="button" :disabled="submitting || !selected?.batchCode || selected.sourceMissing" @click="action = 'unfreeze'">解除冻结</button>
      <button v-if="session?.role === 'ADMIN' && canDo('correct')" class="secondary-button" type="button" :disabled="submitting || !selected?.batchCode || selected.sourceMissing" @click="action = 'correct'">差错冲正</button>
        </div>
      </details>
      <button v-if="selected?.pending && canDo('confirm')" class="secondary-button" type="button" :disabled="submitting || !confirmLines.length" @click="action = 'confirm'">确认入账</button>
    </fieldset>
    <form v-if="action" class="stock-form" @submit.prevent>
      <fieldset class="stock-form stock-plain-fields" :disabled="locked">
      <p v-if="selected && action !== 'receive'" class="archive-note">{{ selected.ownerLabel }} · {{ selected.materialCode }} · {{ selected.warehouseCode }} · {{ selected.batchCode || selected.documentCode }} · 可用 {{ selected.availableQuantity }} {{ selected.quantityUnit }}</p>
      <template v-if="action === 'receive'">
        <label v-if="props.scope === 'prep'">客户编号<input v-model="customerId" :list="`${props.scope}-customers`" aria-label="寄存客户" /></label>
        <label v-if="props.scope === 'prep'">客户名称<input v-model="customerName" aria-label="寄存客户名称" /></label>
        <label>仓库<input v-model="warehouseCode" :aria-label="props.scope === 'company' ? '公司仓库' : '寄存仓库'" /></label>
        <label>物料<input v-model="materialCode" :list="`${props.scope}-materials`" :aria-label="props.scope === 'company' ? '公司物料' : '寄存物料'" /></label>
        <label>数量单位<input v-model="quantityUnit" :aria-label="props.scope === 'company' ? '公司单位' : '寄存单位'" /></label>
        <label>数量<input v-model="quantity" :aria-label="props.scope === 'company' ? '公司数量' : '寄存数量'" /></label>
        <label>重量（克，未称留空）<input v-model="weight" :aria-label="props.scope === 'company' ? '公司重量' : '寄存重量'" /></label>
        <label>外部参考号<input v-model="externalRef" aria-label="外部参考号" /></label>
        <fieldset v-for="(line, index) in extraLines" :key="index" class="stock-line">
          <legend>明细 {{ index + 2 }}</legend>
          <label>物料<input v-model="line.materialCode" :list="`${props.scope}-materials`" :aria-label="`明细 ${index + 2} 物料`" /></label>
          <label>数量单位<input v-model="line.quantityUnit" :aria-label="`明细 ${index + 2} 单位`" /></label>
          <label>数量<input v-model="line.quantity" :aria-label="`明细 ${index + 2} 数量`" /></label>
          <label>重量（克，未称留空）<input v-model="line.weight" :aria-label="`明细 ${index + 2} 重量`" /></label>
          <label>外部参考号<input v-model="line.externalRef" :aria-label="`明细 ${index + 2} 参考号`" /></label>
          <button type="button" class="secondary-button" :disabled="submitting" @click="extraLines.splice(index, 1)">移除明细 {{ index + 2 }}</button>
        </fieldset>
        <button type="button" class="secondary-button" :disabled="submitting" @click="extraLines.push({ materialCode: '', quantityUnit: '颗', quantity: '', weight: '', externalRef: '' })">新增物料明细</button>
        <label>来源说明<input v-model="sourceNote" aria-label="来源说明" /></label>
        <label>交接人<input v-model="handoverPerson" aria-label="交接人" /></label>
        <label class="archive-check"><input v-model="postNow" type="checkbox" aria-label="立即入账" /> 重量已明确，立即入账</label>
        <button class="primary-button" type="button" :disabled="submitting" @click="receive">{{ readyToPost ? '确认入库' : '保存待核' }}</button>
      </template>
      <template v-else-if="action === 'issue'">
        <p v-if="props.scope === 'company'">这次领用只减少库内可用量，并记入已领用未退回。</p>
        <label>本次数量<input v-model="quantity" aria-label="领用数量" /></label>
        <label>本次重量<input v-model="weight" aria-label="领用重量" /></label>
        <label>领用人<input v-model="recipient" aria-label="领用人" /></label>
        <label>用途<input v-model="purpose" aria-label="领用用途" /></label>
        <label v-if="props.scope === 'prep'">加工母单<input v-model="targetOrder" aria-label="领用母单" /></label>
        <label v-else>关联加工单（可空）<input v-model="targetOrder" aria-label="关联加工单" /></label>
        <button class="primary-button" type="button" :disabled="submitting" @click="issue">提交领用</button>
      </template>
      <template v-else-if="action === 'return'">
        <label>原领用单号<input v-model="targetOrder" aria-label="原领用单" /></label>
        <label>原领用明细行号<input v-model.number="sourceLineNo" type="number" min="1" aria-label="原领用明细行号" /></label>
        <label>退回数量<input v-model="quantity" aria-label="退回数量" /></label>
        <label>退回重量<input v-model="weight" aria-label="退回重量" /></label>
        <button class="primary-button" type="button" :disabled="submitting" @click="sendBack">提交退回</button>
      </template>
      <template v-else-if="action === 'customer-return'">
        <label>接收人<input v-model="handoverPerson" aria-label="退还接收人" /></label>
        <label>交接说明<input v-model="sourceNote" aria-label="退还说明" /></label>
        <label>退还数量<input v-model="quantity" aria-label="退还数量" /></label>
        <label>退还重量<input v-model="weight" aria-label="退还重量" /></label>
        <button class="primary-button" type="button" :disabled="submitting" @click="customerReturn">提交退还</button>
      </template>
      <template v-else-if="action === 'order'">
        <label>货品<input v-model="goodsName" aria-label="加工货品" /></label>
        <label>期限<input v-model="dueDate" type="date" aria-label="加工期限" /></label>
        <label>领用数量<input v-model="quantity" aria-label="建单数量" /></label>
        <label>领用重量<input v-model="weight" aria-label="建单重量" /></label>
        <RequirementComposer v-model="requirement" v-model:template-code="templateCode" v-model:template-version="templateVersion" />
        <button class="primary-button" type="button" :disabled="submitting" @click="openOrder">生成加工单</button>
      </template>
      <template v-else-if="action === 'freeze' || action === 'unfreeze'">
        <label v-if="action === 'unfreeze'">原冻结单号<input v-model="targetOrder" aria-label="原冻结单号" /></label>
        <label>本次数量<input v-model="quantity" aria-label="冻结数量" /></label>
        <label>本次重量<input v-model="weight" aria-label="冻结重量" /></label>
        <button class="primary-button" type="button" :disabled="submitting" @click="action === 'freeze' ? freeze() : unfreeze()">{{ action === 'freeze' ? '提交冻结' : '提交解冻' }}</button>
      </template>
      <template v-else-if="action === 'correct'">
        <label>更正方向<select v-model="direction" aria-label="更正方向"><option value="OUT">减少</option><option value="IN">增加</option></select></label>
        <label>更正数量<input v-model="quantity" aria-label="更正数量" /></label>
        <label>更正重量<input v-model="weight" aria-label="更正重量" /></label>
        <label>更正原因<textarea v-model="correctionReason" aria-label="更正原因" /></label>
        <button type="button" class="primary-button" :disabled="submitting" @click="correct">提交冲正</button>
      </template>
      <template v-else-if="action === 'confirm'">
        <p>核对本单全部明细后一次入账，未称清的整单继续待核。</p>
        <fieldset v-for="(line, index) in confirmLines" :key="index" class="stock-line">
          <legend>明细 {{ index + 1 }} · {{ line.materialCode }} · {{ line.quantityUnit }}</legend>
          <label>实收数量<input v-model="line.quantity" :aria-label="`明细 ${index + 1} 实收数量`" /></label>
          <label>实收重量<input v-model="line.weight" :aria-label="`明细 ${index + 1} 实收重量`" /></label>
        </fieldset>
        <button class="primary-button" type="button" :disabled="submitting" @click="confirmPending">确认入账</button>
      </template>
      </fieldset>
    </form>
    <p v-if="pendingCommand" class="operation-feedback warning" role="alert">原库存请求结果待核对：{{ pendingCommand.label }}。内容与编号已保留（{{ pendingCommand.body.requestId }}）。<button class="secondary-button" type="button" :disabled="submitting || !canDo(pendingCommand.action)" @click="sendCommand(pendingCommand)">原请求重试</button></p>
    <p v-if="message" class="operation-feedback">{{ message }}</p>
    <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
    <section v-if="selected" aria-label="批次流水">
      <h3>这批的出入库</h3>
      <p v-if="detailError" class="operation-feedback warning">流水读取失败：{{ detailError }}。已有业务回执保留。</p>
      <p v-for="line in selectedDocument?.lines || []" :key="`storage-${line.lineNo}`">来源明细 {{ line.lineNo }} · {{ line.materialCode }}：{{ storageText(line.storageRequirements) }}</p>
      <p v-if="selectedDocument">来源 {{ selectedDocument.documentCode }} · {{ selectedDocument.sourceNote || '无来源说明' }} · 交接人 {{ selectedDocument.handoverPerson || '未填' }}</p>
      <p v-if="selectedDocument && (selectedDocument.purpose || selectedDocument.recipient)">用途 {{ selectedDocument.purpose || '未填' }} · 领用人 {{ selectedDocument.recipient || '未填' }}<template v-if="selectedDocument.targetOrderCode"> · 关联 {{ selectedDocument.targetOrderCode }}</template></p>
      <p v-if="!movements.length" class="empty-hint">这批还没有可显示的流水</p>
      <article v-for="row in movements" :key="`${row.time}-${row.documentCode}-${row.quantity}`" class="stock-card">
        <strong>{{ row.kindLabel }} {{ row.documentCode }}</strong>
        <span>{{ row.time }} · {{ row.operatorName }}</span>
        <span>{{ row.source }} → {{ row.target }} · {{ row.quantity }} / {{ row.weight }} 克</span>
        <span>前 {{ row.beforeQuantity }} · 后 {{ row.afterQuantity }}</span>
      </article>
    </section>
  </section>
</template>

<style scoped>
.stock-desk { display: grid; gap: 12px; min-width: 0; }
.stock-desk > .operation-feedback { overflow-wrap: anywhere; }
.stock-head, .stock-overview { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.stock-actions, .stock-filters, .stock-form, .stock-line { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; align-items: end; }
.stock-filters label, .stock-form label { display: grid; gap: 4px; min-width: 0; }
.stock-advanced-filters { grid-column: 1 / -1; }
.stock-selected-summary { display: grid; gap: 12px; padding-top: 12px; border-top: 1px solid var(--line); }
.stock-selected-summary h3 { margin: 0; font-size: 15px; overflow-wrap: anywhere; }
.stock-plain-fields { min-width: 0; border: 0; padding: 0; margin: 0; }
.stock-form > .stock-plain-fields,
.stock-plain-fields > p,
.stock-plain-fields > .archive-check,
.stock-plain-fields > .archive-note,
.stock-plain-fields > button,
.stock-plain-fields > .stock-line,
.stock-plain-fields > .requirement-composer,
.stock-filters > button,
.stock-line > button,
.stock-plain-fields label:has(textarea) { grid-column: 1 / -1; }
.stock-line { border: 1px solid var(--line, #ddd); border-radius: 8px; padding: 8px; }
.stock-card, .stock-card-button { overflow-wrap: anywhere; }
.stock-form input, .stock-form textarea, .stock-form select, .stock-filters input, .stock-filters select { width: 100%; max-width: 100%; min-width: 0; box-sizing: border-box; }
.stock-cards { display: grid; gap: 8px; }
.stock-card { border: 1px solid var(--line, #ddd); border-radius: 8px; padding: 8px; min-width: 0; }
.stock-card.is-selected { border-color: var(--accent, #8a6a3b); }
.stock-card-button { display: grid; gap: 4px; width: 100%; text-align: left; background: transparent; border: 0; padding: 0; }
.stock-desk button { min-height: 36px; }
@media (max-width: 900px) {
  .stock-desk { overflow-x: hidden; }
  .stock-overview { display: grid; grid-template-columns: 1fr 1fr; }
}
</style>
