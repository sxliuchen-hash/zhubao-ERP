<script setup lang="ts">
defineProps<{ modelValue: Record<string, string>; fields: Array<[string, string, string?]> }>();
const emit = defineEmits<{ 'update:modelValue': [Record<string, string>] }>();

function update(values: Record<string, string>, key: string, event: Event) {
  emit('update:modelValue', { ...values, [key]: (event.target as HTMLInputElement).value });
}
</script>

<template>
  <div class="operation-inputs">
    <label v-for="[key, label, type] in fields" :key="key">
      <span>{{ label }}</span>
      <select v-if="key === 'disposition'" :value="modelValue[key] || ''" :aria-label="label" @change="update(modelValue, key, $event)">
        <option value="">请选择处理结论</option>
        <option value="RELEASE">放行</option>
        <option value="REWORK">返工</option>
        <option value="SCRAP">报废</option>
        <option value="FREEZE">冻结</option>
      </select>
      <input v-else :value="modelValue[key] || ''" :type="type || 'text'" :step="type === 'number' ? 'any' : undefined"
        :min="type === 'number' ? 0 : undefined" :aria-label="label" @input="update(modelValue, key, $event)" />
    </label>
  </div>
</template>

<style scoped>
.operation-inputs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 12px 0; }
label { display: grid; gap: 6px; min-width: 0; font-size: 13px; }
label:has(textarea) { grid-column: 1 / -1; }
input, select { box-sizing: border-box; width: 100%; min-width: 0; padding: 8px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--text); }
</style>
