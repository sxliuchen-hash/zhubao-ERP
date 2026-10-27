import { describe, expect, it } from 'vitest';
import {
  registerImportSummary,
  registerImportTemplates,
} from './registerImportTemplates';

describe('registerImportTemplates', () => {
  it('keeps front-end register templates aligned with the backend review contract', () => {
    expect(registerImportTemplates).toHaveLength(4);
    expect(registerImportSummary.fields).toBe(48);

    const inbound = registerImportTemplates.find((template) => template.code === 'REG-INBOUND');
    const outbound = registerImportTemplates.find((template) => template.code === 'REG-OUTBOUND');
    const polishing = registerImportTemplates.find((template) => template.code === 'REG-POLISHING');
    const engraving = registerImportTemplates.find((template) => template.code === 'REG-ENGRAVING');

    expect(inbound?.validationRules.map((rule) => rule.code)).toContain('IN-003');
    expect(inbound?.fields.find((field) => field.targetField === 'remark')?.sourceColumn).toBe('备注');
    expect(inbound?.fields.find((field) => field.targetField === 'sourceRegisterType')?.sourceColumn).toBe('登记表类型');
    expect(inbound?.fields.find((field) => field.targetField === 'sourceRegisterLineNo')?.sourceColumn).toBe('原表行号');
    expect(inbound?.fields.map((field) => field.sourceColumn)).toEqual(expect.arrayContaining([
      '序号',
      '入库日期',
      '快递名称',
      '快递单号',
      '寄货人/姓名',
      '收货人/姓名',
      '拆箱签字',
      '货品名称',
      '数量',
      '备注',
    ]));
    expect(outbound?.validationRules.map((rule) => rule.code)).toContain('OUT-003');
    expect(outbound?.fields.map((field) => field.sourceColumn)).toEqual(expect.arrayContaining([
      '序号',
      '日期',
      '客户',
      '交货类目',
      '交货数量',
      '快递',
      '经办人',
      '备注',
    ]));
    expect(polishing?.validationRules.map((rule) => rule.code)).toContain('POL-003');
    expect(polishing?.fields.map((field) => field.sourceColumn)).toEqual(expect.arrayContaining([
      '姓名',
      '日期',
      '序号',
      '工作项目',
      '开始时间',
      '完成时间',
      '完成情况',
      '交单审核',
      '备注',
    ]));
    expect(engraving?.validationRules.map((rule) => rule.code)).toContain('ENG-003');
    expect(engraving?.validationRules[0].description).toContain('上下文继承');
    expect(engraving?.fields.map((field) => field.sourceColumn)).toEqual(expect.arrayContaining([
      '序号',
      '日期',
      '客户',
      '品类',
      '机器编号行号',
      '机器编号',
      '雕刻数量',
      '操机签字',
      '备注',
    ]));

    expect(inbound?.fields.find((field) => field.targetField === 'sourceSheetName')?.targetObject)
      .toBe('RegisterImportPreview');
    expect(engraving?.fields.find((field) => field.targetField === 'machineLineNo')?.targetObject)
      .toBe('RegisterImportPreview');
  });

});
