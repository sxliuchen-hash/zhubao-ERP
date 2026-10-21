<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  approvalStatusTone,
  type ApprovalDecisionCommand,
  type ApprovalTask,
  type ApprovalStatus,
} from '../../data/approvalTaskData';
import type { Tone } from '../../data/m1PrototypeData';
import { ApiRequestError } from '../../services/apiClient';
import type { AuthSession } from '../../services/authApi';
import {
  fetchApprovalTasks,
  fetchPendingApprovalTasks,
  submitApprovalDecision,
} from '../../services/approvalApi';

const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const tasks = ref<ApprovalTask[]>([]);
const pendingTasks = ref<ApprovalTask[]>([]);
const loading = ref(false);
const loadingError = ref('');
const accessDenied = ref(false);
const decisionSubmitting = ref(false);
const decisionResult = ref<ApprovalTask | null>(null);
const decisionError = ref('');
const decisionComment = ref('确认称重、缺陷照片和业务证据完整，批准本次审批。');
const keyword = ref('');
const detailOpen = ref(false);
const selectedCode = ref('');

const fallbackTask = computed(
  () => pendingTasks.value[0] ?? tasks.value.find((task) => task.status === 'WAITING_APPROVAL') ?? tasks.value[0],
);
const activeTask = computed(
  () => tasks.value.find((task) => task.approvalCode === selectedCode.value) ?? fallbackTask.value,
);
const visibleTasks = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (!text) return tasks.value;
  return tasks.value.filter((task) => `${task.approvalCode} ${task.title} ${task.scene} ${task.businessCode} ${task.applicant}`.toLowerCase().includes(text));
});

const approvalStats = computed(() => [
  {
    label: '待审批',
    value: pendingTasks.value.length,
    unit: '项',
    detail: '轻量审批任务',
    tone: 'amber' as Tone,
  },
  {
    label: '已锁定',
    value: tasks.value.filter((task) => task.locked).length,
    unit: '项',
    detail: '处理后不可直接改',
    tone: 'green' as Tone,
  },
  {
    label: '高风险',
    value: tasks.value.filter((task) => task.severity === 'red').length,
    unit: '项',
    detail: '超标损耗或证据缺口',
    tone: 'red' as Tone,
  },
  {
    label: '正式工资',
    value: tasks.value.filter((task) => task.formalPayroll).length,
    unit: '项',
    detail: '一期必须为 0',
    tone: 'blue' as Tone,
  },
]);

const approvalFlow = [
  { code: 'REQ', name: '业务触发', status: '损耗/异常生成任务', tone: 'blue' },
  { code: 'EVD', name: '证据校验', status: '称重/照片/附件', tone: 'amber' },
  { code: 'DEC', name: '审批决定', status: '通过或驳回', tone: 'green' },
  { code: 'LOCK', name: '快照锁定', status: '后续变更走新任务', tone: 'red' },
  { code: 'AUDIT', name: '审计留痕', status: 'JDBC 可持久化', tone: 'violet' },
] as const;

onMounted(async () => {
  await reloadApprovals();
});

function denied(error: unknown) {
  return error instanceof ApiRequestError && (error.status === 403 || error.code === 40301);
}

async function reloadApprovals() {
  loading.value = true;
  loadingError.value = '';
  accessDenied.value = false;

  try {
    const [all, pending] = await Promise.all([fetchApprovalTasks(), fetchPendingApprovalTasks()]);
    tasks.value = all;
    pendingTasks.value = pending;
    if (all.length === 0 && pending.length === 0) {
      loadingError.value = '接口没有返回审批任务';
    }
  } catch (error) {
    tasks.value = [];
    pendingTasks.value = [];
    accessDenied.value = denied(error);
    loadingError.value = error instanceof Error ? error.message : '审批任务加载失败';
  } finally {
    loading.value = false;
    if (!tasks.value.some((task) => task.approvalCode === selectedCode.value)) {
      selectedCode.value = fallbackTask.value?.approvalCode ?? '';
    }
  }
}

