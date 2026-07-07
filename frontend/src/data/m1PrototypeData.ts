export type Tone = 'blue' | 'green' | 'amber' | 'red' | 'violet' | 'gray';

export interface Metric {
  label: string;
  value: string;
  delta: string;
  tone: Tone;
}

export interface WorkQueueItem {
  id: string;
  title: string;
  subtitle: string;
  owner: string;
  due: string;
  severity: 'high' | 'medium' | 'normal';
}

export interface InboundOrder {
  code: string;
  customer: string;
  material: string;
  inboundDate: string;
  expressCompany: string;
  courierNo: string;
  senderName: string;
  receiverName: string;
  unpackSigner: string;
  goodsName: string;
  qty: number;
  weight: string;
  status: string;
  video: string;
  owner: string;
  remark: string;
}

export interface WorkOrder {
  code: string;
  customer: string;
  material: string;
  process: string;
  processCategory: string;
  productCategory: string;
  machineNo: string;
  workItemName: string;
  startedAt: string;
  finishedAt: string;
  completionStatus: string;
  handoffAuditStatus: string;
  assignee: string;
  plannedQty: number;
  finishedQty: number;
  status: string;
  progress: number;
}

export interface QualityCase {
  code: string;
  workOrder: string;
  process: string;
  inspector: string;
  defect: string;
  action: string;
  status: string;
}

export interface InventoryBalance {
  owner: string;
  batch: string;
  material: string;
  location: string;
  status: string;
  qty: number;
  weight: string;
  frozen: string;
}

export interface TraceEvent {
  time: string;
  node: string;
  bizCode: string;
  operator: string;
  evidence: string;
}

export interface MobileTask {
  code: string;
  scene: string;
  nextAction: string;
  required: string;
  status: string;
}

export interface SampleScenario {
  id: string;
  name: string;
  owner: string;
  purpose: string;
  chain: string[];
  evidence: string[];
  status: 'ready' | 'needs-data' | 'blocked';
}

export interface HardwareDevice {
  code: string;
  name: string;
  scene: string;
  protocol: string;
  status: 'online' | 'degraded' | 'manual';
  fallback: string;
}

export interface IntegrationTask {
  id: string;
  title: string;
  target: string;
  health: number;
  lastRun: string;
  nextAction: string;
  owner: string;
}

export interface HardwarePocRecord {
  code: string;
  deviceCode: string;
  scene: string;
  testWindow: string;
  result: string;
  evidence: string;
  blocker: string;
  owner: string;
  nextAction: string;
  tone: Tone;
}

export interface InboundCheckpoint {
  label: string;
  value: string;
  status: string;
  tone: Tone;
}

export interface InboundRisk {
  code: string;
  title: string;
  detail: string;
  owner: string;
  action: string;
  tone: Tone;
}

export interface ProductionStationLoad {
  station: string;
  queue: string;
  capacity: string;
  bottleneck: string;
  tone: Tone;
}

export interface ProductionHandoff {
  code: string;
  from: string;
  to: string;
  inputWeight: string;
  outputWeight: string;
  lossRate: string;
  status: string;
  tone: Tone;
}

export interface QualityMetric {
  label: string;
  value: string;
  detail: string;
  tone: Tone;
}

export interface LossApproval {
  code: string;
  workOrder: string;
  process: string;
  standard: string;
  actual: string;
  evidence: string;
  action: string;
  tone: Tone;
}

export interface InventoryLedgerEntry {
  time: string;
  bizCode: string;
  movement: string;
  before: string;
  after: string;
  source: string;
  operator: string;
}

export interface TraceEvidence {
  label: string;
  status: string;
  link: string;
  tone: Tone;
}

export interface MobileCapability {
  label: string;
  status: string;
  detail: string;
  tone: Tone;
}

export interface OfflineDraft {
  code: string;
  scene: string;
  savedAt: string;
  risk: string;
  tone: Tone;
}

export interface OpenSourceSampleMapping {
  source: string;
  referenceUrl: string;
  reusablePattern: string;
  mappedScenarioId: string;
  mappedModule: string;
  acceptanceRule: string;
  status: 'applied' | 'watching' | 'reserved';
}

export interface AcceptanceDataSet {
  code: string;
  fixture: string;
  scenarioId: string;
  automatedTest: string;
  covers: string[];
  evidence: string[];
  gaps: string[];
  status: 'active' | 'needs-field-data' | 'blocked';
}

export interface ProductionReadinessItem {
  code: string;
  category: string;
  title: string;
  why: string;
  defaultAction: string;
  owner: string;
  evidence: string;
  priority: 'P0' | 'P1';
  status: 'pending' | 'needs-trial' | 'confirmed' | 'deferred';
  tone: Tone;
}

