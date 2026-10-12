<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';
import OrderContextCard from '../scan/OrderContextCard.vue';
import ProgressNoteForm from '../scan/ProgressNoteForm.vue';
import ScanInput from '../scan/ScanInput.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { extractCourierNumber } from '../scan/scanCode';
import { appendProgressRecord, progressTargetFromScan } from '../scan/progressTarget';
import { useScanContext } from '../scan/useScanContext';
import type { InventoryBalance } from '../../data/m1PrototypeData';
import { shipFinishedStock, type OutboundShipmentResult } from '../../services/businessOperationApi';
import {
  queryScanContext,
  type ScanBalanceCandidate,
  type ScanContextResponse,
  type ScanObjectType,
  type ScanProgressRecord,
  type ScanRelatedTarget,
} from '../../services/scanContextApi';

defineProps<{
  title: string;
  intro: string;
}>();

const scanText = ref('');
const logisticsScanText = ref('');
const hint = ref('');
const formError = ref('');
const logisticsHint = ref('');
const refreshError = ref('');
const shipmentCode = ref('');
const expressCompany = ref('');
const logisticsNo = ref('');
const quantity = ref('');
const weight = ref('');
const selectedKey = ref('');
const shipping = ref(false);
const refreshBusy = ref(false);
const shipmentResult = ref<OutboundShipmentResult | null>(null);
const recognizedContent = ref('');
const recognizedType = ref<ScanObjectType | ''>('');
const requestIds = ref<Record<string, string>>({});
const shippingScan = reactive(useScanContext());

const progressTarget = computed(() => progressTargetFromScan(shippingScan.context));
const candidates = computed(() => shippingScan.context?.operationContext.balanceCandidates ?? []);
const mustChoose = computed(() => Boolean(shippingScan.context?.operationContext.balanceSelectionRequired));
const shipAction = computed(() => shippingScan.context?.actions.find((item) => item.code === 'SHIP') ?? null);
const alreadyShipped = computed(() => Boolean(
  shipAction.value && !shipAction.value.allowed && shipAction.value.blockReason?.includes('已经发货'),
));
const shipBlocked = computed(() => {
  const action = shipAction.value;
  if (!action || action.allowed || alreadyShipped.value) return '';
  if (action.blockReason?.includes('请选择完整')) return '';
  return action.blockReason || '当前不能发货';
});
const chosen = computed(() => {
  if (!selectedKey.value) return null;
  const matched = candidates.value.find((item) => balanceKey(item) === selectedKey.value);
  if (matched) return matched;
  const selected = shippingScan.context?.operationContext.selectedBalance;
  if (selected && balanceKey(selected) === selectedKey.value) return selected;
  return null;
});
const businessKey = computed(() => {
  const target = shippingScan.identity;
  if (!target || target.objectType === 'EXPRESS_NO') return '';
  return target.reference || `${target.objectType}:${target.code}`;
});
const evidenceTargetCode = computed(() => {
  if (shipmentResult.value?.shipmentCode) return shipmentResult.value.shipmentCode;
  if (!alreadyShipped.value) return '';
  return shippingScan.context?.target.outboundOrderCode || shippingScan.context?.target.code || '';
});
const shipments = computed(() => shippingScan.context?.progress.shipments ?? []);

function balanceKey(item: ScanBalanceCandidate): string {
  return [
    item.ownerType,
    item.ownerCustomerId,
    item.customerId,
    item.motherOrderCode,
    item.workOrderCode,
    item.materialCode,
    item.warehouseCode,
    item.stockStatus,
  ].join('|');
}

function balanceLabel(item: ScanBalanceCandidate): string {
  return [
    `客户${item.customerId ?? '未填'}`,
    `货权${item.ownerCustomerId ?? '未填'}`,
    `母单${item.motherOrderCode || '未填'}`,
    `工单${item.workOrderCode || '未填'}`,
    `物料${item.materialCode || '未填'}`,
    `仓库${item.warehouseCode || '未填'}`,
    item.stockStatus || '未填状态',
    `${item.quantity ?? '未填'}件`,
    `${item.weight ?? '未填'}克`,
  ].join(' / ');
}

