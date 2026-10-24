<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import NavIcon from '../../components/NavIcon.vue';
import DeskInbox from '../workspace/DeskInbox.vue';
import type { TraceTarget } from '../scan/traceTarget';
import type { InboundOrder, QualityCase, Tone, WorkOrder } from '../../data/m1PrototypeData';
import type { AuthSession } from '../../services/authApi';
import {
  fetchInboundWorkspace,
  fetchProductionWorkspace,
  fetchQualityWorkspace,
} from '../../services/businessWorkspaceApi';
import {
  cancelDeskTask,
  completeDeskTask,
  createDeskTask,
  fetchDeskAssignees,
  fetchDeskTasks,
  type DeskAssignee,
  type DeskTask,
  type DeskTaskKind,
  type DeskTaskView,
} from '../../services/deskTaskApi';
import { fetchCustomers, type CustomerProfile } from '../../services/masterDataApi';

type DeskTab = 'inbound' | 'production' | 'quality' | 'progress' | 'label';

type DispatchRow = {
  code: string;
  type: string;
  object: string;
  material: string;
  node: string;
  owner: string;
  action: string;
  tab: DeskTab;
  priority: string;
  tone: Tone;
  quantity: string;
  weight: string;
  reference: string;
  detail: string;
};

const traceQuery = inject<Ref<string>>('zhubao-trace-query', ref(''));
const traceTarget = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
let traceToken = 0;
const openTab = inject<(tab: DeskTab) => void>('zhubao-open-tab', () => {});
const openOrder = inject<((target: Pick<TraceTarget, 'objectType' | 'code'>) => void) | null>('zhubao-open-order', null);
const openOrders = inject<(() => void) | null>('zhubao-open-orders', null);
const openIntake = inject<(() => void) | null>('zhubao-open-intake', null);
const legacyOpen = ref(!openOrders);
function toggleLegacy() {
  legacyOpen.value = !legacyOpen.value;
  if (legacyOpen.value) void loadDispatchRows();
}
const openLabel = inject<(code: string) => void>('zhubao-open-label', () => {});
const visibleTabs = inject<Ref<string[]>>('zhubao-visible-tabs', ref(['inbound', 'production', 'quality', 'progress', 'label']));
const selectedCode = ref('');
const dispatchRows = ref<DispatchRow[]>([]);
const loadError = ref('');
const demoData = ref(false);
const loading = ref(false);
const refreshedAt = ref('');
const canOpen = (tab: DeskTab) => visibleTabs.value.includes(tab);
const quickSession = inject<Ref<{ effectivePermissions?: string[] } | null>>('zhubao-auth-session', ref(null));
const canStartInbound = computed(() => canOpen('inbound') && (!quickSession.value?.effectivePermissions || quickSession.value.effectivePermissions.includes('BUSINESS_INBOUND_CONFIRM')));
const canReadOrders = computed(() => visibleTabs.value.some(tab => ['inbound', 'production', 'progress', 'quality', 'inventory'].includes(tab)));
const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canAssign = computed(() => authSession.value?.effectivePermissions?.includes('DESK_TASK_ASSIGN') === true);
const canSeeAllOpen = computed(() => authSession.value?.role === 'ADMIN');
const deskView = ref<DeskTaskView>('assigned');
const deskTasks = ref<DeskTask[]>([]);
const deskError = ref('');
const deskLoading = ref(false);
const creating = ref(false);
const savingTask = ref(false);
const actingCode = ref('');
const assignees = ref<DeskAssignee[]>([]);
const taskCustomers = ref<CustomerProfile[]>([]);
const formError = ref('');
const resultNotes = ref<Record<string, string>>({});
const cancelReasons = ref<Record<string, string>>({});
const createRequestId = ref(newRequestId());
const taskForm = ref({
  kind: 'MODELING' as DeskTaskKind,
  title: '',
  note: '',
  customerId: '',
  motherOrderCode: '',
  assigneeUsername: '',
  dueDate: '',
});
const kindLabel: Record<string, string> = { MODELING: '建模', DESIGN: '设计', OTHER: '其他' };
const statusLabel: Record<string, string> = { OPEN: '待办', DONE: '已完成', CANCELLED: '已作废' };
const actionRequests = new Map<string, { id: string; text: string }>();

