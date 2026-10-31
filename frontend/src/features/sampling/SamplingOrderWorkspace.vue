<script setup lang="ts">
import { ref } from 'vue';
import SamplingProductionPane from './SamplingProductionPane.vue';
import SamplingVersionPane from './SamplingVersionPane.vue';
import { fetchSamplingOrders, openSamplingOrder, type SamplingOrderView } from '../../services/samplingApi';

const orders = ref<SamplingOrderView[]>([]);
const customerId = ref('');
const materialIntent = ref('CUSTOMER_SUPPLIED');
const requirement = ref('');
const ownerName = ref('');
const message = ref('');
const requestId = ref(newRequestId());

function newRequestId() {
  return `sampling-order-${Date.now()}`;
}

async function loadOrders() {
  orders.value = await fetchSamplingOrders();
  message.value = orders.value.length ? '已读取打样单' : '没有打样单';
}

async function submit() {
  const id = Number(customerId.value.trim());
  if (!customerId.value.trim() || !Number.isInteger(id) || id <= 0) {
    message.value = '请选择客户';
    return;
  }
  if (!requirement.value.trim()) {
    message.value = '请填写样品要求';
    return;
  }
  if (!ownerName.value.trim()) {
    message.value = '请填写负责人';
    return;
  }
  const saved = await openSamplingOrder({
    requestId: requestId.value,
    customerId: id,
    materialIntent: materialIntent.value,
    requirement: requirement.value.trim(),
    ownerName: ownerName.value.trim(),
  });
  requestId.value = newRequestId();
  requirement.value = '';
  message.value = `已建立 ${saved.samplingCode}`;
  await loadOrders();
}
</script>

<template>
  <section class="page-stack" aria-label="打样单">
    <header>
      <h2>打样单</h2>
      <p>独立于普通加工母单和登记导入。当前状态只有进行中，完结资格尚未核定。</p>
    </header>
    <p role="status">{{ message }}</p>
    <button type="button" @click="loadOrders">查询打样单</button>
    <table v-if="orders.length">
      <thead>
        <tr>
          <th>打样号</th>
          <th>客户</th>
          <th>材料</th>
          <th>样品要求</th>
          <th>负责人</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="order in orders" :key="order.samplingCode">
          <td>{{ order.samplingCode }}</td>
          <td>{{ order.customerName }}</td>
          <td>{{ order.materialIntentLabel }}</td>
          <td>{{ order.requirement }}</td>
          <td>{{ order.ownerName }}</td>
          <td>{{ order.statusLabel }}</td>
        </tr>
      </tbody>
    </table>
    <form @submit.prevent="submit">
      <label>客户编号 <input v-model="customerId" aria-label="客户编号" /></label>
      <label>材料安排
        <select v-model="materialIntent" aria-label="材料安排">
          <option value="CUSTOMER_SUPPLIED">客户来料</option>
          <option value="COMPANY_PREPARED">公司自备</option>
        </select>
      </label>
      <label>样品要求 <input v-model="requirement" aria-label="样品要求" /></label>
      <label>负责人 <input v-model="ownerName" aria-label="负责人" /></label>
      <button type="submit">建立打样单</button>
    </form>
    <SamplingVersionPane />
    <SamplingProductionPane />
  </section>
</template>
