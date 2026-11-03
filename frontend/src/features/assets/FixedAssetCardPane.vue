<script setup lang="ts">
import { ref } from 'vue';
import {
  fetchFixedAssetCards,
  receiveFixedAsset,
  type FixedAssetCardView,
} from '../../services/fixedAssetApi';

const cards = ref<FixedAssetCardView[]>([]);
const source = ref('PURCHASE');
const assetCode = ref('');
const name = ref('');
const category = ref('');
const originalValue = ref('');
const residualValue = ref('');
const startMonth = ref('');
const usefulMonths = ref('');
const supplierName = ref('');
const acquiredOn = ref('');
const handoverLocation = ref('');
const handoverReceiver = ref('');
const paymentAmount = ref('');
const paymentDate = ref('');
const paymentVoucher = ref('');
const message = ref('');
const requestId = ref(newRequestId());

function newRequestId() {
  return `asset-acq-${Date.now()}`;
}

function clearDraft() {
  originalValue.value = '';
  residualValue.value = '';
  paymentAmount.value = '';
  paymentDate.value = '';
  paymentVoucher.value = '';
  supplierName.value = '';
  handoverLocation.value = '';
  handoverReceiver.value = '';
}

function chooseCard(code: string) {
  assetCode.value = code;
  clearDraft();
  message.value = '';
}

function changeSource(next: string) {
  source.value = next;
  clearDraft();
  if (next !== 'EXISTING_CARD') {
    assetCode.value = '';
  }
  message.value = '';
}

function money(raw: string): number | null {
  const text = raw.trim();
  if (!text) return null;
  return Number(text);
}

async function loadCards() {
  cards.value = await fetchFixedAssetCards();
  message.value = cards.value.length ? '已读取资产卡片' : '没有资产卡片';
}

async function submit() {
  const original = money(originalValue.value);
  const residual = money(residualValue.value);
  const payment = money(paymentAmount.value);
  if (originalValue.value.trim() === '0' || paymentAmount.value.trim() === '0') {
    message.value = '未知金额不能写成 0';
    return;
  }
  if ((payment != null || paymentDate.value.trim() || paymentVoucher.value.trim()) && !paymentVoucher.value.trim()) {
    message.value = '没有付款凭据，不能标成已付';
    return;
  }
  if (source.value === 'EXISTING_CARD' && !assetCode.value) {
    message.value = '请选择已有资产';
    return;
  }
  try {
    const saved = await receiveFixedAsset({
      requestId: requestId.value,
      source: source.value,
      assetCode: source.value === 'EXISTING_CARD' ? assetCode.value : '',
      name: name.value.trim(),
      category: category.value.trim(),
      originalValue: source.value === 'EXISTING_CARD' ? null : original,
      residualValue: source.value === 'EXISTING_CARD' ? null : residual,
      startMonth: startMonth.value.trim(),
      usefulMonths: usefulMonths.value.trim() ? Number(usefulMonths.value) : null,
      supplierName: supplierName.value.trim(),
      acquiredOn: acquiredOn.value.trim(),
      handoverLocation: handoverLocation.value.trim(),
      handoverReceiver: handoverReceiver.value.trim(),
      coveredPayableCode: '',
      paymentAmount: payment,
      paymentDate: paymentDate.value.trim(),
      paymentVoucher: paymentVoucher.value.trim(),
    });
    requestId.value = newRequestId();
    await loadCards();
    message.value = saved.amountState === 'INCOMPLETE' ? '金额未齐，未新建原值' : `已记录购置 ${saved.acquisitionId}`;
  } catch (error) {
    message.value = error instanceof Error ? error.message : '购置没有提交';
  }
}
</script>

<template>
  <section class="job-panel" aria-label="资产卡片">
    <header class="job-head">
      <h2>资产卡片</h2>
      <p>先查卡片，再记录新购置或接到已有卡片。未知金额留空，不能写成 0。</p>
    </header>
    <div class="job-toolbar">
      <button type="button" class="secondary-button" @click="loadCards">查询卡片</button>
    </div>
    <p v-if="cards.length === 0" class="info-context">还没有查询到资产卡片</p>
    <ul v-else class="job-records">
      <li v-for="card in cards" :key="card.code">
        <button type="button" class="table-action" @click="chooseCard(card.code)">{{ card.code }} {{ card.name }}</button>
        <span>原值 {{ card.originalValue }}</span>
        <span>位置 {{ card.location.label }}</span>
        <span>保管 {{ card.custodian.label }}</span>
        <span>状态 {{ card.useStatus.label }}</span>
      </li>
    </ul>
    <form class="info-fields" aria-label="资产购置" @submit.prevent="submit">
      <label><span>来源</span>
        <select :value="source" aria-label="购置来源" @change="changeSource(($event.target as HTMLSelectElement).value)">
          <option value="PURCHASE">新购置</option>
          <option value="EXISTING_CARD">已有卡片</option>
        </select>
      </label>
      <label v-if="source === 'EXISTING_CARD'"><span>资产编码</span><input v-model="assetCode" aria-label="已有资产编码" /></label>
      <template v-else>
        <label><span>名称</span><input v-model="name" aria-label="购置名称" /></label>
        <label><span>分类</span><input v-model="category" aria-label="购置分类" /></label>
        <label><span>原值</span><input v-model="originalValue" aria-label="购置原值" placeholder="未齐" /></label>
        <label><span>残值</span><input v-model="residualValue" aria-label="购置残值" /></label>
        <label><span>起始月</span><input v-model="startMonth" aria-label="折旧起始月" /></label>
        <label><span>使用月数</span><input v-model="usefulMonths" aria-label="使用月数" /></label>
      </template>
      <label><span>供应商</span><input v-model="supplierName" aria-label="供应商" /></label>
      <label><span>日期</span><input v-model="acquiredOn" aria-label="购置日期" /></label>
      <label><span>交接位置</span><input v-model="handoverLocation" aria-label="交接位置" /></label>
      <label><span>交接人</span><input v-model="handoverReceiver" aria-label="交接人" /></label>
      <label><span>付款金额</span><input v-model="paymentAmount" aria-label="付款金额" placeholder="未付" /></label>
      <label><span>付款日期</span><input v-model="paymentDate" aria-label="付款日期" /></label>
      <label><span>付款凭据</span><input v-model="paymentVoucher" aria-label="付款凭据" /></label>
      <div class="job-span"><button class="primary-button" type="submit">提交购置</button></div>
    </form>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
  </section>
</template>
