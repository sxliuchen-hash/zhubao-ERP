<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, provide, ref, shallowRef, watch } from 'vue';
import WorkspaceLoadState from '../../components/WorkspaceLoadState.vue';
import type { MobileIntakeResult } from '../../services/mobileIntakeApi';
import { intakeDraftKey, type OrderActionTarget, type OrderChangeTarget, type ProductionMode, type ShippingActionTarget } from './orderActions';
import type { ScanBalanceCandidate } from '../../services/scanContextApi';
import type { InventoryBalance } from '../../data/m1PrototypeData';
import { openMobileScanTarget } from '../scan/mobileScanBridge';
import { validBillContext, type BillContext, type BillMode } from '../finance/billContext';

const props = withDefaults(defineProps<{ canIntake: boolean; canProduction?: boolean; canChange?: boolean; canQuality?: boolean; canShipping?: boolean; canAftercare?: boolean; canLabel?: boolean; canBill?: boolean }>(), { canProduction: false, canChange: false, canQuality: false, canShipping: false, canAftercare: false, canLabel: false, canBill: false });
const emit = defineEmits<{ active: [open: boolean]; saved: [result: MobileIntakeResult]; changed: [] }>();
function intakeComponent() {
  return defineAsyncComponent({ loader: () => import('./IntakeActionPane.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 });
}
const IntakeActionPane = shallowRef(intakeComponent());
function productionComponent() { return defineAsyncComponent({ loader: () => import('./ProductionActionPane.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 }); }
function changeComponent() { return defineAsyncComponent({ loader: () => import('./OrderChangePanel.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 }); }
const ProductionActionPane = shallowRef(productionComponent());
function qualityComponent() { return defineAsyncComponent({ loader: () => import('./QualityActionPane.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 }); }
const QualityActionPane = shallowRef(qualityComponent());
function shippingComponent() { return defineAsyncComponent({ loader: () => import('./ShippingActionPane.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 }); }
const ShippingActionPane = shallowRef(shippingComponent());
function aftercareComponent() { return defineAsyncComponent({ loader: () => import('../inventory/InventoryAftercarePanel.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 }); }
const InventoryAftercarePanel = shallowRef(aftercareComponent());
const OrderChangePanel = shallowRef(changeComponent());
function locatorComponent() { return defineAsyncComponent({ loader: () => import('./OrderActionLocator.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 }); }
const OrderActionLocator = shallowRef(locatorComponent());
function labelComponent() { return defineAsyncComponent({ loader: () => import('../labels/OrderLabelPanel.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 }); }
const OrderLabelPanel = shallowRef(labelComponent());
function billComponent() { return defineAsyncComponent({ loader: () => import('../finance/CustomerBillPanel.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 }); }
const CustomerBillPanel = shallowRef(billComponent());

type ActionKind = 'intake' | 'production' | 'change' | 'quality' | 'shipping' | 'aftercare' | 'label' | 'bill';
const activeKind = ref<ActionKind>('intake');
provide('zhubao-retry-workspace', () => {
  if (activeKind.value === 'intake') IntakeActionPane.value = intakeComponent();
  else if (['production', 'quality', 'shipping'].includes(activeKind.value) && !target.value?.objectType) OrderActionLocator.value = locatorComponent();
  else if (activeKind.value === 'production') ProductionActionPane.value = productionComponent();
  else if (activeKind.value === 'quality') QualityActionPane.value = qualityComponent();
  else if (activeKind.value === 'shipping') ShippingActionPane.value = shippingComponent();
  else if (activeKind.value === 'aftercare') InventoryAftercarePanel.value = aftercareComponent();
  else if (activeKind.value === 'label') OrderLabelPanel.value = labelComponent();
  else if (activeKind.value === 'bill') CustomerBillPanel.value = billComponent();
  else OrderChangePanel.value = changeComponent();
});
const open = ref(false);
let previousBodyOverflow: string | null = null;
function restoreBodyScroll() {
  if (previousBodyOverflow === null) return;
  document.body.style.overflow = previousBodyOverflow;
  previousBodyOverflow = null;
}
watch(open, value => {
  if (!value) { restoreBodyScroll(); return; }
  if (previousBodyOverflow === null) previousBodyOverflow = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
}, { flush: 'sync' });
const target = ref<OrderActionTarget | null>(null);
const draftKey = ref('intake:new');
let aftercareAttempt = 0;
interface ActionDraft { key: string; kind: ActionKind; target: OrderActionTarget | null; motherOrderCode?: string; balance?: ScanBalanceCandidate; inventoryRow?: InventoryBalance; mode: ProductionMode; entryRevision: number; billContext?: BillContext; billMode?: BillMode }
const drafts = ref<ActionDraft[]>([]);
const title = computed(() => ({ intake: '收货办理', production: '工序作业', change: '单据改单', quality: '质量作业', shipping: '发货办理', aftercare: '退货与售后复核', label: '单据标签', bill: '客户账单与收款' }[activeKind.value]));
const needsScanner = computed(() => activeKind.value === 'intake' || (activeKind.value === 'label' && !target.value?.objectType) || (['production', 'quality', 'shipping'].includes(activeKind.value) && !target.value?.objectType));
function allowed(kind: ActionKind) { return kind === 'intake' ? props.canIntake : kind === 'production' ? props.canProduction : kind === 'quality' ? props.canQuality : kind === 'shipping' ? props.canShipping : kind === 'aftercare' ? props.canAftercare : kind === 'label' ? props.canLabel : kind === 'bill' ? props.canBill : props.canChange; }
const panel = ref<HTMLElement | null>(null);
const closeButton = ref<HTMLButtonElement | null>(null);
let opener: HTMLElement | null = null;
provide('zhubao-scan-suspended', ref(false));

function openIntake(input: OrderActionTarget | null = null) {
  openDraft('intake', input);
}
function workTarget(input: OrderActionTarget) { return input.code.trim() && (input.objectType === null || ['MOTHER_ORDER', 'WORK_ORDER'].includes(input.objectType)); }
function openLabel(input: OrderActionTarget | null = null) { if (!input || workTarget(input)) openDraft('label', input); }
function openBill(context: BillContext, mode: BillMode = 'statement') {
  if (!props.canBill || !validBillContext(context)) return;
  reveal({ kind: 'bill', key: 'bill:' + context.customerId + ':' + context.yearMonth, target: { objectType: null, code: String(context.customerId) }, billContext: { ...context }, billMode: mode, mode: 'progress', entryRevision: 1 });
}
function openProduction(input: OrderActionTarget | null = null, mode: ProductionMode = 'progress') {
  if (!input || workTarget(input)) openDraft('production', input, mode);
}
function openOrderChange(input: OrderChangeTarget) {
  if (workTarget(input) && input.motherOrderCode.trim()) openDraft('change', input, 'progress', input.motherOrderCode.trim());
}
function openQuality(input: OrderActionTarget | null = null) {
  if (!input || workTarget(input) || (input.objectType === 'REWORK' && input.code.trim())) openDraft('quality', input);
}
function openShipping(input: ShippingActionTarget | null = null) {
  if (!input || workTarget(input) || (input.objectType === 'OUTBOUND_ORDER' && input.code.trim())) openDraft('shipping', input, 'progress', undefined, input?.balance);
}
function stockIdentity(balance: ScanBalanceCandidate) { return JSON.stringify([balance.customerId, balance.ownerCustomerId, balance.ownerType, balance.motherOrderCode, balance.workOrderCode, balance.materialCode, balance.warehouseCode, balance.stockStatus]); }
function openAftercare(row: InventoryBalance, newAttempt = false) {
  const context = row.operationContext;
  if (!context || context.ownerType !== 'CUSTOMER' || !context.customerId || !context.motherOrderCode || !context.workOrderCode || !context.materialCode || !context.warehouseCode) return;
  openDraft('aftercare', { objectType: null, code: context.workOrderCode }, 'progress', undefined, context, row, newAttempt);
}
function openDraft(kind: ActionKind, input: OrderActionTarget | null, mode: ProductionMode = 'progress', motherOrderCode?: string, balance?: ScanBalanceCandidate, inventoryRow?: InventoryBalance, newAttempt = false) {
  if (!allowed(kind)) return;
  const selected = input?.code.trim() ? { objectType: input.objectType, code: input.code.trim() } : null;
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  target.value = selected;
  activeKind.value = kind;
  draftKey.value = kind === 'intake' ? intakeDraftKey(selected) : `${kind}:${selected?.objectType ?? 'AUTO'}:${selected?.code}`;
  if ((kind === 'shipping' || kind === 'aftercare') && balance) draftKey.value += `:${stockIdentity(balance)}`;
  if (kind === 'aftercare') {
    const baseKey = draftKey.value;
    if (newAttempt) draftKey.value += `:attempt-${++aftercareAttempt}`;
    else draftKey.value = drafts.value.filter(draft => draft.key === baseKey || draft.key.startsWith(`${baseKey}:attempt-`)).at(-1)?.key ?? baseKey;
  }
  reveal({ key: draftKey.value, kind, target: selected, motherOrderCode, ...(balance ? { balance: { ...balance } } : {}), ...(inventoryRow ? { inventoryRow: JSON.parse(JSON.stringify(inventoryRow)) as InventoryBalance } : {}), mode, entryRevision: 1 });
}
function reveal(draft: ActionDraft) {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  target.value = draft.target; activeKind.value = draft.kind; draftKey.value = draft.key;
  const existing = drafts.value.find(item => item.key === draft.key);
  if (existing) { existing.mode = draft.mode; existing.entryRevision += 1; if (draft.billContext) { existing.billContext = draft.billContext; existing.billMode = draft.billMode; } }
  else drafts.value.push(draft);
  open.value = true;
  emit('active', true);
  void nextTick(() => closeButton.value?.focus());
}
async function close() {
  open.value = false;
  emit('active', false);
  await nextTick();
  if (opener?.isConnected) opener.focus();
}
function keydown(event: KeyboardEvent) {
  if (!open.value) return;
  if (event.key === 'Escape') { event.preventDefault(); void close(); return; }
  if (event.key !== 'Tab') return;
  const controls = Array.from(panel.value?.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex="0"]') ?? [])
    .filter(element => element.getClientRects().length > 0);
  const first = controls[0];
  const last = controls.at(-1);
  if (!first || !last) return;
  if (event.shiftKey && (document.activeElement === first || !panel.value?.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
}
watch([() => props.canIntake, () => props.canProduction, () => props.canChange, () => props.canQuality, () => props.canShipping, () => props.canAftercare, () => props.canLabel, () => props.canBill], () => { if (!allowed(activeKind.value) && open.value) void close(); });
onMounted(() => window.addEventListener('keydown', keydown));
onBeforeUnmount(() => { window.removeEventListener('keydown', keydown); restoreBodyScroll(); emit('active', false); });
defineExpose({ openIntake, openProduction, openOrderChange, openQuality, openShipping, openAftercare, openLabel, openBill });
</script>

<template>
  <Teleport to="body">
    <div v-show="open" class="order-action-overlay">
      <section ref="panel" class="order-action-dialog" role="dialog" aria-modal="true" :aria-label="title">
        <header>
          <div><h2>{{ title }}</h2><p>{{ activeKind === 'bill' ? `客户编号 ${target?.code}` : target ? `定位单据 ${target.code}` : activeKind === 'intake' ? '新建收货草稿' : '扫码选择作业单据' }} · 收起后保留本次填写和请求</p></div>
          <div class="order-action-top-actions">
            <button ref="closeButton" class="secondary-button" type="button" @click="close">收起表单</button>
            <button v-if="needsScanner" class="secondary-button order-action-scan" aria-label="当前表单扫码" type="button" @click="openMobileScanTarget">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 4H4v4M16 4h4v4M4 16v4h4M20 16v4h-4M7 12h10" /></svg>
            </button>
          </div>
        </header>
        <div class="order-action-content">
          <div v-for="draft in drafts" :key="draft.key" v-show="open && allowed(draft.kind) && draft.key === draftKey" :data-action-active="open && allowed(draft.kind) && draft.key === draftKey" :inert="!(open && allowed(draft.kind) && draft.key === draftKey) || undefined">
            <component :is="IntakeActionPane" v-if="draft.kind === 'intake'" :target="draft.target" :active="open && allowed(draft.kind) && draft.key === draftKey" @saved="emit('saved', $event)" @navigate="close" />
            <component :is="ProductionActionPane" v-else-if="draft.kind === 'production' && draft.target?.objectType" :target="draft.target" :active="open && allowed(draft.kind) && draft.key === draftKey" :initial-mode="draft.mode" :entry-revision="draft.entryRevision" @changed="emit('changed')" @selected="openProduction" />
            <component :is="QualityActionPane" v-else-if="draft.kind === 'quality' && draft.target?.objectType" :target="draft.target" :active="open && allowed(draft.kind) && draft.key === draftKey" @changed="emit('changed')" @selected="openQuality" />
            <component :is="ShippingActionPane" v-else-if="draft.kind === 'shipping' && draft.target?.objectType" :target="draft.target" :balance="draft.balance" :active="open && allowed(draft.kind) && draft.key === draftKey" @changed="emit('changed')" @selected="openShipping" />
            <component :is="InventoryAftercarePanel" v-else-if="draft.kind === 'aftercare' && draft.inventoryRow" :row="draft.inventoryRow" :active="open && allowed(draft.kind) && draft.key === draftKey" @saved="emit('changed')" @new-request="row => openAftercare(row, true)" />
            <component :is="OrderLabelPanel" v-else-if="draft.kind === 'label'" :initial-target="draft.target" :active="open && allowed(draft.kind) && draft.key === draftKey" @selected="openLabel" />
            <component :is="CustomerBillPanel" v-else-if="draft.kind === 'bill' && draft.billContext" :context="draft.billContext" :initial-mode="draft.billMode" :entry-revision="draft.entryRevision" />
            <component :is="OrderActionLocator" v-else-if="['production', 'quality', 'shipping'].includes(draft.kind)" :initial-target="draft.target" :active="open && allowed(draft.kind) && draft.key === draftKey" :allowed-types="draft.kind === 'quality' ? ['MOTHER_ORDER', 'WORK_ORDER', 'REWORK'] : draft.kind === 'shipping' ? ['MOTHER_ORDER', 'WORK_ORDER', 'OUTBOUND_ORDER'] : ['MOTHER_ORDER', 'WORK_ORDER']" @selected="value => draft.kind === 'quality' ? openQuality(value) : draft.kind === 'shipping' ? openShipping({ ...value, ...(draft.balance ? { balance: draft.balance } : {}) }) : openProduction(value, draft.mode)" />
            <component :is="OrderChangePanel" v-else-if="draft.target && draft.motherOrderCode" :target="{ orderCode: draft.target.code, motherOrderCode: draft.motherOrderCode }" @changed="emit('changed')" />
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.order-action-overlay { position: fixed; inset: 0; z-index: 8000; display: grid; place-items: center; padding: 20px; background: #15272399; }
.order-action-dialog { display: flex; flex-direction: column; width: min(1100px, 100%); max-height: calc(100dvh - 40px); min-width: 0; background: var(--workspace); color: var(--ink); border-radius: 14px; overflow: hidden; box-shadow: 0 16px 60px #0003; }
.order-action-dialog > header { display: flex; gap: 16px; justify-content: space-between; align-items: start; flex: none; padding: 16px 20px; background: var(--content); border-bottom: 1px solid var(--line); }
.order-action-dialog h2 { margin: 0; font-size: 18px; }
.order-action-dialog p { margin: 6px 0 0; color: var(--muted); font-size: 12px; }
.order-action-top-actions { display: flex; gap: 8px; flex: none; }
.order-action-dialog > header > div:first-child { min-width: 0; overflow-wrap: anywhere; }
.order-action-top-actions button { min-height: 44px; }
.order-action-scan { display: none; }
.order-action-scan svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.order-action-content { overflow-y: auto; overscroll-behavior: contain; padding: 20px; min-width: 0; }
@media (max-width: 900px) { .order-action-scan { display: grid; width: 44px; place-items: center; padding: 0; } .order-action-overlay { padding: 0; } .order-action-dialog { width: 100%; height: 100dvh; max-height: 100dvh; border-radius: 0; } .order-action-dialog > header { padding: calc(12px + env(safe-area-inset-top)) 12px 12px; } .order-action-dialog > header p, .order-action-content :deep(.section-heading), .order-action-content :deep(.compact-detail-grid), .order-action-content :deep(.quality-inspector-note) { display: none; } .order-action-content { padding: 12px 12px calc(12px + env(safe-area-inset-bottom)); } }
</style>
