<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { bindEvidence, uploadEvidenceFile } from '../../services/evidenceApi';
import {
  saveProgressRecord,
  type ProgressStage,
  type ScanProgressRecord,
} from '../../services/scanContextApi';

const props = defineProps<{
  stage: ProgressStage;
  targetType: string;
  targetCode: string;
  motherOrderCode?: string;
  processOptions?: string[];
}>();

const emit = defineEmits<{ saved: [record: ScanProgressRecord] }>();

const note = ref('');
const processName = ref('');
const clauseNo = ref('');
const message = ref('');
const saving = ref(false);
const evidencePending = ref(false);
const savedCode = ref('');
const savedFingerprint = ref('');
const requestId = ref(newRequestId());
const pendingFile = ref<File | null>(null);
const attachmentCode = ref('');

const titles: Record<ProgressStage, string> = {
  PRODUCTION: '生产过程说明',
  QUALITY: '质检过程说明',
  SHIPPING: '发货准备说明',
};

const hints: Record<ProgressStage, string> = {
  PRODUCTION: '这只追加生产过程说明，不会交付工序，也不会计算工资。扫码成功还不等于已经留证或已经报工。',
  QUALITY: '这只追加质检过程说明，不会提交质检结论。扫码成功还不等于已经留证或已经报工。',
  SHIPPING: '这只是发货准备，不会生成出货单，也不会扣库存。扫码成功还不等于已经留证或已经报工。',
};

const scenes: Record<ProgressStage, string> = {
  PRODUCTION: 'PROCESS',
  QUALITY: 'CHECK',
  SHIPPING: 'PACK',
};

const processes = computed(() => {
  if (props.stage !== 'PRODUCTION') return [];
  const given = (props.processOptions ?? []).map((item) => item.trim()).filter(Boolean);
  return given.length ? given : ['雕刻', '镶嵌', '抛光'];
});

watch(processes, (options) => {
  if (!options.includes(processName.value)) processName.value = options[0] ?? '';
}, { immediate: true });

watch(() => `${props.targetType}:${props.targetCode}`, (next, previous) => {
  if (!previous || next === previous) return;
  const hadFile = pendingFile.value !== null || attachmentCode.value !== '';
  pendingFile.value = null;
  attachmentCode.value = '';
  savedCode.value = '';
  savedFingerprint.value = '';
  evidencePending.value = false;
  requestId.value = newRequestId();
  message.value = hadFile ? '这份文件属于原单，不会转到新单' : '';
});

function newRequestId(): string {
  const random = globalThis.crypto?.randomUUID?.() ?? String(Date.now());
  return `req-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
}

function fingerprint(): string {
  return [
    props.targetType,
    props.targetCode,
    props.stage,
    processName.value.trim(),
    clauseNo.value.trim(),
    note.value.trim().replace(/\s+/g, ' '),
  ].join('\n');
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement;
  pendingFile.value = input.files?.[0] ?? null;
  attachmentCode.value = '';
  evidencePending.value = false;
}

async function save() {
  const text = note.value.trim().replace(/\s+/g, ' ');
  if (!text) {
    message.value = '请填写过程说明';
    return;
  }
  if (!props.targetCode.trim()) {
    message.value = '请先选定单据';
    return;
  }
  saving.value = true;
  try {
    const current = fingerprint();
    if (!savedCode.value || savedFingerprint.value !== current) {
      const saved = await saveProgressRecord({
        targetType: props.targetType,
        targetCode: props.targetCode.trim(),
        motherOrderCode: props.motherOrderCode?.trim() ?? '',
        stage: props.stage,
        process: props.stage === 'PRODUCTION' ? processName.value.trim() : '',
        clauseNo: props.stage === 'QUALITY' ? clauseNo.value.trim() : '',
        note: text,
        requestId: requestId.value,
      });
      savedCode.value = saved.code ?? '';
      savedFingerprint.value = current;
      evidencePending.value = false;
      message.value = saved.supplementary ? '补充记录已保存' : '过程说明已保存';
      emit('saved', saved);
    }
    if (pendingFile.value || attachmentCode.value) await attach();
  } catch (error) {
    message.value = savedCode.value
      ? '过程已保存，证据待补'
      : (error instanceof Error ? error.message : '过程说明保存失败');
  } finally {
    saving.value = false;
  }
}

async function attach() {
  if (!savedCode.value) return;
  try {
    if (pendingFile.value && !attachmentCode.value) {
      const uploaded = await uploadEvidenceFile({
        objectType: props.targetType,
        businessCode: props.targetCode.trim(),
        scene: scenes[props.stage],
        file: pendingFile.value,
      });
      attachmentCode.value = uploaded.code;
    }
    if (!attachmentCode.value) return;
    await bindEvidence({
      attachmentCode: attachmentCode.value,
      targetType: props.targetType,
      targetCode: props.targetCode.trim(),
      stage: props.stage,
      scene: scenes[props.stage],
      operationType: 'progress_note',
      operationCode: savedCode.value,
      processName: props.stage === 'PRODUCTION' ? processName.value.trim() : '',
      clauseNo: props.stage === 'QUALITY' ? clauseNo.value.trim() : '',
    });
    evidencePending.value = false;
    pendingFile.value = null;
    message.value = '过程说明已保存，证据已关联';
  } catch {
    evidencePending.value = true;
    message.value = '过程已保存，证据待补';
    throw new Error('过程已保存，证据待补');
  }
}

async function retryEvidence() {
  if (!savedCode.value) return;
  saving.value = true;
  try {
    await attach();
  } catch {
    message.value = '过程已保存，证据待补';
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="progress-note" :aria-label="titles[stage]">
    <h3>{{ titles[stage] }}</h3>
    <p class="evidence-hint">{{ hints[stage] }}</p>
    <label v-if="processes.length" class="field">
      <span>工序</span>
      <select v-model="processName" aria-label="过程工序">
        <option v-for="item in processes" :key="item" :value="item">{{ item }}</option>
      </select>
    </label>
    <label v-if="stage === 'QUALITY'" class="field">
      <span>条款号（可选）</span>
      <input v-model="clauseNo" type="text" aria-label="过程条款号">
    </label>
    <label class="field">
      <span>说明</span>
      <textarea v-model="note" aria-label="过程说明" />
    </label>
    <label class="field">
      <span>补充证据（可选）</span>
      <input aria-label="选择过程证据" type="file" accept="image/jpeg,image/png,video/mp4,video/webm" @change="onFile">
    </label>
    <div class="button-row">
      <button class="secondary-button" type="button" :disabled="saving" @click="save">保存过程说明</button>
      <button v-if="evidencePending" class="secondary-button" type="button" aria-label="重试证据" :disabled="saving" @click="retryEvidence">重试证据</button>
    </div>
    <p v-if="savedCode">记录编号 {{ savedCode }}</p>
    <p v-if="message" class="operation-feedback" :class="{ danger: evidencePending || message.includes('不会转到') || message.includes('失败') || message.includes('请') }">
      {{ message }}
    </p>
  </section>
</template>
