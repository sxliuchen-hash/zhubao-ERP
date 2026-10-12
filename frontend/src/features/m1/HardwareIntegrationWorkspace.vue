<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  type HardwarePocRecord,
  type IntegrationTask,
  type Tone,
} from '../../data/m1PrototypeData';
import { fetchAuthSession } from '../../services/authApi';
import {
  fetchHardwareWorkspace,
  type HardwareWorkspaceModel,
} from '../../services/hardwareIntegrationApi';
import {
  consumeReading,
  fetchGatewayDevices,
  fetchGatewaySummary,
  fetchRecentConsumptions,
  fetchRecentReadings,
  ingestDeviceReading,
  registerGatewayDevice,
  rotateGatewayDeviceKey,
  setGatewayDeviceEnabled,
  type ConsumeReadingRequest,
  type DeviceReading,
  type DeviceReadingType,
  type GatewayDeviceView,
  type GatewayIngestionSummary,
  type ReadingConsumption,
  type ReadingConsumptionIntent,
} from '../../services/deviceGatewayApi';

const workspace = ref<HardwareWorkspaceModel>({
  devices: [], tasks: [], pocRecords: [], pocEvidence: [],
  summary: { totalDevices: 0, fallbackRequired: 0, averageHealth: 0, pocRecords: 0, pocBlocked: 0 },
});
const isAdmin = ref(false);
const loadingError = ref('');
const deviceKeyword = ref('');
const selectedDeviceCode = ref('');
const detailOpen = ref(false);

const visibleDevices = computed(() => {
  const text = deviceKeyword.value.trim().toLowerCase();
  if (!text) return workspace.value.devices;
  return workspace.value.devices.filter((device) => `${device.code} ${device.name} ${device.scene}`.toLowerCase().includes(text));
});

const activeDevice = computed(
  () =>
    workspace.value.devices.find((device) => device.code === selectedDeviceCode.value) ??
    workspace.value.devices.find((device) => device.status !== 'online') ??
    workspace.value.devices[0],
);

const activeTask = computed(
  () => [...workspace.value.tasks].sort((a, b) => a.health - b.health)[0],
);

const hardwarePocEvidence = computed(() => workspace.value.pocEvidence);

const hardwareStats = computed(() => [
  {
    label: '设备点位',
    value: workspace.value.summary.totalDevices,
    unit: '个',
    detail: '扫码/打印/视频/称重',
    tone: 'blue' as Tone,
  },
  {
    label: '需兜底',
    value: workspace.value.summary.fallbackRequired,
    unit: '项',
    detail: '不能卡死业务闭环',
    tone: 'amber' as Tone,
  },
  {
    label: '平均健康',
    value: workspace.value.summary.averageHealth,
    unit: '%',
    detail: '联动任务健康度',
    tone: 'green' as Tone,
  },
  {
    label: 'POC 阻断',
    value: workspace.value.summary.pocBlocked,
    unit: '项',
    detail: '必须复测闭环',
    tone: 'red' as Tone,
  },
]);

const hardwareFlow = [
  { code: 'SCAN', name: '扫码输入', status: 'HID/相机/手工兜底', tone: 'green' },
  { code: 'LABEL', name: '标签打印', status: '浏览器打印/PDF', tone: 'blue' },
  { code: 'VIDEO', name: '视频截取', status: 'NVR/FFmpeg/人工上传', tone: 'amber' },
  { code: 'WEIGH', name: '称重交接', status: '人工读数/二期接口', tone: 'red' },
  { code: 'STORE', name: '证据归档', status: 'NAS/对象存储预留', tone: 'violet' },
] as const;

const hardwareBlockers = computed(() => [
  ...workspace.value.pocRecords
    .filter((record) => record.result !== '通过')
    .map((record) => ({
      code: record.code,
      title: record.result,
      detail: `${record.scene} / ${record.blocker}`,
      owner: record.owner,
      tone: record.tone,
    })),
  ...workspace.value.tasks
    .filter((task) => task.health < 75)
    .map((task) => ({
      code: task.id,
      title: `${task.health}%`,
      detail: `${task.title} / ${task.nextAction}`,
      owner: task.owner,
      tone: task.health < 60 ? ('red' as Tone) : ('amber' as Tone),
    })),
]);

