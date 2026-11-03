<script setup lang="ts">
import { ref } from 'vue';
import { continueAssetDepreciation, type FixedAssetDepreciationRun } from '../../services/fixedAssetApi';

const yearMonth = ref('');
const stopMonth = ref('');
const message = ref('');
const run = ref<FixedAssetDepreciationRun | null>(null);
const requestId = ref(newRequestId());

function newRequestId() {
  return `asset-dep-${Date.now()}`;
}

async function submit() {
  if (!yearMonth.value.trim()) {
    message.value = '请填写折旧月份';
    return;
  }
  if (stopMonth.value.trim()) {
    message.value = '本版不填写折旧生效月，停用后暂停新增折旧';
    return;
  }
  const saved = await continueAssetDepreciation({
    requestId: requestId.value,
    yearMonth: yearMonth.value.trim(),
    stopEffectiveMonth: null,
  });
  run.value = saved;
  requestId.value = newRequestId();
  message.value = saved.entries.length
    ? `已按管理算法延续 ${saved.entries.length} 条，不是税务结论`
    : '本月没有可延续的折旧';
}
</script>

<template>
  <section aria-label="资产折旧">
    <p>按使用月数均分并在末期调尾差。这只是当前管理算法，不是全部会计或税务处理。</p>
    <form aria-label="延续折旧" @submit.prevent="submit">
      <label>月份 <input v-model="yearMonth" aria-label="折旧月份" placeholder="YYYY-MM" /></label>
      <label>停用生效月 <input v-model="stopMonth" aria-label="停用生效月" placeholder="本版不填" /></label>
      <button type="submit">延续折旧</button>
    </form>
    <ul v-if="run">
      <li v-for="entry in run.entries" :key="entry.code">{{ entry.assetCode }} {{ entry.yearMonth }} {{ entry.amount }}</li>
    </ul>
    <p v-if="run?.awaitingAssetCodes.length">以下资产已停用或处置，本版暂停新增折旧，已记折旧保留：{{ run.awaitingAssetCodes.join('、') }}</p>
    <p v-if="message">{{ message }}</p>
  </section>
</template>
