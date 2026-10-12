<script setup lang="ts">
import { computed, inject, reactive, ref } from 'vue';
import type { Ref } from 'vue';
import OrderContextCard from './OrderContextCard.vue';
import { canonicalTarget, useScanContext } from './useScanContext';
import { useTraceTarget } from './useTraceTarget';
import type { TraceTarget } from './traceTarget';
import type { ScanIntent, ScanObjectType, ScanRelatedTarget } from '../../services/scanContextApi';

const scan = reactive(useScanContext());
const traceTarget = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
const openTab = inject<(tab: string) => void>('zhubao-open-tab', () => {});

useTraceTarget(['LOOKUP'], (target) => {
  void scan.lookup({
    content: target.code,
    intent: 'LOOKUP',
    ...(target.objectType ? { objectType: target.objectType } : {}),
  });
});

const identity = computed(() => canonicalTarget(scan.context));
const canJump = computed(() => Boolean(identity.value) && !scan.context?.requiresSelection);

const jumps = computed(() => {
  const type = identity.value?.objectType;
  if (!type || !canJump.value) return [];
  return destinations(type);
});

function destinations(type: ScanObjectType) {
  if (type === 'OUTBOUND_ORDER') {
    return [{ intent: 'SHIPPING' as const, tab: 'inventory', label: '去发货' }];
  }
  if (type === 'REWORK') {
    return [{ intent: 'QUALITY' as const, tab: 'quality', label: '去质检' }];
  }
  if (type === 'WORK_ORDER') {
    return [
      { intent: 'PRODUCTION' as const, tab: 'production', label: '去报工' },
      { intent: 'QUALITY' as const, tab: 'quality', label: '去质检' },
      { intent: 'SHIPPING' as const, tab: 'inventory', label: '去发货' },
    ];
  }
  if (type === 'MOTHER_ORDER') {
    return [
      { intent: 'INTAKE' as const, tab: 'inbound', label: '去收货' },
      { intent: 'PRODUCTION' as const, tab: 'production', label: '去报工' },
      { intent: 'QUALITY' as const, tab: 'quality', label: '去质检' },
    ];
  }
  return [{ intent: 'INTAKE' as const, tab: 'inbound', label: '去收货' }];
}

function choose(related: ScanRelatedTarget) {
  if (!related.code || !related.objectType) return;
  void scan.lookup({ content: related.code, objectType: related.objectType, intent: 'LOOKUP' });
}

function jump(intent: ScanIntent, tab: string) {
  const target = identity.value;
  if (!target || scan.context?.requiresSelection || !traceTarget.value) return;
  traceTarget.value = {
    objectType: target.objectType,
    code: target.code,
    intent,
    token: traceTarget.value.token + 1,
  };
  openTab(tab);
}
</script>

<template>
  <section class="section-block" aria-label="追溯回看">
    <h2>追溯回看</h2>
    <p>这里按服务端返回的单据回看收货、工序、质检和发货。没有记录的环节留空，不补样例。</p>
    <p v-if="scan.loading">正在查询</p>
    <p v-if="scan.error" class="operation-feedback danger">{{ scan.error }}</p>
    <OrderContextCard :context="scan.context" @select="choose" />
    <p v-if="scan.context?.requiresSelection">请先选择具体单据，再进入收货、报工、质检或发货。</p>
    <div v-if="canJump" class="button-row">
      <button
        v-for="item in jumps"
        :key="item.label"
        class="secondary-button"
        type="button"
        :aria-label="item.label"
        @click="jump(item.intent, item.tab)"
      >
        {{ item.label }}
      </button>
    </div>
  </section>
</template>
