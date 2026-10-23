<script setup lang="ts">
import { computed, provide } from 'vue';
import QualityPanel from '../quality/QualityPanel.vue';
import type { OrderActionTarget } from './orderActions';

const props = defineProps<{ target: OrderActionTarget; active: boolean }>();
const emit = defineEmits<{ changed: []; selected: [target: OrderActionTarget] }>();
provide('zhubao-scan-suspended', computed(() => !props.active));
</script>

<template>
  <div class="quality-action-pane field-job" :inert="!active || undefined">
    <QualityPanel field embedded :initial-target="target" title="质检与返工复检" intro="先逐条判定，再按当前检验轮次分别办理返工、复检和放行。" @changed="emit('changed')" @target-selected="emit('selected', $event)" />
  </div>
</template>

<style scoped>
.quality-action-pane :deep(.field-dock) { position: sticky; left: auto; right: auto; bottom: 0; width: auto; margin: 0; transform: none; }
</style>
