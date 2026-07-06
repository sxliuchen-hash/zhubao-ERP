<script setup lang="ts">
import { computed, ref } from 'vue';
import AppShell from './components/AppShell.vue';
import ConfigurationWorkspace from './features/m1/ConfigurationWorkspace.vue';
import HardwareIntegrationWorkspace from './features/m1/HardwareIntegrationWorkspace.vue';
import InboundWorkspace from './features/m1/InboundWorkspace.vue';
import InventoryTraceWorkspace from './features/m1/InventoryTraceWorkspace.vue';
import M1Dashboard from './features/m1/M1Dashboard.vue';
import MobileOpsWorkspace from './features/m1/MobileOpsWorkspace.vue';
import ProductionWorkspace from './features/m1/ProductionWorkspace.vue';
import QualityWorkspace from './features/m1/QualityWorkspace.vue';
import SampleLibraryWorkspace from './features/m1/SampleLibraryWorkspace.vue';

type TabKey =
  | 'dashboard'
  | 'inbound'
  | 'production'
  | 'quality'
  | 'inventory'
  | 'configuration'
  | 'sample'
  | 'hardware'
  | 'mobile';

const tabs: Array<{ key: TabKey; label: string; meta: string; badge?: string }> = [
  { key: 'dashboard', label: '首页工作台', meta: '全局态势', badge: '3' },
  { key: 'inbound', label: '来料母单', meta: '收货 / 拆包', badge: '6' },
  { key: 'production', label: '生产工单', meta: '排产 / 报工', badge: '18' },
  { key: 'quality', label: '质检返工', meta: '缺陷 / 损耗', badge: '7' },
  { key: 'inventory', label: '库存追溯', meta: '客户所有权' },
  { key: 'configuration', label: '配置基线', meta: '参数 / 构型', badge: '8' },
  { key: 'sample', label: '样例库', meta: '验收脚本' },
  { key: 'hardware', label: '软硬件联动', meta: '设备 / 兜底', badge: '4' },
  { key: 'mobile', label: 'PWA 作业', meta: '扫码 / 现场' },
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
        <span>
          <strong>{{ tab.label }}</strong>
          <small>{{ tab.meta }}</small>
        </span>
        <b v-if="tab.badge">{{ tab.badge }}</b>
      </button>
    </template>

    <M1Dashboard v-if="activeTab === 'dashboard'" />
    <InboundWorkspace v-else-if="activeTab === 'inbound'" />
    <ProductionWorkspace v-else-if="activeTab === 'production'" />
    <QualityWorkspace v-else-if="activeTab === 'quality'" />
    <InventoryTraceWorkspace v-else-if="activeTab === 'inventory'" />
    <ConfigurationWorkspace v-else-if="activeTab === 'configuration'" />
    <SampleLibraryWorkspace v-else-if="activeTab === 'sample'" />
    <HardwareIntegrationWorkspace v-else-if="activeTab === 'hardware'" />
    <MobileOpsWorkspace v-else />
  </AppShell>
</template>
