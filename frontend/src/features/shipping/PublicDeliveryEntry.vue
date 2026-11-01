<script setup lang="ts">
import { ref, watch } from 'vue';
import { fetchPublicDelivery, publicPhotoUrl, type PublicDelivery } from '../../services/deliveryApi';
const props = withDefaults(defineProps<{ token: string; bag?: string; internalAllowed?: boolean; financeAllowed?: boolean }>(), { bag: '', internalAllowed: false, financeAllowed: false });
const detail = ref<PublicDelivery | null>(null); const error = ref(''); let sequence = 0;
watch(() => props.token, async token => {
  const read = ++sequence; detail.value = null; error.value = '';
  try { const saved = await fetchPublicDelivery(token); if (read === sequence) detail.value = saved; }
  catch (cause) { if (read === sequence) error.value = cause instanceof Error ? cause.message : '公开交付暂不可用'; }
}, { immediate: true });
</script>
<template>
  <main class="public-delivery" aria-label="发货扫码摘要">
    <h1>发货详情</h1>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else-if="!detail" role="status">正在读取发货摘要</p>
    <template v-if="detail">
      <p>{{ detail.customerName || '历史客户名称未记录' }} · {{ detail.deliveryId }}</p>
      <article v-for="item in detail.bags.filter(item => !bag || item.bagCode === `${detail!.deliveryId}-${bag}`)" :key="item.bagCode">
        <h2>{{ item.bagCode }}</h2><p>{{ item.material }} · {{ item.specification || '品名规格未记录' }}</p>
        <p>数量 {{ item.quantity }}{{ item.unit }} · 重量 {{ item.weight == null ? '未称' : `${item.weight}克` }}</p>
        <p style="white-space:pre-wrap">加工要求：{{ item.requirement || '未记录' }}</p><p>来源 {{ item.sourceCode }}</p>
      </article>
      <p style="white-space:pre-wrap">{{ detail.remark }}</p>
      <img v-for="code in detail.publicPhotoIds" :key="code" :src="publicPhotoUrl(token, code)" alt="明确公开的出库照片" referrerpolicy="no-referrer" style="max-width:100%" />
      <p><a :href="`/?delivery=${encodeURIComponent(detail.deliveryId)}`">{{ internalAllowed ? '查看内部交付与加工' : '登录后查看本次交付' }}</a></p>
      <p v-if="financeAllowed"><a :href="`/?billDelivery=${encodeURIComponent(detail.deliveryId)}`">查看允许的账单</a></p>
    </template>
  </main>
</template>
<style scoped>
.public-delivery { max-width:760px; margin:auto; padding:20px; overflow-wrap:anywhere; } article { border-bottom:1px solid #ddd; padding:12px 0; }
</style>