// These samples are the review baseline for M1. In M3 they should become API seed data
// so UI tests, demos, and acceptance scripts all run against the same business cases.
export const metrics: Metric[] = [
  { label: '今日来料', value: '6 单', delta: '2 单待确认客户/物料', tone: 'blue' },
  { label: '在制工单', value: '18 张', delta: '5 张停留超过 4 小时', tone: 'violet' },
  { label: '待质检', value: '7 批', delta: '含 2 批返工复检', tone: 'amber' },
  { label: '库存异常', value: '3 条', delta: '冻结超期 1 条', tone: 'red' },
  { label: '视频证据', value: '92%', delta: '1 个截取任务失败', tone: 'green' },
  { label: '待出库', value: '4 单', delta: '最早交期 07-08', tone: 'gray' },
];

export const workQueues: WorkQueueItem[] = [
  {
    id: 'Q-001',
    title: '超标损耗待审批',
    subtitle: 'CC-20260706-001-JL-ZD001 抛光工序损耗 3.8%',
    owner: '车间组长',
    due: '18 分钟前',
    severity: 'high',
  },
  {
    id: 'Q-002',
    title: '拆包视频截取失败',
    subtitle: '母单 MD-20260706-004 需要重试或人工上传',
    owner: '库管员',
    due: '34 分钟前',
    severity: 'medium',
  },
  {
    id: 'Q-003',
    title: '待质检积压',
    subtitle: '镶石工序 5 批待检，最早 09:40 提交',
    owner: '质检员',
    due: '1 小时前',
    severity: 'medium',
  },
  {
    id: 'Q-004',
    title: '工单等待称重交接',
    subtitle: 'ZD-20260706-011 进入下一工序前必须补录重量',
    owner: '操作工',
    due: '今天 15:20',
    severity: 'normal',
  },
];

export const inboundOrders: InboundOrder[] = [
  {
    code: 'MD-20260706-001',
    customer: '恒信珠宝',
    material: '18K 金戒托',
    inboundDate: '07-06',
    expressCompany: '顺丰',
    courierNo: 'SF1048329001',
    senderName: '张先生',
    receiverName: '何琳',
    unpackSigner: '何琳',
    goodsName: '戒托来料',
    qty: 320,
    weight: '486.25 g',
    status: '待拆包',
    video: '待开始',
    owner: '客户所有',
    remark: '拆箱签字待复核',
  },
  {
    code: 'MD-20260706-002',
    customer: '雅辰饰品',
    material: '银链半成品',
    inboundDate: '07-06',
    expressCompany: '圆通',
    courierNo: 'YT7740192207',
    senderName: '陈女士',
    receiverName: '何琳',
    unpackSigner: '何琳',
    goodsName: '银链半成品',
    qty: 1200,
    weight: '2,840.00 g',
    status: '已入库',
    video: '已关联',
    owner: '客户所有',
    remark: '寄收件人已核对',
  },
  {
    code: 'MD-20260706-003',
    customer: '璟瑞工贸',
    material: '宝石托坯',
    inboundDate: '07-06',
    expressCompany: '京东',
    courierNo: 'JD9301827446',
    senderName: '客户仓库',
    receiverName: '李敏',
    unpackSigner: '待补签',
    goodsName: '宝石托盘',
    qty: 540,
    weight: '718.30 g',
    status: '待确认物料',
    video: '截取中',
    owner: '客户所有',
    remark: '货品名称与物料档案待匹配',
  },
  {
    code: 'MD-20260706-004',
    customer: '瑞禾珠宝',
    material: 'PT950 吊坠坯',
    inboundDate: '07-06',
    expressCompany: '德邦',
    courierNo: 'DB7730018291',
    senderName: '王先生',
    receiverName: '何琳',
    unpackSigner: '何琳',
    goodsName: 'PT950 吊坠坯',
    qty: 180,
    weight: '392.10 g',
    status: '异常待处理',
    video: '失败',
    owner: '客户所有',
    remark: '视频截取失败，需人工上传',
  },
];

export const inboundCheckpoints: InboundCheckpoint[] = [
  { label: '待扫码收货', value: '2 单', status: '快递单未绑定母单', tone: 'amber' },
  { label: '待拆包称重', value: '3 单', status: '需记录拆包前后重量', tone: 'blue' },
  { label: '视频完整率', value: '92%', status: '1 个截取任务失败', tone: 'amber' },
  { label: '标签可打印', value: '4 张', status: '含 1 张待补打', tone: 'green' },
];

