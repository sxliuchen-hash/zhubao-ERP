<script setup lang="ts">
import { computed, ref } from 'vue';
import { downloadEvidence, loadEvidence, loadOwnedStock, loginCustomer } from './customerPortalApi';
import {
  bucketLabel,
  bucketMeaning,
  filterLines,
  weightText,
  type CustomerEvidenceDetail,
  type CustomerOwnedStock,
  type CustomerStockLine,
} from './customerStock';

const username = ref('');
const password = ref('');
const pending = ref('');
const errorText = ref('');
const loggedIn = ref(false);
const stock = ref<CustomerOwnedStock | null>(null);
const evidence = ref<CustomerEvidenceDetail | null>(null);
const material = ref('');
const selected = ref<CustomerStockLine | null>(null);
const readAt = ref('');
const downloadName = ref('');

const visibleLines = computed(() => filterLines(stock.value?.lines ?? [], material.value));

async function submitLogin() {
  pending.value = '';
  errorText.value = '';
  try {
    await loginCustomer(username.value, password.value);
    loggedIn.value = true;
    await refresh();
  } catch (error) {
    const message = error instanceof Error ? error.message : '登录失败';
    if (message.includes('尚未核定')) {
      pending.value = '凭据交付方式尚未核定，不能进入库存';
      return;
    }
    errorText.value = message;
  }
}

async function refresh() {
  errorText.value = '';
  try {
    stock.value = await loadOwnedStock();
    evidence.value = await loadEvidence();
    readAt.value = new Date().toLocaleString('zh-CN', { hour12: false });
  } catch (error) {
    stock.value = null;
    errorText.value = error instanceof Error ? error.message : '库存读取失败';
  }
}

function selectLine(line: CustomerStockLine) {
  selected.value = line;
  downloadName.value = '';
}

async function downloadGranted() {
  const item = evidence.value?.items[0];
  if (!item) return;
  const saved = await downloadEvidence(item);
  downloadName.value = saved.fileName;
}
</script>

<template>
  <main class="customer-portal">
    <h1>客户库存</h1>
    <p class="note">按需刷新，不是实时推送。</p>
    <form v-if="!loggedIn" @submit.prevent="submitLogin">
      <label>账号<input v-model="username" autocomplete="username" /></label>
      <label>密码<input v-model="password" type="password" autocomplete="current-password" /></label>
      <button type="submit">登录</button>
      <p v-if="pending" class="pending">{{ pending }}</p>
      <p v-if="errorText" class="error">{{ errorText }}</p>
    </form>
    <section v-else>
      <p v-if="readAt">本次读取时间 {{ readAt }}</p>
      <button type="button" @click="refresh">刷新</button>
      <p v-if="errorText" class="error">{{ errorText }}</p>
      <template v-else-if="stock">
        <label>筛选<input v-model="material" placeholder="材质或单位" /></label>
        <p v-if="visibleLines.length === 0">暂无自有库存</p>
        <ul v-else>
          <li v-for="line in visibleLines" :key="`${line.materialCode}-${line.unit}-${line.bucket}`">
            <button type="button" @click="selectLine(line)">
              {{ line.materialCode }} {{ line.unit }} {{ bucketLabel(line.bucket) }} {{ line.quantity }}
              {{ weightText(line) }}
            </button>
          </li>
        </ul>
        <article v-if="selected">
          <h2>{{ selected.materialCode }} {{ bucketLabel(selected.bucket) }}</h2>
          <p>{{ bucketMeaning(selected.bucket) }}</p>
          <p>数量 {{ selected.quantity }} {{ selected.unit }}</p>
          <p>重量 {{ weightText(selected) }}</p>
          <p v-if="selected.reconciliationPending">在外数量超出账内加工量，差额待核</p>
        </article>
        <article>
          <h2>照片和文件</h2>
          <p v-if="!evidence || evidence.visibility === 'UNDECIDED' && evidence.items.length === 0">
            照片可见范围尚未核定
          </p>
          <template v-else>
            <p v-for="item in evidence.items" :key="item.grantId">{{ item.fileName }}</p>
            <button type="button" @click="downloadGranted">下载获准文件</button>
            <p v-if="downloadName">已下载 {{ downloadName }}</p>
          </template>
        </article>
        <article v-for="source in stock.sources" :key="source.kind">
          <p v-if="source.state === 'NOT_IMPLEMENTED'">{{ source.note }}</p>
        </article>
      </template>
    </section>
  </main>
</template>
