<script setup lang="ts">
import { ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
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
const selectedCode = ref('');

function newRequestId() {
  return `goldwork-order-${Date.now()}`;
}

function choose(code: string) {
  selectedCode.value = code;
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
  <div class="job-desk">
    <section class="job-surface" aria-label="金工单">
      <header class="job-head">
        <div>
          <h2>金工单</h2>
          <p>独立于打样、普通加工和登记导入。金工要求在建单时冻结，页面不上传模型。</p>
        </div>
        <button type="button" class="primary-button" @click="loadOrders">查询金工单</button>
      </header>
      <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
      <p v-if="selectedCode" class="job-count">当前单据 {{ selectedCode }}</p>
      <p v-else-if="orders.length" class="job-count">共 {{ orders.length }} 张金工单</p>
      <p v-if="!orders.length" class="job-empty">{{ message === '没有金工单' ? '当前没有金工单' : '查询后在这里列出金工单' }}</p>
      <div v-if="orders.length" class="job-table-wrap">
        <table class="job-table">
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
            <tr v-for="order in orders" :key="order.goldworkCode" class="job-row" :aria-selected="selectedCode === order.goldworkCode" @click="choose(order.goldworkCode)">
              <td><button type="button" class="job-code" @click.stop="choose(order.goldworkCode)">{{ order.goldworkCode }}</button></td>
              <td><strong>{{ order.customerName }}</strong></td>
              <td>{{ order.materialIntentLabel }}</td>
              <td>{{ order.requirement }}</td>
              <td>{{ order.routeStepLabels.join('、') }}</td>
              <td>{{ order.ownerName }}</td>
              <td><StatusBadge :label="order.statusLabel" tone="amber" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="orders.length" class="job-order-cards">
        <button v-for="order in orders" :key="`card-${order.goldworkCode}`" type="button" class="job-order-card" :aria-selected="selectedCode === order.goldworkCode" @click="choose(order.goldworkCode)">
          <strong>{{ order.requirement }}</strong>
          <span>{{ order.goldworkCode }} · {{ order.customerName }}</span>
          <small>{{ order.routeStepLabels.join('、') }} · {{ order.statusLabel }}</small>
        </button>
      </div>
    </section>
    <section class="job-surface" aria-label="建立金工单">
      <header class="job-head">
        <div>
          <h2>建立金工单</h2>
          <p>路线在建单时冻结。不填母单号或打样号，也不上传模型。</p>
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
        <label class="job-span"><span>金工要求</span><input v-model="requirement" aria-label="金工要求" /></label>
        <label><span>负责人</span><input v-model="ownerName" aria-label="负责人" /></label>
        <div class="job-span job-actions"><button class="primary-button" type="submit">建立金工单</button></div>
      </form>
    </section>
    <div class="job-work">
      <GoldworkMaterialPane :linked-code="selectedCode" />
      <GoldworkRecoveryPane :linked-code="selectedCode" />
      <GoldworkProductionPane :linked-code="selectedCode" />
      <GoldworkSettlementPane :linked-code="selectedCode" />
    </div>
  </div>
</template>
