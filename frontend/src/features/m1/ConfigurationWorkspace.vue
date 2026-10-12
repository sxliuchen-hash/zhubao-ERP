<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import AccountApprovalPanel from './AccountApprovalPanel.vue';
import type { Tone } from '../../data/m1PrototypeData';
import {
  changeOwnPassword,
  fetchAuthAccounts,
  fetchAuthSession,
  unlockAuthAccount,
  updateAuthAccountStatus,
  upsertAuthAccount,
  type AuthSession,
  type AuthUserAccountSummary,
  type AuthAccountPreApprovalApplication,
  type AuthAccountPreApprovalApplicationRequest,
  type ManageAuthUserAccountRequest,
} from '../../services/authApi';
import {
  fetchStoredContent,
  retireStoredContent,
  type StoredContentInventory,
} from '../../services/documentManagementApi';
import {
  fetchCustomerAuthorizations,
  grantCustomerAuthorization,
  revokeCustomerAuthorization,
  type CustomerAuthorization,
} from '../../services/customerAuthorizationApi';

const authSession = ref<AuthSession | null>(null);
const passwordCurrent = ref('');
const passwordNew = ref('');
const passwordConfirm = ref('');
const passwordBusy = ref(false);
const passwordMessage = ref('');
const authAccounts = ref<AuthUserAccountSummary[]>([]);
const accountKeyword = ref('');
const accountDetailOpen = ref(false);
const accountLoadError = ref('');
const customerAuthorizations = ref<CustomerAuthorization[]>([]);
const customerAuthForm = ref({ username: '', customerCode: '', customerName: '' });
const customerAuthBusy = ref(false);
const customerAuthMessage = ref('');
const customerAuthLoadError = ref('');
const stationProcessNames = ['雕刻', '镶嵌', '抛光'];
const processForm = ref({
  username: '',
  operator: '',
  role: '',
  dataScope: '',
  customerScope: [] as string[],
  enabled: true,
  demoAccount: false,
  processes: [] as string[],
});
const processMessage = ref('');
const accountRoles = [
  { value: 'ADMIN', label: '系统管理员' },
  { value: 'GENERAL_MANAGER', label: '总经理' },
  { value: 'SALES', label: '业务员' },
  { value: 'RECEIVER', label: '收货员' },
  { value: 'WORKSHOP_LEADER', label: '车间组长' },
  { value: 'OPERATOR', label: '操作工' },
  { value: 'QUALITY_INSPECTOR', label: '质检员' },
  { value: 'WAREHOUSE_KEEPER', label: '库管员' },
  { value: 'FINANCE_STATISTICIAN', label: '财务统计' },
];
const dataScopes = [
  { value: 'SELF', label: '本人任务' },
  { value: 'TEAM', label: '本班组/工位' },
  { value: 'DEPARTMENT', label: '本部门' },
  { value: 'CUSTOMER_RANGE', label: '授权客户范围' },
  { value: 'ALL', label: '全部数据' },
];
const accountEditor = ref(emptyAccountEditor());
const accountEditorBusy = ref(false);
const accountEditorMessage = ref('');
const storedContent = ref<StoredContentInventory | null>(null);
const contentError = ref('');
const contentMessage = ref('');
const contentBusyCode = ref('');
const approvalRequest = ref<AuthAccountPreApprovalApplicationRequest | null>(null);
const executedApplicationCode = ref('');
let pendingAccountAction: ((applicationCode: string) => Promise<unknown>) | null = null;

function prepareAccountAction(request: AuthAccountPreApprovalApplicationRequest, action: (applicationCode: string) => Promise<unknown>) {
  if (accountEditorBusy.value) return;
  approvalRequest.value = request;
  pendingAccountAction = action;
  accountEditorMessage.value = '已准备账号操作，请在账号申请与审批区提交申请';
}

