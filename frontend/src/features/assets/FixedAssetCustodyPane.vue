<script setup lang="ts">
import { ref } from 'vue';
import { fetchAssetCustody, transferAssetCustody, type FixedAssetCustodyView } from '../../services/fixedAssetApi';

const assetCode = ref('');
const expectedVersion = ref('0');
const location = ref('');
const custodian = ref('');
const useStatus = ref('');
const history = ref<FixedAssetCustodyView[]>([]);
const message = ref('');
const requestId = ref(newRequestId());

function newRequestId() {
  return `asset-move-${Date.now()}`;
}

function clearFields() {
  location.value = '';
  custodian.value = '';
  useStatus.value = '';
}

function clearDraft() {
  clearFields();
  message.value = '';
}

async function loadHistory() {
  if (!assetCode.value.trim()) {
    message.value = '请选择资产';
    return;
  }
  history.value = await fetchAssetCustody(assetCode.value.trim());
  const latest = history.value.at(-1);
  expectedVersion.value = latest ? String(latest.version) : '0';
  message.value = history.value.length ? '已读取交接历史' : '还没有交接';
}

async function submit() {
  if (!assetCode.value.trim()) {
    message.value = '请选择资产';
    return;
  }
  if (!location.value.trim() && !custodian.value.trim() && !useStatus.value.trim()) {
    message.value = '交接没有变化';
    return;
  }
  try {
    const saved = await transferAssetCustody({
      requestId: requestId.value,
      assetCode: assetCode.value.trim(),
      expectedVersion: Number(expectedVersion.value),
      location: location.value.trim(),
      custodian: custodian.value.trim(),
      useStatus: useStatus.value.trim(),
    });
    requestId.value = newRequestId();
    clearFields();
    await loadHistory();
    message.value = `已交接，原值 ${saved.originalValue}`;
  } catch (error) {
    message.value = error instanceof Error ? error.message : '交接没有提交';
  }
}
</script>

<template>
  <section aria-label="资产交接">
    <label>资产编码 <input v-model="assetCode" aria-label="交接资产编码" @change="clearDraft" /></label>
    <label>卡片版本 <input v-model="expectedVersion" aria-label="交接版本" /></label>
    <button type="button" aria-label="查询交接" @click="loadHistory">查询交接</button>
    <ul>
      <li v-for="row in history" :key="row.transferId">
        原值 {{ row.originalValue }}，位置 {{ row.fromLocationLabel }} 到 {{ row.toLocationLabel }}，保管 {{ row.fromCustodianLabel }} 到 {{ row.toCustodianLabel }}
      </li>
    </ul>
    <form aria-label="办理交接" @submit.prevent="submit">
      <label>新位置 <input v-model="location" aria-label="新位置" placeholder="不改" /></label>
      <label>新保管人 <input v-model="custodian" aria-label="新保管人" placeholder="不改" /></label>
      <label>使用状态
        <select v-model="useStatus" aria-label="交接状态">
          <option value="">不改</option>
          <option value="在用">在用</option>
          <option value="闲置">闲置</option>
        </select>
      </label>
      <button type="submit">提交交接</button>
    </form>
    <p v-if="message">{{ message }}</p>
  </section>
</template>
