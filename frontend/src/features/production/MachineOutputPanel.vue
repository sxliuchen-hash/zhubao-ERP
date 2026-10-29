<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import type { AuthSession } from '../../services/authApi';
import type { Tone } from '../../data/m1PrototypeData';
import { bindMachineOutput, fetchMachineOutputMachineMatchSuggestions, fetchMachineOutputSummary,
  type MachineOutputMachineMatchSuggestion, type MachineOutputSummary } from '../../services/machineOutputApi';
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canBind = computed(() => !session.value?.effectivePermissions || session.value.effectivePermissions.includes('MACHINE_OUTPUT_SUBMIT'));
const binding = ref(false);
const machineOutputSummary = ref<MachineOutputSummary>({
  totalRecords: 0,
  totalQuantity: 0,
  quantityUnit: '件',
  byMachine: [],
  byWorkDate: [],
  unmatchedMachineRecords: 0,
  signatureMissingRecords: 0,
});
const machineOutputSummaryLoading = ref(false);
const machineOutputSummaryError = ref('');
const machineMatchSuggestions = ref<MachineOutputMachineMatchSuggestion[]>([]);
const machineMatchSuggestionError = ref('');

const machineOutputTopMachines = computed(() => machineOutputSummary.value.byMachine.slice(0, 4));
const machineOutputWorkDates = computed(() => machineOutputSummary.value.byWorkDate.slice(0, 3));
const machineMatchSuggestionTop = computed(() => machineMatchSuggestions.value.slice(0, 3));
const machineOutputTotalText = computed(
  () => `${machineOutputSummary.value.totalQuantity}${machineOutputSummary.value.quantityUnit}`,
);

onMounted(reload);
function reload() { void loadMachineOutputSummary(); void loadMachineMatchSuggestions(); }
async function loadMachineOutputSummary() {
  machineOutputSummaryLoading.value = true;
  machineOutputSummaryError.value = '';
  try {
    machineOutputSummary.value = await fetchMachineOutputSummary(100);
  } catch (error) {
    machineOutputSummaryError.value = error instanceof Error ? error.message : '设备产量汇总读取失败';
  } finally {
    machineOutputSummaryLoading.value = false;
  }
}

const machineBindMessage = ref('');

// 将某机器编号的未绑定记录绑定到候选机器档案 ID（仅 READY_TO_BIND 且候选唯一时可用）
async function bindMachineMatch(item: MachineOutputMachineMatchSuggestion) {
  if (!canBind.value || binding.value || item.readiness !== 'READY_TO_BIND' || item.candidateMachineIds.length !== 1) return;
  machineBindMessage.value = '';
  const machineId = item.candidateMachineIds[0];
  if (machineId == null) {
    machineBindMessage.value = `${item.machineNoSnapshot} 无候选机器档案，请先建档`;
    return;
  }
  binding.value = true;
  try {
    const result = await bindMachineOutput({ machineNoSnapshot: item.machineNoSnapshot, machineId });
    machineBindMessage.value = `已将 ${result.machineNoSnapshot} 的 ${result.boundRecords} 条记录绑定到机器档案 ${result.machineId}`;
    await loadMachineMatchSuggestions();
  } catch (error) {
    machineBindMessage.value = error instanceof Error ? error.message : '机器档案绑定失败';
  } finally { binding.value = false; }
}

async function loadMachineMatchSuggestions() {
  machineMatchSuggestionError.value = '';
  try {
    machineMatchSuggestions.value = await fetchMachineOutputMachineMatchSuggestions(100);
  } catch (error) {
    machineMatchSuggestions.value = [];
    machineMatchSuggestionError.value = error instanceof Error ? error.message : '机器档案匹配建议读取失败';
  }
}

function outputQuantityText(quantity: number | string) {
  return `${quantity}${machineOutputSummary.value.quantityUnit}`;
}

function machineMatchReadinessLabel(readiness: MachineOutputMachineMatchSuggestion['readiness']) {
  if (readiness === 'READY_TO_BIND') return '可绑定';
  if (readiness === 'CONFLICT') return '候选冲突';
  return '待建档';
}

function machineMatchTone(readiness: MachineOutputMachineMatchSuggestion['readiness']): Tone {
  if (readiness === 'READY_TO_BIND') return 'green';
  if (readiness === 'CONFLICT') return 'red';
  return 'amber';
}
</script>

