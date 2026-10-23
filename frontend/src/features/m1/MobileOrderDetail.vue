<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import ScanInput from '../scan/ScanInput.vue';
import ProgressNoteForm from '../scan/ProgressNoteForm.vue';
import type { ScanDraft } from '../scan/scanDraft';
import { appendProgressRecord, progressTargetFromScan } from '../scan/progressTarget';
import { canonicalTarget, useScanContext } from '../scan/useScanContext';
import { scanObjectTypeLabel, type ScanContextResponse, type ScanProgressRecord, type ScanRelatedTarget } from '../../services/scanContextApi';

type MobileAction = 'intake' | 'station' | 'progress' | 'quality' | 'shipping';

const props = defineProps<{
  role: string;
  coverDuties?: string[];
  allowedActions?: MobileAction[];
  initialTarget?: { code: string; objectType: NonNullable<ScanContextResponse['target']['objectType']> } | null;
}>();
const emit = defineEmits<{ open: [action: MobileAction, target?: { code: string; objectType: NonNullable<ScanContextResponse['target']['objectType']> }] }>();
const code = ref('');
const locatedInput = ref('');
const qualityProgressOpen = ref(false);
const scan = reactive(useScanContext());
const identity = computed(() => canonicalTarget(scan.context));
const qualityProgressTarget = computed(() => progressTargetFromScan(scan.context));
const hasDuty = (code: string) => props.role !== 'BUSINESS_MANAGER' || (props.coverDuties ?? []).includes(code);
const canOpen = (action: MobileAction) => !props.allowedActions || props.allowedActions.includes(action);
const isReceiving = computed(() => canOpen('intake') && hasDuty('INTAKE') && ['ADMIN', 'BUSINESS_MANAGER', 'RECEIVER', 'WAREHOUSE_KEEPER'].includes(props.role));
const isProduction = computed(() => (canOpen('station') || canOpen('progress')) && hasDuty('REPORT') && ['ADMIN', 'BUSINESS_MANAGER', 'OPERATOR', 'WORKSHOP_LEADER'].includes(props.role));
const isQuality = computed(() => canOpen('quality') && hasDuty('QUALITY') && ['ADMIN', 'BUSINESS_MANAGER', 'QUALITY_INSPECTOR', 'WORKSHOP_LEADER'].includes(props.role));
const isWarehouse = computed(() => canOpen('shipping') && (['ADMIN', 'WAREHOUSE_KEEPER'].includes(props.role)
  || (props.role === 'BUSINESS_MANAGER' && (props.coverDuties ?? []).includes('SHIP'))));
const isWorkTarget = computed(() => ['MOTHER_ORDER', 'WORK_ORDER'].includes(identity.value?.objectType ?? ''));
const isQualityTarget = computed(() => isWorkTarget.value || identity.value?.objectType === 'REWORK');
const formalReport = computed(() => scan.context?.actions.find((item) => item.code === 'REPORT_PROCESS'));
const formalQuality = computed(() => scan.context?.actions.find((item) => item.code === 'SUBMIT_QUALITY'));

onMounted(() => {
  if (!props.initialTarget) return;
  code.value = props.initialTarget.code;
  locatedInput.value = props.initialTarget.code;
  void scan.lookup({ content: props.initialTarget.code, objectType: props.initialTarget.objectType, intent: 'LOOKUP' });
});

watch(code, (value) => {
  if (value.trim() !== locatedInput.value && scan.context) scan.invalidate();
});
watch(identity, () => { qualityProgressOpen.value = false; });

function quantity(value: number | null, unit: string | null): string {
  return value === null ? '未填写' : `${value}${unit ? ` ${unit}` : ''}`;
}

async function recognize(draft: ScanDraft) {
  locatedInput.value = draft.content.trim();
  await scan.lookup({ content: draft.content, objectType: draft.objectType, intent: 'LOOKUP' });
}

async function choose(target: ScanRelatedTarget) {
  if (!target.code || !target.objectType) return;
  code.value = target.code;
  locatedInput.value = target.code;
  await scan.lookup({ content: target.code, objectType: target.objectType, intent: 'LOOKUP' });
}

function open(action: MobileAction) {
  if (!canOpen(action)) return;
  const target = identity.value;
  emit('open', action, target ? { code: target.code, objectType: target.objectType } : undefined);
}

