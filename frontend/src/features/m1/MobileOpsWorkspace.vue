<script setup lang="ts">
import { ref } from 'vue';
import IntakePanel from '../intake/IntakePanel.vue';
import ProductionPanel from '../production/ProductionPanel.vue';
import QualityPanel from '../quality/QualityPanel.vue';
import ShippingPanel from '../shipping/ShippingPanel.vue';

const jobMode = ref<'intake' | 'station' | 'quality' | 'shipping'>('intake');
const insecureContext = typeof window !== 'undefined' && window.isSecureContext === false;
</script>

<template>
  <div class="page-stack intake-page field-job">
    <p v-if="insecureContext" class="operation-feedback danger">
      当前是 HTTP，手机打不开摄像头。请改用 https:// 加服务器地址打开本页。浏览器提示证书不受信任时，选择继续访问。
    </p>
    <div class="field-modes" role="tablist" aria-label="现场作业">
      <button
        :class="jobMode === 'intake' ? 'primary-button' : 'secondary-button'"
        type="button"
        role="tab"
        :aria-selected="jobMode === 'intake'"
        aria-label="收货"
        @click="jobMode = 'intake'"
      >
        收货
      </button>
      <button
        :class="jobMode === 'station' ? 'primary-button' : 'secondary-button'"
        type="button"
        role="tab"
        :aria-selected="jobMode === 'station'"
        aria-label="岗位报工"
        @click="jobMode = 'station'"
      >
        岗位报工
      </button>
      <button
        :class="jobMode === 'quality' ? 'primary-button' : 'secondary-button'"
        type="button"
        role="tab"
        :aria-selected="jobMode === 'quality'"
        aria-label="质检"
        @click="jobMode = 'quality'"
      >
        质检
      </button>
      <button
        :class="jobMode === 'shipping' ? 'primary-button' : 'secondary-button'"
        type="button"
        role="tab"
        :aria-selected="jobMode === 'shipping'"
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

    <QualityPanel
      v-if="jobMode === 'quality'"
      field
      title="手机质检"
      intro="扫子单或不拆母单。逐条判定，不合格和让步要写原因。质检员是当前登录账号。开返工、返工报工、复检和放行仍用这一套。"
    />

    <ProductionPanel
      v-if="jobMode === 'station'"
      field
      title="岗位报工"
      intro="扫母单或子单。已拆母单请点选子单。先核对本岗位要求，再提交。页面只显示当前账号能做的工序。"
    />

    <IntakePanel
      v-if="jobMode === 'intake'"
      field
      title="手机收货"
      intro="扫快递或手输运单。核对客户、要求和期限后建母单。照片或视频失败可以单独重试。"
    />
  </div>
</template>

<style scoped>
@media (max-width: 900px) {
  .field-modes { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .field-modes button { min-width: 0; padding: 8px 4px; font-size: 14px; }
}
</style>
