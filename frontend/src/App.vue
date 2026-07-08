<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import AppShell from './components/AppShell.vue';
import ApprovalWorkspace from './features/m1/ApprovalWorkspace.vue';
import ConfigurationWorkspace from './features/m1/ConfigurationWorkspace.vue';
import DocumentManagementWorkspace from './features/m1/DocumentManagementWorkspace.vue';
import HardwareIntegrationWorkspace from './features/m1/HardwareIntegrationWorkspace.vue';
import InboundWorkspace from './features/m1/InboundWorkspace.vue';
import InventoryTraceWorkspace from './features/m1/InventoryTraceWorkspace.vue';
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
  | 'hardware'
  | 'operations'
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
  { key: 'operations', label: '运维中心', meta: '日志 / 指标', badge: '3', code: 'OC' },
  { key: 'mobile', label: 'PWA 作业', meta: '扫码 / 现场', code: 'PW' },
];

const activeTab = ref<TabKey>('dashboard');
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
const activeTitle = computed(() => (
  visibleTabs.value.find((tab) => tab.key === activeTab.value)?.label
  ?? visibleTabs.value[0]?.label
  ?? '首页工作台'
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
  <AppShell :title="activeTitle">
    <template #nav>
      <button
        v-for="tab in visibleTabs"
        :key="tab.key"
        class="nav-button"
        :data-tab="tab.key"
        :class="{ active: activeTab === tab.key }"
        type="button"
        @click="activateTab(tab.key)"
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
    <OperationsCenterWorkspace v-else-if="activeTab === 'operations'" />
    <MobileOpsWorkspace v-else />
  </AppShell>
</template>
