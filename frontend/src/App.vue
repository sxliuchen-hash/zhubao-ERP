<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import AppShell from './components/AppShell.vue';
import NavIcon from './components/NavIcon.vue';
import WorkspaceLoadState from './components/WorkspaceLoadState.vue';
import LoginPage from './features/auth/LoginPage.vue';
import ChainReview from './features/scan/ChainReview.vue';
import { armMobileScan, cancelMobileScan, openMobileScanTarget } from './features/scan/mobileScanBridge';
import type { TraceTarget } from './features/scan/traceTarget';
import { createWorkspaceState } from './features/workspace/workspaceContext';
import {
  filterWorkspaceTabs,
  groupTabsBySection,
  isTabKey,
  mobileMoreTabs,
  mobileOrderTabs,
  sectionLabel,
  sectionOfTab,
  workspaceTabs,
  type TabKey,
} from './features/workspace/workspaceNavigation';
import M1Dashboard from './features/m1/M1Dashboard.vue';
import { getAuthToken } from './services/apiClient';
import {
  authSessionChangedEvent,
  fetchAuthSession,
  logoutAuthSession,
  type AuthSession,
  type AuthSessionChangedDetail,
  type NavigationMenuItem,
} from './services/authApi';

const activeTab = ref<TabKey>('dashboard');
const workspaceLoaders = {
  inbound: () => import('./features/m1/InboundWorkspace.vue'),
  customers: () => import('./features/m1/CustomerArchiveWorkspace.vue'),
  production: () => import('./features/m1/ProductionWorkspace.vue'),
  progress: () => import('./features/m1/ProgressWorkspace.vue'),
  quality: () => import('./features/m1/QualityWorkspace.vue'),
  inventory: () => import('./features/m1/InventoryTraceWorkspace.vue'),
  finance: () => import('./features/m1/FinanceWorkspace.vue'),
  approvals: () => import('./features/m1/ApprovalWorkspace.vue'),
  configuration: () => import('./features/m1/ConfigurationWorkspace.vue'),
  users: () => import('./features/m1/UserManagementWorkspace.vue'),
  documents: () => import('./features/m1/DocumentManagementWorkspace.vue'),
  sample: () => import('./features/m1/SampleLibraryWorkspace.vue'),
  label: () => import('./features/m1/LabelWorkspace.vue'),
  hardware: () => import('./features/m1/HardwareIntegrationWorkspace.vue'),
  operations: () => import('./features/m1/OperationsCenterWorkspace.vue'),
  mobile: () => import('./features/m1/MobileOpsWorkspace.vue'),
};
const workspaceLoadAttempt = ref(0);
provide('zhubao-retry-workspace', () => { workspaceLoadAttempt.value += 1; });
const systemShellKeys = new Set<TabKey>(['approvals', 'sample', 'documents', 'hardware', 'users', 'configuration', 'operations', 'mobile']);
const activeWorkspace = computed(() => {
  // 重建异步组件以重新请求失败的页面，已加载模块由浏览器缓存复用。
  void workspaceLoadAttempt.value;
  const key = activeTab.value;
  if (key === 'dashboard') return null;
  return defineAsyncComponent({
    loader: workspaceLoaders[key],
    loadingComponent: WorkspaceLoadState,
    errorComponent: WorkspaceLoadState,
    delay: 150,
    timeout: 30_000,
  });
});
const moreOpen = ref(false);
const ordersOpen = ref(false);
const moreQuery = ref('');
const moreFilterRef = ref<HTMLInputElement | null>(null);
const ordersCloseRef = ref<HTMLButtonElement | null>(null);
const traceQuery = ref('');
const traceTarget = ref<TraceTarget | null>(null);
const labelTarget = ref<TraceTarget | null>(null);
const workspace = createWorkspaceState();
let traceToken = 0;
provide('zhubao-workspace-context', workspace);
provide('zhubao-trace-query', traceQuery);
provide('zhubao-trace-target', traceTarget);
provide('zhubao-label-target', labelTarget);
provide('zhubao-open-label', (code: string) => {
  if (!code.trim() || !visibleTabs.value.some((tab) => tab.key === 'label')) return;
  labelTarget.value = { objectType: 'MOTHER_ORDER', code: code.trim(), intent: 'LOOKUP', token: ++traceToken };
  activateTab('label');
});
provide('zhubao-open-tab', (tab: TabKey) => {
  activateTab(tab);
});
const authSession = ref<AuthSession | null>(null);
provide('zhubao-auth-session', authSession);
const authReady = ref(false);
const authRestoreError = ref('');
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
  // 用户管理只给管理员。审批栏目跟随服务端下发的菜单，隐藏入口不能代替接口授权。
  const permittedTabs = workspaceTabs.filter((tab) => {
    if (tab.key === 'users') return authSession.value?.role === 'ADMIN';
    return true;
  });
  if (!authorizedMenuCodes.value || authorizedMenuCodes.value.size === 0) {
    return permittedTabs;
  }
  const filteredTabs = permittedTabs.filter((tab) => authorizedMenuCodes.value?.has(tab.key));
  return filteredTabs.length ? filteredTabs : workspaceTabs.filter((tab) => tab.key === 'dashboard');
});
provide('zhubao-visible-tabs', computed(() => visibleTabs.value.map((tab) => tab.key)));
const navigationGroups = computed(() => groupTabsBySection(visibleTabs.value));
const activeTabMeta = computed(() => visibleTabs.value.find((tab) => tab.key === activeTab.value) ?? visibleTabs.value[0]);
const activeTitle = computed(() => activeTabMeta.value?.label ?? '首页');
const activeLocation = computed(() => sectionLabel(sectionOfTab(activeTabMeta.value?.key ?? 'dashboard')));
const showDeskTab = computed(() => visibleTabs.value.some((tab) => tab.key === 'dashboard'));
const showInventoryTab = computed(() => visibleTabs.value.some((tab) => tab.key === 'inventory'));
const orderDockTabs = computed(() => mobileOrderTabs(visibleTabs.value));
const moreTabs = computed(() => mobileMoreTabs(visibleTabs.value));
const filteredMoreGroups = computed(() => groupTabsBySection(filterWorkspaceTabs(moreTabs.value, moreQuery.value)));
const deskActive = computed(() => activeTab.value === 'dashboard' && !moreOpen.value && !ordersOpen.value);
const ordersActive = computed(() => ordersOpen.value || (!moreOpen.value && orderDockTabs.value.some((tab) => tab.key === activeTab.value)));
const inventoryActive = computed(() => activeTab.value === 'inventory' && !moreOpen.value && !ordersOpen.value);
const moreActive = computed(() => moreOpen.value || moreTabs.value.some((tab) => tab.key === activeTab.value));

