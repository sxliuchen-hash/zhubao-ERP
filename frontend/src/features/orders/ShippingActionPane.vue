<script setup lang="ts">
import { computed, provide } from 'vue';
import ShippingPanel from '../shipping/ShippingPanel.vue';
import type { ScanBalanceCandidate } from '../../services/scanContextApi';
import type { OrderActionTarget } from './orderActions';

const props = defineProps<{ target: OrderActionTarget; balance?: ScanBalanceCandidate; active: boolean }>();
const emit = defineEmits<{ changed: []; selected: [target: OrderActionTarget] }>();
provide('zhubao-scan-suspended', computed(() => !props.active));
</script>

<template>
  <ShippingPanel embedded :initial-target="target" :initial-balance="balance" title="本单发货" intro="选择完整库存维度，核对本次数量、重量及物流后再提交。" :inert="!active || undefined" @saved="emit('changed')" @target-selected="emit('selected', $event)" />
</template>
