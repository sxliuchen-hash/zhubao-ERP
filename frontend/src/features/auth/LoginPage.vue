<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  changeOwnPassword,
  fetchAuthDemoUsers,
  loginAuthSession,
  type AuthSession,
} from '../../services/authApi';

const props = defineProps<{
  session: AuthSession | null;
}>();

const demoUsers = ref<string[]>([]);
const username = ref('');
const password = ref('');
const busy = ref(false);
const message = ref('');
const currentPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');

const mustChangePassword = computed(() => props.session?.passwordChangeRequired === true);

onMounted(async () => {
  try {
    demoUsers.value = await fetchAuthDemoUsers();
  } catch {
    demoUsers.value = [];
  }
});

function syncDemoPassword() {
  if (demoUsers.value.includes(username.value)) {
    password.value = `${username.value}123`;
  }
}

async function handleLogin() {
  busy.value = true;
  message.value = '';
  try {
    const session = await loginAuthSession({
      username: username.value.trim(),
      password: password.value,
    });
    password.value = '';
    message.value = session.passwordChangeRequired
      ? '临时密码已登录，请先设置新密码'
      : `已登录：${session.operator}`;
  } catch (error) {
    message.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    busy.value = false;
  }
}

async function handleChangePassword() {
  busy.value = true;
  message.value = '';
  try {
    if (newPassword.value !== confirmPassword.value) {
      message.value = '两次新密码不一致';
      return;
    }
    await changeOwnPassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    });
    currentPassword.value = '';
    newPassword.value = '';
    confirmPassword.value = '';
  } catch (error) {
    message.value = error instanceof Error ? error.message : '修改密码失败';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <form v-if="mustChangePassword" class="login-card" aria-label="首次修改密码" @submit.prevent="handleChangePassword">
      <div class="login-brand">
        <span class="brand-mark">ZB</span>
        <div>
          <strong>来料加工</strong>
          <span>请先更换临时密码</span>
        </div>
      </div>
      <p class="login-lead">{{ session?.operator }} 的临时密码只能再用来设置新密码。新密码至少 12 位，并且同时包含字母和数字。</p>
      <label>
        <span>当前密码</span>
        <input v-model="currentPassword" type="password" autocomplete="current-password" :disabled="busy" />
      </label>
      <label>
        <span>新密码</span>
        <input v-model="newPassword" type="password" autocomplete="new-password" :disabled="busy" />
      </label>
      <label>
        <span>确认新密码</span>
        <input v-model="confirmPassword" type="password" autocomplete="new-password" :disabled="busy" />
      </label>
      <button class="primary-button" type="submit" :disabled="busy">{{ busy ? '提交中' : '更新密码并进入' }}</button>
      <p v-if="message" class="login-message">{{ message }}</p>
    </form>

    <form v-else class="login-card" aria-label="登录" @submit.prevent="handleLogin">
      <div class="login-brand">
        <span class="brand-mark">ZB</span>
        <div>
          <strong>来料加工</strong>
          <span>收货 · 生产 · 质检 · 库存</span>
        </div>
      </div>
      <h1>登录</h1>
      <p class="login-lead">使用管理员分配的账号进入作业台。演示环境可选用下面的账号，密码是账号名加 123。</p>
      <label>
        <span>账号</span>
        <input
          v-model="username"
          list="login-demo-users"
          autocomplete="username"
          :disabled="busy"
          @change="syncDemoPassword"
        />
        <datalist id="login-demo-users">
          <option v-for="name in demoUsers" :key="name" :value="name" />
        </datalist>
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" :disabled="busy" />
      </label>
      <button class="primary-button" type="submit" :disabled="busy">{{ busy ? '登录中' : '登录' }}</button>
      <p v-if="message" class="login-message">{{ message }}</p>
    </form>
  </main>
</template>
