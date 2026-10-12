<script setup lang="ts">
import { computed } from 'vue';
import {
  scanObjectTypeLabel,
  type ScanBalanceCandidate,
  type ScanContextResponse,
  type ScanProgressRecord,
  type ScanRelatedTarget,
} from '../../services/scanContextApi';
import { progressStageLabel } from './progressTarget';

const props = defineProps<{
  context: ScanContextResponse | null;
}>();

const chainEmpty = computed(() => {
  const progress = props.context?.progress;
  if (!progress) return true;
  return progress.handoffs.length === 0 && !progress.quality && progress.shipments.length === 0;
});

const emit = defineEmits<{
  select: [target: ScanRelatedTarget];
}>();

function text(value: string | null | undefined): string {
  const trimmed = value?.trim() ?? '';
  return trimmed || '未填写';
}

function amount(value: number | null | undefined, unit: string | null | undefined): string {
  if (value === null || value === undefined) return '未填写';
  return unit?.trim() ? `${value} ${unit}` : String(value);
}

function sourceLabel(source: string | null | undefined): string {
  if (source === 'MOTHER_ORDER') return '母单';
  if (source === 'WORK_ORDER') return '子单';
  if (source === 'PROCESS') return '岗位';
  return '未标明来源';
}

function balanceText(item: ScanBalanceCandidate): string {
  return [
    item.materialCode || '未填物料',
    item.warehouseCode || '未填仓库',
    item.ownerType || '未填货权',
    item.stockStatus || '未填状态',
    amount(item.quantity, null),
    amount(item.weight, '克'),
  ].join(' / ');
}

function progressText(record: ScanProgressRecord): string {
  const parts = [progressStageLabel(record.stage)];
  if (record.process?.trim()) parts.push(record.process.trim());
  if (record.supplementary) parts.push('补充记录');
  parts.push(record.note?.trim() || '未填写说明');
  if (record.operatorName?.trim()) parts.push(record.operatorName.trim());
  return parts.join(' · ');
}

const actionLabels: Record<string, string> = {
  SELECT_CHILD: '选择子单',
  INTAKE: '收货建单',
  REPORT_PROCESS: '岗位报工',
  SUBMIT_QUALITY: '提交质检',
  OPEN_REWORK: '开返工',
  REPORT_REWORK: '返工报工',
  RELEASE: '放行',
  SHIP: '发货',
  FILL_LOGISTICS: '填写物流',
};
</script>

<template>
  <article v-if="context" class="scan-context-card section-block" aria-label="扫码上下文">
    <h3>{{ scanObjectTypeLabel(context.target.objectType) }} {{ context.target.code || '待选择' }}</h3>
    <dl class="detail-grid">
      <dt>客户</dt>
      <dd>{{ text(context.order.customerName) }}</dd>
      <dt>货品</dt>
      <dd>{{ text(context.order.goodsName) }}</dd>
      <dt>数量</dt>
      <dd>{{ amount(context.order.quantity, context.order.quantityUnit) }}</dd>
      <dt>重量</dt>
      <dd>{{ amount(context.order.weight, context.order.weightUnit) }}</dd>
      <dt>要求</dt>
      <dd>{{ text(context.order.requirement) }}</dd>
      <dt>期限</dt>
      <dd>{{ text(context.order.dueDate) }}</dd>
    </dl>
    <p v-if="context.requirements.motherRequirement">
      母单要求（{{ sourceLabel(context.requirements.motherSource) }}）：{{ context.requirements.motherRequirement }}
    </p>
    <p v-if="context.requirements.workOrderRequirement">
      子单要求（{{ sourceLabel(context.requirements.workOrderSource) }}）：{{ context.requirements.workOrderRequirement }}
    </p>
    <ul v-if="context.requirements.stationRequirements.length">
      <li v-for="item in context.requirements.stationRequirements" :key="`${item.process}-${item.text}`">
        {{ sourceLabel(item.source) }} {{ item.process || '未填工序' }}：{{ text(item.text) }}
      </li>
    </ul>

    <div v-if="context.requiresSelection" class="scan-choice-list">
      <p>请选择具体单据。系统不会自动打开第一张。</p>
      <p v-if="!context.relatedTargets.length">类型不明确，请先选择对象类型再查询。</p>
      <button
        v-for="item in context.relatedTargets"
        :key="`${item.objectType}-${item.code}`"
        class="secondary-button scan-choice-button"
        type="button"
        :aria-label="`选择${item.code}`"
        @click="emit('select', item)"
      >
        {{ scanObjectTypeLabel(item.objectType) }} {{ item.code }}
        <template v-if="item.requirement"> / {{ item.requirement }}</template>
      </button>
    </div>

    <div v-if="context.operationContext.balanceSelectionRequired">
      <p>有多笔余额，提交库存前必须选择完整维度。当前没有默认选中。</p>
      <ul>
        <li v-for="(item, index) in context.operationContext.balanceCandidates" :key="index">
          {{ balanceText(item) }}
        </li>
      </ul>
    </div>

    <ul v-if="context.actions.length" class="draft-list">
      <li v-for="action in context.actions" :key="action.code">
        {{ actionLabels[action.code] || action.code }}：{{ action.allowed ? '当前可办理' : (action.blockReason || '当前不能办理') }}
      </li>
    </ul>

    <section aria-label="全链路">
      <h3>全链路</h3>
      <p v-if="chainEmpty">暂无工序交付、质检结论或发货事实</p>
      <template v-else>
        <p v-for="item in context.progress.handoffs" :key="`${item.process}-${item.ledgerCode}`">
          工序 {{ text(item.process) }} · {{ text(item.operator) }} · {{ text(item.ledgerCode) }}
        </p>
        <p v-if="context.progress.quality">
          质检 {{ text(context.progress.quality.outcome) }}
          · {{ context.progress.quality.frozen ? '已冻结' : '未冻结' }}
          · 返工 {{ text(context.progress.quality.reworkOrderCode) }}
        </p>
        <p v-for="item in context.progress.shipments" :key="item.outboundCode || item.ledgerCode || item.logisticsNo || ''">
          发货 {{ text(item.outboundCode) }} · {{ text(item.logisticsNo) }} · {{ text(item.status) }}
        </p>
      </template>
    </section>

    <h3>过程记录</h3>
    <p v-if="!context.progress.progressRecords.length">暂无过程记录</p>
    <ul v-else>
      <li v-for="record in context.progress.progressRecords" :key="record.code || record.note || record.recordedAt || ''">
        {{ progressText(record) }}
      </li>
    </ul>
    <p v-if="context.progress.reinspection && !context.progress.reinspection.available">
      {{ context.progress.reinspection.reason || '复检尚未开放' }}
    </p>
  </article>
</template>