const gatewayDevices = ref<GatewayDeviceView[]>([]);
const gatewayReadings = ref<DeviceReading[]>([]);
const gatewaySummary = ref<GatewayIngestionSummary>({
  totalDevices: 0,
  enabledDevices: 0,
  recentReadings: 0,
  readyReadings: 0,
  warningReadings: 0,
  blockedReadings: 0,
});
const gatewayError = ref('');
const gatewayForm = ref({
  deviceCode: '',
  deviceKey: '',
  readingType: 'SCALE_WEIGHT' as DeviceReadingType,
  rawPayload: '',
  businessRefType: '',
  businessRefCode: '',
});
const gatewayFeedback = ref('');
const gatewaySubmitting = ref(false);

const readingTypeOptions: Array<{ value: DeviceReadingType; label: string }> = [
  { value: 'SCALE_WEIGHT', label: '电子秤称重' },
  { value: 'SCAN_EVENT', label: '扫码事件' },
  { value: 'PRINT_ACK', label: '标签打印回执' },
  { value: 'VIDEO_CLIP_MARK', label: '视频截取标记' },
  { value: 'HEARTBEAT', label: '设备心跳' },
];

const gatewayConsumptions = ref<ReadingConsumption[]>([]);
const gatewayConsumedCodes = computed(
  () => new Set(gatewayConsumptions.value.map((consumption) => consumption.readingCode)),
);

const intentByType: Record<DeviceReadingType, ReadingConsumptionIntent> = {
  SCALE_WEIGHT: 'WEIGH_HANDOVER',
  SCAN_EVENT: 'SCAN_TO_REPORT',
  PRINT_ACK: 'PRINT_CONFIRM',
  VIDEO_CLIP_MARK: 'VIDEO_LINK',
  HEARTBEAT: 'DISMISS',
};

const intentLabels: Record<ReadingConsumptionIntent, string> = {
  WEIGH_HANDOVER: '称重交接',
  SCAN_TO_REPORT: '扫码报工',
  PRINT_CONFIRM: '打印确认',
  VIDEO_LINK: '视频关联',
  DISMISS: '忽略',
};

function defaultIntentFor(reading: DeviceReading): ReadingConsumptionIntent {
  if (reading.status === 'BLOCKED') return 'DISMISS';
  return intentByType[reading.readingType];
}

const postingForm = ref({
  enabled: false,
  customerId: '',
  motherOrderCode: '',
  workOrderCode: '',
  materialCode: '',
  fromWarehouseCode: 'WIP',
  toWarehouseCode: 'SEMI-FINISHED',
  quantity: '',
  process: '',
});

function postingFieldsComplete(): boolean {
  return (
    postingForm.value.enabled &&
    postingForm.value.customerId.trim() !== '' &&
    postingForm.value.motherOrderCode.trim() !== '' &&
    postingForm.value.materialCode.trim() !== '' &&
    postingForm.value.fromWarehouseCode.trim() !== '' &&
    postingForm.value.toWarehouseCode.trim() !== '' &&
    postingForm.value.quantity.trim() !== ''
  );
}