function rememberProgress(record: ScanProgressRecord) {
  appendProgressRecord(scan.context, record);
}
</script>

<template>
  <section class="mobile-order-detail page-stack" aria-label="手机订单详情">
    <header class="mobile-order-heading">
      <h2>扫码看订单</h2>
      <p>扫订单贴纸或运单，核对要求后办理本岗事项。</p>
    </header>
    <ScanInput
      v-model="code"
      field-text="订单贴纸或运单"
      input-aria-label="订单贴纸或运单"
      submit-label="查看订单"
      bind-mobile-scan
      :busy="scan.loading"
      @draft="scan.invalidate"
      @recognize="recognize"
    />
    <p v-if="scan.loading" role="status">正在查找订单…</p>
    <p v-if="scan.error" class="operation-feedback danger" role="alert">{{ scan.error }}</p>

    <article v-if="scan.context?.requiresSelection" class="section-block mobile-order-card">
      <h3>请选择要处理的单据</h3>
      <p>同一贴纸关联多张单据，先选定一张。</p>
      <div class="mobile-order-choices">
        <button
          v-for="item in scan.context.relatedTargets"
          :key="`${item.objectType}-${item.code}`"
          type="button"
          class="secondary-button"
          @click="choose(item)"
        >
          {{ scanObjectTypeLabel(item.objectType) }} {{ item.code }}
          <small v-if="item.requirement">{{ item.requirement }}</small>
        </button>
      </div>
    </article>

    <article v-if="identity && scan.context" class="section-block mobile-order-card">
      <div class="mobile-order-title">
        <div>
          <p class="mobile-order-kicker">{{ scanObjectTypeLabel(identity.objectType) }}</p>
          <h3>{{ identity.code }}</h3>
        </div>
        <span v-if="scan.context.progress.quality" class="mobile-order-status">
          质检{{ scan.context.progress.quality.outcome || '待确认' }}
        </span>
      </div>
      <dl v-if="identity.objectType !== 'EXPRESS_NO'" class="mobile-order-facts">
        <div><dt>货品</dt><dd>{{ scan.context.order.goodsName || '未填写' }}</dd></div>
        <div><dt>数量</dt><dd>{{ quantity(scan.context.order.quantity, scan.context.order.quantityUnit) }}</dd></div>
        <div><dt>交期</dt><dd>{{ scan.context.order.dueDate || '未填写' }}</dd></div>
        <div v-if="isReceiving"><dt>客户</dt><dd>{{ scan.context.order.customerName || '未填写' }}</dd></div>
        <div v-if="isReceiving"><dt>重量</dt><dd>{{ quantity(scan.context.order.weight, scan.context.order.weightUnit) }}</dd></div>
      </dl>
      <p v-if="identity.objectType === 'EXPRESS_NO'" class="mobile-order-requirement">这张运单尚未定位到订单，可进入收货建单。</p>
      <section v-else class="mobile-order-requirements" aria-label="订单要求">
        <h4>当前要求</h4>
        <p v-if="scan.context.requirements.workOrderRequirement">子单：{{ scan.context.requirements.workOrderRequirement }}</p>
        <p v-if="scan.context.requirements.motherRequirement">母单：{{ scan.context.requirements.motherRequirement }}</p>
        <p v-if="!scan.context.requirements.workOrderRequirement && !scan.context.requirements.motherRequirement">暂无加工要求</p>
        <ul v-if="isProduction && scan.context.requirements.stationRequirements.length">
          <li v-for="item in scan.context.requirements.stationRequirements" :key="`${item.process}-${item.text}`">
            {{ item.process || '当前工序' }}：{{ item.text || '未填写' }}
          </li>
        </ul>
      </section>
      <p v-if="scan.context.progress.handoffs.length" class="mobile-order-latest">
        最近交工：{{ scan.context.progress.handoffs.at(-1)?.process || '工序' }}
      </p>
      <div class="mobile-order-actions" aria-label="本岗操作">
        <button v-if="isReceiving" class="primary-button" type="button" @click="open('intake')">
          {{ identity.objectType === 'EXPRESS_NO' ? '创建收货订单' : '查看收货与订单资料' }}
        </button>
        <button v-if="isProduction && canOpen('progress') && isWorkTarget" class="primary-button" type="button" @click="open('progress')">更新工序进度</button>
        <button v-if="isProduction && canOpen('station') && isWorkTarget && formalReport?.allowed" class="secondary-button" type="button" @click="open('station')">正式交工</button>
        <button v-if="isQuality && isQualityTarget && qualityProgressTarget" class="primary-button" type="button" @click="qualityProgressOpen = !qualityProgressOpen">记录质检过程</button>
        <button v-if="isQuality && isQualityTarget" class="secondary-button" type="button" @click="open('quality')">逐条判定质检</button>
        <button v-if="isWarehouse && identity.objectType === 'OUTBOUND_ORDER'" class="secondary-button" type="button" @click="open('shipping')">办理发货</button>
      </div>
      <ProgressNoteForm
        v-if="qualityProgressOpen && qualityProgressTarget"
        stage="QUALITY"
        :target-type="qualityProgressTarget.targetType"
        :target-code="qualityProgressTarget.targetCode"
        :mother-order-code="qualityProgressTarget.motherOrderCode"
        @saved="rememberProgress"
      />
      <p v-if="isProduction && isWorkTarget && formalReport && !formalReport.allowed" class="mobile-order-hint">正式交工：{{ formalReport.blockReason || '当前暂不可办理' }}</p>
      <p v-if="isQuality && isQualityTarget && formalQuality && !formalQuality.allowed" class="mobile-order-hint">检验结果：{{ formalQuality.blockReason || '请在质检页查看当前状态' }}</p>
      <details v-if="isReceiving && identity.objectType !== 'EXPRESS_NO'" class="mobile-order-more">
        <summary>查看流转摘要</summary>
        <p>已交工 {{ scan.context.progress.handoffs.length }} 次 · 过程记录 {{ scan.context.progress.progressRecords.length }} 条 · 发货 {{ scan.context.progress.shipments.length }} 次</p>
      </details>
    </article>

    <button v-if="isReceiving && !identity" type="button" class="secondary-button mobile-order-create" @click="open('intake')">没有贴纸？直接新建收货订单</button>
    <details class="mobile-order-shortcuts">
      <summary>直接进入本岗作业</summary>
      <div class="mobile-order-actions">
        <button v-if="isReceiving" type="button" class="secondary-button" @click="open('intake')">收货</button>
        <button v-if="isProduction && canOpen('station')" type="button" class="secondary-button" @click="open('station')">岗位报工</button>
        <button v-if="isProduction && canOpen('progress')" type="button" class="secondary-button" @click="open('progress')">进度报工</button>
        <button v-if="isQuality" type="button" class="secondary-button" @click="open('quality')">质检</button>
        <button v-if="isWarehouse" type="button" class="secondary-button" @click="open('shipping')">发货</button>
      </div>
    </details>
  </section>
