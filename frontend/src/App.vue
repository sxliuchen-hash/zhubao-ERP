<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import AppShell from './components/AppShell.vue';
import NavIcon from './components/NavIcon.vue';
import WorkspaceLoadState from './components/WorkspaceLoadState.vue';
import OrderActionHost from './features/orders/OrderActionHost.vue';
import type { InventoryBalance } from './data/m1PrototypeData';
import type { OrderActionTarget, OrderChangeTarget, ProductionMode, ShippingActionTarget } from './features/orders/orderActions';
import type { BillContext, BillMode } from './features/finance/billContext';
import type { MobileIntakeResult } from './services/mobileIntakeApi';
import LoginPage from './features/auth/LoginPage.vue';
import ChainReview from './features/scan/ChainReview.vue';
import { armMobileScan, cancelMobileScan, openMobileScanTarget } from './features/scan/mobileScanBridge';
import type { TraceTarget } from './features/scan/traceTarget';
import { createWorkspaceState } from './features/workspace/workspaceContext';
import {
  filterWorkspaceTabs,
  desktopNavigationGroups,
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
const orderWorkspaceOpen = ref(false);
const authRevision = ref(0);
const orderWorkspaceLoader = () => import('./features/orders/OrderWorkspace.vue');
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
const contentHeadingKeys = new Set<TabKey>(['customers', 'finance', 'documents', 'users']);
const activeWorkspace = computed(() => {
  // 重建异步组件以重新请求失败的页面，已加载模块由浏览器缓存复用。
  void workspaceLoadAttempt.value;
  const key = activeTab.value;
  if (key === 'dashboard' && !orderWorkspaceOpen.value) return null;
  return defineAsyncComponent({
    loader: orderWorkspaceOpen.value ? orderWorkspaceLoader : workspaceLoaders[key as Exclude<TabKey, 'dashboard'>],
    loadingComponent: WorkspaceLoadState,
    errorComponent: WorkspaceLoadState,
    delay: 150,
    timeout: 30_000,
  });
});
const moreOpen = ref(false);
const moreQuery = ref('');
const moreFilterRef = ref<HTMLInputElement | null>(null);
const traceQuery = ref('');
const traceTarget = ref<TraceTarget | null>(null);
const labelTarget = ref<TraceTarget | null>(null);
const approvalCode = ref('');
const orderActionHost = ref<InstanceType<typeof OrderActionHost> | null>(null);
const orderActionOpen = ref(false);
const orderRefresh = ref(0);
const intakeReceipt = ref<MobileIntakeResult | null>(null);
const workspace = createWorkspaceState();
let traceToken = 0;
provide('zhubao-workspace-context', workspace);
provide('zhubao-trace-query', traceQuery);
provide('zhubao-trace-target', traceTarget);
provide('zhubao-label-target', labelTarget);
provide('zhubao-approval-code', approvalCode);
provide('zhubao-scan-suspended', orderActionOpen);
provide('zhubao-order-refresh', orderRefresh);
provide('zhubao-intake-receipt', intakeReceipt);
provide('zhubao-open-intake', (target?: OrderActionTarget | null) => { if (canIntake.value) orderActionHost.value?.openIntake(target ?? null); });
provide('zhubao-open-production', (target?: OrderActionTarget | null, mode?: ProductionMode) => { if (canProduction.value) orderActionHost.value?.openProduction(target ?? null, mode); });
provide('zhubao-open-quality', (target?: OrderActionTarget | null) => { if (canQuality.value) orderActionHost.value?.openQuality(target ?? null); });
provide('zhubao-open-shipping', (target?: ShippingActionTarget | null) => { if (canShipping.value) orderActionHost.value?.openShipping(target ?? null); });
provide('zhubao-open-aftercare', (row: InventoryBalance) => { if (canAftercare.value) orderActionHost.value?.openAftercare(row); });
provide('zhubao-open-order-change', (target: OrderChangeTarget) => { if (canChange.value) orderActionHost.value?.openOrderChange(target); });
provide('zhubao-open-label-target', (target?: OrderActionTarget | null) => { if (canLabel.value) orderActionHost.value?.openLabel(target ?? null); });
provide('zhubao-open-bill', (context: BillContext, mode?: BillMode) => { if (canBill.value) orderActionHost.value?.openBill(context, mode); });
provide('zhubao-open-orders', openOrders);
provide('zhubao-open-approval', (code: string) => {
  if (!visibleTabs.value.some(tab => tab.key === 'approvals')) return;
  approvalCode.value = code;
  activateTab('approvals');
});
provide('zhubao-open-label', (code: string) => {
  if (code.trim() && canLabel.value) orderActionHost.value?.openLabel({ objectType: 'MOTHER_ORDER', code: code.trim() });
});
provide('zhubao-open-tab', (tab: TabKey) => {
  activateTab(tab);
});
provide('zhubao-open-order', (target: Omit<TraceTarget, 'intent' | 'token'>) => {
  traceTarget.value = { ...target, intent: 'LOOKUP', token: nextTraceToken() };
  openOrders();
});
const authSession = ref<AuthSession | null>(null);
const sessionMark = computed(() => {
  const name = authSession.value?.operator?.trim() ?? '';
  const last = name.split(/\s+/).filter(Boolean).at(-1) ?? '';
  return last.slice(0, 1) || '我';
});
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
  if (!authorizedMenuCodes.value || authorizedMenuCodes.value.size === 0) return workspaceTabs.filter(tab => tab.key === 'dashboard');
  const filteredTabs = permittedTabs.filter((tab) => authorizedMenuCodes.value?.has(tab.key));
  return filteredTabs.length ? filteredTabs : workspaceTabs.filter((tab) => tab.key === 'dashboard');
});
provide('zhubao-visible-tabs', computed(() => visibleTabs.value.map((tab) => tab.key)));
const navigationGroups = computed(() => desktopNavigationGroups(visibleTabs.value));
const supportSectionIds = new Set(['tools', 'system']);
const dailyNavigation = computed(() => navigationGroups.value.filter(group => !supportSectionIds.has(group.id)));
const supportNavigation = computed(() => navigationGroups.value.filter(group => supportSectionIds.has(group.id)));
const activeTabMeta = computed(() => visibleTabs.value.find((tab) => tab.key === activeTab.value) ?? visibleTabs.value[0]);
const activeOrderView = computed(() => workspace.orderView.value ?? 'ORDERS');
const activeTitle = computed(() => {
  if (orderWorkspaceOpen.value) {
    if (activeOrderView.value === 'PRODUCTION') return '生产作业';
    if (activeOrderView.value === 'QUALITY') return '质量作业';
    return '订单中心';
  }
  if (!activeTabMeta.value || activeTabMeta.value.key === 'dashboard') return '工作台';
  return activeTabMeta.value.label;
});
const activeLocation = computed(() => orderWorkspaceOpen.value ? activeOrderView.value === 'PRODUCTION' ? '生产作业' : activeOrderView.value === 'QUALITY' ? '质量作业' : '订单' : sectionLabel(sectionOfTab(activeTabMeta.value?.key ?? 'dashboard')));
const showDeskTab = computed(() => visibleTabs.value.some((tab) => tab.key === 'dashboard'));
const showInventoryTab = computed(() => visibleTabs.value.some((tab) => tab.key === 'inventory'));
const orderDockTabs = computed(() => mobileOrderTabs(visibleTabs.value));
const canReadOrders = computed(() => visibleTabs.value.some(tab => authorizedMenuCodes.value?.has(tab.key) && ['inbound', 'production', 'progress', 'quality', 'inventory'].includes(tab.key)));
const canIntake = computed(() => visibleTabs.value.some(tab => authorizedMenuCodes.value?.has(tab.key) && (tab.key === 'inbound' || tab.key === 'mobile'))
  && (authSession.value?.effectivePermissions ? authSession.value.effectivePermissions.includes('BUSINESS_INBOUND_CONFIRM')
    : ['ADMIN', 'GENERAL_MANAGER', 'RECEIVER', 'WAREHOUSE_KEEPER'].includes(authSession.value?.role ?? '')
      || (authSession.value?.role === 'BUSINESS_MANAGER' && authSession.value.coverDuties?.includes('INTAKE') === true)));
