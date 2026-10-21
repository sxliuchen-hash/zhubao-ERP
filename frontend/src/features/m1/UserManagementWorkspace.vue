<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import {
  deleteAuthAccount, fetchAuthAccountOptions, fetchAuthAccounts, fetchAuthSession,
  logoutAuthSession, unlockAuthAccount, updateAuthAccountStatus, upsertAuthAccount,
  type AuthAccountOptions, type AuthSession, type AuthUserAccountSummary,
} from '../../services/authApi';
import { fetchCustomers, type CustomerProfile } from '../../services/masterDataApi';

const session = ref<AuthSession | null>(null);
const ready = ref(false);
const loading = ref(false);
const busy = ref(false);
const loadError = ref('');
const message = ref('');
const actionError = ref('');
const accounts = ref<AuthUserAccountSummary[]>([]);
const options = ref<AuthAccountOptions>({ roles: [] });
const customers = ref<CustomerProfile[]>([]);
const keyword = ref('');
const status = ref('all');
const coverDutyOptions = [
  { code: 'INTAKE', label: '收货', packaged: true },
  { code: 'REPORT', label: '报工', packaged: true },
  { code: 'QUALITY', label: '质检', packaged: true },
  { code: 'SPLIT', label: '拆单', packaged: true },
  { code: 'LABEL', label: '标签打印', packaged: true },
  { code: 'MASTER', label: '客户与材质档案', packaged: true },
  { code: 'SHIP', label: '发货', packaged: false },
  { code: 'RETURN', label: '退货', packaged: false },
  { code: 'REGISTER', label: '登记导入', packaged: false },
  { code: 'LOSS', label: '损耗审批', packaged: false },
];
const defaultCoverDuties = coverDutyOptions.filter((item) => item.packaged).map((item) => item.code);
const editorMode = ref<'create' | 'edit' | 'password' | null>(null);
const editor = ref(emptyEditor());
const editorSection = ref<HTMLElement | null>(null);
const scopes = [
  { value: 'SELF', label: '本人任务' }, { value: 'TEAM', label: '本班组/工位' },
  { value: 'DEPARTMENT', label: '本部门' }, { value: 'CUSTOMER_RANGE', label: '授权客户范围' },
  { value: 'ALL', label: '全部数据' },
];
const organizationHint = computed(() => {
  if (editor.value.dataScope === 'TEAM' && !editor.value.teamCode.trim()) return '未填写班组编号时，只能看到本人经手的单据';
  if (editor.value.dataScope === 'DEPARTMENT' && !editor.value.departmentCode.trim()) return '未填写部门编号时，只能看到本人经手的单据';
  if (editor.value.dataScope === 'CUSTOMER_RANGE' && selectedCustomerCodes.value.length === 0) return '未选择客户时，看不到任何客户单据';
  return '';
});
const processes = ['雕刻', '镶嵌', '抛光'];
const isAdmin = computed(() => session.value?.role === 'ADMIN');
const visibleAccounts = computed(() => accounts.value.filter(account => {
  const matchesText = `${account.username} ${account.operator} ${account.roleLabel}`.toLowerCase().includes(keyword.value.trim().toLowerCase());
  return matchesText && (status.value === 'all' || (status.value === 'enabled' ? account.enabled : !account.enabled));
}));
const selectedRole = computed(() => options.value.roles.find(role => role.value === editor.value.role));
const selectedCustomerCodes = computed(() => editor.value.customerCodes.split(/[\s,，;；]+/).filter(Boolean));
const unresolvedCustomerCodes = computed(() => selectedCustomerCodes.value.filter(code =>
  !customers.value.some(customer => String(customer.customerId) === code)));

function toggleCustomerCode(id: number, checked: boolean) {
  const code = String(id);
  const values = selectedCustomerCodes.value.filter(item => item !== code);
  if (checked) values.push(code);
  editor.value.customerCodes = [...new Set(values)].join('\n');
}

function emptyEditor() {
  return { username: '', operator: '', password: '', confirmPassword: '', role: 'OPERATOR', dataScope: 'SELF',
    customerCodes: '', teamCode: '', departmentCode: '', allowedProcesses: [] as string[], coverDuties: [...defaultCoverDuties], enabled: true, demoAccount: false };
}

