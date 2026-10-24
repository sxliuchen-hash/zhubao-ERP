<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { changeOwnPassword, fetchAuthSession, type AuthSession } from '../../services/authApi';
import {
  fetchStoredContent,
  retireStoredContent,
  type StoredContentInventory,
} from '../../services/documentManagementApi';

const props = withDefaults(defineProps<{ initialSection?: 'account' | 'storage'; accountOnly?: boolean }>(), { initialSection: 'account', accountOnly: false });
const activeSection = ref(props.initialSection);

const authSession = ref<AuthSession | null>(null);
const passwordCurrent = ref('');
const passwordNew = ref('');
const passwordConfirm = ref('');
const passwordBusy = ref(false);
const passwordMessage = ref('');
const storedContent = ref<StoredContentInventory | null>(null);
const contentError = ref('');
const contentMessage = ref('');
const contentBusyCode = ref('');
const canManageStorage = computed(() => !props.accountOnly && authSession.value?.role === 'ADMIN'
  && (!authSession.value.effectivePermissions || authSession.value.effectivePermissions.includes('DOCUMENT_CONTENT_MANAGE')));
let contentGeneration = 0;
let disposed = false;

onMounted(async () => {
  await restoreAuthSession();
  if (activeSection.value === 'storage') {
    if (canManageStorage.value) await loadStoredContent();
    else activeSection.value = 'account';
  }
});
watch(activeSection, section => { if (section === 'storage' && canManageStorage.value) void loadStoredContent(); });
watch(canManageStorage, allowed => {
  if (allowed) return;
  contentGeneration += 1;
  storedContent.value = null;
  contentError.value = '';
  activeSection.value = 'account';
});
onBeforeUnmount(() => { disposed = true; contentGeneration += 1; });

async function restoreAuthSession() {
  try {
    authSession.value = await fetchAuthSession();
  } catch {
    authSession.value = null;
  }
}

async function loadStoredContent() {
  if (!canManageStorage.value) return;
  const token = ++contentGeneration;
  contentError.value = '';
  try {
    const result = await fetchStoredContent();
    if (!disposed && token === contentGeneration) storedContent.value = result;
  } catch (error) {
    if (disposed || token !== contentGeneration) return;
    storedContent.value = null;
    contentError.value = error instanceof Error ? error.message : '存储内容加载失败';
  }
}

async function retireContent(code: string, fileName: string) {
  if (!canManageStorage.value) return;
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
  } catch (error) {
    passwordMessage.value = error instanceof Error ? error.message : '自助改密失败';
  } finally {
    passwordBusy.value = false;
  }
}
</script>

<template>
  <div class="page-stack">
    <nav v-if="!accountOnly" class="configuration-sections" aria-label="配置事项">
      <button type="button" class="secondary-button" :aria-pressed="activeSection === 'account'" @click="activeSection = 'account'">个人安全</button>
      <button v-if="canManageStorage" type="button" class="secondary-button" :aria-pressed="activeSection === 'storage'" @click="activeSection = 'storage'">存储管理</button>
    </nav>

    <section v-show="activeSection === 'account'" class="security-context-panel" aria-label="个人登录与密码">
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

    </section>

    <section v-if="activeSection === 'storage' && canManageStorage" class="security-context-panel" aria-label="存储内容管理">
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
        <p v-if="contentMessage" class="operation-feedback">{{ contentMessage }}</p>
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
.auth-password-strip { display: grid; gap: 10px; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); }
form label { display: grid; gap: 6px; min-width: 0; }
form input { box-sizing: border-box; width: 100%; min-width: 0; }
.configuration-sections { display: flex; gap: 10px; flex-wrap: wrap; }
.configuration-sections button[aria-pressed="true"] { background: var(--action-soft); color: var(--action); }
@media (max-width: 900px) {
  .auth-password-strip { grid-template-columns: 1fr 1fr; }
  .auth-password-strip > div,
  .auth-password-strip > button { grid-column: 1 / -1; }
  .configuration-sections button, .auth-password-strip button { min-height: 44px; }
  .auth-password-strip input { font-size: 16px; min-height: 44px; }
}
</style>
