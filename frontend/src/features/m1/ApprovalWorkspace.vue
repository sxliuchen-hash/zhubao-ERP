<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue';
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
  fetchApprovalTask,
  fetchApprovalTasks,
  fetchPendingApprovalTasks,
  submitApprovalDecision,
} from '../../services/approvalApi';

import { useWorkspaceContext } from '../workspace/useWorkspaceContext';
import { approvalDecisionReason } from '../approvals/approvalDecisionAccess';

const workspace = useWorkspaceContext();
const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const requestedCode = inject<Ref<string>>('zhubao-approval-code', ref(''));
const tasks = ref<ApprovalTask[]>([]);
const pendingTasks = ref<ApprovalTask[]>([]);
const loading = ref(false);
const loadingError = ref('');
const accessDenied = ref(false);
const keyword = ref('');
const detailOpen = ref(false);
const selectedCode = ref('');
const decisionSubmitting = computed(() => Object.values(workspace.approvalDecisions.value).some(item => item.outcome === 'submitting'));
const decisionSession = computed(() => workspace.approvalDecisions.value[selectedCode.value]);
const decisionResult = computed(() => decisionSession.value?.result ?? null);
const decisionError = computed(() => decisionSession.value?.error ?? '');
const decisionComment = computed({ get: () => workspace.approvalComments.value[selectedCode.value] ?? '', set: value => { if (selectedCode.value) workspace.approvalComments.value[selectedCode.value] = value; } });
const activeTask = computed(() => tasks.value.find(task => task.approvalCode === selectedCode.value));
const decisionBlocked = computed(() => activeTask.value ? approvalDecisionReason(activeTask.value, authSession.value) : '请先选择审批任务');
const decisionLocked = computed(() => loading.value || accessDenied.value || Boolean(loadingError.value) || decisionSubmitting.value || ['unknown', 'succeeded', 'confirmed'].includes(decisionSession.value?.outcome ?? ''));
const checking = ref('');
const decisionHint = ref('');
const unresolved = computed(() => Object.values(workspace.approvalDecisions.value).filter(item => ['submitting', 'unknown'].includes(item.outcome)));
const completedDecisions = computed(() => Object.values(workspace.approvalDecisions.value).filter(item => item.result));
let generation = 0;
let disposed = false;
onBeforeUnmount(() => { disposed = true; ++generation; });
watch(decisionSubmitting, (busy, previous) => { if (!busy && previous) void reloadApprovals(); });
watch(requestedCode, code => { if (decisionSubmitting.value) return; selectedCode.value = code; detailOpen.value = Boolean(code); decisionHint.value = ''; }, { immediate: true });
const visibleTasks = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (!text) return tasks.value;
  return tasks.value.filter((task) => `${task.approvalCode} ${task.title} ${task.scene} ${task.businessCode} ${task.applicant}`.toLowerCase().includes(text));
});
watch(keyword, () => {
  if (!decisionSubmitting.value && selectedCode.value && !visibleTasks.value.some(task => task.approvalCode === selectedCode.value)) { selectedCode.value = ''; detailOpen.value = false; }
}, { flush: 'sync' });
const requestedMissing = computed(() => !loading.value && Boolean(selectedCode.value) && !activeTask.value);

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
    detail: '审批不执行工资发放',
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
  if (decisionSubmitting.value) return;
  const token = ++generation;
  loading.value = true; loadingError.value = ''; accessDenied.value = false;
  try {
    const [all, pending] = await Promise.all([fetchApprovalTasks(), fetchPendingApprovalTasks()]);
    if (disposed || token !== generation) return;
    tasks.value = all.map(task => {
      const receipt = workspace.approvalDecisions.value[task.approvalCode]?.result;
      return receipt && receipt.status !== 'WAITING_APPROVAL' && task.status === 'WAITING_APPROVAL' ? receipt : task;
    });
    pendingTasks.value = pending.filter(task => !workspace.approvalDecisions.value[task.approvalCode]?.result);
  } catch (error) {
    if (disposed || token !== generation) return;
    tasks.value = []; pendingTasks.value = []; accessDenied.value = denied(error);
    loadingError.value = error instanceof Error ? error.message : '审批任务加载失败';
  } finally { if (!disposed && token === generation) loading.value = false; }
}
function updateTask(result: ApprovalTask) {
  tasks.value = tasks.value.map(task => task.approvalCode === result.approvalCode ? result : task);
  if (result.status !== 'WAITING_APPROVAL') pendingTasks.value = pendingTasks.value.filter(task => task.approvalCode !== result.approvalCode);
}
async function decideActiveTask(decision: ApprovalDecisionCommand['decision']) {
  const task = activeTask.value;
  if (!task || decisionLocked.value || decisionBlocked.value) return;
  if (!decisionComment.value.trim()) { decisionHint.value = '请填写本次实际审批意见'; return; }
  const code = task.approvalCode;
  const command: ApprovalDecisionCommand = { decision, operator: authSession.value?.operator || '', comment: decisionComment.value.trim(), evidenceCode: task.evidenceCode };
  workspace.approvalDecisions.value[code] = { task: { ...task }, command, outcome: 'submitting', result: null, error: '' };
  const entry = workspace.approvalDecisions.value[code];
  ++generation; loading.value = false; decisionHint.value = '';
  try {
    const result = await submitApprovalDecision(code, command);
    if (workspace.approvalDecisions.value[code] !== entry) return;
    if (result.approvalCode !== code || result.scene !== task.scene || result.businessCode !== task.businessCode || result.status !== (decision === 'APPROVE' ? 'APPROVED' : 'REJECTED')) throw new Error('审批回执与原任务或决定不一致，请保留原操作核对');
    entry.outcome = 'succeeded'; entry.result = result;
    if (!disposed) updateTask(result);
  } catch (error) {
    if (workspace.approvalDecisions.value[code] !== entry) return;
    const known = error instanceof ApiRequestError && error.status >= 400 && error.status < 500 && error.status !== 408;
    entry.outcome = known ? 'rejected' : 'unknown'; entry.error = error instanceof Error ? error.message : '审批提交失败';
  }
}
async function checkDecision(code: string) {
  const entry = workspace.approvalDecisions.value[code];
  if (!entry || entry.outcome !== 'unknown' || checking.value) return;
  checking.value = code;
  try {
    const result = await fetchApprovalTask(code);
    if (workspace.approvalDecisions.value[code] !== entry) return;
    if (result.approvalCode !== code || result.scene !== entry.task.scene || result.businessCode !== entry.task.businessCode) throw new Error('核对响应与原审批任务不一致');
    if (result.status === 'WAITING_APPROVAL') { entry.error = '原任务仍待审批，结果尚不能确认，请稍后再次只读核对'; return; }
    entry.outcome = 'confirmed'; entry.result = result; entry.error = '';
    if (!disposed) updateTask(result);
  } catch (error) { if (workspace.approvalDecisions.value[code] === entry) entry.error = error instanceof Error ? error.message : '原审批核对失败'; }
  finally { checking.value = ''; }
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
  if (decisionSubmitting.value) return;
  selectedCode.value = code;
  decisionHint.value = '';
  detailOpen.value = true;
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
    <p v-if="requestedMissing" class="operation-feedback danger" role="alert">指定审批不在当前可见列表中，请重新选择；没有改为另一条审批。</p>
    <section v-if="unresolved.length" class="approval-unresolved" aria-label="原审批请求核对">
      <article v-for="entry in unresolved" :key="entry.task.approvalCode" class="operation-feedback warning">
        <strong>{{ entry.task.approvalCode }} · {{ entry.outcome === 'submitting' ? '原决定提交中' : '原决定结果待核对' }}</strong>
        <p>{{ entry.command.decision === 'APPROVE' ? '通过' : '驳回' }} · {{ entry.command.comment }}</p><p>{{ entry.error }}</p>
        <button v-if="entry.outcome === 'unknown'" class="secondary-button" type="button" :disabled="Boolean(checking)" @click="checkDecision(entry.task.approvalCode)">只读核对审批结果</button>
      </article>
    </section>
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

    <details v-if="completedDecisions.length" class="approval-unresolved" aria-label="本次审批回执"><summary>本次审批回执</summary><article v-for="entry in completedDecisions" :key="entry.task.approvalCode"><strong>{{ entry.result?.approvalCode }} · {{ entry.result && statusText(entry.result.status) }}</strong><p>{{ entry.outcome === 'confirmed' ? '只读核对原任务当前结果' : '原决定真实回执' }} · {{ entry.result?.decisionOperator }} · {{ entry.result?.decisionComment }}</p></article></details>
    <section class="module-work-grid approval-work-grid" aria-label="审批任务主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <h2>审批任务池</h2>
          </div>
          <div class="module-tools">
            <button class="secondary-button" type="button" :disabled="loading || decisionSubmitting" @click="reloadApprovals">
              {{ loading ? '刷新中' : '刷新任务' }}
            </button>
          </div>
        </div>
        <div class="module-tools production-filters">
          <label>
            <span>查找</span>
            <input v-model="keyword" placeholder="审批单、标题或业务单" aria-label="审批查找" :disabled="decisionSubmitting" />
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
                <td><button class="table-action" type="button" :disabled="decisionSubmitting" :aria-label="'查看审批 ' + task.approvalCode" @click.stop="selectTask(task.approvalCode)">{{ task.approvalCode }}</button></td>
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
            :disabled="decisionSubmitting"
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

      <aside v-if="activeTask && detailOpen" class="module-side-panel">
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
            <textarea v-model="decisionComment" rows="3" aria-label="本次审批意见" :disabled="decisionLocked || Boolean(decisionBlocked)" placeholder="核对本任务后，填写通过或驳回原因" />
          </label>
          <p v-if="decisionBlocked" class="operation-feedback warning">{{ decisionBlocked }}</p><p v-if="decisionHint" class="operation-feedback warning">{{ decisionHint }}</p>
          <div class="detail-actions">
            <button class="primary-button" type="button" :disabled="decisionLocked || Boolean(decisionBlocked)" @click="decideActiveTask('APPROVE')">
              {{ decisionSubmitting ? '提交中' : '审批通过' }}
            </button>
            <button class="secondary-button" type="button" :disabled="decisionLocked || Boolean(decisionBlocked)" @click="decideActiveTask('REJECT')">
              驳回补证
            </button>
          </div>
          <div v-if="decisionResult" class="operation-feedback success">
            <strong>{{ decisionSession?.outcome === 'confirmed' ? '原审批已处理，请核对实际决定人' : '审批决定已返回' }}</strong>
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

      <section v-if="activeTask && detailOpen" class="evidence-slot-panel">
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


<style scoped>
.approval-unresolved { display: grid; gap: 10px; overflow-wrap: anywhere; }
.approval-workspace { min-width: 0; }
.approval-work-grid > * { min-width: 0; overflow-wrap: anywhere; }
.approval-comment-field textarea { width: 100%; box-sizing: border-box; min-width: 0; }
@media (max-width: 900px) { .approval-command-surface { display: none; } .approval-workspace button { min-height: 44px; } .approval-comment-field textarea { font-size: 16px; } }
</style>
