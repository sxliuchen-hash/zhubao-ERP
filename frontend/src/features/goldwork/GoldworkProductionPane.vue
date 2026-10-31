<script setup lang="ts">
import { ref } from 'vue';
import { decideGoldworkQuality, reportGoldworkOperation } from '../../services/goldworkApi';

const goldworkCode = ref('');
const stepId = ref('GOLD_CAST');
const netWeight = ref('');
const weightUnit = ref('克');
const purity = ref('');
const attachmentCode = ref('');
const reportId = ref('');
const priorQualityId = ref('');
const outcome = ref('RELEASE');
const note = ref('');
const message = ref('');

function newRequestId() {
  return `goldwork-report-${Date.now()}`;
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

async function report() {
  if (!attachmentCode.value.trim()) {
    message.value = '没有有效的现场照片';
    return;
  }
  const net = optionalNumber(netWeight.value);
  if (net === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await reportGoldworkOperation(goldworkCode.value.trim(), {
    requestId: newRequestId(),
    stepId: stepId.value,
    pieceCount: null,
    netWeight: net,
    weightUnit: net == null ? '' : weightUnit.value.trim(),
    purity: purity.value.trim(),
    attachmentCode: attachmentCode.value.trim(),
    settleWage: false,
    postStock: false,
    closeWip: false,
  });
  reportId.value = saved.reportId;
  message.value = `已报工 ${saved.stepLabel}，未记工资`;
}

async function decide() {
  const net = optionalNumber(netWeight.value);
  if (net === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  if (net == null) {
    message.value = '请填写本次检查份额';
    return;
  }
  const saved = await decideGoldworkQuality(goldworkCode.value.trim(), {
    requestId: newRequestId(),
    reportId: reportId.value.trim(),
    priorQualityId: priorQualityId.value.trim(),
    outcome: outcome.value,
    pieceCount: null,
    netWeight: net,
    weightUnit: weightUnit.value.trim(),
    purity: '',
    note: note.value.trim(),
    useBeadDefault: false,
    changePurity: false,
    changeFee: false,
  });
  message.value = `已记录 ${saved.outcome}，成色未改，收费未改`;
}
</script>

<template>
  <section class="page-stack" aria-label="报工和质量">
    <header>
      <h2>报工与质量</h2>
      <p>只报金工工序，必须填写照片编号。不上传文件，不记工资或库存。返工不改成色和收费。</p>
    </header>
    <p role="status">{{ message }}</p>
    <label>金工号 <input v-model="goldworkCode" aria-label="报工金工号" /></label>
    <label>工序
      <select v-model="stepId" aria-label="报工工序">
        <option value="GOLD_FLAT">平设</option>
        <option value="GOLD_MODEL">建模</option>
        <option value="GOLD_CAST">倒模</option>
        <option value="GOLD_FINISH">执模</option>
        <option value="POLISH_GOLD">抛光（金）</option>
      </select>
    </label>
    <label>净重 <input v-model="netWeight" aria-label="报工净重" /></label>
    <label>单位 <input v-model="weightUnit" aria-label="报工单位" /></label>
    <label>成色 <input v-model="purity" aria-label="报工成色" /></label>
    <label>照片编号 <input v-model="attachmentCode" aria-label="照片编号" /></label>
    <button type="button" @click="report">报工</button>
    <label>报工编号 <input v-model="reportId" aria-label="报工编号" /></label>
    <label>原轮次 <input v-model="priorQualityId" aria-label="原轮次" /></label>
    <label>结论
      <select v-model="outcome" aria-label="质量结论">
        <option value="RELEASE">放行</option>
        <option value="REWORK">返工</option>
        <option value="PENDING">待核</option>
      </select>
    </label>
    <label>说明 <input v-model="note" aria-label="质量说明" /></label>
    <button type="button" @click="decide">记录质量</button>
  </section>
</template>
