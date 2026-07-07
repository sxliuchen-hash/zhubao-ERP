import type { RegisterImportPreview } from '../../data/registerImportTemplates';

const issueHeader = [
  'importBatchNo',
  'templateCode',
  'templateName',
  'sourceFileName',
  'sourceSheetName',
  'sourceLineNo',
  'sourceRegisterSequence',
  'status',
  'ruleCode',
  'level',
  'field',
  'message',
  'fallback',
  'normalizedFields',
];

export function registerPreviewIssueCount(preview: RegisterImportPreview | null): number {
  return preview?.rows.reduce((sum, row) => sum + row.messages.length, 0) ?? 0;
}

export function buildRegisterPreviewIssueCsv(preview: RegisterImportPreview): string {
  const rows = preview.rows.flatMap((row) =>
    row.messages.map((message) => [
      preview.importBatchNo,
      preview.templateCode,
      preview.templateName,
      preview.sourceFileName,
      row.normalizedFields.sourceSheetName ?? '',
      String(row.sourceLineNo),
      row.sourceRegisterSequence,
      row.status,
      message.ruleCode,
      message.level,
      message.field,
      message.message,
      message.fallback,
      JSON.stringify(row.normalizedFields),
    ]),
  );
  return [issueHeader, ...rows].map((row) => row.map(csvCell).join(',')).join('\r\n');
}

export function registerPreviewIssueFileName(preview: RegisterImportPreview): string {
  const safeBatchNo = preview.importBatchNo.replace(/[^A-Za-z0-9_-]/g, '_');
  return `${safeBatchNo}_issues.csv`;
}

function csvCell(value: string): string {
  if (/[",\r\n]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}