function prepareUpsert(request: ManageAuthUserAccountRequest, actionType: string) {
  prepareAccountAction({
    actionType, targetUsername: request.username, targetOperator: request.operator,
    targetRole: request.role, targetDataScope: request.dataScope,
    targetCustomerScope: request.customerScope, targetEnabled: request.enabled,
    targetAllowedProcesses: [...(request.allowedProcesses ?? [])],
    statusAction: actionType === 'CHANGE_STATUS' ? request.enabled ? 'ENABLE' : 'DISABLE' : undefined,
    demoAccount: request.demoAccount, reason: '',
  }, code => upsertAuthAccount({ ...request, preApprovalApplicationCode: code }));
}

async function executeApprovedAction(application: AuthAccountPreApprovalApplication) {
  const request = approvalRequest.value;
  accountEditorMessage.value = '';
  if (!request || !pendingAccountAction) {
    accountEditorMessage.value = '请先重新选择并准备该账号操作，再执行对应申请';
    return;
  }
  const expectedReadiness: Record<string, string> = {
    CREATE_ACCOUNT: 'AUTH-PRE-CREATE-001', RESET_PASSWORD: 'AUTH-PRE-PASSWORD-001',
    CHANGE_STATUS: 'AUTH-PRE-STATUS-001', CHANGE_ROLE_SCOPE: 'AUTH-PRE-ROLE-001',
  };
  if (application.targetUsername !== request.targetUsername || application.readinessCode !== expectedReadiness[request.actionType]
    || (request.targetOperator !== undefined && application.targetOperator !== request.targetOperator)
    || (request.targetRole !== undefined && application.targetRole !== request.targetRole)
    || (request.targetDataScope !== undefined && application.targetDataScope !== request.targetDataScope)
    || (request.targetEnabled !== undefined && application.targetEnabled !== request.targetEnabled)
    || (request.demoAccount !== undefined && application.demoAccount !== request.demoAccount)
    || (request.statusAction !== undefined && application.statusAction !== request.statusAction)
    || (request.targetAllowedProcesses !== undefined && JSON.stringify(application.targetAllowedProcesses ?? []) !== JSON.stringify(request.targetAllowedProcesses))
    || (request.targetCustomerScope !== undefined && JSON.stringify(application.targetCustomerScope) !== JSON.stringify(request.targetCustomerScope))) {
    accountEditorMessage.value = '已批准申请与当前准备的账号操作不一致，请重新申请';
    return;
  }
  accountEditorBusy.value = true;
  try {
    await pendingAccountAction(application.applicationCode);
    executedApplicationCode.value = application.applicationCode;
    pendingAccountAction = null;
    approvalRequest.value = null;
    accountEditor.value.password = '';
    accountEditorMessage.value = `已执行 ${application.applicationCode} 的账号操作`;
    await loadAuthAccounts();
  } catch (error) {
    accountEditorMessage.value = error instanceof Error ? error.message : '执行账号操作失败';
  } finally {
    accountEditorBusy.value = false;
  }
}

function emptyAccountEditor() {
  return {
    username: '',
    password: '',
    operator: '',
    role: 'OPERATOR',
    dataScope: 'SELF',
    enabled: true,
    demoAccount: false,
    editing: false,
  };
}

onMounted(async () => {
  await restoreAuthSession();
  await loadAuthAccounts();
  await loadCustomerAuthorizations();
  await loadStoredContent();
});

async function restoreAuthSession() {
  try {
    authSession.value = await fetchAuthSession();
  } catch {
    authSession.value = null;
  }
}

async function loadAuthAccounts() {
  accountLoadError.value = '';
  try {
    authAccounts.value = await fetchAuthAccounts();
    if (authAccounts.value.length === 0) {
      accountLoadError.value = '接口没有返回账号';
    }
  } catch (error) {
    authAccounts.value = [];
    accountLoadError.value = error instanceof Error ? error.message : '账号列表加载失败';
  }
}

async function loadCustomerAuthorizations() {
  customerAuthLoadError.value = '';
  try {
    customerAuthorizations.value = await fetchCustomerAuthorizations();
  } catch (error) {
    customerAuthorizations.value = [];
    customerAuthLoadError.value = error instanceof Error ? error.message : '客户授权加载失败';
  }
}

