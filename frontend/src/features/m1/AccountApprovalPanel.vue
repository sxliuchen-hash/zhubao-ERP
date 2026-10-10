<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  fetchAuthAccountPreApprovalApplications, fetchAuthAccountPreApprovalReadiness,
  submitAuthAccountPreApprovalApplication, uploadAuthAccountPreApprovalEvidence,
  type AuthAccountPreApprovalApplication, type AuthAccountPreApprovalApplicationRequest,
  type AuthAccountPreApprovalReadiness,
} from '../../services/authApi';
import { fetchApprovalTasks, submitApprovalDecision } from '../../services/approvalApi';
import type { ApprovalTask } from '../../data/approvalTaskData';

const props = defineProps<{ request: AuthAccountPreApprovalApplicationRequest | null; operator: string; executing?: boolean; executedApplicationCode?: string }>();
const emit = defineEmits<{ execute: [application: AuthAccountPreApprovalApplication] }>();
const applications = ref<AuthAccountPreApprovalApplication[]>([]);
const tasks = ref<ApprovalTask[]>([]);
const readiness = ref<AuthAccountPreApprovalReadiness[]>([]);
const reason = ref('');
const evidence = ref<string[]>([]);
const comment = ref('');
const message = ref('');
const busy = ref(false);
const actionCodes: Record<string, string> = {
  CREATE_ACCOUNT: 'AUTH-PRE-CREATE-001', CHANGE_ROLE_SCOPE: 'AUTH-PRE-ROLE-001',
  RESET_PASSWORD: 'AUTH-PRE-PASSWORD-001', CHANGE_STATUS: 'AUTH-PRE-STATUS-001',
};
const statusActions: Record<string, string> = { ENABLE: '启用账号', DISABLE: '停用账号', UNLOCK: '解锁账号' };
const currentReadiness = computed(() => readiness.value.find(item => item.readinessCode === actionCodes[props.request?.actionType ?? '']));
watch(() => props.request, () => { reason.value = ''; evidence.value = []; message.value = ''; });
watch(() => props.executedApplicationCode, code => { if (code) void refresh(); });

async function run(action: () => Promise<void>) {
  busy.value = true;
  message.value = '';
  try { await action(); } catch (error) {
    message.value = error instanceof Error ? error.message : '账号审批操作失败';
  } finally { busy.value = false; }
}
async function refresh() {
  await run(async () => {
    const [loadedApplications, loadedTasks, loadedReadiness] = await Promise.all([
      fetchAuthAccountPreApprovalApplications(), fetchApprovalTasks(), fetchAuthAccountPreApprovalReadiness(),
    ]);
    applications.value = loadedApplications;
    tasks.value = loadedTasks;
    readiness.value = loadedReadiness;
  });
}
onMounted(refresh);
function task(application: AuthAccountPreApprovalApplication) {
  return tasks.value.find(item => item.approvalCode === application.approvalTaskCode);
}
function canExecute(application: AuthAccountPreApprovalApplication) {
  const approval = task(application);
  return application.applicationStatus !== 'EXECUTED' && application.applicationCode !== props.executedApplicationCode
    && application.readyForBlockingApproval && approval?.status === 'APPROVED'
    && approval.scene === 'ACCOUNT_PRE_APPROVAL' && approval.businessCode === application.applicationCode;
}
function statusLabel(application: AuthAccountPreApprovalApplication) {
  if (application.applicationStatus === 'EXECUTED' || application.applicationCode === props.executedApplicationCode) return '已执行';
  const status = task(application)?.status;
  if (status === 'APPROVED') return '已批准';
  if (status === 'REJECTED') return '已驳回';
  if (status === 'WAITING_APPROVAL') return '待审批';
  return '尚未生成审批任务';
}
async function submit() {
  await run(async () => {
    if (!props.request) throw new Error('请先在账号表单选择要申请的操作');
    if (!reason.value.trim()) throw new Error('请填写申请原因');
    const application = await submitAuthAccountPreApprovalApplication({
      ...props.request, reason: reason.value.trim(), providedEvidence: [...evidence.value],
    });
    applications.value = [application, ...applications.value];
    tasks.value = await fetchApprovalTasks();
    message.value = `申请已提交：${application.applicationCode}，等待批准后执行`;
  });
}
async function decide(application: AuthAccountPreApprovalApplication, decision: 'APPROVE' | 'REJECT') {
  await run(async () => {
    if (!props.operator) throw new Error('请先登录');
    if (!comment.value.trim()) throw new Error('请填写审批意见');
    const result = await submitApprovalDecision(application.approvalTaskCode, {
      decision, operator: props.operator, comment: comment.value.trim(),
    });
    tasks.value = [...tasks.value.filter(item => item.approvalCode !== result.approvalCode), result];
    message.value = decision === 'APPROVE' ? '已批准，请执行账号操作' : '已驳回，账号未变更';
  });
}
async function upload(application: AuthAccountPreApprovalApplication, event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  await run(async () => {
    const result = await uploadAuthAccountPreApprovalEvidence(application.applicationCode, file);
    message.value = `已关联 ${result.attachmentCount} 个审批附件`;
  });
  input.value = '';
}
</script>

