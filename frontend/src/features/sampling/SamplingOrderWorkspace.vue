<script setup lang="ts">
import { ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import SamplingProductionPane from './SamplingProductionPane.vue';
import SamplingSettlementPane from './SamplingSettlementPane.vue';
import SamplingVersionPane from './SamplingVersionPane.vue';
import { fetchSamplingOrders, openSamplingOrder, type SamplingOrderView } from '../../services/samplingApi';

const orders = ref<SamplingOrderView[]>([]);
const customerId = ref('');
const materialIntent = ref('CUSTOMER_SUPPLIED');
const requirement = ref('');
const ownerName = ref('');
const message = ref('');
const requestId = ref(newRequestId());
const selectedCode = ref('');

function newRequestId() {
  return `sampling-order-${Date.now()}`;
}

function choose(code: string) {
  selectedCode.value = code;
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
  <div class="job-desk">
    <section class="job-surface" aria-label="打样单">
      <header class="job-head">
        <div>
          <h2>打样单</h2>
          <p>独立于普通加工母单和登记导入。当前状态只有进行中，完结资格尚未核定。</p>
        </div>
        <button type="button" class="primary-button" @click="loadOrders">查询打样单</button>
      </header>
      <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
      <p v-if="selectedCode" class="job-count">当前单据 {{ selectedCode }}</p>
      <p v-else-if="orders.length" class="job-count">共 {{ orders.length }} 张打样单</p>
      <p v-if="!orders.length" class="job-empty">{{ message === '没有打样单' ? '当前没有打样单' : '查询后在这里列出打样单' }}</p>
      <div v-if="orders.length" class="job-table-wrap">
        <table class="job-table">
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
            <tr v-for="order in orders" :key="order.samplingCode" class="job-row" :aria-selected="selectedCode === order.samplingCode" @click="choose(order.samplingCode)">
              <td><button type="button" class="job-code" @click.stop="choose(order.samplingCode)">{{ order.samplingCode }}</button></td>
              <td><strong>{{ order.customerName }}</strong></td>
              <td>{{ order.materialIntentLabel }}</td>
              <td>{{ order.requirement }}</td>
              <td>{{ order.ownerName }}</td>
              <td><StatusBadge :label="order.statusLabel" tone="amber" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="orders.length" class="job-order-cards">
        <button v-for="order in orders" :key="`card-${order.samplingCode}`" type="button" class="job-order-card" :aria-selected="selectedCode === order.samplingCode" @click="choose(order.samplingCode)">
          <strong>{{ order.requirement }}</strong>
          <span>{{ order.samplingCode }} · {{ order.customerName }}</span>
          <small>{{ order.materialIntentLabel }} · {{ order.ownerName }} · {{ order.statusLabel }}</small>
        </button>
      </div>
    </section>
    <section class="job-surface" aria-label="建立打样单">
      <header class="job-head">
        <div>
          <h2>建立打样单</h2>
          <p>客户、材料和样品要求在建单时写入。不填母单号。</p>
        </div>
      </header>
      <form class="info-fields" @submit.prevent="submit">
        <label><span>客户编号</span><input v-model="customerId" aria-label="客户编号" inputmode="numeric" /></label>
        <label><span>材料安排</span>
          <select v-model="materialIntent" aria-label="材料安排">
            <option value="CUSTOMER_SUPPLIED">客户来料</option>
            <option value="COMPANY_PREPARED">公司自备</option>
          </select>
        </label>
        <label class="job-span"><span>样品要求</span><input v-model="requirement" aria-label="样品要求" /></label>
        <label><span>负责人</span><input v-model="ownerName" aria-label="负责人" /></label>
        <div class="job-span job-actions"><button class="primary-button" type="submit">建立打样单</button></div>
      </form>
    </section>
    <div class="job-work">
      <SamplingVersionPane :linked-code="selectedCode" />
      <SamplingProductionPane :linked-code="selectedCode" />
      <SamplingSettlementPane :linked-code="selectedCode" />
    </div>
  </div>
</template>
