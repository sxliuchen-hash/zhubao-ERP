<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import {
  bindEvidence,
  fetchEvidenceBoard,
  uploadEvidenceFile,
  type EvidenceBoard,
  type HistoricalEvidence,
  type EvidenceLink,
} from '../../services/evidenceApi';
import EvidencePreviewLink from './EvidencePreviewLink.vue';

const props = withDefaults(defineProps<{
  targetType: string;
  targetCode: string;
  stage: string;
  scene: string;
  operationType?: string;
  operationCode?: string;
  processName?: string;
  clauseNo?: string;
}>(), {
  operationType: '',
  operationCode: '',
  processName: '',
  clauseNo: '',
});

type FileState = 'LOCAL_PENDING' | 'UPLOADING' | 'FAILED' | 'UPLOADED_PENDING_LINK' | 'LINKED';

interface Snap {
  targetType: string;
  targetCode: string;
  stage: string;
  scene: string;
  operationType: string;
  operationCode: string;
  processName: string;
  clauseNo: string;
}

interface DraftFile {
  id: number;
  file: File;
  name: string;
  previewUrl: string;
  state: FileState;
  message: string;
  attachmentCode: string;
  snap: Snap;
}

const PHOTO_MAX = 8 * 1024 * 1024;
const VIDEO_MAX = 50 * 1024 * 1024;
const stateText: Record<FileState, string> = {
  LOCAL_PENDING: '本地待上传',
  UPLOADING: '上传中',
  FAILED: '失败',
  UPLOADED_PENDING_LINK: '已上传待关联',
  LINKED: '已关联',
};

const items = ref<DraftFile[]>([]);
const formMessage = ref('');
const board = ref<EvidenceBoard>({ links: [], historical: [] });
const boardError = ref('');
const recoveringCode = ref('');
const recoveryErrors = ref<Record<string, string>>({});
const fileRef = ref<HTMLInputElement | null>(null);
const cameraRef = ref<HTMLInputElement | null>(null);
const videoRef = ref<HTMLInputElement | null>(null);
let nextId = 1;

function currentSnap(): Snap {
  return {
    targetType: props.targetType,
    targetCode: props.targetCode.trim(),
    stage: props.stage,
    scene: props.scene,
    operationType: props.operationType.trim(),
    operationCode: props.operationCode.trim(),
    processName: props.processName.trim(),
    clauseNo: props.clauseNo.trim(),
  };
}

function problemOf(file: File): string {
  const type = file.type.toLowerCase();
  if (type === 'image/jpeg' || type === 'image/jpg' || type === 'image/png') {
    return file.size > PHOTO_MAX ? '图片超过 8MB，请重新选择更小的 JPEG 或 PNG。' : '';
  }
  if (type === 'video/mp4' || type === 'video/webm') {
    return file.size > VIDEO_MAX ? '视频超过 50MB，请重新选择更小的 MP4 或 WebM。' : '';
  }
  return '格式不支持。请重新选择 JPEG、PNG、MP4 或 WebM，不能只改扩展名。';
}

function remember(file: File) {
  const problem = problemOf(file);
  if (problem) {
    formMessage.value = problem;
    return;
  }
  formMessage.value = '';
  const previewUrl = typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : '';
  items.value.push({
    id: nextId,
    file,
    name: file.name,
    previewUrl,
    state: 'LOCAL_PENDING',
    message: props.targetCode.trim() ? '' : '尚无正式单号，文件留在当前表单',
    attachmentCode: '',
    snap: currentSnap(),
  });
  nextId += 1;
}

function onPicked(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) remember(file);
  input.value = '';
}

function cancelSelection() {
  formMessage.value = '';
}

function openCamera() {
  cameraRef.value?.click();
}

function openVideo() {
  videoRef.value?.click();
}

function resolveSnap(item: DraftFile): { snap: Snap; switched: boolean; waiting: boolean } {
  if (!item.snap.targetCode) {
    if (!props.targetCode.trim()) return { snap: item.snap, switched: false, waiting: true };
    return { snap: currentSnap(), switched: false, waiting: false };
  }
  const switched = item.snap.targetType !== props.targetType || item.snap.targetCode !== props.targetCode.trim();
  if (switched) return { snap: item.snap, switched: true, waiting: false };
  return {
    snap: {
      ...item.snap,
      operationType: props.operationType.trim(),
      operationCode: props.operationCode.trim(),
      processName: props.processName.trim(),
      clauseNo: props.clauseNo.trim(),
    },
    switched: false,
    waiting: false,
  };
}