</template>

<style scoped>
.mobile-order-heading h2, .mobile-order-card h3 { margin: 0; }
.mobile-order-heading p { margin: 4px 0 0; color: var(--muted); }
.mobile-order-card { display: grid; gap: 12px; }
.mobile-order-title { display: flex; justify-content: space-between; align-items: start; gap: 12px; }
.mobile-order-kicker { margin: 0 0 3px; color: var(--muted); font-size: .85rem; }
.mobile-order-status { padding: 5px 9px; border-radius: 999px; background: var(--surface-soft, var(--content)); white-space: nowrap; }
.mobile-order-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; margin: 0; }
.mobile-order-facts div { min-width: 0; }
.mobile-order-facts dt { color: var(--muted); font-size: .82rem; }
.mobile-order-facts dd { margin: 2px 0 0; overflow-wrap: anywhere; }
.mobile-order-requirements { border-top: 1px solid var(--line); padding-top: 12px; }
.mobile-order-requirements h4, .mobile-order-requirements p { margin: 0 0 7px; }
.mobile-order-requirements ul { margin: 0; padding-left: 20px; }
.mobile-order-actions, .mobile-order-choices { display: grid; gap: 8px; }
.mobile-order-choices button { text-align: left; }
.mobile-order-choices small { display: block; margin-top: 4px; }
.mobile-order-latest, .mobile-order-hint, .mobile-order-requirement { margin: 0; color: var(--muted); }
.mobile-order-more, .mobile-order-shortcuts { border-top: 1px solid var(--line); padding-top: 10px; }
.mobile-order-shortcuts .mobile-order-actions { margin-top: 10px; }
</style>
