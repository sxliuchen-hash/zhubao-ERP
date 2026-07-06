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
  courierNo: string;
  qty: number;
  weight: string;
  status: string;
  video: string;
  owner: string;
}

export interface WorkOrder {
  code: string;
  customer: string;
  material: string;
  process: string;
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
    courierNo: 'SF1048329001',
    qty: 320,
    weight: '486.25 g',
    status: '待拆包',
    video: '待开始',
    owner: '客户所有',
  },
  {
    code: 'MD-20260706-002',
    customer: '雅辰饰品',
    material: '银链半成品',
    courierNo: 'YT7740192207',
    qty: 1200,
    weight: '2,840.00 g',
    status: '已入库',
    video: '已关联',
    owner: '客户所有',
  },
  {
    code: 'MD-20260706-003',
    customer: '璟瑞工贸',
    material: '宝石托坯',
    courierNo: 'JD9301827446',
    qty: 540,
    weight: '718.30 g',
    status: '待确认物料',
    video: '截取中',
    owner: '客户所有',
  },
  {
    code: 'MD-20260706-004',
    customer: '瑞禾珠宝',
    material: 'PT950 吊坠坯',
    courierNo: 'DB7730018291',
    qty: 180,
    weight: '392.10 g',
    status: '异常待处理',
    video: '失败',
    owner: '客户所有',
  },
];

export const workOrders: WorkOrder[] = [
  {
    code: 'ZD-20260706-001',
    customer: '恒信珠宝',
    material: '18K 金戒托',
    process: '抛光',
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
    assignee: '王强',
    plannedQty: 300,
    finishedQty: 210,
    status: '称重待补',
    progress: 70,
  },
  {
    code: 'FG-20260706-002',
    customer: '瑞禾珠宝',
    material: 'PT950 吊坠坯',
    process: '返工抛光',
    assignee: '赵磊',
    plannedQty: 24,
    finishedQty: 12,
    status: '返工中',
    progress: 50,
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

export function getOpenExceptionCount(): number {
  return workQueues.filter((item) => item.severity !== 'normal').length;
}

export function getHardwareManualFallbackCount(): number {
  return hardwareDevices.filter((device) => device.status !== 'online').length;
}
