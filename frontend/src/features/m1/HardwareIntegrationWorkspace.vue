<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  hardwareDevices as fallbackHardwareDevices,
  hardwarePocRecords as fallbackHardwarePocRecords,
  integrationTasks as fallbackIntegrationTasks,
  type HardwareDevice,
  type HardwarePocRecord,
  type IntegrationTask,
  type Tone,
} from '../../data/m1PrototypeData';
import { evidenceAttachments } from '../../data/documentManagementData';
import {
  fetchHardwareWorkspace,
  type HardwareAdapterPlan,
  type HardwarePocEvidenceSummary,
  type HardwareProtocolReviewItem,
  type HardwareProtocolSample,
  type HardwareWorkspaceModel,
} from '../../services/hardwareIntegrationApi';

const workspace = ref<HardwareWorkspaceModel>({
  devices: fallbackHardwareDevices,
  tasks: fallbackIntegrationTasks,
  adapterPlans: fallbackHardwareAdapterPlans(),
  protocolSamples: fallbackHardwareProtocolSamples(),
  protocolReviews: fallbackHardwareProtocolReviews(),
  pocRecords: fallbackHardwarePocRecords,
  pocEvidence: fallbackHardwarePocEvidence(),
  summary: {
    totalDevices: fallbackHardwareDevices.length,
    fallbackRequired: fallbackHardwareDevices.filter((device) => device.status !== 'online').length,
    averageHealth: Math.round(
      fallbackIntegrationTasks.reduce((total, task) => total + task.health, 0) / fallbackIntegrationTasks.length,
    ),
    pocRecords: fallbackHardwarePocRecords.length,
    pocBlocked: fallbackHardwarePocRecords.filter((record) => record.result === '阻断').length,
  },
});
const loadingError = ref('');

const activeDevice = computed(
  () =>
    workspace.value.devices.find((device) => device.status !== 'online') ??
    workspace.value.devices[0],
);

const activeTask = computed(
  () => [...workspace.value.tasks].sort((a, b) => a.health - b.health)[0],
);

const hardwareDevices = computed(() => workspace.value.devices);
const hardwareAdapterPlans = computed(() => workspace.value.adapterPlans);
const hardwareProtocolSamples = computed(() => workspace.value.protocolSamples);
const hardwareProtocolReviews = computed(() => workspace.value.protocolReviews);
const hardwarePocRecords = computed(() => workspace.value.pocRecords);
const hardwarePocEvidence = computed(() => workspace.value.pocEvidence);
const integrationTasks = computed(() => workspace.value.tasks);

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

