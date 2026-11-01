<script setup lang="ts">
import { computed, inject, ref, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { fetchProductionTarget, finishBagStock, type ProductionTargetView } from '../../services/productionQualityApi';
import { fetchDelivery, openDelivery, confirmDelivery, publishDelivery, disableDeliveryLink, fetchDeliveryLabel, fetchDeliveryImpact, correctDelivery, receiveDeliveryReturn, type DeliveryDetail } from '../../services/deliveryApi';
import { uploadEvidenceFile } from '../../services/evidenceApi';
import type { LabelPayload } from '../../services/labelApi';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
import MultiSourceShippingPane from './MultiSourceShippingPane.vue';
import DeliveryAftercarePane from './DeliveryAftercarePane.vue';
import DeliveryA5Sheet from './DeliveryA5Sheet.vue';
import { buildA5Pages, renderA5Markup, type A5BagLine } from './deliveryA5';
import { printOfficeDocument } from './officePrint';
import { shippingBagMarkup, shippingFaceCss } from './shippingBagMarkup';
const props = withDefaults(defineProps<{ initialTarget?: string; initialDelivery?: string }>(), { initialTarget: '', initialDelivery: '' });
const emit = defineEmits<{ changed: [] }>();
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null)); const scope = computed(() => props.initialTarget || 'delivery-workspace');
const sourceCodes = ref(props.initialTarget); const logistics = ref(''); const remark = ref(''); const selectionJson = ref('');
const deliveryId = ref(props.initialDelivery || new URLSearchParams(window.location.search).get('delivery') || ''); const accessId = ref(''); const photoCodes = ref(''); const photoConsent = ref('');
const outbound = ref(''); const returnQuantity = ref(''); const returnWeight = ref(''); const message = ref('');
const shipCommand = useBusinessCommand('delivery-shipping', scope, { sourceCodes, logistics, remark, selectionJson }, message);
const deliveryTarget = computed(() => deliveryId.value.trim() || 'no-delivery');
const command = useBusinessCommand('delivery-action', deliveryTarget, { accessId, photoCodes, photoConsent, outbound, returnQuantity, returnWeight }, message);
const pending = computed(() => shipCommand.pending.value || command.pending.value);
const hasUnconfirmed = computed(() => shipCommand.hasUnconfirmed.value || command.hasUnconfirmed.value);
const shipRevision = ref(0);
const targets = ref<ProductionTargetView[]>([]); const detail = ref<DeliveryDetail | null>(null); const impact = ref<{ confirmedChargeRecord: string; confirmedRefundRecord: string } | null>(null);
const label = ref<LabelPayload | null>(null); const overflow = ref<string[]>([]); const lineNo = ref('');
const canShip = computed(() => session.value?.effectivePermissions?.includes('BUSINESS_OUTBOUND_SHIPMENT') === true);
const canReturn = computed(() => session.value?.effectivePermissions?.includes('BUSINESS_CUSTOMER_RETURN') === true);
const canPrint = computed(() => session.value?.effectivePermissions?.includes('LABEL_PRINT') === true);
const choices = computed(() => targets.value.flatMap(target => target.bags.map(row => ({ bagId: row.bag.id, version: row.bag.version, quantity: String(row.bag.quantity), unit: target.quantityUnit, shippable: row.shippable.quantity >= row.bag.quantity && row.shippable.blockingReasons.length === 0, reason: row.shippable.blockingReasons.join('；') }))));
const currentSource = computed(() => detail.value?.sources.find(row => row.outboundCode === outbound.value));
const returned = computed(() => detail.value?.returns.filter(row => row.outboundCode === outbound.value).reduce((sum, row) => sum + row.quantity, 0) ?? 0);
const a5Lines = computed<A5BagLine[]>(() => detail.value?.sources.map(source => {
  const line = detail.value!.lines.find(row => row.lineNo === source.lineNo)!;
  return { lineNo: source.lineNo, bagCode: `${line.deliveryId}-${line.lineNo}`, material: line.materialCode, spec: line.goodsSpecification ?? '历史未记录', quantity: String(source.quantity), unit: line.quantityUnit, weight: source.weight == null ? null : String(source.weight), requirement: line.processingSummary ?? '历史未记录', sourceCode: line.targetCode };
}) ?? []);
const labelMarkup = computed(() => label.value ? shippingBagMarkup(label.value) : '');
const publicHref = computed(() => accessId.value ? new URL(`/d/${accessId.value}`, window.location.origin).toString() : undefined);
let readSequence = 0;
async function readSources() {
  const read = ++readSequence;
  const codes = [...new Set(sourceCodes.value.split(/[\n,，]/).map(code => code.trim()).filter(Boolean))];
  if (!codes.length) { message.value = '请填写需要汇总的母单或子单号'; return; }
  try { const saved = await Promise.all(codes.map(fetchProductionTarget)); if (read === readSequence) { targets.value = saved; message.value = ''; } }
  catch (cause) { if (read === readSequence) { targets.value = []; message.value = cause instanceof Error ? cause.message : '可发袋读取失败'; } }
}
async function readDelivery() {
  const read = ++readSequence; const id = deliveryId.value.trim(); detail.value = null; label.value = null;
  try {
    const saved = await fetchDelivery(id); const influence = await fetchDeliveryImpact(id); if (read !== readSequence || id !== deliveryId.value.trim()) return;
    detail.value = saved; impact.value = influence; outbound.value = saved.sources[0]?.outboundCode ?? ''; lineNo.value = String(saved.sources[0]?.lineNo ?? '');
  } catch (cause) { if (read === readSequence) message.value = cause instanceof Error ? cause.message : '发货单读取失败'; }
}
async function ship(_request: string, bagIds: string[]) {
  const selected = targets.value.flatMap(target => target.bags.filter(row => bagIds.includes(row.bag.id)).map(row => ({ bagId: row.bag.id, bagVersion: row.bag.version, targetCode: target.targetCode, quantityUnit: target.quantityUnit })));
  const customerId = targets.value[0]?.customerId;
  if (!customerId || selected.length !== bagIds.length) throw new Error('请重新核对本次可发袋');
  selectionJson.value = JSON.stringify({ customerId, selections: selected });
  return shipStored();
}
async function shipStored(): Promise<{ deliveryId: string }> {
  if (!selectionJson.value) throw new Error('请先选择本次发货袋');
  const selected = JSON.parse(selectionJson.value) as { customerId: number; selections: { bagId: string; bagVersion: number; targetCode: string; quantityUnit: string }[] };
  const result = await shipCommand.submit('ship', { ...selected, logisticsNote: logistics.value, remark: remark.value }, async (_code, body) => {
    for (const [index, bag] of body.selections.entries()) await finishBagStock(bag.bagId, { requestId: `${body.requestId}-f${index}`, version: bag.bagVersion });
    const opened = await openDelivery({ ...body, requestId: `${body.requestId}-draft` });
    if (opened.draft.status !== 'CONFIRMED') await confirmDelivery(opened.draft.deliveryId, { requestId: `${body.requestId}-confirm`, version: opened.draft.version });
    return { deliveryId: opened.draft.deliveryId };
  });
  if (!result) throw new Error(message.value || '发货结果待核对，请重试原请求');
  deliveryId.value = result.deliveryId; await readDelivery(); message.value = detail.value ? `本次发货已确认：${result.deliveryId}` : `发货已确认：${result.deliveryId}；${message.value}`; emit('changed'); return result;
}
async function publish() {
  const photos = photoCodes.value.split(/[\s,，]+/).filter(Boolean);
  if (photos.length && photoConsent.value !== 'yes') { message.value = '请明确确认所选照片可向任何持码者公开'; return; }
  const result = await command.submit('publish', { photoIds: photos }, (id, body) => publishDelivery(id, body));
  if (result) { accessId.value = result; message.value = '本次交付扫码链接已生成；只包含明确公开的内容'; }
}
async function disable() {
  const id = deliveryId.value.trim(); const token = accessId.value;
  const result = await command.submit('disable', { version: 1 }, (_code, body) => disableDeliveryLink(id, token, body));
  if (result) { accessId.value = ''; label.value = null; message.value = '公开链接已停用；原发货事实保留'; }
}
async function previewLabel() {
  try { const saved = await fetchDeliveryLabel(deliveryId.value.trim(), Number(lineNo.value), accessId.value); label.value = saved.payload; overflow.value = saved.overflow; }
  catch (cause) { message.value = cause instanceof Error ? cause.message : '贴纸读取失败'; }
}
async function printLabel() { if (!label.value) return; try { await printOfficeDocument(label.value.primaryCode, labelMarkup.value, '40mm 30mm', shippingFaceCss); } catch (cause) { message.value = cause instanceof Error ? cause.message : '打印窗口打开失败'; } }
async function printA5() {
  if (!detail.value) return;
  const pages = buildA5Pages({ deliveryId: detail.value.draft.deliveryId, customerName: detail.value.lines[0]?.customerName ?? '历史名称未记录', logistics: detail.value.draft.logisticsNote, lines: a5Lines.value, feeAmount: null, publicHref: publicHref.value });
  try { await printOfficeDocument(detail.value.draft.deliveryId, pages.map(page => renderA5Markup({ ...page, feeText: '账单另附' })).join(''), 'A5'); }
  catch (cause) { message.value = cause instanceof Error ? cause.message : 'A5打印窗口打开失败'; }
}
async function receive(quantity: string) {
  if (!returnWeight.value.trim()) throw new Error('请填写本次退回份额的实际重量');
  returnQuantity.value = quantity;
  const result = await command.submit('return', { outboundCode: outbound.value, quantity, weight: returnWeight.value }, (id, body) => receiveDeliveryReturn(id, body));
  if (!result) throw new Error(message.value || '退货结果待核对'); await readDelivery(); emit('changed');
}
async function correct(intent: string) {
  if (!detail.value) return;
  const result = await command.submit(`correct-${intent}`, { version: detail.value.draft.version, intent }, (id, body) => correctDelivery(id, body));
  if (!result) throw new Error(message.value || '本次更正未完成'); await readDelivery();
}
async function uploadPhoto(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file || !outbound.value) return;
  try { const saved = await uploadEvidenceFile({ objectType: 'OUTBOUND_ORDER', businessCode: outbound.value, scene: 'PACK', file }); photoCodes.value = [photoCodes.value, saved.code].filter(Boolean).join('、').replace(/、/g, ','); message.value = '照片已上传；勾选明确公开后才会加入扫码摘要'; }
  catch (cause) { message.value = cause instanceof Error ? cause.message : '照片上传失败'; }
  finally { input.value = ''; }
}
async function resume() { if (shipCommand.hasUnconfirmed.value) shipCommand.restorePending(); else command.restorePending(); if (shipCommand.hasUnconfirmed.value && selectionJson.value) { try { await shipStored(); } catch (cause) { message.value = cause instanceof Error ? cause.message : '仍待核对'; } } }
function next() {
  shipCommand.startNext(); command.startNext();
  if (pending.value || hasUnconfirmed.value) return;
  shipRevision.value++; targets.value = []; selectionJson.value = ''; returnQuantity.value = ''; returnWeight.value = '';
}
if (deliveryId.value) void readDelivery();
</script>
<template>
  <section aria-label="独立发货办理" class="page-stack">
    <h3>按实际袋汇总发货</h3><p v-if="message" role="status">{{ message }}</p>
    <template v-if="canShip"><label>加工来源单号<textarea v-model="sourceCodes" placeholder="多张母单或子单逐行填写" /></label><button type="button" :disabled="pending || hasUnconfirmed" @click="readSources">读取可发袋</button><label>物流信息<input v-model="logistics" /></label><label>本次备注<input v-model="remark" /></label>
      <MultiSourceShippingPane v-if="targets.length" :key="shipRevision" :order-code="scope" :bags="choices" :confirm="ship" />
    </template>
    <label>已确认发货单<input v-model="deliveryId" :disabled="pending || hasUnconfirmed" placeholder="FH-日期-序号" /></label><button type="button" :disabled="pending" @click="readDelivery">读取本次发货</button>
    <template v-if="detail && detail.draft.status === 'CONFIRMED'">
      <p>{{ detail.lines[0]?.customerName || '历史名称未记录' }} · {{ detail.draft.deliveryId }}</p>
      <p v-for="row in detail.lines" :key="row.lineNo">袋序 {{ row.lineNo }} · {{ row.targetCode }} · {{ row.materialCode }} · {{ row.quantity }}{{ row.quantityUnit }}</p>
      <template v-if="canShip"><template v-if="session?.role === 'ADMIN'"><label>本次出库照片<input type="file" accept="image/jpeg,image/png" @change="uploadPhoto" /></label><label>明确公开的图片编号<input v-model="photoCodes" placeholder="仅本次出库照片，以逗号分开" /></label><label><input type="checkbox" :checked="photoConsent === 'yes'" @change="photoConsent = ($event.target as HTMLInputElement).checked ? 'yes' : ''" />所选照片可向任何持码者公开</label></template>
        <button type="button" :disabled="pending || hasUnconfirmed" @click="publish">生成本次交付扫码链接</button><a v-if="accessId" :href="`/d/${accessId}`" target="_blank" rel="noreferrer">查看公开摘要</a><button v-if="accessId" type="button" :disabled="pending" @click="disable">停用公开链接</button>
      </template>
      <template v-if="canPrint"><label>发货袋序<select v-model="lineNo"><option v-for="row in detail.sources" :key="row.lineNo" :value="String(row.lineNo)">{{ row.lineNo }}</option></select></label><button type="button" :disabled="!accessId" @click="previewLabel">预览40×30发货袋贴纸</button>
        <p v-for="warning in overflow" :key="warning">{{ warning }}</p><p v-if="label">贴纸为固定字号摘要，完整加工要求和来源见扫码页及A5。打印窗口请选择40×30标签打印机。</p><div v-if="label" class="shipping-preview"><component :is="'style'">{{ shippingFaceCss }}</component><div v-html="labelMarkup"></div><button type="button" @click="printLabel">用40×30标签打印机打印</button></div>
        <p v-if="!accessId">生成本次交付扫码链接后，A5汇总单会带整单二维码。</p>
        <DeliveryA5Sheet :delivery-id="detail.draft.deliveryId" :customer-name="detail.lines[0]?.customerName || '历史名称未记录'" :logistics="detail.draft.logisticsNote" :lines="a5Lines" :fee-amount="null" fee-text="账单另附" :public-href="publicHref" :office-printers="['系统办公打印窗口（选择A5打印机）']" @print="printA5" />
      </template>
      <template v-if="canReturn"><label>原出库行<select v-model="outbound"><option v-for="row in detail.sources" :key="row.outboundCode" :value="row.outboundCode">{{ row.outboundCode }} · {{ row.quantity }}</option></select></label><label>本次退回实重（克）<input v-model="returnWeight" inputmode="decimal" /></label><p>费用状态：{{ impact?.confirmedChargeRecord || '待核' }} · 退款状态：{{ impact?.confirmedRefundRecord || '待核' }}</p>
        <DeliveryAftercarePane v-if="currentSource" v-model:quantity-draft="returnQuantity" :key="outbound" :delivery-id="detail.draft.deliveryId" :original-quantity="String(currentSource.quantity)" :returned-quantity="String(returned)" :has-charge="impact?.confirmedChargeRecord === '已确认'" :has-refund="impact?.confirmedRefundRecord === '已确认'" :correct="correct" :receive="receive" :disabled="pending" />
      </template>
    </template>
    <button v-if="hasUnconfirmed" type="button" @click="resume">恢复原请求并核对结果</button><button type="button" :disabled="pending || hasUnconfirmed" @click="next">开始下一笔发货或退货</button>
  </section>
</template>
