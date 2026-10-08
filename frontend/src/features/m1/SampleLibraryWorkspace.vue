<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  acceptanceDataSets,
  hardwarePocRecords,
  openSourceSampleMappings,
  productionReadinessItems,
  sampleScenarios,
  type Tone,
} from '../../data/m1PrototypeData';
import {
  buildRegisterImportPostingPlan,
  registerCoverageTone,
  registerFieldCoverage,
  registerImportReviewBatches,
  registerImportSummary,
  registerImportTemplates,
  registerProcessCoverage,
  registerRuleTone,
  type RegisterImportBatch,
  type RegisterImportBatchRow,
  type RegisterImportPostingDraft,
  type RegisterImportPostingExecutionResult,
  type RegisterImportPostingPlan,
  type RegisterImportPreview,
  type RegisterImportPreviewRow,
} from '../../data/registerImportTemplates';
import {
  correctRegisterImportRow,
  confirmRegisterImport,
  fetchRegisterImportBatch,
  fetchRegisterImportBatches,
  fetchRegisterImportPostingPlan,
  postRegisterImportBatch,
  previewRegisterImport,
} from '../../services/registerImportApi';
import {
  fetchMachineOutputsByImportBatch,
  type MachineOutputRecord,
} from '../../services/machineOutputApi';
import {
  fetchMotherOrder,
  fetchOutboundOrder,
  type MotherOrderRecord,
  type OutboundOrderRecord,
} from '../../services/businessDocumentApi';
import {
  buildRegisterPreviewIssueCsv,
  registerPreviewIssueCount,
  registerPreviewIssueFileName,
} from './registerPreviewExport';

function scenarioTone(status: string): Tone {
  if (status === 'ready') return 'green';
  if (status === 'blocked') return 'red';
  return 'amber';
}

function scenarioLabel(status: string) {
  if (status === 'ready') return '可演示';
  if (status === 'blocked') return '阻塞';
  return '待补数据';
}

function mappingLabel(status: string) {
  if (status === 'applied') return '已映射';
  if (status === 'watching') return '观察中';
  return '预留';
}

function mappingTone(status: string): Tone {
  if (status === 'applied') return 'green';
  if (status === 'watching') return 'blue';
  return 'gray';
}

function dataSetLabel(status: string) {
  if (status === 'active') return '测试中';
  if (status === 'blocked') return '阻塞';
  return '待现场数据';
}

function dataSetTone(status: string): Tone {
  if (status === 'active') return 'green';
  if (status === 'blocked') return 'red';
  return 'amber';
}

function readinessStatusLabel(status: string) {
  if (status === 'confirmed') return '已确认';
  if (status === 'needs-trial') return '需试点';
  if (status === 'deferred') return '延期';
  return '待确认';
}

function parserStatusLabel(status: string) {
  if (status === 'COMMAND_READY') return '预览就绪';
  return '解析器待接入';
}

function coverageStatusLabel(status: string) {
  if (status === 'covered') return '已覆盖';
  if (status === 'partial') return '待结构化';
  return '未覆盖';
}

function keyFieldLabel(template: (typeof registerImportTemplates)[number]) {
  return template.fields.find((field) =>
    field.targetField.includes('paperExpressText')
    || field.targetField.includes('operatorSignatureName')
    || field.targetField.includes('handoffAuditStatus')
  )?.targetField ?? template.fields[0]?.targetField ?? '待映射';
}

type RegisterRowStatus =
  | RegisterImportPreviewRow['status']
  | RegisterImportBatchRow['status']
  | RegisterImportPostingDraft['status'];

function previewStatusTone(status: RegisterRowStatus): Tone {
  if (status === 'READY') return 'green';
  if (status === 'POSTED') return 'green';
  if (status === 'WARNING') return 'amber';
  return 'red';
}

function batchStatusTone(status: RegisterImportBatch['status']): Tone {
  if (status === 'CONFIRMED') return 'blue';
  if (status === 'POSTED') return 'green';
  return 'red';
}

function batchStatusLabel(status: RegisterImportBatch['status']) {
  if (status === 'CONFIRMED') return '待入账映射';
  if (status === 'POSTED') return '已入业务单据';
  return '已退回';
}

function previewFieldEntries(row: Pick<RegisterImportPreviewRow | RegisterImportBatchRow, 'normalizedFields'>) {
  return Object.entries(row.normalizedFields).slice(0, 6);
}

function postingFieldEntries(draft: RegisterImportPostingDraft) {
  return Object.entries(draft.mappedFields).filter(([, value]) => value).slice(0, 8);
}

function machineOutputQuantity(record: MachineOutputRecord) {
  return `${record.outputQuantity}${record.quantityUnit || ''}`;
}

function documentQuantity(quantity: number | string, unit: string) {
  return `${quantity}${unit || ''}`;
}

function sourceLineText(sourceRegisterType: string, sourceRegisterLineNo?: number) {
  if (!sourceRegisterType && sourceRegisterLineNo == null) {
    return '未记录来源登记表';
  }
  return `${sourceRegisterType || '来源登记表'} / 第 ${sourceRegisterLineNo ?? '-'} 行`;
}

function onRegisterFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedRegisterFile.value = input.files?.[0] ?? null;
  registerPreview.value = null;
  registerConfirmResult.value = null;
  registerPreviewError.value = '';
  registerPreviewDownloadError.value = '';
  registerConfirmError.value = '';
}

async function submitRegisterPreview() {
  registerPreviewError.value = '';
  registerPreviewDownloadError.value = '';
  registerConfirmError.value = '';
  registerConfirmResult.value = null;
  if (!selectedRegisterFile.value) {
    registerPreviewError.value = '请选择现场登记表 Excel 文件';
    return;
  }

  registerPreviewLoading.value = true;
  try {
    registerPreview.value = await previewRegisterImport({
      templateCode: selectedRegisterTemplateCode.value,
      businessDate: registerBusinessDate.value,
      file: selectedRegisterFile.value,
      fileName: selectedRegisterFile.value.name,
    });
  } catch (error) {
    registerPreview.value = null;
    registerPreviewError.value = error instanceof Error ? error.message : '登记表预览失败';
  } finally {
    registerPreviewLoading.value = false;
  }
}

