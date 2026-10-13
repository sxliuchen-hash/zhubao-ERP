<script setup lang="ts">
import { onMounted, ref } from 'vue';
import ScanInput from '../scan/ScanInput.vue';
import { apiGet, apiPost } from '../../services/apiClient';
import {
  fetchProcessProgress,
  fetchProcessProgressHistory,
  openProcessProgress,
  submitProcessProgress,
  type ProcessProgressRow,
  type ProcessProgressTarget,
} from '../../services/processProgressApi';

const percents = [0, 30, 60, 90, 100];
const query = ref('');
const processFilter = ref('');
const dueBefore = ref('');
const page = ref(1);
const rows = ref<ProcessProgressRow[]>([]);
const history = ref<{ newPercent: number; operator: string; createdAt: string; reason: string }[]>([]);
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
    rows.value = await fetchProcessProgress(query.value, processFilter.value, dueBefore.value, page.value, 20);
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

async function loadHistory() {
  if (!target.value || target.value.selectChild) return;
  history.value = await fetchProcessProgressHistory(target.value.orderCode, target.value.currentProcess);
}

const changeRequirement = ref('');
const changeDue = ref('');
const changeId = ref('');
const timeline = ref<string[]>([]);

async function draftChange() {
  error.value = '';
  const created = await apiPost<{ motherOrderCode: string; workOrderCode: string; newRequirement: string; newDueDate: string; requestId: string }, { id: string }>(
    '/api/business/order-changes',
    {
      motherOrderCode: target.value?.motherOrderCode || code.value,
      workOrderCode: target.value?.orderCode === target.value?.motherOrderCode ? '' : target.value?.orderCode || '',
      newRequirement: changeRequirement.value,
      newDueDate: changeDue.value,
      requestId: `change-${Date.now()}`,
    },
  );
  changeId.value = created.id;
  message.value = `已保存改单 ${created.id}，送财务确认后才会生效`;
}

async function submitChange() {
  await apiPost<Record<string, never>, unknown>(`/api/business/order-changes/${changeId.value}/finance-submissions`, {});
  message.value = '已送财务确认';
}

async function loadTimeline() {
  const mother = target.value?.motherOrderCode || code.value;
  timeline.value = await apiGet<string[]>(`/api/business/order-changes/timeline?motherOrderCode=${encodeURIComponent(mother)}`);
}
</script>

<template>
  <section class="section-block" aria-label="工序进度报工">
    <h2>工序进度</h2>
    <p>只记录当前工序的 0%、30%、60%、90%、100%。100% 不会自动交付、质检或写库存。</p>
    <label>查找<input v-model="query" aria-label="进度查找" placeholder="单号、客户、工序或进度" @change="page = 1; reload()" /></label>
    <label>工序<input v-model="processFilter" aria-label="工序筛选" placeholder="雕刻、镶嵌或抛光" @change="page = 1; reload()" /></label>
    <label>期限到<input v-model="dueBefore" aria-label="期限筛选" placeholder="2026-12-31" @change="page = 1; reload()" /></label>
    <ScanInput v-model="code" field-text="进度单号" input-aria-label="进度单号" submit-label="定位" @draft="locate" />
    <button class="secondary-button" type="button" @click="page -= 1; reload()" :disabled="page <= 1">上一页</button>
    <button class="secondary-button" type="button" @click="page += 1; reload()">下一页</button>
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
        <button class="secondary-button" type="button" @click="loadHistory">查看进度历史</button>
        <p v-for="(item, index) in history" :key="index">{{ item.createdAt }} {{ item.operator }} {{ item.newPercent }}% {{ item.reason }}</p>
      </template>
      <h3>改单</h3>
      <label>新要求<input v-model="changeRequirement" aria-label="改单新要求" /></label>
      <label>新期限<input v-model="changeDue" aria-label="改单新期限" /></label>
      <button class="secondary-button" type="button" @click="draftChange">保存改单申请</button>
      <button class="secondary-button" type="button" :disabled="!changeId" @click="submitChange">送财务确认</button>
      <button class="secondary-button" type="button" @click="loadTimeline">查看时间线</button>
      <p v-for="(line, index) in timeline" :key="'t' + index">{{ line }}</p>
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
