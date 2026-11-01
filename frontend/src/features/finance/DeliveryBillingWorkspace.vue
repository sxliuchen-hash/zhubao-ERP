<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import DeliveryChargePane, { type DeliveryChargeDraftLine, type DeliveryChargeSaved } from './DeliveryChargePane.vue';
import { fetchDeliveryBill, saveDeliveryBill, type BillContext } from '../../services/deliveryFinanceApi';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
const props = defineProps<{ deliveryId: string }>();
const key = computed(() => props.deliveryId); const message = ref(''); const context = ref<BillContext | null>(null);
const draftJson = ref(''); const coveredCode = ref(''); const coveredAmount = ref(''); const loss = ref(''); const completed = ref(false); const billAction = ref('');
const command = useBusinessCommand('delivery-bill', key, { draftJson, coveredCode, coveredAmount, loss, billAction }, message);
const { pending, hasUnconfirmed } = command; let sequence = 0;
const text = (value: number | null | undefined) => value == null ? '' : String(value);
const saved = computed<DeliveryChargeSaved | null>(() => draftJson.value ? JSON.parse(draftJson.value) as DeliveryChargeSaved : null);
const sources = computed(() => context.value?.lines.map(line => `${line.targetCode}：${line.quantity}${line.quantityUnit}`).join('；') ?? '');
const actionLabel = computed(() => hasUnconfirmed.value ? '重试原账单办理' : context.value?.bill ? '确认调整并追加差额' : '确认本次费用并入账');
watch(key, () => { context.value = null; completed.value = false; void load(); }, { immediate: true });
async function load() {
  const read = ++sequence; const id = key.value;
  try {
    const view = await fetchDeliveryBill(id); if (read !== sequence) return; context.value = view;
    if (!draftJson.value) {
      const lines: DeliveryChargeDraftLine[] = view.billLines.length ? view.billLines.map(line => ({ name: line.itemName, targetCode: line.targetCode, quantityUnit: line.quantityUnit, method: line.method as DeliveryChargeDraftLine['method'], quantity: text(line.inputQuantity), unitPrice: text(line.unitPrice), baseQuantity: text(line.baseQuantity), exemptQuantity: text(line.exemptQuantity), directAmount: text(line.directAmount), direction: (line.direction ?? '') as DeliveryChargeDraftLine['direction'], basis: line.basis }))
        : view.lines.map(line => ({ name: '加工费', targetCode: line.targetCode, quantityUnit: line.quantityUnit, method: 'QUANTITY', quantity: String(line.quantity), unitPrice: '', baseQuantity: '', exemptQuantity: '', directAmount: '', direction: '', basis: '' }));
      draftJson.value = JSON.stringify({ deliveryId: id, version: (view.bill?.version ?? 0) + 1, lines });
    }
  } catch (cause) { if (read === sequence) message.value = cause instanceof Error ? cause.message : '账单依据读取失败'; }
}
async function submit(payload: DeliveryChargeSaved) {
  if (!context.value || completed.value) return;
  draftJson.value = JSON.stringify(payload);
  const nullable = (value: string) => value.trim() || null;
  const lines = payload.lines.map(line => ({ ...line, targetCode: line.targetCode ?? '', quantityUnit: line.quantityUnit ?? '', quantity: nullable(line.quantity), unitPrice: nullable(line.unitPrice), baseQuantity: nullable(line.baseQuantity), exemptQuantity: nullable(line.exemptQuantity), directAmount: nullable(line.directAmount), direction: line.direction || null }));
  const action = hasUnconfirmed.value ? billAction.value as 'confirmations' | 'adjustments' : context.value.bill ? 'adjustments' : 'confirmations';
  billAction.value = action;
  const result = await command.submit(action, { lines, lossQuantity: nullable(loss.value), coveredAmount: nullable(coveredAmount.value), coveredReceivableCode: coveredCode.value }, (id, body) => saveDeliveryBill(id, action, body));
  if (result) { completed.value = true; message.value = `账单已确认：${result.billId}，金额${result.totalAmount}元`; }
}
async function next() { command.startNext(); if (pending.value || hasUnconfirmed.value) return; draftJson.value = ''; completed.value = false; await load(); }
</script>
<template>
  <section aria-label="按本次发货出账">
    <p v-if="message" role="status">{{ message }}</p>
    <template v-if="context">
      <p>本次实发来源：{{ sources }}</p><p v-if="context.bill">已有账单 {{ context.bill.billId }}；后续确认追加调整，保留原账。</p>
      <label>损耗参考数量<input v-model="loss" placeholder="未核定留空，不自动扣费" /></label>
      <template v-if="!context.bill"><label>覆盖已有应收编号<input v-model="coveredCode" /></label><label>本次覆盖金额<input v-model="coveredAmount" placeholder="无旧账覆盖留空" /></label></template>
      <DeliveryChargePane :delivery-id="deliveryId" :customer-name="context.lines[0]?.customerName || '历史名称未记录'" :shipped-quantity="sources" quantity-unit="" :saved="saved" :disabled="pending || completed || context.draft.status !== 'CONFIRMED'" :action-label="actionLabel" @save="draftJson = JSON.stringify($event); message = '草稿已在页面会话中保留，尚未入账'" @confirm="submit" />
      <button v-if="hasUnconfirmed" type="button" @click="command.restorePending">恢复上次填写后重试</button>
      <button type="button" :disabled="pending || hasUnconfirmed" @click="next">开始下一笔调整</button>
    </template>
  </section>
</template>