const activeScenario = sampleScenarios[0];
const activeDataSet = acceptanceDataSets.find((dataset) => dataset.scenarioId === activeScenario.id);
const activeRegisterTemplate = registerImportTemplates[0];
const selectedRegisterTemplateCode = ref(activeRegisterTemplate.code);
const registerBusinessDate = ref(new Date().toISOString().slice(0, 10));
const selectedRegisterFile = ref<File | null>(null);
const registerPreview = ref<RegisterImportPreview | null>(null);
const registerPreviewLoading = ref(false);
const registerPreviewError = ref('');
const registerPreviewDownloadError = ref('');
const registerConfirmLoading = ref(false);
const registerConfirmError = ref('');
const registerConfirmResult = ref<RegisterImportBatch | null>(null);
const registerReviewBatches = ref<RegisterImportBatch[]>([...registerImportReviewBatches]);
const selectedRegisterBatchNo = ref(registerImportReviewBatches[0]?.importBatchNo ?? '');
const registerBatchDetail = ref<RegisterImportBatch | null>(registerImportReviewBatches[0] ?? null);
const registerBatchLoading = ref(false);
const registerBatchError = ref('');
const registerBatchSource = ref('本地评审样例');
const registerPostingPlan = ref<RegisterImportPostingPlan | null>(
  registerImportReviewBatches[0] ? buildRegisterImportPostingPlan(registerImportReviewBatches[0]) : null,
);
const registerPostingPlanLoading = ref(false);
const registerPostingPlanError = ref('');
const registerPostingPlanSource = ref('本地映射计划');
const registerPostingExecutionLoading = ref(false);
const registerPostingExecutionError = ref('');
const registerPostingExecutionResult = ref<RegisterImportPostingExecutionResult | null>(null);
const registerCorrectionIssueKey = ref('');
const registerCorrectionValue = ref('');
const registerCorrectionLoading = ref(false);
const registerCorrectionError = ref('');
const registerCorrectionMessage = ref('');
const registerMachineOutputs = ref<MachineOutputRecord[]>([]);
const registerMachineOutputLoading = ref(false);
const registerMachineOutputError = ref('');
const registerMotherOrders = ref<MotherOrderRecord[]>([]);
const registerOutboundOrders = ref<OutboundOrderRecord[]>([]);
const registerBusinessDocumentLoading = ref(false);
const registerBusinessDocumentError = ref('');

const selectedRegisterTemplate = computed(() =>
  registerImportTemplates.find((template) => template.code === selectedRegisterTemplateCode.value) ?? activeRegisterTemplate,
);

const registerPreviewRows = computed(() => registerPreview.value?.rows.slice(0, 6) ?? []);
const registerPreviewIssues = computed(() => registerPreviewIssueCount(registerPreview.value));
const registerPreviewBlockers = computed(() =>
  registerPreview.value?.rows.flatMap((row) =>
    row.messages.map((message) => ({
      key: `${row.sourceLineNo}-${message.ruleCode}-${message.field}`,
      row,
      message,
    })),
  ).slice(0, 6) ?? [],
);
const selectedRegisterBatch = computed(() =>
  registerBatchDetail.value
  ?? registerReviewBatches.value.find((batch) => batch.importBatchNo === selectedRegisterBatchNo.value)
  ?? registerReviewBatches.value[0]
  ?? null,
);
const selectedRegisterBatchRows = computed(() => selectedRegisterBatch.value?.rows.slice(0, 8) ?? []);
const selectedRegisterBatchIssues = computed(() =>
  selectedRegisterBatch.value?.rows.reduce((sum, row) => sum + row.messages.length, 0) ?? 0,
);
const selectedPostingDrafts = computed(() => registerPostingPlan.value?.drafts.slice(0, 6) ?? []);
const canExecuteRegisterPosting = computed(() =>
  Boolean(selectedRegisterBatch.value)
  && Boolean(registerPostingPlan.value)
  && registerPostingPlanSource.value === '后端入账映射计划'
  && ['READY', 'WARNING'].includes(registerPostingPlan.value?.status ?? '')
  && !registerPostingPlanLoading.value
  && !registerPostingExecutionLoading.value
  && selectedRegisterBatch.value?.status !== 'POSTED',
);
const postingMissingFields = computed(() =>
  registerPostingPlan.value?.drafts.flatMap((draft) =>
    draft.missingFields.map((issue) => ({
      key: `${draft.sourceLineNo}-${issue.field}`,
      draft,
      issue,
    })),
  ).slice(0, 8) ?? [],
);
const activeCorrectionTarget = computed(() =>
  postingMissingFields.value.find((item) => item.key === registerCorrectionIssueKey.value)
  ?? postingMissingFields.value[0]
  ?? null,
);
const canSubmitRegisterCorrection = computed(() =>
  Boolean(activeCorrectionTarget.value)
  && registerPostingPlanSource.value === '后端入账映射计划'
  && Boolean(registerCorrectionValue.value.trim())
  && !registerCorrectionLoading.value
  && !registerPostingExecutionLoading.value
  && selectedRegisterBatch.value?.status !== 'POSTED',
);
const registerBatchSummary = computed(() => ({
  total: registerReviewBatches.value.length,
  confirmed: registerReviewBatches.value.filter((batch) => batch.status === 'CONFIRMED').length,
  warnings: registerReviewBatches.value.reduce((sum, batch) => sum + batch.warningRows, 0),
  rows: registerReviewBatches.value.reduce((sum, batch) => sum + batch.totalRows, 0),
}));
const registerBusinessDocuments = computed(() => [
  ...registerMotherOrders.value.map((record) => ({
    key: `mother-${record.code}`,
    typeLabel: '结构化母单',
    code: record.code,
    status: record.status,
    primary: `${record.customerId} / ${record.materialCode}`,
    secondary: `${documentQuantity(record.quantity, record.quantityUnit)} / ${record.weight}g / ${record.warehouseCode}`,
    ledgerCode: record.ledgerCode,
    source: sourceLineText(record.sourceRegisterType, record.sourceRegisterLineNo),
  })),
  ...registerOutboundOrders.value.map((record) => ({
    key: `outbound-${record.code}`,
    typeLabel: '结构化出库单',
    code: record.code,
    status: record.status,
    primary: `${record.motherOrderCode} / ${record.workOrderCode}`,
    secondary: `${documentQuantity(record.quantity, record.quantityUnit)} / ${record.weight}g / ${record.logisticsNo}`,
    ledgerCode: record.ledgerCode,
    source: sourceLineText(record.sourceRegisterType, record.sourceRegisterLineNo),
  })),
]);

