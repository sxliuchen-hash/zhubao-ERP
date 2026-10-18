<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { fetchAttachmentPreviewUrl } from '../../services/documentManagementApi';

const props = withDefaults(defineProps<{
  attachmentCode: string;
  label?: string;
  mediaTypeHint?: string;
}>(), { label: '预览' });

const busy = ref(false);
const previewUrl = ref('');
const thumbnailUrl = ref('');
const viewerRef = ref<HTMLElement | null>(null);
const mediaType = ref('');
const fileName = ref('');
const errorMessage = ref('');
const refreshedAfterError = ref(false);
const viewingOriginal = ref(false);
let observer: IntersectionObserver | null = null;
let thumbnailRequested = false;
let thumbnailGeneration = 0;

function contentUrl(raw: string, purpose: 'preview' | 'download', resolution: 'original' | 'thumbnail' | 'preview' | 'poster'): string {
  const url = new URL(raw, window.location.href);
  const expectedPath = `/api/documents/attachments/${encodeURIComponent(props.attachmentCode)}/content`;
  if (url.origin !== window.location.origin || url.pathname !== expectedPath
    || !url.searchParams.has('expiresAt') || !url.searchParams.has('token')
    || url.searchParams.get('purpose') !== purpose || url.searchParams.get('resolution') !== resolution) {
    throw new Error('附件地址无效');
  }
  return url.href;
}

async function freshContentUrl(
  purpose: 'preview' | 'download' = 'preview',
  resolution: 'original' | 'thumbnail' | 'preview' | 'poster' = 'original',
): Promise<string> {
  const preview = await fetchAttachmentPreviewUrl(props.attachmentCode, purpose, resolution);
  mediaType.value = preview.mediaType.toLowerCase();
  fileName.value = preview.fileName;
  return contentUrl(preview.url, purpose, resolution);
}

function coverResolution(): 'thumbnail' | 'poster' | null {
  const hint = props.mediaTypeHint?.toLowerCase() ?? '';
  if (hint.startsWith('image/')) return 'thumbnail';
  if (hint.startsWith('video/')) return 'poster';
  return null;
}

async function loadThumbnail() {
  const resolution = coverResolution();
  if (thumbnailRequested || !resolution) return;
  thumbnailRequested = true;
  const generation = thumbnailGeneration;
  const code = props.attachmentCode;
  try {
    const signed = await fetchAttachmentPreviewUrl(code, 'preview', resolution);
    if (generation !== thumbnailGeneration || code !== props.attachmentCode) return;
    if (signed.mediaType.toLowerCase() !== 'image/jpeg') throw new Error('缩略图类型无效');
    thumbnailUrl.value = contentUrl(signed.url, 'preview', resolution);
  } catch {
    if (generation === thumbnailGeneration) thumbnailUrl.value = '';
  }
}

function observeThumbnail() {
  observer?.disconnect();
  observer = null;
  if (!coverResolution() || !viewerRef.value) return;
  if (typeof IntersectionObserver === 'undefined') {
    void loadThumbnail();
    return;
  }
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) {
      observer?.disconnect();
      observer = null;
      void loadThumbnail();
    }
  });
  observer.observe(viewerRef.value);
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
    const image = props.mediaTypeHint?.toLowerCase().startsWith('image/');
    const url = await freshContentUrl('preview', image ? 'preview' : 'original');
    if (!['image/jpeg', 'image/png', 'video/mp4', 'video/webm'].includes(mediaType.value)) {
      throw new Error('此文件无法在页面内预览，请下载查看');
    }
    viewingOriginal.value = false;
    previewUrl.value = url;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '证据预览失败';
  } finally {
    busy.value = false;
  }
}

async function showOriginal() {
  if (busy.value || !previewUrl.value || !mediaType.value.startsWith('image/')) return;
  busy.value = true;
  errorMessage.value = '';
  try {
    previewUrl.value = await freshContentUrl('preview', 'original');
    viewingOriginal.value = true;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '原图打开失败';
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
    previewUrl.value = await freshContentUrl('preview', viewingOriginal.value || !props.mediaTypeHint?.startsWith('image/')
      ? 'original' : 'preview');
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
    const url = await freshContentUrl('download', 'original');
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
  thumbnailUrl.value = '';
  errorMessage.value = '';
  viewingOriginal.value = false;
  thumbnailRequested = false;
  thumbnailGeneration += 1;
  observeThumbnail();
});

watch(() => props.mediaTypeHint, () => {
  thumbnailUrl.value = '';
  thumbnailRequested = false;
  thumbnailGeneration += 1;
  observeThumbnail();
});

onMounted(observeThumbnail);
onBeforeUnmount(() => {
  thumbnailGeneration += 1;
  observer?.disconnect();
});
</script>

<template>
  <div ref="viewerRef" class="evidence-media-viewer" @click.stop>
    <img v-if="thumbnailUrl" class="evidence-thumbnail" :src="thumbnailUrl" loading="lazy" alt="附件缩略图或视频封面" @error="thumbnailUrl = ''" />
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
          <button v-if="mediaType.startsWith('image/') && !viewingOriginal" class="table-action" type="button" :disabled="busy" @click="showOriginal">查看原图</button>
          <button class="table-action" type="button" aria-label="关闭证据预览" @click="previewUrl = ''">关闭</button>
        </div>
        <img v-if="mediaType.startsWith('image/')" class="evidence-media-content" :src="previewUrl" :alt="fileName || '证据照片'" @error="refreshAfterMediaError" />
        <video v-else-if="mediaType.startsWith('video/')" class="evidence-media-content" :src="previewUrl" :poster="thumbnailUrl || undefined" controls playsinline @error="refreshAfterMediaError" />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.evidence-media-viewer { max-width: 100%; }
.evidence-thumbnail { display: block; width: 96px; height: 72px; object-fit: contain; margin-bottom: .4rem; background: #f4f4f4; }
.evidence-media-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
.evidence-media-overlay { position: fixed; inset: 0; z-index: 10000; display: grid; place-items: center; padding: 1rem; background: rgba(0, 0, 0, .78); }
.evidence-media-panel { width: min(100%, 960px); max-height: 96vh; padding: 1rem; overflow: auto; border-radius: .6rem; background: #fff; }
.evidence-media-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: .75rem; overflow-wrap: anywhere; }
.evidence-media-content { display: block; max-width: 100%; max-height: calc(96vh - 6rem); margin: auto; object-fit: contain; background: #111; }
video.evidence-media-content { width: 100%; }
</style>