const canProduction = computed(() => visibleTabs.value.some(tab => authorizedMenuCodes.value?.has(tab.key) && ['production', 'progress', 'mobile'].includes(tab.key))
  && (authSession.value?.effectivePermissions ? authSession.value.effectivePermissions.includes('BUSINESS_PRODUCTION_REPORT')
    : ['ADMIN', 'GENERAL_MANAGER', 'OPERATOR', 'WORKSHOP_LEADER'].includes(authSession.value?.role ?? '')
      || (authSession.value?.role === 'BUSINESS_MANAGER' && authSession.value.coverDuties?.includes('REPORT') === true)));
const canChange = computed(() => authorizedMenuCodes.value?.has('progress') === true);
const canLabel = computed(() => authorizedMenuCodes.value?.has('label') === true
  && (!authSession.value?.effectivePermissions || authSession.value.effectivePermissions.includes('LABEL_PRINT')));
const canBill = computed(() => authorizedMenuCodes.value?.has('finance') === true
  && (!authSession.value?.effectivePermissions || ['FINANCE_CUSTOMER_READ', 'FINANCE_RECEIPT'].some(permission => authSession.value?.effectivePermissions?.includes(permission))));
const canAftercare = computed(() => authorizedMenuCodes.value?.has('inventory') === true
  && (authSession.value?.effectivePermissions ? ['BUSINESS_CUSTOMER_RETURN', 'BUSINESS_AFTER_SALES_REVIEW'].some(permission => authSession.value?.effectivePermissions?.includes(permission))
    : ['ADMIN', 'GENERAL_MANAGER', 'WAREHOUSE_KEEPER', 'SALES', 'QUALITY_INSPECTOR', 'WORKSHOP_LEADER'].includes(authSession.value?.role ?? '')
      || (authSession.value?.role === 'BUSINESS_MANAGER' && (authSession.value.coverDuties?.includes('RETURN') === true || authSession.value.coverDuties?.includes('QUALITY') === true))));
