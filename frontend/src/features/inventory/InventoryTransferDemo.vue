<script setup lang="ts">
import { computed, ref } from 'vue';
import { createInboundDemoCommand } from './inventoryTypes';

const command = ref(createInboundDemoCommand());
const json = computed(() => JSON.stringify(command.value, null, 2));
</script>

<template>
  <section class="panel">
    <div class="panel-heading">
      <h2>库存台账命令</h2>
      <span class="status-pill">transfer</span>
    </div>
    <p class="muted">M0 使用转移式库存命令作为所有库存变化入口，后续由后端库存服务统一落台账和余额。</p>
    <pre class="result-box">{{ json }}</pre>
  </section>
</template>
