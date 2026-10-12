// 仅供测试和实施评审使用的夹具，不参与日常业务页面初始化。
import type { HardwareDevice, IntegrationTask, HardwarePocRecord, Tone } from './m1PrototypeData';

export interface SampleScenario {
  id: string;
  name: string;
  owner: string;
  purpose: string;
  chain: string[];
  evidence: string[];
  status: 'ready' | 'needs-data' | 'blocked';
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
    why: '纸质/Excel 数据质量不稳定，绕过复核直接入账风险高。',
    defaultAction: '一期开放上传预检、问题 CSV、结构化批次确认、只读入账映射计划和显式入账命令。',
    owner: '仓库 / 车间 / 实施',
    evidence: '预检结果、确认批次、posting command 结果和台账号',
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
    defaultAction: 'M3.117 已建立账号表、PBKDF2 密码哈希、失败锁定、启停、停用会话吊销、管理员解锁、强制首次登录改密、临时密码一次性消费、密码策略、密码过期边界、登录态自助改密续期、认证审计摘要链、业务工作台 GET 首版数据范围过滤、账号审批摘要、账号客户范围展示、前置审批申请单、申请单审批任务交接和强制模式审批通过态校验；上线前替换初始评审账号并补 SSO/JWT、完整组织客户授权表、正式电子签字和审计保全。',
    owner: 'IT / 管理员',
    evidence: '账号清单、权限矩阵、登录审计',
    priority: 'P0',
    status: 'pending',
    tone: 'red',
  },
  {
    code: 'P1-HW-POC-01',
    category: '软硬件',
    title: '硬件 POC 记录证据闭环',
    why: 'POC 记录已可落库，M3.106 已有只读签字闭环就绪度契约，但缺少真实附件上传、正式电子签字和归档闭环仍会让硬件适配结论不可追溯。',
    defaultAction: 'M3.106 已覆盖 hw_poc_record 内存/JDBC 仓储、POC 证据只读关联、适配计划、协议样本、参数复核、复测执行结果和签字闭环就绪度；下一步补现场附件上传、真实设备型号冻结、正式电子签字和负责人闭环。',
    owner: '实施 / IT / 车间',
    evidence: 'V19 seed、/api/hardware/poc-records、现场附件',
    priority: 'P1',
    status: 'needs-trial',
    tone: 'amber',
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


export function getHardwareManualFallbackCount(): number {
  return hardwareDevices.filter((device) => device.status !== 'online').length;
}

export function getHardwarePocBlockedCount(): number {
  return hardwarePocRecords.filter((record) => record.result === '阻断').length;
}