async function consumeGatewayReading(reading: DeviceReading) {
  gatewayFeedback.value = '';
  const request: ConsumeReadingRequest = {
    readingCode: reading.readingCode,
    intent: defaultIntentFor(reading),
    businessRefType: reading.businessRefType || undefined,
    businessRefCode: reading.businessRefCode || undefined,
  };
  const canPost =
    (request.intent === 'WEIGH_HANDOVER' || request.intent === 'SCAN_TO_REPORT') && postingFieldsComplete();
  if (canPost) {
    request.postingCustomerId = Number(postingForm.value.customerId);
    request.postingMotherOrderCode = postingForm.value.motherOrderCode.trim();
    request.postingWorkOrderCode = postingForm.value.workOrderCode.trim() || undefined;
    request.postingMaterialCode = postingForm.value.materialCode.trim();
    request.postingFromWarehouseCode = postingForm.value.fromWarehouseCode.trim();
    request.postingToWarehouseCode = postingForm.value.toWarehouseCode.trim();
    request.postingQuantity = postingForm.value.quantity.trim();
    request.postingProcess = postingForm.value.process.trim() || undefined;
  }
  try {
    const consumption = await consumeReading(request);
    const ledgerNote = consumption.ledgerCode
      ? `，已联动写入台账 ${consumption.ledgerCode}`
      : '（仅留痕，未写台账）';
    gatewayFeedback.value = `读数 ${consumption.readingCode} 已消费为「${intentLabels[consumption.intent]}」，操作人 ${consumption.operator}${ledgerNote}`;
    await refreshGateway();
  } catch (error) {
    gatewayFeedback.value = error instanceof Error ? error.message : '读数消费失败';
  }
}

const deviceForm = ref({
  deviceCode: '',
  deviceName: '',
  acceptedType: 'SCALE_WEIGHT' as DeviceReadingType,
  deviceKey: '',
  location: '',
  fallbackNote: '',
});
const deviceAdminFeedback = ref('');

async function registerDevice() {
  deviceAdminFeedback.value = '';
  if (!deviceForm.value.deviceCode.trim() || !deviceForm.value.deviceName.trim()) {
    deviceAdminFeedback.value = '请填写设备编码和名称';
    return;
  }
  try {
    const device = await registerGatewayDevice({
      deviceCode: deviceForm.value.deviceCode.trim(),
      deviceName: deviceForm.value.deviceName.trim(),
      acceptedTypes: [deviceForm.value.acceptedType, 'HEARTBEAT'],
      deviceKey: deviceForm.value.deviceKey.trim() || undefined,
      location: deviceForm.value.location.trim() || undefined,
      fallbackNote: deviceForm.value.fallbackNote.trim() || undefined,
    });
    deviceAdminFeedback.value = `设备 ${device.deviceCode} 已保存`;
    await refreshGateway();
  } catch (error) {
    deviceAdminFeedback.value = error instanceof Error ? error.message : '设备保存失败（需要管理员权限）';
  }
}

async function toggleDevice(device: GatewayDeviceView) {
  deviceAdminFeedback.value = '';
  try {
    await setGatewayDeviceEnabled(device.deviceCode, !device.enabled);
    deviceAdminFeedback.value = `设备 ${device.deviceCode} 已${device.enabled ? '停用' : '启用'}`;
    await refreshGateway();
  } catch (error) {
    deviceAdminFeedback.value = error instanceof Error ? error.message : '设备状态更新失败';
  }
}

async function rotateDeviceKey(device: GatewayDeviceView) {
  deviceAdminFeedback.value = '';
  const newKey = typeof window !== 'undefined' ? window.prompt(`为 ${device.deviceCode} 输入新设备密钥`) : '';
  if (!newKey || !newKey.trim()) {
    return;
  }
  try {
    await rotateGatewayDeviceKey(device.deviceCode, newKey.trim());
    deviceAdminFeedback.value = `设备 ${device.deviceCode} 密钥已轮换`;
    await refreshGateway();
  } catch (error) {
    deviceAdminFeedback.value = error instanceof Error ? error.message : '密钥轮换失败';
  }
}

function gatewayStatusTone(status: string): Tone {
  if (status === 'READY') return 'green';
  if (status === 'WARNING') return 'amber';
  return 'red';
}