export const inboundRisks: InboundRisk[] = [
  {
    code: 'MD-20260706-003',
    title: '物料名称待确认',
    detail: '宝石托盘客户描述与历史物料档案不一致，需要业务员确认后才能入账。',
    owner: '业务员',
    action: '确认物料档案',
    tone: 'amber',
  },
  {
    code: 'MD-20260706-004',
    title: '拆包视频截取失败',
    detail: 'NVR 时间段任务返回失败，系统允许重试一次，仍失败则人工上传视频。',
    owner: '库管员',
    action: '重试截取',
    tone: 'red',
  },
];

export const workOrders: WorkOrder[] = [
  {
    code: 'ZD-20260706-001',
    customer: '恒信珠宝',
    material: '18K 金戒托',
    process: '抛光',
    processCategory: '人工',
    productCategory: '戒托',
    machineNo: 'POL-01',
    workItemName: '精抛',
    startedAt: '13:40',
    finishedAt: '14:30',
    completionStatus: '部分完成',
    handoffAuditStatus: '待交单审核',
    assignee: '李华',
    plannedQty: 80,
    finishedQty: 56,
    status: '生产中',
    progress: 70,
  },
  {
    code: 'ZD-20260706-002',
    customer: '恒信珠宝',
    material: '18K 金戒托',
    process: '镶石',
    processCategory: '人工',
    productCategory: '戒托',
    machineNo: '人工工位',
    workItemName: '镶石',
    startedAt: '10:10',
    finishedAt: '11:25',
    completionStatus: '已完成',
    handoffAuditStatus: '已接收',
    assignee: '陈敏',
    plannedQty: 80,
    finishedQty: 80,
    status: '待质检',
    progress: 100,
  },
  {
    code: 'ZD-20260706-006',
    customer: '雅辰饰品',
    material: '银链半成品',
    process: '焊接',
    processCategory: '人工',
    productCategory: '银链',
    machineNo: 'WELD-02',
    workItemName: '焊接',
    startedAt: '15:00',
    finishedAt: '进行中',
    completionStatus: '称重待补',
    handoffAuditStatus: '待交单审核',
    assignee: '王强',
    plannedQty: 300,
    finishedQty: 210,
    status: '称重待补',
    progress: 70,
  },
  {
    code: 'ZD-20260706-009',
    customer: '恒信珠宝',
    material: '18K 金戒托',
    process: '雕刻',
    processCategory: '设备',
    productCategory: '戒托',
    machineNo: 'ENG-03',
    workItemName: '雕刻',
    startedAt: '09:00',
    finishedAt: '10:15',
    completionStatus: '已完成 168 件',
    handoffAuditStatus: '操机签字待补',
    assignee: '周凯',
    plannedQty: 200,
    finishedQty: 168,
    status: '待交单审核',
    progress: 84,
  },
  {
    code: 'FG-20260706-002',
    customer: '瑞禾珠宝',
    material: 'PT950 吊坠坯',
    process: '返工抛光',
    processCategory: '人工',
    productCategory: '吊坠坯',
    machineNo: 'POL-02',
    workItemName: '返工抛光',
    startedAt: '15:05',
    finishedAt: '进行中',
    completionStatus: '返工中',
    handoffAuditStatus: '待复检',
    assignee: '赵磊',
    plannedQty: 24,
    finishedQty: 12,
    status: '返工中',
    progress: 50,
  },
];

export const productionStationLoads: ProductionStationLoad[] = [
  { station: '雕刻', queue: '3 台', capacity: '68%', bottleneck: '1 台缺少操机签字', tone: 'amber' },
  { station: '抛光', queue: '6 张', capacity: '78%', bottleneck: '2 张等待交单审核', tone: 'amber' },
  { station: '镶石', queue: '5 张', capacity: '92%', bottleneck: '质检积压，暂缓派新单', tone: 'red' },
  { station: '焊接', queue: '4 张', capacity: '64%', bottleneck: '设备正常，可继续派工', tone: 'green' },
  { station: '终检', queue: '3 张', capacity: '55%', bottleneck: '等待缺陷照片归档', tone: 'blue' },
];

export const productionHandoffs: ProductionHandoff[] = [
  {
    code: 'HO-20260706-014',
    from: '粗抛 / 李华',
    to: '精抛 / 陈敏',
    inputWeight: '121.15 g',
    outputWeight: '119.92 g',
    lossRate: '1.02%',
    status: '可交接',
    tone: 'green',
  },
  {
    code: 'HO-20260706-018',
    from: '焊接 / 王强',
    to: '质检 / 周婷',
    inputWeight: '684.40 g',
    outputWeight: '683.20 g',
    lossRate: '0.18%',
    status: '待补照片',
    tone: 'amber',
  },
  {
    code: 'HO-20260706-021',
    from: '抛光 / 赵磊',
    to: '返工复检 / 刘洋',
    inputWeight: '52.40 g',
    outputWeight: '50.41 g',
    lossRate: '3.80%',
    status: '审批阻断',
    tone: 'red',
  },
];

