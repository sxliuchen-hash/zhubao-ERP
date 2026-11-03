<script setup lang="ts">
import { ref } from 'vue';
import {
  confirmAssetRepair,
  fetchAssetRepairs,
  payAssetRepair,
  recordAssetRepair,
  type FixedAssetRepairView,
} from '../../services/fixedAssetApi';

const assetCode = ref('');
const repairedOn = ref('');
const handoverName = ref('');
const evidenceCode = ref('');
const repairId = ref('');
const amount = ref('');
const payAmount = ref('');
const paidOn = ref('');
const voucher = ref('');
const repairs = ref<FixedAssetRepairView[]>([]);
const message = ref('');
const requestId = ref(newRequestId());

function newRequestId() {
  return `asset-repair-${Date.now()}`;
}

function money(raw: string): number | null {
  const text = raw.trim();
  if (!text) return null;
  return Number(text);
}

function clearMoney() {
  amount.value = '';
  payAmount.value = '';
  paidOn.value = '';
  voucher.value = '';
}

async function loadRepairs() {
  if (!assetCode.value.trim()) {
    message.value = '请选择资产';
    return;
  }
  repairs.value = await fetchAssetRepairs(assetCode.value.trim());
  message.value = repairs.value.length ? '已读取维修记录' : '还没有维修';
}

async function record() {
  if (!assetCode.value.trim() || !repairedOn.value.trim()) {
    message.value = '请填写维修对象和日期';
    return;
  }
  const saved = await recordAssetRepair({
    requestId: requestId.value,
    assetCode: assetCode.value.trim(),
    repairedOn: repairedOn.value.trim(),
    handoverName: handoverName.value.trim(),
    evidenceCode: evidenceCode.value.trim(),
  });
  repairId.value = saved.repairId;
  requestId.value = newRequestId();
  message.value = `${saved.stateLabel}，费用未确认`;
  await loadRepairs();
  message.value = `${saved.stateLabel}，费用未确认`;
}

async function confirmCost() {
  if (!repairId.value.trim()) {
    message.value = '请选择维修记录';
    return;
  }
  if (!amount.value.trim()) {
    message.value = '金额未齐，不能确认维修费用';
    return;
  }
  if (amount.value.trim() === '0') {
    message.value = '未知金额不能写成 0';
    return;
  }
  if (!evidenceCode.value.trim()) {
    message.value = '维修费用必须有凭据';
    return;
  }
  const saved = await confirmAssetRepair({
    requestId: requestId.value,
    repairId: repairId.value.trim(),
    amount: money(amount.value),
    evidenceCode: evidenceCode.value.trim(),
  });
  requestId.value = newRequestId();
  message.value = saved.paid ? '费用已确认并已付' : '费用已确认，尚未付款';
}

async function pay() {
  if (!voucher.value.trim()) {
    message.value = '没有付款凭据，不能标成已付';
    return;
  }
  if (!payAmount.value.trim() || payAmount.value.trim() === '0') {
    message.value = '未知金额不能写成 0';
    return;
  }
  const saved = await payAssetRepair({
    requestId: requestId.value,
    repairId: repairId.value.trim(),
    amount: money(payAmount.value),
    paidOn: paidOn.value.trim(),
    voucher: voucher.value.trim(),
  });
  requestId.value = newRequestId();
  message.value = saved.paid ? `已付 ${saved.paidAmount}，成本仍是 ${saved.costAmount}` : '付款没有完成';
}
</script>

<template>
  <section class="job-panel" aria-label="资产维修">
    <header class="job-head">
      <h2>维修</h2>
      <p>先登记维修，再凭据确认费用，付款另记。费用未齐不能确认。</p>
    </header>
    <div class="info-fields">
      <label><span>资产编码</span><input v-model="assetCode" aria-label="维修资产编码" @change="clearMoney" /></label>
    </div>
    <div class="job-toolbar">
      <button type="button" class="secondary-button" aria-label="查询维修" @click="loadRepairs">查询维修</button>
    </div>
    <ul class="job-records">
      <li v-for="row in repairs" :key="row.repairId">
        {{ row.repairId }} {{ row.stateLabel }} 原值 {{ row.originalValue }}
        费用 {{ row.costAmount == null ? '未齐' : row.costAmount }}
        {{ row.paid ? '已付' : '未付' }}
        <span v-for="payment in row.payments" :key="payment.paymentCode">付款 {{ payment.paymentCode }} 凭据 {{ payment.voucher }}</span>
      </li>
    </ul>
    <form class="info-fields" aria-label="登记维修" @submit.prevent="record">
      <label><span>维修日期</span><input v-model="repairedOn" aria-label="维修日期" /></label>
      <label><span>交接</span><input v-model="handoverName" aria-label="维修交接" /></label>
      <label><span>凭据</span><input v-model="evidenceCode" aria-label="维修凭据" /></label>
      <div class="job-span"><button class="primary-button" type="submit">登记维修</button></div>
    </form>
    <form class="info-fields" aria-label="确认维修费用" @submit.prevent="confirmCost">
      <label><span>维修单</span><input v-model="repairId" aria-label="维修单号" /></label>
      <label><span>费用</span><input v-model="amount" aria-label="维修费用" placeholder="未齐" /></label>
      <div class="job-span"><button class="primary-button" type="submit">确认费用</button></div>
    </form>
    <form class="info-fields" aria-label="支付维修费" @submit.prevent="pay">
      <label><span>付款金额</span><input v-model="payAmount" aria-label="维修付款金额" /></label>
      <label><span>付款日期</span><input v-model="paidOn" aria-label="维修付款日期" /></label>
      <label><span>付款凭据</span><input v-model="voucher" aria-label="维修付款凭据" /></label>
      <div class="job-span"><button class="primary-button" type="submit">登记付款</button></div>
    </form>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
  </section>
</template>
