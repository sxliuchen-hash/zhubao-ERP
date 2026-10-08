<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useDeskFilter } from './useDeskFilter';
import StatusBadge from '../../components/StatusBadge.vue';
import type { Tone } from '../../data/m1PrototypeData';
import {
  fetchQualityWorkspace,
  type QualityWorkspaceModel,
} from '../../services/businessWorkspaceApi';
import {
  approveLoss,
  dispatchRework,
  freezeQualityStock,
  releaseQualityStock,
  reportRework,
  type LossApprovalResult,
  type QualityReleaseResult,
  type ReworkDispatchResult,
  type ReworkReportResult,
  type QualityFreezeResult,
} from '../../services/businessOperationApi';

const emptyQuality = (): QualityWorkspaceModel => ({
  metrics: [],
  cases: [],
  lossApprovals: [],
});

const workspace = ref<QualityWorkspaceModel>(emptyQuality());
const loadError = ref('');
const freezingQualityStock = ref(false);
const qualityFreezeResult = ref<QualityFreezeResult | null>(null);
const qualityFreezeError = ref('');
const dispatchingRework = ref(false);
const reworkDispatchResult = ref<ReworkDispatchResult | null>(null);
const reworkDispatchError = ref('');
const reportingRework = ref(false);
const reworkReportResult = ref<ReworkReportResult | null>(null);
const reworkReportError = ref('');
const releasingQualityStock = ref(false);
const qualityReleaseResult = ref<QualityReleaseResult | null>(null);
const qualityReleaseError = ref('');
const approvingLoss = ref(false);
const lossApprovalResult = ref<LossApprovalResult | null>(null);
const lossApprovalError = ref('');
const selectedQualityCaseCode = ref('');
const { keyword, filtered: filteredCases } = useDeskFilter(
  () => workspace.value.cases,
  (item) => `${item.code} ${item.workOrder} ${item.process} ${item.inspector} ${item.defect} ${item.status}`,
);

const activeCase = computed(
  () =>
    workspace.value.cases.find((item) => item.code === selectedQualityCaseCode.value) ??
    workspace.value.cases.find((item) => item.status.includes('待') || item.action.includes('返工')) ??
    workspace.value.cases[0],
);

const activeApproval = computed(
  () =>
    workspace.value.lossApprovals.find((item) => item.workOrder === activeCase.value?.workOrder) ??
    workspace.value.lossApprovals.find((item) => item.action.includes('待')) ??
    workspace.value.lossApprovals[0],
);

function qualityCaseTone(status: string, action = ''): Tone {
  if (status.includes('完成')) return 'green';
  if (status.includes('称重')) return 'red';
  if (action.includes('返工')) return 'violet';
  return 'amber';
}

onMounted(() => {
  void loadQualityWorkspace();
});

async function loadQualityWorkspace() {
  loadError.value = '';
  try {
    const loaded = await fetchQualityWorkspace();
    workspace.value = loaded;
    if (loaded.cases.length === 0) {
      loadError.value = '接口没有返回质检单';
    }
  } catch (error) {
    workspace.value = emptyQuality();
    loadError.value = error instanceof Error ? error.message : '质检单加载失败';
  } finally {
    if (!workspace.value.cases.some((item) => item.code === selectedQualityCaseCode.value)) {
      selectedQualityCaseCode.value = activeCase.value?.code ?? '';
    }
  }
}

async function freezeActiveQualityStock() {
  if (!activeCase.value) return;

  freezingQualityStock.value = true;
  qualityFreezeResult.value = null;
  qualityFreezeError.value = '';

  try {
    qualityFreezeResult.value = await freezeQualityStock(activeCase.value);
    await loadQualityWorkspace();
  } catch (error) {
    qualityFreezeError.value = error instanceof Error ? error.message : '冻结异常库存失败';
  } finally {
    freezingQualityStock.value = false;
  }
}

async function dispatchActiveRework() {
  if (!activeCase.value) return;

  dispatchingRework.value = true;
  reworkDispatchResult.value = null;
  reworkDispatchError.value = '';

  try {
    reworkDispatchResult.value = await dispatchRework(activeCase.value);
    await loadQualityWorkspace();
  } catch (error) {
    reworkDispatchError.value = error instanceof Error ? error.message : '生成返工工单失败';
  } finally {
    dispatchingRework.value = false;
  }
}

async function reportActiveRework() {
  if (!activeCase.value) return;

  reportingRework.value = true;
  reworkReportResult.value = null;
  reworkReportError.value = '';

  try {
    reworkReportResult.value = await reportRework(activeCase.value);
    await loadQualityWorkspace();
  } catch (error) {
    reworkReportError.value = error instanceof Error ? error.message : '提交返工报工失败';
  } finally {
    reportingRework.value = false;
  }
}

async function releaseActiveQualityStock() {
  if (!activeCase.value) return;

  releasingQualityStock.value = true;
  qualityReleaseResult.value = null;
  qualityReleaseError.value = '';

  try {
    qualityReleaseResult.value = await releaseQualityStock(activeCase.value);
    await loadQualityWorkspace();
  } catch (error) {
    qualityReleaseError.value = error instanceof Error ? error.message : '复检放行失败';
  } finally {
    releasingQualityStock.value = false;
  }
}

