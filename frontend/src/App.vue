<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import AppShell from './components/AppShell.vue';
import ApprovalWorkspace from './features/m1/ApprovalWorkspace.vue';
import ConfigurationWorkspace from './features/m1/ConfigurationWorkspace.vue';
import DocumentManagementWorkspace from './features/m1/DocumentManagementWorkspace.vue';
import HardwareIntegrationWorkspace from './features/m1/HardwareIntegrationWorkspace.vue';
import InboundWorkspace from './features/m1/InboundWorkspace.vue';
import InventoryTraceWorkspace from './features/m1/InventoryTraceWorkspace.vue';
import LabelWorkspace from './features/m1/LabelWorkspace.vue';
import M1Dashboard from './features/m1/M1Dashboard.vue';
import MobileOpsWorkspace from './features/m1/MobileOpsWorkspace.vue';
import OperationsCenterWorkspace from './features/m1/OperationsCenterWorkspace.vue';
import ProductionWorkspace from './features/m1/ProductionWorkspace.vue';
import QualityWorkspace from './features/m1/QualityWorkspace.vue';
import SampleLibraryWorkspace from './features/m1/SampleLibraryWorkspace.vue';
import {
  authSessionChangedEvent,
  fetchAuthSession,
  type AuthSession,
  type AuthSessionChangedDetail,
  type NavigationMenuItem,
} from './services/authApi';

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
  | 'label'
  | 'hardware'
  | 'operations'
  | 'mobile';

const tabs: Array<{ key: TabKey; label: string; meta: string; code: string; group: 'work' | 'system' }> = [
  { key: 'dashboard', label: '首页', meta: '今日待办', code: 'OP', group: 'work' },
  { key: 'inbound', label: '来料', meta: '收货入库', code: 'IN', group: 'work' },
  { key: 'production', label: '生产', meta: '派工报工', code: 'MO', group: 'work' },
  { key: 'quality', label: '质检', meta: '返工放行', code: 'QC', group: 'work' },
  { key: 'inventory', label: '库存', meta: '出库退货', code: 'ST', group: 'work' },
  { key: 'approvals', label: '审批', meta: '待我处理', code: 'AP', group: 'work' },
  { key: 'label', label: '标签', meta: '打印补打', code: 'LB', group: 'work' },
  { key: 'sample', label: '登记导入', meta: 'Excel 入账', code: 'SA', group: 'system' },
  { key: 'documents', label: '附件', meta: '照片视频', code: 'EV', group: 'system' },
  { key: 'hardware', label: '设备', meta: '读数接入', code: 'HW', group: 'system' },
  { key: 'configuration', label: '配置', meta: '账号权限', code: 'CF', group: 'system' },
  { key: 'operations', label: '运维', meta: '备份日志', code: 'OC', group: 'system' },
  { key: 'mobile', label: '手机作业', meta: '扫码报工', code: 'PW', group: 'system' },
];

const activeTab = ref<TabKey>('dashboard');
const traceQuery = ref('');
provide('zhubao-trace-query', traceQuery);
provide('zhubao-open-tab', (tab: TabKey) => {
  activateTab(tab);
});
const authSession = ref<AuthSession | null>(null);
const tabKeySet = new Set<TabKey>(tabs.map((tab) => tab.key));
const authorizedMenuCodes = computed(() => {
  if (!authSession.value) {
    return null;
  }
  return new Set([
    ...authSession.value.menuCodes,
    ...flattenMenuCodes(authSession.value.menuTree),
  ].filter(isTabKey));
});
const visibleTabs = computed(() => {
  if (!authorizedMenuCodes.value || authorizedMenuCodes.value.size === 0) {
    return tabs;
  }
  const filteredTabs = tabs.filter((tab) => authorizedMenuCodes.value?.has(tab.key));
  return filteredTabs.length ? filteredTabs : tabs.filter((tab) => tab.key === 'dashboard');
});
const visibleWorkTabs = computed(() => visibleTabs.value.filter((tab) => tab.group === 'work'));
const visibleSystemTabs = computed(() => visibleTabs.value.filter((tab) => tab.group === 'system'));
const activeTitle = computed(() => (
  visibleTabs.value.find((tab) => tab.key === activeTab.value)?.label
  ?? visibleTabs.value[0]?.label
  ?? '首页'
));

watch(activeTab, () => {
  void nextTick(() => window.scrollTo(0, 0));
});

watch(visibleTabs, (nextTabs) => {
  if (!nextTabs.some((tab) => tab.key === activeTab.value)) {
    activeTab.value = nextTabs[0]?.key ?? 'dashboard';
  }
}, { immediate: true });

onMounted(() => {
  globalThis.addEventListener?.(authSessionChangedEvent, handleAuthSessionChanged as EventListener);
  void restoreAuthSessionForNavigation();
});

onBeforeUnmount(() => {
  globalThis.removeEventListener?.(authSessionChangedEvent, handleAuthSessionChanged as EventListener);
});

async function restoreAuthSessionForNavigation() {
  try {
    authSession.value = await fetchAuthSession();
  } catch {
    authSession.value = null;
  }
}

function handleAuthSessionChanged(event: CustomEvent<AuthSessionChangedDetail>) {
  authSession.value = event.detail.session;
}

function activateTab(tab: TabKey) {
  if (visibleTabs.value.some((item) => item.key === tab)) {
    activeTab.value = tab;
  }
}

function flattenMenuCodes(items: NavigationMenuItem[]): string[] {
  return items.flatMap((item) => [item.code, ...flattenMenuCodes(item.children)]);
}

function isTabKey(value: string): value is TabKey {
  return tabKeySet.has(value as TabKey);
}
</script>

<template>
  <AppShell v-model:trace-query="traceQuery" :title="activeTitle">
    <template #nav>
      <p v-if="visibleWorkTabs.length" class="nav-group-label">作业</p>
      <button
        v-for="tab in visibleWorkTabs"
        :key="tab.key"
        class="nav-button"
        :data-tab="tab.key"
        :class="{ active: activeTab === tab.key }"
        type="button"
        @click="activateTab(tab.key)"
      >
        <strong>{{ tab.label }}</strong>
      </button>
      <p v-if="visibleSystemTabs.length" class="nav-group-label">系统</p>
      <button
        v-for="tab in visibleSystemTabs"
        :key="tab.key"
        class="nav-button"
        :data-tab="tab.key"
        :class="{ active: activeTab === tab.key }"
        type="button"
        @click="activateTab(tab.key)"
      >
        <strong>{{ tab.label }}</strong>
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
    <LabelWorkspace v-else-if="activeTab === 'label'" />
    <HardwareIntegrationWorkspace v-else-if="activeTab === 'hardware'" />
    <OperationsCenterWorkspace v-else-if="activeTab === 'operations'" />
    <MobileOpsWorkspace v-else />
  </AppShell>
</template>
