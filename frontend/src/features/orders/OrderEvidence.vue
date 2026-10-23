<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { fetchHistoricalEvidencePage, fetchLinkedEvidencePage, type EvidencePage, type EvidenceLink, type HistoricalEvidence } from '../../services/evidenceApi';
import EvidencePreviewLink from '../evidence/EvidencePreviewLink.vue';

const props = defineProps<{ targetType: string; targetCode: string }>();
const links = ref<EvidenceLink[]>([]);
const historical = ref<HistoricalEvidence[]>([]);
const errors = ref({ links: '', historical: '' });
const busy = ref({ links: false, historical: false });
const cursors = ref<{ links: number | null; historical: number | null }>({ links: null, historical: null });
const more = ref({ links: false, historical: false });
let disposed = false;
onBeforeUnmount(() => { disposed = true; });

async function load(kind: 'links' | 'historical', append = false) {
  if (busy.value[kind]) return;
  busy.value[kind] = true;
  errors.value[kind] = '';
  const query = { targetType: props.targetType, targetCode: props.targetCode, limit: 20,
    ...(append && cursors.value[kind] !== null ? { afterId: cursors.value[kind]! } : {}) };
  try {
    const result: EvidencePage<EvidenceLink> | EvidencePage<HistoricalEvidence> = kind === 'links'
      ? await fetchLinkedEvidencePage(query) : await fetchHistoricalEvidencePage(query);
    if (disposed) return;
    if (kind === 'links') links.value = [...(append ? links.value : []), ...(result.items as EvidenceLink[])];
    else historical.value = [...(append ? historical.value : []), ...(result.items as HistoricalEvidence[])];
    cursors.value[kind] = result.nextCursor;
    more.value[kind] = result.hasMore;
  } catch (error) {
    if (!disposed) errors.value[kind] = error instanceof Error ? error.message : '附件读取失败';
  } finally {
    if (!disposed) busy.value[kind] = false;
  }
}
void load('links');
void load('historical');
</script>

<template>
  <section class="order-evidence" aria-label="本单附件">
    <p>文件归属：{{ targetCode }}。仅查看本单附件，上传及正式操作关联仍在对应业务表单办理。</p>
    <h4>已关联证据</h4>
    <p v-if="busy.links" role="status">正在读取证据…</p>
    <p v-if="errors.links" role="alert">{{ errors.links }} <button type="button" @click="load('links', links.length > 0)">重试证据</button></p>
    <p v-if="!busy.links && !errors.links && !links.length">本单暂无已关联证据</p>
    <article v-for="item in links" :key="item.linkCode">
      <strong>{{ item.fileName }}</strong>
      <p>{{ item.targetCode }} · {{ item.sceneLabel || item.stage }} · {{ item.operationCode || '未提供操作单号' }} · {{ item.linkedAt }}</p>
      <EvidencePreviewLink :key="item.attachmentCode" :attachment-code="item.attachmentCode" :media-type-hint="item.mediaType" />
    </article>
    <button v-if="more.links" class="secondary-button" type="button" :disabled="busy.links" @click="load('links', true)">加载更多证据</button>
    <h4>历史附件</h4>
    <p v-if="busy.historical" role="status">正在读取历史附件…</p>
    <p v-if="errors.historical" role="alert">{{ errors.historical }} <button type="button" @click="load('historical', historical.length > 0)">重试历史附件</button></p>
    <p v-if="!busy.historical && !errors.historical && !historical.length">本单暂无历史附件</p>
    <article v-for="item in historical" :key="item.attachmentCode">
      <strong>{{ item.fileName }}</strong>
      <p>{{ item.businessCode }} · {{ item.label }} · {{ item.uploadedAt }}</p>
      <EvidencePreviewLink :key="item.attachmentCode" :attachment-code="item.attachmentCode" :media-type-hint="item.mediaType" />
    </article>
    <button v-if="more.historical" class="secondary-button" type="button" :disabled="busy.historical" @click="load('historical', true)">加载更多历史附件</button>
  </section>
</template>

<style scoped>
.order-evidence { overflow-wrap: anywhere; }
.order-evidence article { border-bottom: 1px solid var(--line); padding: 12px 0; }
.order-evidence p { color: var(--muted); }
@media (max-width: 900px) { .order-evidence :deep(button) { min-height: 44px; } }
</style>
