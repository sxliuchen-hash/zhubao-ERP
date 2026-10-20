<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  correctMaintainedOrder,
  fetchRecycleBin,
  listMaintainedMothers,
  recycleMaintainedOrder,
  restoreMaintainedOrder,
  searchMaintainedOrders,
  type MaintainedMotherSummary,
  type MaintainedOrder,
} from '../../services/orderMaintenanceApi';

const code = ref('');
const reason = ref('');
const fromDate = ref('');
const toDate = ref('');
const customer = ref('');
const operatorName = ref('');
const rows = ref<MaintainedOrder[]>([]);
const mothers = ref<MaintainedMotherSummary[]>([]);
const listNote = ref('');
const bin = ref<MaintainedOrder[]>([]);
const message = ref('');
const error = ref('');
const busy = ref(false);
const correctionRequests = new Map<string, string>();
const recycleRequests = new Map<string, string>();

function requestFor(cache: Map<string, string>, fingerprint: string): string {
  let requestId = cache.get(fingerprint);
  if (!requestId) {
    const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    requestId = `maint-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
    cache.set(fingerprint, requestId);
  }
  return requestId;
}

const typeLabel: Record<MaintainedOrder['targetType'], string> = {
  MOTHER: '母单',
  WORK_ORDER: '子单',
  PRINT_JOB: '打印任务',
  PRINT_TARGET: '打印任务',
};

onMounted(() => {
  void loadBin();
  void loadMothers();
});

async function loadBin() {
  try {
    bin.value = await fetchRecycleBin();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '回收站读取失败';
  }
}

async function loadMothers() {
  const page = await listMaintainedMothers({
    from: fromDate.value,
    to: toDate.value,
    customer: customer.value,
    operator: operatorName.value,
  });
  mothers.value = page.items;
  listNote.value = page.truncated
    ? `共 ${page.total} 张，这里显示最近 ${page.items.length} 张。缩小日期可以看全。`
    : `共 ${page.total} 张未回收母单`;
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

function applyFilter() {
  return run(loadMothers, '');
}

function search() {
  return run(async () => {
    rows.value = await searchMaintainedOrders(code.value.trim());
    message.value = `找到 ${rows.value.length} 条`;
  }, '');
}

function openMother(orderCode: string) {
  code.value = orderCode;
  return search();
}

function save(order: MaintainedOrder) {
  return run(async () => {
    const fingerprint = JSON.stringify([
      order.targetType, order.code, order.revision, order.customerName, order.materialCode,
      order.goodsName, order.quantity, order.weight, order.requirement, order.dueDate,
      order.processes, order.copies, order.stationId, order.queueId, reason.value.trim(),
    ]);
    const requestId = requestFor(correctionRequests, fingerprint);
    await correctMaintainedOrder(order, reason.value.trim(), requestId);
    rows.value = await searchMaintainedOrders(order.code);
  }, '已保存修改');
}

function recycle(order: MaintainedOrder) {
  return run(async () => {
    const reasonText = reason.value.trim();
    const fingerprint = JSON.stringify(['recycle', order.targetType, order.code, reasonText]);
    await recycleMaintainedOrder(order.targetType, order.code, reasonText, requestFor(recycleRequests, fingerprint));
    recycleRequests.delete(fingerprint);
    if (code.value.trim()) {
      rows.value = await searchMaintainedOrders(code.value.trim());
    }
    await loadMothers();
  }, '已放入回收站');
}

function restore(order: MaintainedOrder) {
  return run(async () => {
    const fingerprint = JSON.stringify(['restore', order.targetType, order.code, order.recycleReason]);
    await restoreMaintainedOrder(order.targetType, order.code, requestFor(recycleRequests, fingerprint));
    recycleRequests.delete(fingerprint);
    if (code.value.trim()) {
      rows.value = await searchMaintainedOrders(code.value.trim());
    }
    await loadMothers();
  }, '已从回收站恢复');
}

function isOrder(order: MaintainedOrder) {
  return order.targetType === 'MOTHER' || order.targetType === 'WORK_ORDER';
}
</script>

<template>
  <section class="order-maintenance" aria-label="单据维护">
    <header class="maintenance-heading">
      <h2>单据维护</h2>
      <p>先在列表里按日期、客户或建单人找到母单，再打开改正或放入回收站。回收后别人在列表、扫码和打印领取里看不到；库存和账单不会自动冲掉。已入账的客户、材质和货品不能在这里直接改。</p>
    </header>

    <div class="maintenance-filters">
      <label><span>开始日期</span><input v-model="fromDate" type="date" aria-label="开始日期" @keydown.enter.prevent="applyFilter" /></label>
      <label><span>结束日期</span><input v-model="toDate" type="date" aria-label="结束日期" @keydown.enter.prevent="applyFilter" /></label>
      <label><span>客户</span><input v-model="customer" aria-label="客户筛选" placeholder="客户名称" @keydown.enter.prevent="applyFilter" /></label>
      <label><span>建单人</span><input v-model="operatorName" aria-label="建单人筛选" placeholder="收货或建单账号" @keydown.enter.prevent="applyFilter" /></label>
      <button class="secondary-button" type="button" :disabled="busy" aria-label="筛选母单" @click="applyFilter">筛选</button>
    </div>
    <p v-if="listNote" class="maintenance-count">{{ listNote }}</p>
    <div class="module-table-wrap refined-table-wrap">
      <table class="module-table">
        <thead>
          <tr>
            <th>日期</th>
            <th>母单</th>
            <th>客户</th>
            <th>建单人</th>
            <th>货品</th>
            <th>数量 / 重量</th>
            <th>动作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="mothers.length === 0">
            <td colspan="7">没有符合条件的未回收母单。</td>
          </tr>
          <tr v-for="mother in mothers" :key="mother.code" :class="{ 'is-selected': code === mother.code }">
            <td>{{ mother.orderDate || '—' }}</td>
            <td class="code"><strong>{{ mother.code }}</strong></td>
            <td>{{ mother.customerName || '—' }}</td>
            <td>{{ mother.operator || '—' }}</td>
            <td>{{ mother.goodsName || mother.materialCode || '—' }}</td>
            <td>{{ mother.quantity || '—' }} / {{ mother.weight || '—' }}</td>
            <td><button class="secondary-button" type="button" :disabled="busy" :aria-label="`打开 ${mother.code}`" @click="openMother(mother.code)">打开</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="maintenance-lookup">
      <label><span>单号</span><input v-model="code" aria-label="维护单号" placeholder="母单、子单或打印任务" @keydown.enter.prevent="search" /></label>
      <label><span>原因</span><input v-model="reason" aria-label="维护原因" placeholder="修改或删除时必填" /></label>
      <button class="secondary-button" type="button" :disabled="busy" @click="search">查找</button>
    </div>
    <p v-if="message">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>

    <article
      v-for="order in rows"
      :key="`${order.targetType}-${order.code}`"
      class="maintenance-card"
      :class="{ 'is-child': order.targetType !== 'MOTHER' }"
      :aria-label="`${typeLabel[order.targetType]} ${order.code}`"
    >
      <header>
        <h3>{{ typeLabel[order.targetType] }} {{ order.code }}<span v-if="order.recycled">（回收站）</span></h3>
        <p v-if="order.motherOrderCode && order.motherOrderCode !== order.code">所属 {{ order.motherOrderCode }}</p>
      </header>
      <p v-if="isOrder(order) && order.postedFactsLocked" class="maintenance-note">已入账。客户、材质、货品和子单工序不能直接改。尚未拆单、交付或发货的母单可以改正收货数量和重量，系统会追加冲正流水。要求、期限和金额仍走进度页改单。</p>
      <div v-if="isOrder(order)" class="maintenance-fields">
        <label><span>客户</span><input v-model="order.customerName" :aria-label="`${order.code} 客户`" :disabled="order.postedFactsLocked" /></label>
        <label><span>材质</span><input v-model="order.materialCode" :aria-label="`${order.code} 材质`" :disabled="order.postedFactsLocked" /></label>
        <label><span>货品</span><input v-model="order.goodsName" :aria-label="`${order.code} 货品`" :disabled="order.postedFactsLocked" /></label>
        <label><span>数量</span><input v-model="order.quantity" :aria-label="`${order.code} 数量`" :disabled="order.postedFactsLocked && order.targetType !== 'MOTHER'" /></label>
        <label><span>重量</span><input v-model="order.weight" :aria-label="`${order.code} 重量`" :disabled="order.postedFactsLocked && order.targetType !== 'MOTHER'" /></label>
        <label><span>要求</span><input v-model="order.requirement" :aria-label="`${order.code} 要求`" /></label>
        <label><span>期限</span><input v-model="order.dueDate" :aria-label="`${order.code} 期限`" /></label>
        <label v-if="order.targetType === 'WORK_ORDER'"><span>工序</span><input v-model="order.processes" :aria-label="`${order.code} 工序`" :disabled="order.postedFactsLocked" /></label>
      </div>
      <div v-else class="maintenance-fields">
        <label><span>份数</span><input v-model.number="order.copies" :aria-label="`${order.code} 份数`" /></label>
        <label><span>工位</span><input v-model="order.stationId" :aria-label="`${order.code} 工位`" disabled /></label>
        <label v-if="order.targetType === 'PRINT_TARGET'"><span>队列</span><input v-model="order.queueId" :aria-label="`${order.code} 队列`" disabled /></label>
      </div>
      <div class="maintenance-actions">
        <button class="secondary-button" type="button" :disabled="busy" @click="save(order)">保存修改</button>
        <button v-if="!order.recycled" class="secondary-button" type="button" :disabled="busy" @click="recycle(order)">放入回收站</button>
      </div>
      <ul v-if="order.adjustments.length">
        <li v-for="line in order.adjustments" :key="line">{{ line }}</li>
      </ul>
    </article>

    <h3>回收站</h3>
    <p v-if="bin.length === 0" class="empty-hint">回收站是空的。</p>
    <div v-else class="module-table-wrap refined-table-wrap">
      <table class="module-table">
        <thead>
          <tr>
            <th>类型</th>
            <th>单号</th>
            <th>原因</th>
            <th>动作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in bin" :key="`bin-${order.targetType}-${order.code}`">
            <td>{{ typeLabel[order.targetType] }}</td>
            <td class="code">{{ order.code }}</td>
            <td>{{ order.recycleReason || '—' }}</td>
            <td><button class="secondary-button" type="button" :disabled="busy" @click="restore(order)">恢复</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
