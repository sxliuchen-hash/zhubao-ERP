<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import OperationInputs from './OperationInputs.vue';
import QualityPanel from '../quality/QualityPanel.vue';
import { createAmountForm } from './operationAmounts';
import { operationDraft } from './operationDraft';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import type { LossApproval, Tone } from '../../data/m1PrototypeData';
import { ApiRequestError } from '../../services/apiClient';
import {
  fetchQualityWorkspace,
  type QualityWorkspaceModel,
} from '../../services/businessWorkspaceApi';
import {
  approveLoss,
  type LossApprovalResult,
} from '../../services/businessOperationApi';

const emptyQuality = (): QualityWorkspaceModel => ({
  metrics: [],
  cases: [],
  lossApprovals: [],
});

const workspace = ref<QualityWorkspaceModel>(emptyQuality());
const loadError = ref('');
const accessDenied = ref(false);
const loading = ref(false);
const statusKeyword = ref('');
const advancedOpen = ref(false);
const detailOpen = ref(false);
const { operationValues, resetAmounts } = createAmountForm();
const approvingLoss = ref(false);
const lossApprovalResult = ref<LossApprovalResult | null>(null);
const lossApprovalError = ref('');
const selectedQualityCaseCode = ref('');
const { keyword, filtered: filteredCases } = useDeskFilter(
  () => workspace.value.cases,
  (item) => `${item.code} ${item.workOrder} ${item.process} ${item.inspector} ${item.defect} ${item.status}`,
);
const visibleCases = computed(() => {
  const status = statusKeyword.value.trim();
  return filteredCases.value.filter((item) => !status || item.status.includes(status));
});

const activeCase = computed(
  () =>
    workspace.value.cases.find((item) => item.code === selectedQualityCaseCode.value) ??
    workspace.value.cases.find((item) => item.status.includes('待') || item.action.includes('返工')) ??
    workspace.value.cases[0],
);

const activeApproval = computed(
  () =>
    workspace.value.lossApprovals.find((item) => item.workOrder === activeCase.value?.workOrder),
);

watch(activeCase, (item) => { resetAmounts(item?.operationContext); });

function qualityCaseTone(status: string, action = ''): Tone {
  if (status.includes('完成')) return 'green';
  if (status.includes('称重')) return 'red';
  if (action.includes('返工')) return 'violet';
  return 'amber';
}

onMounted(() => {
  void loadQualityWorkspace();
});

function denied(error: unknown) {
  return error instanceof ApiRequestError && (error.status === 403 || error.code === 40301);
}

async function loadQualityWorkspace() {
  loadError.value = '';
  accessDenied.value = false;
  loading.value = true;
  try {
    const loaded = await fetchQualityWorkspace();
    workspace.value = loaded;
    if (loaded.cases.length === 0) {
      loadError.value = '接口没有返回质检单';
    }
  } catch (error) {
    workspace.value = emptyQuality();
    accessDenied.value = denied(error);
    loadError.value = error instanceof Error ? error.message : '质检单加载失败';
  } finally {
    loading.value = false;
    if (!workspace.value.cases.some((item) => item.code === selectedQualityCaseCode.value)) {
      selectedQualityCaseCode.value = activeCase.value?.code ?? '';
    }
  }
}

async function approveActiveLoss() {
  if (!activeCase.value) return;

  approvingLoss.value = true;
  lossApprovalResult.value = null;
  lossApprovalError.value = '';

  try {
    const draft = operationDraft(operationValues.value);
    if (!operationValues.value.approvalCode?.trim()) throw new Error('请填写损耗申请单号');
    const application: LossApproval = {
      code: operationValues.value.approvalCode.trim(), workOrder: activeCase.value.workOrder,
      process: activeCase.value.process, operationContext: activeCase.value.operationContext,
      standard: '', actual: '', evidence: '', action: '待申请', tone: 'amber',
    };
    lossApprovalResult.value = await approveLoss(application, draft);
    await loadQualityWorkspace();
  } catch (error) {
    lossApprovalError.value = error instanceof Error ? error.message : '提交损耗申请失败';
  } finally {
    approvingLoss.value = false;
  }
}

function backToList() {
  detailOpen.value = false;
}

function selectQualityCase(code: string) {
  selectedQualityCaseCode.value = code;
  detailOpen.value = true;
  lossApprovalResult.value = null;
  lossApprovalError.value = '';
}
</script>

