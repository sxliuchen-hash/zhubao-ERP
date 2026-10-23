<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import type { InventoryBalance } from '../../data/m1PrototypeData';
import { ApiRequestError } from '../../services/apiClient';
import { returnCustomerShipment, reviewAfterSalesReturn, type CustomerReturnResult, type AfterSalesReviewResult } from '../../services/businessOperationApi';
import { createAmountForm } from '../m1/operationAmounts';
import { fetchInventoryWorkspace } from '../../services/businessWorkspaceApi';
import { operationDraft } from '../m1/operationDraft';
import OperationInputs from '../m1/OperationInputs.vue';
import StockLedgerPanel from './StockLedgerPanel.vue';

const props = defineProps<{ row: InventoryBalance; active?: boolean }>();
const emit = defineEmits<{ saved: []; newRequest: [row: InventoryBalance] }>();
const row: InventoryBalance = JSON.parse(JSON.stringify(props.row));
const context = row.operationContext;
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
type Mode = 'return' | 'review';
const mode = ref<Mode>(context?.stockStatus === 'SHIPPED' ? 'return' : 'review');
const returnForm = createAmountForm();
const reviewForm = createAmountForm();
returnForm.resetAmounts(context); reviewForm.resetAmounts(context);
const returnValues = returnForm.operationValues;
const reviewValues = reviewForm.operationValues;
const returnResult = ref<CustomerReturnResult | null>(null);
const reviewResult = ref<AfterSalesReviewResult | null>(null);
const error = ref('');
const busy = ref(false);
const ledgerCreated = ref(false);
const ledgerOpen = ref(false);
const ledgerDocument = ref('');
const nextBusy = ref(false);
const nextError = ref('');
type Command = { mode: Mode; values: ReturnType<typeof operationDraft>; documentCode: string };
const unknown = ref<Command | null>(null);
let disposed = false;
onBeforeUnmount(() => { disposed = true; });
function allowed(kind: Mode) {
  const account = session.value;
  if (!account) return true;
  if (account.effectivePermissions) return account.effectivePermissions.includes(kind === 'return' ? 'BUSINESS_CUSTOMER_RETURN' : 'BUSINESS_AFTER_SALES_REVIEW');
  if (['ADMIN', 'GENERAL_MANAGER'].includes(account.role)) return true;
  if (account.role === 'BUSINESS_MANAGER') return account.coverDuties?.includes(kind === 'return' ? 'RETURN' : 'QUALITY') === true;
  return (kind === 'return' ? ['WAREHOUSE_KEEPER', 'SALES', 'QUALITY_INSPECTOR'] : ['QUALITY_INSPECTOR', 'WORKSHOP_LEADER']).includes(account.role);
}
const canReturn = computed(() => context?.ownerType === 'CUSTOMER' && context.stockStatus === 'SHIPPED' && allowed('return'));
const canReview = computed(() => context?.ownerType === 'CUSTOMER' && context.stockStatus === 'FINISHED' && allowed('review'));
function showLedger(documentCode: string) { ledgerDocument.value = documentCode; ledgerCreated.value = true; ledgerOpen.value = !ledgerOpen.value; }
function dimensionKey(value: InventoryBalance) {
  const dimension = value.operationContext;
  return dimension ? JSON.stringify([dimension.customerId, dimension.ownerCustomerId, dimension.ownerType, dimension.motherOrderCode, dimension.workOrderCode, dimension.materialCode, dimension.warehouseCode, dimension.stockStatus]) : '';
}
async function nextRequest() {
  if (nextBusy.value || unknown.value || (!returnResult.value && !reviewResult.value)) return;
  nextBusy.value = true; nextError.value = '';
  try {
    const loaded = await fetchInventoryWorkspace();
    if (disposed) return;
    const current = loaded.balances.find(balance => dimensionKey(balance) === dimensionKey(row));
    if (!current) { nextError.value = '原库存维度已不存在或当前不可见，请回库存列表重新选择；本次回执保留。'; return; }
    emit('newRequest', current);
  } catch (caught) { if (!disposed) nextError.value = caught instanceof Error ? caught.message : '重新读取余额失败，本次回执保留'; }
  finally { nextBusy.value = false; }
}
async function submit(kind: Mode) {
  if (busy.value || unknown.value || (kind === 'return' ? !canReturn.value || returnResult.value : !canReview.value || reviewResult.value)) return;
  error.value = '';
  const form = kind === 'return' ? returnForm : reviewForm;
  try { form.assertAmounts(); }
  catch (caught) { error.value = caught instanceof Error ? caught.message : '请核对数量和重量'; return; }
  const values = JSON.parse(JSON.stringify(operationDraft(form.operationValues.value))) as ReturnType<typeof operationDraft>;
  const required = kind === 'return' ? ['originalShipmentCode', 'returnCode', 'returnLogisticsNo', 'reason'] : ['originalShipmentCode', 'returnCode', 'reviewCode', 'disposition', 'finding'];
  if (required.some(key => !String((values as Record<string, unknown>)[key] ?? '').trim())) { error.value = '请补齐本次办理的业务单号及必填说明'; return; }
  const command: Command = { mode: kind, values, documentCode: String(kind === 'return' ? values.returnCode : values.reviewCode) };
  busy.value = true;
  try {
    if (kind === 'return') {
      const result = await returnCustomerShipment(row, values);
      if (!result?.ledgerCode || result.returnCode !== command.documentCode || result.workOrderCode !== context?.workOrderCode) throw new Error('退货回执与本单不一致，请保留原单号核对台账');
      if (!disposed) returnResult.value = result;
    } else {
      const result = await reviewAfterSalesReturn(row, values);
      if (!result?.ledgerCode || result.reviewCode !== command.documentCode || result.workOrderCode !== context?.workOrderCode) throw new Error('售后回执与本单不一致，请保留原单号核对台账');
      if (!disposed) reviewResult.value = result;
    }
    if (!disposed) emit('saved');
  } catch (caught) {
    if (disposed) return;
    const known = caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408;
    if (!known) unknown.value = command;
    error.value = caught instanceof Error ? caught.message : '退售后办理失败';
  } finally { busy.value = false; }
}
</script>

