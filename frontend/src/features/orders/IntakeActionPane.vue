<script setup lang="ts">
import { computed, inject, provide, ref, type Ref } from 'vue';
import IntakePanel from '../intake/IntakePanel.vue';
import type { TraceTarget } from '../scan/traceTarget';
import type { MobileIntakeResult } from '../../services/mobileIntakeApi';
import type { OrderActionTarget } from './orderActions';

const props = withDefaults(defineProps<{ target: OrderActionTarget | null; active?: boolean }>(), { active: true });
const emit = defineEmits<{ saved: [result: MobileIntakeResult]; navigate: [] }>();
const parentOpenTab = inject<((tab: string) => void) | null>('zhubao-open-tab', null);
const parentOpenLabel = inject<((code: string) => void) | null>('zhubao-open-label', null);
const parentTrace = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
// 每个缓存草稿都有独立的定位输入，其他单据的全局跳转不会改写它。
const localTrace = ref<TraceTarget | null>(props.target ? { ...props.target, intent: 'INTAKE', token: 1 } : null);
provide('zhubao-trace-target', localTrace);
provide('zhubao-scan-suspended', computed(() => !props.active));
provide('zhubao-open-tab', (tab: string) => {
  if (localTrace.value) parentTrace.value = { ...localTrace.value, token: (parentTrace.value?.token ?? 0) + 1 };
  emit('navigate');
  parentOpenTab?.(tab);
});
if (parentOpenLabel) provide('zhubao-open-label', (code: string) => { emit('navigate'); parentOpenLabel(code); });
</script>

<template>
  <div class="intake-action-pane field-job page-stack" :inert="!active || undefined">
    <IntakePanel field title="收货资料" intro="核对客户与来料，再填写要求与证据。" @saved="emit('saved', $event)" />
  </div>
</template>

<style scoped>
.intake-action-pane { padding-bottom: 0; }
.intake-action-pane :deep(.field-dock) { position: sticky; left: auto; right: auto; bottom: 0; width: auto; transform: none; margin: 0; }
</style>
