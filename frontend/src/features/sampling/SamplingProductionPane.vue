<script setup lang="ts">
import { ref } from 'vue';
import { pickSamplingMaterial, reportSamplingOperation } from '../../services/samplingApi';

const samplingCode = ref('');
const receiptId = ref('');
const sourceDocumentCode = ref('');
const lineNo = ref('');
const workshopCode = ref('');
const quantity = ref('');
const unit = ref('颗');
const stepId = ref('SAMPLE_MAKE');
const versionKind = ref('MODEL');
const versionNo = ref('1');
const makingChoice = ref('');
const attachmentCode = ref('');
const message = ref('');

function newRequestId() {
  return `sampling-make-${Date.now()}`;
}

async function pick() {
  const counted = Number(quantity.value);
  if (!samplingCode.value.trim() || !Number.isFinite(counted) || counted <= 0) {
    message.value = '请填写领用数量';
    return;
  }
  if (unit.value.trim() === '0') {
    message.value = '未知重量不能写成 0';
    return;
  }
  const saved = await pickSamplingMaterial(samplingCode.value.trim(), {
    requestId: newRequestId(),
    receiptId: receiptId.value.trim(),
    sourceDocumentCode: sourceDocumentCode.value.trim(),
    lineNo: lineNo.value.trim() ? Number(lineNo.value) : null,
    workshopCode: workshopCode.value.trim(),
    quantity: counted,
    weight: null,
    unit: unit.value.trim(),
  });
  message.value = `已领用 ${saved.useId}，权属 ${saved.owner}`;
}

async function report() {
  if (!attachmentCode.value.trim()) {
    message.value = '没有有效的现场照片';
    return;
  }
  if (makingChoice.value === 'BOTH') {
    message.value = '雕刻与车型能否组合仍待核';
    return;
  }
  const saved = await reportSamplingOperation(samplingCode.value.trim(), {
    requestId: newRequestId(),
    stepId: stepId.value,
    versionKind: versionKind.value,
    versionNo: Number(versionNo.value),
    makingChoices: makingChoice.value ? [makingChoice.value] : [],
    quantity: Number(quantity.value),
    unit: unit.value.trim(),
    attachmentCode: attachmentCode.value.trim(),
  });
  message.value = `已报工 ${saved.stepName}`;
}
</script>

<template>
  <section class="page-stack" aria-label="打样材料和报工">
    <header>
      <h2>材料与生产</h2>
      <p>领用真实来源。报工必须有照片编号，不在这里上传大文件，也不开普通工单。</p>
    </header>
    <p role="status">{{ message }}</p>
    <label>打样号 <input v-model="samplingCode" aria-label="生产打样号" /></label>
    <label>公司收货 <input v-model="receiptId" aria-label="公司收货" /></label>
    <label>客户收货 <input v-model="sourceDocumentCode" aria-label="客户收货" /></label>
    <label>明细行 <input v-model="lineNo" aria-label="明细行" /></label>
    <label>车间 <input v-model="workshopCode" aria-label="车间" /></label>
    <label>数量 <input v-model="quantity" aria-label="生产数量" /></label>
    <label>单位 <input v-model="unit" aria-label="生产单位" /></label>
    <button type="button" @click="pick">领用</button>
    <label>节点
      <select v-model="stepId" aria-label="打样节点">
        <option value="SAMPLE_FLAT">平设</option>
        <option value="SAMPLE_MODEL">建模</option>
        <option value="SAMPLE_BEAD">珠型确认</option>
        <option value="SAMPLE_MAKE">选料与加工</option>
      </select>
    </label>
    <label>版本种类 <input v-model="versionKind" aria-label="报工版本种类" /></label>
    <label>版本号 <input v-model="versionNo" aria-label="报工版本号" /></label>
    <label>加工选择
      <select v-model="makingChoice" aria-label="加工选择">
        <option value="">不选择</option>
        <option value="CARVE">雕刻</option>
        <option value="SHAPE">车型</option>
        <option value="BOTH">雕刻加车型</option>
      </select>
    </label>
    <label>照片编号 <input v-model="attachmentCode" aria-label="照片编号" /></label>
    <button type="button" @click="report">报工</button>
  </section>
</template>