onMounted(() => {
  void loadRegisterReviewBatches();
});

function downloadRegisterPreviewIssues() {
  registerPreviewDownloadError.value = '';
  if (!registerPreview.value || registerPreviewIssues.value === 0) {
    registerPreviewDownloadError.value = '当前预检没有阻断或警告行';
    return;
  }

  const csv = `\uFEFF${buildRegisterPreviewIssueCsv(registerPreview.value)}`;
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = registerPreviewIssueFileName(registerPreview.value);
  link.click();
  URL.revokeObjectURL(url);
}

async function loadRegisterReviewBatches() {
  registerBatchError.value = '';
  registerPostingExecutionError.value = '';
  registerPostingExecutionResult.value = null;
  resetRegisterCorrection();
  registerMachineOutputs.value = [];
  registerMachineOutputError.value = '';
  registerMotherOrders.value = [];
  registerOutboundOrders.value = [];
  registerBusinessDocumentError.value = '';
  registerBatchLoading.value = true;
  try {
    const batches = await fetchRegisterImportBatches(10);
    if (batches.length) {
      registerReviewBatches.value = batches;
      selectedRegisterBatchNo.value = batches[0].importBatchNo;
      registerBatchDetail.value = batches[0];
      registerBatchSource.value = '后端结构化批次';
      await loadRegisterPostingPlan(batches[0].importBatchNo, batches[0]);
    }
  } catch (error) {
    registerBatchSource.value = '本地评审样例';
    registerBatchError.value = error instanceof Error ? error.message : '批次列表读取失败';
    if (selectedRegisterBatch.value) {
      registerPostingPlan.value = buildRegisterImportPostingPlan(selectedRegisterBatch.value);
      registerPostingPlanSource.value = '本地映射计划';
    }
  } finally {
    registerBatchLoading.value = false;
  }
}

async function selectRegisterBatch(importBatchNo: string) {
  selectedRegisterBatchNo.value = importBatchNo;
  registerBatchError.value = '';
  registerPostingExecutionError.value = '';
  registerPostingExecutionResult.value = null;
  resetRegisterCorrection();
  registerMachineOutputs.value = [];
  registerMachineOutputError.value = '';
  registerMotherOrders.value = [];
  registerOutboundOrders.value = [];
  registerBusinessDocumentError.value = '';
  const fallbackBatch = registerReviewBatches.value.find((batch) => batch.importBatchNo === importBatchNo) ?? null;
  registerBatchDetail.value = fallbackBatch ?? registerBatchDetail.value;
  try {
    registerBatchDetail.value = await fetchRegisterImportBatch(importBatchNo);
    registerBatchSource.value = '后端结构化批次';
  } catch (error) {
    registerBatchSource.value = '本地评审样例';
    registerBatchError.value = error instanceof Error ? error.message : '批次详情读取失败';
  }
  await loadRegisterPostingPlan(importBatchNo, registerBatchDetail.value ?? fallbackBatch);
}

async function loadRegisterPostingPlan(importBatchNo: string, fallbackBatch: RegisterImportBatch | null) {
  registerPostingPlanError.value = '';
  registerPostingPlanLoading.value = true;
  try {
    registerPostingPlan.value = await fetchRegisterImportPostingPlan(importBatchNo);
    registerPostingPlanSource.value = '后端入账映射计划';
  } catch (error) {
    if (fallbackBatch) {
      registerPostingPlan.value = buildRegisterImportPostingPlan(fallbackBatch);
    }
    registerPostingPlanSource.value = '本地映射计划';
    registerPostingPlanError.value = error instanceof Error ? error.message : '入账映射计划读取失败';
  } finally {
    registerPostingPlanLoading.value = false;
    syncRegisterCorrectionTarget();
  }
}

function resetRegisterCorrection() {
  registerCorrectionIssueKey.value = '';
  registerCorrectionValue.value = '';
  registerCorrectionError.value = '';
  registerCorrectionMessage.value = '';
}

function syncRegisterCorrectionTarget() {
  const firstTarget = postingMissingFields.value[0];
  if (!firstTarget) {
    registerCorrectionIssueKey.value = '';
    return;
  }
  if (!postingMissingFields.value.some((item) => item.key === registerCorrectionIssueKey.value)) {
    registerCorrectionIssueKey.value = firstTarget.key;
  }
}

async function submitRegisterCorrection() {
  registerCorrectionError.value = '';
  registerCorrectionMessage.value = '';
  const target = activeCorrectionTarget.value;
  const batchNo = selectedRegisterBatch.value?.importBatchNo;
  const correctionValue = registerCorrectionValue.value.trim();
  if (!target || !batchNo) {
    registerCorrectionError.value = '请先选择需要补录的阻断字段';
    return;
  }
  if (registerPostingPlanSource.value !== '后端入账映射计划') {
    registerCorrectionError.value = '当前是本地推导计划，不能提交补录；请先连接后端并读取 posting plan';
    return;
  }
  if (!correctionValue) {
    registerCorrectionError.value = '请输入补录值';
    return;
  }

  registerCorrectionLoading.value = true;
  try {
    const detail = await correctRegisterImportRow(batchNo, target.draft.sourceLineNo, {
      fields: {
        [target.issue.field]: correctionValue,
      },
      operator: '样例库复核员',
      correctionRemark: '样例库登记表复核补录',
    });
    registerBatchDetail.value = detail;
    registerReviewBatches.value = registerReviewBatches.value.map((batch) =>
      batch.importBatchNo === detail.importBatchNo ? detail : batch,
    );
    registerBatchSource.value = '后端结构化批次';
    await loadRegisterPostingPlan(batchNo, detail);
    registerCorrectionMessage.value = `已补录第 ${target.draft.sourceLineNo} 行 ${target.issue.field}`;
    registerCorrectionValue.value = '';
  } catch (error) {
    registerCorrectionError.value = error instanceof Error ? error.message : '登记表复核补录失败';
  } finally {
    registerCorrectionLoading.value = false;
  }
}