async function approveActiveLoss() {
  if (!activeApproval.value) return;

  approvingLoss.value = true;
  lossApprovalResult.value = null;
  lossApprovalError.value = '';

  try {
    lossApprovalResult.value = await approveLoss(activeApproval.value);
    await loadQualityWorkspace();
  } catch (error) {
    lossApprovalError.value = error instanceof Error ? error.message : '提交损耗审批失败';
  } finally {
    approvingLoss.value = false;
  }
}

function selectQualityCase(code: string) {
  selectedQualityCaseCode.value = code;
  qualityFreezeResult.value = null;
  qualityFreezeError.value = '';
  reworkDispatchResult.value = null;
  reworkDispatchError.value = '';
  reworkReportResult.value = null;
  reworkReportError.value = '';
  qualityReleaseResult.value = null;
  qualityReleaseError.value = '';
  lossApprovalResult.value = null;
  lossApprovalError.value = '';
}
</script>

<template>
  <div class="quality-desk page-stack">
    <section class="module-work-grid quality-work-grid" aria-label="质检返工主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>今日质检池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>扫码</span>
              <input v-model="keyword" placeholder="质检单或工单号" aria-label="质检扫码" />
            </label>
          </div>
        </div>

        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="module-table-wrap refined-table-wrap">
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
                v-for="item in filteredCases"
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
              <tr v-if="filteredCases.length === 0">
                <td colspan="7">暂无质检单</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activeCase" class="module-side-panel">
        <section class="active-object-card tone-violet">
          <div class="panel-title-row">
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
          <div class="detail-actions">
            <button class="primary-button" type="button" :disabled="freezingQualityStock" @click="freezeActiveQualityStock">
              {{ freezingQualityStock ? '正在冻结' : '冻结异常库存' }}
            </button>
            <button class="secondary-button" type="button" :disabled="dispatchingRework" @click="dispatchActiveRework">
              {{ dispatchingRework ? '正在派发' : '生成返工工单' }}
            </button>
            <button class="secondary-button" type="button" :disabled="reportingRework" @click="reportActiveRework">
              {{ reportingRework ? '正在报工' : '提交返工报工' }}
            </button>
            <button class="secondary-button" type="button" :disabled="releasingQualityStock" @click="releaseActiveQualityStock">
              {{ releasingQualityStock ? '正在放行' : '复检放行入库' }}
            </button>
            <button class="secondary-button" type="button" :disabled="approvingLoss" @click="approveActiveLoss">
              {{ approvingLoss ? '正在试算' : '提交损耗审批' }}
            </button>
          </div>
          <div v-if="qualityFreezeResult" class="operation-feedback success">
            <strong>质检冻结已写入台账</strong>
            <span>{{ qualityFreezeResult.ledgerCode }} / {{ qualityFreezeResult.qualityCaseCode }}</span>
          </div>
          <div v-else-if="qualityFreezeError" class="operation-feedback danger">
            <strong>冻结异常库存失败</strong>
            <span>{{ qualityFreezeError }}</span>
          </div>
          <div v-if="reworkDispatchResult" class="operation-feedback success">
            <strong>返工派发已写入台账</strong>
            <span>{{ reworkDispatchResult.ledgerCode }} / {{ reworkDispatchResult.reworkOrderCode }}</span>
          </div>
          <div v-else-if="reworkDispatchError" class="operation-feedback danger">
            <strong>生成返工工单失败</strong>
            <span>{{ reworkDispatchError }}</span>
          </div>
          <div v-if="reworkReportResult" class="operation-feedback success">
            <strong>返工报工已写入台账</strong>
            <span>{{ reworkReportResult.ledgerCode }} / {{ reworkReportResult.reworkOrderCode }}</span>
          </div>
          <div v-else-if="reworkReportError" class="operation-feedback danger">
            <strong>提交返工报工失败</strong>
            <span>{{ reworkReportError }}</span>
          </div>
          <div v-if="qualityReleaseResult" class="operation-feedback success">
            <strong>复检放行已写入台账</strong>
            <span>{{ qualityReleaseResult.ledgerCode }} / {{ qualityReleaseResult.qualityCaseCode }}</span>
          </div>
          <div v-else-if="qualityReleaseError" class="operation-feedback danger">
            <strong>复检放行失败</strong>
            <span>{{ qualityReleaseError }}</span>
          </div>
          <div v-if="lossApprovalResult" class="operation-feedback success">
            <strong>损耗审批已生成工资试算</strong>
            <span>{{ lossApprovalResult.approvalCode }} / {{ lossApprovalResult.approvalStatus }}</span>
            <small>试算 {{ lossApprovalResult.trialWage }}，不进入正式工资</small>
          </div>
          <div v-else-if="lossApprovalError" class="operation-feedback danger">
            <strong>提交损耗审批失败</strong>
            <span>{{ lossApprovalError }}</span>
          </div>
        </section>

      </aside>
    </section>
  </div>
</template>
