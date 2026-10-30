<script setup lang="ts">
import { computed, inject, provide, ref, type Ref } from 'vue';
import PortionQualityForm from '../quality/PortionQualityForm.vue';
import QualityPanel from '../quality/QualityPanel.vue';
import LossApplicationPanel from '../quality/LossApplicationPanel.vue';
import type { AuthSession } from '../../services/authApi';
import { fetchQualityWorkspace } from '../../services/businessWorkspaceApi';
import type { LossApproval } from '../../data/m1PrototypeData';
import type { OrderActionTarget } from './orderActions';

const props = defineProps<{ target: OrderActionTarget; active: boolean }>();
const emit = defineEmits<{ changed: []; selected: [target: OrderActionTarget] }>();
provide('zhubao-scan-suspended', computed(() => !props.active));
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canLoss = computed(() => session.value?.effectivePermissions?.includes('BUSINESS_LOSS_APPROVAL') === true);
const loss = ref<LossApproval | null>(null);
const lossLoading = ref(false);
const lossLocked = ref(false);
const lossError = ref('');
async function loadLoss() {
  if (!canLoss.value || lossLoading.value || lossLocked.value || loss.value) return;
  lossLoading.value = true; lossError.value = '';
  try {
    const workspace = await fetchQualityWorkspace();
    const cases = workspace.cases.filter(row => row.workOrder === props.target.code && row.operationContext);
    if (cases.length !== 1) { lossError.value = '本单尚未取得唯一损耗来源，请核对工单和库存后重试。'; return; }
    const row = cases[0];
    loss.value = { code: '', workOrder: row.workOrder, process: row.process, operationContext: row.operationContext,
      standard: '', actual: '', evidence: '', action: '待申请', tone: 'amber' };
  } catch (caught) { lossError.value = caught instanceof Error ? caught.message : '损耗来源读取失败'; }
  finally { lossLoading.value = false; }
}
</script>

<template>
  <div class="quality-action-pane field-job" :inert="!active || undefined">
    <QualityPanel field embedded :initial-target="target" title="质检与返工复检" intro="先逐条判定，再按当前检验轮次分别办理返工、复检和放行。" @changed="emit('changed')" @target-selected="emit('selected', $event)" />
    <PortionQualityForm :bag-id="target.code" />
    <details v-if="canLoss" class="section-block" @toggle="($event.target as HTMLDetailsElement).open && loadLoss()">
      <summary>物理损耗申请</summary>
      <p v-if="lossLoading" role="status">正在核对本单损耗来源…</p>
      <p v-if="lossError" role="alert">{{ lossError }}<button class="secondary-button" type="button" @click="loadLoss">重新读取来源</button></p>
      <LossApplicationPanel v-if="loss" :application="loss" @locked="lossLocked = $event" @changed="emit('changed')" />
    </details>
  </div>
</template>

<style scoped>
.quality-action-pane :deep(.field-dock) { position: sticky; left: auto; right: auto; bottom: 0; width: auto; margin: 0; transform: none; }
</style>
