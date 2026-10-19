<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  correctMaintainedOrder,
  fetchRecycleBin,
  recycleMaintainedOrder,
  restoreMaintainedOrder,
  searchMaintainedOrders,
  type MaintainedOrder,
} from '../../services/orderMaintenanceApi';

const code = ref('');
const reason = ref('');
const rows = ref<MaintainedOrder[]>([]);
const bin = ref<MaintainedOrder[]>([]);
const message = ref('');
const error = ref('');
const busy = ref(false);
const correctionRequests = new Map<string, string>();

const typeLabel: Record<MaintainedOrder['targetType'], string> = {
  MOTHER: '母单',
  WORK_ORDER: '子单',
  PRINT_JOB: '打印任务',
  PRINT_TARGET: '打印任务',
};

onMounted(() => {
  void loadBin();
});

async function loadBin() {
  try {
    bin.value = await fetchRecycleBin();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '回收站读取失败';
  }
}

async function run(action: () => Promise<void>, success: string) {
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    await action();
    if (success) message.value = success;
    await loadBin();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '操作失败';
  } finally {
    busy.value = false;
  }
}

function search() {
  return run(async () => {
    rows.value = await searchMaintainedOrders(code.value.trim());
    message.value = `找到 ${rows.value.length} 条`;
  }, '');
}

function save(order: MaintainedOrder) {
  return run(async () => {
    const fingerprint = JSON.stringify([
      order.targetType, order.code, order.revision, order.customerName, order.materialCode,
      order.goodsName, order.quantity, order.weight, order.requirement, order.dueDate,
      order.processes, order.copies, order.stationId, order.queueId, reason.value.trim(),
    ]);
    let requestId = correctionRequests.get(fingerprint);
    if (!requestId) {
      const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
      requestId = `maint-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
      correctionRequests.set(fingerprint, requestId);
    }
    await correctMaintainedOrder(order, reason.value.trim(), requestId);
    rows.value = await searchMaintainedOrders(order.code);
  }, '已保存修改');
}

function recycle(order: MaintainedOrder) {
  return run(async () => {
    await recycleMaintainedOrder(order.targetType, order.code, reason.value.trim());
    if (code.value.trim()) {
      rows.value = await searchMaintainedOrders(code.value.trim());
    }
  }, '已放入回收站');
}

function restore(order: MaintainedOrder) {
  return run(async () => {
    await restoreMaintainedOrder(order.targetType, order.code);
  }, '已从回收站恢复');
}

function isOrder(order: MaintainedOrder) {
  return order.targetType === 'MOTHER' || order.targetType === 'WORK_ORDER';
}
</script>

<template>
  <section class="order-maintenance" aria-label="单据维护">
    <h2>单据维护</h2>
    <p>管理员可以修改母单、子单和打印任务，已经开工的也能改。放入回收站后，其他人在列表、扫码和打印领取里看不到。已入账的库存和账单不会自动改写。</p>
    <label>单号<input v-model="code" aria-label="维护单号" /></label>
    <label>原因<input v-model="reason" aria-label="维护原因" placeholder="修改或删除时必填" /></label>
    <button class="secondary-button" type="button" :disabled="busy" @click="search">查找</button>
    <p v-if="message">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <article v-for="order in rows" :key="`${order.targetType}-${order.code}`" :aria-label="`${typeLabel[order.targetType]} ${order.code}`">
      <h3>{{ typeLabel[order.targetType] }} {{ order.code }}<span v-if="order.recycled">（回收站）</span></h3>
      <p v-if="order.motherOrderCode && order.motherOrderCode !== order.code">所属 {{ order.motherOrderCode }}</p>
      <template v-if="isOrder(order)">
        <label>客户<input v-model="order.customerName" :aria-label="`${order.code} 客户`" /></label>
        <label>材质<input v-model="order.materialCode" :aria-label="`${order.code} 材质`" /></label>
        <label>货品<input v-model="order.goodsName" :aria-label="`${order.code} 货品`" /></label>
        <label>数量<input v-model="order.quantity" :aria-label="`${order.code} 数量`" /></label>
        <label>重量<input v-model="order.weight" :aria-label="`${order.code} 重量`" /></label>
        <label>要求<input v-model="order.requirement" :aria-label="`${order.code} 要求`" /></label>
        <label>期限<input v-model="order.dueDate" :aria-label="`${order.code} 期限`" /></label>
        <label v-if="order.targetType === 'WORK_ORDER'">工序<input v-model="order.processes" :aria-label="`${order.code} 工序`" /></label>
      </template>
      <template v-else>
        <label>份数<input v-model.number="order.copies" :aria-label="`${order.code} 份数`" /></label>
        <label>工位<input v-model="order.stationId" :aria-label="`${order.code} 工位`" /></label>
        <label v-if="order.targetType === 'PRINT_TARGET'">队列<input v-model="order.queueId" :aria-label="`${order.code} 队列`" /></label>
      </template>
      <button class="secondary-button" type="button" :disabled="busy" @click="save(order)">保存修改</button>
      <button v-if="!order.recycled" class="secondary-button" type="button" :disabled="busy" @click="recycle(order)">放入回收站</button>
      <ul v-if="order.adjustments.length">
        <li v-for="line in order.adjustments" :key="line">{{ line }}</li>
      </ul>
    </article>
    <h3>回收站</h3>
    <p v-if="bin.length === 0">回收站是空的。</p>
    <article v-for="order in bin" :key="`bin-${order.targetType}-${order.code}`">
      <span>{{ typeLabel[order.targetType] }} {{ order.code }}</span>
      <span v-if="order.recycleReason"> {{ order.recycleReason }}</span>
      <button class="secondary-button" type="button" :disabled="busy" @click="restore(order)">恢复</button>
    </article>
  </section>
</template>
