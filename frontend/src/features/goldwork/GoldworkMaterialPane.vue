<script setup lang="ts">
import { ref } from 'vue';
import { receiveGoldworkMaterial, recordGoldworkMovement } from '../../services/goldworkApi';

const goldworkCode = ref('');
const sourceId = ref('');
const sourceLineNo = ref('');
const sourceLineId = ref('');
const pieceCount = ref('');
const netWeight = ref('');
const grossWeight = ref('');
const weightUnit = ref('克');
const purity = ref('');
const stepId = ref('GOLD_CAST');
const kind = ref('OUTPUT');
const disposition = ref('');
const conversionNote = ref('');
const message = ref('');

function newRequestId() {
  return `goldwork-material-${Date.now()}`;
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

async function receive() {
  if (!goldworkCode.value.trim() || !sourceId.value.trim()) {
    message.value = '请选择金工单和真实来源';
    return;
  }
  const pieces = optionalNumber(pieceCount.value);
  const net = optionalNumber(netWeight.value);
  const gross = optionalNumber(grossWeight.value);
  if (pieces === 'zero' || net === 'zero' || gross === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await receiveGoldworkMaterial(goldworkCode.value.trim(), {
    requestId: newRequestId(),
    sourceId: sourceId.value.trim(),
    sourceLineNo: sourceLineNo.value.trim() ? Number(sourceLineNo.value) : null,
    pieceCount: pieces,
    netWeight: net,
    grossWeight: gross,
    weightUnit: weightUnit.value.trim(),
    purity: purity.value.trim(),
  });
  sourceLineId.value = saved.lineId;
  message.value = `已接收 ${saved.lineId}，精度 ${saved.precisionState}，未入账`;
}

async function record() {
  const net = optionalNumber(netWeight.value);
  const gross = optionalNumber(grossWeight.value);
  if (net === 'zero' || gross === 'zero') {
    message.value = '未知重量不能写成 0';
    return;
  }
  if (!sourceLineId.value.trim()) {
    message.value = '请选择金料来源';
    return;
  }
  const saved = await recordGoldworkMovement(goldworkCode.value.trim(), {
    requestId: newRequestId(),
    stepId: stepId.value,
    kind: kind.value,
    sourceLineId: sourceLineId.value.trim(),
    pieceCount: null,
    netWeight: net,
    grossWeight: gross,
    weightUnit: weightUnit.value.trim(),
    purity: purity.value.trim(),
    disposition: disposition.value.trim(),
    conversionNote: conversionNote.value.trim(),
    useGrossAsNet: false,
    inferShortage: false,
    conversionFactor: null,
    enforcePieceConservation: false,
  });
  message.value = `已记录 ${saved.kind}，净重${saved.netWeight == null ? '未填' : saved.netWeight}`;
}
</script>

<template>
  <section class="page-stack" aria-label="金料和投入产出">
    <header>
      <h2>金料与投入产出</h2>
      <p>净重和毛重分开填写。不把毛重写成净金，短重不自动算损耗，空着表示未知。</p>
    </header>
    <p role="status">{{ message }}</p>
    <label>金工号 <input v-model="goldworkCode" aria-label="金料金工号" /></label>
    <label>来源编号 <input v-model="sourceId" aria-label="来源编号" /></label>
    <label>来源行号 <input v-model="sourceLineNo" aria-label="来源行号" /></label>
    <label>件数 <input v-model="pieceCount" aria-label="件数" /></label>
    <label>净重 <input v-model="netWeight" aria-label="净重" /></label>
    <label>毛重 <input v-model="grossWeight" aria-label="毛重" /></label>
    <label>单位 <input v-model="weightUnit" aria-label="重量单位" /></label>
    <label>成色 <input v-model="purity" aria-label="成色" /></label>
    <button type="button" @click="receive">接收金料</button>
    <label>来源明细 <input v-model="sourceLineId" aria-label="来源明细" /></label>
    <label>工序
      <select v-model="stepId" aria-label="金工工序">
        <option value="GOLD_FLAT">平设</option>
        <option value="GOLD_MODEL">建模</option>
        <option value="GOLD_CAST">倒模</option>
        <option value="GOLD_FINISH">执模</option>
        <option value="POLISH_GOLD">抛光（金）</option>
      </select>
    </label>
    <label>记录类型
      <select v-model="kind" aria-label="记录类型">
        <option value="INPUT">投入</option>
        <option value="OUTPUT">产出</option>
        <option value="WIP">在制</option>
        <option value="DISPOSAL">处置</option>
      </select>
    </label>
    <label>处置 <input v-model="disposition" aria-label="处置" /></label>
    <label>转换说明 <input v-model="conversionNote" aria-label="转换说明" /></label>
    <button type="button" @click="record">记录投入产出</button>
  </section>
</template>
