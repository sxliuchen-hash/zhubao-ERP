<script setup lang="ts">
import { ref } from 'vue';
import { confirmAssetStocktake, countAssetStocktake, openAssetStocktake, type FixedAssetStocktake } from '../../services/fixedAssetApi';

const assetCode = ref('');
const countedAt = ref('');
const stocktakeId = ref('');
const expectedVersion = ref('1');
const seen = ref('PRESENT');
const location = ref('');
const exceptionText = ref('');
const current = ref<FixedAssetStocktake | null>(null);
const message = ref('');
const requestId = ref(newRequestId());

function newRequestId() {
  return `asset-pd-${Date.now()}`;
}

async function openSheet() {
  if (!assetCode.value.trim() || !countedAt.value.trim()) {
    message.value = '请填写资产和盘点时点';
    return;
  }
  const saved = await openAssetStocktake({
    requestId: requestId.value,
    assetCode: assetCode.value.trim(),
    countedAt: countedAt.value.trim(),
  });
  current.value = saved;
  stocktakeId.value = saved.stocktakeId;
  expectedVersion.value = String(saved.version);
  requestId.value = newRequestId();
  message.value = saved.bookLocation ? `账面位置 ${saved.bookLocation}` : '账面位置待补';
}

async function countSheet() {
  if (!stocktakeId.value) {
    message.value = '请先打开盘点';
    return;
  }
  const saved = await countAssetStocktake({
    requestId: requestId.value,
    stocktakeId: stocktakeId.value,
    expectedVersion: Number(expectedVersion.value),
    seen: seen.value,
    location: location.value.trim(),
    custodian: '',
    exceptionText: exceptionText.value.trim(),
  });
  current.value = saved;
  expectedVersion.value = String(saved.version);
  requestId.value = newRequestId();
  message.value = saved.seen === 'ABSENT' ? '未见，不会自动报废' : '已登记在场';
}

async function confirmSheet() {
  if (!current.value?.seen) {
    message.value = '请先登记实盘';
    return;
  }
  const saved = await confirmAssetStocktake({
    requestId: requestId.value,
    stocktakeId: stocktakeId.value,
    expectedVersion: Number(expectedVersion.value),
  });
  current.value = saved;
  requestId.value = newRequestId();
  message.value = saved.seen === 'ABSENT' ? '已确认未见，原卡仍在' : '已确认差异，原值不改';
}
</script>

<template>
  <section aria-label="资产盘点">
    <p>漏扫和未见不报废，也不改原值。确认与登记分开。</p>
    <form aria-label="打开盘点" @submit.prevent="openSheet">
      <label>资产编码 <input v-model="assetCode" aria-label="盘点资产编码" @change="location = ''" /></label>
      <label>时点 <input v-model="countedAt" aria-label="盘点时点" /></label>
      <button type="submit">打开盘点</button>
    </form>
    <form aria-label="登记实盘" @submit.prevent="countSheet">
      <label>结果
        <select v-model="seen" aria-label="实盘结果">
          <option value="PRESENT">在场</option>
          <option value="ABSENT">未见</option>
        </select>
      </label>
      <label>实盘位置 <input v-model="location" aria-label="实盘位置" placeholder="不改" /></label>
      <label>异常 <input v-model="exceptionText" aria-label="盘点异常" /></label>
      <button type="submit">登记实盘</button>
    </form>
    <button type="button" @click="confirmSheet">确认差异</button>
    <p v-if="message">{{ message }}</p>
  </section>
</template>
