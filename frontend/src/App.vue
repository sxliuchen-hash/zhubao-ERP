<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import AppShell from './components/AppShell.vue';
import NavIcon from './components/NavIcon.vue';
import LoginPage from './features/auth/LoginPage.vue';
import ApprovalWorkspace from './features/m1/ApprovalWorkspace.vue';
import ConfigurationWorkspace from './features/m1/ConfigurationWorkspace.vue';
import DocumentManagementWorkspace from './features/m1/DocumentManagementWorkspace.vue';
import HardwareIntegrationWorkspace from './features/m1/HardwareIntegrationWorkspace.vue';
import InboundWorkspace from './features/m1/InboundWorkspace.vue';
import InventoryTraceWorkspace from './features/m1/InventoryTraceWorkspace.vue';
import FinanceWorkspace from './features/m1/FinanceWorkspace.vue';
import LabelWorkspace from './features/m1/LabelWorkspace.vue';
import M1Dashboard from './features/m1/M1Dashboard.vue';
import MobileOpsWorkspace from './features/m1/MobileOpsWorkspace.vue';
import OperationsCenterWorkspace from './features/m1/OperationsCenterWorkspace.vue';
import ProductionWorkspace from './features/m1/ProductionWorkspace.vue';
import ProgressWorkspace from './features/m1/ProgressWorkspace.vue';
import QualityWorkspace from './features/m1/QualityWorkspace.vue';
import SampleLibraryWorkspace from './features/m1/SampleLibraryWorkspace.vue';
import {
  authSessionChangedEvent,
  fetchAuthSession,
  logoutAuthSession,
  type AuthSession,
  type AuthSessionChangedDetail,
  type NavigationMenuItem,
} from './services/authApi';

type TabKey =
  | 'dashboard'
  | 'inbound'
  | 'production'
  | 'progress'
  | 'quality'
  | 'inventory'
  | 'finance'
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
  { key: 'progress', label: '进度', meta: '在制筛选', code: 'PG', group: 'work' },
  { key: 'quality', label: '质检', meta: '返工放行', code: 'QC', group: 'work' },
  { key: 'inventory', label: '库存', meta: '出库退货', code: 'ST', group: 'work' },
  { key: 'finance', label: '财务', meta: '客户账成本', code: 'FN', group: 'work' },
  { key: 'approvals', label: '审批', meta: '待我处理', code: 'AP', group: 'work' },
  { key: 'label', label: '标签', meta: '打印补打', code: 'LB', group: 'work' },
  { key: 'sample', label: '登记导入', meta: 'Excel 入账', code: 'SA', group: 'system' },
  { key: 'documents', label: '附件', meta: '照片视频', code: 'EV', group: 'system' },
  { key: 'hardware', label: '设备', meta: '读数接入', code: 'HW', group: 'system' },
  { key: 'configuration', label: '配置', meta: '用户与内容', code: 'CF', group: 'system' },
  { key: 'operations', label: '运维', meta: '硬件存储', code: 'OC', group: 'system' },
  { key: 'mobile', label: '手机作业', meta: '收货报工', code: 'PW', group: 'system' },
];

const activeTab = ref<TabKey>('dashboard');
const traceQuery = ref('');
provide('zhubao-trace-query', traceQuery);
provide('zhubao-open-tab', (tab: TabKey) => {
  activateTab(tab);
});
const authSession = ref<AuthSession | null>(null);
const authReady = ref(false);
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
const navigationGroups = computed(() => [
  { label: '作业', tabs: visibleWorkTabs.value },
  { label: '系统', tabs: visibleSystemTabs.value },
]);
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
  } finally {
    authReady.value = true;
  }
}

async function handleLogout() {
  await logoutAuthSession();
  authSession.value = null;
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
  <p v-if="!authReady" class="login-page">正在恢复登录</p>
  <LoginPage v-else-if="!authSession || authSession.passwordChangeRequired" :session="authSession" />
  <AppShell v-else v-model:trace-query="traceQuery" :title="activeTitle">
    <template #session>
      <div class="top-session">
        <span>{{ authSession?.operator }}</span>
        <button class="secondary-button" type="button" @click="handleLogout">退出</button>
      </div>
    </template>

    <template #nav>
      <template v-for="group in navigationGroups" :key="group.label">
        <p v-if="group.tabs.length" class="nav-group-label">{{ group.label }}</p>
        <button
          v-for="tab in group.tabs"
          :key="tab.key"
          class="nav-button"
          :data-tab="tab.key"
          :class="{ active: activeTab === tab.key }"
          :title="`${tab.label} · ${tab.meta}`"
          type="button"
          @click="activateTab(tab.key)"
        >
          <span class="nav-icon-wrap">
            <NavIcon :name="tab.key" />
          </span>
          <span class="nav-label"><strong>{{ tab.label }}</strong></span>
        </button>
      </template>
    </template>

    <M1Dashboard v-if="activeTab === 'dashboard'" />
    <InboundWorkspace v-else-if="activeTab === 'inbound'" />
    <ProductionWorkspace v-else-if="activeTab === 'production'" />
    <ProgressWorkspace v-else-if="activeTab === 'progress'" />
    <QualityWorkspace v-else-if="activeTab === 'quality'" />
    <InventoryTraceWorkspace v-else-if="activeTab === 'inventory'" />
    <FinanceWorkspace v-else-if="activeTab === 'finance'" />
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
