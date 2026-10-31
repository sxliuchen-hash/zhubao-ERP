<script setup lang="ts">
import { ref } from 'vue';
import {
  addSamplingVersion,
  confirmSamplingVersion,
  fetchSamplingVersions,
  rejectSamplingVersion,
  type SamplingVersionView,
} from '../../services/samplingApi';

const samplingCode = ref('');
const versions = ref<SamplingVersionView[]>([]);
const kind = ref('MODEL');
const parameterText = ref('');
const modelFileName = ref('');
const modelFormat = ref('');
const modelVersion = ref('');
const modelLocationNote = ref('');
const message = ref('');
const requestId = ref(newRequestId());

function newRequestId() {
  return `sampling-version-${Date.now()}`;
}

async function loadVersions() {
  if (!samplingCode.value.trim()) {
    message.value = '请填写打样号';
    return;
  }
  versions.value = await fetchSamplingVersions(samplingCode.value.trim());
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
  const saved = await addSamplingVersion(samplingCode.value.trim(), {
    requestId: requestId.value,
    kind: kind.value,
    parameterText: parameterText.value.trim(),
    evidenceCode: '',
    modelFileName: modelFileName.value.trim(),
    modelFormat: modelFormat.value.trim(),
    modelVersion: modelVersion.value.trim(),
    modelLocationNote: modelLocationNote.value.trim(),
    modelEvidenceCode: '',
  });
  requestId.value = newRequestId();
  message.value = saved.modelReferenced ? '已保存模型引用，不代表外部原件已备份' : `已保存${saved.kindLabel}版本`;
  await loadVersions();
}

async function decide(version: SamplingVersionView, accepted: boolean) {
  const note = accepted ? '客户确认' : '需要修改';
  const action = accepted ? confirmSamplingVersion : rejectSamplingVersion;
  await action(version.samplingCode, {
    requestId: newRequestId(),
    kind: version.kind,
    versionNo: version.versionNo,
    note,
    credentialCode: '',
  });
  message.value = accepted ? '已记录客户确认' : '已记录驳回';
  await loadVersions();
}

function looksFetchable(note: string) {
  const text = note.trim().toLowerCase();
  return text.startsWith('http://') || text.startsWith('https://') || text.startsWith('file:') || /^[a-z]:\\/.test(text);
}
</script>

<template>
  <section class="page-stack" aria-label="打样版本">
    <header>
      <h2>版本与客户确认</h2>
      <p>已确认版本不会被新版本覆盖。没有大文件也能保存。有模型引用不等于已经备份外部原件。</p>
    </header>
    <p role="status">{{ message }}</p>
    <label>打样号 <input v-model="samplingCode" aria-label="版本打样号" /></label>
    <button type="button" @click="loadVersions">查询版本</button>
    <ul>
      <li v-for="version in versions" :key="`${version.kind}-${version.versionNo}`">
        <span>{{ version.kindLabel }} 第 {{ version.versionNo }} 版 {{ version.decisionLabel }}</span>
        <span v-if="version.parameterText"> {{ version.parameterText }}</span>
        <span v-if="version.modelReferenced"> 存放引用不代表已备份外部原件</span>
        <button v-if="version.decision === 'PENDING'" type="button" @click="decide(version, true)">确认</button>
        <button v-if="version.decision === 'PENDING'" type="button" @click="decide(version, false)">驳回</button>
      </li>
    </ul>
    <form @submit.prevent="submit">
      <label>版本种类
        <select v-model="kind" aria-label="版本种类">
          <option value="FLAT">平设</option>
          <option value="MODEL">模型</option>
          <option value="BEAD">珠型</option>
        </select>
      </label>
      <label>尺寸参数 <input v-model="parameterText" aria-label="尺寸参数" /></label>
      <label>模型文件名 <input v-model="modelFileName" aria-label="模型文件名" /></label>
      <label>模型格式 <input v-model="modelFormat" aria-label="模型格式" /></label>
      <label>模型版本 <input v-model="modelVersion" aria-label="模型版本" /></label>
      <label>存放说明 <input v-model="modelLocationNote" aria-label="存放说明" /></label>
      <button type="submit">保存版本</button>
    </form>
  </section>
</template>
