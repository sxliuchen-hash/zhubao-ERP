<script setup lang="ts">
import type { Tone } from '../data/m1PrototypeData';

withDefaults(
  defineProps<{
    tone?: Tone;
    label: string;
  }>(),
  {
    tone: 'gray',
  },
);
</script>

<template>
  <span class="status-badge" :class="`tone-${tone}`">{{ label }}</span>
</template>
