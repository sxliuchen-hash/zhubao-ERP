<script setup lang="ts">
import { computed, inject, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import IntakePanel from '../intake/IntakePanel.vue';
import ProductionPanel from '../production/ProductionPanel.vue';
import QualityPanel from '../quality/QualityPanel.vue';
import ShippingPanel from '../shipping/ShippingPanel.vue';
import ProcessProgressPanel from './ProcessProgressPanel.vue';

type JobMode = 'intake' | 'station' | 'progress' | 'quality' | 'shipping';

const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const jobMode = ref<JobMode>('intake');
const insecureContext = typeof window !== 'undefined' && window.isSecureContext === false;

const primaryModes = computed(() => {
  switch (authSession.value?.role) {
    case 'ADMIN': return [{ key: 'intake', label: '收货' }, { key: 'station', label: '报工' }, { key: 'quality', label: '质检' }] as const;
    case 'RECEIVER':
    case 'WAREHOUSE_KEEPER': return [{ key: 'intake', label: '收货' }] as const;
    case 'OPERATOR': return [{ key: 'station', label: '报工' }] as const;
    case 'WORKSHOP_LEADER': return [{ key: 'station', label: '报工' }, { key: 'quality', label: '质检' }] as const;
    case 'QUALITY_INSPECTOR': return [{ key: 'quality', label: '质检' }] as const;
    default: return [];
  }
});
const extraModes = computed(() => {
  switch (authSession.value?.role) {
    case 'ADMIN': return [{ key: 'progress', label: '进度报工' }, { key: 'shipping', label: '发货' }] as const;
    case 'OPERATOR':
    case 'WORKSHOP_LEADER': return [{ key: 'progress', label: '进度报工' }] as const;
    case 'WAREHOUSE_KEEPER': return [{ key: 'shipping', label: '发货' }] as const;
    default: return [];
  }
});

watch([primaryModes, extraModes], () => {
  if (![...primaryModes.value, ...extraModes.value].some((mode) => mode.key === jobMode.value)) {
    jobMode.value = primaryModes.value[0]?.key ?? extraModes.value[0]?.key ?? 'intake';
  }
}, { immediate: true });
</script>

<template>
  <div class="page-stack intake-page field-job">
    <p v-if="insecureContext" class="operation-feedback danger">
      当前是 HTTP，手机打不开摄像头。请改用 https:// 加服务器地址打开本页。浏览器提示证书不受信任时，选择继续访问。
    </p>
    <div v-if="primaryModes.length > 1 || extraModes.length" class="field-modes" role="tablist" aria-label="现场作业">
      <button
        v-for="mode in primaryModes"
        :key="mode.key"
        :class="jobMode === mode.key ? 'primary-button' : 'secondary-button'"
        type="button"
        role="tab"
        :aria-selected="jobMode === mode.key"
        :aria-label="mode.label"
        @click="jobMode = mode.key"
      >
        {{ mode.label }}
      </button>
    </div>
    <details v-if="extraModes.length" class="field-extra-modes" :open="extraModes.some((mode) => mode.key === jobMode) || undefined">
      <summary>其他现场操作</summary>
      <div class="field-extra-actions">
        <button
          v-for="mode in extraModes"
          :key="mode.key"
          :class="jobMode === mode.key ? 'primary-button' : 'secondary-button'"
          type="button"
          :aria-pressed="jobMode === mode.key"
          @click="jobMode = mode.key"
        >
          {{ mode.label }}
        </button>
      </div>
    </details>
    <p v-if="!primaryModes.length && !extraModes.length" class="operation-feedback">当前岗位没有现场提交操作，请到工作台查看可处理的单据。</p>

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

    <ProcessProgressPanel v-if="jobMode === 'progress' && extraModes.some((mode) => mode.key === 'progress')" />

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
.field-modes {
  grid-template-columns: repeat(auto-fit, minmax(88px, 1fr));
}

.field-extra-modes {
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--content);
  padding: 8px 12px;
}

.field-extra-modes summary {
  min-height: 36px;
  align-content: center;
  color: var(--muted);
  cursor: pointer;
}

.field-extra-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 8px;
  padding-top: 8px;
}
</style>