function generateNonce(): string {
  const cryptoObj = typeof globalThis !== 'undefined' ? globalThis.crypto : undefined;
  if (cryptoObj && typeof cryptoObj.randomUUID === 'function') {
    return cryptoObj.randomUUID();
  }
  return `nonce-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

async function refreshGateway() {
  try {
    const [devices, readings, summary, consumptions] = await Promise.all([
      fetchGatewayDevices(),
      fetchRecentReadings(20),
      fetchGatewaySummary(),
      fetchRecentConsumptions(20),
    ]);
    gatewayDevices.value = devices;
    gatewayReadings.value = readings;
    gatewaySummary.value = summary;
    gatewayConsumptions.value = consumptions;
    gatewayError.value = '';
  } catch (error) {
    gatewayError.value =
      error instanceof Error ? error.message : '设备网关接口不可用，读数接入需连接后端 API';
  }
}

async function submitGatewayReading() {
  if (!isAdmin.value) return;
  gatewayFeedback.value = '';
  if (!gatewayForm.value.deviceKey.trim()) {
    gatewayFeedback.value = '请填写设备密钥';
    return;
  }
  gatewaySubmitting.value = true;
  try {
    const reading = await ingestDeviceReading({
      deviceCode: gatewayForm.value.deviceCode.trim(),
      deviceKey: gatewayForm.value.deviceKey.trim(),
      readingType: gatewayForm.value.readingType,
      rawPayload: gatewayForm.value.rawPayload,
      businessRefType: gatewayForm.value.businessRefType.trim() || undefined,
      businessRefCode: gatewayForm.value.businessRefCode.trim() || undefined,
      // 携带时间戳和唯一随机数，兼容防重放校验。
      capturedAt: new Date().toISOString(),
      nonce: generateNonce(),
    });
    gatewayFeedback.value = `读数 ${reading.readingCode} 已接入：${reading.status} ${reading.message}`;
    await refreshGateway();
  } catch (error) {
    gatewayFeedback.value = error instanceof Error ? error.message : '读数接入失败';
  } finally {
    gatewaySubmitting.value = false;
  }
}

onMounted(async () => {
  try {
    workspace.value = await fetchHardwareWorkspace();
  } catch (error) {
    loadingError.value = error instanceof Error ? error.message : '硬件接口不可用';
  }
  try {
    isAdmin.value = (await fetchAuthSession())?.role === 'ADMIN';
  } catch {
    isAdmin.value = false;
  }
  await refreshGateway();
});

function deviceTone(status: string): Tone {
  if (status === 'online') return 'green';
  if (status === 'degraded') return 'amber';
  return 'gray';
}

function deviceLabel(status: string) {
  if (status === 'online') return '在线';
  if (status === 'degraded') return '需验证';
  return '人工兜底';
}

function selectDevice(code: string) {
  selectedDeviceCode.value = code;
  detailOpen.value = true;
}

function backToDevices() {
  detailOpen.value = false;
}

function pocTone(record: HardwarePocRecord): Tone {
  return record.tone;
}

function taskTone(task: IntegrationTask): Tone {
  if (task.health < 60) return 'red';
  if (task.health < 80) return 'amber';
  return 'green';
}

</script>

<template>
  <div class="hardware-desk page-stack desk-page" :class="{ 'is-detail': detailOpen }">
    <p v-if="loadingError" class="operation-feedback danger" role="alert">设备状态读取失败：{{ loadingError }}</p>
    <p v-else-if="workspace.devices.length === 0" class="empty-hint">暂无设备点位，请先登记并连接现场设备。</p>
    <section class="module-command-surface hardware-command-surface" aria-label="软硬件联动作业台">
      <div class="module-command-main">
        <div class="console-title-line">
          <span class="eyebrow">HARDWARE DESK</span>
          <StatusBadge label="兼容优先 / 失败兜底" tone="amber" />
        </div>
        <div class="module-command-title">
          <h2>软硬件联动作业台</h2>
          <div class="module-date-card">
            <strong>试点线</strong>
            <span>扫码 / 视频 / 标签 / 称重</span>
          </div>
        </div>

        <div class="module-stat-grid">
          <article v-for="item in hardwareStats" :key="item.label" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.detail }}</em>
          </article>
        </div>

        <div class="module-flow-strip" aria-label="软硬件联动链路">
          <article v-for="step in hardwareFlow" :key="step.code" :class="`tone-${step.tone}`">
            <b>{{ step.code }}</b>
            <div>
              <strong>{{ step.name }}</strong>
              <span>{{ step.status }}</span>
            </div>
          </article>
        </div>

        <div class="ops-scope-ribbon" aria-label="硬件 POC 仓储边界">
          <span>POC 仓储可切换</span>
          <span>zhubao.hardware.poc.repository</span>
          <span>本地默认 memory</span>
          <span>MySQL 默认 jdbc</span>
          <span>/api/hardware/poc-records 接口不变</span>
        </div>
      </div>

      <aside class="module-priority-panel">
        <div>
          <span class="eyebrow">FALLBACK</span>
          <strong>联动阻断队列</strong>
        </div>
        <article v-for="row in hardwareBlockers.slice(0, 4)" :key="row.code" :class="`tone-${row.tone}`">
          <StatusBadge :label="row.title" :tone="row.tone" />
          <div>
            <strong>{{ row.code }}</strong>
            <span>{{ row.detail }}</span>
            <small>{{ row.owner }}</small>
          </div>
        </article>
      </aside>
    </section>

    <section class="module-work-grid hardware-work-grid" aria-label="设备联动主控台">
      <div class="module-master-panel">
        <div class="module-toolbar refined-module-toolbar">
          <div>
            <span class="eyebrow">DEVICE POOL</span>
            <h2>设备兼容池</h2>
          </div>
          <div class="module-tools">
            <label>
              <span>设备/业务码</span>
              <input v-model="deviceKeyword" placeholder="设备编码或名称" aria-label="设备或业务编码" />
            </label>
            <button class="primary-button" type="button">新建 POC</button>
            <button class="secondary-button" type="button">导出清单</button>
          </div>
        </div>

        <div class="module-table-wrap refined-table-wrap desk-table-wrap">
          <table class="module-table hardware-device-table">
            <thead>
              <tr>
                <th>设备</th>
                <th>场景</th>
                <th>协议</th>
                <th>状态</th>
                <th>失败兜底</th>
                <th>动作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="device in visibleDevices" :key="device.code" @click="selectDevice(device.code)">
                <td>
                  <span class="cell-stack">
                    <b>{{ device.code }}</b>
                    <small>{{ device.name }}</small>
                  </span>
                </td>
                <td>{{ device.scene }}</td>
                <td>{{ device.protocol }}</td>
                <td><StatusBadge :label="deviceLabel(device.status)" :tone="deviceTone(device.status)" /></td>
                <td>{{ device.fallback }}</td>
                <td><button class="table-action" type="button">记录 POC</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="desk-cards">
          <p v-if="visibleDevices.length === 0" class="empty-hint">暂无设备</p>
          <button v-for="device in visibleDevices" :key="`card-${device.code}`" class="desk-card" type="button" @click="selectDevice(device.code)">
            <span class="desk-card-top">
              <strong class="code">{{ device.code }}</strong>
              <StatusBadge :label="deviceLabel(device.status)" :tone="deviceTone(device.status)" />
            </span>
            <span>{{ device.name }}</span>
            <span>{{ device.scene }} · {{ device.protocol }}</span>
          </button>
        </div>
      </div>

      <aside class="module-side-panel">
        <section v-if="activeDevice" class="active-object-card tone-amber">
          <div class="panel-title-row">
            <button class="secondary-button detail-back" type="button" @click="backToDevices">返回列表</button>
            <StatusBadge :label="deviceLabel(activeDevice.status)" :tone="deviceTone(activeDevice.status)" />
          </div>
          <strong>{{ activeDevice.name }}</strong>
          <dl class="detail-grid compact-detail-grid">
            <dt>编码</dt>
            <dd>{{ activeDevice.code }}</dd>
            <dt>场景</dt>
            <dd>{{ activeDevice.scene }}</dd>
            <dt>协议</dt>
            <dd>{{ activeDevice.protocol }}</dd>
            <dt>兜底</dt>
            <dd>{{ activeDevice.fallback }}</dd>
          </dl>
        </section>

        <section class="evidence-slot-panel">
          <div class="panel-title-row">
            <span class="eyebrow">ACTIVE TASK</span>
            <StatusBadge :label="activeTask ? `${activeTask.health}%` : '暂无'" :tone="activeTask ? taskTone(activeTask) : 'gray'" />
          </div>
          <div v-if="activeTask" class="rule-stack">
            <article>
              <strong>{{ activeTask.title }}</strong>
              <span>{{ activeTask.target }} / {{ activeTask.lastRun }}</span>
            </article>
            <article>
              <strong>下一步</strong>
              <span>{{ activeTask.nextAction }} / {{ activeTask.owner }}</span>
            </article>
          </div>
        </section>
      </aside>

    <section class="module-detail-grid hardware-detail-grid" aria-label="POC 记录和部署拓扑">
      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">POC RECORDS</span>
          <StatusBadge :label="`${workspace.pocRecords.length} 条`" tone="blue" />
        </div>
        <div class="hardware-poc-list">
          <article v-for="record in workspace.pocRecords" :key="record.code" :class="`tone-${pocTone(record)}`">
            <div>
              <strong>{{ record.code }}</strong>
              <span>{{ record.scene }} / {{ record.testWindow }}</span>
              <small>{{ record.evidence }}</small>
            </div>
            <StatusBadge :label="record.result" :tone="pocTone(record)" />
          </article>
        </div>
      </section>

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">TASK HEALTH</span>
          <StatusBadge :label="`${workspace.summary.averageHealth}%`" tone="green" />
        </div>
        <div class="integration-list compact-integration-list">
          <article v-for="task in workspace.tasks" :key="task.id">
            <div class="integration-main">
              <strong>{{ task.title }}</strong>
              <span>{{ task.target }}</span>
              <small>{{ task.lastRun }} / {{ task.owner }}</small>
            </div>
            <div class="health-meter">
              <span>{{ task.health }}%</span>
              <i><b :style="{ width: `${task.health}%` }" /></i>
            </div>
          </article>
        </div>
      </section>

    </section>
    </section>

    <section class="section-block" aria-label="设备网关读数接入">
      <div class="section-heading">
        <div>
          <h2>设备网关读数接入</h2>
          <p>
            低成本硬件（串口转 HTTP 桥、ESP32、浏览器桥、扫码枪中继）无需厂商 SDK，凭设备密钥即可推送标准化读数。
            读数经归一化后按 READY / WARNING / BLOCKED 分级，原始报文保留供人工兜底。
          </p>
        </div>
        <StatusBadge label="设备密钥鉴权 / 非人工登录" tone="violet" />
      </div>

      <div v-if="gatewayError" class="operation-feedback warning">
        <strong>设备网关接口不可用</strong>
        <span>{{ gatewayError }}</span>
        <small>请检查网关连接和设备配置后重试。</small>
      </div>

      <div class="module-stat-grid">
        <article class="tone-blue">
          <span>注册设备</span>
          <strong>{{ gatewaySummary.totalDevices }}<small>个</small></strong>
          <em>启用 {{ gatewaySummary.enabledDevices }}</em>
        </article>
        <article class="tone-green">
          <span>READY 读数</span>
          <strong>{{ gatewaySummary.readyReadings }}<small>条</small></strong>
          <em>可直接消费</em>
        </article>
        <article class="tone-amber">
          <span>WARNING</span>
          <strong>{{ gatewaySummary.warningReadings }}<small>条</small></strong>
          <em>需人工确认</em>
        </article>
        <article class="tone-red">
          <span>BLOCKED</span>
          <strong>{{ gatewaySummary.blockedReadings }}<small>条</small></strong>
          <em>保留原始报文</em>
        </article>
      </div>

      <div class="split-layout wide-left">
        <details v-if="isAdmin" class="section-block" aria-label="管理员诊断区">
          <summary>管理员诊断区（模拟设备推送）</summary>
          <div class="section-heading">
            <div>
              <h3>模拟设备推送</h3>
              <p>仅用于现场联调。提交会写入真实网关读数记录，请使用专用诊断设备。</p>
            </div>
          </div>
          <form class="gateway-form" @submit.prevent="submitGatewayReading">
            <label>
              <span>设备编码</span>
              <input v-model="gatewayForm.deviceCode" aria-label="设备编码" />
            </label>
            <label>
              <span>设备密钥</span>
              <input v-model="gatewayForm.deviceKey" type="password" placeholder="请输入诊断设备密钥" aria-label="设备密钥" />
            </label>
            <label>
              <span>读数类型</span>
              <select v-model="gatewayForm.readingType" aria-label="读数类型">
                <option v-for="option in readingTypeOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
            <label>
              <span>原始报文</span>
              <input v-model="gatewayForm.rawPayload" aria-label="原始报文" />
            </label>
            <label>
              <span>业务对象类型</span>
              <input v-model="gatewayForm.businessRefType" placeholder="可选，如 子工单" aria-label="业务对象类型" />
            </label>
            <label>
              <span>业务对象编码</span>
              <input v-model="gatewayForm.businessRefCode" placeholder="可选，如 ZD-20260706-001" aria-label="业务对象编码" />
            </label>
            <button class="primary-button" type="submit" :disabled="gatewaySubmitting">
              {{ gatewaySubmitting ? '接入中…' : '推送读数' }}
            </button>
          </form>
          <div v-if="gatewayFeedback" class="operation-feedback">
            <span>{{ gatewayFeedback }}</span>
          </div>
        </details>

        <div class="section-block">
          <div class="section-heading">
            <div>
              <h3>已注册设备</h3>
              <p>凭密钥推送读数，停用设备立即拒绝接入。设备管理需管理员权限。</p>
            </div>
            <StatusBadge label="ADMIN 管理" tone="violet" />
          </div>
          <div class="integration-list compact-integration-list">
            <article v-for="device in gatewayDevices" :key="device.deviceCode">
              <div class="integration-main">
                <strong>{{ device.deviceCode }} / {{ device.deviceName }}</strong>
                <span>{{ device.acceptedTypes.join('、') }}</span>
                <small>{{ device.location }} / {{ device.fallbackNote }}</small>
              </div>
              <StatusBadge :label="device.enabled ? '启用' : '停用'" :tone="device.enabled ? 'green' : 'gray'" />
              <p>近期读数 {{ device.recentReadings }} 条 / {{ device.lastReadingAt || '暂无' }}</p>
              <div class="device-admin-actions">
                <button class="table-action" type="button" @click="toggleDevice(device)">
                  {{ device.enabled ? '停用' : '启用' }}
                </button>
                <button class="table-action" type="button" @click="rotateDeviceKey(device)">轮换密钥</button>
              </div>
            </article>
            <p v-if="gatewayDevices.length === 0" class="empty-hint">暂无注册设备或后端未连接。</p>
          </div>

          <form class="gateway-form" @submit.prevent="registerDevice">
            <label>
              <span>新设备编码</span>
              <input v-model="deviceForm.deviceCode" placeholder="如 SCALE-02" aria-label="新设备编码" />
            </label>
            <label>
              <span>设备名称</span>
              <input v-model="deviceForm.deviceName" placeholder="如 二号称重台" aria-label="设备名称" />
            </label>
            <label>
              <span>主读数类型</span>
              <select v-model="deviceForm.acceptedType" aria-label="主读数类型">
                <option v-for="option in readingTypeOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
            <label>
              <span>设备密钥</span>
              <input v-model="deviceForm.deviceKey" type="password" placeholder="新建必填" aria-label="设备密钥" />
            </label>
            <label>
              <span>部署位置</span>
              <input v-model="deviceForm.location" placeholder="可选" aria-label="部署位置" />
            </label>
            <label>
              <span>失败兜底</span>
              <input v-model="deviceForm.fallbackNote" placeholder="可选" aria-label="失败兜底" />
            </label>
            <button class="primary-button" type="submit">注册 / 更新设备</button>
          </form>
          <div v-if="deviceAdminFeedback" class="operation-feedback">
            <span>{{ deviceAdminFeedback }}</span>
          </div>
        </div>
      </div>

      <div class="gateway-posting-toggle">
        <label class="posting-check">
          <input v-model="postingForm.enabled" type="checkbox" />
          <span>联动报工：消费称重/扫码读数时同时写库存台账（WIP → 半成品）</span>
        </label>
        <div v-if="postingForm.enabled" class="gateway-form posting-form">
          <label><span>客户ID</span><input v-model="postingForm.customerId" placeholder="必填，如 1001" /></label>
          <label><span>母单编码</span><input v-model="postingForm.motherOrderCode" placeholder="必填" /></label>
          <label><span>工单编码</span><input v-model="postingForm.workOrderCode" placeholder="扫码读数可留空" /></label>
          <label><span>物料编码</span><input v-model="postingForm.materialCode" placeholder="必填" /></label>
          <label><span>源仓</span><input v-model="postingForm.fromWarehouseCode" /></label>
          <label><span>目标仓</span><input v-model="postingForm.toWarehouseCode" /></label>
          <label><span>数量</span><input v-model="postingForm.quantity" placeholder="必填，件数" /></label>
          <label><span>工序</span><input v-model="postingForm.process" placeholder="可选" /></label>
        </div>
      </div>

      <div class="module-table-wrap refined-table-wrap desk-table-wrap">
        <table class="module-table">
          <thead>
            <tr>
              <th>读数编码</th>
              <th>设备</th>
              <th>类型</th>
              <th>归一化</th>
              <th>状态</th>
              <th>原始报文</th>
              <th>接收时间</th>
              <th>消费</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="reading in gatewayReadings" :key="reading.readingCode">
              <td><b>{{ reading.readingCode }}</b></td>
              <td>{{ reading.deviceCode }}</td>
              <td>{{ reading.readingType }}</td>
              <td>{{ reading.normalizedValue }}{{ reading.normalizedUnit }} {{ reading.normalizedCode }}</td>
              <td><StatusBadge :label="reading.status" :tone="gatewayStatusTone(reading.status)" /></td>
              <td>{{ reading.rawPayload }}</td>
              <td>{{ reading.receivedAt }}</td>
              <td>
                <StatusBadge v-if="gatewayConsumedCodes.has(reading.readingCode)" label="已消费" tone="gray" />
                <button
                  v-else
                  class="table-action"
                  type="button"
                  @click="consumeGatewayReading(reading)"
                >
                  {{ intentLabels[defaultIntentFor(reading)] }}
                </button>
              </td>
            </tr>
            <tr v-if="gatewayReadings.length === 0">
              <td colspan="8" class="empty-hint">暂无读数记录。连接后端并推送读数后展示。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h3>读数消费记录</h3>
            <p>消费是可追责的人工动作：记录谁把设备读数转成业务信号，用于与业务台账对账。消费本身不写库存台账。</p>
          </div>
          <StatusBadge label="RBAC 守卫 / 一次消费" tone="green" />
        </div>
        <div class="module-table-wrap refined-table-wrap desk-table-wrap">
          <table class="module-table">
            <thead>
              <tr>
                <th>消费编码</th>
                <th>读数</th>
                <th>意图</th>
                <th>归一化</th>
                <th>业务对象</th>
                <th>台账</th>
                <th>操作人</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="consumption in gatewayConsumptions" :key="consumption.consumptionCode">
                <td><b>{{ consumption.consumptionCode }}</b></td>
                <td>{{ consumption.readingCode }}</td>
                <td><StatusBadge :label="intentLabels[consumption.intent]" tone="blue" /></td>
                <td>{{ consumption.normalizedValue }}{{ consumption.normalizedUnit }} {{ consumption.normalizedCode }}</td>
                <td>{{ consumption.businessRefType }} {{ consumption.businessRefCode }}</td>
                <td>
                  <StatusBadge
                    v-if="consumption.ledgerCode"
                    :label="consumption.ledgerCode"
                    tone="green"
                  />
                  <span v-else class="muted">仅留痕</span>
                </td>
                <td>{{ consumption.operator }} / {{ consumption.role }}</td>
                <td>{{ consumption.consumedAt }}</td>
              </tr>
              <tr v-if="gatewayConsumptions.length === 0">
                <td colspan="8" class="empty-hint">暂无消费记录。</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>
