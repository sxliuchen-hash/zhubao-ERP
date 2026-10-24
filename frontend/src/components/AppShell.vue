<script setup lang="ts">
import { ref } from 'vue';
import UpgradeNotice from './UpgradeNotice.vue';

defineProps<{
  title: string;
  location?: string;
  headingInContent?: boolean;
}>();

const traceQuery = defineModel<string>('traceQuery', { default: '' });
const emit = defineEmits<{ openTrace: [] }>();

const collapsedStorageKey = 'zhubao-sidebar-collapsed';
const collapsed = ref(readCollapsed());
const traceOpen = ref(false);
const businessDate = formatToday();

function formatToday() {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function readCollapsed() {
  try {
    return globalThis.localStorage?.getItem(collapsedStorageKey) === '1';
  } catch {
    return false;
  }
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value;
  try {
    globalThis.localStorage?.setItem(collapsedStorageKey, collapsed.value ? '1' : '0');
  } catch {
    // 浏览器禁止写入本地存储时，本次收起仍然生效。
  }
}
</script>

<template>
  <div class="app-shell ops-shell review-shell" :class="{ 'is-collapsed': collapsed }">
    <aside id="app-sidebar" class="sidebar ops-sidebar review-sidebar">
      <div class="brand ops-brand">
        <span class="brand-mark" aria-hidden="true">Z</span>
        <div class="brand-copy">
          <strong>来料加工</strong>
          <span>生产与经营工作台</span>
        </div>
      </div>

      <nav class="nav ops-nav" aria-label="主导航">
        <slot name="nav" />
      </nav>

      <button
        class="sidebar-toggle"
        type="button"
        :aria-expanded="collapsed ? 'false' : 'true'"
        aria-controls="app-sidebar"
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="toggleCollapsed"
      >
        <svg v-if="collapsed" class="sidebar-glyph" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M9 6l6 6-6 6" />
        </svg>
        <svg v-else class="sidebar-glyph" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M15 6l-6 6 6 6" />
        </svg>
        <span class="sidebar-toggle-label">{{ collapsed ? '展开' : '收起' }}</span>
      </button>
    </aside>

    <main class="workspace ops-workspace review-workspace">
      <div class="top-strip ops-topbar review-topbar">
        <div class="mobile-brand">
          <span class="brand-mark" aria-hidden="true">Z</span>
          <div>
            <strong>{{ title }}</strong>
            <span v-if="location && location !== title">{{ location }}</span>
          </div>
        </div>
        <p class="crumb"><span>来料加工</span><b>{{ title }}</b></p>
        <div class="top-cluster">
        <div class="context-stack">
          <span>{{ businessDate }}</span>
        </div>
        <slot name="session" />
        <button class="trace-toggle" type="button" :aria-expanded="traceOpen ? 'true' : 'false'" aria-controls="trace-search" @click="traceOpen = !traceOpen">查单</button>
        <label id="trace-search" class="global-search lean-search" :class="{ 'is-open': traceOpen }">
          <span class="trace-label">查单</span>
          <input
            v-model="traceQuery"
            placeholder="搜索母单、子单、运单或客户"
            aria-label="追溯查询"
            @keydown.enter.prevent="emit('openTrace')"
          />
          <button class="secondary-button" type="button" aria-label="打开追溯单据" @click="emit('openTrace')">打开单据</button>
        </label>
        </div>
      </div>

      <UpgradeNotice />

      <header v-if="!headingInContent" class="workspace-header ops-page-header review-page-header lean-header">
        <p v-if="location && location !== title" class="page-location">{{ location }}</p>
        <h1>{{ title }}</h1>
      </header>

      <slot />
    </main>

    <nav class="mobile-tabbar" aria-label="手机导航">
      <slot name="mobile-nav" />
    </nav>
  </div>
</template>