async function executeRegisterPosting() {
  registerPostingExecutionError.value = '';
  registerPostingExecutionResult.value = null;
  registerMachineOutputs.value = [];
  registerMachineOutputError.value = '';
  registerMotherOrders.value = [];
  registerOutboundOrders.value = [];
  registerBusinessDocumentError.value = '';

  const batchNo = selectedRegisterBatch.value?.importBatchNo;
  if (!batchNo || !registerPostingPlan.value) {
    registerPostingExecutionError.value = '请先选择已确认的登记表批次';
    return;
  }
  if (registerPostingPlanSource.value !== '后端入账映射计划') {
    registerPostingExecutionError.value = '当前是本地推导计划，不能执行入账；请先连接后端并读取 posting plan';
    return;
  }
  if (registerPostingPlan.value.status === 'BLOCKED') {
    registerPostingExecutionError.value = '存在阻断行，不能执行入账';
    return;
  }
  if (registerPostingPlan.value.status === 'POSTED' || selectedRegisterBatch.value?.status === 'POSTED') {
    registerPostingExecutionError.value = '该批次已入账，不能重复执行';
    return;
  }

  registerPostingExecutionLoading.value = true;
  try {
    const result = await postRegisterImportBatch(batchNo, {
      allowWarnings: registerPostingPlan.value.status === 'WARNING',
      operator: '样例库复核员',
      postingRemark: '样例库登记表批次显式入账',
    });
    registerPostingExecutionResult.value = result;

    try {
      const [detail, plan] = await Promise.all([
        fetchRegisterImportBatch(batchNo),
        fetchRegisterImportPostingPlan(batchNo),
      ]);
      registerBatchDetail.value = detail;
      registerReviewBatches.value = registerReviewBatches.value.map((batch) =>
        batch.importBatchNo === detail.importBatchNo ? detail : batch,
      );
      registerBatchSource.value = '后端结构化批次';
      registerPostingPlan.value = plan;
      registerPostingPlanSource.value = '后端入账映射计划';
      await Promise.all([
        loadRegisterMachineOutputs(batchNo),
        loadRegisterBusinessDocuments(result),
      ]);
    } catch (error) {
      registerPostingExecutionError.value = error instanceof Error
        ? `入账已完成，但刷新批次状态失败：${error.message}`
        : '入账已完成，但刷新批次状态失败';
    }
  } catch (error) {
    registerPostingExecutionError.value = error instanceof Error ? error.message : '登记表批次入账失败';
  } finally {
    registerPostingExecutionLoading.value = false;
  }
}

async function loadRegisterBusinessDocuments(result: RegisterImportPostingExecutionResult) {
  registerBusinessDocumentError.value = '';
  registerMotherOrders.value = [];
  registerOutboundOrders.value = [];
  if (!['REG-INBOUND', 'REG-OUTBOUND'].includes(result.templateCode)) {
    return;
  }

  registerBusinessDocumentLoading.value = true;
  try {
    const businessCodes = result.items
      .map((item) => item.businessCode)
      .filter((code) => code && code.trim());
    if (result.templateCode === 'REG-INBOUND') {
      registerMotherOrders.value = await Promise.all(businessCodes.map((code) => fetchMotherOrder(code)));
    } else {
      registerOutboundOrders.value = await Promise.all(businessCodes.map((code) => fetchOutboundOrder(code)));
    }
  } catch (error) {
    registerMotherOrders.value = [];
    registerOutboundOrders.value = [];
    registerBusinessDocumentError.value = error instanceof Error ? error.message : '结构化业务单据读取失败';
  } finally {
    registerBusinessDocumentLoading.value = false;
  }
}

async function loadRegisterMachineOutputs(importBatchNo: string) {
  registerMachineOutputError.value = '';
  registerMachineOutputLoading.value = true;
  try {
    registerMachineOutputs.value = await fetchMachineOutputsByImportBatch(importBatchNo);
  } catch (error) {
    registerMachineOutputs.value = [];
    registerMachineOutputError.value = error instanceof Error ? error.message : '设备产量记录读取失败';
  } finally {
    registerMachineOutputLoading.value = false;
  }
}

async function confirmRegisterPreview() {
  registerConfirmError.value = '';
  registerConfirmResult.value = null;
  if (!registerPreview.value) {
    registerConfirmError.value = '请先上传并通过预检';
    return;
  }
  if (!registerPreview.value.executable) {
    registerConfirmError.value = '存在阻断行，不能确认导入批次';
    return;
  }

  registerConfirmLoading.value = true;
  try {
    const confirmed = await confirmRegisterImport({
      preview: registerPreview.value,
      operator: '样例库复核员',
      confirmRemark: '样例库确认导入仅保存结构化批次，暂不直接写业务台账',
    });
    registerConfirmResult.value = confirmed;
    registerReviewBatches.value = [
      confirmed,
      ...registerReviewBatches.value.filter((batch) => batch.importBatchNo !== confirmed.importBatchNo),
    ];
    selectedRegisterBatchNo.value = confirmed.importBatchNo;
    registerBatchDetail.value = confirmed;
    registerBatchSource.value = '本次确认结果';
    registerPostingPlan.value = buildRegisterImportPostingPlan(confirmed);
    registerPostingPlanSource.value = '本地映射计划';
  } catch (error) {
    registerConfirmError.value = error instanceof Error ? error.message : '确认导入批次失败';
  } finally {
    registerConfirmLoading.value = false;
  }
}

const sampleKpis = computed(() => [
  {
    label: '核心样例',
    value: sampleScenarios.length,
    unit: '条',
    detail: `${sampleScenarios.filter((scenario) => scenario.status === 'ready').length} 条可演示`,
    tone: 'blue' as Tone,
  },
  {
    label: '开源映射',
    value: openSourceSampleMappings.filter((mapping) => mapping.status === 'applied').length,
    unit: '项',
    detail: `覆盖 ${openSourceSampleMappings.length} 个参考对象`,
    tone: 'green' as Tone,
  },
  {
    label: '真实数据集',
    value: acceptanceDataSets.length,
    unit: '组',
    detail: `${acceptanceDataSets.filter((dataset) => dataset.status === 'active').length} 组进入回归`,
    tone: 'violet' as Tone,
  },
  {
    label: '待补缺口',
    value: acceptanceDataSets.reduce((sum, dataset) => sum + dataset.gaps.length, 0),
    unit: '项',
    detail: '现场数据、登录人、硬件证据',
    tone: 'amber' as Tone,
  },
  {
    label: '硬件 POC',
    value: hardwarePocRecords.length,
    unit: '项',
    detail: `${hardwarePocRecords.filter((record) => record.tone === 'red').length} 项阻断`,
    tone: 'red' as Tone,
  },
  {
    label: '登记模板',
    value: registerImportSummary.templates,
    unit: '类',
    detail: `${registerImportSummary.commandReadyTemplates} 类命令已对齐`,
    tone: 'green' as Tone,
  },
]);

