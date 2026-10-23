<script setup lang="ts">
import { defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, provide, ref, shallowRef, watch } from 'vue';
import WorkspaceLoadState from '../../components/WorkspaceLoadState.vue';
import type { MobileIntakeResult } from '../../services/mobileIntakeApi';
import { intakeDraftKey, type OrderActionTarget } from './orderActions';

const props = defineProps<{ canIntake: boolean }>();
const emit = defineEmits<{ active: [open: boolean]; saved: [result: MobileIntakeResult] }>();
function intakeComponent() {
  return defineAsyncComponent({ loader: () => import('./IntakeActionPane.vue').then(module => module.default), loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, timeout: 30_000 });
}
const IntakeActionPane = shallowRef(intakeComponent());
provide('zhubao-retry-workspace', () => { IntakeActionPane.value = intakeComponent(); });
const open = ref(false);
const target = ref<OrderActionTarget | null>(null);
const draftKey = ref('intake:new');
const drafts = ref<Array<{ key: string; target: OrderActionTarget | null }>>([]);
const panel = ref<HTMLElement | null>(null);
const closeButton = ref<HTMLButtonElement | null>(null);
let opener: HTMLElement | null = null;
provide('zhubao-scan-suspended', ref(false));

function openIntake(input: OrderActionTarget | null = null) {
  if (!props.canIntake) return;
  const selected = input?.code.trim() ? { objectType: input.objectType, code: input.code.trim() } : null;
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  target.value = selected;
  draftKey.value = intakeDraftKey(selected);
  if (!drafts.value.some(draft => draft.key === draftKey.value)) drafts.value.push({ key: draftKey.value, target: selected });
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
watch(() => props.canIntake, allowed => { if (!allowed && open.value) void close(); });
onMounted(() => window.addEventListener('keydown', keydown));
onBeforeUnmount(() => { window.removeEventListener('keydown', keydown); emit('active', false); });
defineExpose({ openIntake });
</script>

<template>
  <Teleport to="body">
    <div v-show="open" class="order-action-overlay">
      <section ref="panel" class="order-action-dialog" role="dialog" aria-modal="true" aria-label="收货办理">
        <header><div><h2>收货办理</h2><p>{{ target ? `定位单据 ${target.code}` : '新建收货草稿' }} · 收起后保留本次填写和请求</p></div><button ref="closeButton" class="secondary-button" type="button" @click="close">收起表单</button></header>
        <div class="order-action-content">
          <div v-for="draft in drafts" :key="draft.key" v-show="open && canIntake && draft.key === draftKey" :data-action-active="open && canIntake && draft.key === draftKey">
            <component :is="IntakeActionPane" :target="draft.target" :active="open && canIntake && draft.key === draftKey" @saved="emit('saved', $event)" @navigate="close" />
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
.order-action-dialog > header button { min-height: 44px; flex: none; }
.order-action-content { overflow-y: auto; overscroll-behavior: contain; padding: 20px; min-width: 0; }
@media (max-width: 900px) { .order-action-overlay { padding: 0; } .order-action-dialog { width: 100%; height: 100dvh; max-height: 100dvh; border-radius: 0; } .order-action-dialog > header { padding: calc(12px + env(safe-area-inset-top)) 12px 12px; } .order-action-content { padding: 12px 12px calc(12px + env(safe-area-inset-bottom)); } }
</style>