async function decideActiveTask(decision: ApprovalDecisionCommand['decision']) {
  if (!activeTask.value) return;

  decisionSubmitting.value = true;
  decisionResult.value = null;
  decisionError.value = '';

  const command: ApprovalDecisionCommand = {
    decision,
    operator: authSession.value?.operator || authSession.value?.username || '',
    comment: decision === 'APPROVE' ? decisionComment.value : '证据不完整，驳回并要求补充称重/照片。',
    evidenceCode: activeTask.value.evidenceCode,
  };

  try {
    const result = await submitApprovalDecision(activeTask.value.approvalCode, command);
    decisionResult.value = result;
    tasks.value = tasks.value.map((task) => (task.approvalCode === result.approvalCode ? result : task));
    pendingTasks.value = pendingTasks.value.filter((task) => task.approvalCode !== result.approvalCode);
  } catch (error) {
    decisionError.value = error instanceof Error ? error.message : '审批提交失败';
  } finally {
    decisionSubmitting.value = false;
  }
}

function statusText(status: ApprovalStatus): string {
  const map: Record<ApprovalStatus, string> = {
    WAITING_APPROVAL: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    AUTO_APPROVED: '自动通过',
  };
  return map[status];
}

function selectTask(code: string) {
  selectedCode.value = code;
  detailOpen.value = true;
  decisionResult.value = null;
  decisionError.value = '';
}

function backToList() {
  detailOpen.value = false;
}

function taskTone(task: ApprovalTask): Tone {
  if (task.status === 'WAITING_APPROVAL') return task.severity;
  return approvalStatusTone(task.status);
}
</script>