<template>
  <section class="aftercare-panel page-stack" aria-label="库存退货与售后复核" :inert="active === false || undefined">
    <h2>退货与售后复核</h2>
    <p>{{ row.owner }} · 母单 {{ context?.motherOrderCode }} · 工单 {{ context?.workOrderCode }} · 货主 {{ context?.ownerCustomerId ?? '未记录' }} · {{ context?.materialCode }} · {{ context?.warehouseCode }} · {{ context?.stockStatus }}</p>
    <p>原选余额 {{ row.qty }} {{ row.quantityUnit || '数量单位未提供' }} / {{ context?.weight }} 克。修改本次数量后请重新确认重量。</p>
    <nav class="button-row" aria-label="退售后办理方式"><button v-if="canReturn" class="secondary-button" type="button" :aria-pressed="mode === 'return'" @click="mode = 'return'">退货入库</button><button v-if="canReview" class="secondary-button" type="button" :aria-pressed="mode === 'review'" @click="mode = 'review'">售后复核</button></nav>
    <p v-if="!canReturn && !canReview">当前库存状态或有效权限不支持办理，请核对原出库、退货单和库存来源。</p>
    <p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p>
    <p v-if="unknown" class="operation-feedback warning" role="status">原{{ unknown.mode === 'return' ? '退货' : '售后复核' }} {{ unknown.documentCode }} 结果待核对，原内容已保留。此处暂停提交，请先核对原单台账，避免重复入库。<button class="secondary-button" type="button" @click="showLedger(unknown.documentCode)">核对原单台账</button></p>
    <fieldset v-show="mode === 'return' && canReturn" :disabled="busy || Boolean(unknown) || Boolean(returnResult)">
      <legend>退货入库</legend>
      <OperationInputs v-model="returnValues" :fields="[['originalShipmentCode', '原出库单号'], ['returnCode', '退货单号'], ['returnLogisticsNo', '退货运单号'], ['reason', '退货原因'], ['quantity', '本次数量', 'number'], ['weight', '本次重量（克）', 'number'], ['evidenceCode', '证据编号']]" />
      <p v-if="returnForm.amountHint.value">{{ returnForm.amountHint.value }}</p><button class="primary-button" type="button" @click="submit('return')">{{ busy ? '提交中…' : '提交退货入库' }}</button>
    </fieldset>
    <fieldset v-show="mode === 'review' && canReview" :disabled="busy || Boolean(unknown) || Boolean(reviewResult)">
      <legend>售后复核</legend><p>核对原退货库存和证据，再明确选择放行、返工、报废或冻结；不自动沿用初次质检结论。</p>
      <OperationInputs v-model="reviewValues" :fields="[['originalShipmentCode', '复核原出库单号'], ['returnCode', '复核退货单号'], ['reviewCode', '售后复核单号'], ['disposition', '处理结论'], ['finding', '复核说明'], ['quantity', '复核数量', 'number'], ['weight', '复核重量（克）', 'number'], ['evidenceCode', '复核证据编号']]" />
      <p v-if="reviewForm.amountHint.value">{{ reviewForm.amountHint.value }}</p><button class="primary-button" type="button" @click="submit('review')">{{ busy ? '提交中…' : '提交售后复核' }}</button>
    </fieldset>
    <p v-if="returnResult" class="operation-feedback success" role="status">客户退货已写入台账：{{ returnResult.returnCode }} / {{ returnResult.ledgerCode }} · {{ returnResult.quantity }} {{ row.quantityUnit || '数量单位未提供' }} / {{ returnResult.weight }} 克</p>
    <p v-if="reviewResult" class="operation-feedback success" role="status">售后复核已写入台账：{{ reviewResult.reviewCode }} / {{ reviewResult.ledgerCode }} · {{ reviewResult.disposition }} · {{ reviewResult.quantity }} {{ row.quantityUnit || '数量单位未提供' }} / {{ reviewResult.weight }} 克</p>
    <button v-if="returnResult || reviewResult" class="secondary-button" type="button" @click="showLedger(returnResult?.returnCode || reviewResult?.reviewCode || '')">核对本次台账</button>
    <button v-if="returnResult || reviewResult" class="secondary-button" type="button" :disabled="nextBusy" @click="nextRequest">{{ nextBusy ? '正在读取余额…' : '重新读取余额，办理另一笔' }}</button>
    <p v-if="nextError" class="operation-feedback warning" role="alert">{{ nextError }}</p>
    <StockLedgerPanel v-if="ledgerCreated" v-show="ledgerOpen" :key="ledgerDocument" :initial-document-code="ledgerDocument" />
  </section>
</template>

<style scoped>
.aftercare-panel { min-width: 0; overflow-wrap: anywhere; }
h2, p { margin: 0; }
fieldset { margin: 0; min-width: 0; border: 1px solid var(--line); border-radius: 10px; padding: 12px; }
button { min-height: 44px; }
</style>