watch(activeTab, () => {
  moreOpen.value = false;
  ordersOpen.value = false;
  moreQuery.value = '';
  void nextTick(() => window.scrollTo(0, 0));
});

watch(moreOpen, (open) => {
  if (!open) return;
  ordersOpen.value = false;
  void nextTick(() => moreFilterRef.value?.focus());
});

watch(visibleTabs, (nextTabs) => {
  if (!nextTabs.some((tab) => tab.key === activeTab.value)) {
    activeTab.value = nextTabs[0]?.key ?? 'dashboard';
  }
}, { immediate: true });

onMounted(() => {
  globalThis.addEventListener?.(authSessionChangedEvent, handleAuthSessionChanged as EventListener);
  globalThis.addEventListener?.('keydown', handleGlobalKeydown);
  void restoreAuthSessionForNavigation();
});

onBeforeUnmount(() => {
  globalThis.removeEventListener?.(authSessionChangedEvent, handleAuthSessionChanged as EventListener);
  globalThis.removeEventListener?.('keydown', handleGlobalKeydown);
  cancelMobileScan();
  workspace.clear();
});

async function restoreAuthSessionForNavigation() {
  authReady.value = false;
  authRestoreError.value = '';
  try {
    authSession.value = await fetchAuthSession();
  } catch {
    authSession.value = null;
    // 会话失效时接口层已经清掉令牌，这里回到登录页。令牌还在只说明服务器暂时没响应。
    authRestoreError.value = getAuthToken() ? '登录信息还在这台设备上，但服务器暂时没有响应。' : '';
  } finally {
    authReady.value = true;
  }
}

async function handleLogout() {
  cancelMobileScan();
  workspace.clear();
  await logoutAuthSession();
  authSession.value = null;
  labelTarget.value = null;
  traceTarget.value = null;
  traceQuery.value = '';
}

function handleAuthSessionChanged(event: CustomEvent<AuthSessionChangedDetail>) {
  cancelMobileScan();
  workspace.clear();
  moreOpen.value = false;
  ordersOpen.value = false;
  authSession.value = event.detail.session;
  if (event.detail.session) {
    authRestoreError.value = '';
    return;
  }
  labelTarget.value = null;
  traceTarget.value = null;
  traceQuery.value = '';
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || (!moreOpen.value && !ordersOpen.value)) return;
  moreOpen.value = false;
  ordersOpen.value = false;
}

function openTraceDocument() {
  const code = traceQuery.value.trim();
  if (!code) return;
  traceToken += 1;
  traceTarget.value = { objectType: null, code, intent: 'LOOKUP', token: traceToken };
}

function activateTab(tab: TabKey) {
  if (visibleTabs.value.some((item) => item.key === tab)) {
    activeTab.value = tab;
  }
}

function openMobileTab(tab: TabKey) {
  moreOpen.value = false;
  ordersOpen.value = false;
  activateTab(tab);
}

function openMobileOrders() {
  moreOpen.value = false;
  if (orderDockTabs.value.length === 1) {
    openMobileTab(orderDockTabs.value[0].key);
    return;
  }
  ordersOpen.value = !ordersOpen.value;
  if (ordersOpen.value) {
    void nextTick(() => ordersCloseRef.value?.focus());
  }
}