async function submit(item: DraftFile) {
  const decision = resolveSnap(item);
  if (decision.waiting) {
    item.state = 'LOCAL_PENDING';
    item.message = '尚无正式单号，文件留在当前表单';
    return;
  }
  if (decision.switched) {
    item.message = `这份文件属于原单 ${decision.snap.targetCode}，不会转到新单`;
  }
  item.snap = decision.snap;
  if (!decision.snap.operationType || !decision.snap.operationCode) {
    item.state = item.attachmentCode ? 'UPLOADED_PENDING_LINK' : 'LOCAL_PENDING';
    item.message = item.attachmentCode
      ? '已上传，等正式操作编号后再关联'
      : '请先完成当前操作，取得编号后再上传并关联';
    return;
  }
  item.state = 'UPLOADING';
  try {
    if (!item.attachmentCode) {
      const uploaded = await uploadEvidenceFile({
        objectType: decision.snap.targetType,
        businessCode: decision.snap.targetCode,
        scene: decision.snap.scene,
        file: item.file,
      });
      item.attachmentCode = uploaded.code;
    }
    await bindEvidence({
      attachmentCode: item.attachmentCode,
      targetType: decision.snap.targetType,
      targetCode: decision.snap.targetCode,
      stage: decision.snap.stage,
      scene: decision.snap.scene,
      operationType: decision.snap.operationType,
      operationCode: decision.snap.operationCode,
      processName: decision.snap.processName,
      clauseNo: decision.snap.clauseNo,
    });
    item.state = 'LINKED';
    item.message = decision.switched ? item.message : '已关联到当前操作';
    await loadBoard();
  } catch (error) {
    const text = error instanceof Error ? error.message : '上传或关联失败';
    if (item.attachmentCode) {
      item.state = 'UPLOADED_PENDING_LINK';
      item.message = `业务记录保留，证据待补：${text}`;
    } else {
      item.state = 'FAILED';
      item.message = text;
    }
  }
}

async function loadBoard() {
  if (!props.targetType || !props.targetCode.trim()) {
    board.value = { links: [], historical: [] };
    boardError.value = '';
    return;
  }
  try {
    board.value = await fetchEvidenceBoard({
      targetType: props.targetType,
      targetCode: props.targetCode.trim(),
      stage: props.stage,
      operationType: props.operationType,
      operationCode: props.operationCode,
    });
    boardError.value = '';
  } catch (error) {
    board.value = { links: [], historical: [] };
    boardError.value = error instanceof Error ? error.message : '证据暂时无法打开';
  }
}

function previewKind(item: DraftFile): 'image' | 'video' | '' {
  if (item.file.type.startsWith('image/')) return 'image';
  if (item.file.type.startsWith('video/')) return 'video';
  return '';
}

function linkKey(link: EvidenceLink): string {
  return link.linkCode;
}

function historicalKey(item: HistoricalEvidence): string {
  return item.attachmentCode;
}

function canRecover(item: HistoricalEvidence): boolean {
  return item.fileState === 'UPLOADED_PENDING_LINK'
    && item.businessCode === props.targetCode.trim()
    && item.objectType === props.targetType
    && item.sceneCode === props.scene
    && Boolean(props.operationType.trim() && props.operationCode.trim());
}

async function recover(item: HistoricalEvidence) {
  if (!canRecover(item) || recoveringCode.value) return;
  recoveringCode.value = item.attachmentCode;
  delete recoveryErrors.value[item.attachmentCode];
  try {
    await bindEvidence({
      attachmentCode: item.attachmentCode,
      targetType: props.targetType,
      targetCode: props.targetCode.trim(),
      stage: props.stage,
      scene: props.scene,
      operationType: props.operationType.trim(),
      operationCode: props.operationCode.trim(),
      processName: props.processName.trim(),
      clauseNo: props.clauseNo.trim(),
    });
    await loadBoard();
  } catch (error) {
    recoveryErrors.value[item.attachmentCode] = error instanceof Error ? error.message : '关联失败';
  } finally {
    recoveringCode.value = '';
  }
}

