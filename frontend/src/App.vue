<script setup lang="ts">
import { computed, ref } from 'vue';
import AppShell from './components/AppShell.vue';
import M0StatusCard from './components/M0StatusCard.vue';
import ScanResolver from './features/scan/ScanResolver.vue';
import InventoryTransferDemo from './features/inventory/InventoryTransferDemo.vue';
import VideoTaskDemo from './features/media/VideoTaskDemo.vue';

type TabKey = 'overview' | 'scan' | 'inventory' | 'media';

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'overview', label: 'M0 总览' },
  { key: 'scan', label: '扫码验证' },
  { key: 'inventory', label: '库存台账' },
  { key: 'media', label: '视频任务' },
];

const activeTab = ref<TabKey>('overview');
const activeTitle = computed(() => tabs.find((tab) => tab.key === activeTab.value)?.label ?? 'M0');
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

    <M0StatusCard v-if="activeTab === 'overview'" />
    <ScanResolver v-else-if="activeTab === 'scan'" />
    <InventoryTransferDemo v-else-if="activeTab === 'inventory'" />
    <VideoTaskDemo v-else />
  </AppShell>
</template>