const scenarioRows = computed(() =>
  sampleScenarios.map((scenario) => {
    const mappings = openSourceSampleMappings.filter((mapping) => mapping.mappedScenarioId === scenario.id);
    const datasets = acceptanceDataSets.filter((dataset) => dataset.scenarioId === scenario.id);

    return {
      ...scenario,
      mappingCount: mappings.length,
      dataSetCount: datasets.length,
      primaryFixture: datasets[0]?.fixture ?? '待绑定 fixture',
      coverage: `${scenario.chain.length} 节点 / ${scenario.evidence.length} 证据`,
    };
  }),
);

const researchCards = [
  {
    source: 'ERPNext Job Card',
    referenceUrl: 'https://docs.frappe.io/erpnext/job-card',
    reusablePattern: '工单按工序拆到 Job Card，记录工作站、员工、完工数量、时间日志、物料请求和 WIP 进度。',
    mappedTo: '生产工单 / 工序报工 / 损耗试算',
    gate: '报工样例必须写库存台账或证据事件，不能只改页面状态。',
    tone: 'blue' as Tone,
  },
  {
    source: 'Odoo Quality Checks',
    referenceUrl:
      'https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/quality/quality_management/quality_checks.html',
    reusablePattern: '质检点可由规则自动生成，也可落在制造单、库存作业或 Shop Floor 工序上处理 Pass/Fail。',
    mappedTo: '质检返工 / 复检放行 / 缺陷附件',
    gate: '质检样例必须包含缺陷、处理动作、返工或复检结果、照片和责任人。',
    tone: 'green' as Tone,
  },
  {
    source: 'InvenTree Build Orders',
    referenceUrl: 'https://docs.inventree.org/en/stable/manufacturing/build/',
    reusablePattern: '构建单按 BOM 分配库存，完工时扣减已分配库存并生成成品，测试结果和附件可绑定到构建输出。',
    mappedTo: '客户库存隔离 / 批次追溯 / 验收测试',
    gate: '样例必须校验客户所有权、批次、库位、状态和测试结果，不允许只保留汇总数。',
    tone: 'violet' as Tone,
  },
] as const;

const testGates = [
  { code: 'G1', label: '业务主链路', detail: '来料、拆包、工单、报工、质检、出库必须一条样例跑通。', state: '已接入', tone: 'green' as Tone },
  { code: 'G2', label: '库存台账', detail: '数量、重量、状态、库位变化必须有 before/after。', state: '已接入', tone: 'green' as Tone },
  { code: 'G3', label: '证据链', detail: '称重、照片、视频、附件、操作日志需要统一编号。', state: '部分缺口', tone: 'amber' as Tone },
  { code: 'G4', label: '硬件兜底', detail: 'NVR、扫码、标签、电子秤失败时保留人工路径。', state: 'POC 中', tone: 'amber' as Tone },
  { code: 'G5', label: '自动化测试', detail: 'fixture 必须被后端或前端测试引用。', state: '已接入', tone: 'blue' as Tone },
  { code: 'G6', label: '真实数据替换', detail: '试点前用脱敏真实单据替换演示样例。', state: '未完成', tone: 'red' as Tone },
] as const;

const fieldGapRows = computed(() =>
  acceptanceDataSets.flatMap((dataset) =>
    dataset.gaps.map((gap, index) => ({
      code: `${dataset.code}-G${index + 1}`,
      scenarioId: dataset.scenarioId,
      gap,
      fixture: dataset.fixture,
      tone: dataset.status === 'active' ? ('amber' as Tone) : ('red' as Tone),
    })),
  ),
);

const readinessSummary = computed(() => ({
  total: productionReadinessItems.length,
  p0Pending: productionReadinessItems.filter((item) => item.priority === 'P0' && item.status !== 'confirmed').length,
  needsTrial: productionReadinessItems.filter((item) => item.status === 'needs-trial').length,
  confirmed: productionReadinessItems.filter((item) => item.status === 'confirmed').length,
}));

const registerCoverageSummary = computed(() => ({
  processCovered: registerProcessCoverage.filter((item) => item.status === 'covered').length,
  processPartial: registerProcessCoverage.filter((item) => item.status === 'partial').length,
  fieldsCovered: registerFieldCoverage.filter((item) => item.status === 'covered').length,
  fieldsPartial: registerFieldCoverage.filter((item) => item.status === 'partial').length,
}));

const registerTemplateRows = computed(() =>
  registerImportTemplates.map((template) => ({
    ...template,
    requiredFields: template.fields.filter((field) => field.required).length,
    blockerRules: template.validationRules.filter((rule) => rule.level === 'BLOCK').length,
  })),
);
</script>