<template>
<div class="section-block module-rule-panel machine-output-summary-panel">
  <div class="section-heading">
    <div>
      <h2>设备产量汇总</h2><button type="button" class="secondary-button" :disabled="machineOutputSummaryLoading || binding" @click="reload">刷新设备统计与匹配建议</button>
      <details class="production-note">
        <summary>统计说明</summary>
        <p>雕刻登记表入账后的设备产量记录，按最近批次进入生产统计口径。</p>
      </details>
    </div>
    <StatusBadge :label="machineOutputSummaryLoading ? '读取中' : machineOutputSummaryError ? '未读取' : `${machineOutputSummary.totalRecords} 条`" tone="green" />
  </div>

  <div v-if="!machineOutputSummaryError" class="machine-output-summary-grid">
    <article>
      <span>最近产量</span>
      <strong>{{ machineOutputSummaryLoading ? '读取中' : machineOutputTotalText }}</strong>
    </article>
    <article>
      <span>机器档案待匹配</span>
      <strong>{{ machineOutputSummaryLoading ? '读取中' : machineOutputSummary.unmatchedMachineRecords }}</strong>
    </article>
    <article>
      <span>签名附件待补</span>
      <strong>{{ machineOutputSummaryLoading ? '读取中' : machineOutputSummary.signatureMissingRecords }}</strong>
    </article>
  </div>

  <div v-if="machineOutputSummaryError" class="operation-feedback danger">
    <strong>设备产量汇总读取失败</strong>
    <span>{{ machineOutputSummaryError }}</span>
  </div>

  <div v-else class="machine-output-split">
    <div>
      <strong>按机器</strong>
      <div class="rule-stack machine-output-list" v-if="machineOutputTopMachines.length">
        <article v-for="item in machineOutputTopMachines" :key="item.machineNoSnapshot">
          <span>{{ item.machineNoSnapshot }}</span>
          <b>{{ outputQuantityText(item.quantity) }}</b>
          <small>{{ item.records }} 条记录</small>
        </article>
      </div>
      <small v-else>暂无设备产量记录</small>
    </div>
    <div>
      <strong>按日期</strong>
      <div class="rule-stack machine-output-list" v-if="machineOutputWorkDates.length">
        <article v-for="item in machineOutputWorkDates" :key="item.workDate">
          <span>{{ item.workDate }}</span>
          <b>{{ outputQuantityText(item.quantity) }}</b>
          <small>{{ item.records }} 条记录</small>
        </article>
      </div>
      <small v-else>暂无日期汇总</small>
    </div>
  </div>

  <div class="machine-match-suggestion-panel">
    <div class="machine-match-suggestion-title">
      <strong>机器档案匹配建议</strong>
      <span>按现场机器编号归集未绑定记录，先给出只读建议，不自动改账。</span>
    </div>
    <div v-if="machineMatchSuggestionError" class="operation-feedback danger">
      <strong>机器档案匹配建议读取失败</strong>
      <span>{{ machineMatchSuggestionError }}</span>
    </div>
    <div v-else-if="machineMatchSuggestionTop.length" class="rule-stack machine-match-suggestion-list">
      <article v-for="item in machineMatchSuggestionTop" :key="item.machineNoSnapshot">
        <div>
          <strong>{{ item.machineNoSnapshot }}</strong>
          <span>{{ item.productCategory || '未记录品类' }} / {{ item.latestWorkDate || '未记录日期' }}</span>
        </div>
        <StatusBadge :label="machineMatchReadinessLabel(item.readiness)" :tone="machineMatchTone(item.readiness)" />
        <small>
          未匹配 {{ item.unmatchedRecords }} 条 / 已绑定 {{ item.matchedRecords }} 条 /
          候选 {{ item.candidateMachineIds.length ? item.candidateMachineIds.join(', ') : '待建档' }}
        </small>
        <small>{{ item.suggestedAction }}</small>
        <button
          v-if="canBind && item.readiness === 'READY_TO_BIND' && item.candidateMachineIds.length === 1"
          :disabled="binding"
          class="table-action"
          type="button"
          @click="bindMachineMatch(item)"
        >
          绑定到机器档案 {{ item.candidateMachineIds[0] }}
        </button>
      </article>
    </div>
    <small v-else>暂无待匹配机器档案。</small>
    <p v-if="machineBindMessage" class="operation-feedback">{{ machineBindMessage }}</p>
  </div>
</div>
</template>
