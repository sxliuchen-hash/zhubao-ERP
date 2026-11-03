<script setup lang="ts">
import { ref } from 'vue';
import {
  fetchAssetDispositions,
  recordAssetDisposition,
  type FixedAssetDispositionView,
} from '../../services/fixedAssetApi';

const assetCode = ref('');
const kind = ref('SCRAP');
const occurredOn = ref('');
const basis = ref('');
const receiptCode = ref('');
const proceeds = ref('');
const rows = ref<FixedAssetDispositionView[]>([]);
const message = ref('');
const requestId = ref(newRequestId());

function newRequestId() {
  return `asset-dsp-${Date.now()}`;
}

function money(raw: string): number | null {
  const text = raw.trim();
  if (!text) return null;
  return Number(text);
}

async function loadRows() {
  if (!assetCode.value.trim()) {
    message.value = '请选择资产';
    return;
  }
  rows.value = await fetchAssetDispositions(assetCode.value.trim());
  message.value = rows.value.length ? '已读取处置记录' : '还没有处置';
}

async function submit() {
  if (proceeds.value.trim() === '0') {
    message.value = '未知金额不能写成 0';
    return;
  }
  if (proceeds.value.trim() && !receiptCode.value.trim()) {
    message.value = '处置回款必须另有真实资金来源';
    return;
  }
  const saved = await recordAssetDisposition({
    requestId: requestId.value,
    assetCode: assetCode.value.trim(),
    kind: kind.value,
    occurredOn: occurredOn.value.trim(),
    basis: basis.value.trim(),
    receiptCode: receiptCode.value.trim(),
    proceeds: money(proceeds.value),
  });
  requestId.value = newRequestId();
  message.value = saved.received
    ? `已关联回款，净值待核，原值 ${saved.originalValue}`
    : `${saved.kindLabel}已记录，未收款，净值待核`;
  await loadRows();
  message.value = saved.received
    ? `已关联回款，净值待核，原值 ${saved.originalValue}`
    : `${saved.kindLabel}已记录，未收款，净值待核`;
}
</script>

<template>
  <section class="job-panel" aria-label="资产处置">
    <header class="job-head">
      <h2>停用、报废与处置</h2>
      <p>净值待核。报废不是收款，也不扣员工工资。</p>
    </header>
    <div class="info-fields">
      <label><span>资产编码</span><input v-model="assetCode" aria-label="处置资产编码" /></label>
    </div>
    <div class="job-toolbar">
      <button type="button" class="secondary-button" aria-label="查询处置" @click="loadRows">查询处置</button>
    </div>
    <ul class="job-records">
      <li v-for="row in rows" :key="row.dispositionId">
        {{ row.kindLabel }} 原值 {{ row.originalValue }} 净值 {{ row.netValue == null ? '待核' : row.netValue }}
        折旧影响 {{ row.depreciationImpactLabel }} {{ row.received ? '已关联回款' : '未收款' }}
      </li>
    </ul>
    <form class="info-fields" aria-label="登记处置" @submit.prevent="submit">
      <label><span>类型</span>
        <select v-model="kind" aria-label="处置类型">
          <option value="STOP">停用</option>
          <option value="SCRAP">报废</option>
          <option value="DISPOSE">处置</option>
        </select>
      </label>
      <label><span>日期</span><input v-model="occurredOn" aria-label="处置日期" /></label>
      <label><span>依据</span><input v-model="basis" aria-label="处置依据" /></label>
      <label><span>收款单</span><input v-model="receiptCode" aria-label="回款收款单" placeholder="没有则留空" /></label>
      <label><span>回款金额</span><input v-model="proceeds" aria-label="回款金额" placeholder="未收款" /></label>
      <div class="job-span"><button class="primary-button" type="submit">登记处置</button></div>
    </form>
    <p v-if="message" class="operation-feedback" role="status">{{ message }}</p>
  </section>
</template>
