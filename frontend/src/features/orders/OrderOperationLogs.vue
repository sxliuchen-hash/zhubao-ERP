<script setup lang="ts">
import { inject, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { fetchOrderOperationLogs, type OrderOperationLog } from '../../services/orderMaintenanceApi';
const props = defineProps<{ code: string }>();
const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const rows = ref<OrderOperationLog[]>([]);
const error = ref('');
const loading = ref(false);
let generation = 0;
let disposed = false;
async function load() {
  const token = ++generation;
  rows.value = []; error.value = ''; loading.value = false;
  if (session.value?.role !== 'ADMIN') return;
  loading.value = true;
  try {
    const data = await fetchOrderOperationLogs(props.code);
    if (!disposed && token === generation) rows.value = data;
  } catch (caught) {
    if (!disposed && token === generation) error.value = caught instanceof Error ? caught.message : '操作日志加载失败';
  } finally { if (!disposed && token === generation) loading.value = false; }
}
watch([() => props.code, session], () => { void load(); }, { immediate: true, deep: true });
onBeforeUnmount(() => { disposed = true; ++generation; });
</script>

<template>
  <section v-if="session?.role === 'ADMIN'" aria-label="订单操作日志">
    <h4>操作日志（仅管理员）</h4>
    <p>按母单汇总关联子单和执行轮次，保留原单号。历史未记录的操作人或时间显示为未记录。</p>
    <p v-if="loading" role="status">正在读取操作日志…</p>
    <p v-else-if="error" role="alert">{{ error }} <button type="button" @click="load">重试日志</button></p>
    <p v-else-if="!rows.length">暂无操作日志</p>
    <ol v-else>
      <li v-for="(entry, index) in rows" :key="index">
        <p><strong>{{ entry.action }}</strong> · {{ entry.targetCode }} · {{ entry.operator || '操作人未记录' }} · {{ entry.createdAt || '时间未记录' }}</p>
        <p v-if="entry.detail">{{ entry.detail }}</p>
      </li>
    </ol>
  </section>
</template>

<style scoped>
ol { padding-left: 22px; }
li { border-bottom: 1px solid var(--line); padding: 8px 0; }
p { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
