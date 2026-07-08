import type { Tone } from './m1PrototypeData';
import type {
  AuthAccountPreApprovalApplication,
  AuthAccountPreApprovalDraft,
  AuthAccountPreApprovalReadiness,
  AuthUserAccountSummary,
} from '../services/authApi';

export interface ConfigurationBaselineSummary {
  systemParameters: number;
  codeRules: number;
  roleBaselines: number;
  processTemplates: number;
  warehouseStates: number;
  videoParameters: number;
  approvalRequiredItems: number;
  changeRequests: number;
  auditEvents: number;
}

export interface SystemParameter {
  key: string;
  value: string;
  category: string;
  level: string;
  description: string;
  changeControl: string;
  effectiveScope: string;
}

export interface CodeRule {
  objectType: string;
  format: string;
  example: string;
  uniqueness: string;
  changePolicy: string;
}

export interface RolePermissionBaseline {
  role: string;
  dataScope: string;
  permissions: string[];
  sensitiveActions: string[];
}

export interface ProcessTemplate {
  code: string;
  name: string;
  materialScope: string;
  steps: string[];
  lossRule: string;
  wageRule: string;
  qualityRule: string;
  snapshotFields: string;
}

export interface WarehouseState {
  code: string;
  name: string;
  ownerBoundary: string;
  stockStatus: string;
  transitionRule: string;
  negativeAllowed: boolean;
}

export interface VideoParameter {
  key: string;
  value: string;
  scene: string;
  fallback: string;
  retention: string;
  changeControl: string;
}

export interface ComponentBaseline {
  component: string;
  version: string;
  license: string;
  purpose: string;
  source: string;
  risk: string;
}

export interface BackupPolicy {
  target: string;
  strategy: string;
  rpo: string;
  rto: string;
  owner: string;
}

export interface ConfigurationChangeRequest {
  code: string;
  targetKey: string;
  targetName: string;
  changeType: string;
  beforeValue: string;
  afterValue: string;
  reason: string;
  applicant: string;
  approver: string;
  status: string;
  riskLevel: string;
  submittedAt: string;
}

export interface ConfigurationAuditEvent {
  eventId: string;
  targetKey: string;
  operation: string;
  operator: string;
  occurredAt: string;
  result: string;
  evidence: string;
  traceId: string;
}

export interface PermissionGrant {
  code: string;
  label: string;
}

export interface SecurityHeaderContract {
  operatorHeader: string;
  roleHeader: string;
  dataScopeHeader: string;
  customerScopeHeader: string;
}

export interface NavigationMenuItem {
  code: string;
  label: string;
  module: string;
  route: string;
  buttonCodes: string[];
  children: NavigationMenuItem[];
}

export interface SecurityCurrentContext {
  operator: string;
  role: string;
  roleLabel: string;
  dataScope: string;
  dataScopeLabel: string;
  dataBoundary: string;
  customerScope: string[];
  headerBased: boolean;
  allowedPermissions: PermissionGrant[];
  sensitiveActionCodes: string[];
  menuCodes: string[];
  menuTree: NavigationMenuItem[];
  limitationNotes: string[];
  headerContract: SecurityHeaderContract;
}

export interface ConfigurationBaselineWorkspace {
  summary: ConfigurationBaselineSummary;
  systemParameters: SystemParameter[];
  codeRules: CodeRule[];
  rolePermissions: RolePermissionBaseline[];
  processTemplates: ProcessTemplate[];
  warehouseStates: WarehouseState[];
  videoParameters: VideoParameter[];
  components: ComponentBaseline[];
  backupPolicies: BackupPolicy[];
  changeRequests: ConfigurationChangeRequest[];
  auditEvents: ConfigurationAuditEvent[];
  securityContext: SecurityCurrentContext;
}

export const configurationToneByLevel: Record<string, Tone> = {
  C1: 'red',
  C2: 'amber',
  C3: 'blue',
  C4: 'violet',
  C5: 'gray',
};