async function grantCustomerAuth() {
  customerAuthMessage.value = '';
  if (!customerAuthForm.value.username.trim() || !customerAuthForm.value.customerCode.trim()) {
    customerAuthMessage.value = '请填写账号和客户编码';
    return;
  }
  customerAuthBusy.value = true;
  try {
    await grantCustomerAuthorization({
      username: customerAuthForm.value.username.trim(),
      customerCode: customerAuthForm.value.customerCode.trim(),
      customerName: customerAuthForm.value.customerName.trim() || undefined,
    });
    customerAuthMessage.value = `已授权 ${customerAuthForm.value.username} 访问 ${customerAuthForm.value.customerCode}`;
    customerAuthForm.value = { username: customerAuthForm.value.username, customerCode: '', customerName: '' };
    await loadCustomerAuthorizations();
  } catch (error) {
    customerAuthMessage.value = error instanceof Error ? error.message : '授权失败（需要总经理/管理员权限）';
  } finally {
    customerAuthBusy.value = false;
  }
}

async function revokeCustomerAuth(authorization: CustomerAuthorization) {
  customerAuthMessage.value = '';
  try {
    const result = await revokeCustomerAuthorization({
      username: authorization.username,
      customerCode: authorization.customerCode,
    });
    customerAuthMessage.value = result.removed
      ? `已撤销 ${authorization.username} 对 ${authorization.customerCode} 的授权`
      : '未找到对应授权记录';
    await loadCustomerAuthorizations();
  } catch (error) {
    customerAuthMessage.value = error instanceof Error ? error.message : '撤销失败';
  }
}

function resetAccountEditor() {
  accountEditor.value = emptyAccountEditor();
  accountEditorMessage.value = '';
}

function editAccount(account: AuthUserAccountSummary) {
  selectAccount(account);
  accountEditor.value = {
    username: account.username,
    password: '',
    operator: account.operator,
    role: account.role,
    dataScope: account.dataScope,
    enabled: account.enabled,
    demoAccount: account.demoAccount,
    editing: true,
  };
  accountEditorMessage.value = '';
}

async function saveAccount() {
  accountEditorMessage.value = '';
  const editor = accountEditor.value;
  if (!editor.username.trim() || !editor.operator.trim()) {
    accountEditorMessage.value = '请填写账号和姓名';
    return;
  }
  if (!editor.editing && !editor.password.trim()) {
    accountEditorMessage.value = '新建账号必须填写初始密码';
    return;
  }
  const existing = authAccounts.value.find(account => account.username === editor.username.trim());
  prepareUpsert({
    username: editor.username.trim(), password: editor.password || undefined,
    operator: editor.operator.trim(), role: editor.role, dataScope: editor.dataScope,
    customerScope: existing?.customerScope ?? [], allowedProcesses: existing?.allowedProcesses ?? [],
    enabled: editor.enabled, demoAccount: editor.demoAccount,
  }, !editor.editing ? 'CREATE_ACCOUNT' : editor.password ? 'RESET_PASSWORD'
    : existing?.enabled !== editor.enabled ? 'CHANGE_STATUS' : 'CHANGE_ROLE_SCOPE');
}

async function toggleAccount(account: AuthUserAccountSummary) {
  const enabled = !account.enabled;
  prepareAccountAction({ actionType: 'CHANGE_STATUS', targetUsername: account.username,
    targetEnabled: enabled, statusAction: enabled ? 'ENABLE' : 'DISABLE', reason: '' }, code => updateAuthAccountStatus(account.username, {
      enabled, reason: enabled ? '管理员启用' : '管理员停用', preApprovalApplicationCode: code,
    }));
}

async function unlockAccount(account: AuthUserAccountSummary) {
  prepareAccountAction({ actionType: 'CHANGE_STATUS', targetUsername: account.username,
    statusAction: 'UNLOCK', reason: '' }, code => unlockAuthAccount(account.username, { reason: '管理员解锁', preApprovalApplicationCode: code }));
}

async function loadStoredContent() {
  contentError.value = '';
  try {
    storedContent.value = await fetchStoredContent();
  } catch (error) {
    storedContent.value = null;
    contentError.value = error instanceof Error ? error.message : '存储内容加载失败';
  }
}