onMounted(async () => {
  try {
    session.value = await fetchAuthSession();
    if (isAdmin.value) await reload();
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '用户管理加载失败';
  } finally {
    ready.value = true;
  }
});

async function reload() {
  if (!isAdmin.value || loading.value) return;
  loading.value = true;
  loadError.value = '';
  try {
    const [users, accountOptions, customerProfiles] = await Promise.all([
      fetchAuthAccounts(), fetchAuthAccountOptions(), fetchCustomers(),
    ]);
    accounts.value = users;
    options.value = accountOptions;
    customers.value = customerProfiles;
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '用户列表加载失败';
  } finally {
    loading.value = false;
  }
}

function openEditor(account?: AuthUserAccountSummary, passwordOnly = false) {
  if (busy.value || !isAdmin.value) return;
  actionError.value = '';
  message.value = '';
  editorMode.value = account ? passwordOnly ? 'password' : 'edit' : 'create';
  editor.value = account ? {
    username: account.username, operator: account.operator, password: '', confirmPassword: '', role: account.role,
    dataScope: account.dataScope, customerCodes: account.customerScope.join('\n'),
    teamCode: account.teamCode ?? '', departmentCode: account.departmentCode ?? '',
    allowedProcesses: [...(account.allowedProcesses ?? [])],
    coverDuties: account.role === 'BUSINESS_MANAGER' && account.coverDuties?.length ? [...account.coverDuties] : [...defaultCoverDuties],
    enabled: account.enabled, demoAccount: account.demoAccount,
  } : emptyEditor();
  void nextTick(() => editorSection.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }));
}

async function runAction(action: () => Promise<unknown>, success: string) {
  if (busy.value || !isAdmin.value) return;
  busy.value = true;
  actionError.value = '';
  message.value = '';
  try {
    await action();
    message.value = success;
    editorMode.value = null;
    editor.value.password = '';
    editor.value.confirmPassword = '';
    await reload();
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '操作失败，请重试';
  } finally {
    busy.value = false;
  }
}

async function saveAccount() {
  const form = editor.value;
  actionError.value = '';
  if (!form.username.trim() || !form.operator.trim()) {
    actionError.value = '请填写账号和姓名';
    return;
  }
  const passwordRequired = editorMode.value === 'create' || editorMode.value === 'password';
  if (passwordRequired && !form.password) {
    actionError.value = '请填写密码';
    return;
  }
  if (form.password && (form.password.length < 8 || !/[a-zA-Z]/.test(form.password) || !/[0-9]/.test(form.password))) {
    actionError.value = '密码至少 8 位，且同时包含字母和数字';
    return;
  }
  if (form.password !== form.confirmPassword) {
    actionError.value = '两次密码不一致';
    return;
  }
  const customerScope = [...new Set(form.customerCodes.split(/[\s,，;；]+/).filter(Boolean))];
  if (editorMode.value !== 'password' && form.dataScope === 'CUSTOMER_RANGE' && customerScope.length === 0) {
    actionError.value = '请至少选择一个客户档案';
    return;
  }
  if (editorMode.value !== 'password' && form.role === 'BUSINESS_MANAGER' && form.coverDuties.length === 0) {
    actionError.value = '业务管理至少保留一项权限';
    return;
  }
  if (editorMode.value === 'create' && accounts.value.some(account => account.username.toLowerCase() === form.username.trim().toLowerCase())) {
    actionError.value = '账号已存在，请使用编辑操作';
    return;
  }
  await runAction(async () => {
    await upsertAuthAccount({
      username: form.username.trim(), operator: form.operator.trim(), password: form.password || undefined,
      role: form.role, dataScope: form.dataScope, customerScope: form.dataScope === 'CUSTOMER_RANGE' ? customerScope : [],
      teamCode: form.teamCode.trim(), departmentCode: form.departmentCode.trim(),
      allowedProcesses: [...form.allowedProcesses],
      coverDuties: form.role === 'BUSINESS_MANAGER' ? [...form.coverDuties] : [],
      enabled: form.enabled, demoAccount: form.demoAccount,
    });
    if (form.username === session.value?.username) {
      await logoutAuthSession();
      session.value = null;
    }
  }, editorMode.value === 'password' ? `已重置 ${form.username} 的密码` : `已保存用户 ${form.username}`);
}

