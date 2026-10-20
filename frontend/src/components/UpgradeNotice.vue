<script setup lang="ts">
import { onMounted, ref } from 'vue';

const noticeId = '20261020';
const storageKey = `zhubao-upgrade-notice-${noticeId}`;
const visible = ref(false);

onMounted(() => {
  try {
    visible.value = globalThis.localStorage?.getItem(storageKey) !== '1';
  } catch {
    visible.value = true;
  }
});

function dismiss() {
  visible.value = false;
  try {
    globalThis.localStorage?.setItem(storageKey, '1');
  } catch {
    // 浏览器禁止写入时，本次关闭仍然生效，下次打开会再看到。
  }
}
</script>

<template>
  <section v-if="visible" class="upgrade-notice" aria-label="版本升级通知">
    <header>
      <strong>2026-10-20 版本升级</strong>
      <button class="secondary-button" type="button" @click="dismiss">知道了</button>
    </header>
    <ul>
      <li>运维里的单据维护会列出未回收母单，可按日期、客户和建单人筛选后再打开。回收仍不会自动冲掉库存和账单。</li>
      <li>已经入账的客户、材质和货品不能直接改。还没拆单、交付或发货的母单，可以改正收货数量和重量，系统会留下冲正流水。</li>
      <li>回收和恢复一次完成，中途失败不会只改一半。打印任务只有未领取的可以改份数，失败任务不会再被静默补打。</li>
      <li>客户改名后，原来的授权范围还在。标签和扫码按授权客户过滤。手机新建收货时，证据编号不再写成运单号。</li>
      <li>网站图标已更新。手机上已经安装的应用图标要等新的安装包，这次服务器升级不会替换手机里的安装包。</li>
    </ul>
  </section>
</template>
