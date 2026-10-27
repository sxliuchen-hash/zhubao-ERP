<script setup lang="ts">
import { ref, watch } from 'vue';
import { ApiRequestError, apiPost } from '../../services/apiClient';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';

const props = defineProps<{ code: string; requirement: string }>();
const emit = defineEmits<{ saved: [requirement: string] }>();
const draft = ref('');
const message = ref('');
const error = ref('');
const submitting = ref(false);
const attempt = ref<RequestAttempt | null>(null);

watch(() => [props.code, props.requirement] as const, () => {
  draft.value = props.requirement || '';
  message.value = '';
  error.value = '';
  attempt.value = null;
}, { immediate: true });

async function save() {
  const text = draft.value.trim();
  if (!text) {
    error.value = '请填写加工要求';
    return;
  }
  if (text.length > 500) {
    error.value = '加工要求不能超过 500 字';
    return;
  }
  if (text === props.requirement.trim()) {
    error.value = '加工要求没有变化';
    return;
  }
  attempt.value = nextRequestAttempt(attempt.value, `${props.code}|${text}`, 'req');
  submitting.value = true;
  error.value = '';
  try {
    const saved = await apiPost<{ requirement: string; requestId: string }, { code: string; processingRequirement: string }>(
      `/api/business/work-orders/${encodeURIComponent(props.code)}/requirement`,
      { requirement: text, requestId: attempt.value.requestId },
    );
    if (saved.code !== props.code) throw new Error('保存结果与当前子单不一致');
    message.value = '加工要求已保存，岗位和质检按新要求执行';
    emit('saved', saved.processingRequirement);
  } catch (caught) {
    const known = caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408;
    if (known) attempt.value = null;
    error.value = caught instanceof Error ? caught.message : '加工要求保存失败';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <form class="child-requirement" aria-label="编辑子单加工要求" @submit.prevent="save">
    <label>
      <span>子单加工要求</span>
      <textarea v-model="draft" maxlength="500" aria-label="子单加工要求" :disabled="submitting" />
    </label>
    <p>保存后直接生效，不送财务审批。期限、路线和费用仍从改单办理。已印出的标签不会自动更换。</p>
    <button class="primary-button" type="submit" :disabled="submitting">{{ submitting ? '正在保存' : '保存加工要求' }}</button>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
    <p v-if="error" class="operation-feedback danger" role="alert">{{ error }} <button v-if="attempt" class="secondary-button" type="button" :disabled="submitting" @click="save">原要求重试</button></p>
  </form>
</template>

<style scoped>
.child-requirement { display: grid; gap: 8px; }
.child-requirement label { display: grid; gap: 4px; }
.child-requirement span { color: var(--muted); font-size: 12px; }
.child-requirement textarea { width: 100%; min-height: 72px; }
.child-requirement p { margin: 0; color: var(--muted); font-size: 13px; }
.child-requirement .primary-button { justify-self: start; }
</style>