export const qualityCases: QualityCase[] = [
  {
    code: 'QC-20260706-021',
    workOrder: 'ZD-20260706-002',
    process: '镶石',
    inspector: '周娜',
    defect: '松石 3 件，划痕 2 件',
    action: '返工',
    status: '待生成返工工单',
  },
  {
    code: 'QC-20260706-022',
    workOrder: 'ZD-20260706-006',
    process: '焊接',
    inspector: '周娜',
    defect: '焊点偏移 6 件',
    action: '复检',
    status: '待确认称重',
  },
  {
    code: 'QC-20260706-023',
    workOrder: 'FG-20260706-001',
    process: '返工抛光',
    inspector: '刘洋',
    defect: '返工通过',
    action: '放行',
    status: '已完成',
  },
];

export const qualityMetrics: QualityMetric[] = [
  { label: '待派检', value: '7 批', detail: '最早提交 09:40', tone: 'amber' },
  { label: '返工复检', value: '2 批', detail: '均已生成返工工单', tone: 'violet' },
  { label: '缺陷照片', value: '15 张', detail: '2 张待补拍', tone: 'blue' },
  { label: '损耗阻断', value: '1 条', detail: '超过工序标准 1.8%', tone: 'red' },
];

export const lossApprovals: LossApproval[] = [
  {
    code: 'LOSS-20260706-009',
    workOrder: 'ZD-20260706-001',
    process: '抛光',
    standard: '2.0%',
    actual: '3.8%',
    evidence: '投入 121.15g / 产出 116.55g / 缺陷照片 5 张',
    action: '待组长审批',
    tone: 'red',
  },
  {
    code: 'LOSS-20260706-010',
    workOrder: 'ZD-20260706-006',
    process: '焊接',
    standard: '0.8%',
    actual: '待确认',
    evidence: '缺少产出称重照片',
    action: '待补称重',
    tone: 'amber',
  },
];

export const inventoryBalances: InventoryBalance[] = [
  {
    owner: '恒信珠宝',
    batch: 'MD-20260706-001',
    material: '18K 金戒托',
    location: '原料库 A-01',
    status: '待加工',
    qty: 240,
    weight: '365.10 g',
    frozen: '0',
  },
  {
    owner: '恒信珠宝',
    batch: 'ZD-20260706-001',
    material: '18K 金戒托',
    location: '车间-抛光',
    status: '在制',
    qty: 80,
    weight: '121.15 g',
    frozen: '0',
  },
  {
    owner: '雅辰饰品',
    batch: 'MD-20260706-002',
    material: '银链半成品',
    location: '成品库 F-02',
    status: '成品',
    qty: 620,
    weight: '1,451.20 g',
    frozen: '200 件',
  },
  {
    owner: '瑞禾珠宝',
    batch: 'FG-20260706-002',
    material: 'PT950 吊坠坯',
    location: '返工区 R-01',
    status: '返工在制',
    qty: 24,
    weight: '52.40 g',
    frozen: '0',
  },
];

export const traceEvents: TraceEvent[] = [
  {
    time: '08:42',
    node: '扫码收货',
    bizCode: 'SF1048329001',
    operator: '库管员 何琳',
    evidence: '快递单照片',
  },
  {
    time: '09:05',
    node: '拆包称重',
    bizCode: 'MD-20260706-001',
    operator: '库管员 何琳',
    evidence: '拆包视频 05:14',
  },
  {
    time: '10:20',
    node: '生成子工单',
    bizCode: 'ZD-20260706-001',
    operator: '车间组长 马杰',
    evidence: '母单标签扫描',
  },
  {
    time: '14:30',
    node: '工序报工',
    bizCode: 'BG-20260706-044',
    operator: '操作工 李华',
    evidence: '称重交接 121.15g',
  },
  {
    time: '15:10',
    node: '质检异常',
    bizCode: 'QC-20260706-021',
    operator: '质检员 周娜',
    evidence: '缺陷照片 5 张',
  },
];

