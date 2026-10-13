<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import AppShell from './components/AppShell.vue';
import NavIcon from './components/NavIcon.vue';
import WorkspaceLoadState from './components/WorkspaceLoadState.vue';
import LoginPage from './features/auth/LoginPage.vue';
import ChainReview from './features/scan/ChainReview.vue';
import type { TraceTarget } from './features/scan/traceTarget';
import M1Dashboard from './features/m1/M1Dashboard.vue';
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
  | 'users'
  | 'documents'
  | 'sample'
  | 'label'
  | 'hardware'
  | 'operations'
  | 'mobile';

type NavGroupId = 'desk' | 'inbound' | 'production' | 'inventory' | 'finance' | 'system';

/** 只改变展示分组，不新增菜单编码，也不因此放宽权限。 */
const navGroups: Array<{ id: NavGroupId; label: string }> = [
  { id: 'desk', label: '工作台' },
  { id: 'inbound', label: '来料与订单' },
  { id: 'production', label: '生产与质量' },
  { id: 'inventory', label: '库存与交付' },
  { id: 'finance', label: '财务经营' },
  { id: 'system', label: '协同与系统' },
];

const tabs: Array<{ key: TabKey; label: string; meta: string; code: string; group: NavGroupId }> = [
  { key: 'dashboard', label: '首页', meta: '今日待办', code: 'OP', group: 'desk' },
  { key: 'inbound', label: '来料', meta: '收货入库', code: 'IN', group: 'inbound' },
  { key: 'label', label: '标签', meta: '打印补打', code: 'LB', group: 'inbound' },
  { key: 'production', label: '生产', meta: '派工报工', code: 'MO', group: 'production' },
  { key: 'progress', label: '进度', meta: '在制筛选', code: 'PG', group: 'production' },
  { key: 'quality', label: '质检', meta: '返工放行', code: 'QC', group: 'production' },
  { key: 'inventory', label: '库存', meta: '出库退货', code: 'ST', group: 'inventory' },
  { key: 'finance', label: '财务', meta: '客户账成本', code: 'FN', group: 'finance' },
  { key: 'approvals', label: '审批', meta: '待我处理', code: 'AP', group: 'system' },
  { key: 'sample', label: '登记导入', meta: 'Excel 入账', code: 'SA', group: 'system' },
  { key: 'documents', label: '附件', meta: '照片视频', code: 'EV', group: 'system' },
  { key: 'hardware', label: '设备', meta: '读数接入', code: 'HW', group: 'system' },
  { key: 'users', label: '用户管理', meta: '账号与权限', code: 'US', group: 'system' },
  { key: 'configuration', label: '配置', meta: '个人与存储', code: 'CF', group: 'system' },
  { key: 'operations', label: '运维', meta: '硬件存储', code: 'OC', group: 'system' },
  { key: 'mobile', label: '手机作业', meta: '收货报工', code: 'PW', group: 'system' },
];

const mobilePrimaryKeys: TabKey[] = ['dashboard', 'mobile', 'approvals'];

const activeTab = ref<TabKey>('dashboard');
const workspaceLoaders = {
  inbound: () => import('./features/m1/InboundWorkspace.vue'),
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
const activeWorkspace = computed(() => {
  // 重建异步组件以重新请求失败的页面，已加载模块由浏览器缓存复用。
  void workspaceLoadAttempt.value;
  if (activeTab.value === 'dashboard') return null;
  return defineAsyncComponent({
    loader: workspaceLoaders[activeTab.value],
    loadingComponent: WorkspaceLoadState,
    errorComponent: WorkspaceLoadState,
    delay: 150,
    timeout: 30_000,
  });
});
const moreOpen = ref(false);
const moreQuery = ref('');
const traceQuery = ref('');
const traceTarget = ref<TraceTarget | null>(null);
const labelTarget = ref<TraceTarget | null>(null);
let traceToken = 0;
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
  const permittedTabs = tabs.filter(tab => tab.key !== 'users' || authSession.value?.role === 'ADMIN');
  if (!authorizedMenuCodes.value || authorizedMenuCodes.value.size === 0) {
    return permittedTabs;
  }
  const filteredTabs = permittedTabs.filter((tab) => authorizedMenuCodes.value?.has(tab.key));
  return filteredTabs.length ? filteredTabs : tabs.filter((tab) => tab.key === 'dashboard');
});
provide('zhubao-visible-tabs', computed(() => visibleTabs.value.map((tab) => tab.key)));
const navigationGroups = computed(() => navGroups
  .map((group) => ({
    label: group.label,
    tabs: visibleTabs.value.filter((tab) => tab.group === group.id),
  }))
  .filter((group) => group.tabs.length > 0));
