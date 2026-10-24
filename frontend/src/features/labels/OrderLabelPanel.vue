<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, provide, reactive, ref, watch, type Ref } from 'vue';
import { fetchMotherLabel, fetchWorkOrderLabel, reprintLabel, type LabelPayload, type LabelType } from '../../services/labelApi';
import type { AuthSession } from '../../services/authApi';
import { ApiRequestError } from '../../services/apiClient';
import type { OrderActionTarget } from '../orders/orderActions';
import type { ScanDraft } from '../scan/scanDraft';
import type { TraceTarget } from '../scan/traceTarget';
import type { ScanRelatedTarget } from '../../services/scanContextApi';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import ScanInput from '../scan/ScanInput.vue';
import OrderContextCard from '../scan/OrderContextCard.vue';
import PrintJobPanel from './PrintJobPanel.vue';
import { buildLabelMarkup, labelFaceCss, labelPreviewScale, openLabelPrint } from '../m1/labelPrint';

const props = withDefaults(defineProps<{ initialTarget?: OrderActionTarget | null; externalPayload?: LabelPayload | null; active?: boolean }>(), { initialTarget: null, externalPayload: null, active: true });
const emit = defineEmits<{ selected: [target: OrderActionTarget]; previewChanged: [hasPreview: boolean] }>();
const openLabel = inject<((target: OrderActionTarget) => void) | null>('zhubao-open-label-target', null);
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canPrint = computed(() => !session.value?.effectivePermissions || session.value.effectivePermissions.includes('LABEL_PRINT'));
provide('zhubao-scan-suspended', computed(() => !props.active));
const code = ref('');
const scan = reactive(useScanContext());
const preview = ref<LabelPayload | null>(null);
const lastPayload = ref<LabelPayload | null>(null);
const external = ref(false);
const loading = ref(false);
const hint = ref('');
const error = ref('');
const reprintReason = ref('');
const reprintUnknown = ref(false);
const printLocked = ref(false);
const locked = computed(() => loading.value || printLocked.value || reprintUnknown.value);
const loadedSticker = ref<{ type: LabelType; code: string } | null>(null);
let disposed = false;
onBeforeUnmount(() => { disposed = true; });
watch(preview, value => { if (value) lastPayload.value = value; emit('previewChanged', Boolean(value)); });
function invalidate() { if (locked.value) return; scan.invalidate(); preview.value = null; loadedSticker.value = null; external.value = false; error.value = ''; hint.value = '单号已变化，请重新查询'; }
watch(code, invalidate, { flush: 'sync' });
watch(() => props.externalPayload, value => {
  if (props.initialTarget || locked.value) return;
  if (!value) { if (external.value) preview.value = null; return; }
  external.value = true; preview.value = value; loadedSticker.value = null;
});
const incoming = inject<Ref<TraceTarget | null>>('zhubao-label-target', ref(null));
watch(incoming, target => { if (!props.initialTarget && target?.code && !locked.value) { code.value = target.code; void locate({ content: target.code, ...(target.objectType ? { objectType: target.objectType } : {}) }); } }, { immediate: true });
onMounted(() => { if (props.initialTarget) { code.value = props.initialTarget.code; void locate({ content: props.initialTarget.code, ...(props.initialTarget.objectType ? { objectType: props.initialTarget.objectType } : {}) }); } });
const explicitMother = computed(() => scan.context?.requiresSelection && scan.context.target.objectType === 'MOTHER_ORDER' && scan.context.target.code ? scan.context.target.code : '');
async function locate(draft: ScanDraft) {
  if (locked.value) return;
  error.value = ''; hint.value = ''; preview.value = null; loadedSticker.value = null; external.value = false;
  const context = await scan.lookup({ content: draft.content, ...(draft.objectType ? { objectType: draft.objectType } : {}), intent: 'LOOKUP' });
  if (!context) { if (scan.error) error.value = scan.error; return; }
  const explicit = props.initialTarget ?? (draft.objectType ? { objectType: draft.objectType, code: draft.content.trim() } : null);
  const mother = explicit?.objectType === 'MOTHER_ORDER' && context.target.objectType === 'MOTHER_ORDER' && explicit.code === context.target.code ? { objectType: 'MOTHER_ORDER' as const, code: explicit.code } : null;
  const target = canonicalTarget(context) ?? mother;
  if (!target) { hint.value = '请选择母单或子单，不会自动选择第一张'; return; }
  if (props.initialTarget && (target.code !== props.initialTarget.code || (props.initialTarget.objectType && target.objectType !== props.initialTarget.objectType))) { scan.invalidate(); error.value = '标签目标与当前单据或执行轮次不一致，未打开原单标签。'; return; }
  if (target.objectType !== 'MOTHER_ORDER' && target.objectType !== 'WORK_ORDER') { error.value = '该对象没有可用的母单或子单标签接口，请从原单核对。'; return; }
  if (!props.initialTarget && openLabel) { openLabel({ objectType: target.objectType, code: target.code }); return; }
  const token = scan.revision;
  loading.value = true;
  try {
    const payload = target.objectType === 'WORK_ORDER' ? await fetchWorkOrderLabel(target.code) : await fetchMotherLabel(target.code);
    if (disposed || token !== scan.revision) return;
    const expectedType = target.objectType === 'WORK_ORDER' ? 'WORK_ORDER' : 'MOTHER';
    if (payload.primaryCode !== target.code || payload.type !== expectedType) { error.value = '标签返回的类型或单号与本单不一致，未开放打印。'; return; }
    preview.value = payload; loadedSticker.value = { type: payload.type, code: payload.primaryCode };
    hint.value = `已从单据打开${payload.typeLabel}标签：${payload.primaryCode}。`;
    if (!payload.fields.some(field => field.name === '验证码' && field.value.trim())) hint.value += '收货母单标签可直接打印；生产流转前仍须在来料页选择不拆或拆单。';
  } catch (caught) { if (!disposed && token === scan.revision) error.value = caught instanceof Error ? caught.message : '标签读取失败'; }
  finally { if (!disposed && token === scan.revision) loading.value = false; }
}
function choose(target: ScanRelatedTarget) {
  if (!target.code || !target.objectType || locked.value) return;
  if (props.initialTarget && (target.code !== props.initialTarget.code || target.objectType !== props.initialTarget.objectType)) { emit('selected', { objectType: target.objectType, code: target.code }); return; }
  void locate({ content: target.code, objectType: target.objectType });
}
async function reprint() {
  if (locked.value || !canPrint.value) return;
  if (!loadedSticker.value) { error.value = '请先按单号打开现场贴纸'; return; }
  if (!reprintReason.value.trim()) { error.value = '补打必须填写原因'; return; }
  loading.value = true; error.value = '';
  try {
    const result = await reprintLabel({ type: loadedSticker.value.type, primaryCode: loadedSticker.value.code, reason: reprintReason.value.trim() });
    if (disposed) return;
    if (result.primaryCode !== loadedSticker.value.code || result.type !== loadedSticker.value.type) throw new Error('补打回执与本单不一致，请保留原原因核对');
    preview.value = result; hint.value = `已记录补打：${result.primaryCode}。尚未创建新的打印批次。`;
  } catch (caught) {
    if (disposed) return;
    const known = caught instanceof ApiRequestError && caught.status >= 400 && caught.status < 500 && caught.status !== 408;
    reprintUnknown.value = !known;
    error.value = caught instanceof Error ? caught.message : '补打记录失败';
  } finally { loading.value = false; }
}
const markup = computed(() => preview.value ? buildLabelMarkup(preview.value) : '');
const sheetStyle = computed(() => ({ width: `calc(${preview.value?.printerSetting.widthMm ?? 40}mm * var(--label-scale, ${labelPreviewScale}))`, height: `calc(${preview.value?.printerSetting.heightMm ?? 30}mm * var(--label-scale, ${labelPreviewScale}))` }));
const scaleStyle = computed(() => ({ transform: `scale(var(--label-scale, ${labelPreviewScale}))`, transformOrigin: 'top left', width: `${preview.value?.printerSetting.widthMm ?? 40}mm`, height: `${preview.value?.printerSetting.heightMm ?? 30}mm` }));
function printBrowser() { if (preview.value && canPrint.value && !locked.value) openLabelPrint(preview.value); }
function officialPrint() {
  if (!preview.value || !openLabel || !['MOTHER', 'WORK_ORDER'].includes(preview.value.type)) return;
  openLabel({ objectType: preview.value.type === 'MOTHER' ? 'MOTHER_ORDER' : 'WORK_ORDER', code: preview.value.primaryCode });
}
</script>

