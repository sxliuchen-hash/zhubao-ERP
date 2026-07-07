import { describe, expect, it } from 'vitest';
import { registerImportSummary, registerImportTemplates } from './registerImportTemplates';

describe('registerImportTemplates', () => {
  it('keeps front-end register templates aligned with the backend review contract', () => {
    expect(registerImportTemplates).toHaveLength(4);
    expect(registerImportSummary.fields).toBe(39);

    const inbound = registerImportTemplates.find((template) => template.code === 'REG-INBOUND');
    const outbound = registerImportTemplates.find((template) => template.code === 'REG-OUTBOUND');
    const polishing = registerImportTemplates.find((template) => template.code === 'REG-POLISHING');
    const engraving = registerImportTemplates.find((template) => template.code === 'REG-ENGRAVING');

    expect(inbound?.validationRules.map((rule) => rule.code)).toContain('IN-003');
    expect(outbound?.validationRules.map((rule) => rule.code)).toContain('OUT-003');
    expect(polishing?.validationRules.map((rule) => rule.code)).toContain('POL-003');
    expect(engraving?.validationRules.map((rule) => rule.code)).toContain('ENG-003');
    expect(engraving?.validationRules[0].description).toContain('上下文继承');

    expect(inbound?.fields.find((field) => field.targetField === 'sourceSheetName')?.targetObject)
      .toBe('RegisterImportPreview');
    expect(engraving?.fields.find((field) => field.targetField === 'machineLineNo')?.targetObject)
      .toBe('RegisterImportPreview');
  });
});
