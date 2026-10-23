<script setup lang="ts">
import { computed, onMounted, provide, reactive, ref, watch } from 'vue';
import ScanInput from '../scan/ScanInput.vue';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import type { ScanDraft } from '../scan/scanDraft';
import { scanObjectTypeLabel, type ScanObjectType, type ScanRelatedTarget } from '../../services/scanContextApi';
import type { OrderActionTarget } from './orderActions';

const props = defineProps<{ active: boolean; allowedTypes: ScanObjectType[]; initialTarget?: OrderActionTarget | null }>();
const emit = defineEmits<{ selected: [target: OrderActionTarget] }>();
const code = ref('');
const scan = reactive(useScanContext());
provide('zhubao-scan-suspended', computed(() => !props.active));
watch(code, () => scan.invalidate(), { flush: 'sync' });
watch(() => props.active, active => { if (!active) scan.invalidate(); });
const unsupported = ref('');
onMounted(() => {
  if (props.initialTarget) {
    code.value = props.initialTarget.code;
    void locate({ content: props.initialTarget.code, ...(props.initialTarget.objectType ? { objectType: props.initialTarget.objectType } : {}) });
  }
});
async function locate(draft: ScanDraft) {
  unsupported.value = '';
  const context = await scan.lookup({ content: draft.content, ...(draft.objectType ? { objectType: draft.objectType } : {}), intent: 'LOOKUP' });
  const target = canonicalTarget(context);
  if (!target || !props.active) return;
  if (!props.allowedTypes.includes(target.objectType)) { unsupported.value = '这类单据不能办理当前作业，请重新选择。'; return; }
  emit('selected', { objectType: target.objectType, code: target.code });
}
function choose(item: ScanRelatedTarget) {
  if (item.code && item.objectType) { code.value = item.code; void locate({ content: item.code, objectType: item.objectType }); }
}
</script>

<template>
  <section class="section-block page-stack" aria-label="作业单据定位" :inert="!active || undefined">
    <p>扫码或手输单号。多张母单、子单必须选择，不会自动使用第一张。</p>
    <ScanInput v-model="code" field-text="作业单号" input-aria-label="作业单号" submit-label="定位作业" bind-mobile-scan :busy="scan.loading" @draft="scan.invalidate" @recognize="locate" />
    <p v-if="scan.loading" role="status">正在定位单据…</p>
    <p v-if="scan.error || unsupported" class="operation-feedback danger" role="alert">{{ scan.error || unsupported }}</p>
    <template v-if="scan.context?.requiresSelection">
      <p>请选择要办理的单据</p>
      <button v-for="item in scan.context.relatedTargets" :key="`${item.objectType}:${item.code}`" class="secondary-button" type="button" @click="choose(item)">{{ scanObjectTypeLabel(item.objectType) }} {{ item.code }}</button>
    </template>
  </section>
</template>