export const authAccountSummaryFallback: AuthUserAccountSummary[] = [
  {
    username: 'admin',
    operator: '系统管理员',
    role: 'ADMIN',
    roleLabel: '系统管理员',
    dataScope: 'ALL',
    dataScopeLabel: '全部数据',
    customerScope: [],
    enabled: true,
    demoAccount: true,
    passwordChangedAt: '2026-07-08 09:00:00',
    passwordExpiresAt: '2026-10-06 09:00:00',
    passwordExpired: false,
    failedLoginCount: 0,
    lockedUntil: '',
    locked: false,
  },
  {
    username: 'leader',
    operator: '车间组长 马杰',
    role: 'WORKSHOP_LEADER',
    roleLabel: '车间组长',
    dataScope: 'TEAM',
    dataScopeLabel: '本班组/工位',
    customerScope: [],
    enabled: true,
    demoAccount: true,
    passwordChangedAt: '2026-07-08 09:00:00',
    passwordExpiresAt: '2026-10-06 09:00:00',
    passwordExpired: false,
    failedLoginCount: 0,
    lockedUntil: '',
    locked: false,
  },
  {
    username: 'warehouse',
    operator: '库管员 何琳',
    role: 'WAREHOUSE_KEEPER',
    roleLabel: '库管员',
    dataScope: 'DEPARTMENT',
    dataScopeLabel: '本部门',
    customerScope: [],
    enabled: true,
    demoAccount: true,
    passwordChangedAt: '2026-07-08 09:00:00',
    passwordExpiresAt: '2026-10-06 09:00:00',
    passwordExpired: false,
    failedLoginCount: 1,
    lockedUntil: '',
    locked: false,
  },
  {
    username: 'operator',
    operator: '操作工 王强',
    role: 'OPERATOR',
    roleLabel: '操作工',
    dataScope: 'SELF',
    dataScopeLabel: '本人任务',
    customerScope: [],
    enabled: true,
    demoAccount: true,
    passwordChangedAt: '2026-07-08 09:00:00',
    passwordExpiresAt: '2026-10-06 09:00:00',
    passwordExpired: false,
    failedLoginCount: 0,
    lockedUntil: '',
    locked: false,
  },
  {
    username: 'planner',
    operator: '计划员 陈敏',
    role: 'SALES',
    roleLabel: '业务员',
    dataScope: 'CUSTOMER_RANGE',
    dataScopeLabel: '授权客户范围',
    customerScope: ['恒信珠宝', '雅辰饰品'],
    enabled: false,
    demoAccount: false,
    passwordChangedAt: '2026-03-01 09:00:00',
    passwordExpiresAt: '2026-05-30 09:00:00',
    passwordExpired: true,
    failedLoginCount: 0,
    lockedUntil: '',
    locked: false,
  },
];

