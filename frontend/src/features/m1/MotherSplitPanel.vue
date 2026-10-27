<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { ApiRequestError } from '../../services/apiClient';
import { storageText } from '../../services/masterDataApi';
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

const props = withDefaults(defineProps<{ motherOrderCode: string; intakeMode?: boolean }>(), { intakeMode: false });
const emit = defineEmits<{ arranged: [view: MotherOrderSplitView]; locked: [locked: boolean] }>();
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canArrange = computed(() => !session.value || (session.value.effectivePermissions ? session.value.effectivePermissions.includes('BUSINESS_MOTHER_SPLIT')
  : session.value.role === 'BUSINESS_MANAGER' ? session.value.coverDuties?.includes('SPLIT') : ['ADMIN', 'GENERAL_MANAGER', 'RECEIVER', 'WAREHOUSE_KEEPER', 'WORKSHOP_LEADER'].includes(session.value.role)));
const canPrint = computed(() => !session.value?.effectivePermissions || session.value.effectivePermissions.includes('LABEL_PRINT'));
const choice = ref('');
const stickerError = ref('');
const success = ref('');
type Arrangement = { key: string; kind: 'keep' | 'split'; code: string; requestId: string; lines?: import('../../services/motherSplitApi').SplitLineRequest[] };
const pending = ref<Arrangement | null>(null);
const locked = computed(() => submitting.value || Boolean(pending.value));
let generation = 0;
onBeforeUnmount(() => { generation += 1; });

const view = ref<MotherOrderSplitView | null>(null);
const stickers = ref<LabelPayload[]>([]);
const lines = ref<Array<{ quantity: string; weight: string; processes: string[];
  allocations: Array<{ sourceLineNo: number | null; quantity: string; weight: string }> }>>([blankLine(), blankLine()]);
const loadError = ref('');
const formError = ref('');
const submitting = ref(false);
watch(locked, value => emit('locked', value));
const requestIds = ref<Record<string, string>>({});

const multiMaterial = computed(() => new Set(view.value?.sourceLines?.map(source => source.materialCode) ?? []).size > 1);
const hasSources = computed(() => Boolean(view.value?.sourceLines?.length));
const canKeep = computed(() => canArrange.value && !multiMaterial.value && view.value != null && view.value.decision !== 'KEEP' && view.value.decision !== 'SPLIT' && view.value.children.length === 0);
const canSplit = computed(() => {
  if (!canArrange.value || !view.value || view.value.decision === 'KEEP') return false;
  return Number(view.value.remainingQuantity) > 0 && Number(view.value.remainingWeight) > 0;
});

watch(() => props.motherOrderCode, () => {
  void load();
}, { immediate: true });

function blankLine() {
  return { quantity: '', weight: '', processes: [...processOptions], allocations: [] as Array<{
    sourceLineNo: number | null; quantity: string; weight: string }> };
}

// 有来源时直接放一行可输入的数量和重量。只读框在手机上点了不会弹出键盘。
function draftLine() {
  const line = blankLine();
  const sources = view.value?.sourceLines ?? [];
  if (!sources.length) return line;
  line.allocations = [{ sourceLineNo: sources.length === 1 ? sources[0].lineNo : null, quantity: '', weight: '' }];
  return line;
}

async function load() {
  const seen = ++generation;
  const code = props.motherOrderCode;
  loadError.value = ''; formError.value = ''; stickerError.value = ''; success.value = '';
  view.value = null; stickers.value = []; lines.value = [blankLine(), blankLine()]; choice.value = '';
  if (!code) return;
  try {
    const current = await fetchMotherSplit(code);
    if (seen !== generation || code !== props.motherOrderCode) return;
    view.value = current;
    lines.value = [draftLine(), draftLine()];
    emit('arranged', current);
    await loadStickers(current);
  } catch (error) { if (seen === generation) loadError.value = error instanceof Error ? error.message : '母单拆分信息加载失败'; }
}

async function loadStickers(current: MotherOrderSplitView) {
  const seen = generation;
  stickerError.value = '';
  if (!canPrint.value || (current.decision !== 'KEEP' && !current.children.length)) { stickers.value = []; return; }
  try {
    const settled = await Promise.allSettled([
      fetchMotherLabel(current.motherOrderCode), ...current.children.map(child => fetchWorkOrderLabel(child.code)),
    ]);
    if (seen !== generation || current.motherOrderCode !== props.motherOrderCode) return;
    stickers.value = settled.flatMap(result => result.status === 'fulfilled' ? [result.value] : []);
    const failures = settled.filter(result => result.status === 'rejected');
    if (failures.length) stickerError.value = `生产安排已保存，${failures.length} 张标签待处理：` + failures.map(result => result.status === 'rejected' ? String(result.reason instanceof Error ? result.reason.message : result.reason) : '').join('；');
  } catch (error) { if (seen === generation) stickerError.value = error instanceof Error ? error.message : '标签读取失败，可单独重试'; }
}

