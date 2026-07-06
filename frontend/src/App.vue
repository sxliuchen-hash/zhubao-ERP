<script setup lang="ts">
import { computed, ref } from 'vue';
import AppShell from './components/AppShell.vue';
import InboundWorkspace from './features/m1/InboundWorkspace.vue';
import InventoryTraceWorkspace from './features/m1/InventoryTraceWorkspace.vue';
import M1Dashboard from './features/m1/M1Dashboard.vue';
import MobileOpsWorkspace from './features/m1/MobileOpsWorkspace.vue';
import ProductionWorkspace from './features/m1/ProductionWorkspace.vue';
import QualityWorkspace from './features/m1/QualityWorkspace.vue';

type TabKey = 'dashboard' | 'inbound' | 'production' | 'quality' | 'inventory' | 'mobile';

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'dashboard', label: '首页工作台' },
  { key: 'inbound', label: '来料母单' },
  { key: 'production', label: '生产工单' },
  { key: 'quality', label: '质检返工' },
  { key: 'inventory', label: '库存追溯' },
  { key: 'mobile', label: 'PWA 作业' },
];

const activeTab = ref<TabKey>('dashboard');
const activeTitle = computed(() => tabs.find((tab) => tab.key === activeTab.value)?.label ?? '首页工作台');
</script>

<template>
  <AppShell :title="activeTitle">
    <template #nav>
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="nav-button"
        :class="{ active: activeTab === tab.key }"
        type="button"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </template>

    <M1Dashboard v-if="activeTab === 'dashboard'" />
    <InboundWorkspace v-else-if="activeTab === 'inbound'" />
    <ProductionWorkspace v-else-if="activeTab === 'production'" />
    <QualityWorkspace v-else-if="activeTab === 'quality'" />
    <InventoryTraceWorkspace v-else-if="activeTab === 'inventory'" />
    <MobileOpsWorkspace v-else />
  </AppShell>
</template>
