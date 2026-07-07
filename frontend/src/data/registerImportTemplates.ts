import type { Tone } from './m1PrototypeData';

export interface RegisterFieldMapping {
  sourceColumn: string;
  targetField: string;
  targetObject: string;
  required: boolean;
  normalizeRule: string;
  validationRule: string;
}

export interface RegisterValidationRule {
  code: string;
  level: 'BLOCK' | 'WARN';
  description: string;
  fallback: string;
}

export interface RegisterImportTemplate {
  code: string;
  name: string;
  sourceFile: string;
  businessFlow: string;
  targetCommand: string;
  targetLedgerType: string;
  parserStatus: 'COMMAND_READY' | 'PARSER_PENDING';
  nextAction: string;
  fields: RegisterFieldMapping[];
  validationRules: RegisterValidationRule[];
}

export interface RegisterImportSummary {
  templates: number;
  fields: number;
  requiredFields: number;
  commandReadyTemplates: number;
  parserStatus: string;
}

export const registerImportTemplates: RegisterImportTemplate[] = [
  {
    code: 'REG-INBOUND',
    name: '签收入库登记表',
    sourceFile: '入库登记表.xlsx',
    businessFlow: '收货签收 -> 拆包称重 -> 客户所有原料入账',
    targetCommand: 'ConfirmInboundCommand',
    targetLedgerType: 'inbound_confirm',
    parserStatus: 'COMMAND_READY',
    nextAction: '补 sourceSheetName/importBatchNo 后接入 Excel 上传解析',
    fields: [
      field('序号', 'sourceRegisterSequence', false, '保留纸质序号', '不作业务主键'),
      field('入库日期', 'inboundDate', true, '月日补全年份', 'yyyy-MM-dd'),
      field('快递名称', 'expressCompany', true, '原文 trim', '不能为空'),
      field('快递单号', 'courierNo', true, '原文 trim', '全局追溯入口，建议唯一'),
      field('寄货人姓名', 'senderName', false, '姓名快照', '可为空但应提示补全'),
      field('收货人姓名', 'receiverName', false, '姓名快照', '可为空但应提示补全'),
      field('拆箱签字', 'unpackSigner', true, '姓名快照', '一期保存姓名，二期接签名附件'),
      field('货品名称', 'goodsName', true, '现场名称快照', '必须映射物料档案'),
      field('数量', 'quantity + quantityUnit', true, '数量默认单位件', '数量必须大于 0'),
    ],
    validationRules: [
      rule('IN-001', 'BLOCK', '快递单号重复时不得确认入库', '转人工复核'),
      rule('IN-002', 'WARN', '纸质月日必须按业务日期补全年份', '导入预览标黄'),
    ],
  },
  {
    code: 'REG-OUTBOUND',
    name: '交货情况登记表',
    sourceFile: '出货登记表.xlsx',
    businessFlow: '成品库存 -> 打包出货 -> 已发货库存',
    targetCommand: 'ShipFinishedStockCommand',
    targetLedgerType: 'outbound_shipment',
    parserStatus: 'COMMAND_READY',
    nextAction: '补出库单结构化持久化和导入批次表',
    fields: [
      field('序号', 'sourceRegisterSequence', false, '保留纸质序号', '不作业务主键'),
      field('日期', 'deliveryDate', true, '月日补全年份', 'yyyy-MM-dd'),
      field('客户', 'customerId/customerNameSnapshot', true, '客户名称匹配主数据', '匹配失败转待确认'),
      field('交货类目', 'deliveryCategory', true, '枚举映射', '成品/返修件/补发件/样品'),
      field('交货数量', 'quantity + quantityUnit', true, '数量默认单位件', '必须匹配库存单位'),
      field('快递', 'paperExpressText + expressCompany + logisticsNo', true, '原文保留后拆分', '物流单号不能为空'),
      field('经办人', 'handler', true, '姓名快照', '与系统操作人分开保存'),
      field('备注', 'remark', false, '原文保留', '不得承载关键字段'),
    ],
    validationRules: [
      rule('OUT-001', 'BLOCK', '出库数量不得超过客户成品可用库存', '拒绝确认出货'),
      rule('OUT-002', 'BLOCK', '经办人缺失不得确认发货', '保存草稿'),
    ],
  },
  {
    code: 'REG-POLISHING',
    name: '工作项目记录单（日）',
    sourceFile: '抛光登记表.xlsx',
    businessFlow: '工序日报 -> 交单审核 -> 工序报工',
    targetCommand: 'ReportProductionCommand',
    targetLedgerType: 'production_report',
    parserStatus: 'COMMAND_READY',
    nextAction: '补日报导入预览和交单审核动作',
    fields: [
      field('姓名', 'operator', true, '表头继承到明细行', '必须匹配员工或保存姓名快照'),
      field('日期', 'workDate', true, '表头继承到明细行', 'yyyy-MM-dd'),
      field('序号', 'sourceRegisterSequence', false, '保留纸质序号', '不作业务主键'),
      field('工作项目', 'workItemName', true, '原文 trim', '粗抛/精抛/返工抛光等'),
      field('开始时间', 'startedAt', false, '与 workDate 组合', '跨班次需人工确认'),
      field('完成时间', 'finishedAt', false, '与 workDate 组合', '进行中可为空'),
      field('完成情况', 'completionStatus', true, '原文快照', '不等同工序状态'),
      field('交单审核', 'handoffAuditStatus', true, '审核状态快照', '未审核不得进下一工序'),
      field('备注', 'remark', false, '原文保留', '异常说明'),
    ],
    validationRules: [
      rule('POL-001', 'BLOCK', '姓名和日期必须展开到每条工作项目明细', '导入预览阻断'),
      rule('POL-002', 'BLOCK', '未交单审核不得进入下一工序或复检', '保存报工草稿'),
    ],
  },
  {
    code: 'REG-ENGRAVING',
    name: '雕刻机统计单（日）',
    sourceFile: '雕刻登记表.xlsx',
    businessFlow: '设备产量统计 -> 工序报工/工资试算',
    targetCommand: 'ReportProductionCommand / biz_machine_output_record',
    targetLedgerType: 'production_report',
    parserStatus: 'COMMAND_READY',
    nextAction: '新增设备产量独立落库和机器档案匹配',
    fields: [
      field('序号', 'sourceRegisterSequence', false, '日内分组序号', '不作业务主键'),
      field('日期', 'workDate', true, '合并单元格继承', 'yyyy-MM-dd'),
      field('客户', 'customerId/customerNameSnapshot', true, '合并单元格继承并匹配主数据', '匹配失败转待确认'),
      field('品类', 'productCategory', true, '合并单元格继承', '产能统计必填'),
      field('机器编号', 'machineNo', true, '1-8 号机器行保留', '未匹配设备档案时保存文本'),
      field('雕刻数量', 'quantity + quantityUnit', true, '默认单位件', '数量必须大于 0'),
      field('操机签字', 'operatorSignatureName', true, '姓名快照', '不能用录入人替代'),
      field('备注', 'remark', false, '原文保留', '设备异常说明'),
    ],
    validationRules: [
      rule('ENG-001', 'BLOCK', '日期、客户、品类空白格必须从上一组合并区域继承', '导入预览阻断'),
      rule('ENG-002', 'BLOCK', '操机签字缺失不得确认设备产量', '保存草稿'),
    ],
  },
];

export const registerImportSummary: RegisterImportSummary = {
  templates: registerImportTemplates.length,
  fields: registerImportTemplates.reduce((sum, template) => sum + template.fields.length, 0),
  requiredFields: registerImportTemplates.reduce(
    (sum, template) => sum + template.fields.filter((field) => field.required).length,
    0,
  ),
  commandReadyTemplates: registerImportTemplates.filter((template) => template.parserStatus === 'COMMAND_READY').length,
  parserStatus: 'Excel parser pending; command mapping ready',
};

export function registerRuleTone(level: RegisterValidationRule['level']): Tone {
  return level === 'BLOCK' ? 'red' : 'amber';
}

function field(
  sourceColumn: string,
  targetField: string,
  required: boolean,
  normalizeRule: string,
  validationRule: string,
): RegisterFieldMapping {
  return {
    sourceColumn,
    targetField,
    targetObject: '业务命令',
    required,
    normalizeRule,
    validationRule,
  };
}

function rule(
  code: string,
  level: RegisterValidationRule['level'],
  description: string,
  fallback: string,
): RegisterValidationRule {
  return { code, level, description, fallback };
}
