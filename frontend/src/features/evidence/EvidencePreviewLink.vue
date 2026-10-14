<script setup lang="ts">
import { ref } from 'vue';
import { fetchAttachmentPreviewUrl } from '../../services/documentManagementApi';
import { previewTarget } from './previewNavigation';

const props = withDefaults(defineProps<{
  attachmentCode: string;
  label?: string;
}>(), { label: '打开' });

const opening = ref(false);
const errorMessage = ref('');
const target = previewTarget();

async function openPreview() {
  if (opening.value) return;
  errorMessage.value = '';
  opening.value = true;
  const opened = target === '_blank' ? window.open('', '_blank') : null;
  if (opened) opened.opener = null;
  try {
    const preview = await fetchAttachmentPreviewUrl(props.attachmentCode);
    const url = new URL(preview.url, window.location.href);
    if (url.origin !== window.location.origin || !url.pathname.startsWith('/api/documents/attachments/')) {
      throw new Error('附件预览地址无效');
    }
    if (opened && !opened.closed) {
      opened.location.href = url.href;
    } else {
      window.location.assign(url.href);
    }
  } catch (error) {
    opened?.close();
    errorMessage.value = error instanceof Error ? error.message : '附件预览失败';
  } finally {
    opening.value = false;
  }
}
</script>

<template>
  <span>
    <button class="table-action" type="button" :disabled="opening" @click="openPreview">
      {{ opening ? '正在打开' : label }}
    </button>
    <small v-if="errorMessage" class="operation-feedback danger" role="alert">{{ errorMessage }}</small>
  </span>
</template>
