import { describe, expect, it } from 'vitest';
import {
  registerFieldCoverage,
  registerImportSummary,
  registerImportTemplates,
  registerProcessCoverage,
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
    expect(outbound?.validationRules.map((rule) => rule.code)).toContain('OUT-003');
    expect(polishing?.validationRules.map((rule) => rule.code)).toContain('POL-003');
    expect(engraving?.validationRules.map((rule) => rule.code)).toContain('ENG-003');
    expect(engraving?.validationRules[0].description).toContain('上下文继承');

    expect(inbound?.fields.find((field) => field.targetField === 'sourceSheetName')?.targetObject)
      .toBe('RegisterImportPreview');
    expect(engraving?.fields.find((field) => field.targetField === 'machineLineNo')?.targetObject)
      .toBe('RegisterImportPreview');
  });

  it('keeps register process and field coverage audit explicit', () => {
    expect(registerProcessCoverage).toHaveLength(4);
    expect(registerProcessCoverage.some((row) => row.coveredProcess.includes('雕刻'))).toBe(true);
    expect(registerProcessCoverage.some((row) => row.coveredProcess.includes('粗抛'))).toBe(true);
    expect(registerProcessCoverage.some((row) => row.sourceFile === '出货登记表.xlsx' && row.coveredProcess.includes('出货'))).toBe(true);

    expect(registerFieldCoverage).toHaveLength(48);
    expect(registerFieldCoverage.some((row) =>
      row.sourceFile === '雕刻登记表.xlsx'
      && row.sourceColumn === '操机签字'
      && row.targetField === 'operatorSignatureName',
    )).toBe(true);
    expect(registerFieldCoverage.some((row) =>
      row.sourceFile === '抛光登记表.xlsx'
      && row.sourceColumn === '交单审核'
      && row.status === 'partial',
    )).toBe(true);
    expect(registerFieldCoverage.some((row) =>
      row.sourceColumn === '原表行号'
      && row.targetField === 'sourceRegisterLineNo',
    )).toBe(true);
  });
});