function scaled(value: string | number): bigint {
  const text = String(value).trim();
  if (!/^\d+(\.\d{1,3})?$/.test(text)) throw new Error('数量和重量最多三位小数，请填写实称数据');
  const [whole, fraction = ''] = text.split('.');
  const result = BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0'));
  if (result > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('数值过大，请分开核对录入');
  return result;
}
function decimal(value: bigint): string { return `${value / 1000n}.${String(value % 1000n).padStart(3, '0')}`.replace(/\.?0+$/, '') || '0'; }
function allocationTotal(line: typeof lines.value[number], key: 'quantity' | 'weight'): string {
  try { return decimal(line.allocations.reduce((sum, row) => sum + (row[key].trim() ? scaled(row[key]) : 0n), 0n)); }
  catch { return ''; }
}
function fillRemaining(lineIndex: number, allocationIndex: number) {
  if (locked.value) return;
  const row = lines.value[lineIndex].allocations[allocationIndex];
  const source = view.value?.sourceLines?.find(source => source.lineNo === row.sourceLineNo);
  if (!source) { formError.value = '请先选择来源明细'; return; }
  try {
    const others = lines.value.flatMap(line => line.allocations).filter(item => item !== row && item.sourceLineNo === row.sourceLineNo);
    const quantity = scaled(source.remainingQuantity) - others.reduce((sum, item) => sum + (item.quantity.trim() ? scaled(item.quantity) : 0n), 0n);
    const weight = scaled(source.remainingWeight) - others.reduce((sum, item) => sum + (item.weight.trim() ? scaled(item.weight) : 0n), 0n);
    if (quantity <= 0n || weight <= 0n) throw new Error('该来源剩余数量或实称重量不足，请核对其他草稿分配');
    row.quantity = decimal(quantity); row.weight = decimal(weight); formError.value = '';
  } catch (error) { formError.value = error instanceof Error ? error.message : '剩余量读取失败'; }
}
function generateSourceDrafts() {
  if (locked.value || !view.value?.sourceLines?.length) return;
  if (lines.value.some(line => line.quantity || line.weight || line.allocations.some(row => row.quantity || row.weight)) && !globalThis.confirm('重新生成会覆盖当前拆分草稿，继续吗？')) return;
  lines.value = view.value.sourceLines.filter(source => source.remainingQuantity > 0 && source.remainingWeight > 0).map(source => ({
    ...blankLine(), allocations: [{ sourceLineNo: source.lineNo, quantity: String(source.remainingQuantity), weight: String(source.remainingWeight) }],
  }));
  while (lines.value.length < 2) lines.value.push(draftLine());
  formError.value = ''; choice.value = 'split';
}

function toggleProcess(index: number, name: string) {
  const current = lines.value[index].processes;
  const next = current.includes(name)
    ? current.filter((item) => item !== name)
    : [...current, name];
  lines.value[index].processes = processOptions.filter((item) => next.includes(item));
}

function addLine() {
  lines.value.push(draftLine());
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

async function executeArrangement(attempt: Arrangement) {
  if (submitting.value || !canArrange.value) return;
  const seen = generation;
  submitting.value = true; pending.value = attempt; formError.value = '';
  try {
    const current = attempt.kind === 'keep' ? await keepMotherOrder(attempt.code, attempt.requestId) : await splitMotherOrder(attempt.code, attempt.lines!, attempt.requestId);
    if (seen !== generation || attempt.code !== props.motherOrderCode) return;
    pending.value = null; clearRequestId(attempt.key); view.value = current; emit('arranged', current);
    success.value = attempt.kind === 'keep' ? '整单生产安排已保存' : '子单已生成，剩余来源可稍后继续分配';
    if (attempt.kind === 'split') lines.value = [draftLine(), draftLine()];
    void loadStickers(current);
  } catch (error) {
    if (seen !== generation) return;
    if (error instanceof ApiRequestError && error.status >= 400 && error.status < 500 && error.status !== 408) pending.value = null;
    formError.value = error instanceof Error ? error.message : '生产安排结果待核对，请使用原请求重试';
  } finally { submitting.value = false; }
}
async function keepWhole() {
  if (locked.value || !canKeep.value) return;
  await executeArrangement({ key: `keep|${props.motherOrderCode}`, kind: 'keep', code: props.motherOrderCode, requestId: ensureRequestId(`keep|${props.motherOrderCode}`, 'keep') });
}
async function splitMany() {
  if (locked.value || !canSplit.value || !view.value) return;
  formError.value = '';
  try {
    const parsed = lines.value.map(line => ({
      quantity: Number(hasSources.value ? allocationTotal(line, 'quantity') : line.quantity),
      weight: Number(hasSources.value ? allocationTotal(line, 'weight') : line.weight), processes: [...line.processes],
      ...(hasSources.value ? { allocations: line.allocations.map(row => ({ sourceLineNo: Number(row.sourceLineNo), quantity: Number(decimal(scaled(row.quantity))), weight: Number(decimal(scaled(row.weight))) })) } : {}),
    }));
    if (parsed.length < 2) throw new Error('拆成多张至少需要两张子单');
    if (parsed.some(line => !Number.isFinite(line.quantity) || line.quantity <= 0 || !Number.isFinite(line.weight) || line.weight <= 0)) throw new Error('每张子单的数量和重量必须大于 0');
    if (parsed.some(line => !line.processes.length)) throw new Error('每张子单至少选择一道正式工序');
    if (hasSources.value) {
      const used = new Map<number, { quantity: bigint; weight: bigint }>();
      for (const line of parsed) {
        if (!line.allocations?.length) throw new Error('每张子单必须分配来源明细');
        const materials = new Set<string>(); const selected = new Set<number>();
        for (const row of line.allocations) {
          const source = view.value.sourceLines!.find(source => source.lineNo === row.sourceLineNo);
          if (!source || selected.has(row.sourceLineNo) || row.quantity <= 0 || row.weight <= 0) throw new Error('来源明细无效、重复或分配量不大于零');
          selected.add(row.sourceLineNo); materials.add(source.materialCode);
          const sum = used.get(row.sourceLineNo) ?? { quantity: 0n, weight: 0n };
          sum.quantity += scaled(row.quantity); sum.weight += scaled(row.weight); used.set(row.sourceLineNo, sum);
          if (sum.quantity > scaled(source.remainingQuantity) || sum.weight > scaled(source.remainingWeight)) throw new Error(`来源 ${source.lineNo} 的数量或重量超过剩余`);
        }
        if (materials.size > 1) throw new Error('一张子单不能跨材质，请分别拆单');
      }
    }
    const quantity = parsed.reduce((sum, line) => sum + scaled(line.quantity), 0n);
    const weight = parsed.reduce((sum, line) => sum + scaled(line.weight), 0n);
    if (quantity > scaled(view.value.remainingQuantity)) throw new Error('拆出的数量合计不得超过母单剩余');
    if (weight > scaled(view.value.remainingWeight)) throw new Error('拆出的重量合计不得超过母单剩余');
    const key = `split|${props.motherOrderCode}|${JSON.stringify(parsed)}`;
    await executeArrangement({ key, kind: 'split', code: props.motherOrderCode, requestId: ensureRequestId(key, 'split'), lines: parsed });
  } catch (error) { formError.value = error instanceof Error ? error.message : '请核对子单来源分配'; }
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
          <p>保存：{{ storageText(source.storageRequirements) }}</p>
        </li>
      </ul>
      <p v-if="success" class="operation-feedback">{{ success }}</p>
      <p v-if="!view.decision">已入库 · 待安排生产；打印收货标签不确认不拆。</p>
      <p v-if="multiMaterial">多材质母单必须按来源拆成子单，不能整单生产。</p>
      <div v-if="intakeMode && canArrange && view.decision !== 'KEEP'" class="button-row"><button v-if="canKeep" type="button" class="primary-button" :disabled="locked" @click="keepWhole">整单生产</button><button v-if="canSplit" type="button" class="secondary-button" :disabled="locked" @click="choice = 'split'">拆成子单</button><button type="button" class="secondary-button" :disabled="locked" @click="choice = 'later'">稍后安排</button></div>
      <p v-if="choice === 'later'">已保留待安排状态，可从本单详情继续。</p>
      <button v-if="canKeep && !intakeMode" class="primary-button" type="button" :disabled="submitting" @click="keepWhole">
        不拆，预览流转贴纸
      </button>
      <p v-if="canKeep" class="split-summary">确认不拆后将按整张母单生产，不能再拆成子单；预览后点击“打印这张”出纸。</p>

      <fieldset v-if="canSplit && (!intakeMode || choice === 'split')" class="split-form" :disabled="locked">
        <h4>按来源分配拆成多张</h4>
        <p>直接填写每张子单的数量和重量。有多项来源时按来源分别填写，合计不得超过剩余。以下工序是正式路线，加工要求文字不会自动转成工序。</p>
        <button v-if="hasSources" type="button" class="secondary-button" @click="generateSourceDrafts">按明细生成草稿</button>
        <article v-for="(line, index) in lines" :key="index" class="split-line">
          <strong>第 {{ index + 1 }} 张</strong>
          <label v-if="!hasSources || line.allocations.length === 1" class="field">
            <span>数量（{{ view.quantityUnit }}）</span>
            <input v-if="!hasSources" v-model="line.quantity" type="text" inputmode="decimal" autocomplete="off" autocapitalize="off" enterkeyhint="done" :aria-label="`第${index + 1}张数量`" />
            <input v-else :value="line.allocations[0].quantity" type="text" inputmode="decimal" autocomplete="off" autocapitalize="off" enterkeyhint="done" :aria-label="`第${index + 1}张数量`" @input="line.allocations[0].quantity = ($event.target as HTMLInputElement).value" />
          </label>
          <label v-if="!hasSources || line.allocations.length === 1" class="field">
            <span>重量（克）</span>
            <input v-if="!hasSources" v-model="line.weight" type="text" inputmode="decimal" autocomplete="off" autocapitalize="off" enterkeyhint="done" :aria-label="`第${index + 1}张重量`" />
            <input v-else :value="line.allocations[0].weight" type="text" inputmode="decimal" autocomplete="off" autocapitalize="off" enterkeyhint="done" :aria-label="`第${index + 1}张重量`" @input="line.allocations[0].weight = ($event.target as HTMLInputElement).value" />
          </label>
          <p v-else class="split-summary">本张合计 {{ allocationTotal(line, 'quantity') || '0' }} {{ view.quantityUnit }} / {{ allocationTotal(line, 'weight') || '0' }} 克</p>
          <div v-if="view.sourceLines?.length" class="source-allocations">
            <div v-for="(allocation, allocationIndex) in line.allocations" :key="allocationIndex" class="split-line">
              <label class="field"><span>来源明细</span><select v-model.number="allocation.sourceLineNo" :aria-label="`第${index + 1}张来源${allocationIndex + 1}`" @change="allocation.quantity = ''; allocation.weight = ''">
                <option :value="null">请选择来源</option>
                <option v-for="source in view.sourceLines" :key="source.lineNo" :value="source.lineNo">第 {{ source.lineNo }} 行 · {{ source.materialName }} · 剩余 {{ source.remainingQuantity }} {{ source.quantityUnit }}</option>
              </select></label>
              <label v-show="line.allocations.length !== 1" class="field"><span>分配数量</span><input v-model="allocation.quantity" type="text" inputmode="decimal" autocomplete="off" autocapitalize="off" enterkeyhint="done" :aria-label="`第${index + 1}张分配数量${allocationIndex + 1}`" /></label>
              <label v-show="line.allocations.length !== 1" class="field"><span>分配重量（克）</span><input v-model="allocation.weight" type="text" inputmode="decimal" autocomplete="off" autocapitalize="off" enterkeyhint="done" :aria-label="`第${index + 1}张分配重量${allocationIndex + 1}`" /></label>
              <button type="button" class="table-action" @click="fillRemaining(index, allocationIndex)">填入剩余量</button>
              <button v-if="line.allocations.length > 1" type="button" class="table-action" @click="line.allocations.splice(allocationIndex, 1)">移除来源</button>
            </div>
            <button v-if="(view.sourceLines?.length ?? 0) > 1" type="button" class="table-action" @click="line.allocations.push({ sourceLineNo: null, quantity: '', weight: '' })">增加来源明细</button>
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
      </fieldset>

      <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>

      <p v-if="pending" class="operation-feedback warning">安排结果待核对，原提交快照保留。<button type="button" :disabled="submitting" @click="executeArrangement(pending)">用原请求核对生产安排</button></p>
      <p v-if="stickerError" class="operation-feedback warning">{{ stickerError }} <button type="button" :disabled="submitting" @click="loadStickers(view)">只重试标签</button></p>
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
          <button v-if="canPrint" class="table-action" type="button" @click="openLabelPrint(sticker)">打印这张</button>
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
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 220px));
  justify-content: start;
  gap: 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
}
.split-line > strong,
.split-line > button,
.split-line > .source-allocations,
.split-line > .process-checks,
.split-line > .split-summary,
.split-line > .field:has(select) { grid-column: 1 / -1; }
.split-line input,
.split-line select {
  width: 100%;
  min-width: 0;
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
@media (max-width: 900px) {
  .split-line { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .split-line input,
  .split-line select { font-size: 16px; }
}
@media (max-width: 600px) {
  .split-line { grid-template-columns: 1fr; }
  .sticker-list { --sticker-scale: 1.1; }
}
</style>