async function retireContent(code: string, fileName: string) {
  contentMessage.value = '';
  if (!globalThis.confirm?.(`下线 ${fileName} 后，本地文件会删除，台账保留为已下线。继续吗？`)) {
    return;
  }
  contentBusyCode.value = code;
  try {
    await retireStoredContent(code);
    contentMessage.value = `已下线 ${fileName}`;
    await loadStoredContent();
  } catch (error) {
    contentMessage.value = error instanceof Error ? error.message : '下线失败';
  } finally {
    contentBusyCode.value = '';
  }
}

const filteredAccounts = computed(() => {
  const text = accountKeyword.value.trim().toLowerCase();
  if (!text) return authAccounts.value;
  return authAccounts.value.filter((account) => `${account.username} ${account.operator} ${account.roleLabel}`.toLowerCase().includes(text));
});

function selectAccount(account: AuthUserAccountSummary) {
  processForm.value = {
    username: account.username,
    operator: account.operator,
    role: account.role,
    dataScope: account.dataScope,
    customerScope: [...(account.customerScope ?? [])],
    enabled: account.enabled,
    demoAccount: account.demoAccount,
    processes: [...(account.allowedProcesses ?? [])],
  };
  processMessage.value = '';
  accountDetailOpen.value = true;
}

function backToAccounts() {
  accountDetailOpen.value = false;
}

async function saveProcesses() {
  processMessage.value = '';
  if (!processForm.value.username) {
    processMessage.value = '请先点一个账号';
    return;
  }
  prepareUpsert({
    username: processForm.value.username, operator: processForm.value.operator,
    role: processForm.value.role, dataScope: processForm.value.dataScope,
    customerScope: [...processForm.value.customerScope], enabled: processForm.value.enabled,
    demoAccount: processForm.value.demoAccount, allowedProcesses: [...processForm.value.processes],
  }, 'CHANGE_ROLE_SCOPE');
  processMessage.value = '工序变更已准备，请提交账号申请';
}

async function handleChangePassword() {
  passwordBusy.value = true;
  passwordMessage.value = '';
  try {
    if (!authSession.value) {
      passwordMessage.value = '请先登录后再自助改密';
      return;
    }
    if (passwordNew.value !== passwordConfirm.value) {
      passwordMessage.value = '两次新密码不一致';
      return;
    }
    authSession.value = await changeOwnPassword({
      currentPassword: passwordCurrent.value,
      newPassword: passwordNew.value,
    });
    passwordCurrent.value = '';
    passwordNew.value = '';
    passwordConfirm.value = '';
    passwordMessage.value = `自助改密成功，密码到期 ${authSession.value.passwordExpiresAt}`;
    await loadAuthAccounts();
  } catch (error) {
    passwordMessage.value = error instanceof Error ? error.message : '自助改密失败';
  } finally {
    passwordBusy.value = false;
  }
}

function accountStatusLabel(account: AuthUserAccountSummary): string {
  if (!account.enabled) return '停用';
  if (account.passwordChangeRequired) return '需首次改密';
  if (account.passwordExpired) return '密码过期';
  if (account.locked) return '锁定';
  return '启用';
}

function accountStatusTone(account: AuthUserAccountSummary): Tone {
  if (!account.enabled) return 'gray';
  if (account.passwordChangeRequired) return 'amber';
  if (account.passwordExpired) return 'red';
  if (account.locked) return 'red';
  if (account.failedLoginCount > 0) return 'amber';
  return 'green';
}
</script>

