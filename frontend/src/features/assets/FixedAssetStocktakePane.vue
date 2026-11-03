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
  <section class="job-panel" aria-label="资产盘点">
    <header class="job-head">
      <h2>逐件盘点</h2>
      <p>漏扫和未见不报废，也不改原值。确认与登记分开。</p>
    </header>
    <form class="info-fields" aria-label="打开盘点" @submit.prevent="openSheet">
      <label><span>资产编码</span><input v-model="assetCode" aria-label="盘点资产编码" @change="location = ''" /></label>
      <label><span>时点</span><input v-model="countedAt" aria-label="盘点时点" /></label>
      <div class="job-span"><button class="secondary-button" type="submit">打开盘点</button></div>
    </form>
    <form class="info-fields" aria-label="登记实盘" @submit.prevent="countSheet">
      <label><span>结果</span>
        <select v-model="seen" aria-label="实盘结果">
          <option value="PRESENT">在场</option>
          <option value="ABSENT">未见</option>
        </select>
      </label>
      <label><span>实盘位置</span><input v-model="location" aria-label="实盘位置" placeholder="不改" /></label>
      <label><span>异常</span><input v-model="exceptionText" aria-label="盘点异常" /></label>
      <div class="job-span"><button class="primary-button" type="submit">登记实盘</button></div>
    </form>
    <div class="job-toolbar">
      <button type="button" class="primary-button" @click="confirmSheet">确认差异</button>
    </div>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
  </section>
</template>
