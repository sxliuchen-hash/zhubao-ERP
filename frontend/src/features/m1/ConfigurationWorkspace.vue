<script setup lang="ts">
import { onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
import {
  changeOwnPassword,
  fetchAuthAccounts,
  fetchAuthDemoUsers,
  fetchAuthSession,
  loginAuthSession,
  logoutAuthSession,
  unlockAuthAccount,
  updateAuthAccountStatus,
  upsertAuthAccount,
  type AuthSession,
  type AuthUserAccountSummary,
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
const authDemoUsers = ref<string[]>([]);
const authUsername = ref('');
const authPassword = ref('');
const authBusy = ref(false);
const authMessage = ref('');
const passwordCurrent = ref('');
const passwordNew = ref('');
const passwordConfirm = ref('');
const passwordBusy = ref(false);
const passwordMessage = ref('');
const authAccounts = ref<AuthUserAccountSummary[]>([]);
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
const processBusy = ref(false);
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
  await loadDemoUsers();
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

async function loadDemoUsers() {
  try {
    authDemoUsers.value = await fetchAuthDemoUsers();
  } catch {
    authDemoUsers.value = [];
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
  accountEditorBusy.value = true;
  try {
    await upsertAuthAccount({
      username: editor.username.trim(),
      password: editor.password.trim() || undefined,
      operator: editor.operator.trim(),
      role: editor.role,
      dataScope: editor.dataScope,
      enabled: editor.enabled,
      demoAccount: editor.demoAccount,
    });
    accountEditorMessage.value = editor.editing ? `已更新 ${editor.username}` : `已创建 ${editor.username}`;
    if (!editor.editing) {
      resetAccountEditor();
    } else {
      accountEditor.value.password = '';
    }
    await loadAuthAccounts();
  } catch (error) {
    accountEditorMessage.value = error instanceof Error ? error.message : '保存账号失败';
  } finally {
    accountEditorBusy.value = false;
  }
}

async function toggleAccount(account: AuthUserAccountSummary) {
  accountEditorMessage.value = '';
  try {
    await updateAuthAccountStatus(account.username, {
      enabled: !account.enabled,
      reason: account.enabled ? '管理员停用' : '管理员启用',
    });
    accountEditorMessage.value = account.enabled ? `已停用 ${account.username}` : `已启用 ${account.username}`;
    await loadAuthAccounts();
  } catch (error) {
    accountEditorMessage.value = error instanceof Error ? error.message : '更新账号状态失败';
  }
}

async function unlockAccount(account: AuthUserAccountSummary) {
  accountEditorMessage.value = '';
  try {
    await unlockAuthAccount(account.username, { reason: '管理员解锁' });
    accountEditorMessage.value = `已解锁 ${account.username}`;
    await loadAuthAccounts();
  } catch (error) {
    accountEditorMessage.value = error instanceof Error ? error.message : '解锁失败';
  }
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
}

async function saveProcesses() {
  processMessage.value = '';
  if (!processForm.value.username) {
    processMessage.value = '请先点一个账号';
    return;
  }
  processBusy.value = true;
  try {
    await upsertAuthAccount({
      username: processForm.value.username,
      operator: processForm.value.operator,
      role: processForm.value.role,
      dataScope: processForm.value.dataScope,
      customerScope: processForm.value.customerScope,
      enabled: processForm.value.enabled,
      demoAccount: processForm.value.demoAccount,
      allowedProcesses: processForm.value.processes,
    });
    processMessage.value = processForm.value.processes.length
      ? `已绑定 ${processForm.value.processes.join('、')}`
      : '已清空可做工序';
    await loadAuthAccounts();
  } catch (error) {
    processMessage.value = error instanceof Error ? error.message : '保存工序失败';
  } finally {
    processBusy.value = false;
  }
}

function syncDemoPassword() {
  if (authDemoUsers.value.includes(authUsername.value)) {
    authPassword.value = `${authUsername.value}123`;
  }
}

async function handleLogin() {
  authBusy.value = true;
  authMessage.value = '';
  try {
    authSession.value = await loginAuthSession({
      username: authUsername.value,
      password: authPassword.value,
    });
    authMessage.value = `已登录：${authSession.value.roleLabel}`;
    await loadAuthAccounts();
    await loadCustomerAuthorizations();
  } catch (error) {
    authMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    authBusy.value = false;
  }
}

async function handleLogout() {
  authBusy.value = true;
  authMessage.value = '';
  try {
    await logoutAuthSession();
    authSession.value = null;
    authMessage.value = '已退出登录会话';
    await loadAuthAccounts();
    await loadCustomerAuthorizations();
  } finally {
    authBusy.value = false;
  }
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
        <h2>登录与客户范围</h2>
        <p>登录、查看账号、授权客户范围。</p>
      </div>
    </section>

    <section class="security-context-panel" aria-label="登录、账号和客户范围">
      <div class="security-context-main">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">SIGN IN</span>
            <h2>登录会话</h2>
            <p>登录后，后续请求携带 Bearer token。客户范围以当前会话为准。</p>
          </div>
          <StatusBadge :label="authSession ? authSession.roleLabel : '未登录'" :tone="authSession ? 'green' : 'amber'" />
        </div>

        <form class="auth-session-strip" aria-label="轻量登录会话" @submit.prevent="handleLogin">
          <div>
            <span>当前会话</span>
            <strong>{{ authSession ? `${authSession.operator} / ${authSession.roleLabel}` : '未登录' }}</strong>
            <small>
              {{
                authSession
                  ? `会话 ${authSession.expiresAt} / 密码到期 ${authSession.passwordExpiresAt || '未记录'}${authSession.passwordChangeRequired ? ' / 需先自助改密' : ''}`
                  : '登录成功后写入本地 token'
              }}
            </small>
          </div>
          <label>
            <span>账号</span>
            <input
              v-model="authUsername"
              :disabled="authBusy"
              list="auth-demo-users"
              autocomplete="username"
              @change="syncDemoPassword"
            />
            <datalist id="auth-demo-users">
              <option v-for="username in authDemoUsers" :key="username" :value="username" />
            </datalist>
          </label>
          <label>
            <span>密码</span>
            <input v-model="authPassword" :disabled="authBusy" type="password" autocomplete="current-password" />
          </label>
          <div class="auth-session-actions">
            <button class="primary-button" type="submit" :disabled="authBusy">
              {{ authBusy ? '处理中' : '登录' }}
            </button>
            <button class="secondary-button" type="button" :disabled="authBusy || !authSession" @click="handleLogout">退出</button>
          </div>
          <small v-if="authMessage" class="auth-session-message">{{ authMessage }}</small>
        </form>

        <form class="auth-password-strip" aria-label="自助改密续期" @submit.prevent="handleChangePassword">
          <div>
            <span>自助改密</span>
            <strong>{{ authSession ? '登录后更新自己的密码' : '需先建立登录会话' }}</strong>
            <small>仅接受 Bearer token，不使用请求头角色兜底。</small>
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
        <div class="security-account-contract">
          <strong>账号</strong>
          <p v-if="accountLoadError" class="operation-feedback danger">{{ accountLoadError }}</p>
          <p v-if="authAccounts.length === 0" class="empty-hint">暂无账号</p>
          <article
            v-for="account in authAccounts"
            :key="account.username"
            class="account-process-row"
            @click="selectAccount(account)"
          >
            <div>
              <b>{{ account.username }} / {{ account.operator }}</b>
              <span>{{ account.roleLabel }} · {{ account.dataScopeLabel }}</span>
              <small>
                {{ account.demoAccount ? '演示账号' : '生产账号' }} · 失败 {{ account.failedLoginCount }} 次
                <template v-if="account.customerScope.length"> · 客户 {{ account.customerScope.join('、') }}</template>
                <template v-if="account.allowedProcesses?.length"> · 可做 {{ account.allowedProcesses.join('、') }}</template>
                <template v-if="account.lockedUntil"> · 锁定至 {{ account.lockedUntil }}</template>
                <template v-if="account.passwordExpired"> · 密码已过期</template>
              </small>
            </div>
            <StatusBadge :label="accountStatusLabel(account)" :tone="accountStatusTone(account)" />
          </article>
          <form class="customer-auth-form" @submit.prevent="saveProcesses">
            <strong>可做工序</strong>
            <p class="customer-auth-hint">点上面的账号，勾选雕刻、镶嵌、抛光中的一道或多道。</p>
            <p>{{ processForm.username ? `${processForm.username} / ${processForm.operator}` : '尚未选择账号' }}</p>
            <label v-for="name in stationProcessNames" :key="name">
              <input v-model="processForm.processes" type="checkbox" :value="name" />
              {{ name }}
            </label>
            <button class="primary-button" type="submit" :disabled="processBusy || !processForm.username">
              {{ processBusy ? '保存中' : '保存工序' }}
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
              {{ accountEditorBusy ? '保存中' : accountEditor.editing ? '保存账号' : '创建账号' }}
            </button>
          </div>
          <p v-if="accountEditorMessage" class="customer-auth-message">{{ accountEditorMessage }}</p>
        </form>
      </div>
      <aside class="security-context-side">
        <div class="security-account-contract">
          <strong>账号操作</strong>
          <article v-for="account in authAccounts" :key="`manage-${account.username}`" class="account-process-row">
            <div>
              <b>{{ account.username }} / {{ account.operator }}</b>
              <span>{{ account.roleLabel }} · {{ account.dataScopeLabel }}</span>
            </div>
            <div class="account-row-actions">
              <button class="table-action" type="button" @click="editAccount(account)">编辑</button>
              <button class="table-action" type="button" @click="toggleAccount(account)">
                {{ account.enabled ? '停用' : '启用' }}
              </button>
              <button v-if="account.locked" class="table-action" type="button" @click="unlockAccount(account)">解锁</button>
            </div>
          </article>
          <p v-if="authAccounts.length === 0" class="empty-hint">暂无账号可管理</p>
        </div>
      </aside>
    </section>

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
        <div class="ops-table-wrap">
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
        <p v-if="storedContent && storedContent.items.length === 0" class="empty-hint">还没有存储内容</p>
      </div>
    </section>
  </div>
</template>