<template>
  <div class="page-stack">
    <section class="operations-hero">
      <div class="hero-copy">
        <span class="eyebrow">Accounts</span>
        <h2>账号与客户范围</h2>
        <p>管理账号、岗位工序、客户范围与账号操作审批。</p>
      </div>
    </section>

    <section class="security-context-panel" aria-label="登录、账号和客户范围">
      <div class="security-context-main">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">SIGN IN</span>
            <h2>登录会话</h2>
            <p>客户范围以当前登录账号为准。</p>
          </div>
          <StatusBadge :label="authSession ? authSession.roleLabel : '未登录'" :tone="authSession ? 'green' : 'amber'" />
        </div>

        <p class="empty-hint">当前会话：{{ authSession ? `${authSession.operator} / ${authSession.roleLabel}` : '未登录' }}。切换账号请使用顶部退出入口。</p>

        <form class="auth-password-strip" aria-label="自助改密续期" @submit.prevent="handleChangePassword">
          <div>
            <span>自助改密</span>
            <strong>{{ authSession ? '登录后更新自己的密码' : '需先建立登录会话' }}</strong>
            <small>更新当前登录账号的密码。</small>
          </div>
          <label>
            <span>当前密码</span>
            <input
              v-model="passwordCurrent"
              :disabled="passwordBusy || !authSession"
              type="password"
              autocomplete="current-password"
            />
          </label>
          <label>
            <span>新密码</span>
            <input
              v-model="passwordNew"
              :disabled="passwordBusy || !authSession"
              type="password"
              autocomplete="new-password"
            />
          </label>
          <label>
            <span>确认新密码</span>
            <input
              v-model="passwordConfirm"
              :disabled="passwordBusy || !authSession"
              type="password"
              autocomplete="new-password"
            />
          </label>
          <div class="auth-session-actions">
            <button class="primary-button" type="submit" :disabled="passwordBusy || !authSession">
              {{ passwordBusy ? '处理中' : '更新密码' }}
            </button>
          </div>
          <small v-if="passwordMessage" class="auth-session-message">{{ passwordMessage }}</small>
        </form>

        <div class="security-identity-grid">
          <article>
            <span>操作人</span>
            <strong>{{ authSession?.operator || '未登录' }}</strong>
          </article>
          <article>
            <span>角色</span>
            <strong>{{ authSession?.roleLabel || '未登录' }}</strong>
            <small>{{ authSession?.role || '' }}</small>
          </article>
          <article>
            <span>数据范围</span>
            <strong>{{ authSession?.dataScopeLabel || '未登录' }}</strong>
          </article>
          <article>
            <span>客户范围</span>
            <strong>
              {{
                authSession?.customerScope.length
                  ? authSession.customerScope.join('、')
                  : '未登录或当前账号没有客户范围'
              }}
            </strong>
          </article>
        </div>
      </div>

      <aside class="security-context-side">
        <div class="security-account-contract desk-page" :class="{ 'is-detail': accountDetailOpen }">
          <div class="module-master-panel">
            <strong>账号</strong>
            <label>
              <span>查找账号</span>
              <input v-model="accountKeyword" aria-label="查找账号" placeholder="账号或姓名" />
            </label>
            <p v-if="accountLoadError" class="operation-feedback danger">{{ accountLoadError }}</p>
            <p v-if="filteredAccounts.length === 0" class="empty-hint">暂无账号</p>
            <button
              v-for="account in filteredAccounts"
              :key="account.username"
              class="desk-card"
              type="button"
              @click="selectAccount(account)"
            >
              <span class="desk-card-top">
                <strong>{{ account.username }} / {{ account.operator }}</strong>
                <StatusBadge :label="accountStatusLabel(account)" :tone="accountStatusTone(account)" />
              </span>
              <span>{{ account.roleLabel }} · {{ account.dataScopeLabel }}</span>
              <span>
                {{ account.demoAccount ? '演示账号' : '生产账号' }} · 失败 {{ account.failedLoginCount }} 次
                <template v-if="account.customerScope.length"> · 客户 {{ account.customerScope.join('、') }}</template>
                <template v-if="account.allowedProcesses?.length"> · 可做 {{ account.allowedProcesses.join('、') }}</template>
              </span>
            </button>
          </div>
          <form class="customer-auth-form module-side-panel" @submit.prevent="saveProcesses">
            <button class="secondary-button detail-back" type="button" @click="backToAccounts">返回账号</button>
            <strong>可做工序</strong>
            <p class="customer-auth-hint">点上面的账号，勾选雕刻、镶嵌、抛光中的一道或多道。</p>
            <p>{{ processForm.username ? `${processForm.username} / ${processForm.operator}` : '尚未选择账号' }}</p>
            <label v-for="name in stationProcessNames" :key="name">
              <input v-model="processForm.processes" type="checkbox" :value="name" />
              {{ name }}
            </label>
            <button class="primary-button" type="submit" :disabled="accountEditorBusy || !processForm.username">
              申请工序变更
            </button>
            <p v-if="processMessage" class="customer-auth-message">{{ processMessage }}</p>
          </form>
        </div>

        <div class="security-customer-auth">
          <strong>客户授权</strong>
          <p class="customer-auth-hint">
            客户范围以授权表为准。授权或撤销需要总经理或管理员权限。
          </p>
          <form class="customer-auth-form" @submit.prevent="grantCustomerAuth">
            <label>
              <span>账号</span>
              <input v-model="customerAuthForm.username" placeholder="如 sales01" aria-label="授权账号" />
            </label>
            <label>
              <span>客户编码</span>
              <input v-model="customerAuthForm.customerCode" placeholder="如 CUST-1001" aria-label="客户编码" />
            </label>
            <label>
              <span>客户名称</span>
              <input v-model="customerAuthForm.customerName" placeholder="可选" aria-label="客户名称" />
            </label>
            <button class="primary-button" type="submit" :disabled="customerAuthBusy">
              {{ customerAuthBusy ? '授权中…' : '授权客户' }}
            </button>
          </form>
          <p v-if="customerAuthMessage" class="customer-auth-message">{{ customerAuthMessage }}</p>
          <p v-if="customerAuthLoadError" class="operation-feedback danger">{{ customerAuthLoadError }}</p>
          <article v-for="auth in customerAuthorizations" :key="`${auth.username}|${auth.customerCode}`">
            <div>
              <b>{{ auth.username }} → {{ auth.customerCode }}</b>
              <span>{{ auth.customerName || '未填客户名称' }}</span>
              <small>授权人 {{ auth.grantedBy || '未记录' }} · {{ auth.grantedAt || '未记录时间' }}</small>
            </div>
            <button class="table-action" type="button" @click="revokeCustomerAuth(auth)">撤销</button>
          </article>
          <p v-if="!customerAuthLoadError && customerAuthorizations.length === 0" class="empty-hint">
            暂无客户授权记录
          </p>
        </div>
      </aside>
    </section>

    <section class="security-context-panel" aria-label="用户管理">
      <div class="security-context-main">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">USERS</span>
            <h2>用户管理</h2>
            <p>管理员和总经理可以新建账号、修改角色和数据范围、启停或解锁。新密码至少 12 位，并且同时包含字母和数字。编辑时留空密码表示不修改。</p>
          </div>
          <button class="secondary-button" type="button" @click="resetAccountEditor">新建账号</button>
        </div>
        <form class="account-admin-form" @submit.prevent="saveAccount">
          <div class="account-admin-grid">
            <label>
              <span>账号</span>
              <input v-model="accountEditor.username" :disabled="accountEditor.editing" autocomplete="off" />
            </label>
            <label>
              <span>姓名</span>
              <input v-model="accountEditor.operator" autocomplete="off" />
            </label>
            <label>
              <span>{{ accountEditor.editing ? '重置密码' : '初始密码' }}</span>
              <input v-model="accountEditor.password" type="password" autocomplete="new-password" />
            </label>
            <label>
              <span>角色</span>
              <select v-model="accountEditor.role">
                <option v-for="role in accountRoles" :key="role.value" :value="role.value">{{ role.label }}</option>
              </select>
            </label>
            <label>
              <span>数据范围</span>
              <select v-model="accountEditor.dataScope">
                <option v-for="scope in dataScopes" :key="scope.value" :value="scope.value">{{ scope.label }}</option>
              </select>
            </label>
          </div>
          <div class="account-admin-checks">
            <label><input v-model="accountEditor.enabled" type="checkbox" /> 启用</label>
            <label><input v-model="accountEditor.demoAccount" type="checkbox" /> 演示账号</label>
          </div>
          <div class="auth-session-actions">
            <button class="primary-button" type="submit" :disabled="accountEditorBusy">
              {{ accountEditorBusy ? '保存中' : accountEditor.editing ? '申请更新账号' : '申请创建账号' }}
            </button>
          </div>
          <p v-if="accountEditorMessage" class="customer-auth-message">{{ accountEditorMessage }}</p>
        </form>
      </div>
      <aside class="security-context-side">
        <div class="security-account-contract">
          <strong>账号操作</strong>
          <article v-for="account in filteredAccounts" :key="`manage-${account.username}`" class="desk-card production-handoff-card">
            <div>
              <b>{{ account.username }} / {{ account.operator }}</b>
              <span>{{ account.roleLabel }} · {{ account.dataScopeLabel }}</span>
            </div>
            <div class="account-row-actions">
              <button class="table-action" type="button" @click="editAccount(account)">编辑</button>
              <button class="table-action" type="button" @click="toggleAccount(account)">
                {{ account.enabled ? '申请停用' : '申请启用' }}
              </button>
              <button v-if="account.locked" class="table-action" type="button" @click="unlockAccount(account)">申请解锁</button>
            </div>
          </article>
          <p v-if="authAccounts.length === 0" class="empty-hint">暂无账号可管理</p>
        </div>
      </aside>
    </section>

    <AccountApprovalPanel :request="approvalRequest" :operator="authSession?.operator || ''" :executing="accountEditorBusy" :executed-application-code="executedApplicationCode" @execute="executeApprovedAction" />

    <section class="security-context-panel" aria-label="存储内容管理">
      <div class="security-context-main">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">STORAGE</span>
            <h2>存储内容</h2>
            <p>
              {{
                storedContent
                  ? `${storedContent.provider} · ${storedContent.rootPath} · 目录 ${storedContent.usedLabel} · 磁盘已用 ${storedContent.diskUsedPercent}%`
                  : '查看已登记的照片、视频和附件，并下线不再保留的本地文件。'
              }}
            </p>
          </div>
          <button class="secondary-button" type="button" @click="loadStoredContent">刷新</button>
        </div>
        <p v-if="contentError" class="operation-feedback danger">{{ contentError }}</p>
        <p v-if="contentMessage" class="customer-auth-message">{{ contentMessage }}</p>
        <div class="ops-table-wrap desk-table-wrap">
          <table class="ops-data-table">
            <thead>
              <tr>
                <th>文件</th>
                <th>业务</th>
                <th>归属</th>
                <th>大小</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in storedContent?.items ?? []" :key="item.code">
                <td>
                  <span class="cell-stack">
                    <b>{{ item.fileName }}</b>
                    <small>{{ item.code }}</small>
                  </span>
                </td>
                <td>{{ item.businessType }} / {{ item.businessCode }}</td>
                <td>{{ item.owner }}</td>
                <td>{{ item.sizeLabel }}</td>
                <td>{{ item.status }}</td>
                <td>
                  <button
                    class="table-action"
                    type="button"
                    :disabled="item.status === '已下线' || contentBusyCode === item.code"
                    @click="retireContent(item.code, item.fileName)"
                  >
                    {{ item.status === '已下线' ? '已下线' : '下线' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="desk-cards">
          <article v-for="item in storedContent?.items ?? []" :key="`card-${item.code}`" class="desk-card production-handoff-card">
            <span class="desk-card-top">
              <strong>{{ item.fileName }}</strong>
              <span>{{ item.status }}</span>
            </span>
            <span>{{ item.businessType }} / {{ item.businessCode }}</span>
            <span>{{ item.owner }} · {{ item.sizeLabel }}</span>
            <button
              class="table-action"
              type="button"
              :disabled="item.status === '已下线' || contentBusyCode === item.code"
              @click="retireContent(item.code, item.fileName)"
            >
              {{ item.status === '已下线' ? '已下线' : '下线' }}
            </button>
          </article>
        </div>
        <p v-if="storedContent && storedContent.items.length === 0" class="empty-hint">还没有存储内容</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-password-strip,
.customer-auth-form { display: grid; gap: 10px; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); }
form label { display: grid; gap: 6px; min-width: 0; }
form input:not([type="checkbox"]), form select { box-sizing: border-box; width: 100%; min-width: 0; }
.customer-auth-form label:has(input[type="checkbox"]) { display: flex; align-items: center; }
</style>
