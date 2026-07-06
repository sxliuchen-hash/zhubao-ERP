import type { Tone } from './m1PrototypeData';

export interface ConfigurationBaselineSummary {
  systemParameters: number;
  codeRules: number;
  roleBaselines: number;
  processTemplates: number;
  warehouseStates: number;
  videoParameters: number;
  approvalRequiredItems: number;
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
}

export const configurationToneByLevel: Record<string, Tone> = {
  C1: 'red',
  C2: 'amber',
  C3: 'blue',
  C4: 'violet',
  C5: 'gray',
};

export const configurationBaselineFallback: ConfigurationBaselineWorkspace = {
  summary: {
    systemParameters: 7,
    codeRules: 5,
    roleBaselines: 6,
    processTemplates: 2,
    warehouseStates: 6,
    videoParameters: 5,
    approvalRequiredItems: 8,
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
      steps: ['粗抛', '精抛', '镶石', '终检入库'],
      lossRule: '抛光工序标准损耗 2.0%，超标进入审批',
      wageRule: '工序单价按工单创建时快照，一期仅工资试算',
      qualityRule: '镶石和终检必须质检',
      snapshotFields: '物料单价、工序单价、损耗率、客户归属',
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
};
