<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import { getAuthToken } from '../../services/apiClient';
import { uploadReportPhoto } from '../../services/evidenceApi';
import { stampPhotoTimestamp } from './photoStamp';

const props = defineProps<{ businessType: 'SAMPLING' | 'GOLDWORK'; businessCode: string; modelValue: string }>();
const emit = defineEmits<{ 'update:modelValue': [code: string] }>();
const owner = getAuthToken();
const current = computed(() => {
  const key = `${owner}\n${props.businessType}\n${props.businessCode.trim()}`;
  if (!drafts.has(key)) drafts.set(key, { file: null, preview: '', attachment: props.modelValue, pending: false, message: '' });
  return drafts.get(key)!;
});
watch(() => props.businessCode, () => { emit('update:modelValue', current.value.attachment); });
watch(() => props.modelValue, value => {
  const draft = current.value;
  if (!value && draft.attachment) {
    if (draft.preview) URL.revokeObjectURL(draft.preview);
    draft.file = null; draft.preview = ''; draft.attachment = ''; draft.message = '';
  } else if (value) draft.attachment = value;
});

async function choose(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  const draft = current.value;
  if (!props.businessCode.trim()) { draft.message = '请先填写业务单号'; return; }
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 8 * 1024 * 1024) {
    draft.message = '请选择8MB以内的JPEG或PNG照片'; return;
  }
  draft.pending = true;
  draft.file = null;
  draft.attachment = '';
  emit('update:modelValue', '');
  const code = props.businessCode.trim();
  try {
    draft.file = await stampPhotoTimestamp(file);
    if (draft.preview) URL.revokeObjectURL(draft.preview);
    draft.preview = typeof URL.createObjectURL === 'function' ? URL.createObjectURL(draft.file) : '';
    draft.attachment = '';
    if (props.businessCode.trim() === code) emit('update:modelValue', '');
  } catch (error) { draft.message = error instanceof Error ? error.message : '照片处理失败'; }
  finally { draft.pending = false; input.value = ''; }
  if (draft.file) await upload(draft, code);
}

async function upload(draft = current.value, code = props.businessCode.trim()) {
  if (!draft.file || draft.pending) return;
  if (getAuthToken() !== owner) { draft.message = '账号已变化，请重新打开办理页'; return; }
  draft.pending = true;
  draft.message = '正在上传照片';
  try {
    const saved = await uploadReportPhoto(props.businessType, code, draft.file);
    draft.attachment = saved.code;
    draft.message = '照片已上传，可以报工';
    if (props.businessCode.trim() === code) emit('update:modelValue', saved.code);
  } catch (error) { draft.message = error instanceof Error ? error.message : '照片上传失败，文件已保留，请重试'; }
  finally { draft.pending = false; }
}
</script>

<script lang="ts">
interface PhotoDraft { file: File | null; preview: string; attachment: string; pending: boolean; message: string }
// 当前页面会话内保留实际文件，换单或失败不丢照片，也不把其他单的上传结果填过来。
const drafts = reactive(new Map<string, PhotoDraft>());
</script>

<template>
  <div aria-label="现场报工照片">
    <label>拍照 <input type="file" accept="image/jpeg,image/png" capture="environment" :disabled="current.pending || !businessCode.trim()" @change="choose" /></label>
    <label>从相册选图 <input type="file" accept="image/jpeg,image/png" :disabled="current.pending || !businessCode.trim()" @change="choose" /></label>
    <img v-if="current.preview" :src="current.preview" alt="本次报工照片" style="max-width:180px;max-height:180px" />
    <p v-if="current.attachment">照片已上传</p>
    <p v-if="current.message" role="status">{{ current.message }}</p>
    <button v-if="current.file && !current.attachment" type="button" :disabled="current.pending" @click="upload()">重试上传照片</button>
  </div>
</template>