const activeTabMeta = computed(() => visibleTabs.value.find((tab) => tab.key === activeTab.value) ?? visibleTabs.value[0]);
const activeTitle = computed(() => activeTabMeta.value?.label ?? '首页');
const activeLocation = computed(() => navGroups.find((group) => group.id === activeTabMeta.value?.group)?.label ?? '');
const mobilePrimaryTabs = computed(() => mobilePrimaryKeys
  .map((key) => visibleTabs.value.find((tab) => tab.key === key))
  .filter((tab): tab is (typeof tabs)[number] => Boolean(tab))
  .map((tab) => ({
    key: tab.key,
    label: tab.key === 'dashboard' ? '工作台' : tab.key === 'mobile' ? '现场作业' : '审批',
  })));
const moreTabs = computed(() => visibleTabs.value.filter((tab) => !mobilePrimaryKeys.includes(tab.key)));
const filteredMoreTabs = computed(() => {
  const query = moreQuery.value.trim();
  if (!query) return moreTabs.value;
  return moreTabs.value.filter((tab) => tab.label.includes(query) || tab.meta.includes(query));
});

watch(activeTab, () => {
  moreOpen.value = false;
  moreQuery.value = '';
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
  labelTarget.value = null;
  traceTarget.value = null;
}

function handleAuthSessionChanged(event: CustomEvent<AuthSessionChangedDetail>) {
  authSession.value = event.detail.session;
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
  activateTab(tab);
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
  <AppShell v-else v-model:trace-query="traceQuery" :title="activeTitle" :location="activeLocation" :class="{ 'system-workspace-shell': activeTabMeta?.group === 'system' }" @open-trace="openTraceDocument">
    <template #session>
      <div class="top-session">
        <span>{{ authSession?.operator }}</span>
        <button class="secondary-button" type="button" @click="handleLogout">退出</button>
      </div>
    </template>

    <template #mobile-nav>
      <button
        v-for="item in mobilePrimaryTabs"
        :key="item.key"
        class="mobile-tab"
        :class="{ active: activeTab === item.key }"
        :data-mobile-tab="item.key"
        type="button"
        @click="openMobileTab(item.key)"
      >
        <NavIcon :name="item.key" />
        <span>{{ item.label }}</span>
      </button>
      <button
        v-if="moreTabs.length"
        class="mobile-tab"
        :class="{ active: moreOpen || moreTabs.some((tab) => tab.key === activeTab) }"
        type="button"
        aria-haspopup="dialog"
        :aria-expanded="moreOpen ? 'true' : 'false'"
        @click="moreOpen = !moreOpen"
      >
        <NavIcon name="more" />
        <span>全部功能</span>
      </button>
      <div v-if="moreOpen" class="mobile-more-sheet" role="dialog" aria-label="全部功能">
        <label class="mobile-more-filter">
          <span class="visually-hidden">筛选功能</span>
          <input v-model="moreQuery" aria-label="筛选功能" placeholder="筛选功能" />
        </label>
        <button
          v-for="tab in filteredMoreTabs"
          :key="tab.key"
          class="mobile-more-item"
          :data-mobile-tab="tab.key"
          type="button"
          @click="openMobileTab(tab.key)"
        >
          {{ tab.label }}
        </button>
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

    <ChainReview v-if="traceTarget?.intent === 'LOOKUP'" />
    <M1Dashboard v-if="activeTab === 'dashboard'" />
    <component :is="activeWorkspace" v-else />
  </AppShell>
</template>
