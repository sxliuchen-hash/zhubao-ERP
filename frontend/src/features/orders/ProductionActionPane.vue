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
const progressLocked = ref(false);
const stationLocked = ref(false);
const locked = computed(() => progressLocked.value || stationLocked.value);
provide('zhubao-scan-suspended', computed(() => !props.active));
watch(() => props.entryRevision, () => { if (!locked.value) mode.value = props.initialMode; }, { immediate: true });
watch(mode, value => { if (value === 'progress') progressCreated.value = true; else stationCreated.value = true; }, { immediate: true });
function delivered(result: ProcessStationReport) { if (result.processCompleted !== false) deliveredProcess.value = result.processName; emit('changed'); }
function switchMode(next: ProductionMode) { if (!locked.value) mode.value = next; }
</script>

<template>
  <div class="production-action-pane field-job page-stack" :inert="!active || undefined">
    <p class="action-lead">当前单据 {{ target.code }}</p>
    <nav class="production-modes" aria-label="工序办理方式">
      <button class="secondary-button" type="button" :disabled="locked" :aria-pressed="mode === 'progress'" @click="switchMode('progress')">加工中，记进度<small>记录本工序做到哪里</small></button>
      <button class="secondary-button" type="button" :disabled="locked" :aria-pressed="mode === 'station'" @click="switchMode('station')">已做完，去交工<small>分批登记本次交出数量</small></button>
    </nav>
    <p v-if="locked" class="info-context" role="status">当前请求正在提交或等待结果核对，请先处理原请求。</p>
    <div v-show="mode === 'progress'" :inert="mode !== 'progress' || undefined">
      <ProcessProgressPanel v-if="progressCreated" embedded :initial-target="target" :formally-delivered-process="deliveredProcess" @saved="emit('changed')" @locked="progressLocked = $event" @handoff="switchMode('station')" @target-selected="emit('selected', $event, mode)" />
    </div>
    <div v-show="mode === 'station'" :inert="mode !== 'station' || undefined">
      <ProductionPanel v-if="stationCreated" field embedded :initial-target="target" title="登记本次交工" intro="核对加工要求，按本次实际交出的数量登记。" @saved="delivered" @locked="stationLocked = $event" @target-selected="emit('selected', $event, mode)" />
    </div>
  </div>
</template>

<style scoped>
.action-lead { margin: 0; color: var(--muted); }
.production-modes { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.production-modes button { display: grid; gap: 5px; min-height: 64px; white-space: normal; text-align: left; }
.production-modes small { font-size: 12px; font-weight: 400; color: var(--muted); }
.production-modes button[aria-pressed="true"] { color: var(--action); background: var(--action-soft); }
.production-action-pane { padding-bottom: 0; }
.production-action-pane :deep(.field-dock) { position: sticky; left: auto; right: auto; bottom: 0; width: auto; margin: 0; transform: none; }
@media (max-width: 900px) { .action-lead { display: none; } }
</style>
