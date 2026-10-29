<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import type { LossApproval } from '../../data/m1PrototypeData';
import { ApiRequestError } from '../../services/apiClient';
import { approveLoss, type LossApprovalRequest, type LossApprovalResult } from '../../services/businessOperationApi';
import OperationInputs from '../m1/OperationInputs.vue';

const props = defineProps<{ application: LossApproval }>();
const emit = defineEmits<{ locked: [value: boolean]; changed: [] }>();
const application: LossApproval = JSON.parse(JSON.stringify(props.application));
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const permitted = computed(() => !session.value?.effectivePermissions || session.value.effectivePermissions.includes('BUSINESS_LOSS_APPROVAL'));
const values = ref<Record<string, string>>({ approvalCode: '', inputWeight: '', outputWeight: '', reasonableTransferWeight: '', standardLossRatePercent: '', evidenceCode: '', remark: '' });
const busy = ref(false);
const pending = ref<Partial<LossApprovalRequest> | null>(null);
const result = ref<LossApprovalResult | null>(null);
const error = ref('');
let disposed = false;
onBeforeUnmount(() => { disposed = true; });

async function submit() {
  if (!permitted.value || busy.value || pending.value || result.value) return;
  const draft = values.value;
  if (!draft.approvalCode.trim()) { error.value = '请填写损耗申请单号'; return; }
  for (const key of ['inputWeight', 'outputWeight', 'reasonableTransferWeight', 'standardLossRatePercent']) {
    const text = draft[key].trim();
    if (!text || !/^\d+(\.\d+)?$/.test(text) || !Number.isFinite(Number(text)) || (key === 'inputWeight' && Number(text) <= 0)) {
      error.value = '请填写有效的投入、产出、合理转移重量和标准损耗率，投入重量须大于零'; return;
    }
  }
  pending.value = { ...draft, requestId: `loss-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}` };
  emit('locked', true);
  await send();
}
async function send() {
  if (!pending.value || busy.value || !permitted.value) return;
  busy.value = true; error.value = '';
  try {
    const response = await approveLoss(application, pending.value);
    if (disposed) return;
    if (!response?.approvalCode || response.approvalCode !== pending.value.approvalCode || response.workOrderCode !== application.workOrder) throw new Error('损耗申请回执与原单不一致，请保留原请求核对');
    result.value = response; pending.value = null; emit('locked', false); emit('changed');
  } catch (caught) {
    if (disposed) return;
    if (caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408) {
      pending.value = null; emit('locked', false);
    }
    error.value = caught instanceof Error ? caught.message : '损耗申请失败';
  } finally { busy.value = false; }
}
</script>

<template>
  <section class="page-stack" aria-label="物理损耗申请">
    <h3>损耗申请</h3>
    <p>{{ application.workOrder }} · {{ application.process }}。记录实物损耗及审批，费用在交货后结单时确定。</p>
    <p v-if="!permitted">当前账号无损耗申请权限。</p>
    <fieldset :disabled="!permitted || busy || Boolean(pending) || Boolean(result)">
      <legend>本次损耗事实</legend>
      <OperationInputs v-model="values" :fields="[['approvalCode', '损耗申请单号'], ['inputWeight', '投入重量（克）', 'number'], ['outputWeight', '产出重量（克）', 'number'], ['reasonableTransferWeight', '合理转移重量（克）', 'number'], ['standardLossRatePercent', '标准损耗率（%）', 'number'], ['evidenceCode', '损耗证据编号'], ['remark', '损耗说明']]" />
      <button class="secondary-button" type="button" @click="submit">{{ busy ? '提交中…' : '提交损耗申请' }}</button>
    </fieldset>
    <p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p>
    <p v-if="pending && !busy" class="operation-feedback warning">结果尚未确认，原单和内容已保留。<button type="button" class="secondary-button" @click="send">原损耗请求重试</button></p>
    <p v-if="result" class="operation-feedback success" role="status">损耗申请已记录：{{ result.approvalCode }} / {{ result.approvalStatus }} · 实际损耗 {{ result.lossWeight }} 克，超标 {{ result.excessLossWeight }} 克</p>
  </section>
</template>
