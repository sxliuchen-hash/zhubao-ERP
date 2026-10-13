<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  fetchProcessProgress,
  openProcessProgress,
  submitProcessProgress,
  type ProcessProgressRow,
  type ProcessProgressTarget,
} from '../../services/processProgressApi';

const percents = [0, 30, 60, 90, 100];
const query = ref('');
const rows = ref<ProcessProgressRow[]>([]);
const code = ref('');
const target = ref<ProcessProgressTarget | null>(null);
const percent = ref(0);
const note = ref('');
const reason = ref('');
const message = ref('');
const error = ref('');
const requestIds = ref<Record<string, string>>({});

onMounted(() => {
  void reload();
});

async function reload() {
  error.value = '';
  try {
    rows.value = await fetchProcessProgress(query.value);
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '进度加载失败';
  }
}

async function locate() {
  error.value = '';
  message.value = '';
  target.value = null;
  if (!code.value.trim()) {
    error.value = '请先扫码或输入单号';
    return;
  }
  try {
    target.value = await openProcessProgress(code.value.trim());
    if (!target.value.selectChild) {
      percent.value = rows.value.find((row) => row.orderCode === target.value?.orderCode)?.percent ?? 0;
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '无法定位单据';
  }
}

function chooseChild(childCode: string) {
  code.value = childCode;
  void locate();
}

async function submit() {
  error.value = '';
  message.value = '';
  if (!target.value || target.value.selectChild || target.value.formallyDelivered) return;
  const key = `${target.value.orderCode}|${target.value.currentProcess}|${percent.value}|${note.value}|${reason.value}`;
  const requestId = ensureRequestId(key);
  try {
    const saved = await submitProcessProgress({
      code: target.value.orderCode,
      percent: percent.value,
      expectedVersion: rows.value.find((row) => row.orderCode === target.value?.orderCode)?.version ?? 0,
      note: note.value,
      reason: reason.value,
      requestId,
    });
    delete requestIds.value[key];
    message.value = saved.percent === 100
      ? '已记下 100%。这只是工序进度，仍需在岗位报工里正式交付。'
      : `已记下 ${saved.percent}%`;
    await reload();
    await locate();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '进度提交失败';
  }
}

function ensureRequestId(key: string) {
  const current = requestIds.value[key];
  if (current) return current;
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}`;
  const created = `progress-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
  requestIds.value = { ...requestIds.value, [key]: created };
  return created;
}
</script>

<template>
  <section class="section-block" aria-label="工序进度报工">
    <h2>工序进度</h2>
    <p>只记录当前工序的 0%、30%、60%、90%、100%。100% 不会自动交付、质检或写库存。</p>
    <label>查找<input v-model="query" aria-label="进度查找" placeholder="单号、客户、工序或进度" @change="reload" /></label>
    <label>扫码或单号<input v-model="code" aria-label="进度单号" @keyup.enter="locate" /></label>
    <button class="secondary-button" type="button" @click="locate">定位</button>
    <p v-if="error" class="operation-feedback danger">{{ error }}</p>
    <p v-if="message" class="operation-feedback">{{ message }}</p>
    <div v-if="target?.selectChild">
      <p>这张母单已拆分，请选择具体子单。</p>
      <button v-for="child in target.children" :key="child.code" class="secondary-button" type="button" @click="chooseChild(child.code)">
        {{ child.code }}
      </button>
    </div>
    <form v-else-if="target" @submit.prevent="submit">
      <p>{{ target.customerName }} · {{ target.currentProcess }} · {{ target.processingRequirement || '未记录要求' }}</p>
      <p v-if="target.formallyDelivered">这道工序已经正式交付，不能再用进度代替。</p>
      <template v-else>
        <label>进度
          <select v-model.number="percent" aria-label="工序进度">
            <option v-for="item in percents" :key="item" :value="item">{{ item }}%</option>
          </select>
        </label>
        <label>说明<input v-model="note" aria-label="进度说明" /></label>
        <label>回退原因<input v-model="reason" aria-label="进度回退原因" placeholder="只有往回调时必填" /></label>
        <button class="primary-button" type="submit">提交进度</button>
      </template>
    </form>
    <div class="desk-cards">
      <article v-for="row in rows" :key="row.orderCode + row.currentProcess" class="user-card">
        <strong>{{ row.orderCode }}</strong>
        <p>母单 {{ row.motherOrderCode }} · {{ row.customerName }}</p>
        <p>{{ row.currentProcess }} {{ row.percent }}% · {{ row.businessStatus }}</p>
      </article>
    </div>
  </section>
</template>
