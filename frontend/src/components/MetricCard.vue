<script setup lang="ts">
import type { Metric } from '../data/m1PrototypeData';

defineProps<{
  metric: Metric;
}>();
</script>

<template>
  <article class="metric-card" :class="`tone-${metric.tone}`">
    <span>{{ metric.label }}</span>
    <strong>{{ metric.value }}</strong>
    <small>{{ metric.delta }}</small>
  </article>
</template>