const canShipping = computed(() => visibleTabs.value.some(tab => authorizedMenuCodes.value?.has(tab.key) && ['inventory', 'mobile'].includes(tab.key))
  && (authSession.value?.effectivePermissions ? authSession.value.effectivePermissions.includes('BUSINESS_OUTBOUND_SHIPMENT')
    : ['ADMIN', 'GENERAL_MANAGER', 'WAREHOUSE_KEEPER', 'SALES'].includes(authSession.value?.role ?? '')
      || (authSession.value?.role === 'BUSINESS_MANAGER' && authSession.value.coverDuties?.includes('SHIP') === true)));
const canQuality = computed(() => visibleTabs.value.some(tab => authorizedMenuCodes.value?.has(tab.key) && ['quality', 'mobile'].includes(tab.key))
  && (authSession.value?.effectivePermissions ? ['BUSINESS_QUALITY_FREEZE', 'BUSINESS_REWORK_DISPATCH', 'BUSINESS_REWORK_REPORT', 'BUSINESS_QUALITY_RELEASE'].some(permission => authSession.value?.effectivePermissions?.includes(permission))
    : ['ADMIN', 'GENERAL_MANAGER', 'WORKSHOP_LEADER', 'QUALITY_INSPECTOR'].includes(authSession.value?.role ?? '')
      || (authSession.value?.role === 'BUSINESS_MANAGER' && authSession.value.coverDuties?.includes('QUALITY') === true)));