<template>
  <section class="order-label-panel page-stack" :inert="!active || undefined" aria-label="单据标签办理">
    <div class="label-layout">
      <section class="label-forms form-card">
        <h3>现场贴纸</h3><p>母单、子单分别从原单读取；多个对象先选择，不使用样例字段。</p>
        <fieldset v-if="!initialTarget" class="label-plain-fields" :disabled="locked"><ScanInput v-model="code" field-text="单号或二维码" input-aria-label="现场贴纸单号" submit-label="打开贴纸" submit-aria-label="打开现场贴纸" photo-aria-label="拍贴纸" :busy="locked || scan.loading" @draft="invalidate" @recognize="locate" /></fieldset>
        <button v-else class="secondary-button" type="button" :disabled="locked || scan.loading" @click="locate({ content: initialTarget.code, ...(initialTarget.objectType ? { objectType: initialTarget.objectType } : {}) })">重新读取本单标签</button>
        <p v-if="loading || scan.loading" role="status">正在读取标签…</p><p v-if="hint" class="operation-feedback">{{ hint }}</p><p v-if="error" class="operation-feedback danger" role="alert">{{ error }}</p>
        <p v-if="reprintUnknown" class="operation-feedback warning">补打记录结果待核对，原单及原因保留，尚未提交新的打印任务。</p>
        <button v-if="explicitMother" class="secondary-button" type="button" :disabled="locked" @click="choose({ objectType: 'MOTHER_ORDER', code: explicitMother, motherOrderCode: explicitMother, requirement: null, quantity: null, quantityUnit: null })">选择母单标签 {{ explicitMother }}</button>
        <OrderContextCard v-if="scan.context?.requiresSelection" :context="scan.context" @select="choose" />
        <details v-if="loadedSticker" class="info-disclosure"><summary>补打这张标签</summary>
        <fieldset class="label-plain-fields" :disabled="locked || !canPrint"><label class="field">补打原因<input v-model="reprintReason" aria-label="补打原因" placeholder="如 贴纸丢失" /></label><button class="secondary-button" type="button" :disabled="!loadedSticker || locked || !canPrint" @click="reprint">补打并记录</button></fieldset>
        </details>
      </section>
      <section v-if="preview" class="label-preview form-card" aria-label="标签预览">
        <header class="preview-toolbar"><strong>标签预览</strong><span>{{ preview.printerSetting.widthMm }}×{{ preview.printerSetting.heightMm }}mm · 黑白</span><button class="primary-button" type="button" :disabled="locked || !canPrint" @click="printBrowser">浏览器预览打印</button></header>
        <component :is="'style'">{{ labelFaceCss }}</component><div class="label-mat"><div class="label-sheet" :style="sheetStyle"><div class="label-scale" :style="scaleStyle" v-html="markup"></div></div></div>
        <p class="qr-content-line">二维码内容：{{ preview.qrContent }}。浏览器预览打印不等于厂内出纸。</p>
        <p v-if="external">当前为高级工具生成的预览。工位打印从正式单据重新读取字段和模板，任务内容以服务端冻结快照为准。</p><button v-if="external && openLabel && ['MOTHER', 'WORK_ORDER'].includes(preview.type)" class="secondary-button" type="button" :disabled="locked || !canPrint" @click="officialPrint">进入正式单据工位打印</button>
      </section>
      <section v-else class="label-preview form-card"><p>请先按单号打开贴纸或生成标签，成功后再预览和打印。</p></section>
    </div>
    <PrintJobPanel v-if="lastPayload" v-show="preview && !external" :payload="lastPayload" :disabled="!canPrint || reprintUnknown" @locked="printLocked = $event" />
  </section>
</template>

<style scoped>
.order-label-panel, .label-layout > * { min-width: 0; }
.label-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; align-items: start; }
.form-card { border: 1px solid var(--line); border-radius: 10px; padding: 14px; display: grid; gap: 10px; }
.label-plain-fields { padding: 0; margin: 0; border: 0; min-width: 0; display: grid; gap: 8px; }
h3, p { margin: 0; }
.field { display: grid; gap: 4px; }
input { width: 100%; min-width: 0; box-sizing: border-box; }
button, input { min-height: 44px; }
.preview-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.label-mat { overflow: auto; min-width: 0; padding: 12px; border: 1px solid var(--line); background: var(--workspace); }
.label-sheet { position: relative; }
.label-scale { position: absolute; left: 0; top: 0; }
.qr-content-line { overflow-wrap: anywhere; font-size: 12px; }
@media (max-width: 900px) { .label-layout { grid-template-columns: minmax(0, 1fr); } .label-preview { --label-scale: 1.1; } }
</style>