onMounted(() => {
  if (legacyOpen.value) void loadDispatchRows();
  void loadDeskTasks();
});

async function loadDispatchRows() {
  loadError.value = '';
  demoData.value = false;
  loading.value = true;
  const errors: string[] = [];
  const rows: DispatchRow[] = [];

  if (canOpen('inbound')) try {
    const inbound = await fetchInboundWorkspace();
    demoData.value ||= inbound.dataSource === 'DEMO';
    rows.push(...inbound.orders.map(inboundRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '来料单据加载失败');
  }

  if (canOpen('production')) try {
    const production = await fetchProductionWorkspace();
    demoData.value ||= production.dataSource === 'DEMO';
    rows.push(...production.workOrders.map(workOrderRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '工单加载失败');
  }

  if (canOpen('quality')) try {
    const quality = await fetchQualityWorkspace();
    demoData.value ||= quality.dataSource === 'DEMO';
    rows.push(...quality.cases.map(qualityRow));
  } catch (error) {
    errors.push(error instanceof Error ? error.message : '质检单加载失败');
  }

  dispatchRows.value = rows;
  if (!rows.some((row) => row.code === selectedCode.value)) {
    selectedCode.value = rows[0]?.code ?? '';
  }
  loadError.value = errors.join('；');
  loading.value = false;
  refreshedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function inboundRow(order: InboundOrder): DispatchRow {
  const blocked = order.video === '失败' || order.status.includes('异常');
  return {
    code: order.code,
    type: '母单',
    object: order.customer,
    material: order.material,
    node: order.status,
    owner: order.receiverName || '收货',
    action: '继续处理母单',
    tab: 'inbound',
    priority: blocked ? 'P0' : 'P1',
    tone: inboundTone(order.status, order.video),
    quantity: `${order.qty} 件`,
    weight: order.weight,
    reference: [order.inboundDate, order.expressCompany, order.courierNo].filter(Boolean).join(' · '),
    detail: [order.remark, `附件：${order.video}`].filter(Boolean).join('；'),
  };
}

function workOrderRow(order: WorkOrder): DispatchRow {
  const blocked = order.status.includes('称重') || order.status.includes('阻断');
  return {
    code: order.code,
    type: '工单',
    object: order.customer,
    material: order.material,
    node: order.process,
    owner: order.assignee,
    action: '去报工',
    tab: 'production',
    priority: blocked ? 'P0' : 'P2',
    tone: workOrderTone(order.status),
    quantity: `${order.finishedQty} / ${order.plannedQty} 件`,
    weight: '',
    reference: order.operationContext?.motherOrderCode ?? '',
    detail: `${order.status}；进度 ${order.progress}%`,
  };
}

function qualityRow(item: QualityCase): DispatchRow {
  return {
    code: item.code,
    type: '质检',
    object: item.process,
    material: item.defect,
    node: item.status,
    owner: item.inspector,
    action: '去质检',
    tab: 'quality',
    priority: item.status.includes('待') ? 'P1' : 'P2',
    tone: qualityTone(item.status),
    quantity: '',
    weight: '',
    reference: item.workOrder,
    detail: item.defect || '未记录缺陷',
  };
}

const visibleRows = computed(() => {
  const query = traceQuery.value.trim().toLowerCase();
  if (!query) {
    return dispatchRows.value;
  }
  return dispatchRows.value.filter((row) =>
    `${row.code} ${row.type} ${row.object} ${row.material} ${row.owner} ${row.node}`.toLowerCase().includes(query),
  );
});

const listKeyword = ref('');
const detailOpen = ref(false);
const listedRows = computed(() => {
  const text = listKeyword.value.trim().toLowerCase();
  if (!text) return visibleRows.value;
  return visibleRows.value.filter((row) => `${row.code} ${row.type} ${row.object} ${row.owner}`.toLowerCase().includes(text));
});

const selectedRow = computed(() =>
  visibleRows.value.find((row) => row.code === selectedCode.value) ?? visibleRows.value[0],
);

const commandStats = computed(() => [
  { label: '母单记录', value: countType('母单'), unit: '单', tone: 'amber' as Tone, tab: 'inbound' as DeskTab },
  { label: '工序工单', value: countType('工单'), unit: '张', tone: 'blue' as Tone, tab: 'production' as DeskTab },
  { label: '质检记录', value: countType('质检'), unit: '批', tone: 'amber' as Tone, tab: 'quality' as DeskTab },
  { label: '异常提示', value: dispatchRows.value.filter(row => row.tone === 'red').length, unit: '项', tone: 'red' as Tone },
].filter(item => !item.tab || canOpen(item.tab)));

function countType(type: string) {
  return dispatchRows.value.filter((row) => row.type === type).length;
}

function selectRow(code: string) {
  selectedCode.value = code;
  detailOpen.value = true;
}

function backToDesk() {
  detailOpen.value = false;
}

function openSelected() {
  if (selectedRow.value) {
    if (openOrder) {
      const row = selectedRow.value;
      openOrder({ objectType: row.type === '母单' ? 'MOTHER_ORDER' : null, code: row.type === '质检' ? row.reference : row.code });
      return;
    }
    if (selectedRow.value.type === '母单') {
      traceToken += 1;
      traceTarget.value = { objectType: 'MOTHER_ORDER', code: selectedRow.value.code, intent: 'INTAKE', token: traceToken };
    }
    openTab(selectedRow.value.tab);
  }
}

function startInbound() {
  if (openIntake) { openIntake(); return; }
  traceQuery.value = '';
  traceTarget.value = null;
  openTab('inbound');
}

function printSelected() {
  if (selectedRow.value?.type === '母单') openLabel(selectedRow.value.code);
}

function workOrderTone(status: string): Tone {
  if (status.includes('称重') || status.includes('阻断')) return 'red';
  if (status.includes('待') || status.includes('返工')) return 'amber';
  return 'green';
}

function inboundTone(status: string, video: string): Tone {
  if (video === '失败' || status.includes('异常')) return 'red';
  if (status.includes('待') || video === '截取中') return 'amber';
  return 'green';
}

function qualityTone(status: string): Tone {
  if (status.includes('待')) return 'amber';
  if (status.includes('完成')) return 'green';
  return 'blue';
}

function newRequestId() {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID();
  return `desk-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function todayIso() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function isOverdue(task: DeskTask) {
  return task.status === 'OPEN' && task.dueDate < todayIso();
}

function taskSubject(task: DeskTask) {
  return [task.customerName, task.motherOrderCode].filter(Boolean).join(' · ') || '未关联客户';
}

function showComplete(task: DeskTask) {
  return task.status === 'OPEN' && (deskView.value === 'assigned' || deskView.value === 'open');
}

function showCancel(task: DeskTask) {
  return task.status === 'OPEN' && (deskView.value === 'issued' || deskView.value === 'open');
}

const emptyDeskText = computed(() => {
  if (deskView.value === 'issued') return '还没有发出的待办';
  if (deskView.value === 'open') return '没有未完成待办';
  return '暂无派给我的待办';
});

async function loadDeskTasks() {
  deskError.value = '';
  deskLoading.value = true;
  try {
    deskTasks.value = await fetchDeskTasks(deskView.value);
  } catch (error) {
    deskTasks.value = [];
    deskError.value = error instanceof Error ? error.message : '轻任务待办暂时读不到';
  } finally {
    deskLoading.value = false;
  }
}

async function switchDeskView(view: DeskTaskView) {
  deskView.value = view;
  await loadDeskTasks();
}

async function openCreate() {
  creating.value = !creating.value;
  formError.value = '';
  if (!creating.value) return;
  try {
    const [people, customerRows] = await Promise.all([fetchDeskAssignees(), fetchCustomers()]);
    assignees.value = people;
    taskCustomers.value = customerRows.filter((item) => item.enabled);
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '待办选项暂时读不到';
  }
}

async function submitTask() {
  formError.value = '';
  savingTask.value = true;
  try {
    await createDeskTask({
      kind: taskForm.value.kind,
      title: taskForm.value.title.trim(),
      note: taskForm.value.note.trim(),
      customerId: taskForm.value.customerId ? Number(taskForm.value.customerId) : null,
      motherOrderCode: taskForm.value.motherOrderCode.trim(),
      assigneeUsername: taskForm.value.assigneeUsername,
      dueDate: taskForm.value.dueDate,
      requestId: createRequestId.value,
    });
    createRequestId.value = newRequestId();
    taskForm.value = {
      kind: 'MODELING', title: '', note: '', customerId: '', motherOrderCode: '', assigneeUsername: '', dueDate: '',
    };
    creating.value = false;
    deskView.value = 'issued';
    await loadDeskTasks();
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '待办没有保存';
  } finally {
    savingTask.value = false;
  }
}

function actionRequest(key: string, text: string) {
  const current = actionRequests.get(key);
  if (current && current.text === text) return current.id;
  const id = newRequestId();
  actionRequests.set(key, { id, text });
  return id;
}

async function finishTask(task: DeskTask) {
  deskError.value = '';
  actingCode.value = task.taskCode;
  const note = (resultNotes.value[task.taskCode] ?? '').trim();
  try {
    await completeDeskTask(task.taskCode, {
      requestId: actionRequest(`complete:${task.taskCode}`, note),
      expectedVersion: task.version,
      resultNote: note,
    });
    actionRequests.delete(`complete:${task.taskCode}`);
    await loadDeskTasks();
  } catch (error) {
    deskError.value = error instanceof Error ? error.message : '待办没有完成';
  } finally {
    actingCode.value = '';
  }
}

async function voidTask(task: DeskTask) {
  const reason = (cancelReasons.value[task.taskCode] ?? '').trim();
  if (!reason) {
    deskError.value = '请填写作废原因';
    return;
  }
  deskError.value = '';
  actingCode.value = task.taskCode;
  try {
    await cancelDeskTask(task.taskCode, {
      requestId: actionRequest(`cancel:${task.taskCode}`, reason),
      expectedVersion: task.version,
      reason,
    });
    actionRequests.delete(`cancel:${task.taskCode}`);
    await loadDeskTasks();
  } catch (error) {
    deskError.value = error instanceof Error ? error.message : '待办没有作废';
  } finally {
    actingCode.value = '';
  }
}
</script>

<template>
  <div class="command-center-v3 page-stack">
    <p v-if="demoData" class="operation-feedback warning">当前待办含评审样例，仅用于演示。</p>
    <section class="module-master-panel dashboard-start" aria-label="开始业务">
      <div class="floor-toolbar dashboard-start-toolbar">
        <div>
          <h2>{{ authSession?.operator || '今天' }}，今天好</h2>
          <p class="empty-hint dashboard-intro">先看待办和本岗订单，再进入单据核对与办理。</p>
        </div>
      </div>
      <nav class="dashboard-shortcuts" aria-label="工作台快捷入口">
        <button v-if="canStartInbound" type="button" aria-label="新建收货／母单" @click="startInbound"><i><NavIcon name="inbound" /></i><span>新建收货</span></button>
        <button v-if="canOpen('production')" type="button" @click="openTab('production')"><i><NavIcon name="production" /></i><span>生产作业</span></button>
        <button v-if="canOpen('quality')" type="button" @click="openTab('quality')"><i><NavIcon name="quality" /></i><span>质量作业</span></button>
        <button v-if="canOpen('label') && !openOrders" type="button" @click="openTab('label')"><i><NavIcon name="label" /></i><span>标签打印</span></button>
        <button v-if="openOrders && canReadOrders" type="button" @click="openOrders"><i><NavIcon name="orders" /></i><span>查看订单</span></button>
        <button v-else-if="canOpen('progress')" type="button" @click="openTab('progress')"><i><NavIcon name="progress" /></i><span>在制进度</span></button>
      </nav>
      <p v-if="legacyOpen && !loading && !loadError && countType('母单') === 0 && canOpen('inbound')" class="operation-feedback">
        还没有母单。点击“新建收货／母单”，填写客户、运单、来料数量和重量，保存后即可打印母单标签，再核对加工要求和期限、确认拆单方式。
      </p>
    </section>
    <DeskInbox v-if="openOrders" />
    <button v-if="openOrders" class="secondary-button dashboard-history-toggle" type="button" :aria-expanded="legacyOpen" @click="toggleLegacy">{{ legacyOpen ? '收起其他单据记录' : '其他单据记录' }}</button>
    <section v-if="legacyOpen" class="mission-kpi-grid lean-kpis" aria-label="权限范围内业务概览">
      <article v-for="item in commandStats" :key="item.label" :class="`tone-${item.tone}`">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
      </article>
    </section>
    <p v-if="legacyOpen" class="empty-hint">本次旧列表返回的可见记录数，不随查找变化。{{ refreshedAt ? `更新于 ${refreshedAt}` : '正在读取业务数据' }}</p>

    <section class="module-master-panel desk-task-panel" aria-label="我的待办">
      <div class="floor-toolbar desk-task-heading">
        <div>
          <h2>我的待办</h2>
          <p class="empty-hint desk-task-intro">建模、设计等不经过工序的事项。</p>
        </div>
        <button v-if="canAssign" class="desk-text-action" type="button" :aria-expanded="creating" aria-controls="desk-task-create" @click="openCreate">{{ creating ? '收起新建' : '新建待办' }}</button>
      </div>
      <div class="desk-view-tabs" role="group" aria-label="待办分类">
        <button :aria-pressed="deskView === 'assigned'" type="button" @click="switchDeskView('assigned')">派给我的</button>
        <button v-if="canAssign" :aria-pressed="deskView === 'issued'" type="button" @click="switchDeskView('issued')">我发出的</button>
        <button v-if="canSeeAllOpen" :aria-pressed="deskView === 'open'" type="button" @click="switchDeskView('open')">全部未完成</button>
      </div>
      <form v-if="creating" id="desk-task-create" class="desk-task-form" @submit.prevent="submitTask">
        <label>
          <span>类型</span>
          <select v-model="taskForm.kind" aria-label="待办类型">
            <option value="MODELING">建模</option>
            <option value="DESIGN">设计</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <label>
          <span>标题</span>
          <input v-model="taskForm.title" aria-label="待办标题" maxlength="80" />
        </label>
        <label>
          <span>期限</span>
          <input v-model="taskForm.dueDate" aria-label="待办期限" type="date" />
        </label>
        <label>
          <span>责任人</span>
          <select v-model="taskForm.assigneeUsername" aria-label="待办责任人">
            <option value="">请选择</option>
            <option v-for="person in assignees" :key="person.username" :value="person.username">{{ person.operator }}</option>
          </select>
        </label>
        <label>
          <span>客户</span>
          <select v-model="taskForm.customerId" aria-label="待办客户">
            <option value="">不关联客户</option>
            <option v-for="customer in taskCustomers" :key="customer.customerId" :value="String(customer.customerId)">{{ customer.name }}</option>
          </select>
        </label>
        <label>
          <span>母单号</span>
          <input v-model="taskForm.motherOrderCode" aria-label="待办母单号" placeholder="可选" />
        </label>
        <label class="span-2">
          <span>说明</span>
          <input v-model="taskForm.note" aria-label="待办说明" maxlength="500" />
        </label>
        <div class="active-action-grid">
          <button class="primary-button" type="submit" :disabled="savingTask">{{ savingTask ? '保存中…' : '保存待办' }}</button>
        </div>
      </form>
      <p v-if="formError" class="operation-feedback danger">{{ formError }}</p>
      <p v-if="deskError" class="operation-feedback danger">{{ deskError }}</p>
      <p v-else-if="deskLoading" class="empty-hint">正在读取待办…</p>
      <p v-else-if="deskTasks.length === 0" class="empty-hint">{{ emptyDeskText }}</p>
      <div v-if="deskTasks.length" class="erp-table-wrap desk-table-wrap">
        <table class="erp-table desk-task-table">
          <thead>
            <tr>
              <th>类型</th>
              <th>标题</th>
              <th>客户／母单</th>
              <th>期限</th>
              <th>责任人</th>
              <th>状态</th>
              <th>动作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in deskTasks" :key="task.taskCode">
              <td>{{ kindLabel[task.kind] ?? task.kind }}</td>
              <td>
                <span class="cell-stack">
                  <b>{{ task.title }}</b>
                  <small v-if="task.note">{{ task.note }}</small>
                </span>
              </td>
              <td>{{ taskSubject(task) }}</td>
              <td>
                <span>{{ task.dueDate }}</span>
                <StatusBadge v-if="isOverdue(task)" label="逾期" tone="red" />
              </td>
              <td>{{ task.assigneeName || task.assigneeUsername }}</td>
              <td>{{ statusLabel[task.status] ?? task.status }}</td>
              <td>
                <div v-if="showComplete(task)" class="desk-task-action">
                  <input v-model="resultNotes[task.taskCode]" aria-label="结果说明" placeholder="结果说明，可选" />
                  <button class="table-action" type="button" :disabled="actingCode === task.taskCode" @click="finishTask(task)">完成</button>
                </div>
                <div v-if="showCancel(task)" class="desk-task-action">
                  <input v-model="cancelReasons[task.taskCode]" aria-label="作废原因" placeholder="作废原因" />
                  <button class="table-action" type="button" :disabled="actingCode === task.taskCode" @click="voidTask(task)">作废</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="deskTasks.length" class="desk-cards">
        <article v-for="task in deskTasks" :key="`card-${task.taskCode}`" class="desk-card desk-task-card">
          <span class="desk-task-icon"><NavIcon name="sample" /></span>
          <div class="desk-task-copy">
            <span class="desk-card-top">
              <strong class="code">{{ task.title }}</strong>
              <StatusBadge v-if="isOverdue(task)" label="逾期" tone="red" />
              <span v-else>{{ statusLabel[task.status] ?? task.status }}</span>
            </span>
            <p class="desk-task-meta">{{ kindLabel[task.kind] ?? task.kind }} · {{ taskSubject(task) }}</p>
            <p class="desk-task-meta">{{ task.dueDate }} · {{ task.assigneeName || task.assigneeUsername }}</p>
            <details class="desk-task-handling">
              <summary>{{ showComplete(task) || showCancel(task) ? '办理待办' : '查看记录' }}</summary>
              <p class="desk-task-meta">{{ task.taskCode }}</p>
              <p v-if="task.note" class="desk-task-meta">{{ task.note }}</p>
              <div v-if="showComplete(task)" class="desk-task-action">
                <input v-model="resultNotes[task.taskCode]" aria-label="卡片结果说明" placeholder="结果说明，可选" />
                <button class="table-action" type="button" :disabled="actingCode === task.taskCode" @click="finishTask(task)">完成</button>
              </div>
              <div v-if="showCancel(task)" class="desk-task-action">
                <input v-model="cancelReasons[task.taskCode]" aria-label="卡片作废原因" placeholder="作废原因" />
                <button class="table-action" type="button" :disabled="actingCode === task.taskCode" @click="voidTask(task)">作废</button>
              </div>
            </details>
          </div>
        </article>
      </div>
    </section>

    <section v-if="legacyOpen" class="production-desk-grid command-grid-v2 lean-desk desk-page" :class="{ 'is-detail': detailOpen }" aria-label="业务单据和当前处理单">
      <main class="dispatch-console-v3 dispatch-console-v2 module-master-panel">
        <div class="floor-toolbar">
          <h2>业务单据</h2>
          <label>
            <span>查找</span>
            <input v-model="listKeyword" aria-label="待办查找" placeholder="单号、类型或责任人" />
          </label>
          <button class="secondary-button" type="button" :disabled="loading" @click="loadDispatchRows">{{ loading ? '刷新中…' : '刷新单据' }}</button>
        </div>
        <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        <div class="erp-table-wrap command-table-wrap desk-table-wrap">
          <table class="erp-table floor-dispatch-table command-table-v2">
            <thead>
              <tr>
                <th>优先级</th>
                <th>单号</th>
                <th>类型</th>
                <th>业务对象</th>
                <th>节点</th>
                <th>责任人</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in listedRows"
                :key="`${row.type}-${row.code}`"
                :class="{ 'is-selected': selectedRow?.code === row.code }"
                @click="selectRow(row.code)"
              >
                <td><StatusBadge :label="row.priority" :tone="row.tone" /></td>
                <td><strong>{{ row.code }}</strong></td>
                <td>{{ row.type }}</td>
                <td>
                  <span class="cell-stack">
                    <b>{{ row.object }}</b>
                    <small>{{ row.material }}</small>
                  </span>
                </td>
                <td>{{ row.node }}</td>
                <td>{{ row.owner }}</td>
                <td>
                  <button class="table-action" type="button" @click.stop="selectRow(row.code); openSelected()">
                    {{ row.action }}
                  </button>
                </td>
              </tr>
              <tr v-if="listedRows.length === 0">
                <td colspan="7">{{ loading ? '正在读取单据…' : loadError ? '暂无待办单据，请刷新重试' : dispatchRows.length === 0 ? '暂无业务单据' : '没有匹配的单据' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="desk-cards">
          <p v-if="listedRows.length === 0" class="empty-hint">{{ loading ? '正在读取单据…' : loadError ? '暂无待办单据，请刷新重试' : dispatchRows.length === 0 ? '暂无业务单据' : '没有匹配的单据' }}</p>
          <button v-for="row in listedRows" :key="`card-${row.type}-${row.code}`" class="desk-card" type="button" @click="selectRow(row.code)">
            <span class="desk-card-top">
              <strong class="code">{{ row.code }}</strong>
              <StatusBadge :label="row.priority" :tone="row.tone" />
            </span>
            <span>{{ row.type }} · {{ row.object }}</span>
            <span>{{ row.node }} · {{ row.owner }}</span>
            <span v-if="row.quantity || row.weight">{{ [row.quantity, row.weight].filter(Boolean).join(' · ') }}</span>
          </button>
        </div>
      </main>

      <aside class="active-record-panel module-side-panel">
        <div class="panel-title-row">
          <button class="secondary-button detail-back" type="button" @click="backToDesk">返回列表</button>
          <h2>当前处理单</h2>
          <StatusBadge v-if="selectedRow" :label="selectedRow.priority" :tone="selectedRow.tone" />
        </div>
        <template v-if="selectedRow">
          <div class="active-record-code">
            <strong>{{ selectedRow.code }}</strong>
            <span>{{ selectedRow.object }} / {{ selectedRow.material }}</span>
          </div>
          <dl class="detail-grid active-record-grid">
            <dt>类型</dt>
            <dd>{{ selectedRow.type }}</dd>
            <dt>节点</dt>
            <dd>{{ selectedRow.node }}</dd>
            <dt>责任人</dt>
            <dd>{{ selectedRow.owner }}</dd>
            <template v-if="selectedRow.quantity">
              <dt>{{ selectedRow.type === '工单' ? '已完／计划' : '数量' }}</dt>
              <dd>{{ selectedRow.quantity }}</dd>
            </template>
            <template v-if="selectedRow.weight">
              <dt>重量</dt>
              <dd>{{ selectedRow.weight }}</dd>
            </template>
            <template v-if="selectedRow.reference">
              <dt>{{ selectedRow.type === '母单' ? '收货／运单' : '关联单号' }}</dt>
              <dd>{{ selectedRow.reference }}</dd>
            </template>
            <dt>处理信息</dt>
            <dd>{{ selectedRow.detail }}</dd>
          </dl>
          <p v-if="selectedRow.type === '母单'" class="empty-hint">母单已建立后可直接打印标签；继续处理可核对加工要求、完成期限和拆单方式。</p>
          <div class="active-action-grid">
            <button class="primary-button" type="button" @click="openSelected">{{ openOrder ? '查看订单详情' : selectedRow.action }}</button>
            <button v-if="selectedRow.type === '母单' && canOpen('label')" class="secondary-button" type="button" @click="printSelected">打印此母单标签</button>
          </div>
        </template>
        <p v-else class="empty-hint">{{ loadError ? '暂无待办单据' : '选择单据查看数量、状态和下一步操作' }}</p>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.command-center-v3 { display: flex; flex-direction: column; }
.command-center-v3 > * { order: 3; }
.dashboard-start { order: 0; }
.desk-task-panel { order: 1; }
.desk-inbox { order: 2; }
.dashboard-history-toggle { align-self: start; min-height: 44px; }
.dashboard-shortcuts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px; max-width: 560px; }
.dashboard-shortcuts button { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; min-width: 0; min-height: 78px; padding: 5px 0; border: 0; background: transparent; cursor: pointer; font-size: 13px; }
.dashboard-shortcuts i { display: grid; place-items: center; width: 42px; height: 42px; border: 1px solid var(--line); border-radius: 12px; background: var(--content); color: var(--action); }
.dashboard-shortcuts button:hover i { background: var(--action-soft); border-color: var(--action); }
.desk-task-heading { margin: 0; }
.desk-text-action { min-height: 44px; padding: 0 2px 0 12px; border: 0; background: transparent; color: var(--action); white-space: nowrap; cursor: pointer; }
.desk-view-tabs { display: grid; grid-auto-flow: column; grid-auto-columns: minmax(0, 1fr); gap: 6px; width: fit-content; max-width: 100%; }
.desk-view-tabs button { min-height: 44px; padding: 8px 12px; border: 0; border-radius: 7px; background: var(--workspace); color: var(--muted); cursor: pointer; white-space: nowrap; }
.desk-view-tabs button[aria-pressed="true"] { background: var(--action-soft); color: var(--action); font-weight: 600; }
.desk-task-icon { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 9px; background: var(--action-soft); color: var(--action); }
.desk-task-copy { display: grid; gap: 5px; min-width: 0; }
.desk-task-meta { font-size: 12px; color: var(--muted); overflow-wrap: anywhere; }
.desk-task-handling summary { display: flex; align-items: center; min-height: 44px; color: var(--action); cursor: pointer; }
.desk-task-handling summary::after { content: '⌄'; margin-left: 6px; }
.desk-task-handling summary::-webkit-details-marker { display: none; }
.desk-task-handling[open] summary::after { content: '⌃'; }
.desk-task-handling .desk-task-action { grid-template-columns: minmax(0, 1fr) auto; margin-top: 8px; align-items: center; }
@media (min-width: 901px) {
  .command-center-v3 { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(280px, 0.8fr); align-items: start; }
  .command-center-v3 > * { grid-column: 1 / -1; }
  .desk-inbox { grid-column: 1; grid-row: 2; }
  .desk-task-panel { grid-column: 2; grid-row: 2; }
  .dashboard-shortcuts { display: flex; flex-wrap: wrap; max-width: none; gap: 8px; }
  .dashboard-shortcuts button { flex-direction: row; min-height: 36px; padding: 4px 12px; border: 1px solid var(--line-strong); border-radius: var(--radius-control); color: var(--action); background: var(--content); }
  .dashboard-shortcuts i { width: 20px; height: 20px; border: 0; border-radius: 0; background: transparent; }
  .dashboard-shortcuts button[aria-label="新建收货／母单"] { background: var(--action); border-color: var(--action); color: #fff; }
  .dashboard-shortcuts button[aria-label="新建收货／母单"] i { color: inherit; }
}
@media (max-width: 900px) {
  .command-center-v3 { gap: 16px; padding: 4px 8px; }
  .command-center-v3 .dashboard-start,
  .command-center-v3 .desk-task-panel { padding: 0; border: 0; border-radius: 0; box-shadow: none; background: transparent; }
  .dashboard-start-toolbar { margin-bottom: 12px; }
  .dashboard-start h2 { font-size: 21px; font-weight: 600; }
  .dashboard-intro,
  .desk-task-intro { display: none; }
  .dashboard-shortcuts { max-width: none; }
  .desk-inbox { order: 1; }
  .desk-task-panel { order: 2; gap: 10px; }
  .desk-task-heading h2 { font-size: 15px; }
  .desk-view-tabs { width: 100%; }
  .desk-view-tabs button { padding: 8px 4px; font-size: 13px; }
  .desk-task-panel > .empty-hint { padding: 16px 12px; border: 1px solid var(--line); border-radius: 10px; background: var(--content); text-align: center; font-size: 13px; }
  .desk-task-panel .desk-task-card { grid-template-columns: 32px minmax(0, 1fr); gap: 11px; padding: 14px; border-radius: 12px; }
  .desk-card-top { flex-wrap: nowrap; align-items: start; }
  .desk-card-top strong { font-size: 14px; }
  .desk-card-top > :last-child { flex-shrink: 0; font-size: 12px; }
  .desk-task-form { grid-template-columns: 1fr 1fr; padding: 12px; border: 1px solid var(--line); border-radius: 12px; background: var(--content); }
  .dashboard-history-toggle { align-self: center; border: 0; background: transparent; font-size: 12px; color: var(--muted); }
}
</style>
