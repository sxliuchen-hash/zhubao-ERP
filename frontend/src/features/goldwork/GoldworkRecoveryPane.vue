<script setup lang="ts">
import { ref } from 'vue';
import { issueGoldworkSupplement, recordGoldworkRecovery, returnGoldworkSupplement } from '../../services/goldworkApi';

const goldworkCode = ref('');
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

function newRequestId() {
  return `goldwork-recovery-${Date.now()}`;
}

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
  const saved = await recordGoldworkRecovery(goldworkCode.value.trim(), {
    requestId: newRequestId(),
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
  });
  message.value = `已记录回收 ${saved.recoveryId}，未折抵费用`;
}

async function issue() {
  const net = optionalNumber(netWeight.value);
  if (net === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await issueGoldworkSupplement(goldworkCode.value.trim(), {
    requestId: newRequestId(),
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
  });
  priorSupplementId.value = saved.supplementId;
  message.value = `已补料 ${saved.supplementId}，未记补料费`;
}

async function giveBack() {
  const net = optionalNumber(netWeight.value);
  if (net === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await returnGoldworkSupplement(goldworkCode.value.trim(), {
    requestId: newRequestId(),
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
  });
  message.value = `已退补 ${saved.supplementId}`;
}
</script>

<template>
  <section class="page-stack" aria-label="回收和补料">
    <header>
      <h2>回收与补料</h2>
      <p>回收不并进成品，也不折抵费用。补料另记来源，不改最初来料，不另收补料费。</p>
    </header>
    <p role="status">{{ message }}</p>
    <label>金工号 <input v-model="goldworkCode" aria-label="回收金工号" /></label>
    <label>来源明细 <input v-model="sourceLineId" aria-label="回收来源明细" /></label>
    <label>成品产出 <input v-model="outputMovementId" aria-label="成品产出" /></label>
    <label>净重 <input v-model="netWeight" aria-label="回收净重" /></label>
    <label>单位 <input v-model="weightUnit" aria-label="回收单位" /></label>
    <label>成色 <input v-model="purity" aria-label="回收成色" /></label>
    <label>去向
      <select v-model="recoveryKind" aria-label="回收去向">
        <option value="RECOVERABLE">可回收金料</option>
        <option value="REMAINDER">余料</option>
        <option value="PHYSICAL_SCRAP">实物报废</option>
        <option value="IRRECOVERABLE">不可恢复损耗</option>
      </select>
    </label>
    <button type="button" @click="recover">记录回收</button>
    <label>最初来料 <input v-model="originalLineId" aria-label="最初来料" /></label>
    <label>补料来源 <input v-model="sourceId" aria-label="补料来源" /></label>
    <label>用途 <input v-model="purpose" aria-label="补料用途" /></label>
    <label>原补料 <input v-model="priorSupplementId" aria-label="原补料" /></label>
    <button type="button" @click="issue">补料</button>
    <button type="button" @click="giveBack">退补</button>
  </section>
</template>
