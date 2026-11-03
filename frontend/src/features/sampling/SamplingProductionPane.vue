<script setup lang="ts">
import { ref, watch } from 'vue';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
import ReportPhotoCapture from '../evidence/ReportPhotoCapture.vue';
import { pickSamplingMaterial, reportSamplingOperation } from '../../services/samplingApi';

const props = defineProps<{ linkedCode?: string }>();
const samplingCode = ref('');
watch(() => props.linkedCode, (value) => { if (value) samplingCode.value = value; }, { immediate: true });
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
const operation = useBusinessCommand('sampling-make', samplingCode, { receiptId, sourceDocumentCode, lineNo, workshopCode, quantity, unit, stepId, versionKind, versionNo, makingChoice, attachmentCode }, message);


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
  const saved = await operation.submit('pick', {
    receiptId: receiptId.value.trim(),
    sourceDocumentCode: sourceDocumentCode.value.trim(),
    lineNo: lineNo.value.trim() ? Number(lineNo.value) : null,
    workshopCode: workshopCode.value.trim(),
    quantity: counted,
    weight: null,
    unit: unit.value.trim(),
  }, (code, body) => pickSamplingMaterial(code, body));
  if (!saved) return;
  message.value = `已领用 ${saved.useId}，权属 ${saved.owner}`;
}

async function report() {
  if (!attachmentCode.value.trim()) {
    message.value = '没有有效的现场照片';
    return;
  }
  const saved = await operation.submit('report', {
    stepId: stepId.value,
    versionKind: versionKind.value,
    versionNo: Number(versionNo.value),
    makingChoices: makingChoice.value === 'BOTH' ? ['CARVE', 'SHAPE'] : makingChoice.value ? [makingChoice.value] : [],
    quantity: Number(quantity.value),
    unit: unit.value.trim(),
    attachmentCode: attachmentCode.value.trim(),
  }, (code, body) => reportSamplingOperation(code, body));
  if (!saved) return;
  message.value = `已报工 ${saved.stepName}`;
  attachmentCode.value = '';
}
</script>

<template>
  <section class="job-pane" aria-label="打样材料和报工">
    <header>
      <h2>材料与生产</h2>
      <p>领用真实来源，拍照或选图后直接报工。模型大文件仍只登记存放引用。</p>
    </header>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <fieldset class="job-fieldset" :disabled="operation.pending.value">
      <div class="info-fields">
        <label><span>打样号</span><input v-model="samplingCode" aria-label="生产打样号" /></label>
        <label><span>公司收货</span><input v-model="receiptId" aria-label="公司收货" /></label>
        <label><span>客户收货</span><input v-model="sourceDocumentCode" aria-label="客户收货" /></label>
        <label><span>明细行</span><input v-model="lineNo" aria-label="明细行" /></label>
        <label><span>车间</span><input v-model="workshopCode" aria-label="车间" /></label>
        <label><span>数量</span><input v-model="quantity" aria-label="生产数量" /></label>
        <label><span>单位</span><input v-model="unit" aria-label="生产单位" /></label>
      </div>
      <div class="job-toolbar"><button type="button" class="primary-button" @click="pick">领用</button></div>
      <div class="job-block">
        <h3>现场报工</h3>
        <div class="info-fields">
          <label><span>节点</span>
            <select v-model="stepId" aria-label="打样节点">
              <option value="SAMPLE_FLAT">平设</option>
              <option value="SAMPLE_MODEL">建模</option>
              <option value="SAMPLE_BEAD">珠型确认</option>
              <option value="SAMPLE_MAKE">选料与加工</option>
            </select>
          </label>
          <label><span>版本种类</span><input v-model="versionKind" aria-label="报工版本种类" /></label>
          <label><span>版本号</span><input v-model="versionNo" aria-label="报工版本号" /></label>
          <label><span>加工选择</span>
            <select v-model="makingChoice" aria-label="加工选择">
              <option value="">不选择</option>
              <option value="CARVE">雕刻</option>
              <option value="SHAPE">车型</option>
              <option value="BOTH">雕刻加车型</option>
            </select>
          </label>
          <div class="job-span"><ReportPhotoCapture v-model="attachmentCode" business-type="SAMPLING" :business-code="samplingCode" /></div>
        </div>
        <div class="job-toolbar"><button type="button" class="primary-button" @click="report">报工</button></div>
      </div>
    </fieldset>
    <p v-if="operation.pending.value" role="status">正在提交，请稍候</p>
    <div class="job-actions">
      <button v-if="operation.hasUnconfirmed.value" type="button" class="secondary-button" @click="operation.restorePending">恢复上次填写</button>
      <button type="button" class="secondary-button" @click="operation.startNext">开始下一笔</button>
    </div>
  </section>
</template>