function rememberProgress(record: ScanProgressRecord) {
  appendProgressRecord(shippingScan.context, record);
}

function clearTarget() {
  const had = shippingScan.context !== null || shippingScan.loading || selectedKey.value !== '' || shipmentResult.value !== null;
  shippingScan.invalidate();
  selectedKey.value = '';
  shipmentResult.value = null;
  refreshError.value = '';
  requestIds.value = {};
  recognizedContent.value = '';
  recognizedType.value = '';
  shipmentCode.value = '';
  expressCompany.value = '';
  logisticsNo.value = '';
  quantity.value = '';
  weight.value = '';
  formError.value = '';
  if (had) hint.value = '标签已变化，请重新识别';
}

watch(scanText, () => clearTarget(), { flush: 'sync' });

function applyBalance(response: ScanContextResponse) {
  selectedKey.value = '';
  const operation = response.operationContext;
  if (!operation.balanceSelectionRequired && operation.selectedBalance) {
    selectedKey.value = balanceKey(operation.selectedBalance);
  }
}

function fieldText(value: string | number | null | undefined): string {
  return String(value ?? '').trim();
}

function fingerprint(): string {
  const balance = chosen.value;
  return [
    fieldText(shipmentCode.value),
    balance ? balanceKey(balance) : '',
    fieldText(quantity.value),
    fieldText(weight.value),
    fieldText(expressCompany.value),
    fieldText(logisticsNo.value),
  ].join('|');
}

