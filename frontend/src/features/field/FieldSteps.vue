<script setup lang="ts">
defineProps<{
  steps: string[];
  current: number;
}>();
</script>

<template>
  <ol class="field-steps" aria-label="作业步骤">
    <li
      v-for="(step, index) in steps"
      :key="step"
      :class="{ 'is-current': index === current, 'is-done': index < current }"
    >
      <span aria-hidden="true">{{ index + 1 }}</span>
      <span>{{ step }}</span>
    </li>
  </ol>
</template>