export const inventoryLedgerEntries: InventoryLedgerEntry[] = [
  {
    time: '09:05',
    bizCode: 'LEDGER-20260706-001',
    movement: '来料入库',
    before: '0 件 / 0 g',
    after: '320 件 / 486.25 g',
    source: 'MD-20260706-001',
    operator: '何琳',
  },
  {
    time: '10:20',
    bizCode: 'LEDGER-20260706-007',
    movement: '工单领料',
    before: '320 件 / 486.25 g',
    after: '240 件 / 365.10 g',
    source: 'ZD-20260706-001',
    operator: '马杰',
  },
  {
    time: '14:30',
    bizCode: 'LEDGER-20260706-018',
    movement: '工序产出',
    before: '80 件 / 121.15 g',
    after: '56 件 / 84.95 g',
    source: 'BG-20260706-044',
    operator: '李华',
  },
  {
    time: '15:10',
    bizCode: 'LEDGER-20260706-022',
    movement: '质检冻结',
    before: '56 件 / 84.95 g',
    after: '5 件冻结 / 7.20 g',
    source: 'QC-20260706-021',
    operator: '周婷',
  },
];

export const traceEvidence: TraceEvidence[] = [
  { label: '业务单据', status: '已关联 6 张', link: '母单 / 子工单 / 报工 / 质检 / 台账 / 出库', tone: 'green' },
  { label: '称重记录', status: '2 条待补照片', link: 'HO-20260706-018 / HO-20260706-021', tone: 'amber' },
  { label: '视频证据', status: '1 个截取失败', link: 'INT-VID-001', tone: 'red' },
  { label: '操作审计', status: '完整', link: '扫码、补打、审批均已留痕', tone: 'green' },
];

export const mobileTasks: MobileTask[] = [
  {
    code: 'ZD-20260706-001',
    scene: '扫码进入报工',
    nextAction: '提交抛光数量 24 件',
    required: '数量、重量、照片可选',
    status: '可操作',
  },
  {
    code: 'ZD-20260706-006',
    scene: '称重交接',
    nextAction: '补录投入/产出重量',
    required: '投入重量、产出重量、交接人',
    status: '必填阻断',
  },
  {
    code: 'QC-20260706-021',
    scene: '移动质检',
    nextAction: '选择返工原因并拍照',
    required: '不合格数量、原因、处理方式',
    status: '待质检',
  },
];

export const mobileCapabilities: MobileCapability[] = [
  { label: '扫码识别', status: '可用', detail: 'BarcodeDetector 优先，ZXing 兜底，失败可手输编码。', tone: 'green' },
  { label: '离线草稿', status: '设计中', detail: '弱网时暂存报工和质检，恢复网络后补提交。', tone: 'amber' },
  { label: '拍照留证', status: '一期必做', detail: '缺陷、称重、人工视频兜底都需要附件。', tone: 'blue' },
  { label: '权限边界', status: '待接入', detail: '当前为样例身份，M3 接入真实角色权限。', tone: 'gray' },
];

export const offlineDrafts: OfflineDraft[] = [
  {
    code: 'DRAFT-BG-001',
    scene: '抛光报工',
    savedAt: '14:28',
    risk: '恢复网络后需复核工单状态',
    tone: 'amber',
  },
  {
    code: 'DRAFT-QC-003',
    scene: '缺陷拍照',
    savedAt: '15:06',
    risk: '照片已缓存，质检结果未提交',
    tone: 'red',
  },
];

export const sampleScenarios: SampleScenario[] = [
  {
    id: 'SAMPLE-MAIN-001',
    name: '客户来料主链路',
    owner: '业务评审',
    purpose: '验证从快递收货到追溯查询的端到端样例',
    chain: ['客户订单', '来料母单', '拆包称重', '子工单', '报工', '质检', '成品入库', '出库追溯'],
    evidence: ['母单标签', '称重交接', '拆包视频', '库存台账', '质检照片'],
    status: 'ready',
  },
  {
    id: 'SAMPLE-REWORK-001',
    name: '质检返工样例',
    owner: '质检评审',
    purpose: '验证返工只走一套工单状态机，避免双状态机冲突',
    chain: ['报工完成', '质检不合格', '生成返工工单', '返工报工', '复检放行'],
    evidence: ['缺陷照片', '返工原因', '返工工单', '复检记录'],
    status: 'ready',
  },
  {
    id: 'SAMPLE-LOSS-001',
    name: '超标损耗审批样例',
    owner: '车间评审',
    purpose: '验证损耗归因能追溯到称重交接和工序投入',
    chain: ['工序投入', '产出称重', '损耗计算', '超标审批', '工资试算扣减'],
    evidence: ['投入重量', '产出重量', '审批记录', '工资试算快照'],
    status: 'needs-data',
  },
  {
    id: 'SAMPLE-HW-001',
    name: '视频与标签兜底样例',
    owner: '硬件评审',
    purpose: '验证 NVR 截取失败时可以人工上传，不阻塞业务流转',
    chain: ['开始拆包', '记录时间段', '视频截取失败', '人工上传', '追溯关联'],
    evidence: ['NVR 时间段', '失败日志', '人工上传文件', '操作日志'],
    status: 'ready',
  },
];