function ensureRequestId(key: string) {
  const current = requestIds.value[key];
  if (current) return current;
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const created = `ship-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
  requestIds.value = { ...requestIds.value, [key]: created };
  return created;
}

function rowOf(balance: ScanBalanceCandidate): InventoryBalance {
  return {
    owner: shippingScan.context?.order.customerName || '',
    batch: balance.workOrderCode || balance.motherOrderCode || '',
    material: balance.materialCode || '',
    location: balance.warehouseCode || '',
    status: balance.stockStatus || '',
    qty: balance.quantity ?? 0,
    weight: String(balance.weight ?? ''),
    frozen: '0',
    operationContext: {
      customerId: balance.customerId,
      ownerCustomerId: balance.ownerCustomerId,
      motherOrderCode: balance.motherOrderCode || '',
      workOrderCode: balance.workOrderCode || '',
      materialCode: balance.materialCode || '',
      warehouseCode: balance.warehouseCode || '',
      ownerType: balance.ownerType || '',
      stockStatus: balance.stockStatus || '',
      quantity: balance.quantity ?? 0,
      weight: balance.weight ?? 0,
    },
  };
}

async function recognize(draft: ScanDraft) {
  formError.value = '';
  hint.value = '';
  const content = draft.content.trim();
  if (!content) {
    shippingScan.invalidate();
    hint.value = '请先扫描要发货的标签';
    return;
  }
  const response = await shippingScan.lookup({
    content,
    objectType: draft.objectType,
    intent: 'SHIPPING',
  });
  if (!response) {
    if (shippingScan.error) formError.value = shippingScan.error;
    return;
  }
  recognizedContent.value = content;
  recognizedType.value = draft.objectType ?? '';
  applyBalance(response);
}

async function chooseTarget(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
  scanText.value = related.code;
  await recognize({ content: related.code, objectType: related.objectType });
}

function fillLogistics(draft: ScanDraft) {
  logisticsHint.value = '';
  const number = extractCourierNumber(draft.content);
  if (!number) {
    logisticsHint.value = '未识别的物流单号';
    return;
  }
  logisticsNo.value = number;
}

async function refreshShipment() {
  if (!shipmentResult.value || !recognizedContent.value) return;
  const seen = shippingScan.revision;
  refreshBusy.value = true;
  refreshError.value = '';
  try {
    const loaded = await queryScanContext({
      content: recognizedContent.value,
      intent: 'SHIPPING',
      ...(recognizedType.value ? { objectType: recognizedType.value } : {}),
    });
    if (shippingScan.revision !== seen || !shipmentResult.value) return;
    shippingScan.context = loaded;
  } catch {
    if (shippingScan.revision !== seen || !shipmentResult.value) return;
    refreshError.value = '发货已成功，刷新失败。请单独重试刷新，不会再次扣库。';
  } finally {
    if (shippingScan.revision === seen) refreshBusy.value = false;
  }
}

async function confirmShip() {
  formError.value = '';
  hint.value = '';
  if (alreadyShipped.value) return;
  if (shipBlocked.value) {
    formError.value = shipBlocked.value;
    return;
  }
  if (!chosen.value || chosen.value.stockStatus !== 'FINISHED') {
    formError.value = '请选择完整的物料、仓库、货权和库存状态';
    return;
  }
  if (!fieldText(shipmentCode.value) || !fieldText(logisticsNo.value)) {
    formError.value = '请填写出货单号和物流单号';
    return;
  }
  const quantityText = fieldText(quantity.value);
  const weightText = fieldText(weight.value);
  if (!quantityText || !weightText) {
    formError.value = '请确认本次数量和本次重量；留空不会自动沿用整批重量';
    return;
  }
  const seen = shippingScan.revision;
  const balance = chosen.value;
  const requestId = ensureRequestId(fingerprint());
  const extra = {
    shipmentCode: fieldText(shipmentCode.value),
    logisticsNo: fieldText(logisticsNo.value),
    expressCompany: fieldText(expressCompany.value),
    quantity: quantityText,
    weight: weightText,
    deliveryCategory: '成品',
    requestId,
  };
  const send = () => shipFinishedStock(rowOf(balance), extra);
  shipping.value = true;
  try {
    const result = await send();
    if (shippingScan.revision !== seen) return;
    shipmentResult.value = result;
    await refreshShipment();
  } catch (error) {
    if (shippingScan.revision !== seen) return;
    const message = error instanceof Error ? error.message : '确认发货失败';
    if (message === '后端接口不可用') {
      try {
        const replayed = await send();
        if (shippingScan.revision !== seen) return;
        shipmentResult.value = replayed;
        await refreshShipment();
        return;
      } catch (again) {
        if (shippingScan.revision !== seen) return;
        formError.value = again instanceof Error ? again.message : '确认发货失败';
        hint.value = '结果未知。请用原确认重试，不会另开一次发货。';
        return;
      }
    }
    formError.value = message;
  } finally {
    if (shippingScan.revision === seen) shipping.value = false;
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
    <ScanInput
      v-model="scanText"
      field-text="成品标签"
      input-aria-label="发货贴纸"
      placeholder="JG|work_order|子单号 或 JG|outbound|出货单号"
      submit-label="识别标签"
      submit-aria-label="识别发货对象"
      photo-aria-label="拍发货标签"
      camera-aria-label="拍发货贴纸"
      :busy="shippingScan.loading || shipping"
      @draft="clearTarget"
      @recognize="recognize"
    />
    <p v-if="hint" class="operation-feedback">{{ hint }}</p>
    <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
    <OrderContextCard :context="shippingScan.context" @select="chooseTarget" />
    <ProgressNoteForm
      v-if="progressTarget"
      stage="SHIPPING"
      :target-type="progressTarget.targetType"
      :target-code="progressTarget.targetCode"
      :mother-order-code="progressTarget.motherOrderCode"
      @saved="rememberProgress"
    />

    <div v-if="alreadyShipped" class="operation-feedback success">
      <strong>这张出货单已经发货，不能再次扣库存</strong>
      <span v-for="item in shipments" :key="item.outboundCode || item.ledgerCode || item.logisticsNo || ''">
        {{ item.outboundCode || '出货单' }} / {{ item.logisticsNo || '未填物流' }} / {{ item.ledgerCode || '未记台账' }}
      </span>
      <span>可以补传证据，不会再次发货。</span>
    </div>

    <template v-if="shippingScan.context && !shippingScan.context.requiresSelection && !alreadyShipped">
      <h3>可发货库存</h3>
      <p v-if="mustChoose">有多笔余额，请选择完整维度。不会默认第一笔，也不能只按工单匹配。</p>
      <p v-else-if="!candidates.length">没有可发货的成品余额</p>
      <div class="button-row">
        <button
          v-for="item in candidates"
          :key="balanceKey(item)"
          class="secondary-button"
          type="button"
          :aria-pressed="selectedKey === balanceKey(item)"
          :aria-label="`选择余额 ${balanceLabel(item)}`"
          @click="selectedKey = balanceKey(item)"
        >
          {{ balanceLabel(item) }}
        </button>
      </div>
      <p v-if="chosen">本次从 {{ balanceLabel(chosen) }} 发出。数量和重量需要单独确认，不会自动提交全部余额。</p>
      <p v-if="shipBlocked" class="operation-feedback warning">{{ shipBlocked }}</p>
      <div class="intake-form">
        <label class="field">
          <span>出货单号</span>
          <input v-model="shipmentCode" type="text" aria-label="出货单号" />
        </label>
        <label class="field">
          <span>物流公司</span>
          <input v-model="expressCompany" type="text" aria-label="物流公司" />
        </label>
        <label class="field">
          <span>物流单号</span>
          <input v-model="logisticsNo" type="text" aria-label="物流单号" />
        </label>
        <label class="field">
          <span>本次数量</span>
          <input v-model="quantity" type="number" min="0" step="1" aria-label="本次发货数量" />
        </label>
        <label class="field">
          <span>本次重量（克）</span>
          <input v-model="weight" type="number" min="0" step="0.001" aria-label="本次发货重量" />
        </label>
      </div>
      <ScanInput
        v-model="logisticsScanText"
        field-text="物流扫码"
        input-aria-label="物流扫码"
        placeholder="扫快递单只填写物流单号"
        submit-label="填写物流"
        submit-aria-label="填写物流单号"
        photo-aria-label="拍物流单"
        camera-aria-label="拍物流贴纸"
        @recognize="fillLogistics"
      />
      <p v-if="logisticsHint" class="operation-feedback warning">{{ logisticsHint }}</p>
      <button class="primary-button" type="button" :disabled="shipping || shippingScan.loading" @click="confirmShip">
        {{ shipping ? '正在发货' : '确认发货' }}
      </button>
    </template>

    <div v-if="shipmentResult" class="operation-feedback success">
      <strong>发货已写入</strong>
      <span>{{ shipmentResult.shipmentCode }} / {{ shipmentResult.ledgerCode }}</span>
      <span>{{ shipmentResult.logisticsNo }}</span>
    </div>
    <p v-if="refreshError" class="operation-feedback warning">{{ refreshError }}</p>
    <button v-if="refreshError" class="secondary-button" type="button" :disabled="refreshBusy" aria-label="重试刷新" @click="refreshShipment">
      {{ refreshBusy ? '正在刷新' : '重试刷新' }}
    </button>

    <section v-if="businessKey" class="section-block">
      <h2>发货证据</h2>
      <p v-if="evidenceTargetCode">业务已保存。证据上传或关联失败时只重试文件，不会再次发货。</p>
      <p v-else>可以先选择文件。确认发货成功后才关联到真实出货单，不会把待上传文件当成已经发货。</p>
      <EvidenceCapture
        :key="businessKey"
        target-type="OUTBOUND_ORDER"
        :target-code="evidenceTargetCode"
        stage="SHIPPING"
        scene="PACK"
        operation-type="outbound_shipment"
        :operation-code="evidenceTargetCode"
      />
    </section>
  </section>
</template>
