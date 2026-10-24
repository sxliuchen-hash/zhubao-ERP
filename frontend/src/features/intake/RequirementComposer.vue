<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { fetchShortcuts, fetchTemplates, type RequirementShortcut, type RequirementTemplate } from '../../services/masterDataApi';
import { composeRequirement } from './requirementText';

const props = withDefaults(defineProps<{
  modelValue: string;
  shortcuts?: RequirementShortcut[];
  canManage?: boolean;
  templateCode?: string;
  templateVersion?: number;
}>(), {
  shortcuts: undefined,
  canManage: false,
  templateCode: '',
  templateVersion: 0,
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'update:templateCode': [value: string];
  'update:templateVersion': [value: number];
  'add-shortcut': [phrase: string];
  'remove-shortcut': [item: RequirementShortcut];
}>();

const templates = ref<RequirementTemplate[]>([]);
const ownShortcuts = ref<RequirementShortcut[]>([]);
const steps = ref<string[]>([]);
const supplement = ref('');
const customStep = ref('');
const newShortcut = ref('');
const manual = ref(Boolean(props.modelValue.trim()));
const error = ref('');
const writing = ref(false);
const selectedCode = ref(props.templateCode);
const finalText = ref(props.modelValue);

const phrases = computed(() => (props.shortcuts ?? ownShortcuts.value));

onMounted(() => {
  void loadTemplates();
});

watch(() => props.templateCode, (code) => {
  selectedCode.value = code;
  if (!code && !props.modelValue.trim()) {
    steps.value = [];
    supplement.value = '';
    manual.value = false;
    finalText.value = '';
    error.value = '';
  }
});

watch(() => props.modelValue, (value) => {
  if (value === finalText.value) return;
  finalText.value = value;
  manual.value = Boolean(value.trim());
  steps.value = [];
  supplement.value = '';
  error.value = '';
});

async function loadTemplates() {
  try {
    const rows = await fetchTemplates(false);
    templates.value = Array.isArray(rows) ? rows : [];
  } catch {
    templates.value = [];
    error.value = '加工要求模板加载失败，可继续手填要求或重试加载';
  }
  if (props.shortcuts) return;
  try {
    const rows = await fetchShortcuts();
    ownShortcuts.value = Array.isArray(rows) ? rows : [];
  } catch {
    ownShortcuts.value = [];
  }
}

function publish(text: string) {
  if (text.length > 500) {
    error.value = '加工要求超过 500 字，请缩短后再保存';
    return false;
  }
  error.value = '';
  finalText.value = text;
  writing.value = true;
  emit('update:modelValue', text);
  queueMicrotask(() => {
    writing.value = false;
  });
  return true;
}

function confirmOverwrite() {
  if (!manual.value) return true;
  return globalThis.confirm('已修改的加工要求会被当前步骤覆盖，补充说明会保留。继续吗？');
}

function addStep(phrase: string) {
  const text = phrase.trim();
  if (!text || steps.value.includes(text)) return;
  if (text.length > 40 || /[、,，;；\r\n]/.test(text) || steps.value.length >= 12) {
    error.value = '每项步骤最多 40 字且不含分隔符，最多选择 12 项';
    return;
  }
  if (!confirmOverwrite()) return;
  const next = [...steps.value, text];
  if (!publish(composeRequirement(next, supplement.value))) return;
  manual.value = false;
  steps.value = next;
}

function removeStep(index: number) {
  if (!confirmOverwrite()) return;
  const next = steps.value.filter((_, current) => current !== index);
  if (!publish(composeRequirement(next, supplement.value))) return;
  manual.value = false;
  steps.value = next;
}

function moveStep(index: number, offset: number) {
  const target = index + offset;
  if (target < 0 || target >= steps.value.length || !confirmOverwrite()) return;
  const copy = [...steps.value];
  const [item] = copy.splice(index, 1);
  copy.splice(target, 0, item);
  if (!publish(composeRequirement(copy, supplement.value))) return;
  manual.value = false;
  steps.value = copy;
}

function applyTemplate(code: string) {
  selectedCode.value = code;
  if (!code) {
    emit('update:templateCode', '');
    emit('update:templateVersion', 0);
    return;
  }
  const template = templates.value.find((item) => item.templateCode === code);
  if (!template) return;
  if (!confirmOverwrite()) {
    selectedCode.value = props.templateCode;
    return;
  }
  const nextSteps = [...template.steps];
  if (!publish(composeRequirement(nextSteps, supplement.value))) {
    selectedCode.value = props.templateCode;
    return;
  }
  manual.value = false;
  steps.value = nextSteps;
  selectedCode.value = template.templateCode;
  emit('update:templateCode', template.templateCode);
  emit('update:templateVersion', template.version);
}

function onSupplement(value: string) {
  if (!confirmOverwrite()) return;
  if (!publish(composeRequirement(steps.value, value))) return;
  supplement.value = value;
  manual.value = false;
}

function onFinalInput(event: Event) {
  if (writing.value) return;
  manual.value = true;
  finalText.value = (event.target as HTMLTextAreaElement).value;
  error.value = finalText.value.length > 500 ? '加工要求超过 500 字，请缩短后再保存' : '';
  emit('update:modelValue', finalText.value);
}

function submitShortcut() {
  const phrase = newShortcut.value.trim();
  if (!phrase) return;
  emit('add-shortcut', phrase);
  newShortcut.value = '';
}
</script>

<template>
  <div class="requirement-composer">
    <p v-if="error" class="operation-feedback danger">{{ error }} <button type="button" @click="loadTemplates">重试加载模板</button></p>
    <label>
      <span>加工要求模板</span>
      <select :value="selectedCode" aria-label="加工要求模板" @change="applyTemplate(($event.target as HTMLSelectElement).value)">
        <option value="">不使用模板，自行勾选</option>
        <option v-for="item in templates" :key="item.templateCode" :value="item.templateCode">{{ item.name }}</option>
      </select>
    </label>
    <div class="shortcut-list" aria-label="加工要求快捷词">
      <span v-for="item in phrases" :key="item.shortcutId" class="shortcut-chip">
        <button type="button" class="shortcut-phrase" role="checkbox" :aria-checked="steps.includes(item.phrase)" :aria-label="`填入快捷词 ${item.phrase}`" @click="steps.includes(item.phrase) ? removeStep(steps.indexOf(item.phrase)) : addStep(item.phrase)">{{ steps.includes(item.phrase) ? '☑' : '☐' }} {{ item.phrase }}</button>
        <button v-if="canManage" type="button" class="shortcut-remove" :aria-label="`删除快捷词 ${item.phrase}`" @click="emit('remove-shortcut', item)">×</button>
      </span>
      <p v-if="!phrases.length" class="empty-hint">还没有快捷词。有档案维护权限时可以添加常用加工要求。</p>
    </div>
    <details v-if="canManage" class="info-disclosure"><summary>维护快捷词</summary>
    <div class="catalog-create">
      <span>自定义快捷词</span>
      <input v-model="newShortcut" aria-label="新快捷词" placeholder="例如：镜面抛光" />
      <button type="button" class="secondary-button" @click="submitShortcut">添加快捷词</button>
    </div>
    </details>
    <label>
      <span>自定义步骤</span>
      <input v-model="customStep" aria-label="自定义加工步骤" placeholder="例如：车形（车成珠型）" @keydown.enter.prevent="addStep(customStep); customStep = ''" />
    </label>
    <button type="button" class="secondary-button" @click="addStep(customStep); customStep = ''">加入步骤</button>
    <ol v-if="steps.length" class="composer-steps" aria-label="已选加工步骤">
      <li v-for="(step, index) in steps" :key="`${step}-${index}`">
        <span>{{ index + 1 }}. {{ step }}</span>
        <button type="button" :aria-label="`上移 ${step}`" @click="moveStep(index, -1)">上移</button>
        <button type="button" :aria-label="`下移 ${step}`" @click="moveStep(index, 1)">下移</button>
        <button type="button" :aria-label="`取消步骤 ${step}`" @click="removeStep(index)">取消</button>
      </li>
    </ol>
    <label>
      <span>本单补充说明</span>
      <textarea :value="supplement" aria-label="本单补充说明" @input="onSupplement(($event.target as HTMLTextAreaElement).value)" />
    </label>
    <p class="requirement-final-text"><strong>将写入母单的加工要求</strong><span>{{ finalText || '尚未填写加工要求' }}</span></p>
    <details class="info-disclosure"><summary>直接编辑最终要求</summary>
    <label>
      <span>最终要求全文</span>
      <textarea :value="finalText" aria-label="加工要求" @input="onFinalInput" />
    </label>
    </details>
  </div>
</template>

<style scoped>
.requirement-composer { display: grid; gap: 8px; min-width: 0; }
.requirement-final-text { display: grid; gap: 6px; margin: 0; padding: 12px 0; border-top: 1px solid var(--line); overflow-wrap: anywhere; white-space: pre-wrap; }
.requirement-composer select,
.requirement-composer input,
.requirement-composer textarea { width: 100%; max-width: 100%; box-sizing: border-box; }
.shortcut-list, .composer-steps { display: flex; flex-wrap: wrap; gap: 6px; margin: 0; padding: 0; list-style: none; }
.composer-steps li, .shortcut-chip { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
.requirement-composer button { min-height: 36px; }
</style>