async function removeAccount(account: AuthUserAccountSummary) {
  if (!globalThis.confirm(`确认删除用户 ${account.username}（${account.operator}）？删除后无法登录，历史业务记录保留。`)) return;
  await runAction(() => deleteAuthAccount(account.username), `已删除用户 ${account.username}`);
}
</script>

<template>
  <div class="user-management-workspace ops-workspace">
    <p v-if="!ready" role="status">正在加载用户管理</p>
    <p v-else-if="!isAdmin" role="alert" class="operation-feedback danger">仅系统管理员可以管理用户和配置权限。{{ loadError }}</p>
    <template v-else>
      <section class="section-block">
        <div class="section-heading">
          <div><h2>用户管理</h2><p>管理账号、角色与数据权限，保存后直接生效。</p></div>
          <div class="user-actions">
            <button type="button" class="secondary-button" :disabled="busy || loading" @click="reload">刷新</button>
            <button type="button" class="primary-button" :disabled="busy || loading || !!loadError" @click="openEditor()">新增用户</button>
          </div>
        </div>
        <div class="user-filters">
          <label>查找用户<input v-model="keyword" aria-label="查找用户" placeholder="账号、姓名或角色" /></label>
          <label>账号状态<select v-model="status" aria-label="账号状态"><option value="all">全部</option><option value="enabled">启用</option><option value="disabled">停用</option></select></label>
          <span>共 {{ visibleAccounts.length }} 个用户</span>
        </div>
        <p v-if="loading" role="status">正在加载用户列表</p>
        <p v-if="loadError" role="alert" class="operation-feedback danger">{{ loadError }}</p>
        <p v-if="message" role="status" class="operation-feedback">{{ message }}</p>
        <p v-if="actionError" role="alert" class="operation-feedback danger">{{ actionError }}</p>
        <div class="user-table-wrap">
          <table class="ops-data-table">
            <thead><tr><th>账号 / 姓名</th><th>角色</th><th>数据权限</th><th>工序</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="account in visibleAccounts" :key="account.username" :data-user="account.username">
                <td><b>{{ account.username }}</b><div>{{ account.operator }}</div></td>
                <td>{{ account.roleLabel }}</td>
                <td>{{ account.dataScopeLabel }}<div v-if="account.dataScope === 'CUSTOMER_RANGE'">{{ account.customerScope.join('、') || '未授权客户' }}</div><div v-if="account.teamCode">班组 {{ account.teamCode }}</div><div v-if="account.departmentCode">部门 {{ account.departmentCode }}</div></td>
                <td>{{ account.allowedProcesses?.join('、') || '未绑定工序' }}</td>
                <td>{{ account.enabled ? '启用' : '停用' }}{{ account.locked ? ' · 已锁定' : '' }}{{ account.passwordChangeRequired ? ' · 待改密' : '' }}</td>
                <td><div class="user-actions">
                  <button class="table-action" type="button" :disabled="busy || loading || !!loadError" @click="openEditor(account)">编辑</button>
                  <button class="table-action" type="button" :disabled="busy || loading || !!loadError" @click="openEditor(account, true)">重置密码</button>
                  <button class="table-action" type="button" :disabled="busy || loading || !!loadError || account.username === session?.username" @click="runAction(() => updateAuthAccountStatus(account.username, { enabled: !account.enabled, reason: '管理员用户管理' }), `已${account.enabled ? '停用' : '启用'} ${account.username}`)">{{ account.enabled ? '停用' : '启用' }}</button>
                  <button v-if="account.locked" class="table-action" type="button" :disabled="busy || loading || !!loadError" @click="runAction(() => unlockAuthAccount(account.username, { reason: '管理员解锁' }), `已解锁 ${account.username}`)">解锁</button>
                  <button class="table-action danger" type="button" :disabled="busy || loading || !!loadError || account.username === session?.username" @click="removeAccount(account)">删除</button>
                </div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="user-cards" aria-label="用户列表">
          <article v-for="account in visibleAccounts" :key="account.username" class="user-card">
            <header>
              <strong>{{ account.username }}</strong>
              <span>{{ account.operator }}</span>
            </header>
            <p>角色 {{ account.roleLabel }}</p>
            <p>数据权限 {{ account.dataScopeLabel }}<span v-if="account.dataScope === 'CUSTOMER_RANGE'"> · {{ account.customerScope.join('、') || '未授权客户' }}</span></p>
            <p>工序 {{ account.allowedProcesses?.join('、') || '未绑定工序' }}</p>
            <p>状态 {{ account.enabled ? '启用' : '停用' }}{{ account.locked ? ' · 已锁定' : '' }}{{ account.passwordChangeRequired ? ' · 待改密' : '' }}</p>
            <div class="user-actions">
              <button class="table-action" type="button" :disabled="busy || loading || !!loadError" @click="openEditor(account)">编辑</button>
              <button class="table-action" type="button" :disabled="busy || loading || !!loadError" @click="openEditor(account, true)">重置密码</button>
              <button class="table-action" type="button" :disabled="busy || loading || !!loadError || account.username === session?.username" @click="runAction(() => updateAuthAccountStatus(account.username, { enabled: !account.enabled, reason: '管理员用户管理' }), `已${account.enabled ? '停用' : '启用'} ${account.username}`)">{{ account.enabled ? '停用' : '启用' }}</button>
              <button v-if="account.locked" class="table-action" type="button" :disabled="busy || loading || !!loadError" @click="runAction(() => unlockAuthAccount(account.username, { reason: '管理员解锁' }), `已解锁 ${account.username}`)">解锁</button>
              <button class="table-action danger" type="button" :disabled="busy || loading || !!loadError || account.username === session?.username" @click="removeAccount(account)">删除</button>
            </div>
          </article>
        </div>
        <p v-if="!loading && !loadError && !visibleAccounts.length" class="empty-hint">暂无符合条件的用户</p>
      </section>

      <section v-if="editorMode" ref="editorSection" class="section-block user-editor" aria-label="用户编辑">
        <div class="section-heading"><h2>{{ editorMode === 'create' ? '新增用户' : editorMode === 'password' ? `重置密码 · ${editor.username}` : `编辑用户 · ${editor.username}` }}</h2><button class="secondary-button" type="button" :disabled="busy" @click="editorMode = null">取消</button></div>
        <form @submit.prevent="saveAccount">
          <p v-if="actionError" role="alert" class="operation-feedback danger">{{ actionError }}</p>
          <p v-if="editor.username === session?.username">修改自己的账号后，需要重新登录。</p>
          <fieldset :disabled="busy">
            <div v-if="editorMode !== 'password'" class="user-form-grid">
              <label>账号<input v-model="editor.username" aria-label="用户账号" :disabled="editorMode === 'edit'" required autocomplete="off" /></label>
              <label>姓名<input v-model="editor.operator" aria-label="用户姓名" required /></label>
              <label>角色<select v-model="editor.role" aria-label="用户角色"><option v-for="role in options.roles" :key="role.value" :value="role.value">{{ role.label }}</option></select></label>
              <label>数据范围<select v-model="editor.dataScope" aria-label="用户数据范围"><option v-for="scope in scopes" :key="scope.value" :value="scope.value">{{ scope.label }}</option></select></label>
              <label>班组编号<input v-model="editor.teamCode" aria-label="班组编号" maxlength="40" placeholder="选择本班组时必填" /></label>
              <label>部门编号<input v-model="editor.departmentCode" aria-label="部门编号" maxlength="40" placeholder="选择本部门时必填" /></label>
              <p v-if="organizationHint" class="operation-feedback danger">{{ organizationHint }}</p>
            </div>
            <div v-if="editorMode !== 'password' && editor.role === 'BUSINESS_MANAGER'" class="cover-duty-panel">
              <p>默认勾选收货、报工、质检，以及拆单、标签和客户档案。其余项默认不勾，可以按人另加或取消。数据范围在上面单独选。</p>
              <div class="user-checks" aria-label="业务管理权限">
                <label v-for="duty in coverDutyOptions" :key="duty.code">
                  <input v-model="editor.coverDuties" type="checkbox" :value="duty.code" :aria-label="`顶岗${duty.label}`" />
                  {{ duty.label }}
                </label>
              </div>
            </div>
            <div v-if="editorMode !== 'password' && editor.dataScope === 'CUSTOMER_RANGE'" class="customer-scope" aria-label="授权客户档案">
              <span>授权客户档案</span>
              <label v-for="customer in customers" :key="customer.customerId">
                <input type="checkbox" :checked="selectedCustomerCodes.includes(String(customer.customerId))"
                  :aria-label="`授权客户${customer.name}`"
                  @change="toggleCustomerCode(customer.customerId, ($event.target as HTMLInputElement).checked)" />
                {{ customer.name }}（{{ customer.customerCode }} · ID {{ customer.customerId }}）
              </label>
              <p v-if="unresolvedCustomerCodes.length">历史授权值待人工核对：{{ unresolvedCustomerCodes.join('、') }}。请选择对应客户档案。</p>
            </div>
            <template v-if="editorMode !== 'password'">
              <p>可操作工序</p><div class="user-checks"><label v-for="process in processes" :key="process"><input v-model="editor.allowedProcesses" type="checkbox" :value="process" />{{ process }}</label></div>
              <div class="user-checks"><label><input v-model="editor.enabled" type="checkbox" :disabled="editor.username === session?.username" />启用账号</label></div>
              <details class="user-permissions"><summary>查看角色包含的操作权限（{{ selectedRole?.permissions.length ?? 0 }} 项）</summary><p>这里是角色模板。业务管理还要看已勾选的顶岗；桌面和手机使用同一份登录会话。菜单或按钮没显示，也不能绕过接口。</p><ul><li v-for="permission in selectedRole?.permissions ?? []" :key="permission.code">{{ permission.label }}</li></ul></details>
            </template>
            <template v-if="editorMode !== 'edit'">
              <p>密码至少 8 位，包含字母和数字。用户下次登录需自行修改密码。</p>
              <div class="user-form-grid password-fields"><label>{{ editorMode === 'create' ? '初始密码' : '新密码' }}<input v-model="editor.password" aria-label="用户密码" type="password" autocomplete="new-password" required /></label><label>确认密码<input v-model="editor.confirmPassword" aria-label="确认用户密码" type="password" autocomplete="new-password" required /></label></div>
            </template>
            <button class="primary-button" type="submit">{{ busy ? '保存中…' : editorMode === 'password' ? '确认重置密码' : '保存用户' }}</button>
          </fieldset>
        </form>
      </section>
    </template>
  </div>
