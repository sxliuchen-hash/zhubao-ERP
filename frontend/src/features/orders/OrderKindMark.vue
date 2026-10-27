<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{ objectType?: string | null; kind?: 'mother' | 'child' | '' }>();
const kind = computed(() => props.kind || (props.objectType === 'MOTHER_ORDER' ? 'mother' : props.objectType === 'WORK_ORDER' ? 'child' : ''));
const label = computed(() => kind.value === 'mother' ? '母单' : kind.value === 'child' ? '子单' : '');
</script>

<template>
  <span v-if="kind" class="order-kind" :class="kind" :title="label" :aria-label="label">
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path v-if="kind === 'mother'" d="M2.5 3.2h7.2v8.2H2.5zM5.2 5.2h8.3v8.2H5.2z" />
      <path v-else d="M3.2 2.4h9.6v11.2H3.2zM5.4 6h5.2M5.4 8.6h5.2M5.4 11.2h3.2" />
    </svg>
    <span aria-hidden="true">{{ kind === 'mother' ? '母' : '子' }}</span>
  </span>
</template>

<style scoped>
.order-kind {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 20px;
  padding: 0 5px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  vertical-align: middle;
  white-space: nowrap;
}
.order-kind svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.4;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.order-kind.mother { background: #17263c; color: #f3e6c8; }
.order-kind.child { background: #e7eefb; color: #244bb0; }
</style>