export const openSourceSampleMappings: OpenSourceSampleMapping[] = [
  {
    source: 'ERPNext Work Order / Job Card',
    referenceUrl: 'https://docs.frappe.io/erpnext/user/manual/en/job-card',
    reusablePattern: '工单按工序拆成 Job Card，记录工作站、操作员、数量、时间和 WIP 移动。',
    mappedScenarioId: 'SAMPLE-MAIN-001',
    mappedModule: '生产工单 / 工序报工',
    acceptanceRule: '每一次报工必须落库存台账或证据事件，不能只改页面状态。',
    status: 'applied',
  },
  {
    source: 'Odoo Manufacturing Quality Checks',
    referenceUrl: 'https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/quality/quality_management/quality_checks.html',
    reusablePattern: '质量检查点绑定制造流转，现场按 Pass/Fail、拍照、测量等检查类型执行。',
    mappedScenarioId: 'SAMPLE-REWORK-001',
    mappedModule: '质检返工 / 复检放行',
    acceptanceRule: '质检样例必须包含缺陷、处理动作、返工或复检结果以及附件证据。',
    status: 'applied',
  },
  {
    source: 'InvenTree Build Orders / Stock',
    referenceUrl: 'https://docs.inventree.org/en/stable/manufacturing/build/',
    reusablePattern: '构建单分配、消耗库存并产生成品库存，库存批次和库位保持可追溯。',
    mappedScenarioId: 'SAMPLE-MAIN-001',
    mappedModule: '库存追溯 / 客户所有权',
    acceptanceRule: '客户所有权、批次、库位、库存状态必须随真实链路连续校验。',
    status: 'applied',
  },
  {
    source: 'Odoo Shop Floor',
    referenceUrl: 'https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/manufacturing/shop_floor.html',
    reusablePattern: '现场作业界面优先呈现工位、下一动作、阻断原因和质量检查。',
    mappedScenarioId: 'SAMPLE-HW-001',
    mappedModule: '首页工作台 / PWA 作业',
    acceptanceRule: '首页和移动端样例必须先展示待处理对象、责任人和下一动作。',
    status: 'watching',
  },
];

export const acceptanceDataSets: AcceptanceDataSet[] = [
  {
    code: 'REAL-FLOW-20260706-001',
    fixture: 'backend/src/test/resources/acceptance/real-flow-scenario-20260706.json',
    scenarioId: 'SAMPLE-MAIN-001',
    automatedTest: 'BusinessOperationRealFlowTest',
    covers: [
      '客户所有权库存维度',
      '生产报工',
      '质检冻结',
      '返工派发',
      '复检放行',
      '成品出库',
      '客户退货',
      '售后复核分流',
      '损耗审批工资试算',
    ],
    evidence: [
      'HO-20260706-021',
      'PHOTO-QC-20260706-021',
      'ATT-OUT-20260706-021',
      'PHOTO-ASR-20260707-021',
    ],
    gaps: [
      '仍为单份脱敏 JSON fixture',
      '尚未覆盖批量导入真实单据',
      '尚未绑定真实登录操作人',
    ],
    status: 'active',
  },
  {
    code: 'FIELD-HW-POC-20260706',
    fixture: 'frontend/src/data/m1PrototypeData.ts#hardwarePocRecords',
    scenarioId: 'SAMPLE-HW-001',
    automatedTest: 'm1PrototypeData.test.ts / HardwareIntegrationWorkspace.test.ts',
    covers: ['NVR 截取失败兜底', '标签打印补打', 'PWA 弱光扫码失败样本', '称重人工录入'],
    evidence: ['POC-VID-20260706-001', 'POC-LBL-20260706-001', 'POC-SCAN-20260706-001', 'POC-SCALE-20260706-001'],
    gaps: ['尚未上传现场真实视频片段', '尚未冻结电子秤接口型号', '尚未完成弱光扫码二轮 POC'],
    status: 'needs-field-data',
  },
];