export const authAccountPreApprovalReadinessFallback: AuthAccountPreApprovalReadiness[] = [
  {
    readinessCode: 'AUTH-PRE-CREATE-001',
    actionType: '创建生产账号',
    scenario: '新增非 demo 账号或授予敏感角色前必须先完成申请与审批',
    enforcementStatus: '生产配置可强制申请单号',
    requiredBeforeAction: ['账号申请单', '直属负责人审批', '系统管理员复核', '角色与数据范围说明', '初始密码交付确认'],
    missingCapabilities: ['审批通过态强制校验', '正式电子签字', '审批后自动放行'],
    approverRoles: ['GENERAL_MANAGER', 'ADMIN'],
    auditEvidence: '当前可保存前置申请单、生成 ACCOUNT_PRE_APPROVAL 任务，并在强制模式校验任务 APPROVED 后放行账号动作',
    rolloutGate: '生产上线前禁止直接创建生产账号',
    nextAction: '补正式电子签字、审批后自动放行和完整 IAM 账号生命周期',
    riskLevel: '高',
    tone: 'red',
  },
  {
    readinessCode: 'AUTH-PRE-ROLE-001',
    actionType: '变更角色/数据范围',
    scenario: '调整角色、dataScope 或 customerScope 会改变可见客户和敏感动作',
    enforcementStatus: '仅事后审批摘要',
    requiredBeforeAction: ['变更原因', '原角色/新角色对比', '客户范围清单', '业务负责人确认', '回收计划'],
    missingCapabilities: ['客户授权表', '审批前差异比对', '变更前冻结快照', '审批拒绝回滚'],
    approverRoles: ['GENERAL_MANAGER', 'ADMIN', '业务负责人'],
    auditEvidence: '账号审计会记录目标角色、数据范围和客户范围',
    rolloutGate: '生产上线前仅允许评审环境通过账号管理直接调整',
    nextAction: '补组织客户授权表和审批前差异校验',
    riskLevel: '高',
    tone: 'red',
  },
  {
    readinessCode: 'AUTH-PRE-PASSWORD-001',
    actionType: '管理员重置密码',
    scenario: '管理员重置生产账号密码前必须核实本人或离线交接场景',
    enforcementStatus: '弱前置校验',
    requiredBeforeAction: ['重置原因', '本人核验方式', '临时密码交付记录', '密码过期策略确认'],
    missingCapabilities: ['本人核验附件', '临时密码一次性通道', '强制首次登录改密'],
    approverRoles: ['ADMIN', '账号负责人'],
    auditEvidence: '当前会刷新 passwordChangedAt、清零失败锁定并写 ACCOUNT_MANAGEMENT 审计',
    rolloutGate: '允许管理员重置，但必须保留 reason 和审计记录',
    nextAction: '补本人核验附件和首次登录强制改密标记',
    riskLevel: '中',
    tone: 'amber',
  },
  {
    readinessCode: 'AUTH-PRE-STATUS-001',
    actionType: '账号启停/解锁',
    scenario: '停用、启用或解锁会直接影响现场登录能力',
    enforcementStatus: '弱前置校验',
    requiredBeforeAction: ['启停/解锁原因', '影响岗位', '交接状态', '管理员复核'],
    missingCapabilities: ['岗位交接附件', '解锁本人确认', '停用后会话吊销'],
    approverRoles: ['ADMIN', 'GENERAL_MANAGER'],
    auditEvidence: '当前启停/解锁会写审计并生成事后复核任务',
    rolloutGate: '试点允许管理员处理，生产需补停用会话吊销',
    nextAction: '补会话吊销、岗位交接附件和解锁本人确认',
    riskLevel: '中',
    tone: 'amber',
  },
];

export const authAccountPreApprovalDraftFallback: AuthAccountPreApprovalDraft = {
  draftCode: 'AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER',
  readinessCode: 'AUTH-PRE-CREATE-001',
  actionType: '创建生产账号',
  targetUsername: 'planner',
  applicant: '系统管理员',
  applicantRole: 'ADMIN',
  requiredEvidence: ['账号申请单', '直属负责人审批', '系统管理员复核', '角色与数据范围说明', '初始密码交付确认'],
  providedEvidence: ['账号申请单：AUTH-REQ-001'],
  missingEvidence: ['直属负责人审批', '系统管理员复核', '角色与数据范围说明', '初始密码交付确认'],
  missingCapabilities: ['审批通过态强制校验', '正式电子签字', '审批后自动放行'],
  approverRoles: ['GENERAL_MANAGER', 'ADMIN'],
  blockingStatus: '缺少前置材料',
  readyForBlockingApproval: false,
  sourceSummary: 'action=创建生产账号; targetUsername=planner; boundary=pre-action draft only, not a blocking IAM workflow',
  nextAction: '补齐前置材料：直属负责人审批 / 系统管理员复核 / 角色与数据范围说明 / 初始密码交付确认',
  riskLevel: '高',
  tone: 'red',
};

export const authAccountPreApprovalApplicationFallback: AuthAccountPreApprovalApplication[] = [
  {
    applicationCode: 'AUTH-PRE-APP-202607090001-0001',
    draftCode: 'AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER',
    readinessCode: 'AUTH-PRE-CREATE-001',
    actionType: '创建生产账号',
    targetUsername: 'planner',
    applicant: '系统管理员',
    applicantRole: 'ADMIN',
    targetOperator: '计划员 陈敏',
    targetRole: 'SALES',
    targetDataScope: 'CUSTOMER_RANGE',
    targetCustomerScope: ['恒信珠宝', '雅辰饰品'],
    targetEnabled: true,
    demoAccount: false,
    reason: '新增业务账号前置审批申请',
    requiredEvidence: ['账号申请单', '直属负责人审批', '系统管理员复核', '角色与数据范围说明', '初始密码交付确认'],
    providedEvidence: ['账号申请单：AUTH-REQ-001'],
    missingEvidence: ['直属负责人审批', '系统管理员复核', '角色与数据范围说明', '初始密码交付确认'],
    missingCapabilities: ['审批通过态强制校验', '正式电子签字', '审批后自动放行'],
    approverRoles: ['GENERAL_MANAGER', 'ADMIN'],
    blockingStatus: '缺少前置材料',
    applicationStatus: 'WAITING_EVIDENCE',
    readyForBlockingApproval: false,
    sourceSummary: 'applicationBoundary=stored pre-approval application, not yet an enforcing IAM workflow',
    nextAction: '补齐前置材料：直属负责人审批 / 系统管理员复核 / 角色与数据范围说明 / 初始密码交付确认',
    riskLevel: '高',
    tone: 'red',
    approvalTaskCode: 'AUTH-PRE-TASK-202607090001-0001',
    submittedAt: '2026-07-09 09:00:00',
  },
];