<template>
  <div class="quality-desk page-stack desk-page" :class="{ 'is-detail': detailOpen }">
    <p v-if="workspace.dataSource === 'DEMO'" class="operation-feedback warning">当前显示评审样例，仅用于演示。</p>
    <QualityPanel
      title="对照加工要求"
      intro="扫子单贴纸。不拆的母单扫母单贴纸。质检员是当前登录账号。超时用原确认回放，不会另开一次质检。"
    />

    <section class="module-work-grid quality-work-grid" aria-label="质检返工主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>今日质检池</h2>
          </div>
        </div>
        <div class="module-tools production-filters">
          <label>
            <span>扫码</span>
            <input v-model="keyword" placeholder="质检单或工单号" aria-label="质检扫码" />
          </label>
          <button class="secondary-button" type="button" :aria-expanded="advancedOpen ? 'true' : 'false'" @click="advancedOpen = !advancedOpen">
            {{ advancedOpen ? '收起筛选' : '更多筛选' }}
          </button>
          <label v-if="advancedOpen">
            <span>状态</span>
            <input v-model="statusKeyword" placeholder="如 待质检" aria-label="质检状态筛选" />
          </label>
        </div>

        <p v-if="loading" class="empty-hint">正在加载质检</p>
        <p v-if="accessDenied" class="operation-feedback danger">无权限</p>
        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="module-table-wrap refined-table-wrap desk-table-wrap">
          <table class="module-table quality-dispatch-table">
            <thead>
              <tr>
                <th>质检单</th>
                <th>工单/工序</th>
                <th>质检员</th>
                <th>缺陷</th>
                <th>处理</th>
                <th>状态</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in visibleCases"
                :key="item.code"
                :class="{ 'is-selected': activeCase?.code === item.code }"
                :aria-selected="activeCase?.code === item.code"
                @click="selectQualityCase(item.code)"
              >
                <td><strong>{{ item.code }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ item.workOrder }}</b>
                    <small>{{ item.process }}</small>
                  </span>
                </td>
                <td>{{ item.inspector }}</td>
                <td>{{ item.defect }}</td>
                <td>{{ item.action }}</td>
                <td><StatusBadge :label="item.status" :tone="qualityCaseTone(item.status, item.action)" /></td>
                <td><button class="table-action" type="button" @click.stop="selectQualityCase(item.code)">处理</button></td>
              </tr>
              <tr v-if="!loading && visibleCases.length === 0">
                <td colspan="7">暂无质检单</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="desk-cards">
          <p v-if="!loading && visibleCases.length === 0" class="empty-hint">暂无质检单</p>
          <button
            v-for="item in visibleCases"
            :key="item.code"
            class="desk-card"
            type="button"
            @click="selectQualityCase(item.code)"
          >
            <span class="desk-card-top">
              <strong class="code">{{ item.code }}</strong>
              <StatusBadge :label="item.status" :tone="qualityCaseTone(item.status, item.action)" />
            </span>
            <span>{{ item.workOrder }} · {{ item.process }}</span>
            <span>{{ item.defect }} · {{ item.action }}</span>
          </button>
        </div>
      </div>

      <aside v-if="activeCase" class="module-side-panel">
        <section class="active-object-card tone-violet">
          <div class="panel-title-row">
            <button class="secondary-button detail-back" type="button" @click="backToList">返回列表</button>
            <StatusBadge :label="activeCase.status" :tone="qualityCaseTone(activeCase.status, activeCase.action)" />
          </div>
          <strong>{{ activeCase.code }}</strong>
          <dl class="detail-grid compact-detail-grid">
            <dt>工单</dt>
            <dd>{{ activeCase.workOrder }}</dd>
            <dt>工序</dt>
            <dd>{{ activeCase.process }}</dd>
            <dt>缺陷</dt>
            <dd>{{ activeCase.defect }}</dd>
            <dt>损耗审批</dt>
            <dd>{{ activeApproval?.code ?? '无' }}</dd>
          </dl>
          <p class="operation-feedback">冻结、返工、复检和放行走上方质检面板。这里只提交损耗工资试算。</p>
          <OperationInputs v-model="operationValues" :fields="[['approvalCode', '损耗申请单号'], ['operator', '损耗报工人'], ['process', '损耗所属工序'], ['inputWeight', '投入重量（克）', 'number'], ['outputWeight', '产出重量（克）', 'number'], ['reasonableTransferWeight', '合理转移重量（克）', 'number'], ['standardLossRatePercent', '标准损耗率（%）', 'number'], ['acceptedQuantity', '验收数量', 'number'], ['pieceRate', '计件单价', 'number'], ['lossDeductionUnitPrice', '损耗扣减单价', 'number']]" />
          <div class="detail-actions">
            <button class="secondary-button" type="button" :disabled="approvingLoss" @click="approveActiveLoss">
              {{ approvingLoss ? '正在试算' : '提交损耗申请' }}
            </button>
          </div>
          <div v-if="lossApprovalResult" class="operation-feedback success">
            <strong>损耗申请已生成工资试算</strong>
            <span>{{ lossApprovalResult.approvalCode }} / {{ lossApprovalResult.approvalStatus }}</span>
            <small>试算 {{ lossApprovalResult.trialWage }}，不进入正式工资</small>
          </div>
          <div v-else-if="lossApprovalError" class="operation-feedback danger">
            <strong>提交损耗申请失败</strong>
            <span>{{ lossApprovalError }}</span>
          </div>
        </section>

      </aside>
    </section>
  </div>
</template>
