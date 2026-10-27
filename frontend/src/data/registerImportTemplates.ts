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

export interface RegisterImportPreviewMessage {
  ruleCode: string;
  level: 'BLOCK' | 'WARN';
  field: string;
  message: string;
  fallback: string;
}

export interface RegisterImportPreviewRow {
  sourceLineNo: number;
  sourceRegisterSequence: string;
  status: 'READY' | 'WARNING' | 'BLOCKED';
  normalizedFields: Record<string, string>;
  messages: RegisterImportPreviewMessage[];
}

export interface RegisterImportPreview {
  importBatchNo: string;
  templateCode: string;
  templateName: string;
  sourceFileName: string;
  totalRows: number;
  readyRows: number;
  warningRows: number;
  blockedRows: number;
  executable: boolean;
  nextAction: string;
  rows: RegisterImportPreviewRow[];
}

export interface RegisterImportBatchRow {
  importBatchNo: string;
  sourceLineNo: number;
  sourceRegisterSequence: string;
  status: 'READY' | 'WARNING' | 'BLOCKED' | 'POSTED';
  normalizedFields: Record<string, string>;
  messages: RegisterImportPreviewMessage[];
}

export interface RegisterImportBatch {
  importBatchNo: string;
  templateCode: string;
  templateName: string;
  sourceFileName: string;
  targetCommand: string;
  totalRows: number;
  readyRows: number;
  warningRows: number;
  blockedRows: number;
  executable: boolean;
  status: 'CONFIRMED' | 'POSTED' | 'REJECTED';
  confirmedBy: string;
  confirmedAt: string;
  confirmRemark: string;
  nextAction: string;
  rows: RegisterImportBatchRow[];
}

export interface RegisterImportPostingIssue {
  field: string;
  level: 'BLOCK' | 'WARN';
  message: string;
  fallback: string;
}

export interface RegisterImportPostingDraft {
  importBatchNo: string;
  sourceLineNo: number;
  sourceRegisterSequence: string;
  targetCommand: string;
  status: 'READY' | 'WARNING' | 'BLOCKED' | 'POSTED';
  mappedFields: Record<string, string>;
  missingFields: RegisterImportPostingIssue[];
  warningMessages: RegisterImportPreviewMessage[];
}

export interface RegisterImportPostingPlan {
  importBatchNo: string;
  templateCode: string;
  templateName: string;
  targetCommand: string;
  status: 'READY' | 'WARNING' | 'BLOCKED' | 'POSTED';
  totalRows: number;
  readyDrafts: number;
  warningDrafts: number;
  blockedDrafts: number;
  nextAction: string;
  drafts: RegisterImportPostingDraft[];
}

export interface RegisterImportPostingExecutionItem {
  sourceLineNo: number;
  sourceRegisterSequence: string;
  targetCommand: string;
  status: 'POSTED' | 'SKIPPED';
  businessCode: string;
  ledgerCode: string;
  message: string;
}

