<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue';

/** 同一个输入框既显示已选项，也在下拉列表里按关键字筛选。 */
export type FilterOption = {
  value: string;
  label: string;
  searchText?: string;
};

const props = withDefaults(defineProps<{
  modelValue: string;
  options: FilterOption[];
  ariaLabel: string;
  placeholder?: string;
  emptyText?: string;
}>(), {
  placeholder: '输入关键字筛选',
  emptyText: '没有匹配项',
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'update:query': [value: string];
}>();

const listId = useId();
const open = ref(false);
const filtering = ref(false);
const query = ref('');
const activeIndex = ref(0);

const selected = computed(() => props.options.find((item) => item.value === props.modelValue) ?? null);
const inputText = computed(() => (filtering.value ? query.value : selected.value?.label ?? ''));
const filtered = computed(() => {
  const keyword = filtering.value ? query.value.trim().toLowerCase() : '';
  if (!keyword) return props.options;
  return props.options.filter((item) => `${item.label} ${item.searchText ?? ''}`.toLowerCase().includes(keyword));
});

watch(filtered, (items) => {
  if (activeIndex.value >= items.length) activeIndex.value = 0;
});

watch(() => props.modelValue, (value, previous) => {
  if (value && value !== previous) {
    query.value = '';
    filtering.value = false;
    open.value = false;
  }
});

function optionId(value: string) {
  return `${listId}-${value.replace(/[^A-Za-z0-9_-]/g, '_')}`;
}

function onFocus(event: FocusEvent) {
  open.value = true;
  if (filtering.value) return;
  (event.target as HTMLInputElement).select?.();
}

function onMouseUp(event: MouseEvent) {
  if (filtering.value) return;
  event.preventDefault();
  (event.currentTarget as HTMLInputElement).select?.();
}

function onInput(event: Event) {
  const value = (event.target as HTMLInputElement).value;
  filtering.value = true;
  query.value = value;
  open.value = true;
  activeIndex.value = 0;
  emit('update:query', value);
  if (props.modelValue && value.trim() !== selected.value?.label) {
    emit('update:modelValue', '');
  }
}

function onBlur() {
  open.value = false;
}

function choose(option: FilterOption) {
  emit('update:modelValue', option.value);
  query.value = '';
  filtering.value = false;
  open.value = false;
  emit('update:query', '');
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    if (!open.value) {
      open.value = true;
      activeIndex.value = 0;
      return;
    }
    const count = filtered.value.length;
    if (!count) return;
    const step = event.key === 'ArrowDown' ? 1 : -1;
    activeIndex.value = (activeIndex.value + step + count) % count;
    return;
  }
  if (event.key === 'Enter' && open.value) {
    const option = filtered.value[activeIndex.value];
    if (!option) return;
    event.preventDefault();
    choose(option);
    return;
  }
  if (event.key === 'Escape') open.value = false;
}
</script>

<template>
  <div class="combo">
    <input
      :value="inputText"
      type="text"
      role="combobox"
      autocomplete="off"
      :aria-label="ariaLabel"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="listId"
      :aria-activedescendant="open && filtered[activeIndex] ? optionId(filtered[activeIndex].value) : undefined"
      :placeholder="placeholder"
      @focus="onFocus"
      @mouseup="onMouseUp"
      @input="onInput"
      @blur="onBlur"
      @keydown="onKeydown"
    />
    <ul v-if="open" :id="listId" class="combo-list" role="listbox" :aria-label="`${ariaLabel}选项`" @mousedown.prevent>
      <li v-for="(option, index) in filtered" :key="option.value">
        <button
          :id="optionId(option.value)"
          type="button"
          role="option"
          class="combo-option"
          :class="{ 'is-active': index === activeIndex, 'is-selected': option.value === modelValue }"
          :aria-selected="option.value === modelValue ? 'true' : 'false'"
          @mousedown.prevent
          @click="choose(option)"
        >
          {{ option.label }}
        </button>
      </li>
      <li v-if="!filtered.length" class="combo-empty">{{ emptyText }}</li>
    </ul>
  </div>
</template>

<style scoped>
.combo { position: relative; }
.combo input { width: 100%; }
.combo-list {
  position: absolute;
  z-index: 30;
  top: calc(100% + 4px);
  right: 0;
  left: 0;
  max-height: 240px;
  margin: 0;
  padding: 4px;
  overflow: auto;
  list-style: none;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-control);
  background: var(--content);
  box-shadow: 0 8px 24px rgba(23, 38, 60, 0.12);
}
.combo-option {
  display: block;
  width: 100%;
  min-height: 36px;
  padding: 6px 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.combo-option.is-active,
.combo-option:hover { background: var(--soft); }
.combo-option.is-selected { background: var(--action-soft); }
.combo-empty { padding: 8px; color: var(--muted); }
</style>