export const productionReadinessItems: ProductionReadinessItem[] = [
  {
    code: 'P0-01',
    category: '所有权',
    title: '客户来料所有权边界',
    why: '客户料和自有料混账会导致库存、损耗和赔付无法解释。',
    defaultAction: '一期所有来料默认客户所有；企业自有料单独仓库和台账维度。',
    owner: '业务负责人 / 仓库',
    evidence: '客户来料样例、库存余额截图、台账导出',
    priority: 'P0',
    status: 'pending',
    tone: 'red',
  },
  {
    code: 'P0-02',
    category: '损耗',
    title: '损耗计算口径',
    why: '损耗直接影响审批、责任归因和工资试算。',
    defaultAction: '按工序投入计算，不用母单总入和最终出货倒推。',
    owner: '车间主管 / 财务',
    evidence: '损耗公式签字确认、3 条真实工单试算',
    priority: 'P0',
    status: 'pending',
    tone: 'red',
  },
  {
    code: 'P0-06',
    category: '返工',
    title: '返工主状态机',
    why: '返工单和返工工单双状态机会导致库存重复流转。',
    defaultAction: '一期返工作为特殊工单类型，关联原工单、原工序和质检记录。',
    owner: '质检主管 / 技术负责人',
    evidence: '返工闭环测试用例',
    priority: 'P0',
    status: 'needs-trial',
    tone: 'amber',
  },
  {
    code: 'P0-07',
    category: '登记表',
    title: '现场登记表导入边界',
    why: '纸质/Excel 数据质量不稳定，直接入账风险高。',
    defaultAction: '一期只开放上传预检和问题 CSV，不自动确认入账。',
    owner: '仓库 / 车间 / 实施',
    evidence: '4 张登记表预检结果和修表记录',
    priority: 'P0',
    status: 'confirmed',
    tone: 'green',
  },
  {
    code: 'P0-08',
    category: '主数据',
    title: '真实客户、物料、工序主数据',
    why: '没有真实主数据无法验证匹配、追溯和库存维度。',
    defaultAction: '提供脱敏客户 5 个、物料 20 个、工序模板 3 套。',
    owner: '业务负责人 / 实施',
    evidence: '脱敏主数据表、导入日志',
    priority: 'P0',
    status: 'pending',
    tone: 'red',
  },
  {
    code: 'P0-09',
    category: '运维',
    title: '备份和恢复验收',
    why: '只备份不恢复无法证明上线可恢复。',
    defaultAction: '上线前做一次非破坏性恢复演练，保留 JSON 记录和 checksum。',
    owner: 'IT / 实施',
    evidence: 'verify-restore-*.json、备份 SHA-256',
    priority: 'P0',
    status: 'needs-trial',
    tone: 'amber',
  },
  {
    code: 'P0-10',
    category: '权限',
    title: '登录账号和权限边界',
    why: '演示账号不能用于生产，敏感动作必须受控。',
    defaultAction: '上线前建立生产用户表、密码哈希、角色、数据范围和登录审计。',
    owner: 'IT / 管理员',
    evidence: '账号清单、权限矩阵、登录审计',
    priority: 'P0',
    status: 'pending',
    tone: 'red',
  },
  {
    code: 'BR-LOSS-01',
    category: '损耗',
    title: '损耗主口径',
    why: '重量、件数、金额混用会导致损耗报表和工资试算不可解释。',
    defaultAction: '贵重物料按重量，普通件按数量，金额只用于试算。',
    owner: '车间主管 / 财务',
    evidence: '损耗规则确认表、试算截图',
    priority: 'P0',
    status: 'pending',
    tone: 'red',
  },
  {
    code: 'BR-PROC-02',
    category: '工序',
    title: '必须交单审核的工序',
    why: '未定义审核节点会让抛光、雕刻、返工抛光直接进入下一环节。',
    defaultAction: '抛光、雕刻、返工抛光先设为必须交单审核。',
    owner: '车间主管 / 质检',
    evidence: '工序模板配置、交单审核样例',
    priority: 'P1',
    status: 'needs-trial',
    tone: 'amber',
  },
  {
    code: 'OPS-01',
    category: '部署',
    title: '部署形态',
    why: '腾讯云和本地部署的备份、硬件接入、网络和运维责任不同。',
    defaultAction: '保留 CVM + Docker Compose + COS 与本地服务器 + Docker Compose 两套交付方案。',
    owner: 'IT / 实施',
    evidence: '部署方案签字、资源清单、运行包验收记录',
    priority: 'P1',
    status: 'pending',
    tone: 'amber',
  },
];

