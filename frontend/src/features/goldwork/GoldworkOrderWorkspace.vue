<script setup lang="ts">
import { ref } from 'vue';
import GoldworkMaterialPane from './GoldworkMaterialPane.vue';
import GoldworkProductionPane from './GoldworkProductionPane.vue';
import GoldworkSettlementPane from './GoldworkSettlementPane.vue';
import GoldworkRecoveryPane from './GoldworkRecoveryPane.vue';
import { fetchGoldworkOrders, openGoldworkOrder, type GoldworkOrderView } from '../../services/goldworkApi';

const orders = ref<GoldworkOrderView[]>([]);
const customerId = ref('');
const materialIntent = ref('CUSTOMER_SUPPLIED');
const requirement = ref('');
const ownerName = ref('');
const message = ref('');
const requestId = ref(newRequestId());

function newRequestId() {
  return `goldwork-order-${Date.now()}`;
}

async function loadOrders() {
  orders.value = await fetchGoldworkOrders();
  message.value = orders.value.length ? '已读取金工单' : '没有金工单';
}

async function submit() {
  const id = Number(customerId.value.trim());
  if (!customerId.value.trim() || !Number.isInteger(id) || id <= 0) {
    message.value = '请选择客户';
    return;
  }
  if (!requirement.value.trim()) {
    message.value = '请填写金工要求';
    return;
  }
  if (!ownerName.value.trim()) {
    message.value = '请填写负责人';
    return;
  }
  const saved = await openGoldworkOrder({
    requestId: requestId.value,
    customerId: id,
    materialIntent: materialIntent.value,
    requirement: requirement.value.trim(),
    ownerName: ownerName.value.trim(),
  });
  requestId.value = newRequestId();
  requirement.value = '';
  message.value = `已建立 ${saved.goldworkCode}`;
  await loadOrders();
}
</script>

<template>
  <section class="page-stack" aria-label="金工单">
    <header>
      <h2>金工单</h2>
      <p>独立于打样、普通加工和登记导入。金工要求在建单时冻结，页面不上传模型。</p>
    </header>
    <p role="status">{{ message }}</p>
    <button type="button" @click="loadOrders">查询金工单</button>
    <table v-if="orders.length">
      <thead>
        <tr>
          <th>金工号</th>
          <th>客户</th>
          <th>材料</th>
          <th>金工要求</th>
          <th>路线</th>
          <th>负责人</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="order in orders" :key="order.goldworkCode">
          <td>{{ order.goldworkCode }}</td>
          <td>{{ order.customerName }}</td>
          <td>{{ order.materialIntentLabel }}</td>
          <td>{{ order.requirement }}</td>
          <td>{{ order.routeStepLabels.join('、') }}</td>
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
      <label>金工要求 <input v-model="requirement" aria-label="金工要求" /></label>
      <label>负责人 <input v-model="ownerName" aria-label="负责人" /></label>
      <button type="submit">建立金工单</button>
    </form>
    <GoldworkMaterialPane />
    <GoldworkRecoveryPane />
    <GoldworkProductionPane />
    <GoldworkSettlementPane />
  </section>
</template>