function startDockScan() {
  moreOpen.value = false;
  ordersOpen.value = false;
  if (openMobileScanTarget()) return;
  if (!visibleTabs.value.some((tab) => tab.key === 'mobile')) return;
  const switching = activeTab.value !== 'mobile';
  armMobileScan();
  if (switching) activateTab('mobile');
  else {
    void nextTick(() => {
      if (!openMobileScanTarget()) cancelMobileScan();
    });
  }
}

function flattenMenuCodes(items: NavigationMenuItem[]): string[] {
  return items.flatMap((item) => [item.code, ...flattenMenuCodes(item.children)]);
}
</script>

<template>
  <p v-if="!authReady" class="login-page">正在恢复登录</p>
  <div v-else-if="authRestoreError" class="login-page">
    <section class="login-card">
      <h1>暂时连不上服务器</h1>
      <p>{{ authRestoreError }}</p>
      <button class="primary-button" type="button" @click="restoreAuthSessionForNavigation">重试</button>
    </section>
  </div>
  <LoginPage v-else-if="!authSession || authSession.passwordChangeRequired" :session="authSession" />
  <AppShell v-else v-model:trace-query="traceQuery" :title="activeTitle" :location="activeLocation" :class="{ 'system-workspace-shell': activeTabMeta ? systemShellKeys.has(activeTabMeta.key) : false }" @open-trace="openTraceDocument">
    <template #session>
      <div class="top-session">
        <span>{{ authSession?.operator }}</span>
        <button class="secondary-button" type="button" @click="handleLogout">退出</button>
      </div>
    </template>

    <template #mobile-nav>
      <div class="mobile-tab-cluster">
        <button
          v-if="showDeskTab"
          class="mobile-tab"
          :class="{ active: deskActive }"
          data-mobile-section="desk"
          data-mobile-tab="dashboard"
          type="button"
          @click="openMobileTab('dashboard')"
        >
          <NavIcon name="dashboard" />
          <span>待办</span>
        </button>
        <button
          v-if="orderDockTabs.length"
          class="mobile-tab"
          :class="{ active: ordersActive }"
          data-mobile-section="orders"
          type="button"
          :aria-expanded="ordersOpen ? 'true' : 'false'"
          @click="openMobileOrders"
        >
          <NavIcon name="orders" />
          <span>订单</span>
        </button>
      </div>
      <button class="mobile-scan-key" type="button" aria-label="扫码" @click="startDockScan">
        <span class="mobile-scan-finder" aria-hidden="true"></span>
        <span>扫码</span>
      </button>
      <div class="mobile-tab-cluster">
        <button
          v-if="showInventoryTab"
          class="mobile-tab"
          :class="{ active: inventoryActive }"
          data-mobile-section="inventory"
          data-mobile-tab="inventory"
          type="button"
          @click="openMobileTab('inventory')"
        >
          <NavIcon name="inventory" />
          <span>库存</span>
        </button>
        <button
          v-if="moreTabs.length"
          class="mobile-tab"
          :class="{ active: moreActive }"
          data-mobile-section="more"
          type="button"
          aria-haspopup="dialog"
          :aria-expanded="moreOpen ? 'true' : 'false'"
          @click="moreOpen = !moreOpen"
        >
          <NavIcon name="more" />
          <span>更多</span>
        </button>
      </div>
      <div v-if="ordersOpen" class="mobile-more-sheet" role="dialog" aria-label="订单">
        <button ref="ordersCloseRef" class="secondary-button mobile-sheet-close" type="button" @click="ordersOpen = false">关闭</button>
        <p class="mobile-more-heading">打开原有页面</p>
        <button
          v-for="tab in orderDockTabs"
          :key="tab.key"
          class="mobile-more-item"
          :data-mobile-tab="tab.key"
          type="button"
          @click="openMobileTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>
      <div v-if="moreOpen" class="mobile-more-sheet" role="dialog" aria-label="更多">
        <button class="secondary-button mobile-sheet-close" type="button" @click="moreOpen = false">关闭</button>
        <label class="mobile-more-filter">
          <span class="visually-hidden">筛选功能</span>
          <input ref="moreFilterRef" v-model="moreQuery" aria-label="筛选功能" placeholder="筛选功能" />
        </label>
        <template v-for="group in filteredMoreGroups" :key="group.id">
          <p class="mobile-more-heading">{{ group.label }}</p>
          <button
            v-for="tab in group.tabs"
            :key="tab.key"
            class="mobile-more-item"
            :data-mobile-tab="tab.key"
            type="button"
            @click="openMobileTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </template>
        <p v-if="!filteredMoreGroups.length" class="mobile-more-heading">没有匹配的功能</p>
      </div>
    </template>

    <template #nav>
      <template v-for="group in navigationGroups" :key="group.id">
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

    <ChainReview v-if="traceTarget?.intent === 'LOOKUP'" />
    <M1Dashboard v-if="activeTab === 'dashboard'" />
    <component :is="activeWorkspace" v-else />
  </AppShell>
</template>