<template>
  <div class="sample-control-workbench page-stack">
    <section class="sample-command-surface" aria-label="登记导入">
      <div class="sample-command-copy">
        <h2>登记导入</h2>
        <p>上传入库、出货、抛光、雕刻登记表。先预检，确认无误后再写入库存台账。</p>
      </div>
    </section>

    <section class="sample-ops-grid register-import-grid" aria-label="现场登记表导入准备度">
      <div class="sample-table-panel register-import-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">REGISTER IMPORT</span>
            <h2>登记表导入</h2>
            <p>选择模板和 Excel，先看预检结果，阻断行不能入账。</p>
          </div>
          <StatusBadge :label="`${registerImportSummary.templates} 类模板`" tone="green" />
        </div>
        <div class="register-kpi-strip" aria-label="登记表导入指标">
          <article>
            <span>映射字段</span>
            <strong>{{ registerImportSummary.fields }}</strong>
            <small>{{ registerImportSummary.requiredFields }} 个必填</small>
          </article>
          <article>
            <span>命令对齐</span>
            <strong>{{ registerImportSummary.commandReadyTemplates }}</strong>
            <small>入库/报工/出货</small>
          </article>
          <article>
            <span>解析状态</span>
            <strong>上传预览</strong>
            <small>{{ registerImportSummary.parserStatus }}</small>
          </article>
        </div>
        <div class="erp-table-wrap sample-matrix-wrap">
          <table class="erp-table register-template-table">
            <thead>
              <tr>
                <th>模板</th>
                <th>来源文件</th>
                <th>业务链路</th>
                <th>目标命令</th>
                <th>字段</th>
                <th>阻断规则</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="template in registerTemplateRows" :key="template.code" class="register-template-row">
                <td>
                  <span class="cell-stack">
                    <b>{{ template.code }}</b>
                    <small>{{ template.name }}</small>
                  </span>
                </td>
                <td>{{ template.sourceFile }}</td>
                <td>{{ template.businessFlow }}</td>
                <td>{{ template.targetCommand }}</td>
                <td>
                  <span class="cell-stack">
                    <b>{{ template.fields.length }} 项 / {{ template.requiredFields }} 必填</b>
                    <small>{{ keyFieldLabel(template) }}</small>
                  </span>
                </td>
                <td>{{ template.blockerRules }} 条</td>
                <td><StatusBadge :label="parserStatusLabel(template.parserStatus)" tone="green" /></td>
              </tr>
            </tbody>
          </table>
        </div>
        <form class="register-preview-console" aria-label="登记表上传预检" @submit.prevent="submitRegisterPreview">
          <div class="register-preview-title">
            <span class="eyebrow">UPLOAD PREVIEW</span>
            <strong>登记表上传预检</strong>
          </div>
          <div class="register-preview-controls">
            <label>
              <span>登记模板</span>
              <select v-model="selectedRegisterTemplateCode">
                <option v-for="template in registerImportTemplates" :key="template.code" :value="template.code">
                  {{ template.code }} / {{ template.name }}
                </option>
              </select>
            </label>
            <label>
              <span>业务日期</span>
              <input v-model="registerBusinessDate" type="date">
            </label>
            <label>
              <span>Excel 文件</span>
              <input type="file" accept=".xlsx" @change="onRegisterFileChange">
            </label>
            <button class="primary-button" type="submit" :disabled="registerPreviewLoading">
              {{ registerPreviewLoading ? '预检中' : '上传预检' }}
            </button>
          </div>
          <div class="register-preview-current">
            <span>{{ selectedRegisterTemplate.sourceFile }}</span>
            <strong>{{ selectedRegisterTemplate.targetCommand }}</strong>
            <small>{{ selectedRegisterFile?.name ?? '未选择文件' }}</small>
          </div>
          <p v-if="registerPreviewError" class="register-preview-error">{{ registerPreviewError }}</p>
        </form>
        <section v-if="registerPreview" class="register-preview-result" aria-label="登记表预检结果">
          <div class="register-preview-summary">
            <article>
              <span>预览批次</span>
              <strong>{{ registerPreview.importBatchNo }}</strong>
              <small>{{ registerPreview.sourceFileName }}</small>
            </article>
            <article>
              <span>解析行数</span>
              <strong>{{ registerPreview.totalRows }}</strong>
              <small>{{ registerPreview.nextAction }}</small>
            </article>
            <article>
              <span>READY</span>
              <strong>{{ registerPreview.readyRows }}</strong>
              <small>可复核行</small>
            </article>
            <article>
              <span>BLOCKED</span>
              <strong>{{ registerPreview.blockedRows }}</strong>
              <small>阻断行</small>
            </article>
          </div>
          <div class="register-preview-actions">
            <button
              class="secondary-button"
              type="button"
              :disabled="registerPreviewIssues === 0"
              @click="downloadRegisterPreviewIssues"
            >
              下载阻断/警告 CSV
            </button>
            <button
              class="primary-button"
              type="button"
              :disabled="registerConfirmLoading || !registerPreview.executable"
              @click="confirmRegisterPreview"
            >
              {{ registerConfirmLoading ? '确认中' : '确认导入批次' }}
            </button>
            <span>{{ registerPreviewIssues }} 条需处理消息</span>
          </div>
          <p v-if="registerPreviewDownloadError" class="register-preview-error">
            {{ registerPreviewDownloadError }}
          </p>
          <p v-if="registerConfirmError" class="register-preview-error">
            {{ registerConfirmError }}
          </p>
          <article v-if="registerConfirmResult" class="register-preview-confirmation">
            <div>
              <span>确认批次</span>
              <strong>{{ registerConfirmResult.importBatchNo }}</strong>
              <small>{{ registerConfirmResult.status }} / {{ registerConfirmResult.confirmedBy }}</small>
            </div>
            <div>
              <span>保存行数</span>
              <strong>{{ registerConfirmResult.rows.length }}</strong>
              <small>{{ registerConfirmResult.nextAction }}</small>
            </div>
            <div>
              <span>目标命令</span>
              <strong>{{ registerConfirmResult.targetCommand }}</strong>
              <small>{{ registerConfirmResult.confirmRemark }}</small>
            </div>
          </article>
          <div v-if="registerPreviewBlockers.length" class="register-preview-blockers">
            <article v-for="item in registerPreviewBlockers" :key="item.key">
              <b>{{ item.message.ruleCode }}</b>
              <span>第 {{ item.row.sourceLineNo }} 行 / {{ item.message.field }} / {{ item.message.message }}</span>
              <small>{{ item.message.fallback }}</small>
            </article>
          </div>
          <div class="erp-table-wrap register-preview-table-wrap">
            <table class="erp-table register-preview-table">
              <thead>
                <tr>
                  <th>原行</th>
                  <th>序号</th>
                  <th>状态</th>
                  <th>标准化字段</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in registerPreviewRows" :key="`${row.sourceLineNo}-${row.sourceRegisterSequence}`">
                  <td>{{ row.sourceLineNo }}</td>
                  <td>{{ row.sourceRegisterSequence }}</td>
                  <td><StatusBadge :label="row.status" :tone="previewStatusTone(row.status)" /></td>
                  <td>
                    <span class="register-field-chips">
                      <small v-for="[field, value] in previewFieldEntries(row)" :key="field">
                        <b>{{ field }}</b>{{ value || '-' }}
                      </small>
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <aside class="register-detail-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">ACTIVE TEMPLATE</span>
            <h2>{{ activeRegisterTemplate.name }}</h2>
          </div>
          <StatusBadge label="命令已对齐" tone="green" />
        </div>
        <dl class="detail-grid">
          <dt>目标命令</dt>
          <dd>{{ activeRegisterTemplate.targetCommand }}</dd>
          <dt>台账类型</dt>
          <dd>{{ activeRegisterTemplate.targetLedgerType }}</dd>
          <dt>下一动作</dt>
          <dd>{{ activeRegisterTemplate.nextAction }}</dd>
        </dl>
        <div class="register-field-list" aria-label="字段映射">
          <article v-for="field in activeRegisterTemplate.fields.slice(0, 7)" :key="field.sourceColumn">
            <strong>{{ field.sourceColumn }}</strong>
            <span>{{ field.targetField }}</span>
            <small>{{ field.normalizeRule }}</small>
          </article>
        </div>
        <div class="register-rule-list" aria-label="导入校验规则">
          <article
            v-for="rule in activeRegisterTemplate.validationRules"
            :key="rule.code"
            :class="`tone-${registerRuleTone(rule.level)}`"
          >
            <b>{{ rule.code }}</b>
            <span>{{ rule.description }}</span>
            <small>{{ rule.fallback }}</small>
          </article>
        </div>
      </aside>
    </section>

    <section class="section-block register-review-panel" aria-label="登记表确认批次复核">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">BATCH REVIEW</span>
          <h2>登记表确认批次复核</h2>
          <p>展示最近已确认的结构化导入批次、行级标准化字段和消息，当前状态仍是“待业务单据入账映射”。</p>
        </div>
        <div class="register-review-actions">
          <StatusBadge :label="registerBatchSource" tone="blue" />
          <button class="secondary-button" type="button" :disabled="registerBatchLoading" @click="loadRegisterReviewBatches">
            {{ registerBatchLoading ? '读取中' : '刷新批次' }}
          </button>
        </div>
      </div>
      <div class="register-review-kpis" aria-label="确认批次统计">
        <article>
          <span>确认批次</span>
          <strong>{{ registerBatchSummary.total }}</strong>
          <small>{{ registerBatchSummary.confirmed }} 个待入账映射</small>
        </article>
        <article>
          <span>行级数据</span>
          <strong>{{ registerBatchSummary.rows }}</strong>
          <small>保留原表行号和纸质序号</small>
        </article>
        <article>
          <span>警告行</span>
          <strong>{{ registerBatchSummary.warnings }}</strong>
          <small>允许确认但需二次复核</small>
        </article>
        <article>
          <span>当前消息</span>
          <strong>{{ selectedRegisterBatchIssues }}</strong>
          <small>来自所选批次行级 messages</small>
        </article>
      </div>
      <p v-if="registerBatchError" class="register-preview-error">
        {{ registerBatchError }}；当前继续显示本地评审批次，不代表已写业务台账。
      </p>
      <div class="register-review-grid">
        <div class="erp-table-wrap register-review-batch-wrap">
          <table class="erp-table register-review-batch-table">
            <thead>
              <tr>
                <th>批次</th>
                <th>模板</th>
                <th>来源文件</th>
                <th>行数</th>
                <th>目标命令</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="batch in registerReviewBatches"
                :key="batch.importBatchNo"
                :class="{ 'is-selected': batch.importBatchNo === selectedRegisterBatchNo }"
                @click="selectRegisterBatch(batch.importBatchNo)"
              >
                <td>
                  <span class="cell-stack">
                    <b>{{ batch.importBatchNo }}</b>
                    <small>{{ batch.confirmedAt }} / {{ batch.confirmedBy }}</small>
                  </span>
                </td>
                <td>{{ batch.templateCode }}</td>
                <td>{{ batch.sourceFileName }}</td>
                <td>{{ batch.totalRows }} 行 / {{ batch.warningRows }} 警告</td>
                <td>{{ batch.targetCommand }}</td>
                <td><StatusBadge :label="batchStatusLabel(batch.status)" :tone="batchStatusTone(batch.status)" /></td>
              </tr>
            </tbody>
          </table>
        </div>
        <aside v-if="selectedRegisterBatch" class="register-review-detail">
          <div class="register-review-detail-head">
            <span>{{ selectedRegisterBatch.templateName }}</span>
            <strong>{{ selectedRegisterBatch.importBatchNo }}</strong>
            <StatusBadge
              :label="batchStatusLabel(selectedRegisterBatch.status)"
              :tone="batchStatusTone(selectedRegisterBatch.status)"
            />
          </div>
          <dl class="detail-grid">
            <dt>目标命令</dt>
            <dd>{{ selectedRegisterBatch.targetCommand }}</dd>
            <dt>下一动作</dt>
            <dd>{{ selectedRegisterBatch.nextAction }}</dd>
            <dt>复核备注</dt>
            <dd>{{ selectedRegisterBatch.confirmRemark }}</dd>
          </dl>
          <section v-if="registerPostingPlan" class="register-posting-plan-card" aria-label="登记表入账映射计划">
            <div class="register-posting-plan-head">
              <div>
                <span class="eyebrow">POSTING PLAN</span>
                <strong>入账映射计划</strong>
              </div>
              <div class="register-posting-actions">
                <StatusBadge :label="registerPostingPlanLoading ? '映射读取中' : registerPostingPlanSource" tone="blue" />
                <button
                  type="button"
                  class="secondary-button"
                  :disabled="!canExecuteRegisterPosting"
                  @click="executeRegisterPosting"
                >
                  {{ registerPostingExecutionLoading ? '入账中...' : '执行入账' }}
                </button>
              </div>
            </div>
            <div class="register-posting-kpis">
              <article>
                <span>计划状态</span>
                <strong>{{ registerPostingPlan.status }}</strong>
                <small>{{ registerPostingPlan.nextAction }}</small>
              </article>
              <article>
                <span>可生成</span>
                <strong>{{ registerPostingPlan.readyDrafts }}</strong>
                <small>{{ registerPostingPlan.targetCommand }}</small>
              </article>
              <article>
                <span>阻断</span>
                <strong>{{ registerPostingPlan.blockedDrafts }}</strong>
                <small>缺主数据或称重字段</small>
              </article>
            </div>
            <p v-if="registerPostingPlanError" class="register-preview-error">
              {{ registerPostingPlanError }}；当前显示本地推导计划，不执行入账。
            </p>
            <p v-if="registerPostingExecutionError" class="register-preview-error">
              {{ registerPostingExecutionError }}
            </p>
            <div v-if="registerPostingExecutionResult" class="register-posting-result">
              <div>
                <span>入账结果</span>
                <strong>{{ registerPostingExecutionResult.status }} / {{ registerPostingExecutionResult.operator }}</strong>
                <small>{{ registerPostingExecutionResult.nextAction }}</small>
              </div>
              <article v-for="item in registerPostingExecutionResult.items" :key="`${item.sourceLineNo}-${item.businessCode}`">
                <b>第 {{ item.sourceLineNo }} 行</b>
                <span>{{ item.businessCode }}</span>
                <small>{{ item.ledgerCode }} / {{ item.message }}</small>
              </article>
            </div>
            <p v-if="registerBusinessDocumentError" class="register-preview-error">
              {{ registerBusinessDocumentError }}
            </p>
            <div
              v-if="registerBusinessDocumentLoading || registerBusinessDocuments.length"
              class="register-business-document-result"
            >
              <div>
                <span>业务单据快照</span>
                <strong>{{ registerBusinessDocumentLoading ? '读取中' : `${registerBusinessDocuments.length} 张` }}</strong>
                <small>库存台账成功后保存结构化母单/出库单快照</small>
              </div>
              <article v-for="document in registerBusinessDocuments" :key="document.key">
                <b>{{ document.typeLabel }} / {{ document.code }}</b>
                <span>{{ document.status }} / {{ document.primary }}</span>
                <small>{{ document.secondary }} / {{ document.ledgerCode }}</small>
                <small>{{ document.source }}</small>
              </article>
            </div>
            <p v-if="registerMachineOutputError" class="register-preview-error">
              {{ registerMachineOutputError }}
            </p>
            <div v-if="registerMachineOutputLoading || registerMachineOutputs.length" class="register-machine-output-result">
              <div>
                <span>设备产量</span>
                <strong>{{ registerMachineOutputLoading ? '读取中' : `${registerMachineOutputs.length} 条` }}</strong>
                <small>雕刻登记表独立落库，不再只写台账备注</small>
              </div>
              <article v-for="record in registerMachineOutputs" :key="record.code">
                <b>{{ record.code }} / {{ record.machineNoSnapshot }}</b>
                <span>{{ record.productCategory }} / {{ machineOutputQuantity(record) }}</span>
                <small>{{ record.operatorSignatureName }} / {{ record.ledgerCode }}</small>
              </article>
            </div>
            <div v-if="postingMissingFields.length" class="register-posting-missing-list">
              <article v-for="item in postingMissingFields" :key="item.key">
                <b>第 {{ item.draft.sourceLineNo }} 行 / {{ item.issue.field }}</b>
                <span>{{ item.issue.message }}</span>
                <small>{{ item.issue.fallback }}</small>
              </article>
            </div>
            <form
              v-if="postingMissingFields.length"
              class="register-correction-panel"
              aria-label="登记表复核补录"
              @submit.prevent="submitRegisterCorrection"
            >
              <div>
                <span>复核补录</span>
                <strong>
                  第 {{ activeCorrectionTarget?.draft.sourceLineNo ?? '-' }} 行 /
                  {{ activeCorrectionTarget?.issue.field ?? '待选择字段' }}
                </strong>
                <small>只更新确认批次字段，不直接写库存台账或业务单据。</small>
              </div>
              <label>
                <span>字段</span>
                <select v-model="registerCorrectionIssueKey">
                  <option v-for="item in postingMissingFields" :key="item.key" :value="item.key">
                    第 {{ item.draft.sourceLineNo }} 行 / {{ item.issue.field }}
                  </option>
                </select>
              </label>
              <label>
                <span>补录值</span>
                <input v-model="registerCorrectionValue" placeholder="输入主数据编码或称重值" />
              </label>
              <button type="submit" class="secondary-button" :disabled="!canSubmitRegisterCorrection">
                {{ registerCorrectionLoading ? '保存中...' : '保存补录' }}
              </button>
            </form>
            <p v-if="registerCorrectionMessage" class="register-correction-ok">
              {{ registerCorrectionMessage }}，已刷新入账映射计划。
            </p>
            <p v-if="registerCorrectionError" class="register-correction-error">
              {{ registerCorrectionError }}
            </p>
            <div class="erp-table-wrap register-posting-draft-wrap">
              <table class="erp-table register-posting-draft-table">
                <thead>
                  <tr>
                    <th>原行</th>
                    <th>目标命令</th>
                    <th>状态</th>
                    <th>已映射字段</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="draft in selectedPostingDrafts" :key="`${draft.importBatchNo}-${draft.sourceLineNo}`">
                    <td>{{ draft.sourceLineNo }}</td>
                    <td>{{ draft.targetCommand }}</td>
                    <td><StatusBadge :label="draft.status" :tone="previewStatusTone(draft.status)" /></td>
                    <td>
                      <span class="register-field-chips">
                        <small v-for="[field, value] in postingFieldEntries(draft)" :key="field">
                          <b>{{ field }}</b>{{ value }}
                        </small>
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <div class="erp-table-wrap register-review-row-wrap">
            <table class="erp-table register-review-row-table">
              <thead>
                <tr>
                  <th>原行</th>
                  <th>序号</th>
                  <th>状态</th>
                  <th>字段快照</th>
                  <th>消息</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in selectedRegisterBatchRows" :key="`${row.importBatchNo}-${row.sourceLineNo}`">
                  <td>{{ row.sourceLineNo }}</td>
                  <td>{{ row.sourceRegisterSequence || '-' }}</td>
                  <td><StatusBadge :label="row.status" :tone="previewStatusTone(row.status)" /></td>
                  <td>
                    <span class="register-field-chips">
                      <small v-for="[field, value] in previewFieldEntries(row)" :key="field">
                        <b>{{ field }}</b>{{ value || '-' }}
                      </small>
                    </span>
                  </td>
                  <td>
                    <span v-if="row.messages.length" class="register-message-stack">
                      <small v-for="message in row.messages" :key="`${message.ruleCode}-${message.field}`">
                        {{ message.ruleCode }} / {{ message.message }}
                      </small>
                    </span>
                    <span v-else class="muted">无</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </aside>
      </div>
    </section>

  </div>
</template>
