<script setup lang="ts">
import { ref, watch } from 'vue';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
import {
  addSamplingVersion,
  confirmSamplingVersion,
  fetchSamplingVersions,
  rejectSamplingVersion,
  type SamplingVersionView,
} from '../../services/samplingApi';

const props = defineProps<{ linkedCode?: string }>();
const samplingCode = ref('');
watch(() => props.linkedCode, (value) => { if (value) samplingCode.value = value; }, { immediate: true });
const versions = ref<SamplingVersionView[]>([]);
const kind = ref('MODEL');
const parameterText = ref('');
const modelFileName = ref('');
const modelFormat = ref('');
const modelVersion = ref('');
const modelLocationNote = ref('');
const message = ref('');
const operation = useBusinessCommand('sampling-version', samplingCode, { kind, parameterText, modelFileName, modelFormat, modelVersion, modelLocationNote }, message);
watch(samplingCode, () => { versions.value = []; });


async function loadVersions() {
  if (!samplingCode.value.trim()) {
    message.value = '请填写打样号';
    return;
  }
  const code = samplingCode.value.trim();
  const saved = await fetchSamplingVersions(code);
  if (samplingCode.value.trim() !== code) return;
  versions.value = saved;
  message.value = versions.value.length ? '已读取版本' : '没有版本';
}

async function submit() {
  if (kind.value === 'MODEL' && looksFetchable(modelLocationNote.value)) {
    message.value = '模型存放说明不能是网址或本机路径';
    return;
  }
  if (kind.value !== 'MODEL' && (modelFileName.value.trim() || modelFormat.value.trim() || modelVersion.value.trim())) {
    message.value = '只有模型版本可以登记模型引用';
    return;
  }
  const saved = await operation.submit('version', {
    kind: kind.value,
    parameterText: parameterText.value.trim(),
    evidenceCode: '',
    modelFileName: modelFileName.value.trim(),
    modelFormat: modelFormat.value.trim(),
    modelVersion: modelVersion.value.trim(),
    modelLocationNote: modelLocationNote.value.trim(),
    modelEvidenceCode: '',
  }, (code, body) => addSamplingVersion(code, body));
  if (!saved) return;
  message.value = saved.modelReferenced ? '已保存模型引用，不代表外部原件已备份' : `已保存${saved.kindLabel}版本`;
  await loadVersions();
}

async function decide(version: SamplingVersionView, accepted: boolean) {
  const note = accepted ? '客户确认' : '需要修改';
  const action = accepted ? confirmSamplingVersion : rejectSamplingVersion;
  const saved = await operation.submit(`decision:${version.kind}:${version.versionNo}`, {
    kind: version.kind,
    versionNo: version.versionNo,
    note,
    credentialCode: '',
  }, (code, body) => action(code, body));
  if (!saved) return;
  message.value = accepted ? '已记录客户确认' : '已记录驳回';
  await loadVersions();
}

function looksFetchable(note: string) {
  const text = note.trim().toLowerCase();
  return text.startsWith('http://') || text.startsWith('https://') || text.startsWith('file:') || /^[a-z]:\\/.test(text);
}
</script>

<template>
  <section class="job-pane" aria-label="打样版本">
    <header>
      <h2>版本与客户确认</h2>
      <p>已确认版本不会被新版本覆盖。没有大文件也能保存。有模型引用不等于已经备份外部原件。</p>
    </header>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <fieldset class="job-fieldset" :disabled="operation.pending.value">
      <div class="info-fields">
        <label><span>打样号</span><input v-model="samplingCode" aria-label="版本打样号" /></label>
      </div>
      <div class="job-toolbar">
        <button type="button" class="secondary-button" @click="loadVersions">查询版本</button>
      </div>
      <ul class="job-records">
        <li v-for="version in versions" :key="`${version.kind}-${version.versionNo}`">
          <span>{{ version.kindLabel }} 第 {{ version.versionNo }} 版 {{ version.decisionLabel }}</span>
          <span v-if="version.parameterText"> {{ version.parameterText }}</span>
          <span v-if="version.modelReferenced"> 存放引用不代表已备份外部原件</span>
          <button v-if="version.decision === 'PENDING'" type="button" class="secondary-button" @click="decide(version, true)">确认</button>
          <button v-if="version.decision === 'PENDING'" type="button" class="secondary-button" @click="decide(version, false)">驳回</button>
        </li>
      </ul>
      <form class="info-fields" @submit.prevent="submit">
        <label><span>版本种类</span>
          <select v-model="kind" aria-label="版本种类">
            <option value="FLAT">平设</option>
            <option value="MODEL">模型</option>
            <option value="BEAD">珠型</option>
          </select>
        </label>
        <label><span>尺寸参数</span><input v-model="parameterText" aria-label="尺寸参数" /></label>
        <label><span>模型文件名</span><input v-model="modelFileName" aria-label="模型文件名" /></label>
        <label><span>模型格式</span><input v-model="modelFormat" aria-label="模型格式" /></label>
        <label><span>模型版本</span><input v-model="modelVersion" aria-label="模型版本" /></label>
        <label><span>存放说明</span><input v-model="modelLocationNote" aria-label="存放说明" /></label>
        <div class="job-span"><button class="primary-button" type="submit">保存版本</button></div>
      </form>
    </fieldset>
    <p v-if="operation.pending.value" role="status">正在提交，请稍候</p>
    <div class="job-actions">
      <button v-if="operation.hasUnconfirmed.value" type="button" class="secondary-button" @click="operation.restorePending">恢复上次填写</button>
      <button type="button" class="secondary-button" @click="operation.startNext">开始下一笔</button>
    </div>
  </section>
</template>
