<script setup lang="ts">
import { ref, watch } from 'vue';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
import ReportPhotoCapture from '../evidence/ReportPhotoCapture.vue';
import { decideGoldworkQuality, reportGoldworkOperation } from '../../services/goldworkApi';

const props = defineProps<{ linkedCode?: string }>();
const goldworkCode = ref('');
watch(() => props.linkedCode, (value) => { if (value) goldworkCode.value = value; }, { immediate: true });
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
const operation = useBusinessCommand('goldwork-report', goldworkCode, { stepId, netWeight, weightUnit, purity, attachmentCode, reportId, priorQualityId, outcome, note }, message);


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
  const saved = await operation.submit('report', {
    stepId: stepId.value,
    pieceCount: null,
    netWeight: net,
    weightUnit: net == null ? '' : weightUnit.value.trim(),
    purity: purity.value.trim(),
    attachmentCode: attachmentCode.value.trim(),
    settleWage: false,
    postStock: false,
    closeWip: false,
  }, (code, body) => reportGoldworkOperation(code, body));
  if (!saved) return;
  reportId.value = saved.reportId;
  attachmentCode.value = '';
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
  const saved = await operation.submit('quality', {
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
  }, (code, body) => decideGoldworkQuality(code, body));
  if (!saved) return;
  message.value = `已记录 ${saved.outcome}，成色未改，收费未改`;
}
</script>

<template>
  <section class="job-pane" aria-label="报工和质量">
    <header>
      <h2>报工与质量</h2>
      <p>拍照或选图后报工，不记工资或库存。返工不改成色和收费。</p>
    </header>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <fieldset class="job-fieldset" :disabled="operation.pending.value">
      <div class="info-fields">
        <label><span>金工号</span><input v-model="goldworkCode" aria-label="报工金工号" /></label>
        <label><span>工序</span>
          <select v-model="stepId" aria-label="报工工序">
            <option value="GOLD_FLAT">平设</option>
            <option value="GOLD_MODEL">建模</option>
            <option value="GOLD_CAST">倒模</option>
            <option value="GOLD_FINISH">执模</option>
            <option value="POLISH_GOLD">抛光（金）</option>
          </select>
        </label>
        <label><span>净重</span><input v-model="netWeight" aria-label="报工净重" /></label>
        <label><span>单位</span><input v-model="weightUnit" aria-label="报工单位" /></label>
        <label><span>成色</span><input v-model="purity" aria-label="报工成色" /></label>
        <div class="job-span"><ReportPhotoCapture v-model="attachmentCode" business-type="GOLDWORK" :business-code="goldworkCode" /></div>
      </div>
      <div class="job-toolbar"><button type="button" class="primary-button" @click="report">报工</button></div>
      <div class="job-block">
        <h3>质量</h3>
        <div class="info-fields">
          <label><span>报工编号</span><input v-model="reportId" aria-label="报工编号" /></label>
          <label><span>原轮次</span><input v-model="priorQualityId" aria-label="原轮次" /></label>
          <label><span>结论</span>
            <select v-model="outcome" aria-label="质量结论">
              <option value="RELEASE">放行</option>
              <option value="REWORK">返工</option>
              <option value="PENDING">待核</option>
            </select>
          </label>
          <label><span>说明</span><input v-model="note" aria-label="质量说明" /></label>
        </div>
        <div class="job-toolbar"><button type="button" class="primary-button" @click="decide">记录质量</button></div>
      </div>
    </fieldset>
    <p v-if="operation.pending.value" role="status">正在提交，请稍候</p>
    <div class="job-actions">
      <button v-if="operation.hasUnconfirmed.value" type="button" class="secondary-button" @click="operation.restorePending">恢复上次填写</button>
      <button type="button" class="secondary-button" @click="operation.startNext">开始下一笔</button>
    </div>
  </section>
</template>
