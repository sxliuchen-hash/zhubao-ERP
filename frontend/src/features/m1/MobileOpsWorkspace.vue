<script setup lang="ts">
import { computed, inject, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import type { ScanObjectType } from '../../services/scanContextApi';
import type { TraceTarget } from '../scan/traceTarget';
import IntakePanel from '../intake/IntakePanel.vue';
import ProductionPanel from '../production/ProductionPanel.vue';
import QualityPanel from '../quality/QualityPanel.vue';
import ShippingPanel from '../shipping/ShippingPanel.vue';
import MobileOrderDetail from './MobileOrderDetail.vue';
import ProcessProgressPanel from './ProcessProgressPanel.vue';

type JobMode = 'detail' | 'intake' | 'station' | 'progress' | 'quality' | 'shipping';

const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const traceTarget = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
const jobMode = ref<JobMode>('detail');
const lastTarget = ref<{ code: string; objectType: ScanObjectType } | null>(null);
const progressCode = ref('');
const insecureContext = typeof window !== 'undefined' && window.isSecureContext === false;

const coverDuties = computed(() => authSession.value?.coverDuties ?? []);
const modePermission: Record<Exclude<JobMode, 'detail'>, string> = {
  intake: 'BUSINESS_INBOUND_CONFIRM',
  station: 'BUSINESS_PRODUCTION_REPORT',
  progress: 'BUSINESS_PRODUCTION_REPORT',
  quality: 'BUSINESS_QUALITY_FREEZE',
  shipping: 'BUSINESS_OUTBOUND_SHIPMENT',
};
const canUseMode = (mode: Exclude<JobMode, 'detail'>) =>
  !authSession.value?.effectivePermissions || authSession.value.effectivePermissions.includes(modePermission[mode]);

const primaryModes = computed(() => {
  const modes = (() => { switch (authSession.value?.role) {
    case 'ADMIN': return [{ key: 'intake', label: '收货' }, { key: 'station', label: '报工' }, { key: 'quality', label: '质检' }] as const;
    case 'BUSINESS_MANAGER': return [
      ...(coverDuties.value.includes('INTAKE') ? [{ key: 'intake' as const, label: '收货' }] : []),
      ...(coverDuties.value.includes('REPORT') ? [{ key: 'station' as const, label: '报工' }] : []),
      ...(coverDuties.value.includes('QUALITY') ? [{ key: 'quality' as const, label: '质检' }] : []),
    ];
    case 'RECEIVER':
    case 'WAREHOUSE_KEEPER': return [{ key: 'intake', label: '收货' }] as const;
    case 'OPERATOR': return [{ key: 'station', label: '报工' }] as const;
    case 'WORKSHOP_LEADER': return [{ key: 'station', label: '报工' }, { key: 'quality', label: '质检' }] as const;
    case 'QUALITY_INSPECTOR': return [{ key: 'quality', label: '质检' }] as const;
    default: return [];
  } })();
  return modes.filter((mode) => canUseMode(mode.key));
});
const extraModes = computed(() => {
  const modes = (() => { switch (authSession.value?.role) {
    case 'ADMIN': return [{ key: 'progress', label: '进度报工' }, { key: 'shipping', label: '发货' }] as const;
    case 'OPERATOR':
    case 'WORKSHOP_LEADER': return [{ key: 'progress', label: '进度报工' }] as const;
    case 'BUSINESS_MANAGER': return [
      ...(coverDuties.value.includes('REPORT') ? [{ key: 'progress' as const, label: '进度报工' }] : []),
      ...(coverDuties.value.includes('SHIP') ? [{ key: 'shipping' as const, label: '发货' }] : []),
    ];
    case 'WAREHOUSE_KEEPER': return [{ key: 'shipping', label: '发货' }] as const;
    default: return [];
  } })();
  return modes.filter((mode) => canUseMode(mode.key));
});

watch([primaryModes, extraModes], () => {
  if (jobMode.value !== 'detail' && ![...primaryModes.value, ...extraModes.value].some((mode) => mode.key === jobMode.value)) {
    jobMode.value = 'detail';
    lastTarget.value = null;
  }
});

function open(action: Exclude<JobMode, 'detail'>, target?: { code: string; objectType: ScanObjectType }) {
  if (![...primaryModes.value, ...extraModes.value].some((mode) => mode.key === action)) return;
  if (target) lastTarget.value = target;
  progressCode.value = target?.code ?? '';
  if (target && action !== 'progress') {
    const intent = action === 'intake' ? 'INTAKE' : action === 'station' ? 'PRODUCTION' : action === 'quality' ? 'QUALITY' : 'SHIPPING';
    traceTarget.value = { ...target, intent, token: (traceTarget.value?.token ?? 0) + 1 };
  }
  jobMode.value = action;
}
</script>

<template>
  <div class="page-stack intake-page field-job">
    <p v-if="insecureContext" class="operation-feedback danger">
      当前是 HTTP，手机打不开摄像头。请改用 https:// 加服务器地址打开本页。浏览器提示证书不受信任时，选择继续访问。
    </p>
    <p v-if="!primaryModes.length && !extraModes.length" class="operation-feedback">当前岗位没有现场提交操作，请到工作台查看可处理的单据。</p>
    <MobileOrderDetail
      v-if="jobMode === 'detail' && (primaryModes.length || extraModes.length)"
      :role="authSession?.role || ''"
      :cover-duties="authSession?.coverDuties ?? []"
      :allowed-actions="[...primaryModes, ...extraModes].map((mode) => mode.key)"
      :initial-target="lastTarget"
      @open="open"
    />
    <button v-if="jobMode !== 'detail'" class="secondary-button mobile-order-back" type="button" @click="jobMode = 'detail'">返回订单详情</button>

    <ShippingPanel
      v-if="jobMode === 'shipping' && extraModes.some((mode) => mode.key === 'shipping')"
      title="确认发货"
      intro="扫成品标签，核对库存、数量、重量和物流后提交。"
    />

    <QualityPanel
      v-if="jobMode === 'quality' && primaryModes.some((mode) => mode.key === 'quality')"
      field
      title="手机质检"
      intro="扫码核对加工要求，逐条判定后提交。"
    />

    <ProcessProgressPanel v-if="jobMode === 'progress' && extraModes.some((mode) => mode.key === 'progress')" :initial-code="progressCode" />

    <ProductionPanel
      v-if="jobMode === 'station' && primaryModes.some((mode) => mode.key === 'station')"
      field
      title="岗位报工"
      intro="扫码核对本岗位要求，确认后报工。"
    />

    <IntakePanel
      v-if="jobMode === 'intake' && primaryModes.some((mode) => mode.key === 'intake')"
      field
      title="手机收货"
      intro="扫快递或手输运单，核对来料后建母单。"
    />
  </div>
</template>

<style scoped>
.mobile-order-back { justify-self: start; }
</style>
