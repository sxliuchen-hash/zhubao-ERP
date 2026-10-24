<script setup lang="ts">
import { computed, provide, ref, watch } from 'vue';
import ProcessProgressPanel from '../m1/ProcessProgressPanel.vue';
import ProductionPanel from '../production/ProductionPanel.vue';
import type { ProcessStationReport } from '../../services/processStationApi';
import type { OrderActionTarget, ProductionMode } from './orderActions';

const props = withDefaults(defineProps<{ target: OrderActionTarget; active: boolean; initialMode?: ProductionMode; entryRevision: number }>(), { initialMode: 'progress' });
const emit = defineEmits<{ changed: []; selected: [target: OrderActionTarget, mode: ProductionMode] }>();
const mode = ref<ProductionMode>(props.initialMode);
const progressCreated = ref(false);
const stationCreated = ref(false);
const deliveredProcess = ref('');
provide('zhubao-scan-suspended', computed(() => !props.active));
watch(() => props.entryRevision, () => { mode.value = props.initialMode; }, { immediate: true });
watch(mode, value => { if (value === 'progress') progressCreated.value = true; else stationCreated.value = true; }, { immediate: true });
function delivered(result: ProcessStationReport) { deliveredProcess.value = result.processName; emit('changed'); }
</script>

<template>
  <div class="production-action-pane field-job page-stack" :inert="!active || undefined">
    <p class="action-lead">当前目标 {{ target.code }}。工序进度与正式交工各自保存，不共用请求编号。</p>
    <nav class="production-modes" aria-label="工序办理方式">
      <button class="secondary-button" type="button" :aria-pressed="mode === 'progress'" @click="mode = 'progress'">更新进度</button>
      <button class="secondary-button" type="button" :aria-pressed="mode === 'station'" @click="mode = 'station'">正式交工</button>
    </nav>
    <div v-show="mode === 'progress'" :inert="mode !== 'progress' || undefined">
      <ProcessProgressPanel v-if="progressCreated" embedded :initial-target="target" :formally-delivered-process="deliveredProcess" @saved="emit('changed')" @target-selected="emit('selected', $event, mode)" />
    </div>
    <div v-show="mode === 'station'" :inert="mode !== 'station' || undefined">
      <ProductionPanel v-if="stationCreated" field embedded :initial-target="target" title="正式交工" intro="逐条核对本岗位要求，再按原单据数量和重量办理交付。" @saved="delivered" @target-selected="emit('selected', $event, mode)" />
    </div>
  </div>
</template>

<style scoped>
.action-lead { margin: 0; color: var(--muted); }
.production-modes { display: flex; gap: 10px; flex-wrap: wrap; }
.production-modes button { min-height: 44px; }
.production-modes button[aria-pressed="true"] { color: var(--action); background: var(--action-soft); }
.production-action-pane { padding-bottom: 0; }
.production-action-pane :deep(.field-dock) { position: sticky; left: auto; right: auto; bottom: 0; width: auto; margin: 0; transform: none; }
@media (max-width: 900px) { .action-lead { display: none; } }
</style>