watch(() => [props.targetType, props.targetCode, props.stage, props.operationType, props.operationCode], () => {
  loadBoard();
}, { immediate: true });

onBeforeUnmount(() => {
  for (const item of items.value) {
    if (item.previewUrl && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(item.previewUrl);
  }
});
</script>

<template>
  <section class="evidence-capture">
    <div class="section-heading">
      <div>
        <h3>现场证据</h3>
        <p>扫码成功不等于已经留证。拍照、录视频或选文件后需要上传并关联。</p>
      </div>
    </div>
    <p class="evidence-hint">视频请尽量控制在 30 秒内。系统只检查文件大小，不检查时长。</p>
    <div class="button-row">
      <button class="secondary-button" type="button" aria-label="拍照留证" @click="openCamera">拍照留证</button>
      <button class="secondary-button" type="button" aria-label="录视频留证" @click="openVideo">录视频留证</button>
      <button class="secondary-button" type="button" aria-label="取消选择" @click="cancelSelection">取消选择</button>
    </div>
    <input
      ref="cameraRef"
      class="still-capture"
      type="file"
      accept="image/jpeg,image/png"
      capture="environment"
      aria-label="拍摄照片证据"
      @change="onPicked"
      @cancel="cancelSelection"
    />
    <input
      ref="videoRef"
      class="still-capture"
      type="file"
      accept="video/mp4,video/webm"
      capture="environment"
      aria-label="录制视频证据"
      @change="onPicked"
      @cancel="cancelSelection"
    />
    <label class="field">
      <span>选择文件</span>
      <input
        ref="fileRef"
        type="file"
        accept="image/jpeg,image/png,video/mp4,video/webm"
        aria-label="选择证据文件"
        @change="onPicked"
        @cancel="cancelSelection"
      />
    </label>
    <p v-if="formMessage" class="operation-feedback danger">{{ formMessage }}</p>
    <ul v-if="items.length" class="evidence-file-list">
      <li v-for="item in items" :key="item.id" :data-state="item.state">
        <strong>{{ item.name }}</strong>
        <span>{{ stateText[item.state] }}</span>
        <img v-if="item.previewUrl && previewKind(item) === 'image'" class="evidence-preview" :src="item.previewUrl" alt="证据预览" />
        <video v-else-if="item.previewUrl && previewKind(item) === 'video'" class="evidence-preview" :src="item.previewUrl" controls />
        <p v-if="item.message">{{ item.message }}</p>
        <button
          class="primary-button"
          type="button"
          :disabled="item.state === 'UPLOADING' || item.state === 'LINKED'"
          @click="submit(item)"
        >
          {{ item.state === 'FAILED' || item.state === 'UPLOADED_PENDING_LINK' ? '重试' : '上传并关联' }}
        </button>
      </li>
    </ul>
    <p v-if="boardError" class="operation-feedback danger">{{ boardError }}</p>
    <ul v-if="board.links.length" class="evidence-file-list">
      <li v-for="link in board.links" :key="linkKey(link)">
        <strong>{{ link.fileName || link.attachmentCode }}</strong>
        <span>{{ link.sceneLabel }} / 已关联</span>
        <small>{{ link.operationType }} {{ link.operationCode }}</small>
        <EvidencePreviewLink v-if="link.previewUrl" :attachment-code="link.attachmentCode" />
        <span v-else-if="link.status === '已下线'">已下线，不能预览</span>
      </li>
    </ul>
    <ul v-if="board.historical.length" class="evidence-file-list">
      <li v-for="item in board.historical" :key="historicalKey(item)">
        <strong>{{ item.fileName || item.attachmentCode }}</strong>
        <span>{{ item.label }}</span>
        <EvidencePreviewLink v-if="item.previewUrl" :attachment-code="item.attachmentCode" />
        <button v-if="canRecover(item)" class="secondary-button" type="button" :disabled="Boolean(recoveringCode)" @click="recover(item)">
          {{ recoveringCode === item.attachmentCode ? '正在关联' : '关联到当前操作' }}
        </button>
        <small v-if="recoveryErrors[item.attachmentCode]" class="operation-feedback danger" role="alert">{{ recoveryErrors[item.attachmentCode] }}</small>
      </li>
    </ul>
  </section>
</template>