</template>

<style scoped>
.user-management-workspace { display: grid; gap: 18px; }
.user-actions, .user-checks, .user-filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.user-filters { margin: 16px 0; }
.user-filters label, .user-form-grid > label { display: grid; gap: 6px; min-width: 0; align-content: start; }
.user-form-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; align-items: start; margin-bottom: 16px; }
.password-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); max-width: 720px; }
.user-form-grid > label input,
.user-form-grid > label select { width: 100%; height: 36px; min-height: 36px; }
.cover-duty-panel, .customer-scope { margin-bottom: 16px; }
.cover-duty-panel > p, .customer-scope > p { margin: 0 0 8px; color: var(--muted); font-size: 13px; line-height: 1.5; }
.customer-scope { display: grid; gap: 8px; }
.customer-scope > span { color: var(--muted); font-size: 12px; }
.user-checks label { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
.customer-scope label { display: flex; align-items: center; gap: 6px; }
.user-checks input[type="checkbox"],
.customer-scope input[type="checkbox"] { width: 16px; height: 16px; min-height: 16px; }
input:not([type="checkbox"]), select, textarea { width: 100%; box-sizing: border-box; min-width: 0; }
.user-table-wrap { overflow-x: auto; }
.user-cards { display: none; }
.user-card { display: grid; gap: 6px; border: 1px solid var(--line); border-radius: 12px; padding: 12px; }
.user-card header { display: flex; justify-content: space-between; gap: 8px; }
.user-card p { margin: 0; }
table { min-width: 800px; }
@media (max-width: 900px) {
  .user-table-wrap { display: none; }
  .user-cards { display: grid; gap: 10px; }
  table { min-width: 0; }
  .user-form-grid { grid-template-columns: 1fr 1fr; }
}
fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
.user-checks, .user-permissions { margin-bottom: 16px; }
.user-permissions ul { display: grid; gap: 6px; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }
.user-editor { scroll-margin-top: 20px; }
@media (max-width: 640px) { .section-heading { flex-wrap: wrap; gap: 12px; } .user-filters label { flex: 1; } .user-form-grid, .password-fields { grid-template-columns: 1fr; max-width: none; } }
</style>
