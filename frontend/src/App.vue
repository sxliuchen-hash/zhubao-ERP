<script setup lang="ts">
import { computed, ref } from 'vue';
import AppShell from './components/AppShell.vue';
import ApprovalWorkspace from './features/m1/ApprovalWorkspace.vue';
import ConfigurationWorkspace from './features/m1/ConfigurationWorkspace.vue';
import DocumentManagementWorkspace from './features/m1/DocumentManagementWorkspace.vue';
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
  | 'approvals'
  | 'configuration'
  | 'documents'
  | 'sample'
  | 'hardware'
  | 'mobile';

const tabs: Array<{ key: TabKey; label: string; meta: string; badge?: string; code: string }> = [
  { key: 'dashboard', label: '首页工作台', meta: '项目进展 / 调度', badge: '3', code: 'OP' },
  { key: 'inbound', label: '来料母单', meta: '收货 / 拆包', badge: '6', code: 'IN' },
  { key: 'production', label: '生产工单', meta: '排产 / 报工', badge: '18', code: 'MO' },
  { key: 'quality', label: '质检返工', meta: '缺陷 / 损耗', badge: '7', code: 'QC' },
  { key: 'inventory', label: '库存追溯', meta: '客户所有权', code: 'ST' },
  { key: 'approvals', label: '审批工作台', meta: '损耗 / 异常', badge: '2', code: 'AP' },
  { key: 'configuration', label: '配置基线', meta: '参数 / 构型', badge: '8', code: 'CF' },
  { key: 'documents', label: '文档附件', meta: '证据 / 归档', badge: '5', code: 'EV' },
  { key: 'sample', label: '样例库', meta: '验收脚本', code: 'SA' },
  { key: 'hardware', label: '软硬件联动', meta: '设备 / 兜底', badge: '4', code: 'HW' },
  { key: 'mobile', label: 'PWA 作业', meta: '扫码 / 现场', code: 'PW' },
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
        :data-tab="tab.key"
        :class="{ active: activeTab === tab.key }"
        type="button"
        @click="activeTab = tab.key"
      >
        <i class="nav-code" aria-hidden="true">{{ tab.code }}</i>
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
    <ApprovalWorkspace v-else-if="activeTab === 'approvals'" />
    <ConfigurationWorkspace v-else-if="activeTab === 'configuration'" />
    <DocumentManagementWorkspace v-else-if="activeTab === 'documents'" />
    <SampleLibraryWorkspace v-else-if="activeTab === 'sample'" />
    <HardwareIntegrationWorkspace v-else-if="activeTab === 'hardware'" />
    <MobileOpsWorkspace v-else />
  </AppShell>
</template>
