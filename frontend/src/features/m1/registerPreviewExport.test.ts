import { describe, expect, it } from 'vitest';
import {
  buildRegisterPreviewIssueCsv,
  registerPreviewIssueCount,
  registerPreviewIssueFileName,
} from './registerPreviewExport';
import type { RegisterImportPreview } from '../../data/registerImportTemplates';

describe('registerPreviewExport', () => {
  it('builds a CSV for blocked and warning preview messages', () => {
    const preview: RegisterImportPreview = {
      importBatchNo: 'IMP-REG-OUTBOUND-20260707193000',
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      sourceFileName: '出货登记表.xlsx',
      totalRows: 1,
      readyRows: 0,
      warningRows: 0,
      blockedRows: 1,
      executable: false,
      nextAction: '先处理阻断行，再确认导入',
      rows: [{
        sourceLineNo: 3,
        sourceRegisterSequence: '1',
        status: 'BLOCKED',
        normalizedFields: {
          sourceSheetName: 'Sheet1',
          paperExpressText: '',
          customerNameSnapshot: '雅辰饰品',
        },
        messages: [{
          ruleCode: 'OUT-LOGISTICS',
          level: 'BLOCK',
          field: 'logisticsNo',
          message: '物流单号不能为空',
          fallback: '从纸质快递栏拆分或手工补录',
        }],
      }],
    };

    const csv = buildRegisterPreviewIssueCsv(preview);

    expect(registerPreviewIssueCount(preview)).toBe(1);
    expect(registerPreviewIssueFileName(preview)).toBe('IMP-REG-OUTBOUND-20260707193000_issues.csv');
    expect(csv).toContain('sourceLineNo');
    expect(csv).toContain('sourceSheetName');
    expect(csv).toContain('Sheet1');
    expect(csv).toContain('OUT-LOGISTICS');
    expect(csv).toContain('物流单号不能为空');
    expect(csv).toContain('""paperExpressText"":""""');
  });
});