const moreTabs = computed(() => mobileMoreTabs(visibleTabs.value));
const filteredMoreGroups = computed(() => groupTabsBySection(filterWorkspaceTabs(moreTabs.value, moreQuery.value)));
const deskActive = computed(() => activeTab.value === 'dashboard' && !moreOpen.value && !orderWorkspaceOpen.value);
const ordersActive = computed(() => orderWorkspaceOpen.value || (!moreOpen.value && orderDockTabs.value.some((tab) => tab.key === activeTab.value)));
const inventoryActive = computed(() => activeTab.value === 'inventory' && !moreOpen.value && !orderWorkspaceOpen.value);
const moreActive = computed(() => moreOpen.value || (!orderWorkspaceOpen.value && moreTabs.value.some((tab) => tab.key === activeTab.value)));

watch(canReadOrders, allowed => {
  if (!allowed && orderWorkspaceOpen.value) {
    orderWorkspaceOpen.value = false;
    cancelMobileScan();
    workspace.clear();
    traceTarget.value = null;
  }
});

watch(activeTab, () => {
  moreOpen.value = false;
  moreQuery.value = '';
  void nextTick(() => window.scrollTo(0, 0));
});

watch(moreOpen, (open) => {
  if (!open) return;
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
  orderWorkspaceOpen.value = false;
  orderActionOpen.value = false;
  intakeReceipt.value = null;
  approvalCode.value = '';
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
  orderActionOpen.value = false;
  intakeReceipt.value = null;
  approvalCode.value = '';
  orderWorkspaceOpen.value = false;
  authRevision.value += 1;
  labelTarget.value = null;
  traceTarget.value = null;
  traceQuery.value = '';
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
  if (event.key !== 'Escape' || !moreOpen.value) return;
  moreOpen.value = false;
}

function openTraceDocument() {
  const code = traceQuery.value.trim();
  if (!code) return;
  traceTarget.value = { objectType: null, code, intent: 'LOOKUP', token: nextTraceToken() };
}

function nextTraceToken() {
  traceToken = Math.max(traceToken, traceTarget.value?.token ?? 0) + 1;
  return traceToken;
}

function intakeSaved(result: MobileIntakeResult) {
  intakeReceipt.value = result;
  orderRefresh.value += 1;
}

function activateTab(tab: TabKey) {
  if (visibleTabs.value.some((item) => item.key === tab)) {
    orderWorkspaceOpen.value = false;
    if (traceTarget.value?.intent === 'LOOKUP') traceTarget.value = null;
    activeTab.value = tab;
  }
}

function openMobileTab(tab: TabKey) {
  moreOpen.value = false;
  navigateTab(tab);
}
function navigateTab(tab: TabKey) {
  if (!visibleTabs.value.some(item => item.key === tab)) return;
  if (tab === 'production' || tab === 'progress') openOrders('PRODUCTION');
  else if (tab === 'quality') openOrders('QUALITY');
  else if (tab === 'inbound') openOrders('ORDERS');
  else activateTab(tab);
}
function navigationActive(tab: TabKey) {
  if (!orderWorkspaceOpen.value) return activeTab.value === tab;
  return activeOrderView.value === 'PRODUCTION' ? tab === 'production' || tab === 'progress' : activeOrderView.value === 'QUALITY' && tab === 'quality';
}

function openMobileOrders() {
  openOrders();
}

function openOrders(view: 'ORDERS' | 'PRODUCTION' | 'QUALITY' = 'ORDERS') {
  if (!canReadOrders.value) return;
  workspace.orderView.value = view;
  moreOpen.value = false;
  orderWorkspaceOpen.value = true;
}

function startDockScan() {
  moreOpen.value = false;
  if (openMobileScanTarget()) return;
  if (canReadOrders.value) {
    armMobileScan();
    if (orderWorkspaceOpen.value) {
      traceTarget.value = null;
      workspaceLoadAttempt.value += 1;
    }
    openOrders();
    return;
  }
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
  <AppShell v-else v-model:trace-query="traceQuery" :inert="orderActionOpen || undefined" :title="activeTitle" :location="activeLocation" :heading-in-content="!orderWorkspaceOpen && contentHeadingKeys.has(activeTab)" :class="{ 'system-workspace-shell': !orderWorkspaceOpen && activeTabMeta ? systemShellKeys.has(activeTabMeta.key) : false }" @open-trace="openTraceDocument">
    <template #session>
      <div class="top-session">
        <span class="session-name">{{ authSession?.operator }}</span>
        <button class="secondary-button session-logout" type="button" @click="handleLogout">退出</button>
        <details class="session-menu">
          <summary class="session-avatar" :aria-label="`${authSession?.operator || '当前账号'}，账号与退出`">{{ sessionMark }}</summary>
          <div class="session-panel">
            <span class="session-name">{{ authSession?.operator }}</span>
            <button class="secondary-button session-logout" type="button" @click="handleLogout">退出</button>
          </div>
        </details>
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
          v-if="canReadOrders"
          class="mobile-tab"
          :class="{ active: ordersActive }"
          data-mobile-section="orders"
          type="button"
          @click="openMobileOrders"
        >
          <NavIcon name="orders" />
          <span>订单</span>
        </button>
      </div>
      <button class="mobile-scan-key" type="button" aria-label="扫码" @click="startDockScan">
        <span class="mobile-scan-finder" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path d="M8 4H4v4M16 4h4v4M4 16v4h4M20 16v4h-4M7 12h10" />
          </svg>
        </span>
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
      <p v-if="dailyNavigation.length || canReadOrders" class="nav-group-label">日常协同</p>
      <template v-for="group in dailyNavigation" :key="group.id">
        <button
          v-for="tab in group.tabs"
          :key="tab.key"
          class="nav-button"
          :data-tab="tab.key"
          :class="{ active: navigationActive(tab.key) }"
          :title="`${tab.label} · ${tab.meta}`"
          type="button"
          @click="navigateTab(tab.key)"
        >
          <span class="nav-icon-wrap"><NavIcon :name="tab.key" /></span>
          <span class="nav-label"><strong>{{ tab.label }}</strong></span>
        </button>
        <button v-if="group.id === 'desk' && canReadOrders" class="nav-button" data-workspace-section="orders" :class="{ active: orderWorkspaceOpen && activeOrderView === 'ORDERS' }" type="button" @click="openOrders()">
          <span class="nav-icon-wrap"><NavIcon name="orders" /></span><span class="nav-label"><strong>订单中心</strong></span>
        </button>
      </template>
      <button v-if="canReadOrders && !dailyNavigation.some(group => group.id === 'desk')" class="nav-button" data-workspace-section="orders" :class="{ active: orderWorkspaceOpen && activeOrderView === 'ORDERS' }" type="button" @click="openOrders()">
        <span class="nav-icon-wrap"><NavIcon name="orders" /></span><span class="nav-label"><strong>订单中心</strong></span>
      </button>
      <p v-if="supportNavigation.length" class="nav-group-label">支持与管理</p>
      <template v-for="group in supportNavigation" :key="group.id">
        <button
          v-for="tab in group.tabs"
          :key="tab.key"
          class="nav-button"
          :data-tab="tab.key"
          :class="{ active: navigationActive(tab.key) }"
          :title="`${tab.label} · ${tab.meta}`"
          type="button"
          @click="navigateTab(tab.key)"
        >
          <span class="nav-icon-wrap"><NavIcon :name="tab.key" /></span>
          <span class="nav-label"><strong>{{ tab.label }}</strong></span>
        </button>
      </template>
    </template>

    <ChainReview v-if="traceTarget?.intent === 'LOOKUP' && !orderWorkspaceOpen" />
    <M1Dashboard v-if="activeTab === 'dashboard' && !orderWorkspaceOpen" :key="authRevision" />
    <component :is="activeWorkspace" v-else :key="authRevision" />
  </AppShell>
<OrderActionHost v-if="authReady && authSession && !authSession.passwordChangeRequired && !authRestoreError" ref="orderActionHost" :key="authRevision" :can-intake="canIntake" :can-production="canProduction" :can-change="canChange" :can-quality="canQuality" :can-shipping="canShipping" :can-aftercare="canAftercare" :can-label="canLabel" :can-bill="canBill" @active="orderActionOpen = $event" @saved="intakeSaved" @changed="orderRefresh += 1" />
</template>
