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

export function getOpenExceptionCount(): number {
  return workQueues.filter((item) => item.severity !== 'normal').length;
}