<template>
  <div class="approval-workspace page-stack desk-page" :class="{ 'is-detail': detailOpen }">
    <section class="module-command-surface approval-command-surface" aria-label="轻量审批工作台">
      <div class="module-command-main">
        <div class="console-title-line">
          <StatusBadge label="业务审批 / 非正式工资" tone="amber" />
        </div>
        <div class="module-command-title">
          <h2>轻量审批工作台</h2>
          <div class="module-date-card">
            <strong>{{ authSession?.operator || '当前登录人' }}</strong>
            <span>损耗 / 异常 / 证据</span>
          </div>
        </div>

        <div class="module-stat-grid">
          <article v-for="item in approvalStats" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>

        <details class="production-note">
          <summary>审批处理链路</summary>
          <div class="module-flow-strip" aria-label="审批处理链路">
            <article v-for="step in approvalFlow" :key="step.code" :class="`tone-${step.tone}`">
              <b>{{ step.code }}</b>
              <div>
                <strong>{{ step.name }}</strong>
                <span>{{ step.status }}</span>
              </div>
            </article>
          </div>
        </details>
      </div>

      <aside class="module-priority-panel">
        <div>
          <strong>审批阻断队列</strong>
        </div>
        <article v-for="task in pendingTasks.slice(0, 4)" :key="task.approvalCode" :class="`tone-${taskTone(task)}`">
          <StatusBadge :label="statusText(task.status)" :tone="taskTone(task)" />
          <div>
            <strong>{{ task.approvalCode }}</strong>
            <span>{{ task.title }}</span>
            <small>{{ task.approver }} / {{ task.evidenceCode }}</small>
          </div>
        </article>
      </aside>
    </section>

    <section class="module-work-grid approval-work-grid" aria-label="审批任务主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>审批任务池</h2>
          </div>
          <div class="module-tools">
            <button class="secondary-button" type="button" :disabled="loading" @click="reloadApprovals">
              {{ loading ? '刷新中' : '刷新任务' }}
            </button>
            <button class="primary-button" type="button" :disabled="decisionSubmitting || !activeTask" @click="decideActiveTask('APPROVE')">
              {{ decisionSubmitting ? '提交中' : '审批通过' }}
            </button>
          </div>
        </div>
        <div class="module-tools production-filters">
          <label>
            <span>查找</span>
            <input v-model="keyword" placeholder="审批单、标题或业务单" aria-label="审批查找" />
          </label>
        </div>

        <p v-if="loading" class="empty-hint">正在加载审批</p>
        <p v-if="accessDenied" class="operation-feedback danger">无权限</p>
        <p v-if="loadingError" class="operation-feedback danger">{{ loadingError }}</p>
        <div class="module-table-wrap refined-table-wrap desk-table-wrap">
          <table class="module-table approval-task-table">
            <thead>
              <tr>
                <th>审批单</th>
                <th>场景/业务单</th>
                <th>申请/审批</th>
                <th>证据</th>
                <th>状态</th>
                <th>工资边界</th>
                <th>锁定</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="task in visibleTasks"
                :key="task.approvalCode"
                :class="{ 'is-selected': activeTask?.approvalCode === task.approvalCode }"
                :aria-selected="activeTask?.approvalCode === task.approvalCode"
                @click="selectTask(task.approvalCode)"
              >
                <td><strong>{{ task.approvalCode }}</strong></td>
                <td>
                  <span class="cell-stack">
                    <b>{{ task.scene }}</b>
                    <small>{{ task.businessCode }}</small>
                  </span>
                </td>
                <td>
                  <span class="cell-stack">
                    <b>{{ task.applicant }}</b>
                    <small>{{ task.approver }}</small>
                  </span>
                </td>
                <td>{{ task.evidenceCode }}</td>
                <td><StatusBadge :label="statusText(task.status)" :tone="taskTone(task)" /></td>
                <td>{{ task.formalPayroll ? '正式工资' : '仅试算快照' }}</td>
                <td>{{ task.locked ? '已锁定' : '待处理' }}</td>
              </tr>
              <tr v-if="!loading && visibleTasks.length === 0">
                <td colspan="7">暂无审批任务</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="desk-cards">
          <p v-if="!loading && visibleTasks.length === 0" class="empty-hint">暂无审批任务</p>
          <button
            v-for="task in visibleTasks"
            :key="task.approvalCode"
            class="desk-card"
            type="button"
            @click="selectTask(task.approvalCode)"
          >
            <span class="desk-card-top">
              <strong class="code">{{ task.approvalCode }}</strong>
              <StatusBadge :label="statusText(task.status)" :tone="taskTone(task)" />
            </span>
            <span>{{ task.title }}</span>
            <span>{{ task.scene }} · {{ task.businessCode }}</span>
          </button>
        </div>
      </div>

      <aside v-if="activeTask" class="module-side-panel">
        <section class="active-object-card tone-amber">
          <div class="panel-title-row">
            <button class="secondary-button detail-back" type="button" @click="backToList">返回列表</button>
            <StatusBadge :label="statusText(activeTask.status)" :tone="taskTone(activeTask)" />
          </div>
          <strong>{{ activeTask.approvalCode }}</strong>
          <dl class="detail-grid compact-detail-grid">
            <dt>标题</dt>
            <dd>{{ activeTask.title }}</dd>
            <dt>业务单</dt>
            <dd>{{ activeTask.businessCode }}</dd>
            <dt>申请人</dt>
            <dd>{{ activeTask.applicant }}</dd>
            <dt>审批人</dt>
            <dd>{{ activeTask.approver }}</dd>
            <dt>证据</dt>
            <dd>{{ activeTask.evidenceCode }}</dd>
            <dt>工资</dt>
            <dd>{{ activeTask.formalPayroll ? '正式工资' : '仅工资试算，不发放' }}</dd>
          </dl>
          <label class="approval-comment-field">
            <span>审批意见</span>
            <textarea v-model="decisionComment" rows="3" />
          </label>
          <div class="detail-actions">
            <button class="primary-button" type="button" :disabled="decisionSubmitting || activeTask.status !== 'WAITING_APPROVAL'" @click="decideActiveTask('APPROVE')">
              {{ decisionSubmitting ? '提交中' : '审批通过' }}
            </button>
            <button class="secondary-button" type="button" :disabled="decisionSubmitting || activeTask.status !== 'WAITING_APPROVAL'" @click="decideActiveTask('REJECT')">
              驳回补证
            </button>
          </div>
          <div v-if="decisionResult" class="operation-feedback success">
            <strong>审批任务已锁定</strong>
            <span>{{ decisionResult.approvalCode }} / {{ statusText(decisionResult.status) }}</span>
            <small>{{ decisionResult.decisionOperator }} / {{ decisionResult.decisionComment }}</small>
          </div>
          <div v-else-if="decisionError" class="operation-feedback danger">
            <strong>审批提交失败</strong>
            <span>{{ decisionError }}</span>
            <small>请确认后端已有该审批任务。</small>
          </div>
        </section>
      </aside>

      <section v-if="activeTask" class="evidence-slot-panel">
        <div class="panel-title-row">
          <StatusBadge label="审批快照" tone="blue" />
        </div>
        <div class="rule-stack">
          <article>
            <strong>来源摘要</strong>
            <span>{{ activeTask.sourceSummary }}</span>
          </article>
          <article>
            <strong>锁定策略</strong>
            <span>处理后锁定，后续变更必须新建任务或审计事件。</span>
          </article>
          <article>
            <strong>当前边界</strong>
            <span>账号操作在用户管理页由管理员直接完成。此处保留业务审批与历史任务。</span>
          </article>
        </div>
      </section>
    </section>
  </div>
</template>
