<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { buildLabelMarkup, labelFaceCss, labelPreviewScale, openLabelPrint } from './labelPrint';
import { fetchMotherLabel, fetchWorkOrderLabel, type LabelPayload } from '../../services/labelApi';
import {
  fetchMotherSplit,
  keepMotherOrder,
  splitMotherOrder,
  type MotherOrderSplitView,
} from '../../services/motherSplitApi';

// 母单详情里选择不拆或拆成多张。贴纸字段从单据带出，不在这里手填。

const processOptions = ['雕刻', '镶嵌', '抛光'];

const props = defineProps<{
  motherOrderCode: string;
}>();

const view = ref<MotherOrderSplitView | null>(null);
const stickers = ref<LabelPayload[]>([]);
const lines = ref<Array<{ quantity: string; weight: string; processes: string[];
  allocations: Array<{ sourceLineNo: number | null; quantity: string; weight: string }> }>>([blankLine(), blankLine()]);
const loadError = ref('');
const formError = ref('');
const submitting = ref(false);
const requestIds = ref<Record<string, string>>({});

const canKeep = computed(() => view.value != null && view.value.decision !== 'KEEP' && view.value.decision !== 'SPLIT' && view.value.children.length === 0);
const canSplit = computed(() => {
  if (!view.value || view.value.decision === 'KEEP') return false;
  return Number(view.value.remainingQuantity) > 0 && Number(view.value.remainingWeight) > 0;
});

watch(() => props.motherOrderCode, () => {
  void load();
}, { immediate: true });

function blankLine() {
  return { quantity: '', weight: '', processes: [...processOptions], allocations: [] as Array<{
    sourceLineNo: number | null; quantity: string; weight: string }> };
}

async function load() {
  loadError.value = '';
  formError.value = '';
  view.value = null;
  stickers.value = [];
  if (!props.motherOrderCode) return;
  try {
    view.value = await fetchMotherSplit(props.motherOrderCode);
    await loadStickers(view.value);
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '母单拆分信息加载失败';
  }
}

async function loadStickers(current: MotherOrderSplitView) {
  if (current.decision === 'KEEP') {
    stickers.value = [await fetchMotherLabel(current.motherOrderCode)];
    return;
  }
  if (current.children.length === 0) {
    stickers.value = [];
    return;
  }
  const [mother, ...childStickers] = await Promise.all([
    fetchMotherLabel(current.motherOrderCode),
    ...current.children.map((child) => fetchWorkOrderLabel(child.code)),
  ]);
  stickers.value = [mother, ...childStickers];
}

function toggleProcess(index: number, name: string) {
  const current = lines.value[index].processes;
  const next = current.includes(name)
    ? current.filter((item) => item !== name)
    : [...current, name];
  lines.value[index].processes = processOptions.filter((item) => next.includes(item));
}

function addLine() {
  lines.value.push(blankLine());
}

function removeLine(index: number) {
  if (lines.value.length <= 2) return;
  lines.value.splice(index, 1);
}