export const hardwareDevices: HardwareDevice[] = [
  {
    code: 'CAM-UNPACK-01',
    name: '拆包台摄像头',
    scene: '来料拆包视频证据',
    protocol: 'NVR 时间段截取 / RTSP 预留',
    status: 'degraded',
    fallback: '截取失败后允许人工上传 MP4',
  },
  {
    code: 'PRN-LABEL-01',
    name: '标签打印机',
    scene: '母单/子工单标签',
    protocol: '浏览器打印 / Windows 驱动',
    status: 'manual',
    fallback: '使用 PDF 标签模板人工打印',
  },
  {
    code: 'SCAN-PC-01',
    name: 'PC 扫码枪',
    scene: '后台收货和标签复核',
    protocol: 'USB HID 键盘模式',
    status: 'online',
    fallback: '手工录入编码并二次确认',
  },
  {
    code: 'PWA-CAM-01',
    name: '手机摄像头扫码',
    scene: 'PWA 报工/质检',
    protocol: 'BarcodeDetector / ZXing 兜底',
    status: 'degraded',
    fallback: '扫码失败时手工输入工单码',
  },
  {
    code: 'SCALE-01',
    name: '电子秤',
    scene: '称重交接',
    protocol: '一期人工读数，二期 RS232/USB/TCP',
    status: 'manual',
    fallback: '人工录入并拍照留证',
  },
];

export const integrationTasks: IntegrationTask[] = [
  {
    id: 'INT-VID-001',
    title: '拆包视频截取任务',
    target: 'CAM-UNPACK-01 / MD-20260706-004',
    health: 62,
    lastRun: '17:08 截取失败',
    nextAction: '重试一次，仍失败则人工上传',
    owner: '库管员',
  },
  {
    id: 'INT-LBL-001',
    title: '母单标签打印验证',
    target: 'PRN-LABEL-01 / MD-20260706-001',
    health: 88,
    lastRun: '16:42 打印成功',
    nextAction: '补打日志写入审计',
    owner: '库管员',
  },
  {
    id: 'INT-PWA-001',
    title: 'PWA 扫码报工验证',
    target: 'PWA-CAM-01 / ZD-20260706-001',
    health: 74,
    lastRun: '15:30 识别成功',
    nextAction: '补移动端弱光测试',
    owner: '车间组长',
  },
  {
    id: 'INT-SCALE-001',
    title: '称重交接录入验证',
    target: 'SCALE-01 / ZD-20260706-006',
    health: 55,
    lastRun: '14:12 人工录入',
    nextAction: '确认拍照留证和误差参数',
    owner: '质检员',
  },
];

export const hardwarePocRecords: HardwarePocRecord[] = [
  {
    code: 'POC-VID-20260706-001',
    deviceCode: 'CAM-UNPACK-01',
    scene: '拆包台视频截取',
    testWindow: '2026-07-06 08:40-09:10',
    result: '有条件通过',
    evidence: 'NVR 时间段可定位，FFmpeg 截取偶发超时',
    blocker: '需要确认 NVR 账号权限和时间同步',
    owner: '实施顾问',
    nextAction: '补充 NTP 校时检查和一次弱网重试',
    tone: 'amber',
  },
  {
    code: 'POC-LBL-20260706-001',
    deviceCode: 'PRN-LABEL-01',
    scene: '母单/子工单标签打印',
    testWindow: '2026-07-06 10:30-10:45',
    result: '通过',
    evidence: '浏览器打印成功，PDF 模板可兜底',
    blocker: '无阻断',
    owner: '库管员',
    nextAction: '锁定标签模板尺寸并补打印审计',
    tone: 'green',
  },
  {
    code: 'POC-SCAN-20260706-001',
    deviceCode: 'PWA-CAM-01',
    scene: 'PWA 弱光扫码',
    testWindow: '2026-07-06 15:20-15:45',
    result: '阻断',
    evidence: '弱光下识别率不足，手工输入可继续业务',
    blocker: '需要补充 ZXing 兜底和现场光照标准',
    owner: '车间组长',
    nextAction: '安排第二轮弱光 POC 并记录失败样本',
    tone: 'red',
  },
  {
    code: 'POC-SCALE-20260706-001',
    deviceCode: 'SCALE-01',
    scene: '称重交接人工录入',
    testWindow: '2026-07-06 14:00-14:20',
    result: '有条件通过',
    evidence: '人工读数、拍照留证、误差参数可支撑一期',
    blocker: '二期电子秤接口未冻结',
    owner: '质检员',
    nextAction: '一期冻结人工录入，二期单独验证 RS232/USB/TCP',
    tone: 'amber',
  },
];

export function getOpenExceptionCount(): number {
  return workQueues.filter((item) => item.severity !== 'normal').length;
}

export function getHardwareManualFallbackCount(): number {
  return hardwareDevices.filter((device) => device.status !== 'online').length;
}

export function getHardwarePocBlockedCount(): number {
  return hardwarePocRecords.filter((record) => record.result === '阻断').length;
}
