<script setup lang="ts">
import { ref, watch } from 'vue';
import { fetchAttachmentPreviewUrl } from '../../services/documentManagementApi';

const props = withDefaults(defineProps<{
  attachmentCode: string;
  label?: string;
}>(), { label: '预览' });

const busy = ref(false);
const previewUrl = ref('');
const mediaType = ref('');
const fileName = ref('');
const errorMessage = ref('');
const refreshedAfterError = ref(false);

function contentUrl(raw: string, purpose: 'preview' | 'download'): string {
  const url = new URL(raw, window.location.href);
  const expectedPath = `/api/documents/attachments/${encodeURIComponent(props.attachmentCode)}/content`;
  if (url.origin !== window.location.origin || url.pathname !== expectedPath
    || !url.searchParams.has('expiresAt') || !url.searchParams.has('token')
    || url.searchParams.get('purpose') !== purpose || url.searchParams.get('resolution') !== 'original') {
    throw new Error('附件地址无效');
  }
  return url.href;
}

async function freshContentUrl(purpose: 'preview' | 'download' = 'preview'): Promise<string> {
  const preview = await fetchAttachmentPreviewUrl(props.attachmentCode, purpose);
  mediaType.value = preview.mediaType.toLowerCase();
  fileName.value = preview.fileName;
  return contentUrl(preview.url, purpose);
}

async function togglePreview() {
  if (previewUrl.value) {
    previewUrl.value = '';
    return;
  }
  if (busy.value) return;
  busy.value = true;
  errorMessage.value = '';
  refreshedAfterError.value = false;
  try {
    const url = await freshContentUrl();
    if (!['image/jpeg', 'image/png', 'video/mp4', 'video/webm'].includes(mediaType.value)) {
      throw new Error('此文件无法在页面内预览，请下载查看');
    }
    previewUrl.value = url;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '证据预览失败';
  } finally {
    busy.value = false;
  }
}

async function refreshAfterMediaError() {
  if (refreshedAfterError.value || busy.value) {
    previewUrl.value = '';
    errorMessage.value = '媒体加载失败，请重试预览或下载';
    return;
  }
  refreshedAfterError.value = true;
  busy.value = true;
  try {
    previewUrl.value = await freshContentUrl();
  } catch (error) {
    previewUrl.value = '';
    errorMessage.value = error instanceof Error ? error.message : '证据预览失败';
  } finally {
    busy.value = false;
  }
}

async function download() {
  if (busy.value) return;
  busy.value = true;
  errorMessage.value = '';
  try {
    const url = await freshContentUrl('download');
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName.value;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '证据下载失败';
  } finally {
    busy.value = false;
  }
}

watch(() => props.attachmentCode, () => {
  previewUrl.value = '';
  errorMessage.value = '';
});
</script>

<template>
  <div class="evidence-media-viewer" @click.stop>
    <div class="evidence-media-actions">
      <button class="table-action" type="button" :disabled="busy" @click="togglePreview">
        {{ busy ? '正在读取' : previewUrl ? '收起预览' : label }}
      </button>
      <button class="table-action" type="button" :disabled="busy" @click="download">下载证据</button>
    </div>
    <small v-if="errorMessage" class="operation-feedback danger" role="alert">{{ errorMessage }}</small>
  </div>
  <Teleport to="body">
    <div v-if="previewUrl" class="evidence-media-overlay" role="dialog" aria-modal="true" :aria-label="`证据预览：${fileName}`" @click.self="previewUrl = ''">
      <div class="evidence-media-panel">
        <div class="evidence-media-heading">
          <strong>{{ fileName }}</strong>
          <button class="table-action" type="button" aria-label="关闭证据预览" @click="previewUrl = ''">关闭</button>
        </div>
        <img v-if="mediaType.startsWith('image/')" class="evidence-media-content" :src="previewUrl" :alt="fileName || '证据照片'" @error="refreshAfterMediaError" />
        <video v-else-if="mediaType.startsWith('video/')" class="evidence-media-content" :src="previewUrl" controls playsinline @error="refreshAfterMediaError" />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.evidence-media-viewer { max-width: 100%; }
.evidence-media-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
.evidence-media-overlay { position: fixed; inset: 0; z-index: 10000; display: grid; place-items: center; padding: 1rem; background: rgba(0, 0, 0, .78); }
.evidence-media-panel { width: min(100%, 960px); max-height: 96vh; padding: 1rem; overflow: auto; border-radius: .6rem; background: #fff; }
.evidence-media-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: .75rem; overflow-wrap: anywhere; }
.evidence-media-content { display: block; max-width: 100%; max-height: calc(96vh - 6rem); margin: auto; object-fit: contain; background: #111; }
video.evidence-media-content { width: 100%; }
</style>
