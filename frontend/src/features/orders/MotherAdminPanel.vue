<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { fetchCustomers, type CustomerProfile } from '../../services/masterDataApi';
import { correctMotherBasicInfo, recycleMaintainedOrder, searchMaintainedOrders, type MaintainedOrder } from '../../services/orderMaintenanceApi';

const props = defineProps<{ motherCode: string }>();
const emit = defineEmits<{ saved: []; deleted: []; close: [] }>();
const order = ref<MaintainedOrder | null>(null);
const customers = ref<CustomerProfile[]>([]);
const reason = ref('');
const error = ref('');
const message = ref('');
const busy = ref(false);
const loading = ref(false);
const confirmDelete = ref(false);
const requests = new Map<string, string>();
const selectedCustomer = ref<number | null>(null);
let generation = 0;
let disposed = false;
const customerChanged = computed(() => selectedCustomer.value !== (order.value?.customerId ?? null));

function requestFor(payload: unknown) {
  const fingerprint = JSON.stringify(payload);
  let id = requests.get(fingerprint);
  if (!id) {
    id = `mother-admin-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;
    requests.set(fingerprint, id);
  }
  return id;
}

async function load() {
  const token = ++generation;
  order.value = null; error.value = ''; message.value = ''; reason.value = ''; confirmDelete.value = false; loading.value = true;
  try {
    const [rows, profiles] = await Promise.all([searchMaintainedOrders(props.motherCode), fetchCustomers()]);
    if (disposed || token !== generation) return;
    order.value = rows.find(row => row.targetType === 'MOTHER' && row.code === props.motherCode) ?? null;
    if (!order.value) throw new Error('没有找到母单');
    selectedCustomer.value = order.value.customerId ?? null;
    customers.value = profiles.filter(profile => profile.enabled);
  } catch (caught) {
    if (!disposed && token === generation) error.value = caught instanceof Error ? caught.message : '母单加载失败';
  } finally { if (!disposed && token === generation) loading.value = false; }
}

async function save() {
  if (!order.value || busy.value) return;
  if (!reason.value.trim()) { error.value = '请填写修改原因'; return; }
  const current = order.value;
  const code = props.motherCode;
  const token = generation;
  const payload = { customerId: selectedCustomer.value, goodsName: current.goodsName, remark: current.remark ?? '',
    reason: reason.value.trim(), expectedRevision: current.revision };
  busy.value = true; error.value = ''; message.value = '';
  try {
    const saved = await correctMotherBasicInfo(code, { ...payload, requestId: requestFor(['save', code, payload]) });
    if (disposed || token !== generation) return;
    order.value = saved; selectedCustomer.value = saved.customerId ?? null;
    message.value = '母单基础信息已保存；需要更新纸面信息时请重新生成标签。';
    emit('saved');
  } catch (caught) {
    if (!disposed && token === generation) error.value = caught instanceof Error ? caught.message : '保存失败，请保留原内容重试';
  } finally { if (!disposed && token === generation) busy.value = false; }
}

async function remove() {
  if (!order.value || busy.value || !confirmDelete.value) return;
  if (!reason.value.trim()) { error.value = '请填写删除原因'; return; }
  const code = props.motherCode;
  const token = generation;
  const detail = reason.value.trim();
  busy.value = true; error.value = '';
  try {
    await recycleMaintainedOrder('MOTHER', code, detail, requestFor(['delete', code, detail]));
    if (!disposed && token === generation) emit('deleted');
  } catch (caught) {
    if (!disposed && token === generation) error.value = caught instanceof Error ? caught.message : '删除失败，请保留原请求重试';
  } finally { if (!disposed && token === generation) busy.value = false; }
}
watch(() => props.motherCode, () => { busy.value = false; void load(); }, { immediate: true });
onBeforeUnmount(() => { disposed = true; ++generation; });
</script>

<template>
  <section class="mother-admin-panel" aria-label="管理员母单维护">
    <h3>母单基础信息 · {{ motherCode }}</h3>
    <p v-if="loading" role="status">正在读取母单…</p>
    <p v-if="error" role="alert" class="operation-feedback danger">{{ error }}<button v-if="!order" type="button" @click="load">重试</button></p>
    <p v-if="message" role="status">{{ message }}</p>
    <form v-if="order" @submit.prevent="save">
      <label>客户<select v-model="selectedCustomer" aria-label="母单客户" :disabled="busy || order.recycled">
        <option :value="order.customerId ?? null">{{ order.customerName || '历史未记录' }}（当前）</option>
        <option v-for="customer in customers.filter(item => item.customerId !== order?.customerId)" :key="customer.customerId" :value="customer.customerId">{{ customer.name }} · {{ customer.customerCode }}</option>
      </select></label>
      <p v-if="customerChanged">更换客户将同步更正收货库存归属；已有拆单、交工、质检、改单或财务记录的母单需要办理业务更正。</p>
      <label>货品<input v-model="order.goodsName" aria-label="母单货品" maxlength="120" required :disabled="busy || order.recycled" /></label>
      <label>备注<textarea v-model="order.remark" aria-label="母单备注" maxlength="300" :disabled="busy || order.recycled" /></label>
      <label>操作原因<input v-model="reason" aria-label="母单操作原因" maxlength="200" required :disabled="busy" /></label>
      <div class="mother-admin-actions">
        <button type="submit" class="primary-button" :disabled="busy || order.recycled">保存基础信息</button>
        <button type="button" class="secondary-button" :disabled="busy || order.recycled" @click="confirmDelete = !confirmDelete">删除母单</button>
        <button type="button" class="secondary-button" :disabled="busy" @click="emit('close')">关闭维护</button>
      </div>
      <div v-if="confirmDelete" role="alert">
        <p>确认删除母单 {{ motherCode }} 及其子单？删除后进入回收站，可由管理员恢复；库存和账单保留。</p>
        <button type="button" class="secondary-button" :disabled="busy || !reason.trim()" @click="remove">确认删除至回收站</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.mother-admin-panel { border: 1px solid var(--line); border-radius: 10px; padding: 14px; min-width: 0; }
form, label { display: grid; gap: 8px; }
form { gap: 12px; }
input, select, textarea { box-sizing: border-box; width: 100%; min-width: 0; min-height: 40px; border: 1px solid var(--line); border-radius: 6px; padding: 8px; }
.mother-admin-actions { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
