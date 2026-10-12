<script setup lang="ts">
import { ref } from 'vue';
import IntakePanel from '../intake/IntakePanel.vue';
import ProductionPanel from '../production/ProductionPanel.vue';
import ShippingPanel from '../shipping/ShippingPanel.vue';

const jobMode = ref<'intake' | 'station' | 'shipping'>('intake');
</script>

<template>
  <div class="page-stack intake-page">
    <div class="button-row">
      <button
        :class="jobMode === 'intake' ? 'primary-button' : 'secondary-button'"
        type="button"
        aria-label="收货"
        @click="jobMode = 'intake'"
      >
        收货
      </button>
      <button
        :class="jobMode === 'station' ? 'primary-button' : 'secondary-button'"
        type="button"
        aria-label="岗位报工"
        @click="jobMode = 'station'"
      >
        岗位报工
      </button>
      <button
        :class="jobMode === 'shipping' ? 'primary-button' : 'secondary-button'"
        type="button"
        aria-label="发货"
        @click="jobMode = 'shipping'"
      >
        发货
      </button>
    </div>

    <ShippingPanel
      v-if="jobMode === 'shipping'"
      title="确认发货"
      intro="扫成品标签。选择完整库存维度，并填写本次数量、重量和物流。物流扫码只填写运单号。"
    />

    <ProductionPanel
      v-if="jobMode === 'station'"
      title="岗位报工"
      intro="扫子单贴纸。不拆的母单扫母单贴纸。页面只显示当前账号能做的工序。"
    />

    <IntakePanel
      v-if="jobMode === 'intake'"
      title="手机收货"
      intro="扫快递面单上的单号。新单生成母单并写下加工要求和期限，然后补拆包证据。"
    />
  </div>
</template>