export interface RegisterImportPostingExecutionResult {
  importBatchNo: string;
  templateCode: string;
  templateName: string;
  status: 'POSTED' | 'PARTIAL' | 'REJECTED';
  totalDrafts: number;
  postedDrafts: number;
  skippedDrafts: number;
  blockedDrafts: number;
  warningDrafts: number;
  operator: string;
  postedAt: string;
  nextAction: string;
  items: RegisterImportPostingExecutionItem[];
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
    nextAction: '确认导入先落结构化批次，再接客户/物料入账映射',
    fields: [
      field('序号', 'sourceRegisterSequence', false, '保留纸质序号', '不作业务主键'),
      field('工作表名', 'sourceSheetName', false, '取 Excel 工作表名称', '用于定位原始工作表', 'RegisterImportPreview'),
      field('登记表类型', 'sourceRegisterType', false, '按模板名称生成', '用于审计来源'),
      field('原表行号', 'sourceRegisterLineNo', false, '取 Excel 原始行号', '用于回表定位'),
      field('入库日期', 'inboundDate', true, '月日补全年份', 'yyyy-MM-dd'),
      field('快递名称', 'expressCompany', true, '原文 trim', '不能为空'),
      field('快递单号', 'courierNo', true, '原文 trim', '全局追溯入口，建议唯一'),
      field('寄货人/姓名', 'senderName', false, '姓名快照', '可为空但应提示补全'),
      field('收货人/姓名', 'receiverName', false, '姓名快照', '可为空但应提示补全'),
      field('拆箱签字', 'unpackSigner', true, '姓名快照', '一期保存姓名，二期接签名附件'),
      field('货品名称', 'goodsName', true, '现场名称快照', '必须映射物料档案'),
      field('数量', 'quantity + quantityUnit', true, '数量默认单位件', '数量必须大于 0'),
      field('备注', 'remark', false, '原文保留', '异常说明或现场补充信息'),
    ],
    validationRules: [
      rule('IN-001', 'BLOCK', '快递单号重复时不得确认入库', '转人工复核'),
      rule('IN-002', 'WARN', '纸质月日必须按业务日期补全年份', '导入预览标黄'),
      rule('IN-003', 'BLOCK', '未确认客户和物料前不得拆分工单', '保存草稿'),
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
    nextAction: '确认导入先落结构化批次，再接出库单持久化',
    fields: [
      field('序号', 'sourceRegisterSequence', false, '保留纸质序号', '不作业务主键'),
      field('工作表名', 'sourceSheetName', false, '取 Excel 工作表名称', '用于定位原始工作表', 'RegisterImportPreview'),
      field('登记表类型', 'sourceRegisterType', false, '按模板名称生成', '用于审计来源'),
      field('原表行号', 'sourceRegisterLineNo', false, '取 Excel 原始行号', '用于回表定位'),
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
      rule('OUT-003', 'WARN', '纸质快递栏原文必须保留', '写入 paperExpressText'),
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
    nextAction: '确认导入先落结构化批次，再接交单审核动作',
    fields: [
      field('姓名', 'operator', true, '表头继承到明细行', '必须匹配员工或保存姓名快照'),
      field('日期', 'workDate', true, '表头继承到明细行', 'yyyy-MM-dd'),
      field('序号', 'sourceRegisterSequence', false, '保留纸质序号', '不作业务主键'),
      field('工作表名', 'sourceSheetName', false, '取 Excel 工作表名称', '用于定位原始工作表', 'RegisterImportPreview'),
      field('登记表类型', 'sourceRegisterType', false, '按模板名称生成', '用于审计来源'),
      field('原表行号', 'sourceRegisterLineNo', false, '取 Excel 原始行号', '用于回表定位'),
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
      rule('POL-003', 'WARN', '完成情况不能直接驱动后端状态机', '需工序状态规则复核'),
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
    nextAction: '确认导入先落结构化批次，再接设备产量独立落库',
    fields: [
      field('序号', 'sourceRegisterSequence', false, '日内分组序号', '不作业务主键'),
      field('工作表名', 'sourceSheetName', false, '取 Excel 工作表名称', '用于定位原始工作表', 'RegisterImportPreview'),
      field('登记表类型', 'sourceRegisterType', false, '按模板名称生成', '用于审计来源'),
      field('原表行号', 'sourceRegisterLineNo', false, '取 Excel 原始行号', '用于回表定位'),
      field('日期', 'workDate', true, '组内上下文继承', 'yyyy-MM-dd'),
      field('客户', 'customerId/customerNameSnapshot', true, '组内上下文继承并匹配主数据', '匹配失败转待确认'),
      field('品类', 'productCategory', true, '组内上下文继承', '产能统计必填'),
      field('机器编号行号', 'machineLineNo', false, '保留纸质 1-8 台机器行', '用于回表定位', 'RegisterImportPreview'),
      field('机器编号', 'machineNo', true, '1-8 号机器行保留', '未匹配设备档案时保存文本'),
      field('雕刻数量', 'quantity + quantityUnit', true, '默认单位件', '数量必须大于 0'),
      field('操机签字', 'operatorSignatureName', true, '姓名快照', '不能用录入人替代'),
      field('备注', 'remark', false, '原文保留', '设备异常说明'),
    ],
    validationRules: [
      rule('ENG-001', 'BLOCK', '日期、客户、品类空白格必须从本组上下文继承', '导入预览阻断'),
      rule('ENG-002', 'BLOCK', '操机签字缺失不得确认设备产量', '保存草稿'),
      rule('ENG-003', 'WARN', '机器编号未匹配设备档案时标记待匹配', '允许先保存文本'),
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
  parserStatus: 'Excel preview parser ready; structured batch confirm ready; explicit business posting command ready',
};

export function buildRegisterImportPostingPlan(batch: RegisterImportBatch): RegisterImportPostingPlan {
  const drafts = batch.rows.map((row) => buildRegisterImportPostingDraft(batch, row));
  const blockedDrafts = drafts.filter((draft) => draft.status === 'BLOCKED').length;
  const warningDrafts = drafts.filter((draft) => draft.status === 'WARNING').length;
  const readyDrafts = drafts.filter((draft) => draft.status === 'READY').length;
  const postedDrafts = drafts.filter((draft) => draft.status === 'POSTED').length;
  const status = postedDrafts === drafts.length && drafts.length > 0
    ? 'POSTED'
    : blockedDrafts > 0 ? 'BLOCKED' : warningDrafts > 0 ? 'WARNING' : 'READY';

  return {
    importBatchNo: batch.importBatchNo,
    templateCode: batch.templateCode,
    templateName: batch.templateName,
    targetCommand: postingTargetCommand(batch),
    status,
    totalRows: batch.totalRows,
    readyDrafts,
    warningDrafts,
    blockedDrafts,
    nextAction: status === 'POSTED'
      ? '该登记表批次已完成显式入账，重复执行会被拒绝'
      : status === 'BLOCKED'
        ? '先补齐业务主数据、母单/工单、物料、仓库和称重字段，再允许生成业务单据'
        : '可进入业务命令生成，但入账仍必须通过后端业务服务和库存台账',
    drafts,
  };
}

export function registerRuleTone(level: RegisterValidationRule['level']): Tone {
  return level === 'BLOCK' ? 'red' : 'amber';
}

function field(
  sourceColumn: string,
  targetField: string,
  required: boolean,
  normalizeRule: string,
  validationRule: string,
  targetObject = '业务命令',
): RegisterFieldMapping {
  return {
    sourceColumn,
    targetField,
    targetObject,
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

function buildRegisterImportPostingDraft(
  batch: RegisterImportBatch,
  row: RegisterImportBatchRow,
): RegisterImportPostingDraft {
  const mappedFields = mappedPostingFields(batch, row.normalizedFields);
  const missingFields = missingPostingFields(batch.templateCode, mappedFields);
  const warningMessages = row.messages.filter((message) => message.level === 'WARN');
  const status = row.status === 'POSTED'
    ? 'POSTED'
    : missingFields.length > 0 ? 'BLOCKED' : warningMessages.length > 0 ? 'WARNING' : 'READY';

  return {
    importBatchNo: batch.importBatchNo,
    sourceLineNo: row.sourceLineNo,
    sourceRegisterSequence: row.sourceRegisterSequence,
    targetCommand: postingTargetCommand(batch),
    status,
    mappedFields,
    missingFields,
    warningMessages,
  };
}

function mappedPostingFields(template: RegisterImportBatch, fields: Record<string, string>): Record<string, string> {
  const mapped: Record<string, string> = {
    importBatchNo: template.importBatchNo,
    sourceSheetName: fields.sourceSheetName ?? '',
    sourceRegisterType: fields.sourceRegisterType ?? '',
    sourceRegisterLineNo: fields.sourceRegisterLineNo ?? '',
    sourceRegisterSequence: fields.sourceRegisterSequence ?? '',
  };

  if (template.templateCode === 'REG-INBOUND') {
    return {
      ...mapped,
      motherOrderCode: fields.motherOrderCode ?? '',
      customerId: fields.customerId ?? '',
      ownerCustomerId: fields.ownerCustomerId ?? '',
      materialCode: fields.materialCode ?? '',
      warehouseCode: fields.warehouseCode || 'RAW-CUSTOMER',
      inboundDate: fields.inboundDate ?? '',
      expressCompany: fields.expressCompany ?? '',
      courierNo: fields.courierNo ?? '',
      goodsName: fields.goodsName ?? '',
      quantity: fields.quantity ?? '',
      quantityUnit: fields.quantityUnit || '件',
      weight: fields.weight ?? '',
      operator: fields.operator || template.confirmedBy,
      evidenceCode: fields.evidenceCode ?? '',
    };
  }

  if (template.templateCode === 'REG-OUTBOUND') {
    return {
      ...mapped,
      shipmentCode: fields.shipmentCode ?? '',
      motherOrderCode: fields.motherOrderCode ?? '',
      workOrderCode: fields.workOrderCode ?? '',
      customerId: fields.customerId ?? '',
      ownerCustomerId: fields.ownerCustomerId ?? '',
      materialCode: fields.materialCode ?? '',
      fromWarehouseCode: fields.fromWarehouseCode || 'FINISHED',
      toWarehouseCode: fields.toWarehouseCode || 'SHIPPED',
      deliveryDate: fields.deliveryDate ?? '',
      deliveryCategory: fields.deliveryCategory ?? '',
      quantity: fields.quantity ?? '',
      quantityUnit: fields.quantityUnit || '件',
      weight: fields.weight ?? '',
      expressCompany: fields.expressCompany ?? '',
      logisticsNo: fields.logisticsNo ?? '',
      paperExpressText: fields.paperExpressText ?? '',
      handler: fields.handler ?? '',
      operator: fields.operator || template.confirmedBy,
    };
  }

  return {
    ...mapped,
    motherOrderCode: fields.motherOrderCode ?? '',
    workOrderCode: fields.workOrderCode ?? '',
    customerId: fields.customerId ?? '',
    ownerCustomerId: fields.ownerCustomerId ?? '',
    materialCode: fields.materialCode ?? '',
    fromWarehouseCode: fields.fromWarehouseCode ?? '',
    toWarehouseCode: fields.toWarehouseCode ?? '',
    quantity: fields.quantity ?? '',
    quantityUnit: fields.quantityUnit || '件',
    weight: fields.weight ?? '',
    workDate: fields.workDate ?? '',
    process: template.templateCode === 'REG-ENGRAVING' ? (fields.process || '雕刻') : (fields.process || fields.workItemName || ''),
    productCategory: fields.productCategory ?? '',
    machineLineNo: fields.machineLineNo ?? '',
    machineNo: fields.machineNo ?? '',
    workItemName: fields.workItemName ?? '',
    handoffAuditStatus: fields.handoffAuditStatus ?? '',
    operator: fields.operator || template.confirmedBy,
    operatorSignatureName: fields.operatorSignatureName ?? '',
  };
}

function missingPostingFields(templateCode: string, fields: Record<string, string>): RegisterImportPostingIssue[] {
  const required = templateCode === 'REG-INBOUND'
    ? ['motherOrderCode', 'customerId', 'materialCode', 'quantity', 'weight']
    : templateCode === 'REG-OUTBOUND'
      ? ['shipmentCode', 'motherOrderCode', 'workOrderCode', 'customerId', 'materialCode', 'quantity', 'weight', 'logisticsNo']
      : ['motherOrderCode', 'workOrderCode', 'customerId', 'materialCode', 'fromWarehouseCode', 'toWarehouseCode', 'quantity', 'weight', 'process'];
  const engravingRequired = templateCode === 'REG-ENGRAVING' ? ['machineNo', 'operatorSignatureName'] : [];

  return [...required, ...engravingRequired]
    .filter((fieldName) => !fields[fieldName])
    .map((fieldName) => ({
      field: fieldName,
      level: 'BLOCK',
      message: `${fieldName} 缺失，不能生成正式业务入账命令`,
      fallback: '在导入复核页补齐或匹配主数据后重试',
    }));
}

function postingTargetCommand(batch: RegisterImportBatch): string {
  return batch.templateCode === 'REG-ENGRAVING'
    ? 'MachineOutputRecordDraft / ReportProductionCommand'
    : batch.targetCommand;
}