function ensureRequestId(key: string, prefix: string) {
  const current = requestIds.value[key];
  if (current) return current;
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const created = `${prefix}-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
  requestIds.value = { ...requestIds.value, [key]: created };
  return created;
}

function clearRequestId(key: string) {
  if (!requestIds.value[key]) return;
  const next = { ...requestIds.value };
  delete next[key];
  requestIds.value = next;
}

async function keepWhole() {
  formError.value = '';
  submitting.value = true;
  const key = `keep|${props.motherOrderCode}`;
  const requestId = ensureRequestId(key, 'keep');
  try {
    view.value = await keepMotherOrder(props.motherOrderCode, requestId);
    clearRequestId(key);
    await loadStickers(view.value);
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '选择不拆失败';
  } finally {
    submitting.value = false;
  }
}

async function splitMany() {
  formError.value = '';
  if (!view.value) return;
  const hasSources = (view.value.sourceLines?.length ?? 0) > 0;
  const parsed = lines.value.map((line) => ({
    quantity: Number(line.quantity),
    weight: Number(line.weight),
    processes: [...line.processes],
    ...(hasSources ? { allocations: line.allocations.map((allocation) => ({ sourceLineNo: Number(allocation.sourceLineNo),
      quantity: Number(allocation.quantity), weight: Number(allocation.weight) })) } : {}),
  }));
  if (parsed.length < 2) {
    formError.value = '拆成多张至少需要两张子单';
    return;
  }
  if (parsed.some((line) => !Number.isFinite(line.quantity) || line.quantity <= 0 || !Number.isFinite(line.weight) || line.weight <= 0)) {
    formError.value = '每张子单的数量和重量必须大于 0';
    return;
  }
  if (parsed.some((line) => line.processes.length === 0)) {
    formError.value = '每张子单至少选择一道工序';
    return;
  }
  if (hasSources && parsed.some((line) => !line.allocations || line.allocations.length === 0
    || line.allocations.some((item) => !item.sourceLineNo || item.quantity <= 0 || item.weight <= 0)
    || round3(line.allocations.reduce((sum, item) => sum + item.quantity, 0)) !== round3(line.quantity)
    || round3(line.allocations.reduce((sum, item) => sum + item.weight, 0)) !== round3(line.weight))) {
    formError.value = '请逐行分配来源材质，且分配合计须等于子单数量和重量';
    return;
  }
  const quantity = round3(parsed.reduce((sum, line) => sum + line.quantity, 0));
  const weight = round3(parsed.reduce((sum, line) => sum + line.weight, 0));
  if (quantity > round3(Number(view.value.remainingQuantity))) {
    formError.value = '拆出的数量合计不得超过母单剩余';
    return;
  }
  if (weight > round3(Number(view.value.remainingWeight))) {
    formError.value = '拆出的重量合计不得超过母单剩余';
    return;
  }
  submitting.value = true;
  const fingerprint = parsed.map((line) => `${line.quantity}:${line.weight}:${line.processes.join(',')}:${JSON.stringify(line.allocations)}`).join('|');
  const key = `split|${props.motherOrderCode}|${fingerprint}`;
  const requestId = ensureRequestId(key, 'split');
  try {
    view.value = await splitMotherOrder(props.motherOrderCode, parsed, requestId);
    clearRequestId(key);
    lines.value = [blankLine(), blankLine()];
    await loadStickers(view.value);
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '拆分子单失败';
  } finally {
    submitting.value = false;
  }
}

function round3(value: number) {
  return Math.round(value * 1000) / 1000;
}

function sheetStyle(payload: LabelPayload) {
  return {
    width: `calc(${payload.printerSetting.widthMm}mm * var(--sticker-scale, ${labelPreviewScale}))`,
    height: `calc(${payload.printerSetting.heightMm}mm * var(--sticker-scale, ${labelPreviewScale}))`,
  };
}

function scaleStyle(payload: LabelPayload) {
  return {
    transform: `scale(var(--sticker-scale, ${labelPreviewScale}))`,
    transformOrigin: 'top left',
    width: `${payload.printerSetting.widthMm}mm`,
    height: `${payload.printerSetting.heightMm}mm`,
  };
}
</script>

<template>
  <section class="split-panel" aria-label="拆子单">
    <h3>拆子单和贴纸</h3>
    <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
    <template v-else-if="view">
      <p class="split-summary">
        {{ view.decisionLabel }} · 客户 {{ view.customerName || '未记录' }} · 要求 {{ view.processingRequirement || '未记录' }} · 期限 {{ view.dueDate || '未记录' }}
      </p>
      <p class="split-summary">
        剩余 {{ view.remainingQuantity }} {{ view.quantityUnit }} / {{ view.remainingWeight }} 克
      </p>
      <ul v-if="view.sourceLines?.length" class="child-list">
        <li v-for="source in view.sourceLines" :key="source.lineNo">
          来源 {{ source.lineNo }} · {{ source.materialName }}（{{ source.materialCode }}）· {{ source.goodsName }}：
          剩余 {{ source.remainingQuantity }} {{ source.quantityUnit }} / {{ source.remainingWeight }} 克
        </li>
      </ul>
      <button v-if="canKeep" class="primary-button" type="button" :disabled="submitting" @click="keepWhole">
        不拆，预览流转贴纸
      </button>
      <p v-if="canKeep" class="split-summary">确认不拆后将按整张母单生产，不能再拆成子单；预览后点击“打印这张”出纸。</p>

      <div v-if="canSplit" class="split-form">
        <h4>拆成多张</h4>
        <article v-for="(line, index) in lines" :key="index" class="split-line">
          <strong>第 {{ index + 1 }} 张</strong>
          <label class="field">
            <span>数量（{{ view.quantityUnit }}）</span>
            <input v-model="line.quantity" inputmode="decimal" :aria-label="`第${index + 1}张数量`" />
          </label>
          <label class="field">
            <span>重量（克）</span>
            <input v-model="line.weight" inputmode="decimal" :aria-label="`第${index + 1}张重量`" />
          </label>
          <div v-if="view.sourceLines?.length" class="source-allocations">
            <div v-for="(allocation, allocationIndex) in line.allocations" :key="allocationIndex" class="split-line">
              <label class="field"><span>来源明细</span><select v-model.number="allocation.sourceLineNo" :aria-label="`第${index + 1}张来源${allocationIndex + 1}`">
                <option :value="null">请选择来源</option>
                <option v-for="source in view.sourceLines" :key="source.lineNo" :value="source.lineNo">第 {{ source.lineNo }} 行 · {{ source.materialName }} · 剩余 {{ source.remainingQuantity }} {{ source.quantityUnit }}</option>
              </select></label>
              <label class="field"><span>分配数量</span><input v-model="allocation.quantity" inputmode="decimal" :aria-label="`第${index + 1}张分配数量${allocationIndex + 1}`" /></label>
              <label class="field"><span>分配重量（克）</span><input v-model="allocation.weight" inputmode="decimal" :aria-label="`第${index + 1}张分配重量${allocationIndex + 1}`" /></label>
              <button type="button" class="table-action" @click="line.allocations.splice(allocationIndex, 1)">移除来源</button>
            </div>
            <button type="button" class="table-action" @click="line.allocations.push({ sourceLineNo: null, quantity: '', weight: '' })">增加来源明细</button>
          </div>
          <div class="process-checks">
            <label v-for="name in processOptions" :key="name">
              <input
                type="checkbox"
                :checked="line.processes.includes(name)"
                :aria-label="`第${index + 1}张${name}`"
                @change="toggleProcess(index, name)"
              />
              {{ name }}
            </label>
          </div>
          <button v-if="lines.length > 2" class="table-action" type="button" @click="removeLine(index)">去掉这张</button>
        </article>
        <div class="split-actions">
          <button class="table-action" type="button" @click="addLine">再加一张</button>
          <button class="primary-button" type="button" aria-label="生成子单并预览贴纸" :disabled="submitting" @click="splitMany">生成子单并预览贴纸</button>
        </div>
      </div>

      <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>

      <ul v-if="view.children.length" class="child-list">
        <li v-for="child in view.children" :key="child.code">
          {{ child.code }} · 验证码 {{ child.verificationCode }} · {{ child.processes.join('、') }} · {{ child.quantity }} {{ view.quantityUnit }} / {{ child.weight }} 克
        </li>
      </ul>

      <div v-if="stickers.length" class="sticker-list" aria-label="贴纸预览">
        <component :is="'style'">{{ labelFaceCss }}</component>
        <article v-for="sticker in stickers" :key="sticker.primaryCode" class="sticker-card">
          <div class="label-sheet" :style="sheetStyle(sticker)">
            <div class="label-scale" :style="scaleStyle(sticker)" v-html="buildLabelMarkup(sticker)"></div>
          </div>
          <p>二维码 {{ sticker.qrContent }}</p>
          <button class="table-action" type="button" @click="openLabelPrint(sticker)">打印这张</button>
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.split-panel {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.split-panel h3,
.split-panel h4 {
  margin: 0;
}
.split-summary {
  margin: 0;
  color: var(--ink-muted);
  font-size: 13px;
  line-height: 1.5;
}
.split-line {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
}
.process-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
}
.split-actions,
.sticker-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.child-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
}
.sticker-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  overflow-wrap: anywhere;
}
.label-sheet {
  overflow: hidden;
  background: var(--content);
}
@media (max-width: 600px) {
  .sticker-list { --sticker-scale: 1.1; }
}
</style>