export const configurationBaselineFallback: ConfigurationBaselineWorkspace = {
  summary: {
    systemParameters: 7,
    codeRules: 5,
    roleBaselines: 6,
    processTemplates: 4,
    warehouseStates: 6,
    videoParameters: 5,
    approvalRequiredItems: 8,
    changeRequests: 3,
    auditEvents: 3,
  },
  systemParameters: [
    { key: 'system.code.prefix', value: 'JG', category: '编码', level: 'C2', description: '二维码和业务编码系统前缀', changeControl: '管理员变更，记录日志', effectiveScope: '全局' },
    { key: 'weight.precision', value: '3', category: '称重', level: 'C1', description: '重量小数位，影响台账和损耗计算', changeControl: '需审批，保留历史', effectiveScope: '库存/工序' },
    { key: 'split.weight.tolerance', value: '0.02', category: '称重', level: 'C1', description: '工单拆分重量误差阈值', changeControl: '需审批，保留历史', effectiveScope: '母单拆分' },
    { key: 'loss.approval.enabled', value: 'true', category: '损耗', level: 'C1', description: '超标损耗是否触发审批', changeControl: '需审批，保留历史', effectiveScope: '工序报工/质检' },
    { key: 'rework.max.count', value: '2', category: '返工', level: 'C1', description: '单张工单允许返工次数上限', changeControl: '需审批，保留历史', effectiveScope: '质检返工' },
    { key: 'inventory.negative.allowed', value: 'false', category: '库存', level: 'C1', description: '一期不允许负库存', changeControl: '需审批，保留历史', effectiveScope: '库存台账' },
    { key: 'pay.formal.enabled', value: 'false', category: '工资', level: 'C1', description: '一期只做工资试算，不做正式发放', changeControl: '需审批，保留历史', effectiveScope: '工资试算' },
  ],
  codeRules: [
    { objectType: '客户订单', format: 'ORD-YYYYMMDD-流水号', example: 'ORD-20260706-001', uniqueness: '全局唯一，作废不复用', changePolicy: 'M3 中期冻结' },
    { objectType: '母单', format: 'CC-YYYYMMDD-流水号-客户简称', example: 'CC-20260706-001-JL', uniqueness: '按业务日期唯一', changePolicy: 'M3 中期冻结' },
    { objectType: '子工单', format: '母单编码-ZD流水号', example: 'CC-20260706-001-JL-ZD001', uniqueness: '同母单内唯一', changePolicy: 'M3 中期冻结' },
    { objectType: '返工工单', format: '原工单编码-FG流水号', example: 'CC-20260706-001-JL-ZD001-FG001', uniqueness: '来源工单内唯一', changePolicy: 'M3 中期冻结' },
    { objectType: '库存台账', format: 'STK-YYYYMMDD-流水号', example: 'STK-20260706-001', uniqueness: '全局唯一，只追加', changePolicy: 'M3 中期冻结' },
  ],
  rolePermissions: [
    { role: '系统管理员', dataScope: '全局', permissions: ['用户', '角色', '菜单', '参数', '字典', '日志'], sensitiveActions: ['角色权限', '参数修改', '数据字典'] },
    { role: '收货员', dataScope: '收货工位', permissions: ['来料入库', '母单标签', '拆包视频'], sensitiveActions: ['标签补打', '视频人工上传'] },
    { role: '车间组长', dataScope: '本班组', permissions: ['工单拆分', '分配', '返工处理', '称重交接'], sensitiveActions: ['报工更正', '超标损耗审批'] },
    { role: '操作工', dataScope: '本人任务', permissions: ['扫码报工', '称重交接', '附件上传'], sensitiveActions: ['报工提交', '称重提交'] },
    { role: '质检员', dataScope: '质检队列', permissions: ['待质检', '质检记录', '返工/报废发起'], sensitiveActions: ['报废发起', '返工判定'] },
    { role: '库管员', dataScope: '仓库', permissions: ['库存查询', '出库', '打包', '库存调整申请'], sensitiveActions: ['库存调整申请', '出库确认'] },
  ],
  processTemplates: [
    {
      code: 'PROC-RING-STD',
      name: '戒托标准加工',
      materialScope: '18K 金戒托 / PT950 吊坠坯',
      steps: ['雕刻', '粗抛', '精抛', '镶石', '终检入库'],
      lossRule: '雕刻记录机器编号；抛光工序标准损耗 2.0%，超标进入审批',
      wageRule: '工序单价按工单创建时快照，一期仅工资试算',
      qualityRule: '雕刻需操机签字；抛光需交单审核；镶石和终检必须质检',
      snapshotFields: '物料单价、工序单价、损耗率、客户归属、机器编号、交单审核',
    },
    {
      code: 'PROC-CHAIN-STD',
      name: '银链标准加工',
      materialScope: '银链半成品',
      steps: ['焊接', '打磨', '清洗', '终检入库'],
      lossRule: '焊接标准损耗 0.8%，缺少称重阻断提交',
      wageRule: '焊接和打磨计件，清洗不计件',
      qualityRule: '焊接缺陷需拍照，终检后转成品',
      snapshotFields: '工序单价、损耗率、质检规则',
    },
    {
      code: 'PROC-ENGRAVE-DAY',
      name: '雕刻单工序统计',
      materialScope: '雕刻来料 / 戒托半成品',
      steps: ['雕刻', '交单审核'],
      lossRule: '设备工序不默认计算贵金属损耗，异常数量进入复核',
      wageRule: '按机器编号和操机人统计产量，一期只做工资试算',
      qualityRule: '操机签字缺失不得确认产能；必要时转质检抽检',
      snapshotFields: '客户、品类、机器编号、雕刻数量、操机签字',
    },
    {
      code: 'PROC-POLISH-DAILY',
      name: '抛光日报与返工',
      materialScope: '抛光 / 返工抛光',
      steps: ['粗抛', '精抛', '交单审核', '复检'],
      lossRule: '抛光按投入产出重量计算损耗，返工默认不计正式工资',
      wageRule: '记录工作项目、开始/完成时间和完成情况，单价按报工快照',
      qualityRule: '交单审核未通过不得进入下一工序或复检',
      snapshotFields: '工作项目、开始时间、完成时间、完成情况、交单审核',
    },
  ],
  warehouseStates: [
    { code: 'RAW-CUSTOMER', name: '待加工来料仓', ownerBoundary: '客户所有', stockStatus: '待加工', transitionRule: '来料入库：外部 -> 待加工', negativeAllowed: false },
    { code: 'WIP', name: '在制仓', ownerBoundary: '客户所有', stockStatus: '在制', transitionRule: '拆分工单：待加工 -> 在制', negativeAllowed: false },
    { code: 'FINISHED', name: '成品仓', ownerBoundary: '客户所有', stockStatus: '成品', transitionRule: '完工入库：在制 -> 成品', negativeAllowed: false },
    { code: 'FROZEN', name: '冻结仓', ownerBoundary: '客户所有', stockStatus: '冻结', transitionRule: '创建出库：成品 -> 冻结', negativeAllowed: false },
    { code: 'REWORK', name: '返工品仓', ownerBoundary: '客户所有', stockStatus: '返工', transitionRule: '质检返工：成品/在制 -> 返工', negativeAllowed: false },
    { code: 'SCRAP', name: '报废品仓', ownerBoundary: '客户所有', stockStatus: '报废', transitionRule: '质检报废：对应状态 -> 报废', negativeAllowed: false },
  ],
  videoParameters: [
    { key: 'video.buffer.seconds', value: '5', scene: '拆包/打包/异常处理', fallback: '人工上传 MP4', retention: '待现场确认', changeControl: '管理员变更，记录日志' },
    { key: 'video.retry.max', value: '3', scene: 'NVR 时间段截取', fallback: '失败进入人工上传队列', retention: '待现场确认', changeControl: '管理员变更，记录日志' },
    { key: 'video.default.format', value: 'mp4', scene: '业务片段', fallback: '原始文件留存路径', retention: '按对象存储策略', changeControl: '版本记录' },
    { key: 'video.upload.max.size', value: '待确认', scene: '人工上传', fallback: '超限走离线归档', retention: '待现场确认', changeControl: '需审批' },
    { key: 'video.camera.binding', value: '工位/业务点', scene: '拆包台、打包台、异常处理台', fallback: '未绑定时允许人工附件', retention: '长期有效', changeControl: '需审批' },
  ],
  components: [
    { component: 'Spring Boot', version: '3.3.7', license: 'Apache-2.0', purpose: '业务 API', source: 'Maven Central', risk: '版本随后端安全补丁评估' },
    { component: 'Vue', version: '3.5.x', license: 'MIT', purpose: 'PC 后台和 PWA', source: 'npm registry', risk: '组件库待冻结' },
    { component: 'RuoYi-Vue', version: '待冻结', license: 'MIT', purpose: '权限、菜单、字典参考基座', source: 'GitHub', risk: '需确认具体分支和二开边界' },
    { component: 'FFmpeg', version: '待冻结', license: 'LGPL/GPL 需确认', purpose: '视频截取', source: 'ffmpeg.org', risk: '需确认二进制许可证策略' },
    { component: 'ZXing', version: '待冻结', license: 'Apache-2.0', purpose: '扫码兜底', source: 'GitHub/Maven/npm', risk: '移动端兼容性需 POC' },
  ],
  backupPolicies: [
    { target: '业务数据库', strategy: '每日全量 + binlog 增量', rpo: '试点 24 小时，正式 15-60 分钟', rto: '试点 1 个工作日，正式 4 小时', owner: '运维' },
    { target: '视频附件', strategy: 'NAS 快照或对象存储生命周期', rpo: '24 小时', rto: '1 个工作日', owner: '运维/库管' },
    { target: '配置和模板', strategy: '每次变更生成版本记录', rpo: '变更即备份', rto: '4 小时', owner: '系统管理员' },
  ],
  changeRequests: [
    {
      code: 'CFG-CR-20260706-001',
      targetKey: 'split.weight.tolerance',
      targetName: '工单拆分重量误差阈值',
      changeType: '参数调整',
      beforeValue: '0.02',
      afterValue: '0.03',
      reason: '银链半成品拆分时现场称重波动较大，需要先在试点线验证是否放宽阈值。',
      applicant: '车间组长 马杰',
      approver: '生产负责人',
      status: '待审批',
      riskLevel: '高',
      submittedAt: '2026-07-06 16:40',
    },
    {
      code: 'CFG-CR-20260706-002',
      targetKey: 'video.upload.max.size',
      targetName: '人工上传视频大小限制',
      changeType: '参数补全',
      beforeValue: '待确认',
      afterValue: '512 MB',
      reason: '拆包视频人工上传兜底需要明确上限，避免超大文件影响对象存储。',
      applicant: '实施顾问',
      approver: '系统管理员',
      status: '已通过',
      riskLevel: '中',
      submittedAt: '2026-07-06 15:12',
    },
    {
      code: 'CFG-CR-20260706-003',
      targetKey: 'CC-YYYYMMDD-流水号-客户简称',
      targetName: '母单编码规则',
      changeType: '规则变更',
      beforeValue: 'CC-YYYYMMDD-流水号-客户简称',
      afterValue: 'MD-YYYYMMDD-流水号-客户简称',
      reason: '与现场单据口径不一致，暂不建议在 M2 后期变更编码体系。',
      applicant: '业务员',
      approver: '系统管理员',
      status: '已驳回',
      riskLevel: '高',
      submittedAt: '2026-07-06 14:05',
    },
  ],
  auditEvents: [
    {
      eventId: 'CFG-AUD-20260706-001',
      targetKey: 'video.upload.max.size',
      operation: '审批通过',
      operator: '系统管理员',
      occurredAt: '2026-07-06 15:26',
      result: '成功',
      evidence: 'CFG-CR-20260706-002 / 评审记录截图',
      traceId: 'TRACE-CFG-002',
    },
    {
      eventId: 'CFG-AUD-20260706-002',
      targetKey: 'CC-YYYYMMDD-流水号-客户简称',
      operation: '审批驳回',
      operator: '系统管理员',
      occurredAt: '2026-07-06 14:33',
      result: '成功',
      evidence: 'CFG-CR-20260706-003 / 编码冻结说明',
      traceId: 'TRACE-CFG-003',
    },
    {
      eventId: 'CFG-AUD-20260706-003',
      targetKey: 'configuration.baseline',
      operation: '导出基线',
      operator: '实施顾问',
      occurredAt: '2026-07-06 17:10',
      result: '成功',
      evidence: 'M2.2 配置基线评审包',
      traceId: 'TRACE-CFG-EXPORT-001',
    },
  ],
  securityContext: {
    operator: 'system-review',
    role: 'ADMIN',
    roleLabel: '系统管理员',
    dataScope: 'ALL',
    dataScopeLabel: '全部数据',
    dataBoundary: '当前评审默认全局可见；正式上线必须由真实角色、菜单权限和审计日志共同约束。',
    headerBased: true,
    allowedPermissions: [
      { code: 'AUTH_ACCOUNT_MANAGE', label: '登录账号管理' },
      { code: 'BUSINESS_INBOUND_CONFIRM', label: '来料确认入库' },
      { code: 'BUSINESS_PRODUCTION_REPORT', label: '生产报工' },
      { code: 'BUSINESS_QUALITY_FREEZE', label: '质检冻结库存' },
      { code: 'BUSINESS_OUTBOUND_SHIPMENT', label: '成品出库' },
      { code: 'BUSINESS_LOSS_APPROVAL', label: '损耗审批试算' },
      { code: 'DOCUMENT_ATTACHMENT_UPLOAD', label: '证据附件上传' },
    ],
    sensitiveActionCodes: [
      'AUTH_ACCOUNT_MANAGE',
      'BUSINESS_INBOUND_CONFIRM',
      'BUSINESS_PRODUCTION_REPORT',
      'BUSINESS_QUALITY_FREEZE',
      'BUSINESS_OUTBOUND_SHIPMENT',
      'BUSINESS_LOSS_APPROVAL',
      'DOCUMENT_ATTACHMENT_UPLOAD',
    ],
    menuCodes: ['dashboard', 'inbound', 'production', 'quality', 'inventory', 'approvals', 'configuration', 'documents', 'sample', 'hardware', 'operations', 'mobile'],
    menuTree: [
      {
        code: 'sample',
        label: '样例库',
        module: '验收脚本',
        route: '/sample',
        buttonCodes: ['REGISTER_IMPORT_REVIEW', 'REGISTER_IMPORT_POSTING'],
        children: [],
      },
      {
        code: 'configuration',
        label: '配置基线',
        module: '参数 / 构型',
        route: '/configuration',
        buttonCodes: ['AUTH_ACCOUNT_MANAGE'],
        children: [],
      },
    ],
    limitationNotes: [
      '当前身份优先来自登录会话；M3.100 已展示账号仓储、PBKDF2 密码哈希、5 次失败锁定、账号启停、管理员解锁、生产密码策略、密码过期边界、登录态自助改密续期、认证审计摘要链、业务工作台 GET 首版数据范围过滤、账号管理审批摘要和账号客户范围；生产仍需替换初始评审账号和接入 SSO/JWT。',
      '业务工作台 GET 已接入首版 dataScope 过滤；CUSTOMER_RANGE 登录账号会带出账号 customerScope，完整组织客户授权表和深层行级权限仍需继续固化。',
      '当前菜单权限已由后端菜单树返回，前端登录会话会按 menuCodes/menuTree 过滤侧栏；请求头兜底仍展示评审全量导航。',
      '敏感 POST 和账号管理接口已接入轻量角色守卫，账号管理会生成审批摘要任务，但复杂审批流、前置账号审批和工资锁账仍未完成。',
    ],
    customerScope: [],
    headerContract: {
      operatorHeader: 'X-Zhubao-Operator',
      roleHeader: 'X-Zhubao-Role',
      dataScopeHeader: 'X-Zhubao-Data-Scope',
      customerScopeHeader: 'X-Zhubao-Customer-Scope',
    },
  },
};