<template>
  <section class="section-block" aria-label="账号申请与审批">
    <div class="section-heading compact-heading">
      <div><h2>账号申请与审批</h2><p>先选择账号操作，再提交申请、批准并执行。批准本身不会修改账号。</p></div>
      <button class="secondary-button" type="button" :disabled="busy" @click="refresh">刷新审批状态</button>
    </div>
    <form v-if="request" aria-label="账号操作申请" @submit.prevent="submit">
      <p>{{ request.targetUsername }} · {{ statusActions[request.statusAction || ''] || currentReadiness?.actionType || request.actionType }}</p>
      <p v-if="request.targetAllowedProcesses !== undefined">岗位工序：{{ request.targetAllowedProcesses.join('、') || '无' }}</p>
      <label>申请原因<input v-model="reason" aria-label="账号申请原因" required /></label>
      <fieldset v-if="currentReadiness">
        <legend>确认已经提供的材料</legend>
        <label v-for="item in currentReadiness.requiredBeforeAction" :key="item">
          <input v-model="evidence" type="checkbox" :value="item" />{{ item }}
        </label>
      </fieldset>
      <button class="primary-button" type="submit" :disabled="busy">提交账号申请</button>
    </form>
    <label>审批意见<input v-model="comment" aria-label="账号审批意见" /></label>
    <p v-if="message" role="status" class="operation-feedback">{{ message }}</p>
    <p v-if="applications.length === 0" class="empty-hint">暂无账号申请</p>
    <article v-for="application in applications" :key="application.applicationCode" class="account-process-row">
      <div>
        <b>{{ application.applicationCode }} · {{ application.targetUsername }}</b>
        <span>{{ statusActions[application.statusAction || ''] || application.actionType }} · {{ statusLabel(application) }}</span>
        <small v-if="application.targetAllowedProcesses !== undefined">岗位工序：{{ application.targetAllowedProcesses.join('、') || '无' }}</small>
        <small>原因：{{ application.reason }}；{{ application.blockingStatus }}</small>
        <small v-if="application.missingEvidence.length">缺少材料：{{ application.missingEvidence.join('、') }}</small>
        <small v-if="application.missingCapabilities.length">能力边界：{{ application.missingCapabilities.join('、') }}</small>
        <label>上传审批附件<input type="file" :aria-label="`审批附件 ${application.applicationCode}`" :disabled="busy" @change="upload(application, $event)" /></label>
      </div>
      <div class="account-row-actions">
        <template v-if="task(application)?.status === 'WAITING_APPROVAL'">
          <button class="table-action" type="button" :disabled="busy || !application.readyForBlockingApproval" @click="decide(application, 'APPROVE')">批准</button>
          <button class="table-action" type="button" :disabled="busy" @click="decide(application, 'REJECT')">驳回</button>
        </template>
        <button class="primary-button" type="button" :disabled="busy || executing || !canExecute(application)" @click="emit('execute', application)">执行已批准操作</button>
      </div>
    </article>
  </section>
</template>

<style scoped>
label { display: grid; gap: 6px; min-width: 0; margin: 8px 0; }
input:not([type="checkbox"]) { box-sizing: border-box; width: 100%; min-width: 0; max-width: 100%; }
fieldset label { display: flex; align-items: center; }
</style>