onMounted(async () => {
  try {
    workspace.value = await fetchHardwareWorkspace();
  } catch (error) {
    loadingError.value = error instanceof Error ? error.message : '硬件接口不可用，已使用本地样例';
  }
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

function pocTone(record: HardwarePocRecord): Tone {
  return record.tone;
}

function taskTone(task: IntegrationTask): Tone {
  if (task.health < 60) return 'red';
  if (task.health < 80) return 'amber';
  return 'green';
}

function getHardwarePocBlockedCount(): number {
  return workspace.value.summary.pocBlocked;
}

function fallbackHardwarePocEvidence(): HardwarePocEvidenceSummary[] {
  return fallbackHardwarePocRecords.map((record) => {
    const attachments = evidenceAttachments.filter(
      (attachment) => attachment.businessType === '硬件 POC' && attachment.businessCode === record.code,
    );
    if (attachments.length === 0) {
      return {
        pocCode: record.code,
        scene: record.scene,
        result: record.result,
        documentCode: '',
        attachmentCount: 0,
        missingEvidence: 1,
        attachmentCodes: [],
        latestUploadedAt: '',
        status: '待补证据',
        nextAction: '补传现场 POC 原始照片、视频或日志',
        tone: 'red',
      };
    }
    const latest = attachments[attachments.length - 1];
    return {
      pocCode: record.code,
      scene: record.scene,
      result: record.result,
      documentCode: latest.documentCode,
      attachmentCount: attachments.length,
      missingEvidence: attachments.filter((attachment) => attachment.status.includes('缺失')).length,
      attachmentCodes: attachments.map((attachment) => attachment.code),
      latestUploadedAt: latest.uploadedAt,
      status: '已关联证据',
      nextAction: latest.fallbackAction,
      tone: latest.tone,
    };
  });
}

function fallbackHardwareAdapterPlans(): HardwareAdapterPlan[] {
  return fallbackHardwareDevices.map((device) => ({
    deviceCode: device.code,
    deviceName: device.name,
    phase: '一期',
    primaryAdapter: device.protocol,
    fallbackAdapter: device.fallback,
    evidenceRequirement: evidenceRequirementFor(device.code),
    nextIntegrationAction: nextIntegrationActionFor(device.code),
    riskLevel: device.status === 'manual' ? '高' : device.status === 'degraded' ? '中' : '低',
    tone: device.status === 'manual' ? 'red' : device.status === 'degraded' ? 'amber' : 'green',
  }));
}

function fallbackHardwareProtocolSamples(): HardwareProtocolSample[] {
  return [
    {
      deviceCode: 'CAM-UNPACK-01',
      deviceName: '拆包台摄像头',
      protocolType: 'RTSP / ONVIF / FFmpeg',
      sampleStatus: '待复测',
      requiredParameters: ['nvrIp', 'channel', 'startTime', 'endTime', 'timeSyncOffset', 'ffmpegCommand'],
      samplePayload: 'rtsp://nvr.local/ch01?start=20260706T084000&end=20260706T091000',
      evidenceCode: 'HW-PROTO-CAM-20260706-001',
      captureMethod: 'NVR 时间段回放截图 + FFmpeg dry-run 日志',
      nextReviewAction: '补齐 NVR 品牌、通道号、NTP 校时偏差和截取失败日志样本',
      riskLevel: '中',
      tone: 'amber',
    },
    {
      deviceCode: 'PRN-LABEL-01',
      deviceName: '标签打印机',
      protocolType: 'Windows driver / Browser print / PDF',
      sampleStatus: '样本已冻结',
      requiredParameters: ['labelSize', 'dpi', 'printerName', 'templateCode'],
      samplePayload: 'PDF-LABEL-MD-20260706-001',
      evidenceCode: 'HW-PROTO-PRN-20260706-001',
      captureMethod: '浏览器打印预览截图 + PDF 标签模板 checksum',
      nextReviewAction: '确认标签纸规格、DPI、打印机名称和补打权限',
      riskLevel: '低',
      tone: 'blue',
    },
    {
      deviceCode: 'SCAN-PC-01',
      deviceName: 'PC 扫码枪',
      protocolType: 'USB HID',
      sampleStatus: '样本已冻结',
      requiredParameters: ['barcodeFormat', 'prefix', 'suffix', 'scanDistance', 'keyboardLayout'],
      samplePayload: 'WO|ZD-20260706-001|PROC=POLISH',
      evidenceCode: 'HW-PROTO-SCAN-20260706-001',
      captureMethod: '扫码输入录屏 + 原始条码照片',
      nextReviewAction: '确认条码格式、前后缀、键盘布局和弱网场景',
      riskLevel: '低',
      tone: 'green',
    },
    {
      deviceCode: 'PWA-CAM-01',
      deviceName: '手机摄像头扫码',
      protocolType: 'BarcodeDetector / ZXing fallback',
      sampleStatus: '待复测',
      requiredParameters: ['browser', 'androidVersion', 'lighting', 'decodeMs'],
      samplePayload: 'PWA-SCAN-SCREENSHOT-001',
      evidenceCode: 'HW-PROTO-PWA-20260706-001',
      captureMethod: '弱光扫码截图 + 识别耗时日志',
      nextReviewAction: '补齐弱光、反光、低端安卓机型和 ZXing 兜底样本',
      riskLevel: '中',
      tone: 'amber',
    },
    {
      deviceCode: 'SCALE-01',
      deviceName: '电子秤',
      protocolType: 'RS232 / USB / TCP reserved + manual reading',
      sampleStatus: '接口待确认',
      requiredParameters: ['interfaceType', 'baudRate', 'dataBits', 'parity', 'tcpHost', 'unit', 'precision'],
      samplePayload: 'ST,GS,+000123.45,g',
      evidenceCode: 'HW-PROTO-SCALE-20260706-001',
      captureMethod: '人工读数照片 + 串口/TCP 报文样本占位',
      nextReviewAction: '确认 RS232/USB/TCP 接口类型、波特率、精度、校准周期和原始报文',
      riskLevel: '高',
      tone: 'red',
    },
  ];
}

function fallbackHardwareProtocolReviews(): HardwareProtocolReviewItem[] {
  return [
    {
      reviewCode: 'HW-REV-CAM-20260710-001',
      deviceCode: 'CAM-UNPACK-01',
      deviceName: '拆包台摄像头',
      protocolType: 'RTSP / ONVIF / FFmpeg',
      reviewStatus: '待现场复核',
      deviceModelStatus: 'NVR 品牌/通道待冻结',
      confirmedParameters: ['startTime', 'endTime', 'ffmpegCommand'],
      pendingParameters: ['nvrIp', 'channel', 'timeSyncOffset'],
      evidenceCode: 'HW-PROTO-CAM-20260706-001',
      owner: '实施顾问',
      reviewWindow: '2026-07-10 09:00-10:00',
      blocker: 'NTP 校时偏差未复核',
      nextAction: '现场读取 NVR 品牌、通道号和 NTP 偏差后更新 POC 证据',
      riskLevel: '中',
      tone: 'amber',
    },
    {
      reviewCode: 'HW-REV-PRN-20260710-001',
      deviceCode: 'PRN-LABEL-01',
      deviceName: '标签打印机',
      protocolType: 'Windows driver / Browser print / PDF',
      reviewStatus: '参数基本冻结',
      deviceModelStatus: 'Windows 默认打印机待绑定',
      confirmedParameters: ['labelSize', 'dpi', 'templateCode'],
      pendingParameters: ['printerName'],
      evidenceCode: 'HW-PROTO-PRN-20260706-001',
      owner: '库管员',
      reviewWindow: '2026-07-10 10:00-10:30',
      blocker: '现场默认打印机名称未冻结',
      nextAction: '确认打印机名称、驱动版本和补打权限后补标签样张',
      riskLevel: '低',
      tone: 'blue',
    },
    {
      reviewCode: 'HW-REV-SCAN-20260710-001',
      deviceCode: 'SCAN-PC-01',
      deviceName: 'PC 扫码枪',
      protocolType: 'USB HID',
      reviewStatus: '参数已冻结',
      deviceModelStatus: 'USB HID 通用扫码枪',
      confirmedParameters: ['barcodeFormat', 'prefix', 'suffix', 'scanDistance', 'keyboardLayout'],
      pendingParameters: [],
      evidenceCode: 'HW-PROTO-SCAN-20260706-001',
      owner: '库管员',
      reviewWindow: '2026-07-10 10:30-11:00',
      blocker: '无',
      nextAction: '保持扫码样本、误扫样本和手工确认记录',
      riskLevel: '低',
      tone: 'green',
    },
    {
      reviewCode: 'HW-REV-PWA-20260710-001',
      deviceCode: 'PWA-CAM-01',
      deviceName: '手机摄像头扫码',
      protocolType: 'BarcodeDetector / ZXing fallback',
      reviewStatus: '待弱光复核',
      deviceModelStatus: '低端安卓机型待冻结',
      confirmedParameters: ['browser', 'androidVersion'],
      pendingParameters: ['lighting', 'decodeMs'],
      evidenceCode: 'HW-PROTO-PWA-20260706-001',
      owner: '车间组长',
      reviewWindow: '2026-07-10 14:00-14:40',
      blocker: '弱光和反光场景样本不足',
      nextAction: '补弱光、反光和低端安卓扫码耗时样本',
      riskLevel: '中',
      tone: 'amber',
    },
    {
      reviewCode: 'HW-REV-SCALE-20260710-001',
      deviceCode: 'SCALE-01',
      deviceName: '电子秤',
      protocolType: 'RS232 / USB / TCP reserved + manual reading',
      reviewStatus: '接口待确认',
      deviceModelStatus: '电子秤型号和接口类型待冻结',
      confirmedParameters: ['unit', 'precision'],
      pendingParameters: ['interfaceType', 'baudRate', 'dataBits', 'parity', 'tcpHost'],
      evidenceCode: 'HW-PROTO-SCALE-20260706-001',
      owner: '质检员',
      reviewWindow: '2026-07-10 15:00-15:40',
      blocker: 'RS232/USB/TCP 接口类型未确认',
      nextAction: '确认接口类型、波特率、校准周期并保留原始报文照片',
      riskLevel: '高',
      tone: 'red',
    },
  ];
}

function evidenceRequirementFor(deviceCode: string): string {
  if (deviceCode.startsWith('CAM')) return '拆包视频、NVR 通道、截取时间段、附件 checksum';
  if (deviceCode.startsWith('PRN')) return '标签样张、补打日志、母单/子工单编码';
  if (deviceCode.startsWith('SCAN')) return '扫码样本、错误码、手工确认人和时间';
  if (deviceCode.startsWith('PWA')) return '扫码截图、识别耗时、失败原因和手工输入记录';
  return '称重照片、重量值、误差说明、操作人和复核人';
}

function nextIntegrationActionFor(deviceCode: string): string {
  if (deviceCode.startsWith('CAM')) return '补齐现场 NVR 品牌、通道号、时间同步和失败日志样本';
  if (deviceCode.startsWith('PRN')) return '冻结标签尺寸、纸张规格和补打权限';
  if (deviceCode.startsWith('SCAN')) return '确认条码格式、扫码距离和弱网场景';
  if (deviceCode.startsWith('PWA')) return '补弱光、反光和低端安卓机型复测样本';
  return '确认接口类型、精度、校准周期和串口/TCP 采集样本';
}
</script>

<template>
  <div class="hardware-desk page-stack">
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
              <input :value="activeDevice?.code ?? '暂无设备'" aria-label="设备或业务编码" />
            </label>
            <button class="primary-button" type="button">新建 POC</button>
            <button class="secondary-button" type="button">导出清单</button>
          </div>
        </div>

        <div class="module-table-wrap refined-table-wrap">
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
              <tr v-for="device in workspace.devices" :key="device.code">
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
      </div>

      <aside class="module-side-panel">
        <section v-if="activeDevice" class="active-object-card tone-amber">
          <div class="panel-title-row">
            <span class="eyebrow">ACTIVE DEVICE</span>
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
          <div v-if="loadingError" class="operation-feedback warning">
            <strong>硬件 API 暂不可用</strong>
            <span>{{ loadingError }}</span>
            <small>当前使用本地样例，真实设备状态需以后端接口为准。</small>
          </div>
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
    </section>

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

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">POC EVIDENCE</span>
          <StatusBadge label="只读关联" tone="amber" />
        </div>
        <div class="integration-list compact-integration-list">
          <article v-for="evidence in hardwarePocEvidence" :key="evidence.pocCode">
            <div class="integration-main">
              <strong>{{ evidence.pocCode }}</strong>
              <span>{{ evidence.status }} / {{ evidence.documentCode || '未建证据包' }}</span>
              <small>{{ evidence.attachmentCodes.join('、') || '待补现场附件' }}</small>
            </div>
            <div class="health-meter">
              <span>{{ evidence.attachmentCount }} 件</span>
              <i><b :style="{ width: `${evidence.missingEvidence > 0 ? 35 : 100}%` }" /></i>
            </div>
            <p>{{ evidence.nextAction }}</p>
          </article>
        </div>
      </section>

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">ADAPTER PLANS</span>
          <StatusBadge label="低成本优先" tone="blue" />
        </div>
        <div class="integration-list compact-integration-list">
          <article v-for="plan in hardwareAdapterPlans" :key="plan.deviceCode">
            <div class="integration-main">
              <strong>{{ plan.deviceName }} / {{ plan.phase }}</strong>
              <span>{{ plan.primaryAdapter }}</span>
              <small>{{ plan.fallbackAdapter }}</small>
            </div>
            <StatusBadge :label="`风险 ${plan.riskLevel}`" :tone="plan.tone" />
            <p>{{ plan.evidenceRequirement }}；{{ plan.nextIntegrationAction }}</p>
          </article>
        </div>
      </section>

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">PROTOCOL SAMPLES</span>
          <StatusBadge label="复测留痕" tone="amber" />
        </div>
        <div class="integration-list compact-integration-list">
          <article v-for="sample in hardwareProtocolSamples" :key="sample.evidenceCode">
            <div class="integration-main">
              <strong>{{ sample.deviceName }} / {{ sample.protocolType }}</strong>
              <span>{{ sample.sampleStatus }} / {{ sample.samplePayload }}</span>
              <small>{{ sample.evidenceCode }} / {{ sample.captureMethod }}</small>
            </div>
            <StatusBadge :label="`风险 ${sample.riskLevel}`" :tone="sample.tone" />
            <p>{{ sample.requiredParameters.join('、') }}；{{ sample.nextReviewAction }}</p>
          </article>
        </div>
      </section>

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">PROTOCOL REVIEW</span>
          <StatusBadge label="参数复核" tone="red" />
        </div>
        <div class="integration-list compact-integration-list">
          <article v-for="review in hardwareProtocolReviews" :key="review.reviewCode">
            <div class="integration-main">
              <strong>{{ review.deviceName }} / {{ review.reviewStatus }}</strong>
              <span>{{ review.deviceModelStatus }} / {{ review.reviewWindow }}</span>
              <small>{{ review.reviewCode }} / {{ review.owner }} / {{ review.evidenceCode }}</small>
            </div>
            <StatusBadge :label="`风险 ${review.riskLevel}`" :tone="review.tone" />
            <p>
              已确认 {{ review.confirmedParameters.join('、') || '无' }}；
              待复核 {{ review.pendingParameters.join('、') || '无' }}；
              {{ review.blocker }}；{{ review.nextAction }}
            </p>
          </article>
        </div>
      </section>

      <section class="module-rule-panel">
        <div class="panel-title-row">
          <span class="eyebrow">TOPOLOGY</span>
          <StatusBadge label="兼容方案" tone="amber" />
        </div>
        <div class="topology-map compact-topology-map">
          <span>PC 后台</span>
          <span>PWA 手机</span>
          <strong>Spring Boot API</strong>
          <span>MySQL</span>
          <span>NAS/MinIO</span>
          <span>NVR/摄像头</span>
          <span>标签打印</span>
          <span>扫码枪/电子秤</span>
        </div>
      </section>
    </section>

    <section class="operations-hero hardware-hero">
      <div class="hero-copy">
        <span class="eyebrow">Hardware Link</span>
        <h2>软硬件联动指挥台</h2>
        <p>扫码、标签、视频、称重按兼容方案先上线；失败任务进入兜底队列，业务单据继续闭环。</p>
      </div>
      <div class="hero-kpis">
        <article>
          <span>在线设备</span>
          <strong>1</strong>
        </article>
        <article>
          <span>人工兜底</span>
          <strong>2</strong>
        </article>
        <article>
          <span>POC 阻断</span>
          <strong>{{ getHardwarePocBlockedCount() }}</strong>
        </article>
      </div>
    </section>

    <section class="hardware-matrix-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>设备兼容矩阵</h2>
            <p>按设备、协议、现场动作和兜底方式管理，不绑定具体型号。</p>
          </div>
          <button class="secondary-button" type="button">导出设备清单</button>
        </div>
        <div class="device-ops-table">
          <div class="device-ops-row device-ops-head">
            <span>设备</span>
            <span>场景</span>
            <span>协议</span>
            <span>状态</span>
            <span>失败兜底</span>
          </div>
          <div v-for="device in hardwareDevices" :key="device.code" class="device-ops-row">
            <div>
              <small>{{ device.code }}</small>
              <strong>{{ device.name }}</strong>
            </div>
            <span>{{ device.scene }}</span>
            <span>{{ device.protocol }}</span>
            <StatusBadge :label="deviceLabel(device.status)" :tone="deviceTone(device.status)" />
            <span>{{ device.fallback }}</span>
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>联动边界</h2>
            <p>一期只承诺业务可用和证据可追溯。</p>
          </div>
        </div>
        <div class="boundary-list">
          <article>
            <strong>视频</strong>
            <span>NVR 时间段截取优先，失败后人工上传 MP4。</span>
          </article>
          <article>
            <strong>标签</strong>
            <span>浏览器打印优先，PDF 标签模板兜底。</span>
          </article>
          <article>
            <strong>称重</strong>
            <span>一期人工读数录入，二期再接 RS232/USB/TCP。</span>
          </article>
          <article>
            <strong>扫码</strong>
            <span>扫码失败允许手工输入编码并二次确认。</span>
          </article>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>现场 POC 记录</h2>
          <p>每次硬件验证必须记录时间窗口、结果、证据、阻断项和下一步，避免只用口头结论推进集成。</p>
        </div>
        <button class="secondary-button" type="button">导出 POC 记录</button>
      </div>
      <div class="poc-record-grid">
        <article v-for="record in hardwarePocRecords" :key="record.code" :class="`tone-${record.tone}`">
          <div class="poc-record-head">
            <div>
              <small>{{ record.code }} / {{ record.deviceCode }}</small>
              <strong>{{ record.scene }}</strong>
            </div>
            <StatusBadge :label="record.result" :tone="record.tone" />
          </div>
          <dl>
            <dt>窗口</dt>
            <dd>{{ record.testWindow }}</dd>
            <dt>证据</dt>
            <dd>{{ record.evidence }}</dd>
            <dt>阻断</dt>
            <dd>{{ record.blocker }}</dd>
            <dt>负责人</dt>
            <dd>{{ record.owner }}</dd>
          </dl>
          <p>{{ record.nextAction }}</p>
        </article>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>联动任务健康度</h2>
            <p>把硬件调用、失败处理和人工上传都纳入统一任务队列。</p>
          </div>
        </div>
        <div class="integration-list">
          <article v-for="task in integrationTasks" :key="task.id">
            <div class="integration-main">
              <strong>{{ task.title }}</strong>
              <span>{{ task.target }}</span>
              <small>{{ task.lastRun }} / {{ task.owner }}</small>
            </div>
            <div class="health-meter">
              <span>{{ task.health }}%</span>
              <i><b :style="{ width: `${task.health}%` }" /></i>
            </div>
            <p>{{ task.nextAction }}</p>
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>部署拓扑</h2>
            <p>最小部署先跑通单体服务、数据库、文件存储和现场设备。</p>
          </div>
        </div>
        <div class="topology-map">
          <span>PC 后台</span>
          <span>PWA 手机</span>
          <strong>Spring Boot API</strong>
          <span>MySQL</span>
          <span>NAS/对象存储</span>
          <span>NVR/摄像头</span>
          <span>标签打印</span>
          <span>扫码枪/电子秤</span>
        </div>
      </div>
    </section>
  </div>
</template>
