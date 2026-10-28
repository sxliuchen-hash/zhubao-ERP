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

export function registerRuleTone(level: RegisterValidationRule['level']): Tone {
  return level === 'BLOCK' ? 'red' : 'amber';
}
