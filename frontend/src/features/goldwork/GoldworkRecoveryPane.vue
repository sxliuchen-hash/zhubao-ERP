<script setup lang="ts">
import { ref, watch } from 'vue';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
import { issueGoldworkSupplement, recordGoldworkRecovery, returnGoldworkSupplement } from '../../services/goldworkApi';

const props = defineProps<{ linkedCode?: string }>();
const goldworkCode = ref('');
watch(() => props.linkedCode, (value) => { if (value) goldworkCode.value = value; }, { immediate: true });
const sourceLineId = ref('');
const outputMovementId = ref('');
const originalLineId = ref('');
const sourceId = ref('');
const priorSupplementId = ref('');
const purpose = ref('');
const netWeight = ref('');
const weightUnit = ref('克');
const purity = ref('');
const recoveryKind = ref('RECOVERABLE');
const message = ref('');
const operation = useBusinessCommand('goldwork-recovery', goldworkCode, { sourceLineId, outputMovementId, originalLineId, sourceId, priorSupplementId, purpose, netWeight, weightUnit, purity, recoveryKind }, message);


function optionalNumber(text: string) {
  const trimmed = text.trim();
  if (!trimmed) {
    return null;
  }
  const value = Number(trimmed);
  if (!Number.isFinite(value) || value === 0) {
    return 'zero' as const;
  }
  return value;
}

async function recover() {
  const net = optionalNumber(netWeight.value);
  if (net === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await operation.submit('recovery', {
    sourceLineId: sourceLineId.value.trim(),
    outputMovementId: outputMovementId.value.trim(),
    kind: recoveryKind.value,
    pieceCount: null,
    netWeight: net,
    grossWeight: null,
    weightUnit: net == null ? '' : weightUnit.value.trim(),
    purity: purity.value.trim(),
    clearAsLoss: false,
    addToFinished: false,
    feeOffset: null,
    autoReturn: false,
    inferWeight: false,
  }, (code, body) => recordGoldworkRecovery(code, body));
  if (!saved) return;
  message.value = `已记录回收 ${saved.recoveryId}，未折抵费用`;
}

async function issue() {
  const net = optionalNumber(netWeight.value);
  if (net === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await operation.submit('supplement', {
    originalLineId: originalLineId.value.trim(),
    sourceId: sourceId.value.trim(),
    sourceLineNo: null,
    purpose: purpose.value.trim(),
    pieceCount: null,
    netWeight: net,
    grossWeight: null,
    weightUnit: weightUnit.value.trim(),
    purity: purity.value.trim(),
    addOntoOriginal: false,
    changeOwner: false,
    feeAmount: null,
  }, (code, body) => issueGoldworkSupplement(code, body));
  if (!saved) return;
  priorSupplementId.value = saved.supplementId;
  message.value = `已补料 ${saved.supplementId}，未记补料费`;
}

async function giveBack() {
  const net = optionalNumber(netWeight.value);
  if (net === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await operation.submit('return-supplement', {
    originalLineId: originalLineId.value.trim(),
    priorSupplementId: priorSupplementId.value.trim(),
    purpose: purpose.value.trim() || '退回',
    pieceCount: null,
    netWeight: net,
    grossWeight: null,
    weightUnit: weightUnit.value.trim(),
    purity: purity.value.trim(),
    addOntoOriginal: false,
    changeOwner: false,
    feeAmount: null,
  }, (code, body) => returnGoldworkSupplement(code, body));
  if (!saved) return;
  message.value = `已退补 ${saved.supplementId}`;
}
</script>

<template>
  <section class="job-pane" aria-label="回收和补料">
    <header>
      <h2>回收与补料</h2>
      <p>回收不并进成品，也不折抵费用。补料另记来源，不改最初来料，不另收补料费。</p>
    </header>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <fieldset class="job-fieldset" :disabled="operation.pending.value">
      <div class="info-fields">
        <label><span>金工号</span><input v-model="goldworkCode" aria-label="回收金工号" /></label>
        <label><span>来源明细</span><input v-model="sourceLineId" aria-label="回收来源明细" /></label>
        <label><span>成品产出</span><input v-model="outputMovementId" aria-label="成品产出" /></label>
        <label><span>净重</span><input v-model="netWeight" aria-label="回收净重" /></label>
        <label><span>单位</span><input v-model="weightUnit" aria-label="回收单位" /></label>
        <label><span>成色</span><input v-model="purity" aria-label="回收成色" /></label>
        <label><span>去向</span>
          <select v-model="recoveryKind" aria-label="回收去向">
            <option value="RECOVERABLE">可回收金料</option>
            <option value="REMAINDER">余料</option>
            <option value="PHYSICAL_SCRAP">实物报废</option>
            <option value="IRRECOVERABLE">不可恢复损耗</option>
          </select>
        </label>
      </div>
      <div class="job-toolbar"><button type="button" class="primary-button" @click="recover">记录回收</button></div>
      <div class="job-block">
        <h3>补料</h3>
        <div class="info-fields">
          <label><span>最初来料</span><input v-model="originalLineId" aria-label="最初来料" /></label>
          <label><span>补料来源</span><input v-model="sourceId" aria-label="补料来源" /></label>
          <label><span>用途</span><input v-model="purpose" aria-label="补料用途" /></label>
          <label><span>原补料</span><input v-model="priorSupplementId" aria-label="原补料" /></label>
        </div>
        <div class="job-toolbar">
          <button type="button" class="primary-button" @click="issue">补料</button>
          <button type="button" class="secondary-button" @click="giveBack">退补</button>
        </div>
      </div>
    </fieldset>
    <p v-if="operation.pending.value" role="status">正在提交，请稍候</p>
    <div class="job-actions">
      <button v-if="operation.hasUnconfirmed.value" type="button" class="secondary-button" @click="operation.restorePending">恢复上次填写</button>
      <button type="button" class="secondary-button" @click="operation.startNext">开始下一笔</button>
    </div>
  </section>
</template>
